package asd.protocols.overlay.kad.messages;

import java.io.IOException;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class Handshake extends ProtoMessage {
    public static final short ID = Kademlia.ID + 7;

    public final KadID id;

    public Handshake(KadID id) {
        super(ID);
        this.id = id;
    }

    public static final ISerializer<Handshake> serializer = new ISerializer<Handshake>() {
        @Override
        public void serialize(Handshake t, ByteBuf out) throws IOException {
            KadID.serializer.serialize(t.id, out);
        }

        @Override
        public Handshake deserialize(ByteBuf in) throws IOException {
            return new Handshake(KadID.serializer.deserialize(in));
        }
    };
}
