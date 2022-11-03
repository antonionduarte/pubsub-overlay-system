package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import asd.utils.ASDUtils;
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
    private int hopCount;

    public PublishMessage(Host propagationSource, String topic, UUID msgId, byte[] msg) {
        this(propagationSource, topic, msgId, msg, 0);
    }

    private PublishMessage(Host propagationSource, String topic, UUID msgId, byte[] msg, int hopCount) {
        super(ID);
        this.propagationSource = propagationSource;
        this.topic = topic;
        this.msgId = msgId;
        this.msg = msg;
        this.hopCount = hopCount;
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

    public int getHopCount() {
        return hopCount;
    }

    public void incHop() {
        hopCount++;
    }

    public static ISerializer<PublishMessage> serializer = new ISerializer<>() {
        @Override
        public void serialize(PublishMessage publishMessage, ByteBuf byteBuf) throws IOException {
            Host.serializer.serialize(publishMessage.propagationSource, byteBuf);
            ASDUtils.stringSerializer.serialize(publishMessage.topic, byteBuf);
            byteBuf.writeLong(publishMessage.msgId.getMostSignificantBits());
            byteBuf.writeLong(publishMessage.msgId.getLeastSignificantBits());
            byteBuf.writeInt(publishMessage.msg.length);
            byteBuf.writeBytes(publishMessage.msg);
            byteBuf.writeInt(publishMessage.hopCount);
        }

        @Override
        public PublishMessage deserialize(ByteBuf byteBuf) throws IOException {
            var propagationSource = Host.serializer.deserialize(byteBuf);
            var topic = ASDUtils.stringSerializer.deserialize(byteBuf);
            var mostSigBits = byteBuf.readLong();
            var leastSigBits = byteBuf.readLong();
            var msgId = new UUID(mostSigBits, leastSigBits);
            var lenMsg = byteBuf.readInt();
            var msg = new byte[lenMsg];
            byteBuf.readBytes(msg);
            var hopCount = byteBuf.readInt();

            return new PublishMessage(propagationSource, topic, msgId, msg, hopCount);
        }
    };
}
