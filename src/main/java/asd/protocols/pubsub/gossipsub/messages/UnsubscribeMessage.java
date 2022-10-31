package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import asd.utils.ASDUtils;
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
            ASDUtils.stringSerializer.serialize(unsubscribeMessage.topic, byteBuf);
        }

        @Override
        public UnsubscribeMessage deserialize(ByteBuf byteBuf) throws IOException {
            var topic = ASDUtils.stringSerializer.deserialize(byteBuf);

            return new UnsubscribeMessage(topic);
        }
    };
}
