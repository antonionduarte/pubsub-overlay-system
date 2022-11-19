package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;

import java.util.List;
import java.util.Optional;

public class FindSwarmQueryDescriptor implements QueryDescriptor {
	final KadID swarm;
	final FindSwarmQueryCallbacks callbacks;
	final Optional<Integer> sample_size;

	public FindSwarmQueryDescriptor(KadID swarm, Optional<Integer> sample_size, FindSwarmQueryCallbacks callbacks) {
		this.swarm = swarm;
		this.callbacks = callbacks;
		this.sample_size = sample_size;
	}

	@Override
	public KadID getRtid() {
		return KadID.DEFAULT_RTID;
	}

	@Override
	public KadID getTarget() {
		return this.swarm;
	}

	@Override
	public Query createQuery(QueryIO qio, KadID self, KadParams kadparams, List<KadPeer> seeds) {
		return new FindSwarmQuery(qio, self, kadparams, this.swarm, seeds, this);
	}
}
