package asd.protocols.overlay.kad.messages;

import java.io.IOException;
import java.util.List;

import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class FindNodeResponse extends ProtoMessage {
    public static final short ID = Kademlia.ID + 2;

    public final KadPeer[] peers;

    public FindNodeResponse(List<KadPeer> closest) {
        super(ID);
        this.peers = closest.toArray(new KadPeer[closest.size()]);
    }

    private FindNodeResponse(KadPeer[] peers) {
        super(ID);
        this.peers = peers;
    }

    public static final ISerializer<FindNodeResponse> serializer = new ISerializer<FindNodeResponse>() {
        @Override
        public void serialize(FindNodeResponse t, ByteBuf out) throws IOException {
            out.writeInt(t.peers.length);
            for (KadPeer peer : t.peers) {
                KadPeer.serializer.serialize(peer, out);
            }
        }

        @Override
        public FindNodeResponse deserialize(ByteBuf in) throws IOException {
            int size = in.readInt();
            KadPeer[] peers = new KadPeer[size];
            for (int i = 0; i < size; i++) {
                peers[i] = KadPeer.serializer.deserialize(in);
            }
            return new FindNodeResponse(peers);
        }
    };
}
