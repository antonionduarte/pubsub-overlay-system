package asd.protocols.overlay.kad.ipc;

import java.util.List;

import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

public class FindSwarmReply extends ProtoReply {
    public static final short ID = Kademlia.ID + 6;

    public final String swarm;
    public final List<KadPeer> peers;

    public FindSwarmReply(String swarm, List<KadPeer> peers) {
        super(ID);
        this.swarm = swarm;
        this.peers = peers;
    }
}
