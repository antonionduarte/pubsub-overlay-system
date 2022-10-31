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

	/**
	 *
	 * @param peer The node to add to the view.
	 * @return In case the View's capacity was full, return the node that was dropped.
	 */
	public Host addPeer(Host peer) {
		Host dropped = null;
		if (!peer.equals(self)) {
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
		var nodeIndex = random.nextInt(membership.size());
		var list = membership.toArray();
		return (Host) list[nodeIndex];
	}

	public Host selectRandomDiffPeer(Host node) {
		var randomSelect = this.selectRandomPeer();
		while (!node.equals(randomSelect))
			randomSelect = this.selectRandomPeer();
		return randomSelect;
	}

	public Set<Host> subsetRandomElements(int size) {
		List<Host> list = new ArrayList<>(membership);
		Set<Host> subset = new HashSet<>();
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
