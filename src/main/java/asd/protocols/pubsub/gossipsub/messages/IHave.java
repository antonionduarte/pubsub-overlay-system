package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class IHave extends ProtoMessage {

    public static final short ID = GossipSub.ID + 5;

    private Map<String, Set<UUID>> msgIdsPerTopic;

    public IHave() {
        super(ID);
    }

    public Map<String, Set<UUID>> getMsgIdsPerTopic() {
        return msgIdsPerTopic;
    }
}
