package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;

public class FindSwarmQueryDescriptor {
    final KadID swarm;
    final FindSwarmQueryCallbacks callbacks;

    public FindSwarmQueryDescriptor(KadID swarm, FindSwarmQueryCallbacks callbacks) {
        this.swarm = swarm;
        this.callbacks = callbacks;
    }
}
