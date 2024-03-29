package asd.protocols.overlay.kad.ipc;

import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

import java.util.List;

public class JoinSwarmReply extends ProtoReply {
	public static final short ID = Kademlia.ID + 12;

	public final String swarm;
	public final List<KadPeer> peers;

	public JoinSwarmReply(String swarm, List<KadPeer> peers) {
		super(ID);
		this.swarm = swarm;
		this.peers = peers;
	}
}
