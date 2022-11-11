package asd.protocols.overlay.kad.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.PeerDistanceComparator;
import asd.utils.ASDUtils;

public class RoutingTable {
	private final int k;
	private final KadID self;
	private final ArrayList<Bucket> buckets;

	public RoutingTable(int k, KadID self) {
		this.k = k;
		this.self = self;
		this.buckets = new ArrayList<>();

		this.buckets.add(new Bucket(this.k));
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
		return this.buckets.stream().mapToInt(Bucket::size).sum();
	}

	public int buckets() {
		return this.buckets.size();
	}

	public Bucket bucket(int index) {
		return this.buckets.get(index);
	}

	public Bucket lastBucket() {
		return this.buckets.get(this.buckets.size() - 1);
	}

	public List<KadPeer> getPeersFromBucket(int index) {
		var bucket = this.getBucketForCpl(index);
		var peers = new ArrayList<KadPeer>(bucket.size());
		for (var peer : bucket)
			peers.add(peer);
		return peers;
	}

	public List<KadPeer> closest(KadID id, KadID ignore) {
		var peers = new ArrayList<KadPeer>(this.k);
		var bucket_idx = Math.min(this.self.cpl(id), this.bucketsSize() - 1);
		var iter_count = this.buckets.size();
		for (int i = 0; i < iter_count && peers.size() < this.k; ++i) {
			var idx = Math.floorMod(bucket_idx - i, iter_count);
			var bucket = this.buckets.get(idx);
			for (var peer : bucket)
				if (!peer.id.equals(ignore))
					peers.add(peer);
		}
		Collections.sort(peers, new PeerDistanceComparator(id));
		while (peers.size() > this.k)
			peers.remove(peers.size() - 1);
		return peers;
	}

	public List<KadPeer> closest(KadID id) {
		return this.closest(id, null);
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
		// Note: Experimenting with flooding all buckets
		// var peers = new ArrayList<KadPeer>(this.size());
		// for (var bucket : this.buckets) {
		// for (int i = 0; i < bucket.size(); ++i) {
		// peers.add(bucket.get(i));
		// }
		// }

		assert size >= 2;
		var lsize = 2;
		var rsize = size - lsize;
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
		var lsample = ASDUtils.sample(lsize, lpeers);
		var rsample = ASDUtils.sample(rsize, rpeers);
		lsample.forEach(peers::add);
		rsample.forEach(peers::add);

		return peers;
	}

	public List<KadPeer> getSample() {
		return this.getSample(this.k);
	}

	public boolean isEmpty() {
		return this.size() == 0;
	}

	public Stream<KadPeer> stream() {
		return this.buckets.stream().flatMap(Bucket::stream);
	}

	private Bucket getBucketForCpl(int cpl) {
		if (cpl < this.buckets.size())
			return this.buckets.get(cpl);
		var last = this.buckets.get(this.buckets.size() - 1);
		return last;
	}

	private Bucket getOrCreateBucketForCpl(int cpl) {
		if (cpl < this.buckets.size()) {
			if (cpl == this.buckets.size() - 1)
				if (this.buckets.get(cpl).isFull())
					this.unfoldLastBucket();
			return this.buckets.get(cpl);
		}

		while (true) {
			var last = this.buckets.get(this.buckets.size() - 1);
			if (!last.isFull())
				break;

			this.unfoldLastBucket();
		}

		var bucket_index = Math.min(cpl, this.buckets.size() - 1);
		return this.buckets.get(bucket_index);
	}

	private void unfoldLastBucket() {
		var last_cpl = this.buckets.size() - 1;
		var last = this.buckets.get(last_cpl);
		var new_last = new Bucket(this.k);
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

	Bucket getBucket(int index) {
		return this.buckets.get(index);
	}

	// Metrics utilities
	public List<List<String>> dumpForMetrics() {
		var dump = new ArrayList<List<String>>();
		for (int i = 0; i < this.buckets.size(); ++i) {
			var bucket = this.buckets.get(i);
			var row = new ArrayList<String>();
			for (int j = 0; j < bucket.size(); ++j) {
				var peer = bucket.get(j);
				row.add(String.valueOf(peer.host.getPort()));
			}
			dump.add(row);
		}
		return dump;
	}
}
