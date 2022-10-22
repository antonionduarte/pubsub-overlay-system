use std::{collections::HashMap, net::SocketAddr, time::Duration};

use tokio::{
    io::{BufReader, BufWriter},
    net::{TcpListener, TcpStream},
    sync::mpsc,
    task::JoinHandle,
};

use crate::{
    network::{
        wire::{self, ControlMessage, Handshake, Message},
        ConnectionDirection,
    },
    Properties,
};

use super::{Channel, ChannelEvents, ChannelFactory, ChannelMessage};

const TCP_MAGIC_NUMBER: i16 = 0x4505;
const PROP_MAGIC_NUMBER: &'static str = "magic_number";
const PROP_LISTEN_ADDR: &'static str = "listen_address";
const HEARTBEAT_INTERVAL: Duration = Duration::SECOND;

/// If more than this number of events get queued up then we assume there is something wrong with
/// the connection and terminate it.
const MAX_CONNECTION_QUEUED_EVENTS: usize = 512;

type ConnKey = (SocketAddr, ConnectionDirection);
type EventSender<M> = mpsc::UnboundedSender<Event<M>>;
type EventReceiver<M> = mpsc::UnboundedReceiver<Event<M>>;
type CommandSender<M> = mpsc::Sender<Command<M>>;
type CommandReceiver<M> = mpsc::Receiver<Command<M>>;

pub struct TcpChannelFactory;

pub struct TcpChannel<M> {
    /// Handle to the main task
    handle: JoinHandle<()>,
    sender: EventSender<M>,
}

#[derive(Debug)]
pub struct TcpChannelParams {
    pub listen_addr: SocketAddr,
}

impl ChannelFactory for TcpChannelFactory {
    type Channel<M: ChannelMessage> = TcpChannel<M>;

    fn create<E, M>(&self, properties: Properties, events: E) -> std::io::Result<Self::Channel<M>>
    where
        E: ChannelEvents<M>,
        M: ChannelMessage,
    {
        let listen_addr = properties
            .get_addr(PROP_LISTEN_ADDR)
            .expect("tcp channel required listen address")
            .expect("invalid listen address");

        let (task_data, sender) = MainTaskData::new(events, listen_addr);
        let handle = tokio::spawn(main_task(task_data));

        Ok(TcpChannel { handle, sender })
    }
}

impl<M> Drop for TcpChannel<M> {
    fn drop(&mut self) {
        self.handle.abort();
    }
}

impl<M> Channel<M> for TcpChannel<M>
where
    M: ChannelMessage,
{
    fn connect(&self, addr: SocketAddr) {
        self.send_event(Event::OpenConnection(addr));
    }

    fn disconnect(&self, addr: SocketAddr) {
        self.send_event(Event::CloseConnect(addr));
    }

    fn send(&self, host: SocketAddr, direction: ConnectionDirection, message: M) {
        self.send_event(Event::SendMessage {
            host,
            direction,
            message,
        })
    }
}

impl<M> TcpChannel<M> {
    fn send_event(&self, event: Event<M>) {
        if self.sender.send(event).is_err() {
            panic!("Receiver should not be dropped while the channel is alive");
        }
    }
}

impl From<TcpChannelParams> for Properties {
    fn from(params: TcpChannelParams) -> Self {
        let mut props = Properties::new();
        props.insert_addr(PROP_LISTEN_ADDR, params.listen_addr);
        props
    }
}

/// Event processed by the main channel task
#[derive(Debug)]
enum Event<M> {
    OpenConnection(SocketAddr),
    CloseConnect(SocketAddr),
    SendMessage {
        host: SocketAddr,
        direction: ConnectionDirection,
        message: M,
    },
    ConnectionOpened(SocketAddr),
    ConnectionAccepted(SocketAddr, TcpStream),
    HandshakeComplete(SocketAddr, ConnectionDirection, SocketAddr),
    ConnectionFailed(SocketAddr),
    ConnectionClosed(SocketAddr, ConnectionDirection),
}

/// Command processed by each individual connection task
#[derive(Debug)]
enum Command<M> {
    SendMessage(M),
    Close,
}

fn send_event<M>(s: &EventSender<M>, event: Event<M>) {
    if s.send(event).is_err() {
        panic!("Event receiver should not be dropped while connection task is alive");
    }
}

struct Connection<M> {
    _host: SocketAddr,
    _direction: ConnectionDirection,
    listen_addr: Option<SocketAddr>,
    sender: CommandSender<M>,
}

struct MainTaskData<E, M> {
    events: E,
    listen_addr: SocketAddr,
    sender: EventSender<M>,
    receiver: EventReceiver<M>,
    connections: HashMap<ConnKey, Connection<M>>,
}

