use std::{
    collections::HashMap,
    net::SocketAddr,
    ops::DerefMut,
    sync::{Arc, Mutex},
};

use bytes::BytesMut;
use slotmap::SlotMap;

use crate::{
    channel::{ArcChannel, ChannelEvents, ChannelFactory, ConnectionEvent},
    mailbox::{MailboxRouter, MailboxSender},
    protocol::{ProtocolID, ProtocolMessage},
    wire::BabelMessage,
    ChannelID, Properties,
};

type ArcEraseChannelFactory = Arc<dyn EraseChannelFactory>;

trait EraseChannelFactory: Send + Sync + 'static {
    fn create(
        &self,
        properties: Properties,
        events: NetworkServiceChannelEvents,
    ) -> std::io::Result<ArcChannel>;
}

#[derive(Clone)]
struct NetworkServiceChannelEvents {
    channel_id: ChannelID,
    // Mailbox of the protocol that created this channel.
    // That is the only protocol that can receive events.
    events_mailbox: MailboxSender,
    // Needed to route messages to protocols other than the
    // one that create this channel.
    router: MailboxRouter,
}

#[derive(Default)]
pub(crate) struct NetworkServiceBuilder {
    factories: HashMap<String, ArcEraseChannelFactory>,
}

struct NetworkServiceInner {
    router: MailboxRouter,
    factories: HashMap<String, ArcEraseChannelFactory>,
    channels: SlotMap<ChannelID, ArcChannel>,
}

#[derive(Clone)]
pub(crate) struct NetworkService(Arc<Mutex<NetworkServiceInner>>);

impl<C: ChannelFactory> EraseChannelFactory for C {
    fn create(
        &self,
        properties: Properties,
        events: NetworkServiceChannelEvents,
    ) -> std::io::Result<ArcChannel> {
        <Self as ChannelFactory>::create(self, properties, events)
            .map(|c| Arc::new(c) as ArcChannel)
    }
}

impl ChannelEvents for NetworkServiceChannelEvents {
    fn emit(&self, event: ConnectionEvent) {
        self.events_mailbox.connection_event(self.channel_id, event);
    }

    fn message(&self, source: SocketAddr, message: BabelMessage) {
        match self.router.get(message.target_protocol) {
            Some(sender) => {
                sender.message_received(
                    self.channel_id,
                    source,
                    message.message_id,
                    message.message,
                );
            }
            None => log::warn!(
                "Received message for protocol {:?} but that protocol does not exist",
                message.target_protocol
            ),
        }
    }
}

impl NetworkServiceChannelEvents {
    pub fn new(channel_id: ChannelID, sender: MailboxSender, router: MailboxRouter) -> Self {
        Self {
            channel_id,
            events_mailbox: sender,
            router,
        }
    }
}

impl NetworkServiceBuilder {
    pub fn register_channel<C: ChannelFactory>(&mut self, name: &str, factory: C) {
        if self.factories.contains_key(name) {
            panic!("Channel factory {} already registered", name);
        }
        self.factories.insert(name.to_string(), Arc::new(factory));
    }

    pub fn build(self, router: MailboxRouter) -> NetworkService {
        NetworkService::build(self, router)
    }
}

impl NetworkService {
    fn build(builder: NetworkServiceBuilder, router: MailboxRouter) -> Self {
        let inner = Arc::new(Mutex::new(NetworkServiceInner {
            router,
            factories: builder.factories,
            channels: Default::default(),
        }));
        Self(inner)
    }

    pub fn create_channel(
        &self,
        protocol_id: ProtocolID,
        name: &str,
        properties: Properties,
    ) -> ChannelID {
        let mut inner = self.0.lock().unwrap();
        let inner = inner.deref_mut();
        let factories = &inner.factories;
        let router = &inner.router;
        let channels = &mut inner.channels;

        let factory = match factories.get(name) {
            Some(factory) => factory,
            None => {
                log::error!("Attempt to create channel of unknown type: {name}");
                return Default::default();
            }
        };

        let events_router = router.clone();
        let sender = router
            .get(protocol_id)
            .expect("The protocol creating this channel should exist")
            .clone();

        let channel_id = channels.insert_with_key(move |channel_id| {
            match factory.create(
                properties,
                NetworkServiceChannelEvents::new(channel_id, sender, events_router),
            ) {
                Ok(channel) => channel,
                Err(e) => {
                    panic!("Failed to create channel {name}: {e}");
                }
            }
        });
        channel_id
    }

    pub fn connect(&self, channel_id: ChannelID, addr: SocketAddr) {
        let inner = self.0.lock().unwrap();
        let channel = match inner.channels.get(channel_id) {
            Some(channel) => channel,
            None => {
                log::error!("Attempt to connect on invalid channel");
                return;
            }
        };
        channel.connect(addr);
    }

    pub fn disconnect(&self, channel_id: ChannelID, addr: SocketAddr) {
        let inner = self.0.lock().unwrap();
        let channel = match inner.channels.get(channel_id) {
            Some(channel) => channel,
            None => {
                log::error!("Attempt to disconnect on invalid channel");
                return;
            }
        };
        channel.disconnect(addr);
    }

    pub fn send_message<M>(
        &self,
        channel_id: ChannelID,
        addr: SocketAddr,
        source_protocol: ProtocolID,
        message: &M,
    ) where
        M: ProtocolMessage,
    {
        let inner = self.0.lock().unwrap();
        let channel = match inner.channels.get(channel_id) {
            Some(channel) => channel,
            None => {
                log::error!("Attempt to send message on invalid channel");
                return;
            }
        };
        let mut buf = BytesMut::new();
        message.serialize(&mut buf).unwrap();
        channel.send(addr, source_protocol, source_protocol, M::ID, buf.freeze());
    }
}
