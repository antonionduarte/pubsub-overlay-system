package asd.protocols.overlay.hyparview.messages;

import asd.protocols.overlay.hyparview.Hyparview;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

public class NeighborReply extends ProtoMessage {

	public static final short MESSAGE_ID = Hyparview.PROTOCOL_ID + 6;

	private boolean isNeighbourAccepted;

	public NeighborReply(boolean isNeighbourAccepted) {
		super(MESSAGE_ID);
		this.isNeighbourAccepted = isNeighbourAccepted;
	}

	public boolean isNeighbourAccepted() {
		return this.isNeighbourAccepted;
	}

}
