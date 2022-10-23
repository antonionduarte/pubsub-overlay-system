package asd.protocols.overlay.hyparview.messages;

import asd.protocols.overlay.hyparview.Hyparview;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

public class JoinReply extends ProtoMessage {

	public static final short MESSAGE_ID = Hyparview.PROTOCOL_ID + 7;

	public JoinReply() {
		super(MESSAGE_ID);
	}

}
