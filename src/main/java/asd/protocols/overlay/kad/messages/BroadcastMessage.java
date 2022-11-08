package asd.protocols.overlay.kad.messages;

import java.io.IOException;
import java.util.UUID;

import asd.metrics.MetricsMessage;
import asd.metrics.MetricsProtoMessage;
import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.Kademlia;
import asd.protocols.overlay.kad.TopicRegistry;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

public class BroadcastMessage extends MetricsProtoMessage {
    public static final short ID = Kademlia.ID + 20;

    public final KadID rtid;
    public final int depth;
    public final UUID uuid;
    public final KadPeer origin;
    public final byte[] payload;
    public final int hop_count;

    public BroadcastMessage(KadID rtid, int depth, UUID uuid, KadPeer origin, byte[] payload, int hop_count) {
        super(ID);
        this.rtid = rtid;
        this.depth = depth;
        this.uuid = uuid;
        this.origin = origin;
        this.payload = payload;
        this.hop_count = hop_count;
    }

    public static final ISerializer<BroadcastMessage> serializer = new ISerializer<BroadcastMessage>() {
        @Override
        public void serialize(BroadcastMessage t, ByteBuf out) throws IOException {
            KadID.serializer.serialize(t.rtid, out);
            out.writeInt(t.depth);
            out.writeLong(t.uuid.getMostSignificantBits());
            out.writeLong(t.uuid.getLeastSignificantBits());
            KadPeer.serializer.serialize(t.origin, out);
            out.writeInt(t.payload.length);
            out.writeBytes(t.payload);
            out.writeInt(t.hop_count);
        }

        @Override
        public BroadcastMessage deserialize(ByteBuf in) throws IOException {
            var pool = KadID.serializer.deserialize(in);
            var depth = in.readInt();
            var uuid = new UUID(in.readLong(), in.readLong());
            var origin = KadPeer.serializer.deserialize(in);
            var payload = new byte[in.readInt()];
            in.readBytes(payload);
            var hop_count = in.readInt();
            return new BroadcastMessage(pool, depth, uuid, origin, payload, hop_count);
        }
    };

    @Override
    public MetricsMessage serializeToMetric() {
        return new MetricsMessage("BroadcastMessage")
                .property("topic", TopicRegistry.lookup(rtid))
                .property("depth", depth)
                .property("message_id", uuid.toString())
                .property("payload", payload.length)
                .property("hop_count", hop_count);
    }
}
