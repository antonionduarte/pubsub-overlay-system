use std::{
    any::{Any, TypeId},
    collections::HashMap,
};

use crate::{
    ipc::{IpcMessage, IpcService, Notification, Reply, Request},
    mailbox::{MailboxEvent, MailboxReceiver},
    network::{ConnectionEvent, NetworkService, ReceivedMessage},
    timer::TimerService,
    ChannelID, TimerID,
};

use super::{
    Context, Protocol, ProtocolID, ProtocolIpcHandler, ProtocolMessage, ProtocolMessageHandler,
    ProtocolMessageID, SetupContext,
};

type IpcHandler<P> = Box<dyn Fn(&mut P, Context<P>, ProtocolID, &dyn Any) + 'static>;

pub(super) struct IpcDispatcher<P> {
    request_handlers: HashMap<TypeId, IpcHandler<P>>,
    reply_handlers: HashMap<TypeId, IpcHandler<P>>,
    notification_handlers: HashMap<TypeId, IpcHandler<P>>,
}

impl<P> Default for IpcDispatcher<P> {
    fn default() -> Self {
        Self {
            request_handlers: HashMap::new(),
            reply_handlers: HashMap::new(),
            notification_handlers: HashMap::new(),
        }
    }
}

impl<P> IpcDispatcher<P>
where
    P: Protocol,
{
    pub(super) fn register_request<M>(&mut self, handler: ProtocolIpcHandler<P, M>)
    where
        M: Request,
    {
        self.request_handlers.insert(
            TypeId::of::<M>(),
            Box::new(move |p, ctx, src, msg| {
                let message = msg.downcast_ref().unwrap();
                handler(p, ctx, src, message);
            }),
        );
    }

    pub(super) fn register_reply<M>(&mut self, handler: ProtocolIpcHandler<P, M>)
    where
        M: Reply,
    {
        self.reply_handlers.insert(
            TypeId::of::<M>(),
            Box::new(move |p, ctx, src, msg| {
                let message = msg.downcast_ref().unwrap();
                handler(p, ctx, src, message);
            }),
        );
    }

    pub(super) fn register_notification<M>(&mut self, handler: ProtocolIpcHandler<P, M>)
    where
        M: Notification,
    {
        log::trace!(
            "registering notification handler for {} type id {:?}",
            std::any::type_name::<M>(),
            TypeId::of::<M>()
        );
        self.notification_handlers.insert(
            TypeId::of::<M>(),
            Box::new(move |p, ctx, src, msg| {
                let message = msg.downcast_ref().unwrap();
                handler(p, ctx, src, message);
            }),
        );
    }

    fn dispatch(&self, protocol: &mut P, context: Context<P>, message: IpcMessage) {
        let message_typeid = message.message_typeid();
        match message {
            IpcMessage::Request {
                source,
                destination,
                message: messsage,
            } => {
                debug_assert_ne!(source, P::ID);
                debug_assert_eq!(destination, P::ID);
                match self.request_handlers.get(&message_typeid) {
                    Some(handler) => handler(protocol, context, source, &*messsage),
                    None => log::warn!("No handler for request message: {:?}", messsage.type_id()),
                }
            }
            IpcMessage::Reply {
                source,
                destination,
                message: messsage,
            } => {
                debug_assert_ne!(source, P::ID);
                debug_assert_eq!(destination, P::ID);
                match self.reply_handlers.get(&message_typeid) {
                    Some(handler) => handler(protocol, context, source, &*messsage),
                    None => log::warn!("No handler for reply message: {:?}", messsage.type_id()),
                };
            }
            IpcMessage::Notification {
                source,
                message: messsage,
            } => match self.notification_handlers.get(&message_typeid) {
                Some(handler) => handler(protocol, context, source, &*messsage),
                None => log::trace!(
                    "No handler for notification message: {:?}",
                    messsage.type_id()
                ),
            },
        }
    }
}

type MessageHandler<P> = Box<dyn for<'p> Fn(&'p mut P, Context<'p, P>, ReceivedMessage) + 'static>;

pub(super) struct MessageDispatcher<P> {
    dispatchers: HashMap<ProtocolMessageID, MessageHandler<P>>,
}

