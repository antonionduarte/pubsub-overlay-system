package asd.protocols.overlay.hyparview;

import pt.unl.fct.di.novasys.network.data.Host;

import java.util.*;

public class View {

	private final Set<Host> membership;
	private final Host self;
	private final Random random;

	private final int capacity;

	public View(int size, Host self) {
		this.membership = new HashSet<>();
		this.random = new Random();
		this.capacity = size;
		this.self = self;
	}

	public Set<Host> getView() {
		return this.membership;
	}

	public Host addPeer(Host peer) {
		Host dropped = null;
		if (!peer.equals(self) && !membership.contains(peer)) {
			if (this.isFull())
				dropped = this.dropRandomElement();
			this.membership.add(peer);
		}
		return dropped;
	}

	public boolean removePeer(Host peer) {
		return this.membership.remove(peer);
	}

	public Host dropRandomElement() {
		var toDrop = this.selectRandomPeer();
		this.membership.remove(toDrop);
		return toDrop;
	}

	public Host selectRandomPeer() {
		if (membership.size() != 0) {
			var nodeIndex = random.nextInt(membership.size());
			return (Host) membership.toArray()[nodeIndex];
		} else return null;
	}

	public Host selectRandomDiffPeer(Host node) {
		var randomSelect = this.selectRandomPeer();
		while (!node.equals(randomSelect))
			randomSelect = this.selectRandomPeer();
		return randomSelect;
	}

	public Set<Host> subsetRandomElements(int size) {
		var list = new ArrayList<Host>(membership);
		var subset = new HashSet<Host>();

		Collections.shuffle(list);
		for (int i = 0; i < Math.min(size, membership.size()); i++) {
			subset.add(list.get(i));
		}

		return subset;
	}

	public int getSize() {
		return membership.size();
	}

	public int getCapacity() {
		return this.capacity;
	}

	public boolean isFull() {
		return getCapacity() == getSize();
	}
}
