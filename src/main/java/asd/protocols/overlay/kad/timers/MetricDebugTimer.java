package asd.protocols.overlay.kad.timers;

import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class MetricDebugTimer extends ProtoTimer {
	public static final short ID = Kademlia.ID + 50;

	public MetricDebugTimer() {
		super(ID);
	}

	@Override
	public ProtoTimer clone() {
		return this;
	}
}
