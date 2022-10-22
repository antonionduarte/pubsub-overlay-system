package asd.protocols.overlay.hyparview.messages;

import asd.protocols.overlay.hyparview.Hyparview;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;

public class ForwardJoin extends ProtoMessage {

	public static final short MESSAGE_ID = Hyparview.PROTOCOL_ID + 2;

	private Host newNode;
	private int timeToLive;

	public ForwardJoin(Host newNode, int timeToLive) {
		super(MESSAGE_ID);
		this.newNode = newNode;
		this.timeToLive = timeToLive;
	}

	public int getTimeToLive() {
		return timeToLive;
	}

	public Host getNewNode() {
		return newNode;
	}

	public static final ISerializer<ForwardJoin> serializer = new ISerializer<ForwardJoin>() {
		@Override
		public void serialize(ForwardJoin forwardJoin, ByteBuf byteBuf) throws IOException {

		}

		@Override
		public ForwardJoin deserialize(ByteBuf byteBuf) throws IOException {
			return null;
		}
	};
}
