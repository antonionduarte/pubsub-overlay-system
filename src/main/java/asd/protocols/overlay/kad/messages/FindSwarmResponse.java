package asd.protocols.overlay.kad.messages;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class FindSwarmResponse extends ProtoMessage {
    public static final short ID = Kademlia.ID + 4;

    public final long context;
    // Closest known peers to the swarm
    public final List<KadPeer> peers;
    // Known swarm members
    public final List<KadPeer> members;

    public FindSwarmResponse(long context, List<KadPeer> closest) {
        this(context, closest, new ArrayList<>());
    }

    public FindSwarmResponse(long context, List<KadPeer> closest, List<KadPeer> members) {
        super(ID);
        this.context = context;
        this.peers = closest;
        this.members = members;
    }

    @Override
    public String toString() {
        return "FindSwarmResponse [context=" + context + ", peers=" + peers + ", members=" + members + "]";
    }

    public static final ISerializer<FindSwarmResponse> serializer = new ISerializer<FindSwarmResponse>() {
        @Override
        public void serialize(FindSwarmResponse t, ByteBuf out) throws IOException {
            out.writeLong(t.context);
            out.writeInt(t.peers.size());
            for (KadPeer peer : t.peers) {
                KadPeer.serializer.serialize(peer, out);
            }
            out.writeInt(t.members.size());
            for (KadPeer peer : t.members) {
                KadPeer.serializer.serialize(peer, out);
            }
        }

        @Override
        public FindSwarmResponse deserialize(ByteBuf in) throws IOException {
            long context = in.readLong();
            int peersSize = in.readInt();
            List<KadPeer> peers = new ArrayList<>(peersSize);
            for (int i = 0; i < peersSize; i++) {
                peers.add(KadPeer.serializer.deserialize(in));
            }
            int membersSize = in.readInt();
            List<KadPeer> members = new ArrayList<>(membersSize);
            for (int i = 0; i < membersSize; i++) {
                members.add(KadPeer.serializer.deserialize(in));
            }
            return new FindSwarmResponse(context, peers, members);
        }
    };
}
