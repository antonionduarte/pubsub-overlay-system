package asd.protocols.dissemination.plumtree.messages;

import asd.protocols.dissemination.plumtree.PlumTree;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.util.UUID;

public class Graft extends ProtoMessage {
	public static final short MSG_ID = PlumTree.PROTOCOL_ID + 3;

	private final UUID msgId;

	public Graft(UUID msgId) {
		super(MSG_ID);
		this.msgId = msgId;
	}

	public UUID getMsgId() {
		return msgId;
	}

	public static ISerializer<Graft> serializer = new ISerializer<>() {
		@Override
		public void serialize(Graft graft, ByteBuf byteBuf) {
			byteBuf.writeLong(graft.msgId.getMostSignificantBits());
			byteBuf.writeLong(graft.msgId.getLeastSignificantBits());
		}

		@Override
		public Graft deserialize(ByteBuf byteBuf) {
			var mostSigBits = byteBuf.readLong();
			var leastSigBits = byteBuf.readLong();
			var msgId = new UUID(mostSigBits, leastSigBits);
			return new Graft(msgId);
		}
	};
}
