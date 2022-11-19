package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;

import java.util.List;

public class FindClosestQueryDescriptor implements QueryDescriptor {
	final KadID rtid;
	final KadID target;
	final FindClosestQueryCallbacks callbacks;

	public FindClosestQueryDescriptor(KadID rtid, KadID target, FindClosestQueryCallbacks callbacks) {
		this.rtid = rtid;
		this.target = target;
		this.callbacks = callbacks;
	}

	@Override
	public KadID getRtid() {
		return this.rtid;
	}

	@Override
	public KadID getTarget() {
		return this.target;
	}

	@Override
	public Query createQuery(QueryIO qio, KadID self, KadParams kadparams, List<KadPeer> seeds) {
		var query = new FindClosestQuery(qio, self, kadparams, this.target, seeds, this);
		return query;
	}
}
