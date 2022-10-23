package asd.protocols.overlay.hyparview.messages;

import asd.protocols.overlay.hyparview.Hyparview;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

public class Shuffle extends ProtoMessage {

	public static short MESSAGE_ID = Hyparview.PROTOCOL_ID + 5;

	public Shuffle() {
		super(MESSAGE_ID);
	}

}
