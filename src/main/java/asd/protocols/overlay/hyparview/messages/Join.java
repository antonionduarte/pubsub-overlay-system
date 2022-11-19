package asd.protocols.overlay.hyparview.messages;

import asd.protocols.overlay.hyparview.Hyparview;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class Join extends ProtoMessage {

	public static final short MESSAGE_ID = Hyparview.PROTOCOL_ID + 1;
	public static final ISerializer<Join> serializer = new ISerializer<>() {
		@Override
		public void serialize(Join join, ByteBuf byteBuf) {
		}

		@Override
		public Join deserialize(ByteBuf byteBuf) {
			return new Join();
		}
	};

	public Join() {
		super(MESSAGE_ID);
	}
}
