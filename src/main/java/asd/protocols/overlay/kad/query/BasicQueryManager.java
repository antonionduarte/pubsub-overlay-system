package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.routing.RoutingTables;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class BasicQueryManager extends QueryManager {
	private static final Logger logger = LogManager.getLogger(BasicQueryManager.class);

	private final KadParams kadparams;
	private final RoutingTables rts;
	private final KadID self;
	private final QueryManagerIO qmio;
	private final HashMap<Long, Query> queries;
	private long next_context;

	public BasicQueryManager(KadParams kadparams, RoutingTables rts, KadID self, QueryManagerIO qmio) {
		this.kadparams = kadparams;
		this.rts = rts;
		this.self = self;
		this.qmio = qmio;
		this.queries = new HashMap<>();
		this.next_context = 0;
	}

	@Override
	public void findClosest(KadID rtid, KadID target, FindClosestQueryCallbacks callbacks) {
		var descriptor = new FindClosestQueryDescriptor(rtid, target, callbacks);
		this.startQueryInternal(descriptor);
	}

	@Override
	public void findPool(KadID rtid, FindPoolQueryCallbacks callbacks) {
		var descriptor = new FindPoolQueryDescriptor(rtid, Optional.of(this.kadparams.k), callbacks);
		this.startQueryInternal(descriptor);
	}

	@Override
	public void findSwarm(KadID swarm_id, FindSwarmQueryCallbacks callbacks) {
		this.findSwarm(swarm_id, this.kadparams.k, callbacks);
	}

	@Override
	public void findSwarm(KadID swarm_id, int sample_size, FindSwarmQueryCallbacks callbacks) {
		var descriptor = new FindSwarmQueryDescriptor(swarm_id, Optional.of(sample_size), callbacks);
		this.startQueryInternal(descriptor);
	}

	@Override
	public void findValue(KadID key, FindValueQueryCallbacks callbacks) {
		var descriptor = new FindValueQueryDescriptor(key, callbacks);
		this.startQueryInternal(descriptor);
	}

	@Override
	public void onFindNodeResponse(long context, KadID from, List<KadPeer> closest) {
		var query = this.queries.get(context);
		if (query == null) {
			logger.warn("Received FindNodeResponse with unknown context " + context);
			return;
		}
		query.onFindNodeResponse(from, closest);
		this.checkQueryFinished(context);
	}

	@Override
	public void onFindValueResponse(long context, KadID from, List<KadPeer> closest, Optional<byte[]> value) {
		var query = this.queries.get(context);
		if (query == null) {
			logger.warn("Received FindValueResponse with unknown context " + context);
			return;
		}
		query.onFindValueResponse(from, closest, value);
		this.checkQueryFinished(context);
	}

	@Override
	public void onFindSwarmResponse(long context, KadID from, List<KadPeer> closest, List<KadPeer> members) {
		var query = this.queries.get(context);
		if (query == null) {
			logger.warn("Received FindSwarmResponse with unknown context " + context);
			return;
		}
		query.onFindSwarmResponse(from, closest, members);
		this.checkQueryFinished(context);
	}

	@Override
	public void onFindPoolResponse(long context, KadID from, List<KadPeer> closest, List<KadPeer> members) {
		var query = this.queries.get(context);
		if (query == null) {
			logger.warn("Received FindPoolResponse with unknown context " + context);
			return;
		}
		query.onFindPoolResponse(from, closest, members);
		this.checkQueryFinished(context);
	}

	@Override
	public void onPeerError(long context, KadID peer) {
		var query = this.queries.get(context);
		if (query == null) {
			logger.warn("Received PeerError with unknown context " + context);
			return;
		}
		query.onPeerError(peer);
		this.checkQueryFinished(context);
	}

	@Override
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

	private void startQueryInternal(QueryDescriptor desc) {
		var context = this.allocateContext();
		var seeds = this.rts.closest(desc.getRtid(), desc.getTarget());
		var qio = new QMQueryIO(this.qmio, context);
		var query = desc.createQuery(qio, this.self, this.kadparams, seeds);
		this.queries.put(context, query);

		logger.info("Starting query {} with target {} and rtid {} and {} seeds", context, desc.getTarget(),
				desc.getRtid(), seeds.size());
		query.start();
		this.checkQueryFinished(context);
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
