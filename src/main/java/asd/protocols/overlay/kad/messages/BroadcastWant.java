package asd.protocols.overlay.kad.messages;

import java.io.IOException;
import java.util.UUID;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class BroadcastWant extends ProtoMessage {
    public static final short ID = Kademlia.ID + 23;

    public final KadID rtid;
    public final UUID uuid;

    public BroadcastWant(KadID rtid, UUID uuid) {
        super(ID);
        this.rtid = rtid;
        this.uuid = uuid;
    }

    public static final ISerializer<BroadcastWant> serializer = new ISerializer<BroadcastWant>() {
        @Override
        public void serialize(BroadcastWant t, ByteBuf out) throws IOException {
            KadID.serializer.serialize(t.rtid, out);
            out.writeLong(t.uuid.getMostSignificantBits());
            out.writeLong(t.uuid.getLeastSignificantBits());
        }

        @Override
        public BroadcastWant deserialize(ByteBuf in) throws IOException {
            return new BroadcastWant(KadID.serializer.deserialize(in), new UUID(in.readLong(), in.readLong()));
        }
    };
}
