package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.Map;
import java.util.Set;

public class Prune extends ProtoMessage {

    public static final short ID = GossipSub.ID + 7;

    private String topic;
    private Set<Host> peers;
    public Prune(String topic, Set<Host> peers) {
        super(ID);
    }

    public String getTopic() {
        return topic;
    }

    public Set<Host> getPeers() {
        return peers;
    }
}
