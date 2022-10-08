package asd.protocols.overlay.kad;

import java.util.Comparator;

// TODO: Can be improved. Instead of using this comparator store the precomputed distance and use that to compare.
class PeerDistanceComparator implements Comparator<KadPeer> {
	private final KadID from;

	public PeerDistanceComparator(KadID from) {
		this.from = from;
	}

	@Override
	public int compare(KadPeer o1, KadPeer o2) {
		return from.distanceTo(o1.id).compareTo(from.distanceTo(o2.id));
	}
}
