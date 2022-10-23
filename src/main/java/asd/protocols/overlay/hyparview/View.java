package asd.protocols.overlay.hyparview;

import pt.unl.fct.di.novasys.network.data.Host;

import java.sql.Array;
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
	 * @param node The node to add to the view.
	 * @return In case the View's capacity was full, return the node that was dropped.
	 */
	public Host addNode(Host node) {
		Host dropped = null;
		if (!node.equals(self)) {
			if (this.isFull())
				dropped = this.dropRandomElement();
			this.membership.add(node);
		}
		return dropped;
	}

	public boolean removeNode(Host node) {
		return this.membership.remove(node);
	}

	public Host dropRandomElement() {
		var toDrop = this.selectRandomNode();
		this.membership.remove(toDrop);
		return toDrop;
	}

	public Host selectRandomNode() {
		var nodeIndex = random.nextInt(membership.size());
		var list = membership.toArray();
		return (Host) list[nodeIndex];
	}

	public Host selectRandomDiffNode(Host node) {
		var randomSelect = this.selectRandomNode();
		while (!node.equals(randomSelect))
			randomSelect = this.selectRandomNode();
		return randomSelect;
	}

	public Set<Host> subsetRandomElements(int size) {
		List<Host> list = new ArrayList<>(membership);
		Set<Host> subset = new HashSet<>();
		Collections.shuffle(list);
		for (int i = 0; i < size; i++) {
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
