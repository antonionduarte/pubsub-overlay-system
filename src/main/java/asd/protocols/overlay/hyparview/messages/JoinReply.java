package asd.protocols.overlay.hyparview.messages;

import asd.protocols.overlay.hyparview.Hyparview;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class JoinReply extends ProtoMessage {

	public static final short MESSAGE_ID = Hyparview.PROTOCOL_ID + 7;
	public static ISerializer<JoinReply> serializer = new ISerializer<>() {
		@Override
		public void serialize(JoinReply joinReply, ByteBuf byteBuf) {
		}

		@Override
		public JoinReply deserialize(ByteBuf byteBuf) {
			return new JoinReply();
		}
	};

	public JoinReply() {
		super(MESSAGE_ID);
	}
}
