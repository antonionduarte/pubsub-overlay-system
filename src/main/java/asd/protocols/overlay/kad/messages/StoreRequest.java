package asd.protocols.overlay.kad.messages;

import java.io.IOException;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class StoreRequest extends ProtoMessage {
	public static final short ID = Kademlia.ID + 9;

	public final KadID key;
	public final byte[] value;

	public StoreRequest(KadID key, byte[] value) {
		super(ID);
		this.key = key;
		this.value = value;
	}

	@Override
	public String toString() {
		return "StoreRequest{" +
				"key=" + key +
				", value=" + value +
				'}';
	}

	public static final ISerializer<StoreRequest> serializer = new ISerializer<StoreRequest>() {
		@Override
		public void serialize(StoreRequest t, ByteBuf out) throws IOException {
			KadID.serializer.serialize(t.key, out);
			out.writeInt(t.value.length);
			out.writeBytes(t.value);
		}

		@Override
		public StoreRequest deserialize(ByteBuf in) throws IOException {
			var key = KadID.serializer.deserialize(in);
			var value = new byte[in.readInt()];
			in.readBytes(value);
			return new StoreRequest(key, value);
		}
	};
}
