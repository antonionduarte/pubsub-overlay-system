package asd.protocols.overlay.hyparview.messages;

import asd.protocols.overlay.hyparview.Hyparview;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

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
}
