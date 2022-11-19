package asd.protocols.overlay.kad.messages;

import asd.metrics.MetricsMessage;
import asd.metrics.MetricsProtoMessage;
import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import asd.protocols.overlay.kad.TopicRegistry;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.UUID;

public class BroadcastHave extends MetricsProtoMessage {
	public static final short ID = Kademlia.ID + 21;
	public static final ISerializer<BroadcastHave> serializer = new ISerializer<BroadcastHave>() {
		@Override
		public void serialize(BroadcastHave t, ByteBuf out) throws IOException {
			KadID.serializer.serialize(t.rtid, out);
			out.writeLong(t.uuid.getMostSignificantBits());
			out.writeLong(t.uuid.getLeastSignificantBits());
		}

		@Override
		public BroadcastHave deserialize(ByteBuf in) throws IOException {
			return new BroadcastHave(KadID.serializer.deserialize(in), new UUID(in.readLong(), in.readLong()));
		}
	};
	public final KadID rtid;
	public final UUID uuid;

	public BroadcastHave(KadID pool, UUID uuid) {
		super(ID);
		this.rtid = pool;
		this.uuid = uuid;
	}

	@Override
	public MetricsMessage serializeToMetric() {
		return new MetricsMessage("BroadcastHave")
				.property("topic", TopicRegistry.lookup(this.rtid))
				.property("message_id", this.uuid.toString());
	}
}
