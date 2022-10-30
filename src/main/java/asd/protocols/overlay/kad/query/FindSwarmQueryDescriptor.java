package asd.protocols.overlay.kad.query;

import java.util.Optional;

import asd.protocols.overlay.kad.KadID;

public class FindSwarmQueryDescriptor {
    final KadID swarm;
    final FindSwarmQueryCallbacks callbacks;
    final Optional<Integer> sample_size;

    public FindSwarmQueryDescriptor(KadID swarm, FindSwarmQueryCallbacks callbacks) {
        this.swarm = swarm;
        this.callbacks = callbacks;
        this.sample_size = Optional.empty();
    }

    public FindSwarmQueryDescriptor(KadID swarm, int sample_size, FindSwarmQueryCallbacks callbacks) {
        this.swarm = swarm;
        this.callbacks = callbacks;
        this.sample_size = Optional.of(sample_size);
    }
}
