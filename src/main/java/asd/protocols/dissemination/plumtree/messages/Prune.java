package asd.protocols.dissemination.plumtree.messages;

import asd.protocols.dissemination.plumtree.PlumTree;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class Prune extends ProtoMessage {
	public static final short MSG_ID = PlumTree.PROTOCOL_ID + 2;

	public Prune() {
		super(MSG_ID);
	}

	public static ISerializer<Prune> serializer = new ISerializer<>() {
		@Override
		public void serialize(Prune prune, ByteBuf byteBuf) {
		}

		@Override
		public Prune deserialize(ByteBuf byteBuf) {
			return new Prune();
		}
	};
}