impl<E, M> MainTaskData<E, M>
where
    E: ChannelEvents<M>,
    M: ChannelMessage,
{
    fn new(events: E, listen_addr: SocketAddr) -> (Self, EventSender<M>) {
        let (sender, receiver) = mpsc::unbounded_channel();
        tokio::spawn(listen_task(sender.clone(), listen_addr));
        let ret_sender = sender.clone();
        (
            Self {
                events,
                listen_addr,
                sender,
                receiver,
                connections: Default::default(),
            },
            ret_sender,
        )
    }

    fn open_connection(&mut self, host: SocketAddr) {
        let key = (host, ConnectionDirection::Outgoing);
        if self.connections.contains_key(&key) {
            log::warn!("Attempt to open connection to {key:?} but one already exists");
            return;
        }

        let (cmd_sender, cmd_receiver) = mpsc::channel(MAX_CONNECTION_QUEUED_EVENTS);
        tokio::spawn(connect_task(
            self.sender.clone(),
            cmd_receiver,
            self.events.clone(),
            host,
            self.listen_addr,
        ));

        self.connections.insert(
            key,
            Connection {
                _host: host,
                _direction: ConnectionDirection::Outgoing,
                listen_addr: None,
                sender: cmd_sender,
            },
        );
    }

    fn close_connection(&mut self, host: SocketAddr) {
        let conn = match self.connections.get(&(host, ConnectionDirection::Outgoing)) {
            Some(conn) => conn,
            None => {
                log::warn!("Attempt to close connection to {host} but one does not exist");
                return;
            }
        };
        if conn.sender.try_send(Command::Close).is_err() {
            log::error!("Connection task has already stopped");
        }
    }

    fn connection_opened(&mut self, host: SocketAddr) {
        self.events
            .connection_up(host, ConnectionDirection::Outgoing);
    }

    fn connection_accepted(&mut self, host: SocketAddr, stream: TcpStream) {
        let key = (host, ConnectionDirection::Incoming);
        if self.connections.contains_key(&key) {
            log::warn!("Attempt to accept connection from {key:?} but one already exists");
            return;
        }

        let (cmd_sender, cmd_receiver) = mpsc::channel(MAX_CONNECTION_QUEUED_EVENTS);
        tokio::spawn(connection_task(
            self.sender.clone(),
            cmd_receiver,
            self.events.clone(),
            host,
            stream,
            ConnectionDirection::Incoming,
            self.listen_addr,
        ));

        self.connections.insert(
            key,
            Connection {
                _host: host,
                _direction: ConnectionDirection::Incoming,
                listen_addr: None,
                sender: cmd_sender,
            },
        );

        self.events
            .connection_up(host, ConnectionDirection::Incoming);
    }

    fn handshake_complete(
        &mut self,
        host: SocketAddr,
        direction: ConnectionDirection,
        listen_addr: SocketAddr,
    ) {
        let key = (host, direction);
        match self.connections.get_mut(&key) {
            Some(conn) => conn.listen_addr = Some(listen_addr),
            None => {
                log::warn!("Handshake complete for {key:?} but connection does not exist");
                return;
            }
        };
    }

    fn connection_failed(&mut self, addr: SocketAddr) {
        let key = (addr, ConnectionDirection::Outgoing);
        if self.connections.contains_key(&key) {
            return;
        }
        self.events
            .connection_failed(addr, ConnectionDirection::Outgoing);
    }

    fn connection_closed(&mut self, addr: SocketAddr, direction: ConnectionDirection) {
        let key = (addr, direction);
        self.connections
            .remove(&key)
            .expect("connection must exist");
        self.events.connection_down(addr, direction);
    }

    fn send_message(&mut self, host: SocketAddr, direction: ConnectionDirection, message: M) {
        let key = (host, direction);
        let conn = match self.connections.get(&key) {
            Some(conn) => conn,
            None => {
                log::error!("Attempt to send message to {host} but no connection exists");
                return;
            }
        };

        if conn.sender.try_send(Command::SendMessage(message)).is_err() {
            panic!("Command receiver should not be dropped before the connection itself");
        }
    }
}

async fn main_task<E, M>(mut data: MainTaskData<E, M>)
where
    E: ChannelEvents<M>,
    M: ChannelMessage,
{
    while let Some(event) = data.receiver.recv().await {
        match event {
            Event::OpenConnection(host) => data.open_connection(host),
            Event::CloseConnect(host) => data.close_connection(host),
            Event::SendMessage {
                host,
                direction,
                message,
            } => data.send_message(host, direction, message),
            Event::ConnectionOpened(host) => data.connection_opened(host),
            Event::ConnectionAccepted(host, stream) => data.connection_accepted(host, stream),
            Event::HandshakeComplete(host, direction, listen_addr) => {
                data.handshake_complete(host, direction, listen_addr)
            }
            Event::ConnectionFailed(host) => data.connection_failed(host),
            Event::ConnectionClosed(host, direction) => data.connection_closed(host, direction),
        }
    }
}

async fn listen_task<M>(sender: EventSender<M>, listen_addr: SocketAddr) {
    let listener = match TcpListener::bind(listen_addr).await {
        Ok(listener) => listener,
        Err(e) => {
            log::error!("Failed to bind tcp listener to {listen_addr}: {e}");
            return;
        }
    };

    loop {
        let (stream, host) = match listener.accept().await {
            Ok(v) => v,
            Err(e) => {
                log::error!("Failed to accept tcp connection: {e}");
                continue;
            }
        };

        send_event(&sender, Event::ConnectionAccepted(host, stream));
    }
}

