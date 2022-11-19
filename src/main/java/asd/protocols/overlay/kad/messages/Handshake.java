package asd.protocols.overlay.kad.messages;

import asd.metrics.MetricsProtoMessage;
import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class Handshake extends MetricsProtoMessage {
	public static final short ID = Kademlia.ID + 7;
	public static final ISerializer<Handshake> serializer = new ISerializer<Handshake>() {
		@Override
		public void serialize(Handshake t, ByteBuf out) throws IOException {
			KadID.serializer.serialize(t.id, out);
		}

		@Override
		public Handshake deserialize(ByteBuf in) throws IOException {
			return new Handshake(KadID.serializer.deserialize(in));
		}
	};
	public final KadID id;

	public Handshake(KadID id) {
		super(ID);
		this.id = id;
	}
}
