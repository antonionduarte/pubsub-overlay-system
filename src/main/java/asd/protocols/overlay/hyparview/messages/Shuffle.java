package asd.protocols.overlay.hyparview.messages;

import asd.protocols.overlay.hyparview.Hyparview;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class Shuffle extends ProtoMessage {

	public static final short MESSAGE_ID = Hyparview.PROTOCOL_ID + 5;
	public static ISerializer<Shuffle> serializer = new ISerializer<>() {
		@Override
		public void serialize(Shuffle shuffle, ByteBuf byteBuf) throws IOException {
			byteBuf.writeInt(shuffle.shuffleSet.size());
			for (Host host : shuffle.shuffleSet)
				Host.serializer.serialize(host, byteBuf);
			Host.serializer.serialize(shuffle.originalNode, byteBuf);
			byteBuf.writeInt(shuffle.timeToLive);
		}

		@Override
		public Shuffle deserialize(ByteBuf byteBuf) throws IOException {
			Set<Host> shuffleSet = new HashSet<>();
			var size = byteBuf.readInt();
			for (int i = 0; i < size; i++)
				shuffleSet.add(Host.serializer.deserialize(byteBuf));
			Host originalNode = Host.serializer.deserialize(byteBuf);
			var timeToLive = byteBuf.readInt();
			return new Shuffle(timeToLive, shuffleSet, originalNode);
		}
	};
	private final Set<Host> shuffleSet;
	private final Host originalNode;
	private final int timeToLive;

	public Shuffle(int timeToLive, Set<Host> shuffleSet, Host originalNode) {
		super(MESSAGE_ID);
		this.timeToLive = timeToLive;
		this.shuffleSet = shuffleSet;
		this.originalNode = originalNode;
	}

	public Host getOriginalNode() {
		return originalNode;
	}

	public Set<Host> getShuffleSet() {
		return shuffleSet;
	}

	public int getTimeToLive() {
		return timeToLive;
	}
}
