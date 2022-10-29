package asd.protocols.overlay.kad.ipc;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class FindSwarm extends ProtoRequest {
    public static final short ID = Kademlia.ID + 3;

    public final KadID swarm;

    public FindSwarm(KadID swarm) {
        super(ID);
        this.swarm = swarm;
    }

}