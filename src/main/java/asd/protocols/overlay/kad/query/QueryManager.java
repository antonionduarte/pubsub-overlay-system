package asd.protocols.overlay.kad.query;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Optional;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import asd.protocols.overlay.kad.KadAddrBook;
import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.KadRT;
import asd.protocols.overlay.kad.messages.FindNodeResponse;
import asd.protocols.overlay.kad.messages.FindValueResponse;

public class QueryManager {
    private static final Logger logger = LogManager.getLogger(QueryManager.class);

    private final KadParams kadparams;
    private final KadRT routing_table;
    private final KadAddrBook addrbook;
    private final KadID self;
    private final Queue<QueryMessage> queue;
    private final HashMap<Integer, Query> queries;
    private int next_context;

    public QueryManager(KadParams kadparams, KadRT routing_table, KadAddrBook addrbook, KadID self) {
        this.kadparams = kadparams;
        this.routing_table = routing_table;
        this.addrbook = addrbook;
        this.self = self;
        this.queue = new ArrayDeque<>();
        this.queries = new HashMap<>();
        this.next_context = 0;
    }

    public void startQuery(FindClosestQueryDescriptor descriptor) {
        var context = this.allocateContext();
        var seeds = this.routing_table.closest(descriptor.target);
        var query = new FindClosestQuery(context, this.kadparams, descriptor.target, seeds,
                this.addrbook,
                this.queue, this.self, descriptor);
        this.queries.put(context, query);

        logger.info("Starting query {} with target {} and {} seeds", context, descriptor.target, seeds.size());
        query.start();
        if (query.isFinished()) {
            logger.info("Query " + context + " finished");
            this.queries.remove(context);
        }
    }

    public void startQuery(FindValueQueryDescriptor descriptor) {
        var context = this.allocateContext();
        var seeds = this.routing_table.closest(descriptor.target);
        var query = new FindValueQuery(context, this.kadparams, descriptor.target, seeds,
                this.addrbook,
                this.queue, this.self, descriptor);
        this.queries.put(context, query);

        logger.info("Starting query {} with target {} and {} seeds", context, descriptor.target, seeds.size());
        query.start();
        if (query.isFinished()) {
            logger.info("Query " + context + " finished");
            this.queries.remove(context);
        }
    }

    public void onFindNodeResponse(FindNodeResponse msg, KadPeer from) {
        var context = msg.context;
        var query = this.queries.get(context);
        if (query == null) {
            logger.warn("Received FindNodeResponse with unknown context " + context);
            return;
        }
        query.onFindNodeResponse(msg, from);
        if (query.isFinished()) {
            logger.info("Query " + context + " finished");
            this.queries.remove(context);
        }
    }

    public void onFindValueResponse(FindValueResponse msg, KadPeer from) {
        var context = msg.context;
        var query = this.queries.get(context);
        if (query == null) {
            logger.warn("Received FindValueResponse with unknown context " + context);
            return;
        }
        query.onFindValueResponse(msg, from);
    }

    public Optional<QueryMessage> popMessage() {
        if (this.queue.isEmpty())
            return Optional.empty();
        return Optional.of(this.queue.poll());
    }

    private int allocateContext() {
        return this.next_context++;
    }
}
