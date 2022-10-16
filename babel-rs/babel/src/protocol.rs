use std::{cell::RefCell, collections::HashMap, net::SocketAddr, time::Duration};

use bytes::Bytes;

use crate::{
    channel::ConnectionEvent,
    mailbox::MailboxReceiver,
    service::{ipc::IpcService, network::NetworkService, timer::TimerService},
    ChannelID, Deserialize, Properties, Serialize, TimerID,
};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct ProtocolID(pub(crate) i16);

impl From<i16> for ProtocolID {
    fn from(id: i16) -> Self {
        Self(id)
    }
}

impl std::fmt::Display for ProtocolID {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "ProtocolID[{}]", self.0)
    }
}

impl ProtocolID {
    pub const fn new(id: i16) -> Self {
        Self(id)
    }

    pub const fn message(&self, offset: i16) -> ProtocolMessageID {
        ProtocolMessageID(self.0 + offset)
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct ProtocolMessageID(pub(crate) i16);

impl From<i16> for ProtocolMessageID {
    fn from(id: i16) -> Self {
        Self(id)
    }
}

impl ProtocolMessageID {
    pub const fn new(id: i16) -> Self {
        Self(id)
    }
}

pub trait ProtocolMessage: Serialize + Deserialize + Send + 'static {
    const ID: ProtocolMessageID;
}

pub type ProtocolMessageHandler<P, M> = fn(&mut P, Context<P>, ChannelID, SocketAddr, M);

type MessageDeserializer<P> =
    Box<dyn for<'p> Fn(&'p mut P, Context<'p, P>, ChannelID, SocketAddr, Bytes) + 'static>;
type MessageDeserializers<P> = RefCell<HashMap<ProtocolMessageID, MessageDeserializer<P>>>;

pub trait Request {}

pub trait Reply {}

pub trait Protocol: Send + Sized + 'static {
    const ID: ProtocolID;
    const NAME: &'static str;

    #[allow(unused_variables)]
    fn init(&mut self, ctx: Context<Self>) {}

    #[allow(unused_variables)]
    fn on_timer(&mut self, ctx: Context<Self>, timer_id: TimerID) {}

    #[allow(unused_variables)]
    fn on_connection_event(
        &mut self,
        ctx: Context<Self>,
        channel_id: ChannelID,
        event: ConnectionEvent,
    ) {
    }
}

pub struct Context<'p, P> {
    default_channel: &'p mut Option<ChannelID>,
    timer_service: &'p TimerService,
    network_service: &'p NetworkService,
    ipc_service: &'p IpcService,
    deserializers: &'p MessageDeserializers<P>,
    protocol: std::marker::PhantomData<P>,
}

