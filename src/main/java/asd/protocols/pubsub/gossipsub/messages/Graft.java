package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.dissemination.plumtree.messages.Gossip;
import asd.protocols.pubsub.gossipsub.GossipSub;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

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
        public void serialize(Graft graft, ByteBuf byteBuf) {
            byteBuf.writeInt(graft.topics.size());
            for (var topic : graft.topics) {
                byteBuf.writeInt(topic.getBytes().length);
                byteBuf.writeBytes(topic.getBytes());
            }
        }

        @Override
        public Graft deserialize(ByteBuf byteBuf) {
            var numTopics = byteBuf.readInt();
            Set<String> topics = new HashSet<>(numTopics);

            for (int i = 0; i < numTopics; i++) {
                var lenTopic = byteBuf.readInt();
                var topic = new String(byteBuf.readBytes(lenTopic).array());
                topics.add(topic);
            }
            return new Graft(topics);
        }
    };
}
