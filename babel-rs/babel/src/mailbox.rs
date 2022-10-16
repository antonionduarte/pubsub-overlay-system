use std::{collections::HashMap, net::SocketAddr, sync::Arc};

use bytes::Bytes;
use crossbeam::channel::{Receiver, Sender};

use crate::{
    channel::ConnectionEvent,
    protocol::{ProtocolID, ProtocolMessageID},
    ChannelID, TimerID,
};

#[derive(Debug)]
pub enum MailboxEvent {
    TimerExpired(TimerID),
    MessageReceived(ChannelID, SocketAddr, ProtocolMessageID, Bytes),
    ConnectionEvent(ChannelID, ConnectionEvent),
}

#[derive(Debug, Default)]
pub(crate) struct MailboxRouterBuilder {
    mailboxes: HashMap<ProtocolID, MailboxSender>,
}

#[derive(Debug, Clone)]
pub(crate) struct MailboxRouter {
    mailboxes: Arc<HashMap<ProtocolID, MailboxSender>>,
}

#[derive(Debug, Clone)]
pub(crate) struct MailboxSender {
    sender: Sender<MailboxEvent>,
}

#[derive(Debug)]
pub(crate) struct MailboxReceiver {
    receiver: Receiver<MailboxEvent>,
}

impl MailboxRouterBuilder {
    pub fn register_protocol(&mut self, protocol_id: ProtocolID) -> MailboxReceiver {
        if self.mailboxes.contains_key(&protocol_id) {
            panic!("Protocol {} already registered", protocol_id);
        }
        let (sender, receiver) = create_mailbox();
        self.mailboxes.insert(protocol_id, sender);
        receiver
    }

    pub fn build(self) -> MailboxRouter {
        MailboxRouter {
            mailboxes: Arc::new(self.mailboxes),
        }
    }
}

impl MailboxRouter {
    pub fn get(&self, protocol_id: ProtocolID) -> Option<&MailboxSender> {
        self.mailboxes.get(&protocol_id)
    }
}

impl MailboxSender {
    pub fn timer_expired(&self, timer_id: TimerID) {
        self.send_event(MailboxEvent::TimerExpired(timer_id))
    }

    pub fn message_received(
        &self,
        channel_id: ChannelID,
        addr: SocketAddr,
        message_id: ProtocolMessageID,
        message: Bytes,
    ) {
        self.send_event(MailboxEvent::MessageReceived(
            channel_id, addr, message_id, message,
        ))
    }

    pub fn connection_event(&self, channel_id: ChannelID, event: ConnectionEvent) {
        self.send_event(MailboxEvent::ConnectionEvent(channel_id, event))
    }

    fn send_event(&self, event: MailboxEvent) {
        self.sender
            .send(event)
            .expect("Receiver should not be dropped");
    }
}

impl MailboxReceiver {
    pub fn recv(&mut self) -> Option<MailboxEvent> {
        self.receiver.recv().ok()
    }
}

fn create_mailbox() -> (MailboxSender, MailboxReceiver) {
    let (tx, rx) = crossbeam::channel::unbounded();

    let sender = MailboxSender { sender: tx };
    let receiver = MailboxReceiver { receiver: rx };

    (sender, receiver)
}
