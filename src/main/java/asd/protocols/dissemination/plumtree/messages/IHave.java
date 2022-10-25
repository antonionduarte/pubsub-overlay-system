package asd.protocols.dissemination.plumtree.messages;

import asd.protocols.dissemination.plumtree.PlumTree;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class IHave extends ProtoMessage {
	public static final short MSG_ID = PlumTree.PROTOCOL_ID + 4;

	private final int messageId;

	public IHave(int messageId) {
		super(MSG_ID);
		this.messageId = messageId;
	}

	public int getMessageId() {
		return messageId;
	}

	ISerializer<IHave> serializer = new ISerializer<>() {
		@Override
		public void serialize(IHave iHave, ByteBuf byteBuf) {
			byteBuf.writeInt(iHave.messageId);
		}

		@Override
		public IHave deserialize(ByteBuf byteBuf) {
			int messageId = byteBuf.readInt();
			return new IHave(messageId);
		}
	};
}
