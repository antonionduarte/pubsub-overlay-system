package asd.protocols.overlay.kad.messages;

import java.io.IOException;
import java.util.UUID;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class BroadcastMessage extends ProtoMessage {
    public static final short ID = Kademlia.ID + 20;

    public final KadID pool;
    public final int depth;
    public final UUID uuid;
    public final byte[] payload;

    public BroadcastMessage(KadID pool, int depth, UUID uuid, byte[] payload) {
        super(ID);
        this.pool = pool;
        this.depth = depth;
        this.uuid = uuid;
        this.payload = payload;
    }

    public static final ISerializer<BroadcastMessage> serializer = new ISerializer<BroadcastMessage>() {
        @Override
        public void serialize(BroadcastMessage broadcastRequest, ByteBuf out) throws IOException {
            KadID.serializer.serialize(broadcastRequest.pool, out);
            out.writeInt(broadcastRequest.depth);
            out.writeLong(broadcastRequest.uuid.getMostSignificantBits());
            out.writeLong(broadcastRequest.uuid.getLeastSignificantBits());
            out.writeInt(broadcastRequest.payload.length);
            out.writeBytes(broadcastRequest.payload);
        }

        @Override
        public BroadcastMessage deserialize(ByteBuf in) throws IOException {
            KadID pool = KadID.serializer.deserialize(in);
            int depth = in.readInt();
            long msb = in.readLong();
            long lsb = in.readLong();
            UUID uuid = new UUID(msb, lsb);
            int length = in.readInt();
            byte[] payload = new byte[length];
            in.readBytes(payload);
            return new BroadcastMessage(pool, depth, uuid, payload);
        }
    };

}
