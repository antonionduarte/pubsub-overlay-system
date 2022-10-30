package asd.protocols.overlay.common.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.Set;

public class NeighbourUp extends ProtoNotification {

	public static final short NOTIFICATION_ID = 1002;

	private final Host neighbour;

	public NeighbourUp(Host neighbour) {
		super(NOTIFICATION_ID);
		this.neighbour = neighbour;
	}

	public Host getNeighbour() {
		return neighbour;
	}

	@Override
	public String toString() {
		return "NeighbourUp{}";
	}

}
