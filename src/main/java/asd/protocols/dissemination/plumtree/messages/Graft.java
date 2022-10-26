package asd.protocols.dissemination.plumtree.messages;

import asd.protocols.dissemination.plumtree.PlumTree;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class Graft extends ProtoMessage {
	public static final short MSG_ID = PlumTree.PROTOCOL_ID + 3;

	private int messageId;

	public Graft(int messageId) {
		super(MSG_ID);
		this.messageId = messageId;
	}

	public int getMessageId() {
		return messageId;
	}

	public static ISerializer<Graft> serializer = new ISerializer<>() {
		@Override
		public void serialize(Graft graft, ByteBuf byteBuf) {
			byteBuf.writeInt(graft.messageId);
		}

		@Override
		public Graft deserialize(ByteBuf byteBuf) {
			return new Graft(byteBuf.readInt());
		}
	};
}
