package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

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

    public static ISerializer<SubscribeMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(SubscribeMessage subscribeMessage, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(subscribeMessage.topic.length());
            byteBuf.writeBytes(subscribeMessage.topic.getBytes());
        }

        @Override
        public SubscribeMessage deserialize(ByteBuf byteBuf) throws IOException {
            var lenTopic = byteBuf.readInt();
            var topic = new String(byteBuf.readBytes(lenTopic).array());

            return new SubscribeMessage(topic);
        }
    };
}
