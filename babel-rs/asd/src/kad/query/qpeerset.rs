use std::collections::{BTreeMap, HashSet};

use crate::kad::{Distance, KadID};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum QPeerState {
    Pending,
    InProgress,
    Finished,
    Failed,
}

#[derive(Debug)]
pub struct QPeerSet {
    k: u32,
    target: KadID,
    idset: HashSet<KadID>,
    states: BTreeMap<Key, QPeerState>,
    inprogress: u32,
}

impl QPeerSet {
    pub fn new(k: u32, target: KadID) -> Self {
        Self {
            k,
            target,
            idset: Default::default(),
            states: Default::default(),
            inprogress: 0,
        }
    }

    pub fn add(&mut self, peer: &KadID) {
        if self.contains(&peer) {
            return;
        }

        let key = self.key_for(peer);
        self.idset.insert(*peer);
        self.states.insert(key, QPeerState::Pending);
    }

    pub fn set(&mut self, peer: &KadID, state: QPeerState) {
        let key = self.key_for(peer);
        let prev_state = self
            .states
            .insert(key, state)
            .expect("set state for unknown peer");
        if state == QPeerState::InProgress && prev_state != QPeerState::InProgress {
            self.inprogress += 1;
        } else if state != QPeerState::InProgress && prev_state == QPeerState::InProgress {
            self.inprogress -= 1;
        }
    }

    pub fn get(&self, peer: &KadID) -> Option<QPeerState> {
        let key = self.key_for(peer);
        self.states.get(&key).copied()
    }

    pub fn contains(&self, peer: &KadID) -> bool {
        self.idset.contains(peer)
    }

    pub fn candidate(&self) -> Option<KadID> {
        let mut iter = self.states.iter();
        while let Some((key, state)) = iter.next() {
            if *state == QPeerState::Pending {
                return Some(key.id);
            }
        }
        None
    }

    pub fn closest(&self) -> Vec<KadID> {
        let mut closest = Vec::new();
        let mut iter = self.states.iter();
        while let Some((key, state)) = iter.next() {
            if *state == QPeerState::Finished {
                closest.push(key.id);
            }
        }
        closest
    }

    pub fn is_in_state(&self, peer: &KadID, state: QPeerState) -> bool {
        self.get(peer) == Some(state)
    }

    pub fn num_in_progress(&self) -> u32 {
        self.inprogress
    }

    pub fn num_candidates(&self) -> u32 {
        self.states
            .iter()
            .take(self.k as usize)
            .filter(|(_, state)| matches!(state, QPeerState::Pending))
            .count() as u32
    }

    pub fn iter(&self) -> impl Iterator<Item = (KadID, QPeerState)> + '_ {
        self.states.iter().map(|(key, state)| (key.id, *state))
    }

    pub fn iter_finished(&self) -> impl Iterator<Item = KadID> + '_ {
        self.states
            .iter()
            .filter(|(_, state)| matches!(state, QPeerState::Finished))
            .map(|(key, _)| key.id)
    }

    fn key_for(&self, peer: &KadID) -> Key {
        let distance = self.target.distance(peer);
        Key::new(*peer, distance)
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
struct Key {
    id: KadID,
    distance: Distance,
}

impl std::cmp::PartialOrd<Key> for Key {
    fn partial_cmp(&self, other: &Self) -> Option<std::cmp::Ordering> {
        Some(self.cmp(other))
    }
}

impl std::cmp::Ord for Key {
    fn cmp(&self, other: &Self) -> std::cmp::Ordering {
        self.distance.cmp(&other.distance)
    }
}

impl Key {
    fn new(id: KadID, distance: Distance) -> Self {
        Self { id, distance }
    }
}
