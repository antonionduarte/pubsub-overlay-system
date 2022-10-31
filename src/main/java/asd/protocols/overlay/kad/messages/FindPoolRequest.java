package asd.protocols.overlay.kad.messages;

import java.io.IOException;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class FindPoolRequest extends ProtoMessage {
    public static final short ID = Kademlia.ID + 1000;

    public final long context;
    public final KadID pool;

    public FindPoolRequest(long context, KadID pool) {
        super(ID);
        this.context = context;
        this.pool = pool;
    }

    @Override
    public String toString() {
        return "FindPoolRequest [context=" + context + ", pool=" + pool + "]";
    }

    public static final ISerializer<FindPoolRequest> serializer = new ISerializer<FindPoolRequest>() {
        @Override
        public void serialize(FindPoolRequest t, ByteBuf out) throws IOException {
            out.writeLong(t.context);
            KadID.serializer.serialize(t.pool, out);
        }

        @Override
        public FindPoolRequest deserialize(ByteBuf in) throws IOException {
            return new FindPoolRequest(in.readLong(), KadID.serializer.deserialize(in));
        }
    };

}
