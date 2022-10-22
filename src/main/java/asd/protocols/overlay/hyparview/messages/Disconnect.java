package asd.protocols.overlay.hyparview.messages;

import asd.protocols.overlay.hyparview.Hyparview;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class Disconnect extends ProtoMessage {

	public static final short MESSAGE_ID = Hyparview.PROTOCOL_ID + 3;

	public Disconnect(short id) {
		super(id);
	}

	public static final ISerializer<Disconnect> serializer = new ISerializer<Disconnect>() {
		@Override
		public void serialize(Disconnect disconnect, ByteBuf byteBuf) throws IOException {

		}

		@Override
		public Disconnect deserialize(ByteBuf byteBuf) throws IOException {
			return null;
		}
	};
}
