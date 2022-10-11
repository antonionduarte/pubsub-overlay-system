package asd.protocols.overlay.kad.messages;

import java.io.IOException;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class FindNodeRequest extends ProtoMessage {
	public static final short ID = Kademlia.ID + 1;

	public final int context;
	public final KadID target;

	public FindNodeRequest(int context, KadID target) {
		super(ID);
		this.context = context;
		this.target = target;
	}

	@Override
	public String toString() {
		return "FindNodeRequest{" +
				"context=" + context +
				", target=" + target +
				'}';
	}

	public static final ISerializer<FindNodeRequest> serializer = new ISerializer<FindNodeRequest>() {
		@Override
		public void serialize(FindNodeRequest t, ByteBuf out) throws IOException {
			out.writeInt(t.context);
			KadID.serializer.serialize(t.target, out);
		}

		@Override
		public FindNodeRequest deserialize(ByteBuf in) throws IOException {
			return new FindNodeRequest(in.readInt(), KadID.serializer.deserialize(in));
		}
	};
}
