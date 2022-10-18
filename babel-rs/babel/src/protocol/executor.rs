use crate::{
    ipc::IpcService,
    mailbox::MailboxReceiver,
    service::{network::NetworkService, timer::TimerService},
    ChannelID,
};

use super::{Context, MessageDeserializers, Protocol};

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
                crate::mailbox::MailboxEvent::RequestReceived(request) => todo!(),
                crate::mailbox::MailboxEvent::ReplyReceived(reply) => todo!(),
                crate::mailbox::MailboxEvent::NotificationReceived(notification) => todo!(),
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
