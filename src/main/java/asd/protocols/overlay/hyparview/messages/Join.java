package asd.protocols.overlay.hyparview.messages;

import asd.protocols.overlay.hyparview.Hyparview;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class Join extends ProtoMessage {

	public static final short MESSAGE_ID = Hyparview.PROTOCOL_ID + 1;

	public Join(short id) {
		super(id);
	}

	public static final ISerializer<Join> serializer = new ISerializer<Join>() {
		@Override
		public void serialize(Join join, ByteBuf byteBuf) throws IOException {

		}

		@Override
		public Join deserialize(ByteBuf byteBuf) throws IOException {
			return null;
		}
	};
}
