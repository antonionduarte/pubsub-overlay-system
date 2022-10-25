package asd.protocols.overlay.common.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.HashSet;
import java.util.Set;

public class NeighbourUp extends ProtoNotification {

	public static final short NOTIFICATION_ID = 101;

	private final Set<Host> neighbours;

	public NeighbourUp(Host neighbour) {
		super(NOTIFICATION_ID);
		this.neighbours = new HashSet<>();
		this.neighbours.add(neighbour);
	}

	public Set<Host> getNeighbours() {
		return neighbours;
	}

	public int getLength() {
		return neighbours.size();
	}

	@Override
	public String toString() {
		return "NeighbourUp{}";
	}

}
