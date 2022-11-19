package asd.protocols.overlay.hyparview.messages;

import asd.protocols.overlay.hyparview.Hyparview;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class Disconnect extends ProtoMessage {

	public static final short MESSAGE_ID = Hyparview.PROTOCOL_ID + 3;
	public static final ISerializer<Disconnect> serializer = new ISerializer<>() {
		@Override
		public void serialize(Disconnect disconnect, ByteBuf byteBuf) {
		}

		@Override
		public Disconnect deserialize(ByteBuf byteBuf) {
			return new Disconnect();
		}
	};

	public Disconnect() {
		super(MESSAGE_ID);
	}
}
