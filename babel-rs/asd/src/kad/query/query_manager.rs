use std::time::{Duration, Instant};

use slotmap::{Key, KeyData, SlotMap};

use crate::kad::{KadID, KadParams, Peer};

use super::{QPeerSet, QPeerState};

slotmap::new_key_type! { pub struct QueryID; }

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum QueryKind {
    FindNode,
    FindValue,
}

#[derive(Debug)]
pub struct QueryDescriptor {
    kind: QueryKind,
    target: KadID,
}

impl QueryDescriptor {
    pub fn new(kind: QueryKind, target: KadID) -> Self {
        Self { kind, target }
    }
}

#[derive(Debug)]
pub enum QueryResultKind {
    FindNode {
        closest: Vec<KadID>,
    },
    FindValue {
        value: Option<Vec<u8>>,
        closest: Option<KadID>,
    },
}

#[derive(Debug)]
pub struct QueryResult {
    pub query_id: QueryID,
    pub result: QueryResultKind,
}

pub trait QueryIO {
    fn discover(&mut self, peer: &Peer);
    fn find_closest(&self, target: &KadID) -> Vec<Peer>;
    fn send_find_node(&mut self, qid: QueryID, peer: &KadID, target: &KadID);
    fn send_find_value(&mut self, qid: QueryID, peer: &KadID, target: &KadID);
    fn query_result(&mut self, result: QueryResult);
}

#[derive(Debug)]
pub struct QueryManager {
    params: KadParams,
    local_id: KadID,
    queries: SlotMap<QueryID, Query>,
}

impl From<u64> for QueryID {
    fn from(value: u64) -> Self {
        QueryID::from(KeyData::from_ffi(value))
    }
}

impl From<QueryID> for u64 {
    fn from(value: QueryID) -> Self {
        value.data().as_ffi()
    }
}

impl QueryManager {
    pub fn new(params: KadParams, local_id: KadID) -> Self {
        Self {
            params,
            local_id,
            queries: Default::default(),
        }
    }

    pub fn query(&mut self, qio: &mut dyn QueryIO, descriptor: QueryDescriptor) -> QueryID {
        let seeds = qio.find_closest(&descriptor.target);
        let qid = self.queries.insert_with_key(|qid| {
            Query::new(self.params.clone(), self.local_id, qid, descriptor, &seeds)
        });
        let query = &mut self.queries[qid];
        let status = query.start(qio);
        if status == QueryStatus::Finished {
            self.handle_query_finished(qid);
        }
        qid
    }

    pub fn cancel(&mut self, _qio: &mut dyn QueryIO, id: QueryID) {
        match self.queries.remove(id) {
            None => log::warn!("removing unknown query"),
            _ => {}
        }
    }

    pub fn on_find_node_response(
        &mut self,
        qio: &mut dyn QueryIO,
        qid: QueryID,
        peer: KadID,
        peers: Vec<Peer>,
    ) {
        let query = match self.queries.get_mut(qid) {
            Some(query) => query,
            None => {
                log::warn!("received find_node response for unknown query");
                return;
            }
        };
        let status = query.on_find_node_response(qio, peer, peers);
        if status == QueryStatus::Finished {
            self.handle_query_finished(qid);
        }
    }

    pub fn on_find_value_response(
        &mut self,
        qio: &mut dyn QueryIO,
        qid: QueryID,
        peer: KadID,
        closest: Vec<Peer>,
        value: Option<Vec<u8>>,
    ) {
        let query = match self.queries.get_mut(qid) {
            Some(query) => query,
            None => {
                log::warn!("received find_value response for unknown query");
                return;
            }
        };
        let status = query.on_find_value_response(qio, peer, closest, value);
        if status == QueryStatus::Finished {
            self.handle_query_finished(qid);
        }
    }

    pub fn check_timeouts(&mut self, qio: &mut dyn QueryIO) {
        let mut finished = Vec::new();
        for (qid, query) in self.queries.iter_mut() {
            let status = query.check_timeouts(qio);
            if status == QueryStatus::Finished {
                finished.push(qid);
            }
        }
        for qid in finished {
            self.handle_query_finished(qid);
        }
    }

