use std::{net::SocketAddr, time::Duration};

use crate::{
    ipc::{IpcService, Notification, Reply, Request},
    network::NetworkService,
    timer::TimerService,
    ChannelID, Properties, TimerID,
};

use super::{
    IpcDispatcher, MessageDispatcher, Protocol, ProtocolID, ProtocolIpcHandler, ProtocolMessage,
    ProtocolMessageHandler,
};

pub struct SetupContext<'p, P> {
    pub(super) message_dispatcher: &'p mut MessageDispatcher<P>,
    pub(super) ipc_dispatcher: &'p mut IpcDispatcher<P>,
}

impl<'p, P> SetupContext<'p, P>
where
    P: Protocol,
{
    pub(super) fn new(
        message_dispatcher: &'p mut MessageDispatcher<P>,
        ipc_dispatcher: &'p mut IpcDispatcher<P>,
    ) -> Self {
        Self {
            message_dispatcher,
            ipc_dispatcher,
        }
    }

    pub fn message_handler<M: ProtocolMessage>(&mut self, handler: ProtocolMessageHandler<P, M>) {
        self.message_dispatcher.register(handler);
    }

    pub fn request_handler<R: Request>(&mut self, handler: ProtocolIpcHandler<P, R>) {
        self.ipc_dispatcher.register_request(handler);
    }

    pub fn reply_handler<R: Reply>(&mut self, handler: ProtocolIpcHandler<P, R>) {
        self.ipc_dispatcher.register_reply(handler);
    }

    pub fn notification_handler<N: Notification>(&mut self, handler: ProtocolIpcHandler<P, N>) {
        self.ipc_dispatcher.register_notification(handler);
    }
}

pub struct Context<'p, P> {
    pub(super) default_channel: &'p mut Option<ChannelID>,
    pub(super) timer_service: &'p TimerService,
    pub(super) network_service: &'p NetworkService,
    pub(super) ipc_service: &'p IpcService,
    pub(super) protocol: std::marker::PhantomData<P>,
}

impl<'p, P> Context<'p, P>
where
    P: Protocol,
{
    pub(super) fn new(
        default_channel: &'p mut Option<ChannelID>,
        timer_service: &'p TimerService,
        network_service: &'p NetworkService,
        ipc_service: &'p IpcService,
    ) -> Self {
        Self {
            default_channel,
            timer_service,
            network_service,
            ipc_service,
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
        // TODO: direction
        addr: SocketAddr,
        message: &impl ProtocolMessage,
    ) {
        self.network_service
            .send_message(channel_id, addr, P::ID, message)
    }

    pub fn send_request<R: Request>(&mut self, protocol_id: ProtocolID, request: R) {
        self.ipc_service.request(P::ID, protocol_id, request);
    }

    pub fn send_reply<R: Reply>(&mut self, protocol_id: ProtocolID, reply: R) {
        self.ipc_service.reply(P::ID, protocol_id, reply);
    }

    pub fn send_notification<N: Notification>(&mut self, notification: N) {
        self.ipc_service.notification(P::ID, notification);
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
