package asd.protocols.overlay.kad.ipc;

import java.util.List;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

public class JoinSwarmReply extends ProtoReply {
    public static final short ID = Kademlia.ID + 8;

    public final KadID swarm;
    public final List<KadPeer> peers;

    public JoinSwarmReply(KadID swarm, List<KadPeer> peers) {
        super(ID);
        this.swarm = swarm;
        this.peers = peers;
    }
}
