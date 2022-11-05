package asd.protocols.overlay.kad.messages;

import java.io.IOException;
import java.util.Optional;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class FindNodeRequest extends ProtoMessage {
	public static final short ID = Kademlia.ID + 1;

	public final long context;
	public final KadID target;
	public final Optional<KadID> pool;

	public FindNodeRequest(long context, KadID target) {
		super(ID);
		this.context = context;
		this.target = target;
		this.pool = Optional.empty();
	}

	public FindNodeRequest(long context, KadID target, KadID pool) {
		super(ID);
		this.context = context;
		this.target = target;
		this.pool = Optional.of(pool);
	}

	public FindNodeRequest(long context, KadID target, Optional<KadID> pool) {
		super(ID);
		this.context = context;
		this.target = target;
		this.pool = pool;
	}

	@Override
	public String toString() {
		return "FindNodeRequest [context=" + context + ", target=" + target + ", pool=" + pool + "]";
	}

	public static final ISerializer<FindNodeRequest> serializer = new ISerializer<FindNodeRequest>() {

		@Override
		public void serialize(FindNodeRequest m, ByteBuf out) throws IOException {
			out.writeLong(m.context);
			KadID.serializer.serialize(m.target, out);
			if (m.pool.isPresent()) {
				out.writeBoolean(true);
				KadID.serializer.serialize(m.pool.get(), out);
			} else {
				out.writeBoolean(false);
			}
		}

		@Override
		public FindNodeRequest deserialize(ByteBuf in) throws IOException {
			long context = in.readLong();
			KadID target = KadID.serializer.deserialize(in);
			if (in.readBoolean()) {
				KadID pool = KadID.serializer.deserialize(in);
				return new FindNodeRequest(context, target, pool);
			} else {
				return new FindNodeRequest(context, target);
			}
		}
	};
}
