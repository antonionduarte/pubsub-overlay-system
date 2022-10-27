package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.Map;
import java.util.Set;

public class Prune extends ProtoMessage {

    public static final short ID = GossipSub.ID + 7;

    private final Map<String, Set<Host>> peersPerTopic;
    public Prune(Map<String, Set<Host>> peersPerTopic) {
        super(ID);
        this.peersPerTopic = peersPerTopic;
    }

    public void append(Prune prune) {
        for (var entry : prune.getPeersPerTopic().entrySet()) {
            peersPerTopic.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    public Map<String, Set<Host>> getPeersPerTopic() {
        return peersPerTopic;
    }
}
