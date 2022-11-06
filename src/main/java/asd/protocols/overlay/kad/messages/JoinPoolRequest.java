package asd.protocols.overlay.kad.messages;

import java.io.IOException;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class JoinPoolRequest extends ProtoMessage {
    public static final short ID = Kademlia.ID + 2000;

    public final KadID rtid;

    public JoinPoolRequest(KadID rtid) {
        super(ID);
        this.rtid = rtid;
    }

    @Override
    public String toString() {
        return "JoinPoolRequest [pool=" + rtid + "]";
    }

    public static final ISerializer<JoinPoolRequest> serializer = new ISerializer<JoinPoolRequest>() {
        @Override
        public void serialize(JoinPoolRequest t, ByteBuf out) throws IOException {
            KadID.serializer.serialize(t.rtid, out);
        }

        @Override
        public JoinPoolRequest deserialize(ByteBuf in) throws IOException {
            return new JoinPoolRequest(KadID.serializer.deserialize(in));
        }
    };
}
