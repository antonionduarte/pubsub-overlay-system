package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

import java.util.Set;

public class Graft extends ProtoMessage {

    public static final short ID = GossipSub.ID + 4;

    private final Set<String> topics;

    public Graft(Set<String> topics) {
        super(ID);
        this.topics = topics;
    }

    public Set<String> getTopics() {
        return topics;
    }
}
