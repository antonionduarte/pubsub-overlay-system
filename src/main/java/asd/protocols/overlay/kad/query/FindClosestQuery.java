package asd.protocols.overlay.kad.query;

import java.util.List;
import java.util.Queue;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import asd.protocols.overlay.kad.KadAddrBook;
import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.messages.FindNodeRequest;

class FindClosestQuery extends Query {
    private static final Logger logger = LogManager.getLogger(FindClosestQuery.class);

    private final FindClosestQueryCallbacks callbacks;

    public FindClosestQuery(int context, KadParams kadparams, KadID target, List<KadPeer> seeds, KadAddrBook addrbook,
            Queue<QueryMessage> queue, KadID self, FindClosestQueryDescriptor descriptor) {
        super(context, kadparams, target, seeds, addrbook, queue, self);
        this.callbacks = descriptor.callbacks;
    }

    @Override
    void request(KadID target, KadID peer) {
        this.sendMessage(peer, new FindNodeRequest(this.getContext(), target));
    }

    @Override
    void onFinish(List<KadPeer> closest) {
        if (this.callbacks != null)
            this.callbacks.onQueryResult(closest);
    }

}
