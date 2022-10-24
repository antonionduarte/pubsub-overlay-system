package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

public class SubscribeMessage extends ProtoMessage {

    public static final short ID = GossipSub.ID + 1;

    private final String topic;

    public String getTopic() {
        return topic;
    }

    public SubscribeMessage(String topic) {
        super(ID);
        this.topic = topic;
    }
}
