package asd.protocols.overlay.kad.messages;

import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

public class FindValueRequest extends ProtoMessage {
	public static final short ID = Kademlia.ID + 3;

	public FindValueRequest() {
		super(ID);
	}

}
