package asd.protocols.overlay.kad.messages;

import java.io.IOException;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class FindValueRequest extends ProtoMessage {
	public static final short ID = Kademlia.ID + 3;

	public final KadID key;

	public FindValueRequest(KadID key) {
		super(ID);
		this.key = key;
	}

	@Override
	public String toString() {
		return "FindValueRequest{" +
				"key=" + key +
				'}';
	}

	public static final ISerializer<FindValueRequest> serializer = new ISerializer<FindValueRequest>() {
		@Override
		public void serialize(FindValueRequest t, ByteBuf out) throws IOException {
			KadID.serializer.serialize(t.key, out);
		}

		@Override
		public FindValueRequest deserialize(ByteBuf in) throws IOException {
			return new FindValueRequest(KadID.serializer.deserialize(in));
		}
	};
}
