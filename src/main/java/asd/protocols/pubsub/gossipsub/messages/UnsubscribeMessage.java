package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

public class UnsubscribeMessage extends ProtoMessage {

    public static final short ID = GossipSub.ID + 2;

    private final String topic;

    public UnsubscribeMessage(String topic) {
        super(ID);
        this.topic = topic;
    }

    public String getTopic() {
        return topic;
    }
}
