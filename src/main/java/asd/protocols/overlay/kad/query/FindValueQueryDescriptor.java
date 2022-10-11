package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;

public class FindValueQueryDescriptor {
    final KadID target;
    final FindValueQueryCallbacks callbacks;

    public FindValueQueryDescriptor(KadID target, FindValueQueryCallbacks callbacks) {
        this.target = target;
        this.callbacks = callbacks;
    }
}
