package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

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

    public static ISerializer<UnsubscribeMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(UnsubscribeMessage unsubscribeMessage, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(unsubscribeMessage.topic.getBytes().length);
            byteBuf.writeBytes(unsubscribeMessage.topic.getBytes());
        }

        @Override
        public UnsubscribeMessage deserialize(ByteBuf byteBuf) throws IOException {
            var lenTopic = byteBuf.readInt();
            var topic = new String(byteBuf.readBytes(lenTopic).array());

            return new UnsubscribeMessage(topic);
        }
    };
}
