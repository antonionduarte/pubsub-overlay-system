package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class IHave extends ProtoMessage {

    public static final short ID = GossipSub.ID + 5;

    private final Map<String, Set<UUID>> msgIdsPerTopic;

    public IHave(Map<String, Set<UUID>> msgIdsPerTopic) {
        super(ID);
        this.msgIdsPerTopic = msgIdsPerTopic;
    }

    public IHave() {
        this(new HashMap<>());
    }

    public void put(String topic, Set<UUID> msgIds) {
        msgIdsPerTopic.put(topic, msgIds);
    }

    public Map<String, Set<UUID>> getMsgIdsPerTopic() {
        return msgIdsPerTopic;
    }
}
