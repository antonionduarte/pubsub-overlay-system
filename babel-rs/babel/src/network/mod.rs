use std::net::SocketAddr;

use bytes::{Buf, BufMut, Bytes};

use crate::{
    protocol::{ProtocolID, ProtocolMessageID},
    ChannelID,
};

pub mod channel;
pub mod wire;

mod serde_impls;
mod service;

pub(crate) use service::{NetworkService, NetworkServiceBuilder};

pub trait Serialize {
    fn serialize<B>(&self, buf: B) -> std::io::Result<()>
    where
        B: BufMut;
}

pub trait Deserialize: Sized {
    fn deserialize<B>(buf: B) -> std::io::Result<Self>
    where
        B: Buf;
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum ConnectionDirection {
    Incoming,
    Outgoing,
}

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct ConnectionID {
    pub channel_id: ChannelID,
    pub direction: ConnectionDirection,
    pub host: SocketAddr,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum ConnectionEventKind {
    ConnectionUp,
    ConnectionDown,
    ConnectionFailed,
}

#[derive(Debug, Clone)]
pub struct ConnectionEvent {
    pub connection: ConnectionID,
    pub kind: ConnectionEventKind,
}

#[derive(Debug)]
pub(crate) struct ReceivedMessage {
    pub(crate) connection: ConnectionID,
    pub(crate) _source_protocol: ProtocolID,
    pub(crate) message_id: ProtocolMessageID,
    pub(crate) payload: Bytes,
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

impl std::fmt::Display for ConnectionID {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(
            f,
            "ConnectionID {{ channel_id: {:?}, direction: {}, host: {} }}",
            self.channel_id, self.direction, self.host
        )
    }
}

impl ConnectionID {
    pub fn new(channel_id: ChannelID, host: SocketAddr, direction: ConnectionDirection) -> Self {
        Self {
            channel_id,
            direction,
            host,
        }
    }

    pub fn with_channel_id(&self, channel_id: ChannelID) -> Self {
        Self {
            channel_id,
            direction: self.direction,
            host: self.host,
        }
    }

    pub fn with_direction(&self, direction: ConnectionDirection) -> Self {
        Self {
            channel_id: self.channel_id,
            direction,
            host: self.host,
        }
    }

    pub fn with_host(&self, host: SocketAddr) -> Self {
        Self {
            channel_id: self.channel_id,
            direction: self.direction,
            host,
        }
    }
}
