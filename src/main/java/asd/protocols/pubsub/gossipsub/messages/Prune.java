package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

public class Prune extends ProtoMessage {

    public static final short ID = GossipSub.ID + 7;

    public Prune() {
        super(ID);
    }
}
