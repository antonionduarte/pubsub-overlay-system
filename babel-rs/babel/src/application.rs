use crate::{
    ipc::{IpcService, NotificationSender},
    mailbox::{MailboxRouter, MailboxRouterBuilder},
    network::{channel::ChannelFactory, NetworkService, NetworkServiceBuilder},
    protocol::{Protocol, ProtocolExecutor},
    timer::TimerService,
};

type ProtocolExecutorSpawner =
    Box<dyn FnOnce(TimerService, NetworkService, IpcService) + Send + 'static>;

#[derive(Default)]
pub struct ApplicationBuilder {
    router_builder: MailboxRouterBuilder,
    network_builder: NetworkServiceBuilder,
    spawners: Vec<ProtocolExecutorSpawner>,
}

pub struct Application {
    mailbox_router: MailboxRouter,
    timer_service: TimerService,
    network_service: NetworkService,
    ipc_service: IpcService,
    spawners: Vec<ProtocolExecutorSpawner>,
}

impl ApplicationBuilder {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn register_channel<C: ChannelFactory>(mut self, name: &str, factory: C) -> Self {
        self.network_builder.register_channel(name, factory);
        self
    }

    pub fn register_protocol<P: Protocol>(mut self, protocol: P) -> Self {
        let receiver = self.router_builder.register_protocol(P::ID);
        let spawner = Box::new(move |timers, network, ipc| {
            log::debug!(
                "Spawning protocol {} on thread {:?}",
                P::NAME,
                std::thread::current().id()
            );

            let mut executor = ProtocolExecutor::new(protocol, receiver, timers, network, ipc);
            executor.run();
        });
        self.spawners.push(spawner);
        self
    }

    pub async fn build(self) -> Application {
        let router = self.router_builder.build();
        let timer_service = TimerService::new(router.clone());
        let network_service = self.network_builder.build(router.clone());
        let ipc_service = IpcService::new(router.clone());

        Application {
            mailbox_router: router,
            timer_service,
            network_service,
            ipc_service,
            spawners: self.spawners,
        }
    }
}

impl Drop for Application {
    fn drop(&mut self) {
        for (_, mailbox) in self.mailbox_router.iter() {
            mailbox.exit();
        }
    }
}

impl Application {
    pub fn notification_sender(&self) -> NotificationSender {
        NotificationSender::new(self.ipc_service.clone())
    }

    pub async fn run(mut self) {
        let mut handles = Vec::new();

        for spawner in self.spawners.drain(..) {
            let timer_service = self.timer_service.clone();
            let network_service = self.network_service.clone();
            let ipc_service = self.ipc_service.clone();
            let handle = tokio::task::spawn_blocking(move || {
                spawner(timer_service, network_service, ipc_service);
            });
            handles.push(handle);
        }

        for handle in handles {
            handle.await.unwrap();
        }
    }
}
