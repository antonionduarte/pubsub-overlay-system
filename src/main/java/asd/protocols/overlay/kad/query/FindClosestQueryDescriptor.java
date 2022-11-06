package asd.protocols.overlay.kad.query;

import java.util.Optional;

import asd.protocols.overlay.kad.KadID;

public class FindClosestQueryDescriptor {
    final KadID target;
    final Optional<KadID> pool;
    final FindClosestQueryCallbacks callbacks;

    public FindClosestQueryDescriptor(KadID target) {
        this.target = target;
        this.pool = Optional.empty();
        this.callbacks = null;
    }

    public FindClosestQueryDescriptor(KadID target, KadID pool) {
        this.target = target;
        this.pool = Optional.of(pool);
        this.callbacks = null;
    }

    public FindClosestQueryDescriptor(KadID target, FindClosestQueryCallbacks callbacks) {
        this.target = target;
        this.pool = Optional.empty();
        this.callbacks = callbacks;
    }

    public FindClosestQueryDescriptor(KadID target, KadID pool, FindClosestQueryCallbacks callbacks) {
        this.target = target;
        this.pool = Optional.of(pool);
        this.callbacks = callbacks;
    }

    public FindClosestQueryDescriptor(KadID target, Optional<KadID> pool, FindClosestQueryCallbacks callbacks) {
        this.target = target;
        this.pool = pool;
        this.callbacks = callbacks;
    }
}
