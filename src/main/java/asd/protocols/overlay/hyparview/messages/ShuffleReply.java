package asd.protocols.overlay.hyparview.messages;

import asd.protocols.overlay.hyparview.Hyparview;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class ShuffleReply extends ProtoMessage {

	public static final short MESSAGE_ID = Hyparview.PROTOCOL_ID + 8;

	private final Set<Host> shuffleSet;

	public ShuffleReply(Set<Host> shuffleSet) {
		super(MESSAGE_ID);
		this.shuffleSet = shuffleSet;
	}

	public Set<Host> getShuffleSet() {
		return shuffleSet;
	}

	public static ISerializer<ShuffleReply> serializer = new ISerializer<>() {
		@Override
		public void serialize(ShuffleReply shuffleReply, ByteBuf byteBuf) throws IOException {
			byteBuf.writeInt(shuffleReply.shuffleSet.size());
			for (Host host : shuffleReply.shuffleSet)
				Host.serializer.serialize(host, byteBuf);
		}

		@Override
		public ShuffleReply deserialize(ByteBuf byteBuf) throws IOException {
			Set<Host> shuffleSet = new HashSet<>();
			var size = byteBuf.readInt();
			for (int i = 0; i < size; i++)
				shuffleSet.add(Host.serializer.deserialize(byteBuf));
			return new ShuffleReply(shuffleSet);
		}
	};
}