impl<'p, P> Context<'p, P>
where
    P: Protocol,
{
    fn new(
        default_channel: &'p mut Option<ChannelID>,
        timer_service: &'p TimerService,
        network_service: &'p NetworkService,
        ipc_service: &'p IpcService,
        deserializers: &'p MessageDeserializers<P>,
    ) -> Self {
        Self {
            default_channel,
            timer_service,
            network_service,
            ipc_service,
            deserializers,
            protocol: std::marker::PhantomData,
        }
    }

    pub fn create_channel(&mut self, name: &str, properties: impl Into<Properties>) -> ChannelID {
        let properties = properties.into();
        let channel_id = self.network_service.create_channel(P::ID, name, properties);
        if self.default_channel.is_none() {
            *self.default_channel = Some(channel_id);
        }
        channel_id
    }

    pub fn connect(&mut self, addr: SocketAddr) {
        match *self.default_channel {
            Some(channel_id) => self.connect_with(channel_id, addr),
            None => log::error!(
                "Attempt to connect using default channel but there is not default channel"
            ),
        }
    }

    pub fn connect_with(&mut self, channel_id: ChannelID, addr: SocketAddr) {
        self.network_service.connect(channel_id, addr)
    }

    pub fn disconnect(&mut self, addr: SocketAddr) {
        match *self.default_channel {
            Some(channel_id) => self.disconnect_with(channel_id, addr),
            None => log::error!(
                "Attempt to disconnect using default channel but there is not default channel"
            ),
        }
    }

    pub fn disconnect_with(&mut self, channel_id: ChannelID, addr: SocketAddr) {
        self.network_service.disconnect(channel_id, addr)
    }

    pub fn register_message_handler<M: ProtocolMessage>(
        &mut self,
        handler: ProtocolMessageHandler<P, M>,
    ) {
        let mut deserializers = self.deserializers.borrow_mut();
        if deserializers.contains_key(&M::ID) {
            panic!("Attempt to register a deserializer for the same message twice");
        }
        let deserializer = Box::new(
            for<'a> move |p: &'a mut P,
                          ctx: Context<'a, P>,
                          cid: ChannelID,
                          addr: SocketAddr,
                          buf: Bytes|
                          -> () {
                let msg = M::deserialize(buf).unwrap();
                (handler)(p, ctx, cid, addr, msg);
            },
        );
        deserializers.insert(M::ID, deserializer);
    }

    pub fn send_message(&mut self, addr: SocketAddr, message: &impl ProtocolMessage) {
        match *self.default_channel {
            Some(channel_id) => self.send_message_with(channel_id, addr, message),
            None => log::error!(
                "Attempt to send message using default channel but there is not default channel"
            ),
        }
    }

    pub fn send_message_with(
        &mut self,
        channel_id: ChannelID,
        addr: SocketAddr,
        message: &impl ProtocolMessage,
    ) {
        self.network_service
            .send_message(channel_id, addr, P::ID, message)
    }

    pub fn send_request<R: Request>(&mut self, protocol_id: ProtocolID, request: R) {
        todo!()
    }

    pub fn send_reply<R: Reply>(&mut self, protocol_id: ProtocolID, reply: R) {
        todo!()
    }

    pub fn create_timer(&mut self, delay: Duration) -> TimerID {
        return self.timer_service.create_timer(P::ID, delay);
    }

    pub fn create_periodic_timer(&mut self, delay: Duration, interval: Duration) -> TimerID {
        return self
            .timer_service
            .create_periodic_timer(P::ID, delay, interval);
    }

    pub fn cancel_timer(&mut self, timer_id: TimerID) {
        self.timer_service.cancel_timer(timer_id);
    }
}

pub(crate) struct ProtocolExecutor<P: Protocol> {
    protocol: P,
    default_channel: Option<ChannelID>,
    mailbox: MailboxReceiver,
    timers: TimerService,
    network: NetworkService,
    ipc: IpcService,
    deserializers: MessageDeserializers<P>,
}

impl<P: Protocol> ProtocolExecutor<P> {
    pub fn new(
        protocol: P,
        mailbox: MailboxReceiver,
        timers: TimerService,
        network: NetworkService,
        ipc: IpcService,
    ) -> Self {
        Self {
            protocol,
            default_channel: Default::default(),
            mailbox,
            timers,
            network,
            ipc,
            deserializers: Default::default(),
        }
    }

    pub fn run(&mut self) {
        log::trace!("Calling protocol {} init", P::NAME);
        let (protocol, context) = self.create_context();
        protocol.init(context);
        log::info!("Protocol {} started", P::NAME);

        while let Some(event) = self.mailbox.recv() {
            match event {
                crate::mailbox::MailboxEvent::TimerExpired(timer_id) => {
                    log::trace!("Calling protocol {} on_timer({:?})", P::NAME, timer_id);
                    let (protocol, context) = self.create_context();
                    protocol.on_timer(context, timer_id);
                }
                // take now or leave it
                crate::mailbox::MailboxEvent::MessageReceived(channel_id, addr, msg_id, msg) => {
                    let (protocol, context) = self.create_context();
                    let deserializers = context.deserializers.borrow();
                    if let Some(deserializer) = deserializers.get(&msg_id) {
                        (deserializer)(protocol, context, channel_id, addr, msg);
                    } else {
                        log::warn!(
                            "Received message with id {msg_id:?} but no deserializer is registered"
                        );
                    }
                }
                crate::mailbox::MailboxEvent::ConnectionEvent(channel_id, event) => {
                    let (protocol, context) = self.create_context();
                    protocol.on_connection_event(context, channel_id, event);
                }
            }
        }

        log::info!("Protocol {} stopped", P::NAME);
    }

    fn create_context(&mut self) -> (&mut P, Context<P>) {
        let context = Context::new(
            &mut self.default_channel,
            &self.timers,
            &self.network,
            &self.ipc,
            &self.deserializers,
        );
        (&mut self.protocol, context)
    }
}
