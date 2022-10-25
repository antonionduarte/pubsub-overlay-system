package asd.protocols.overlay.hyparview.ipc;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.Set;

public class GetPeersReply extends ProtoReply {

	public static final short REQUEST_ID = 100;

	private final Set<Host> peers;

	public GetPeersReply(Set<Host> peers) {
		super(REQUEST_ID);
		this.peers = peers;
	}

	public Set<Host> getPeers() {
		return peers;
	}

}
