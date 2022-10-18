use std::{any::Any, sync::Arc};

use crate::{mailbox::MailboxRouter, protocol::ProtocolID};

pub trait Request: std::fmt::Debug + Send + 'static {}

pub trait Reply: std::fmt::Debug + Send + 'static {}

pub trait Notification: std::fmt::Debug + Send + Sync + 'static {}

#[derive(Debug, Clone)]
pub(crate) struct IpcService {
    router: MailboxRouter,
}

impl IpcService {
    pub fn new(router: MailboxRouter) -> Self {
        Self { router }
    }

    pub fn request<R: Request>(&self, source: ProtocolID, destination: ProtocolID, request: R) {
        let boxed = BoxedRequest {
            source,
            destination,
            request: Box::new(request),
        };
    }

    pub fn reply<R: Reply>(&self, source: ProtocolID, destination: ProtocolID, reply: R) {
        let boxed = BoxedReply {
            source,
            destination,
            request: Box::new(reply),
        };
    }

    pub fn notification<N: Notification>(&self, source: ProtocolID, notification: N) {
        let boxed = BoxedNotification {
            source,
            notification: Arc::new(notification),
        };
    }
}

#[derive(Debug)]
pub(crate) struct BoxedRequest {
    pub(crate) source: ProtocolID,
    pub(crate) destination: ProtocolID,
    pub(crate) request: Box<dyn Any + Send>,
}

#[derive(Debug)]
pub(crate) struct BoxedReply {
    pub(crate) source: ProtocolID,
    pub(crate) destination: ProtocolID,
    pub(crate) request: Box<dyn Any + Send>,
}

#[derive(Debug, Clone)]
pub(crate) struct BoxedNotification {
    pub(crate) source: ProtocolID,
    pub(crate) notification: Arc<dyn Any + Send + Sync>,
}

impl<T: std::fmt::Debug + Send + 'static> Request for T {}

impl<T: std::fmt::Debug + Send + 'static> Reply for T {}

impl<T: std::fmt::Debug + Send + Sync + 'static> Notification for T {}
