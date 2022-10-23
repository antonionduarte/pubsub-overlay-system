package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

import java.util.Set;
import java.util.UUID;

public class IWant extends ProtoMessage {

    public static final short ID = GossipSub.ID + 6;

    private final Set<UUID> messageIds;

    public IWant(Set<UUID> messageIds) {
        super(ID);
        this.messageIds = messageIds;
    }

    public Set<UUID> getMessageIds() {
        return messageIds;
    }
}