impl<P> Default for MessageDispatcher<P> {
    fn default() -> Self {
        Self {
            dispatchers: HashMap::new(),
        }
    }
}

impl<P> MessageDispatcher<P>
where
    P: Protocol,
{
    pub(super) fn register<M>(&mut self, handler: ProtocolMessageHandler<P, M>)
    where
        M: ProtocolMessage,
    {
        if self.dispatchers.contains_key(&M::ID) {
            panic!("Attempt to register a deserializer for the same message twice");
        }
        let deserializer = Box::new(
            for<'a> move |p: &'a mut P, ctx: Context<'a, P>, message: ReceivedMessage| -> () {
                let msg = M::deserialize(message.payload).unwrap();
                (handler)(p, ctx, message.connection, msg);
            },
        );
        self.dispatchers.insert(M::ID, deserializer);
    }

    fn dispatch(&self, protocol: &mut P, context: Context<P>, message: ReceivedMessage) {
        match self.dispatchers.get(&message.message_id) {
            Some(handler) => handler(protocol, context, message),
            None => log::warn!("No handler for message: {:?}", message.message_id),
        }
    }
}

pub(crate) struct ProtocolExecutor<P: Protocol> {
    protocol: P,
    default_channel: Option<ChannelID>,
    mailbox: MailboxReceiver,
    timers: TimerService,
    network: NetworkService,
    ipc: IpcService,
    message_dispatcher: MessageDispatcher<P>,
    ipc_dispatcher: IpcDispatcher<P>,
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
            message_dispatcher: Default::default(),
            ipc_dispatcher: Default::default(),
        }
    }

    pub fn run(&mut self) {
        log::trace!("Calling protocol {} setup", P::NAME);
        let (protocol, context) = self.create_setup_context();
        protocol.setup(context);
        log::trace!("Calling protocol {} init", P::NAME);
        let (protocol, context) = self.create_context();
        protocol.init(context);
        log::info!("Protocol {} started", P::NAME);

        while let Some(event) = self.mailbox.recv() {
            match event {
                MailboxEvent::TimerExpired(timer_id) => self.on_timer_expired(timer_id),
                MailboxEvent::MessageReceived(message) => self.on_message_received(message),
                MailboxEvent::ConnectionEvent(event) => self.on_connection_event(event),
                MailboxEvent::IpcMessage(message) => self.on_ipc_message(message),
            }
        }

        log::info!("Protocol {} stopped", P::NAME);
    }

    fn on_timer_expired(&mut self, timer_id: TimerID) {
        log::trace!("Calling protocol {} on_timer({:?})", P::NAME, timer_id);
        let (protocol, context) = self.create_context();
        protocol.on_timer(context, timer_id);
    }

    fn on_message_received(&mut self, message: ReceivedMessage) {
        let (protocol, context, message_dispatcher, _) = self.create_context_with_dispatchers();
        message_dispatcher.dispatch(protocol, context, message);
    }

    fn on_connection_event(&mut self, event: ConnectionEvent) {
        let (protocol, context) = self.create_context();
        protocol.on_connection_event(context, event);
    }

    fn on_ipc_message(&mut self, message: IpcMessage) {
        let (protocol, context, _, ipc_dispatcher) = self.create_context_with_dispatchers();
        ipc_dispatcher.dispatch(protocol, context, message);
    }

    fn create_setup_context(&mut self) -> (&mut P, SetupContext<P>) {
        let context = SetupContext::new(&mut self.message_dispatcher, &mut self.ipc_dispatcher);
        (&mut self.protocol, context)
    }

    fn create_context_with_dispatchers(
        &mut self,
    ) -> (
        &mut P,
        Context<P>,
        &mut MessageDispatcher<P>,
        &mut IpcDispatcher<P>,
    ) {
        let context = Context::new(
            &mut self.default_channel,
            &self.timers,
            &self.network,
            &self.ipc,
        );
        (
            &mut self.protocol,
            context,
            &mut self.message_dispatcher,
            &mut self.ipc_dispatcher,
        )
    }

    fn create_context(&mut self) -> (&mut P, Context<P>) {
        let (protocol, context, _, _) = self.create_context_with_dispatchers();
        (protocol, context)
    }
}
