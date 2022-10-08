package asd.protocols.overlay.kad.messages;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

public class KadMessageFindNode extends ProtoMessage {
	public static final short ID = Kademlia.ID + 1;

	public KadMessageFindNode(KadPeer[] closest, KadID target) {
		super(ID);
	}

}
