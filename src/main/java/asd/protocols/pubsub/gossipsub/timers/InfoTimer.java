package asd.protocols.pubsub.gossipsub.timers;

import asd.protocols.pubsub.gossipsub.GossipSub;
import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class InfoTimer extends ProtoTimer {

	public static final short ID = GossipSub.ID + 2;

	public InfoTimer() {
		super(ID);
	}

	@Override
	public ProtoTimer clone() {
		return this;
	}
}
