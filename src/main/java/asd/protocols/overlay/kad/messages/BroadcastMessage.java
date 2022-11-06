package asd.protocols.overlay.kad.messages;

import java.io.IOException;
import java.util.UUID;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class BroadcastMessage extends ProtoMessage {
    public static final short ID = Kademlia.ID + 20;

    public final KadID pool;
    public final int depth;
    public final UUID uuid;
    public final KadPeer origin;
    public final byte[] payload;

    public BroadcastMessage(KadID pool, int depth, UUID uuid, KadPeer origin, byte[] payload) {
        super(ID);
        this.pool = pool;
        this.depth = depth;
        this.uuid = uuid;
        this.origin = origin;
        this.payload = payload;
    }

    public static final ISerializer<BroadcastMessage> serializer = new ISerializer<BroadcastMessage>() {
        @Override
        public void serialize(BroadcastMessage t, ByteBuf out) throws IOException {
            KadID.serializer.serialize(t.pool, out);
            out.writeInt(t.depth);
            out.writeLong(t.uuid.getMostSignificantBits());
            out.writeLong(t.uuid.getLeastSignificantBits());
            KadPeer.serializer.serialize(t.origin, out);
            out.writeInt(t.payload.length);
            out.writeBytes(t.payload);
        }

        @Override
        public BroadcastMessage deserialize(ByteBuf in) throws IOException {
            var pool = KadID.serializer.deserialize(in);
            var depth = in.readInt();
            var uuid = new UUID(in.readLong(), in.readLong());
            var origin = KadPeer.serializer.deserialize(in);
            var payload = new byte[in.readInt()];
            in.readBytes(payload);
            return new BroadcastMessage(pool, depth, uuid, origin, payload);
        }
    };

}
