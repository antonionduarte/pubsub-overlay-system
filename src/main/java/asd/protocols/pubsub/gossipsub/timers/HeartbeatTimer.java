package asd.protocols.pubsub.gossipsub.timers;

import asd.protocols.pubsub.gossipsub.GossipSub;
import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class HeartbeatTimer extends ProtoTimer {

    public static final short ID = GossipSub.ID + 1;

    public HeartbeatTimer() {
        super(ID);
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
