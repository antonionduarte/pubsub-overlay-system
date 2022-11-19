package asd.protocols.overlay.kad.ipc;

import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

import java.util.List;

public class FindPoolReply extends ProtoReply {

	public static final short ID = Kademlia.ID + 4;

	public final String pool;
	public final List<KadPeer> members;

	public FindPoolReply(String pool, List<KadPeer> members) {
		super(ID);
		this.pool = pool;
		this.members = members;
	}
}
