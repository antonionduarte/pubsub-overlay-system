package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.dissemination.plumtree.messages.Gossip;
import asd.protocols.pubsub.gossipsub.GossipSub;
import asd.utils.ASDUtils;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.HashSet;
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

    public static ISerializer<Graft> serializer = new ISerializer<>() {
        @Override
        public void serialize(Graft graft, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(graft.topics.size());
            for (var topic : graft.topics) {
                ASDUtils.stringSerializer.serialize(topic, byteBuf);
            }
        }

        @Override
        public Graft deserialize(ByteBuf byteBuf) throws IOException {
            var numTopics = byteBuf.readInt();
            Set<String> topics = new HashSet<>(numTopics);

            for (int i = 0; i < numTopics; i++) {
                var topic = ASDUtils.stringSerializer.deserialize(byteBuf);
                topics.add(topic);
            }
            return new Graft(topics);
        }
    };
}
