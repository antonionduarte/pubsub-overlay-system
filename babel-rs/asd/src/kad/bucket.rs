use super::{KadID, Peer};

#[derive(Debug, Clone)]
pub struct Bucket {
    peers: Vec<Peer>,
}

impl Bucket {
    pub fn new(k: u32) -> Self {
        Self {
            peers: Vec::with_capacity(k as usize),
        }
    }

    pub fn insert(&mut self, peer: Peer) -> Option<usize> {
        if self.is_full() || self.contains(&peer.id) {
            return None;
        }
        let index = self.peers.len();
        self.peers.push(peer);
        Some(index)
    }

    pub fn get(&self, index: usize) -> Option<&Peer> {
        self.peers.get(index)
    }

    pub fn remove(&mut self, index: usize) -> Option<Peer> {
        if index >= self.peers.len() {
            return None;
        }
        Some(self.peers.swap_remove(index))
    }

    pub fn remove_by_id(&mut self, id: &KadID) -> Option<Peer> {
        let index = self.find_peer_index(id)?;
        Some(self.peers.swap_remove(index))
    }

    pub fn len(&self) -> usize {
        self.peers.len()
    }

    pub fn is_empty(&self) -> bool {
        self.peers.is_empty()
    }

    pub fn is_full(&self) -> bool {
        self.peers.len() == self.peers.capacity()
    }

    pub fn contains(&self, id: &KadID) -> bool {
        self.find_peer_index(id).is_some()
    }

    pub fn iter(&self) -> impl Iterator<Item = &Peer> {
        self.peers.iter()
    }

    pub fn extend(&mut self, peers: impl IntoIterator<Item = Peer>) {
        for peer in peers {
            if self.insert(peer).is_none() {
                break;
            }
        }
    }

    pub fn drain_filter<'a>(
        &'a mut self,
        mut f: impl FnMut(&Peer) -> bool + 'a,
    ) -> impl Iterator<Item = Peer> + 'a {
        self.peers.drain_filter(move |p| f(p))
    }

    fn find_peer_index(&self, id: &KadID) -> Option<usize> {
        self.peers.iter().position(|p| p.id == *id)
    }
}

impl std::ops::Index<usize> for Bucket {
    type Output = Peer;

    fn index(&self, index: usize) -> &Self::Output {
        self.get(index).unwrap()
    }
}
