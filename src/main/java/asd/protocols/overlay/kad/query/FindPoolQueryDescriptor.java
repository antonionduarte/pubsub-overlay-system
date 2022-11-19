package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;

import java.util.List;
import java.util.Optional;

public class FindPoolQueryDescriptor implements QueryDescriptor {
	final KadID target_rtid;
	final FindPoolQueryCallbacks callbacks;
	final Optional<Integer> sample_size;

	public FindPoolQueryDescriptor(KadID pool, Optional<Integer> sample_size, FindPoolQueryCallbacks callbacks) {
		this.target_rtid = pool;
		this.callbacks = callbacks;
		this.sample_size = sample_size;
	}

	@Override
	public KadID getRtid() {
		return KadID.DEFAULT_RTID;
	}

	@Override
	public KadID getTarget() {
		return this.target_rtid;
	}

	@Override
	public Query createQuery(QueryIO qio, KadID self, KadParams kadparams, List<KadPeer> seeds) {
		return new FindPoolQuery(qio, self, kadparams, this.target_rtid, seeds, this);
	}
}
