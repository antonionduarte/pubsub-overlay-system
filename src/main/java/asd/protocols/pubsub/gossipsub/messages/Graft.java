package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

import java.util.Set;

public class Graft extends ProtoMessage {

    public static final short ID = GossipSub.ID + 4;

    private Set<String> topics;

    public Graft() {
        super(ID);
    }

    public Set<String> getTopics() {
        return topics;
    }
}
