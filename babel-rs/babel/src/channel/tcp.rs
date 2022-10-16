use std::{collections::HashMap, net::SocketAddr, str::FromStr};

use bytes::Bytes;
use tokio::{
    io::{BufReader, BufWriter},
    net::{TcpListener, TcpStream},
    sync::mpsc,
    task::JoinHandle,
};

use crate::{
    protocol::{ProtocolID, ProtocolMessageID},
    wire::{self, BabelMessage, ControlMessage, Handshake, Message},
    Properties,
};

use super::{
    Channel, ChannelEvents, ChannelFactory, ConnectionDirection, ConnectionEvent,
    ConnectionEventKind,
};

const TCP_MAGIC_NUMBER: i16 = 0x4505;

pub struct TcpChannelFactory;

pub struct TcpChannel {
    /// Handle to the main task
    handle: JoinHandle<()>,
    sender: EventSender,
}

impl ChannelFactory for TcpChannelFactory {
    type Channel = TcpChannel;

    fn create<E>(&self, properties: Properties, events: E) -> std::io::Result<Self::Channel>
    where
        E: ChannelEvents,
    {
        let listen_addr = properties
            .get_u16("port")
            .transpose()
            .map_err(std::io::Error::other)?
            .map(|p| SocketAddr::from_str(&format!("0.0.0.0:{p}")).unwrap());

        let (task_data, sender) = MainTaskData::new(events, listen_addr);
        let handle = tokio::spawn(main_task(task_data));

        Ok(TcpChannel { handle, sender })
    }
}

impl Drop for TcpChannel {
    fn drop(&mut self) {
        self.handle.abort();
    }
}

impl Channel for TcpChannel {
    fn connect(&self, addr: SocketAddr) {
        self.send_event(Event::OpenConnection(addr));
    }

    fn disconnect(&self, addr: SocketAddr) {
        self.send_event(Event::CloseConnect(addr));
    }

    fn send(
        &self,
        addr: SocketAddr,
        source_protocol: ProtocolID,
        target_protocol: ProtocolID,
        message_id: ProtocolMessageID,
        message: Bytes,
    ) {
        self.send_event(Event::SendMessage {
            destination: addr,
            source_protocol,
            target_protocol,
            message_id,
            message,
        })
    }
}

impl TcpChannel {
    fn send_event(&self, event: Event) {
        self.sender
            .send(event)
            .expect("Receiver should not be dropped while the channel is alive");
    }
}

/// If more than this number of events get queued up then we assume there is something wrong with
/// the connection and terminate it.
const MAX_CONNECTION_QUEUED_EVENTS: usize = 4096;

type EventSender = mpsc::UnboundedSender<Event>;
type EventReceiver = mpsc::UnboundedReceiver<Event>;
type CommandSender = mpsc::Sender<Command>;
type CommandReceiver = mpsc::Receiver<Command>;

/// Event processed by the main channel task
#[derive(Debug)]
enum Event {
    OpenConnection(SocketAddr),
    CloseConnect(SocketAddr),
    SendMessage {
        destination: SocketAddr,
        source_protocol: ProtocolID,
        target_protocol: ProtocolID,
        message_id: ProtocolMessageID,
        message: Bytes,
    },
    ConnectionOpened(SocketAddr, TcpStream, ConnectionDirection),
    ConnectionFailed(SocketAddr),
    ConnectionClosed(SocketAddr),
}

/// Command processed by each individual connection task
#[derive(Debug)]
enum Command {
    SendMessage {
        source_protocol: ProtocolID,
        target_protocol: ProtocolID,
        message_id: ProtocolMessageID,
        message: Bytes,
    },
}

fn send_event(s: &EventSender, event: Event) {
    s.send(event)
        .expect("Event receiver should not be dropped while connection task is alive");
}

struct Connection {
    address: SocketAddr,
    direction: ConnectionDirection,
    sender: CommandSender,
}

struct MainTaskData<E> {
    events: E,
    listen_addr: Option<SocketAddr>,
    sender: EventSender,
    receiver: EventReceiver,
    connections: HashMap<SocketAddr, Connection>,
}

