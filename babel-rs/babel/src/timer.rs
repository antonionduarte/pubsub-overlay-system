use std::{
    collections::BinaryHeap,
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};

use slotmap::SlotMap;
use tokio::{sync::Notify, task::JoinHandle};

use crate::{mailbox::MailboxRouter, protocol::ProtocolID, TimerID};

#[derive(Debug)]
enum TimerKind {
    OneShot,
    Periodic(Duration),
}

#[derive(Debug)]
struct Timer {
    kind: TimerKind,
    protocol_id: ProtocolID,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
struct HeapKey {
    id: TimerID,
    deadline: Instant,
}

#[derive(Debug, Default)]
struct TimerHeap {
    heap: BinaryHeap<HeapKey>,
    timers: SlotMap<TimerID, Timer>,
}

#[derive(Debug)]
pub(crate) struct TimerServiceInner {
    notifier: Arc<Notify>,
    heap: Arc<Mutex<TimerHeap>>,
    handle: JoinHandle<()>,
}

#[derive(Debug, Clone)]
pub(crate) struct TimerService(Arc<TimerServiceInner>);

struct TimerServiceExecutor {
    router: MailboxRouter,
    heap: Arc<Mutex<TimerHeap>>,
    notifier: Arc<Notify>,
}

impl std::cmp::PartialOrd for HeapKey {
    fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
        Some(self.cmp(other))
    }
}

impl std::cmp::Ord for HeapKey {
    fn cmp(&self, other: &Self) -> std::cmp::Ordering {
        self.deadline.cmp(&other.deadline)
    }
}

impl TimerHeap {
    pub fn create(&mut self, protocol_id: ProtocolID, kind: TimerKind, delay: Duration) -> TimerID {
        let deadline = Instant::now() + delay;
        let id = self.timers.insert(Timer { kind, protocol_id });
        self.heap.push(HeapKey { id, deadline });
        id
    }

    pub fn cancel(&mut self, timer_id: TimerID) {
        self.timers.remove(timer_id);
    }
}

impl Drop for TimerServiceInner {
    fn drop(&mut self) {
        self.handle.abort();
    }
}

impl TimerService {
    pub fn new(router: MailboxRouter) -> Self {
        let heap = Arc::new(Mutex::new(TimerHeap::default()));
        let notifier = Arc::new(Notify::new());
        let mut executor =
            TimerServiceExecutor::new(router.clone(), heap.clone(), notifier.clone());

        let handle = tokio::spawn(async move { executor.run().await });
        let inner = Arc::new(TimerServiceInner {
            notifier,
            heap,
            handle,
        });

        Self(inner)
    }

    pub fn create_timer(&self, protocol_id: ProtocolID, delay: Duration) -> TimerID {
        let mut heap = self.0.heap.lock().unwrap();
        let timer_id = heap.create(protocol_id, TimerKind::OneShot, delay);
        self.0.notifier.notify_one();
        timer_id
    }

    pub fn create_periodic_timer(
        &self,
        protocol_id: ProtocolID,
        delay: Duration,
        interval: Duration,
    ) -> TimerID {
        let mut heap = self.0.heap.lock().unwrap();
        let timer_id = heap.create(protocol_id, TimerKind::Periodic(interval), delay);
        self.0.notifier.notify_one();
        timer_id
    }

    pub fn cancel_timer(&self, timer_id: TimerID) {
        let mut heap = self.0.heap.lock().unwrap();
        heap.cancel(timer_id);
    }
}

impl TimerServiceExecutor {
    fn new(router: MailboxRouter, heap: Arc<Mutex<TimerHeap>>, notifier: Arc<Notify>) -> Self {
        Self {
            router,
            heap,
            notifier,
        }
    }

    async fn run(&mut self) {
        loop {
            let deadline = {
                let heap = self.heap.lock().unwrap();
                let key = heap.heap.peek().copied();
                key.map(|key| key.deadline)
            };

            match deadline {
                Some(deadline) => tokio::select! {
                    _ = self.notifier.notified() => continue,
                    _ = tokio::time::sleep_until(deadline.into()) => {},
                },
                None => {
                    self.notifier.notified().await;
                    continue;
                }
            }

            let mut heap = self.heap.lock().unwrap();
            if let Some(HeapKey { id, .. }) = heap.heap.pop() {
                if let Some(timer) = heap.timers.get(id) {
                    if let Some(sender) = self.router.get(timer.protocol_id) {
                        sender.timer_expired(id);
                    }

                    match timer.kind {
                        TimerKind::OneShot => {
                            heap.timers.remove(id);
                        }
                        TimerKind::Periodic(period) => heap.heap.push(HeapKey {
                            id,
                            deadline: Instant::now() + period,
                        }),
                    }
                }
            }
        }
    }
}
