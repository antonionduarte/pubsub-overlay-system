package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

class FindClosestQuery extends Query {
	private static final Logger logger = LogManager.getLogger(FindClosestQuery.class);

	private final KadID rtid;
	private final FindClosestQueryCallbacks callbacks;

	public FindClosestQuery(QueryIO qio, KadID self, KadParams kadparams, KadID target, List<KadPeer> seeds,
	                        FindClosestQueryDescriptor descriptor) {
		super(qio, self, kadparams, target, seeds);
		this.rtid = descriptor.rtid;
		this.callbacks = descriptor.callbacks;
	}

	@Override
	void request(QueryIO qio, KadID peer, KadID target) {
		qio.findNodeRequest(peer, this.rtid, target);
	}

	@Override
	void onFinish(QPeerSet set) {
		if (this.callbacks == null) {
			return;
		}

		var closest = set.closest();
		this.callbacks.onQueryResult(new FindClosestQueryResult(closest));
	}

}
