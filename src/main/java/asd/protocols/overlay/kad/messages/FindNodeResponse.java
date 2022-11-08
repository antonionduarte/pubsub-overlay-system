package asd.protocols.overlay.kad.messages;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import asd.metrics.MetricsProtoMessage;
import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

public class FindNodeResponse extends MetricsProtoMessage {
    public static final short ID = Kademlia.ID + 2;

    public final long context;
    public final List<KadPeer> peers;
    public final Optional<KadID> pool;

    public FindNodeResponse(long context, List<KadPeer> closest) {
        super(ID);
        this.context = context;
        this.peers = List.copyOf(closest);
        this.pool = Optional.empty();
    }

    public FindNodeResponse(long context, List<KadPeer> closest, KadID pool) {
        super(ID);
        this.context = context;
        this.peers = List.copyOf(closest);
        this.pool = Optional.of(pool);
    }

    public FindNodeResponse(long context, List<KadPeer> closest, Optional<KadID> pool) {
        super(ID);
        this.context = context;
        this.peers = List.copyOf(closest);
        this.pool = pool;
    }

    @Override
    public String toString() {
        return "FindNodeResponse [context=" + context + ", peers=" + peers + ", pool=" + pool + "]";
    }

    public static final ISerializer<FindNodeResponse> serializer = new ISerializer<FindNodeResponse>() {

        @Override
        public void serialize(FindNodeResponse m, ByteBuf out) throws IOException {
            out.writeLong(m.context);
            out.writeInt(m.peers.size());
            for (KadPeer peer : m.peers) {
                KadPeer.serializer.serialize(peer, out);
            }
            if (m.pool.isPresent()) {
                out.writeBoolean(true);
                KadID.serializer.serialize(m.pool.get(), out);
            } else {
                out.writeBoolean(false);
            }
        }

        @Override
        public FindNodeResponse deserialize(ByteBuf in) throws IOException {
            long context = in.readLong();
            int size = in.readInt();
            List<KadPeer> peers = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                peers.add(KadPeer.serializer.deserialize(in));
            }
            if (in.readBoolean()) {
                KadID pool = KadID.serializer.deserialize(in);
                return new FindNodeResponse(context, peers, pool);
            } else {
                return new FindNodeResponse(context, peers);
            }
        }
    };
}
