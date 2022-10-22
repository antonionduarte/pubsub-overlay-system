package asd.protocols.overlay.hyparview;

import pt.unl.fct.di.novasys.network.data.Host;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public class View {

	private final Set<Host> membership;
	private final Random random;

	private final int size;

	public View(int size) {
		this.membership = new HashSet<>();
		this.random = new Random();
		this.size = size;
	}

	public Set<Host> getView() {
		return this.membership;
	}

	public void addNode(Host node) {
		this.membership.add(node);
	}

	public void removeNode(Host node) {
		this.membership.remove(node);
	}

	public void dropRandomElement() {

	}

	public int getSize() {
		return membership.size();
	}
}
