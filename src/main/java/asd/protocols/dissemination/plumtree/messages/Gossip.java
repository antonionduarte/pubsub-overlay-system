package asd.protocols.dissemination.plumtree.messages;

import asd.protocols.dissemination.plumtree.PlumTree;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class Gossip extends ProtoMessage {
	public static final short MSG_ID = PlumTree.PROTOCOL_ID + 2;

	private final int messageId;
	private final byte[] msg;

	public Gossip(int messageId, byte[] msg) {
		super(MSG_ID);
		this.messageId = messageId;
		this.msg = msg;
	}

	public byte[] getMsg() {
		return msg;
	}

	public int getMessageId() {
		return messageId;
	}

	public static ISerializer<Gossip> serializer = new ISerializer<>() {
		@Override
		public void serialize(Gossip gossip, ByteBuf byteBuf) {
			byteBuf.writeInt(gossip.messageId);
			byteBuf.writeInt(gossip.msg.length);
			byteBuf.writeBytes(gossip.msg);
		}

		@Override
		public Gossip deserialize(ByteBuf byteBuf) {
			int messageId = byteBuf.readInt();
			int msgLength = byteBuf.readInt();
			byte[] msg = new byte[msgLength];
			byteBuf.readBytes(msg);
			return new Gossip(messageId, msg);
		}
	};
}
