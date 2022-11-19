package asd.protocols.overlay.kad.messages;

import asd.metrics.MetricsProtoMessage;
import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class JoinPoolRequest extends MetricsProtoMessage {
	public static final short ID = Kademlia.ID + 2000;
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
	public final KadID rtid;

	public JoinPoolRequest(KadID rtid) {
		super(ID);
		this.rtid = rtid;
	}

	@Override
	public String toString() {
		return "JoinPoolRequest [pool=" + rtid + "]";
	}
}
