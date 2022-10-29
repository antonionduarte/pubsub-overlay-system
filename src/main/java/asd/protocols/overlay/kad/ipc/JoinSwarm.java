package asd.protocols.overlay.kad.ipc;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class JoinSwarm extends ProtoRequest {
    public static final short ID = Kademlia.ID + 7;

    public final KadID swarm;

    public JoinSwarm(KadID swarm) {
        super(ID);
        this.swarm = swarm;
    }

}