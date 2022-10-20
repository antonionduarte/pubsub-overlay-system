use std::net::SocketAddr;

use crate::Properties;

use super::{ConnectionDirection, Deserialize, Serialize};

pub mod tcp;

pub trait ChannelMessage: Send + Sync + Serialize + Deserialize + 'static {}

impl<T: Send + Sync + Serialize + Deserialize + 'static> ChannelMessage for T {}

pub trait ChannelEvents<T>: Clone + Send + Sync + 'static
where
    T: ChannelMessage,
{
    fn connection_up(&self, host: SocketAddr, direction: ConnectionDirection);
    fn connection_down(&self, host: SocketAddr, direction: ConnectionDirection);
    fn connection_failed(&self, host: SocketAddr, direction: ConnectionDirection);
    fn received_message(&self, host: SocketAddr, direction: ConnectionDirection, message: T);
}

pub trait ChannelFactory: Send + Sync + 'static {
    type Channel<M: ChannelMessage>: Channel<M>;

    fn create<E, M>(&self, properties: Properties, events: E) -> std::io::Result<Self::Channel<M>>
    where
        E: ChannelEvents<M>,
        M: ChannelMessage;
}

pub trait Channel<T>: Send + Sync + 'static
where
    T: ChannelMessage,
{
    fn connect(&self, host: SocketAddr);

    fn disconnect(&self, host: SocketAddr);

    fn send(&self, host: SocketAddr, direction: ConnectionDirection, message: T);
}
