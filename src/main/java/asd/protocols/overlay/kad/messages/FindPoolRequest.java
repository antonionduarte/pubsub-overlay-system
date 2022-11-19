package asd.protocols.overlay.kad.messages;

import asd.metrics.MetricsProtoMessage;
import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class FindPoolRequest extends MetricsProtoMessage {
	public static final short ID = Kademlia.ID + 1000;
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

}