async fn connect_task<E, M>(
    sender: EventSender<M>,
    receiver: CommandReceiver<M>,
    events: E,
    addr: SocketAddr,
    local_listen_address: SocketAddr,
) where
    E: ChannelEvents<M>,
    M: ChannelMessage,
{
    match TcpStream::connect(addr).await {
        Ok(stream) => {
            send_event(&sender, Event::ConnectionOpened(addr));
            connection_task(
                sender,
                receiver,
                events,
                addr,
                stream,
                ConnectionDirection::Outgoing,
                local_listen_address,
            )
            .await;
        }
        Err(e) => {
            send_event(&sender, Event::ConnectionFailed(addr));
            log::error!("TCP Failed to connect to {addr}: {e}");
        }
    }
}

async fn connection_task<E, M>(
    sender: EventSender<M>,
    mut receiver: CommandReceiver<M>,
    events: E,
    addr: SocketAddr,
    stream: TcpStream,
    direction: ConnectionDirection,
    local_listen_address: SocketAddr,
) where
    E: ChannelEvents<M>,
    M: ChannelMessage,
{
    let result = connection_task_helper(
        sender.clone(),
        receiver,
        events,
        addr,
        stream,
        direction,
        local_listen_address,
    )
    .await;
    if let Some(err) = result.err() {
        log::error!("TCP connection to {addr} failed: {err}");
    }
    let _ = sender.send(Event::ConnectionClosed(addr, direction));
}

async fn connection_task_helper<E, M>(
    sender: EventSender<M>,
    mut receiver: CommandReceiver<M>,
    events: E,
    addr: SocketAddr,
    stream: TcpStream,
    direction: ConnectionDirection,
    local_listen_address: SocketAddr,
) -> std::io::Result<()>
where
    E: ChannelEvents<M>,
    M: ChannelMessage,
{
    enum LoopEv<M: ChannelMessage> {
        Command(Command<M>),
        Message(Message<M>),
        Heartbeat,
    }

    let (reader, writer) = stream.into_split();
    let (reader, writer) = (BufReader::new(reader), BufWriter::new(writer));
    let (mut encoder, mut decoder) = (wire::Encoder::new(writer), wire::Decoder::new(reader));

    // Handshake
    let mut properties = Properties::default();
    properties.insert_i16(PROP_MAGIC_NUMBER, TCP_MAGIC_NUMBER);
    properties.insert_addr(PROP_LISTEN_ADDR, local_listen_address);

    let handshake = Handshake::new(properties);
    let remote_properties = match direction {
        ConnectionDirection::Incoming => {
            // just assume their handshake is correct, dont have time for this right now.
            let hs = decoder.decode::<M>().await.unwrap();
            encoder
                .control(&ControlMessage::SecondHandshake(handshake))
                .await?;
            match hs {
                Message::Control(ControlMessage::FirstHandshake(hs)) => hs.properties,
                _ => panic!("Expected first handshake"),
            }
        }
        ConnectionDirection::Outgoing => {
            encoder
                .control(&ControlMessage::FirstHandshake(handshake))
                .await?;
            let hs = decoder.decode::<M>().await?;
            match hs {
                Message::Control(ControlMessage::SecondHandshake(hs)) => hs.properties,
                _ => panic!("Expected second handshake"),
            }
        }
    };

    log::trace!("Handshake complete with {addr:?} {remote_properties:#?}");
    let listen_addr = match direction {
        ConnectionDirection::Incoming => remote_properties
            .get_addr(PROP_LISTEN_ADDR)
            .unwrap()
            .unwrap(),
        ConnectionDirection::Outgoing => addr,
    };

    send_event(
        &sender,
        Event::HandshakeComplete(addr, direction, listen_addr),
    );

    let mut heartbeat_ticker = tokio::time::interval(HEARTBEAT_INTERVAL);

    // Exchange messages
    loop {
        let ev = tokio::select! {
            Some(cmd) = receiver.recv() => LoopEv::Command(cmd),
            Ok(msg) = decoder.decode() => LoopEv::Message(msg),
            _ = heartbeat_ticker.tick() => LoopEv::Heartbeat,
        };

        match ev {
            LoopEv::Command(cmd) => match cmd {
                Command::SendMessage(message) => {
                    encoder.application(&message).await?;
                }
                Command::Close => break,
            },
            LoopEv::Message(msg) => match msg {
                Message::Control(msg) => match msg {
                    wire::ControlMessage::Heartbeat => {} // Ignore heartbeats for now
                    _ => panic!("unexpetect control message: {msg:?}"),
                },
                // voulez-vouz ahaa
                Message::Application(msg) => events.received_message(listen_addr, direction, msg),
            },
            LoopEv::Heartbeat => {
                encoder.control(&ControlMessage::Heartbeat).await?;
            }
        }
    }

    Ok(())
}
