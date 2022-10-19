use std::{any::Any, sync::Arc};

use crate::{mailbox::MailboxRouter, protocol::ProtocolID};

pub trait Request: std::fmt::Debug + Send + 'static {}

pub trait Reply: std::fmt::Debug + Send + 'static {}

pub trait Notification: std::fmt::Debug + Send + Sync + 'static {}

#[derive(Debug)]
pub(crate) enum IpcMessage {
    Request {
        source: ProtocolID,
        destination: ProtocolID,
        message: Box<dyn Any + Send + 'static>,
    },
    Reply {
        source: ProtocolID,
        destination: ProtocolID,
        message: Box<dyn Any + Send + 'static>,
    },
    Notification {
        source: ProtocolID,
        message: Arc<dyn Any + Send + Sync + 'static>,
    },
}

impl IpcMessage {
    pub fn request<R: Request>(source: ProtocolID, destination: ProtocolID, message: R) -> Self {
        Self::Request {
            source,
            destination,
            message: Box::new(message),
        }
    }

    pub fn reply<R: Reply>(source: ProtocolID, destination: ProtocolID, message: R) -> Self {
        Self::Reply {
            source,
            destination,
            message: Box::new(message),
        }
    }

    pub fn notification<N: Notification>(source: ProtocolID, message: Arc<N>) -> Self {
        Self::Notification { source, message }
    }

    pub fn message_typeid(&self) -> std::any::TypeId {
        match self {
            Self::Request { message, .. } => (**message).type_id(),
            Self::Reply { message, .. } => (**message).type_id(),
            Self::Notification { message, .. } => (**message).type_id(),
        }
    }
}

#[derive(Debug, Clone)]
pub(crate) struct IpcService {
    router: MailboxRouter,
}

impl IpcService {
    pub fn new(router: MailboxRouter) -> Self {
        Self { router }
    }

    pub fn request<R: Request>(&self, source: ProtocolID, destination: ProtocolID, request: R) {
        let mailbox = match self.router.get(destination) {
            Some(mailbox) => mailbox,
            None => {
                log::error!("Protocol {} not found", destination);
                return;
            }
        };
        mailbox.ipc_message(IpcMessage::request(source, destination, request));
    }

    pub fn reply<R: Reply>(&self, source: ProtocolID, destination: ProtocolID, reply: R) {
        let mailbox = match self.router.get(destination) {
            Some(mailbox) => mailbox,
            None => {
                log::error!("Protocol {} not found", destination);
                return;
            }
        };
        mailbox.ipc_message(IpcMessage::reply(source, destination, reply));
    }

    pub fn notification<N: Notification>(&self, source: ProtocolID, notification: N) {
        let notification = Arc::new(notification);
        for (destination, mailbox) in self.router.iter() {
            log::trace!("Send notification to {}", destination);
            mailbox.ipc_message(IpcMessage::notification(source, notification.clone()));
        }
    }
}

impl<T: std::fmt::Debug + Send + 'static> Request for T {}

impl<T: std::fmt::Debug + Send + 'static> Reply for T {}

impl<T: std::fmt::Debug + Send + Sync + 'static> Notification for T {}
