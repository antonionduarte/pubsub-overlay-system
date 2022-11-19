package asd.protocols.dissemination.plumtree.messages;

import asd.protocols.dissemination.plumtree.PlumTree;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.util.UUID;

public class IHave extends ProtoMessage {
	public static final short MSG_ID = PlumTree.PROTOCOL_ID + 4;
	public static ISerializer<IHave> serializer = new ISerializer<>() {
		@Override
		public void serialize(IHave iHave, ByteBuf byteBuf) {
			byteBuf.writeLong(iHave.msgId.getMostSignificantBits());
			byteBuf.writeLong(iHave.msgId.getLeastSignificantBits());
		}

		@Override
		public IHave deserialize(ByteBuf byteBuf) {
			var mostSigBits = byteBuf.readLong();
			var leastSigBits = byteBuf.readLong();
			var msgId = new UUID(mostSigBits, leastSigBits);
			return new IHave(msgId);
		}
	};
	private final UUID msgId;

	public IHave(UUID msgId) {
		super(MSG_ID);
		this.msgId = msgId;
	}

	public UUID getMsgId() {
		return msgId;
	}
}
