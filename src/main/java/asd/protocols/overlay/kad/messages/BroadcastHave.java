package asd.protocols.overlay.kad.messages;

import java.io.IOException;
import java.util.UUID;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class BroadcastHave extends ProtoMessage {
    public static final short ID = Kademlia.ID + 21;

    public final KadID pool;
    public final UUID uuid;

    public BroadcastHave(KadID pool, UUID uuid) {
        super(ID);
        this.pool = pool;
        this.uuid = uuid;
    }

    public static final ISerializer<BroadcastHave> serializer = new ISerializer<BroadcastHave>() {
        @Override
        public void serialize(BroadcastHave t, ByteBuf out) throws IOException {
            KadID.serializer.serialize(t.pool, out);
            out.writeLong(t.uuid.getMostSignificantBits());
            out.writeLong(t.uuid.getLeastSignificantBits());
        }

        @Override
        public BroadcastHave deserialize(ByteBuf in) throws IOException {
            return new BroadcastHave(KadID.serializer.deserialize(in), new UUID(in.readLong(), in.readLong()));
        }
    };
}
