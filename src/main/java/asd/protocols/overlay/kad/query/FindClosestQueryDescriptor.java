package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;

public class FindClosestQueryDescriptor {
    final KadID target;
    final FindClosestQueryCallbacks callbacks;

    public FindClosestQueryDescriptor(KadID target) {
        this(target, null);
    }

    public FindClosestQueryDescriptor(KadID target, FindClosestQueryCallbacks callbacks) {
        this.target = target;
        this.callbacks = callbacks;
    }
}
