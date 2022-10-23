package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

public class PX extends ProtoMessage {

    public static final short ID = GossipSub.ID + 8;

    public PX() {
        super(ID);
    }
}
