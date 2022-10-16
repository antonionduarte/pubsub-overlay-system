use super::{Bucket, KadID, Peer};

type BucketIndex = usize;

#[derive(Debug, Clone)]
pub struct RoutingTable {
    k: u32,
    id: KadID,
    buckets: Vec<Bucket>,
}

impl RoutingTable {
    pub fn new(k: u32, id: KadID) -> Self {
        Self {
            k,
            id,
            buckets: vec![Bucket::new(k)],
        }
    }

    pub fn insert(&mut self, peer: Peer) -> bool {
        let bucket_idx = self.bucket_index_for_id_create(&peer.id);
        let bucket = &mut self.buckets[bucket_idx];
        bucket.insert(peer).is_some()
    }

    pub fn remove(&mut self, id: &KadID) -> Option<Peer> {
        let bucket_index = self.bucket_index_for_id(id);
        self.buckets[bucket_index].remove_by_id(id)
    }

    pub fn len(&self) -> usize {
        self.buckets.iter().map(|b| b.len()).sum()
    }

    pub fn closest(&self, id: &KadID) -> Vec<Peer> {
        let mut buf = Vec::with_capacity(self.k as usize);
        self.closest_into(id, &mut buf);
        buf
    }

    pub fn closest_into(&self, id: &KadID, peers: &mut Vec<Peer>) {
        peers.clear();

        let mut bucket_index = self.bucket_index_for_id(id);
        while peers.len() < self.k as usize && bucket_index < self.buckets.len() {
            let bucket = &self.buckets[bucket_index];
            peers.extend(bucket.iter().cloned());
            bucket_index = bucket_index.wrapping_sub(1);
        }

        peers.sort_by_cached_key(|p| p.id.distance(id));
        peers.truncate(self.k as usize);
    }

    pub fn contains(&self, id: &KadID) -> bool {
        let bucket_idx = self.bucket_index_for_id(id);
        self.buckets[bucket_idx].contains(id)
    }

    fn bucket_index_for_id(&self, id: &KadID) -> BucketIndex {
        let cpl = self.id.cpl(id);
        self.bucket_index_for_cpl(cpl)
    }

    fn bucket_index_for_cpl(&self, cpl: u32) -> BucketIndex {
        (self.buckets.len() - 1).min(cpl as usize)
    }

    fn bucket_index_for_id_create(&mut self, id: &KadID) -> BucketIndex {
        let cpl = self.id.cpl(id);
        self.bucket_index_for_cpl_create(cpl)
    }

    fn bucket_index_for_cpl_create(&mut self, cpl: u32) -> BucketIndex {
        let last_bucket_idx = self.buckets.len() - 1;
        if cpl <= last_bucket_idx as u32 {
            if cpl == last_bucket_idx as u32 {
                if self.buckets[last_bucket_idx].is_full() {
                    self.unfold_last_bucket();
                }
                return last_bucket_idx;
            }
        }

        if !self.buckets[last_bucket_idx].is_full() {
            return last_bucket_idx;
        }

        self.unfold_last_bucket();
        last_bucket_idx
    }

    fn unfold_last_bucket(&mut self) {
        let mut bucket = Bucket::new(self.k);
        let last_cpl = (self.buckets.len() - 1) as u32;
        let last_bucket = &mut self.buckets.last_mut().unwrap();
        bucket.extend(last_bucket.drain_filter(|p| p.id.cpl(&self.id) != last_cpl));
    }
}

impl std::fmt::Display for RoutingTable {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        for (i, bucket) in self.buckets.iter().enumerate() {
            writeln!(f, "Bucket {}:", i)?;
            for peer in bucket.iter() {
                writeln!(f, "\t {}, cpl = {}", peer, self.id.cpl(&peer.id))?;
            }
        }
        Ok(())
    }
}