impl<E> MainTaskData<E>
where
    E: ChannelEvents,
{
    fn new(events: E, listen_addr: Option<SocketAddr>) -> (Self, EventSender) {
        let (sender, receiver) = mpsc::unbounded_channel();
        if let Some(listen_addr) = listen_addr {
            tokio::spawn(listen_task(sender.clone(), listen_addr));
        }
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

    fn open_connection(&mut self, addr: SocketAddr) {
        if self.connections.contains_key(&addr) {
            log::warn!("Attempt to open connection to {addr} but one already exists");
            return;
        }
        tokio::spawn(connect_task(self.sender.clone(), addr));
    }

    fn close_connection(&mut self, addr: SocketAddr) {
        let conn = match self.connections.remove(&addr) {
            Some(conn) => conn,
            None => {
                log::warn!("Attempt to close connection to {addr} but one does not exist");
                return;
            }
        };
        self.events.emit(ConnectionEvent {
            remote_addr: addr,
            direction: conn.direction,
            kind: ConnectionEventKind::ConnectionDown,
        });
    }

    fn connection_opened(
        &mut self,
        addr: SocketAddr,
        stream: TcpStream,
        direction: ConnectionDirection,
    ) {
        if self.connections.contains_key(&addr) {
            log::warn!("Attempt to open double connection to {addr}, direction: {direction}");
            return;
        }
        let (cmd_sender, cmd_receiver) = mpsc::channel(MAX_CONNECTION_QUEUED_EVENTS);
        tokio::spawn(connection_task(
            self.sender.clone(),
            cmd_receiver,
            self.events.clone(),
            addr,
            stream,
            direction,
        ));
        self.connections.insert(
            addr,
            Connection {
                address: addr,
                direction,
                sender: cmd_sender,
            },
        );
    }

    fn send_message(
        &mut self,
        destination: SocketAddr,
        source_protocol: ProtocolID,
        target_protocol: ProtocolID,
        message_id: ProtocolMessageID,
        message: Bytes,
    ) {
        let conn = match self.connections.get(&destination) {
            Some(conn) => conn,
            None => {
                log::error!("Attempt to send message to {destination} but no connection exists");
                return;
            }
        };

        conn.sender
            .try_send(Command::SendMessage {
                source_protocol,
                target_protocol,
                message_id,
                message,
            })
            .unwrap();
    }

    fn connection_failed(&mut self, addr: SocketAddr) {
        if self.connections.contains_key(&addr) {
            return;
        }
        self.events.emit(ConnectionEvent {
            remote_addr: addr,
            direction: ConnectionDirection::Outgoing,
            kind: ConnectionEventKind::ConnectionFailed,
        });
    }

    fn connection_closed(&mut self, addr: SocketAddr) {
        let conn = self
            .connections
            .remove(&addr)
            .expect("connection must exist");
        self.events.emit(ConnectionEvent {
            remote_addr: addr,
            direction: conn.direction,
            kind: ConnectionEventKind::ConnectionDown,
        });
    }
}

async fn main_task<E>(mut data: MainTaskData<E>)
where
    E: ChannelEvents,
{
    while let Some(event) = data.receiver.recv().await {
        match event {
            Event::OpenConnection(addr) => data.open_connection(addr),
            Event::CloseConnect(addr) => data.close_connection(addr),
            Event::SendMessage {
                destination,
                source_protocol,
                target_protocol,
                message_id,
                message,
            } => data.send_message(
                destination,
                source_protocol,
                target_protocol,
                message_id,
                message,
            ),
            Event::ConnectionOpened(addr, stream, dir) => data.connection_opened(addr, stream, dir),
            Event::ConnectionFailed(addr) => data.connection_failed(addr),
            Event::ConnectionClosed(addr) => data.connection_closed(addr),
        }
    }
}

async fn listen_task(sender: EventSender, listen_addr: SocketAddr) {
    let listener = match TcpListener::bind(listen_addr).await {
        Ok(listener) => listener,
        Err(e) => {
            log::error!("Failed to bind tcp listener to {listen_addr}: {e}");
            return;
        }
    };

    loop {
        let (stream, addr) = match listener.accept().await {
            Ok(v) => v,
            Err(e) => {
                log::error!("Failed to accept tcp connection: {e}");
                continue;
            }
        };

        send_event(
            &sender,
            Event::ConnectionOpened(addr, stream, ConnectionDirection::Incoming),
        );
    }
}

async fn connect_task(sender: EventSender, addr: SocketAddr) {
    match TcpStream::connect(addr).await {
        Ok(stream) => send_event(
            &sender,
            Event::ConnectionOpened(addr, stream, ConnectionDirection::Outgoing),
        ),
        Err(e) => {
            send_event(&sender, Event::ConnectionFailed(addr));
            log::error!("TCP Failed to connect to {addr}: {e}");
        }
    }
}

async fn connection_task<E>(
    sender: EventSender,
    mut receiver: CommandReceiver,
    events: E,
    addr: SocketAddr,
    stream: TcpStream,
    direction: ConnectionDirection,
) where
    E: ChannelEvents,
{
    enum LoopEv {
        Command(Command),
        Message(Message<BabelMessage>),
    }

    let (reader, writer) = stream.into_split();
    let (reader, writer) = (BufReader::new(reader), BufWriter::new(writer));
    let (mut encoder, mut decoder) = (
        wire::Encoder::<_, BabelMessage>::new(writer),
        wire::Decoder::<_, BabelMessage>::new(reader),
    );

    // Handshake
    let mut properties = Properties::default();
    properties.insert_i16("magic_number", TCP_MAGIC_NUMBER);
    let handshake = Handshake::new(properties);
    match direction {
        ConnectionDirection::Incoming => {
            // just assume their handshake is correct, dont have time for this right now.
            decoder.decode().await.unwrap();
            encoder
                .control(&ControlMessage::SecondHandshake(handshake))
                .await
                .unwrap();
        }
        ConnectionDirection::Outgoing => {
            encoder
                .control(&ControlMessage::FirstHandshake(handshake))
                .await
                .unwrap();
            decoder.decode().await.unwrap(); // same crap, just assume its correct
        }
    }

    // Exchange messages
    loop {
        let ev = tokio::select! {
            Some(cmd) = receiver.recv() => LoopEv::Command(cmd),
            Ok(msg) = decoder.decode() => LoopEv::Message(msg),
        };

        match ev {
            LoopEv::Command(cmd) => match cmd {
                Command::SendMessage {
                    source_protocol,
                    target_protocol,
                    message_id,
                    message,
                } => {
                    encoder
                        .application(&BabelMessage {
                            source_protocol,
                            target_protocol,
                            message_id,
                            message,
                        })
                        .await
                        .unwrap();
                }
            },
            LoopEv::Message(msg) => match msg {
                Message::Control(msg) => match msg {
                    wire::ControlMessage::Heartbeat => {
                        encoder.control(&ControlMessage::Heartbeat).await.unwrap();
                        // just echo hearbeats for now
                        encoder.control(&ControlMessage::Heartbeat).await.unwrap();
                    }
                    _ => panic!("unexpetect control message: {msg:?}"),
                },
                // voulez-vouz ahaa
                Message::Application(msg) => events.message(addr, msg),
            },
        }
    }
}
