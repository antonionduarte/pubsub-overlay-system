package asd.protocols.overlay.kad.messages;

import asd.metrics.MetricsProtoMessage;
import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class FindValueRequest extends MetricsProtoMessage {
	public static final short ID = Kademlia.ID + 5;
	public static final ISerializer<FindValueRequest> serializer = new ISerializer<FindValueRequest>() {
		@Override
		public void serialize(FindValueRequest t, ByteBuf out) throws IOException {
			out.writeLong(t.context);
			KadID.serializer.serialize(t.key, out);
		}

		@Override
		public FindValueRequest deserialize(ByteBuf in) throws IOException {
			return new FindValueRequest(in.readLong(), KadID.serializer.deserialize(in));
		}
	};
	public final long context;
	public final KadID key;

	public FindValueRequest(long context, KadID key) {
		super(ID);
		this.context = context;
		this.key = key;
	}

	@Override
	public String toString() {
		return "FindValueRequest{" +
				"context=" + context +
				", key=" + key +
				'}';
	}
}
