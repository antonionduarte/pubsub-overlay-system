use std::{collections::HashMap, sync::Arc};

use crossbeam::channel::{Receiver, Sender};

use crate::{
    ipc::IpcMessage,
    network::{ConnectionEvent, ReceivedMessage},
    protocol::ProtocolID,
    timer::TimerID,
};

#[derive(Debug)]
pub(crate) enum MailboxEvent {
    TimerExpired(TimerID),
    MessageReceived(ReceivedMessage),
    ConnectionEvent(ConnectionEvent),
    IpcMessage(IpcMessage),
    Exit,
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

    pub fn iter(&self) -> impl Iterator<Item = (ProtocolID, &MailboxSender)> {
        self.mailboxes.iter().map(|(k, v)| (*k, v))
    }
}

impl MailboxSender {
    pub fn timer_expired(&self, timer_id: TimerID) {
        self.send_event(MailboxEvent::TimerExpired(timer_id))
    }

    pub fn message_received(&self, received_message: ReceivedMessage) {
        self.send_event(MailboxEvent::MessageReceived(received_message))
    }

    pub fn connection_event(&self, event: ConnectionEvent) {
        self.send_event(MailboxEvent::ConnectionEvent(event))
    }

    pub fn ipc_message(&self, message: IpcMessage) {
        self.send_event(MailboxEvent::IpcMessage(message))
    }

    pub fn exit(&self) {
        self.send_event(MailboxEvent::Exit)
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
