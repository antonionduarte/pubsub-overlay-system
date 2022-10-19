use std::{net::SocketAddr, sync::Arc};

use bytes::Bytes;

use crate::{
    protocol::{ProtocolID, ProtocolMessageID},
    wire::BabelMessage,
    Properties,
};

pub mod tcp;

pub(crate) type ArcChannel = Arc<dyn Channel>;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum ConnectionDirection {
    Incoming,
    Outgoing,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum ConnectionEventKind {
    ConnectionUp,
    ConnectionDown,
    ConnectionFailed,
}

#[derive(Debug, Clone)]
pub struct ConnectionEvent {
    pub remote_addr: SocketAddr,
    pub direction: ConnectionDirection,
    pub kind: ConnectionEventKind,
}

pub trait ChannelEvents: Clone + Send + Sync + 'static {
    fn connection(&self, event: ConnectionEvent);
    // TODO: direction
    fn message(&self, source: SocketAddr, message: BabelMessage);
}

pub trait ChannelFactory: Send + Sync + 'static {
    type Channel: Channel;

    fn create<E>(&self, properties: Properties, events: E) -> std::io::Result<Self::Channel>
    where
        E: ChannelEvents;
}

pub trait Channel: Send + Sync + 'static {
    fn connect(&self, addr: SocketAddr);

    fn disconnect(&self, addr: SocketAddr);

    // TODO: direction, BabelMessage
    fn send(
        &self,
        addr: SocketAddr,
        source_protocol: ProtocolID,
        target_protocol: ProtocolID,
        message_id: ProtocolMessageID,
        message: Bytes,
    );
}

impl std::fmt::Display for ConnectionDirection {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let display = match self {
            ConnectionDirection::Incoming => "Incoming",
            ConnectionDirection::Outgoing => "Outgoing",
        };
        f.write_str(display)
    }
}
