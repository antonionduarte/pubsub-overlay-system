package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadDistance;
import asd.protocols.overlay.kad.KadID;

import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class QPeerSet {
	final SortedMap<Key, State> peers;
	private final int k;
	private final KadID target;
	private final HashMap<KadID, Key> keys;
	private int inprogress;
	public QPeerSet(int k, KadID target) {
		this.k = k;
		this.target = target;
		this.keys = new HashMap<>();
		this.peers = new TreeMap<>();
		this.inprogress = 0;
	}

	public void add(KadID id) {
		if (!this.contains(id)) {
			var key = Key.create(id, this.target);
			this.keys.put(id, key);
			this.peers.put(key, State.PENDING);
		}
	}

	public void markInProgress(KadID id) {
		assert this.contains(id);
		var key = this.keys.get(id);
		var prev = this.peers.put(key, State.INPROGRESS);
		if (prev != State.INPROGRESS) {
			this.inprogress += 1;
		}
	}

	public void markFinished(KadID id) {
		assert this.contains(id);
		var key = this.keys.get(id);
		var prev = this.peers.put(key, State.FINISHED);
		if (prev == State.INPROGRESS) {
			this.inprogress -= 1;
		}
	}

	public void markFailed(KadID id) {
		assert this.contains(id);
		var key = this.keys.get(id);
		var prev = this.peers.put(key, State.FAILED);
		if (prev == State.INPROGRESS) {
			this.inprogress -= 1;
		}
	}

	public boolean contains(KadID id) {
		return this.keys.containsKey(id);
	}

	public State getState(KadID id) {
		var key = this.keys.get(id);
		if (key == null) {
			return null;
		}
		return this.peers.get(key);
	}

	public boolean isInState(KadID id, State state) {
		var key = this.keys.get(id);
		if (key == null) {
			return false;
		}
		return this.peers.get(key) == state;
	}

	public int getNumInProgress() {
		return this.inprogress;
	}

	public int getNumCandidates() {
		return (int) this.peers.entrySet().stream().limit(this.k).filter(entry -> entry.getValue() == State.PENDING)
				.count();
	}

	public KadID getCandidate() {
		return this.peers.entrySet().stream().limit(this.k).filter(entry -> entry.getValue() == State.PENDING)
				.findFirst().map(entry -> entry.getKey().id).orElse(null);
	}

	public Stream<Entry<KadID, State>> stream() {
		return this.peers.entrySet().stream()
				.map(entry -> new HashMap.SimpleEntry<>(entry.getKey().id, entry.getValue()));
	}

	public List<KadID> closest() {
		return this.streamFinishedKClosest().collect(Collectors.toList());
	}

	private Stream<KadID> streamFinishedKClosest() {
		return this.peers.entrySet().stream().filter(entry -> entry.getValue() == State.FINISHED).limit(this.k)
				.map(entry -> entry.getKey().id);
	}

	public enum State {
		PENDING,
		INPROGRESS,
		FINISHED,
		FAILED,
	}

	static class Key implements Comparable<Key> {
		public final KadID id;
		public final KadDistance distance;

		public Key(KadID id, KadDistance distance) {
			this.id = id;
			this.distance = distance;
		}

		public static Key create(KadID id, KadID reference) {
			return new Key(id, reference.distanceTo(id));
		}

		@Override
		public boolean equals(Object other) {
			if (other == null) {
				return false;
			}
			if (other == this) {
				return true;
			}
			if (!(other instanceof Key)) {
				return false;
			}
			return this.id.equals(((Key) other).id);
		}

		@Override
		public int hashCode() {
			return id.hashCode();
		}

		@Override
		public String toString() {
			return "Key{" +
					"id=" + id +
					", distance=" + distance +
					'}';
		}

		@Override
		public int compareTo(Key o) {
			return this.distance.compareTo(o.distance);
		}
	}
}
