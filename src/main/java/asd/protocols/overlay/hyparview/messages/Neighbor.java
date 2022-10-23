package asd.protocols.overlay.hyparview.messages;

import asd.protocols.overlay.hyparview.Hyparview;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class Neighbor extends ProtoMessage {

	public static final short MESSAGE_ID = Hyparview.PROTOCOL_ID + 4;

	public enum Priority {
		HIGH,
		LOW
	}

	private Priority priority;

	public Neighbor(Priority priority) {
		super(MESSAGE_ID);
		this.priority = priority;
	}

	public Priority getPriority() {
		return priority;
	}

	public static ISerializer<Neighbor>  serializer = new ISerializer<>() {
		@Override
		public void serialize(Neighbor neighbor, ByteBuf byteBuf) {
			var priority = neighbor.priority.ordinal();
			byteBuf.writeInt(priority);
		}

		@Override
		public Neighbor deserialize(ByteBuf byteBuf) {
			return new Neighbor(byteBuf.readInt() == 0 ? Priority.HIGH : Priority.LOW);
		}
	};
}
