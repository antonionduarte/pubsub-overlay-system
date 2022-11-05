package asd.protocols.overlay.kad;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import asd.utils.ASDUtils;

public class KadRT {
	private final int k;
	private final KadID self;
	private final ArrayList<KadBucket> buckets;

	public KadRT(int k, KadID self) {
		this.k = k;
		this.self = self;
		this.buckets = new ArrayList<>();

		this.buckets.add(new KadBucket(this.k));
	}

	public boolean add(KadPeer peer) {
		var cpl = this.self.cpl(peer.id);
		var bucket = this.getOrCreateBucketForCpl(cpl);
		return bucket.add(peer);
	}

	public boolean remove(KadID id) {
		var cpl = this.self.cpl(id);
		var bucket = this.getBucketForCpl(cpl);
		return bucket.removeByID(id);
	}

	public int size() {
		return this.buckets.stream().mapToInt(KadBucket::size).sum();
	}

	public List<KadPeer> getPeersFromBucket(int index) {
		var bucket = this.getBucketForCpl(index);
		var peers = new ArrayList<KadPeer>(bucket.size());
		for (var peer : bucket)
			peers.add(peer);
		return peers;
	}

	public List<KadPeer> closest(KadID id) {
		var peers = new ArrayList<KadPeer>(this.k);
		var bucket_idx = Math.min(this.self.cpl(id), this.bucketsSize() - 1);
		var looped = false;
		while (peers.size() < this.k && bucket_idx >= 0) {
			var bucket = this.buckets.get(bucket_idx);
			for (int i = 0; i < bucket.size(); ++i)
				peers.add(bucket.get(i));
			bucket_idx -= 1;
			if (bucket_idx < 0 && !looped) {
				bucket_idx = this.bucketsSize() - 1;
				looped = true;
			}
		}
		Collections.sort(peers, new PeerDistanceComparator(id));
		while (peers.size() > this.k)
			peers.remove(peers.size() - 1);
		return peers;
	}

	public boolean contains(KadID id) {
		var cpl = this.self.cpl(id);
		var bucket = this.getBucketForCpl(cpl);
		return bucket.contains(id);
	}

	public List<KadPeer> getSample(int size) {
		var peers = new ArrayList<KadPeer>(size);
		for (var bucket : this.buckets) {
			for (int i = 0; i < bucket.size(); ++i) {
				peers.add(bucket.get(i));
				if (peers.size() >= size)
					return peers;
			}
		}
		return peers;
	}

	public List<KadPeer> getBroadcastSample(int left_bucket, int size) {
		assert size >= 2;
		var hsize = size / 2;
		var lpeers = new HashSet<KadPeer>();
		var rpeers = new HashSet<KadPeer>();
		var lbucket = this.getBucketForCpl(left_bucket);
		for (var p : lbucket)
			lpeers.add(p);
		for (int i = left_bucket + 1; i < this.buckets.size(); ++i) {
			var bucket = this.buckets.get(i);
			for (var p : bucket)
				rpeers.add(p);
		}

		var peers = new ArrayList<KadPeer>(size);
		var lsample = ASDUtils.sample(hsize, lpeers);
		var rsample = ASDUtils.sample(hsize, rpeers);
		lsample.forEach(peers::add);
		rsample.forEach(peers::add);

		return peers;
	}

	public List<KadPeer> getSample() {
		return this.getSample(this.k);
	}

	private KadBucket getBucketForCpl(int cpl) {
		if (cpl < this.buckets.size())
			return this.buckets.get(cpl);
		var last = this.buckets.get(this.buckets.size() - 1);
		return last;
	}

	private KadBucket getOrCreateBucketForCpl(int cpl) {
		if (cpl < this.buckets.size()) {
			if (cpl == this.buckets.size() - 1)
				if (this.buckets.get(cpl).isFull())
					this.unfoldLastBucket();
			return this.buckets.get(cpl);
		}

		var last = this.buckets.get(this.buckets.size() - 1);
		if (!last.isFull())
			return last;

		this.unfoldLastBucket();
		return this.buckets.get(this.buckets.size() - 1);
	}

	private void unfoldLastBucket() {
		var last_cpl = this.buckets.size() - 1;
		var last = this.buckets.get(last_cpl);
		var new_last = new KadBucket(this.k);
		this.buckets.add(new_last);
		assert last.isFull();

		int index = 0;
		while (index < last.size()) {
			var peer = last.get(index);
			var cpl = this.self.cpl(peer.id);
			if (cpl == last_cpl) {
				index += 1;
			} else {
				last.remove(index);
				new_last.add(peer);
			}
		}
	}

	@Override
	public String toString() {
		var sb = new StringBuilder();
		for (int i = 0; i < this.buckets.size(); ++i) {
			var bucket = this.buckets.get(i);
			sb.append(String.format("Bucket %d:\n", i));
			for (int j = 0; j < bucket.size(); ++j) {
				var peer = bucket.get(j);
				sb.append(String.format("\t%s cpl: %d\n", peer, this.self.cpl(peer.id)));
			}
		}
		return sb.toString();
	}

	// Testing utilities
	int bucketsSize() {
		return this.buckets.size();
	}

	KadBucket getBucket(int index) {
		return this.buckets.get(index);
	}
}
