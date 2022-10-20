use crate::{
    network::{ConnectionEvent, ConnectionID, Deserialize, Serialize},
    TimerID,
};

mod context;
pub use context::*;
mod executor;
pub(crate) use executor::*;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub struct ProtocolID(pub(crate) i16);

#[derive(Debug, Clone, PartialEq, Eq, Hash)]
pub struct ProtocolMessageID(pub(crate) i16);

pub trait ProtocolMessage: Serialize + Deserialize + Send + 'static {
    const ID: ProtocolMessageID;
}

pub type ProtocolMessageHandler<P, M> = fn(&mut P, Context<P>, ConnectionID, M);

pub type ProtocolIpcHandler<P, M> = fn(&mut P, Context<P>, ProtocolID, &M);

pub trait Protocol: Send + Sized + 'static {
    const ID: ProtocolID;
    const NAME: &'static str;

    #[allow(unused_variables)]
    fn setup(&mut self, ctx: SetupContext<Self>) {}

    #[allow(unused_variables)]
    fn init(&mut self, ctx: Context<Self>) {}

    #[allow(unused_variables)]
    fn on_timer(&mut self, ctx: Context<Self>, timer_id: TimerID) {}

    #[allow(unused_variables)]
    fn on_connection_event(&mut self, ctx: Context<Self>, event: ConnectionEvent) {}
}

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
