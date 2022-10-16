use crate::mailbox::MailboxRouter;

#[derive(Debug, Clone)]
pub(crate) struct IpcService {}

impl IpcService {
    pub fn spawn(router: MailboxRouter) -> Self {
        Self {}
    }
}
