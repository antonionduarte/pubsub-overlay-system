package asd.protocols.overlay.kad.query;

import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;

class FindClosestQuery extends Query {
    private static final Logger logger = LogManager.getLogger(FindClosestQuery.class);

    private final FindClosestQueryCallbacks callbacks;

    public FindClosestQuery(QueryIO qio, KadID self, KadParams kadparams, KadID target, List<KadPeer> seeds,
            FindClosestQueryDescriptor descriptor) {
        super(qio, self, kadparams, target, seeds);
        this.callbacks = descriptor.callbacks;
    }

    @Override
    void request(QueryIO qio, KadID peer, KadID target) {
        qio.findNodeRequest(peer, target);
    }

    @Override
    void onFinish(QPeerSet set) {
        if (this.callbacks == null)
            return;

        var closest = set.closest();
        this.callbacks.onQueryResult(closest);
    }

}
