package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.UUID;

public class PublishMessage extends ProtoMessage {

    public static final short ID = GossipSub.ID + 3;

    private final Host propagationSource;
    private final String topic;
    private final UUID msgId;
    private final byte[] msg;

    public PublishMessage(Host propagationSource, String topic, UUID msgId, byte[] msg) {
        super(ID);
        this.propagationSource = propagationSource;
        this.topic = topic;
        this.msgId = msgId;
        this.msg = msg;
    }

    public Host getPropagationSource() {
        return propagationSource;
    }

    public String getTopic() {
        return topic;
    }

    public UUID getMsgId() {
        return msgId;
    }

    public byte[] getMsg() {
        return msg;
    }

    public static ISerializer<PublishMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(PublishMessage publishMessage, ByteBuf byteBuf) throws IOException {
            Host.serializer.serialize(publishMessage.propagationSource, byteBuf);
            byteBuf.writeInt(publishMessage.topic.length());
            byteBuf.writeBytes(publishMessage.topic.getBytes());
            byteBuf.writeLong(publishMessage.msgId.getMostSignificantBits());
            byteBuf.writeLong(publishMessage.msgId.getLeastSignificantBits());
            byteBuf.writeInt(publishMessage.msg.length);
            byteBuf.writeBytes(publishMessage.msg);
        }

        @Override
        public PublishMessage deserialize(ByteBuf byteBuf) throws IOException {
            var propagationSource = Host.serializer.deserialize(byteBuf);
            var lenTopic = byteBuf.readInt();
            var topic = new String(byteBuf.readBytes(lenTopic).array());
            var mostSigBits = byteBuf.readLong();
            var leastSigBits = byteBuf.readLong();
            var msgId = new UUID(mostSigBits, leastSigBits);
            var lenMsg = byteBuf.readInt();
            var msg = byteBuf.readBytes(lenMsg).array();

            return new PublishMessage(propagationSource, topic, msgId, msg);
        }
    };
}
