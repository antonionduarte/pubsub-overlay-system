use std::{collections::HashMap, net::SocketAddr};

use super::{KadID, Peer};

#[derive(Debug, Default, Clone)]
pub struct AddrBook {
    id2host: HashMap<KadID, SocketAddr>,
    host2id: HashMap<SocketAddr, KadID>,
}

impl AddrBook {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn insert(&mut self, id: KadID, host: SocketAddr) {
        self.id2host.insert(id, host);
        self.host2id.insert(host, id);
    }

    pub fn insert_peer(&mut self, peer: &Peer) {
        self.insert(peer.id, peer.host);
    }

    pub fn id_to_host(&self, id: &KadID) -> Option<SocketAddr> {
        self.id2host.get(id).copied()
    }

    pub fn host_to_id(&self, host: &SocketAddr) -> Option<KadID> {
        self.host2id.get(host).copied()
    }

    pub fn id_to_peer(&self, id: &KadID) -> Option<Peer> {
        self.id_to_host(id).map(|host| Peer { id: *id, host })
    }

    pub fn host_to_peer(&self, host: &SocketAddr) -> Option<Peer> {
        self.host_to_id(host).map(|id| Peer { id, host: *host })
    }
}