    fn handle_query_finished(&mut self, qid: QueryID) {
        self.queries.remove(qid);
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
enum QueryStatus {
    Active,
    Finished,
}

#[derive(Debug)]
struct ActiveRequest {
    peer: KadID,
    start: Instant,
}

#[derive(Debug)]
struct Query {
    params: KadParams,
    local_id: KadID,
    query_id: QueryID,
    descriptor: QueryDescriptor,
    pset: QPeerSet,
    active_requests: Vec<ActiveRequest>,

    // only used in FindValue query
    value: Option<Vec<u8>>,
    value_provider: Option<KadID>,
}

impl Query {
    fn new(
        params: KadParams,
        local_id: KadID,
        query_id: QueryID,
        descriptor: QueryDescriptor,
        seeds: &[Peer],
    ) -> Self {
        let mut pset = QPeerSet::new(params.k, descriptor.target);
        for seed in seeds {
            if seed.id != local_id {
                pset.add(&seed.id);
            }
        }

        let active_requests = Vec::with_capacity(params.alpha as usize);
        Self {
            params,
            local_id,
            query_id,
            descriptor,
            pset,
            active_requests,
            value: None,
            value_provider: None,
        }
    }

    fn start(&mut self, qio: &mut dyn QueryIO) -> QueryStatus {
        let status = self.make_requests(qio);
        if status == QueryStatus::Finished {
            self.finish(qio);
        }
        status
    }

    fn on_find_node_response(
        &mut self,
        qio: &mut dyn QueryIO,
        peer: KadID,
        peers: Vec<Peer>,
    ) -> QueryStatus {
        assert!(self.descriptor.kind == QueryKind::FindNode);
        self.pset.set(&peer, QPeerState::Finished);
        self.remove_active_request(&peer);
        self.add_extra_peers(qio, &peers);
        let status = self.make_requests(qio);
        if status == QueryStatus::Finished {
            self.finish(qio);
        }
        status
    }

    fn on_find_value_response(
        &mut self,
        qio: &mut dyn QueryIO,
        peer: KadID,
        closest: Vec<Peer>,
        value: Option<Vec<u8>>,
    ) -> QueryStatus {
        assert!(self.descriptor.kind == QueryKind::FindValue);
        self.pset.set(&peer, QPeerState::Finished);
        self.remove_active_request(&peer);
        self.add_extra_peers(qio, &closest);
        match value {
            Some(value) => {
                self.value = Some(value);
                self.value_provider = Some(peer);
                self.finish(qio);
                QueryStatus::Finished
            }
            None => {
                let status = self.make_requests(qio);
                if status == QueryStatus::Finished {
                    self.finish(qio);
                }
                status
            }
        }
    }

    fn finish(&mut self, qio: &mut dyn QueryIO) {
        match self.descriptor.kind {
            QueryKind::FindNode => {
                let closest = self.pset.closest();
                qio.query_result(QueryResult {
                    query_id: self.query_id,
                    result: QueryResultKind::FindNode { closest },
                });
            }
            QueryKind::FindValue => {
                qio.query_result(QueryResult {
                    query_id: self.query_id,
                    result: QueryResultKind::FindValue {
                        value: self.value.take(),
                        closest: self
                            .pset
                            .iter_finished()
                            .filter(|id| Some(id) != self.value_provider.as_ref())
                            .next(),
                    },
                });
            }
        }
    }

    fn check_timeouts(&mut self, qio: &mut dyn QueryIO) -> QueryStatus {
        let now = Instant::now();
        let timedout_peers = self
            .active_requests
            .drain_filter(|req| now.duration_since(req.start) > Duration::from_secs(3))
            .map(|req| req.peer)
            .collect::<Vec<_>>();

        let mut status = QueryStatus::Active;
        for peer in timedout_peers {
            self.pset.set(&peer, QPeerState::Failed);
            status = self.make_requests(qio);
        }
        if status == QueryStatus::Finished {
            self.finish(qio);
        }
        status
    }

    #[must_use]
    fn make_requests(&mut self, qio: &mut dyn QueryIO) -> QueryStatus {
        while self.pset.num_in_progress() < self.params.alpha && self.pset.num_candidates() > 0 {
            let candidate = self.pset.candidate().expect("num_candiates > 0");
            self.pset.set(&candidate, QPeerState::InProgress);
            self.add_active_request(&candidate);
            match self.descriptor.kind {
                QueryKind::FindNode => {
                    qio.send_find_node(self.query_id, &candidate, &self.descriptor.target)
                }
                QueryKind::FindValue => {
                    qio.send_find_value(self.query_id, &candidate, &self.descriptor.target)
                }
            }
        }

        //log::trace!(
        //    "num_in_progress = {} num_candidates = {}",
        //    self.pset.num_in_progress(),
        //    self.pset.num_candidates()
        //);
        if self.pset.num_in_progress() == 0 && self.pset.num_candidates() == 0 {
            QueryStatus::Finished
        } else {
            QueryStatus::Active
        }
    }

    fn add_active_request(&mut self, peer: &KadID) {
        assert!(self.active_requests.len() < self.params.alpha as usize);
        self.active_requests.push(ActiveRequest {
            peer: *peer,
            start: Instant::now(),
        });
    }

    fn remove_active_request(&mut self, peer: &KadID) {
        let idx = self.active_requests.iter().position(|r| r.peer == *peer);
        if let Some(idx) = idx {
            self.active_requests.swap_remove(idx);
        }
    }

    fn add_extra_peers(&mut self, qio: &mut dyn QueryIO, peers: &[Peer]) {
        for peer in peers {
            if peer.id != self.local_id {
                self.pset.add(&peer.id);
                qio.discover(peer);
            }
        }
    }
}
