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
    public final UUID uuid;
    public final KadPeer origin;
    public final int hop_count;
    public final int ceil;
    public final boolean apply_redundancy;
    public final byte[] payload;

    public BroadcastMessage(KadID rtid, UUID uuid, KadPeer origin, int hop_count, int ceil, boolean apply_redundancy,
            byte[] payload) {
        super(ID);
        this.rtid = rtid;
        this.uuid = uuid;
        this.origin = origin;
        this.hop_count = hop_count;
        this.ceil = ceil;
        this.apply_redundancy = apply_redundancy;
        this.payload = payload;
    }

    @Override
    public MetricsMessage serializeToMetric() {
        return new MetricsMessage("BroadcastMessage")
                .property("topic", TopicRegistry.lookup(rtid))
                .property("message_id", uuid.toString())
                .property("payload", payload.length)
                .property("ceil", ceil)
                .property("hop_count", hop_count);
    }

    public static final ISerializer<BroadcastMessage> serializer = new ISerializer<BroadcastMessage>() {
        @Override
        public void serialize(BroadcastMessage t, ByteBuf out) throws IOException {
            KadID.serializer.serialize(t.rtid, out);
            out.writeLong(t.uuid.getMostSignificantBits());
            out.writeLong(t.uuid.getLeastSignificantBits());
            KadPeer.serializer.serialize(t.origin, out);
            out.writeInt(t.hop_count);
            out.writeInt(t.ceil);
            out.writeBoolean(t.apply_redundancy);
            out.writeInt(t.payload.length);
            out.writeBytes(t.payload);
        }

        @Override
        public BroadcastMessage deserialize(ByteBuf in) throws IOException {
            var rtid = KadID.serializer.deserialize(in);
            var uuid = new UUID(in.readLong(), in.readLong());
            var origin = KadPeer.serializer.deserialize(in);
            var hop_count = in.readInt();
            var ceil = in.readInt();
            var apply_redundancy = in.readBoolean();
            var payload = new byte[in.readInt()];
            in.readBytes(payload);
            return new BroadcastMessage(rtid, uuid, origin, hop_count, ceil, apply_redundancy, payload);
        }
    };
}
