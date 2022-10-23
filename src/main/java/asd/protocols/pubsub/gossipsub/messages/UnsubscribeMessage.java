package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

public class UnsubscribeMessage extends ProtoMessage {

    public static final short ID = GossipSub.ID + 2;

    private String topic;

    public String getTopic() {
        return topic;
    }

    public UnsubscribeMessage() {
        super(ID);
    }
}
