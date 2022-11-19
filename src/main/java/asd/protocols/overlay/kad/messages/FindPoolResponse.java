package asd.protocols.overlay.kad.messages;

import asd.metrics.MetricsProtoMessage;
import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FindPoolResponse extends MetricsProtoMessage {
	public static final short ID = Kademlia.ID + 10000;
	public static final ISerializer<FindPoolResponse> serializer = new ISerializer<FindPoolResponse>() {
		@Override
		public void serialize(FindPoolResponse t, ByteBuf out) throws IOException {
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
		public FindPoolResponse deserialize(ByteBuf in) throws IOException {
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
			return new FindPoolResponse(context, peers, members);
		}
	};
	public final long context;
	// Closest known peers to the swarm
	public final List<KadPeer> peers;
	// Known pool members
	public final List<KadPeer> members;

	public FindPoolResponse(long context, List<KadPeer> closest) {
		this(context, closest, new ArrayList<>());
	}

	public FindPoolResponse(long context, List<KadPeer> closest, List<KadPeer> members) {
		super(ID);
		this.context = context;
		this.peers = closest;
		this.members = members;
	}

	@Override
	public String toString() {
		return "FindPoolResponse [context=" + context + ", peers=" + peers + ", members=" + members + "]";
	}
}
