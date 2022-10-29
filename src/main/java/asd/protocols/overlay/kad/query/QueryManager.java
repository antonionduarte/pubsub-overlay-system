package asd.protocols.overlay.kad.query;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.KadRT;

public class QueryManager {
    private static final Logger logger = LogManager.getLogger(QueryManager.class);

    private final KadParams kadparams;
    private final KadRT routing_table;
    private final KadID self;
    private final QueryManagerIO qmio;
    private final HashMap<Long, Query> queries;
    private long next_context;

    public QueryManager(KadParams kadparams, KadRT routing_table, KadID self, QueryManagerIO qmio) {
        this.kadparams = kadparams;
        this.routing_table = routing_table;
        this.self = self;
        this.qmio = qmio;
        this.queries = new HashMap<>();
        this.next_context = 0;
    }

    public void startQuery(FindClosestQueryDescriptor descriptor) {
        var context = this.allocateContext();
        var seeds = this.routing_table.closest(descriptor.target);
        var qio = new QMQueryIO(this.qmio, context);
        var query = new FindClosestQuery(qio, this.self, this.kadparams, descriptor.target, seeds, descriptor);
        this.queries.put(context, query);

        logger.info("Starting query {} with target {} and {} seeds", context, descriptor.target, seeds.size());
        query.start();
        this.checkQueryFinished(context);
    }

    public void startQuery(FindValueQueryDescriptor descriptor) {
        var context = this.allocateContext();
        var seeds = this.routing_table.closest(descriptor.target);
        var qio = new QMQueryIO(this.qmio, context);
        var query = new FindValueQuery(qio, this.self, this.kadparams, descriptor.target, seeds, descriptor);
        this.queries.put(context, query);

        logger.info("Starting query {} with target {} and {} seeds", context, descriptor.target, seeds.size());
        query.start();
        this.checkQueryFinished(context);
    }

    public void startQuery(FindSwarmQueryDescriptor descriptor) {
        var context = this.allocateContext();
        var seeds = this.routing_table.closest(descriptor.swarm);
        var qio = new QMQueryIO(this.qmio, context);
        var query = new FindSwarmQuery(qio, this.self, this.kadparams, descriptor.swarm, seeds, descriptor);
        this.queries.put(context, query);

        logger.info("Starting query {} with swarm {} and {} seeds", context, descriptor.swarm, seeds.size());
        query.start();
        this.checkQueryFinished(context);
    }

    public void onFindNodeResponse(long context, KadID from, List<KadPeer> closest) {
        var query = this.queries.get(context);
        if (query == null) {
            logger.warn("Received FindNodeResponse with unknown context " + context);
            return;
        }
        query.onFindNodeResponse(from, closest);
        this.checkQueryFinished(context);
    }

    public void onFindValueResponse(long context, KadID from, List<KadPeer> closest, Optional<byte[]> value) {
        var query = this.queries.get(context);
        if (query == null) {
            logger.warn("Received FindValueResponse with unknown context " + context);
            return;
        }
        query.onFindValueResponse(from, closest, value);
        this.checkQueryFinished(context);
    }

    public void onFindSwarmResponse(long context, KadID from, List<KadPeer> closest, List<KadPeer> members) {
        var query = this.queries.get(context);
        if (query == null) {
            logger.warn("Received FindSwarmResponse with unknown context " + context);
            return;
        }
        query.onFindSwarmResponse(from, closest, members);
        this.checkQueryFinished(context);
    }

    public void onPeerError(long context, KadID peer) {
        var query = this.queries.get(context);
        if (query == null) {
            logger.warn("Received PeerError with unknown context " + context);
            return;
        }
        query.onPeerError(peer);
        this.checkQueryFinished(context);
    }

    public void checkTimeouts() {
        var iter = this.queries.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            var query = entry.getValue();
            query.checkTimeouts();
            if (query.isFinished()) {
                iter.remove();
            }
        }
    }

    private void checkQueryFinished(long context) {
        var query = this.queries.get(context);
        if (query.isFinished()) {
            logger.info("Query " + context + " finished");
            this.queries.remove(context);
        }
    }

    private long allocateContext() {
        return this.next_context++;
    }
}
