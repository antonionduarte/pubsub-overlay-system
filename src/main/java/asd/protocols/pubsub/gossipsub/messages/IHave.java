package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import asd.utils.ASDUtils;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.*;

public class IHave extends ProtoMessage {

    public static final short ID = GossipSub.ID + 5;

    private final Map<String, Set<UUID>> msgIdsPerTopic;

    public IHave(Map<String, Set<UUID>> msgIdsPerTopic) {
        super(ID);
        this.msgIdsPerTopic = msgIdsPerTopic;
    }

    public IHave() {
        this(new HashMap<>());
    }

    public void put(String topic, Set<UUID> msgIds) {
        msgIdsPerTopic.put(topic, msgIds);
    }

    public Map<String, Set<UUID>> getMsgIdsPerTopic() {
        return msgIdsPerTopic;
    }

    public static ISerializer<IHave> serializer = new ISerializer<>() {
        @Override
        public void serialize(IHave iHave, ByteBuf byteBuf) throws IOException {
            byteBuf.writeInt(iHave.msgIdsPerTopic.entrySet().size());
            for (var entry : iHave.msgIdsPerTopic.entrySet()) {
                var topic = entry.getKey();
                ASDUtils.stringSerializer.serialize(topic, byteBuf);

                var msgIds = entry.getValue();
                byteBuf.writeInt(msgIds.size());
                for (var msgId : msgIds) {
                    byteBuf.writeLong(msgId.getMostSignificantBits());
                    byteBuf.writeLong(msgId.getLeastSignificantBits());
                }
            }
        }

        @Override
        public IHave deserialize(ByteBuf byteBuf) throws IOException {
            int numEntries = byteBuf.readInt();
            Map<String, Set<UUID>> msgIdsPerTopic = new HashMap<>(numEntries);

            for (int i = 0; i < numEntries; i++) {
                var topic = ASDUtils.stringSerializer.deserialize(byteBuf);

                var numIds = byteBuf.readInt();
                Set<UUID> msgIds = new HashSet<>(numIds);
                for (int j = 0; j < numIds; j++) {
                    var mostSigBits = byteBuf.readLong();
                    var leastSigBits = byteBuf.readLong();
                    msgIds.add(new UUID(mostSigBits, leastSigBits));
                }
                msgIdsPerTopic.put(topic, msgIds);
            }
            return new IHave(msgIdsPerTopic);
        }
    };
}
