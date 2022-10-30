package asd.protocols.overlay.kad.query;

import java.util.Optional;

import asd.protocols.overlay.kad.KadID;

public class FindPoolQueryDescriptor {
    final KadID pool;
    final FindPoolQueryCallbacks callbacks;
    final Optional<Integer> sample_size;

    public FindPoolQueryDescriptor(KadID pool, FindPoolQueryCallbacks callbacks) {
        this.pool = pool;
        this.callbacks = callbacks;
        this.sample_size = Optional.empty();
    }

    public FindPoolQueryDescriptor(KadID pool, int sample_size, FindPoolQueryCallbacks callbacks) {
        this.pool = pool;
        this.callbacks = callbacks;
        this.sample_size = Optional.of(sample_size);
    }
}
