package asd.protocols.overlay.kad.messages;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class FindNodeResponse extends ProtoMessage {
    public static final short ID = Kademlia.ID + 2;

    public final long context;
    public final List<KadPeer> peers;

    public FindNodeResponse(long context, List<KadPeer> closest) {
        super(ID);
        this.context = context;
        this.peers = List.copyOf(closest);
    }

    @Override
    public String toString() {
        return "FindNodeResponse{" +
                "context=" + context +
                ", peers=" + peers +
                '}';
    }

    public static final ISerializer<FindNodeResponse> serializer = new ISerializer<FindNodeResponse>() {
        @Override
        public void serialize(FindNodeResponse t, ByteBuf out) throws IOException {
            out.writeLong(t.context);
            out.writeInt(t.peers.size());
            for (KadPeer peer : t.peers) {
                KadPeer.serializer.serialize(peer, out);
            }
        }

        @Override
        public FindNodeResponse deserialize(ByteBuf in) throws IOException {
            var context = in.readLong();
            var size = in.readInt();
            var peers = new ArrayList<KadPeer>(size);
            for (int i = 0; i < size; i++) {
                peers.add(KadPeer.serializer.deserialize(in));
            }
            return new FindNodeResponse(context, peers);
        }
    };
}
