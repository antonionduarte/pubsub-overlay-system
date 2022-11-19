package asd.protocols.overlay.kad.messages;

import asd.metrics.MetricsProtoMessage;
import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class FindNodeRequest extends MetricsProtoMessage {
	public static final short ID = Kademlia.ID + 1;
	public static final ISerializer<FindNodeRequest> serializer = new ISerializer<FindNodeRequest>() {

		@Override
		public void serialize(FindNodeRequest m, ByteBuf out) throws IOException {
			out.writeLong(m.context);
			KadID.serializer.serialize(m.rtid, out);
			KadID.serializer.serialize(m.target, out);
		}

		@Override
		public FindNodeRequest deserialize(ByteBuf in) throws IOException {
			return new FindNodeRequest(in.readLong(), KadID.serializer.deserialize(in),
					KadID.serializer.deserialize(in));
		}
	};
	public final long context;
	public final KadID rtid;
	public final KadID target;

	public FindNodeRequest(long context, KadID target) {
		super(ID);
		this.context = context;
		this.rtid = KadID.DEFAULT_RTID;
		this.target = target;
	}

	public FindNodeRequest(long context, KadID rtid, KadID target) {
		super(ID);
		this.context = context;
		this.target = target;
		this.rtid = rtid;
	}

	@Override
	public String toString() {
		return "FindNodeRequest [context=" + context + ", target=" + target + ", rtid=" + rtid + "]";
	}
}
