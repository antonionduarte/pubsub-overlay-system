package asd.protocols.overlay.hyparview.messages;

import asd.protocols.overlay.hyparview.Hyparview;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class NeighborReply extends ProtoMessage {

	public static final short MESSAGE_ID = Hyparview.PROTOCOL_ID + 6;
	public static ISerializer<NeighborReply> serializer = new ISerializer<>() {
		@Override
		public void serialize(NeighborReply neighborReply, ByteBuf byteBuf) {
			byteBuf.writeBoolean(neighborReply.isNeighbourAccepted);
		}

		@Override
		public NeighborReply deserialize(ByteBuf byteBuf) {
			return new NeighborReply(byteBuf.readBoolean());
		}
	};
	private final boolean isNeighbourAccepted;

	public NeighborReply(boolean isNeighbourAccepted) {
		super(MESSAGE_ID);
		this.isNeighbourAccepted = isNeighbourAccepted;
	}

	public boolean isNeighbourAccepted() {
		return this.isNeighbourAccepted;
	}
}
