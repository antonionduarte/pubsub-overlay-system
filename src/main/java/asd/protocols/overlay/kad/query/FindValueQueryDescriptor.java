package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;

import java.util.List;

public class FindValueQueryDescriptor implements QueryDescriptor {
	final KadID key;
	final FindValueQueryCallbacks callbacks;

	public FindValueQueryDescriptor(KadID key, FindValueQueryCallbacks callbacks) {
		this.key = key;
		this.callbacks = callbacks;
	}

	@Override
	public KadID getRtid() {
		return KadID.DEFAULT_RTID;
	}

	@Override
	public KadID getTarget() {
		return this.key;
	}

	@Override
	public Query createQuery(QueryIO qio, KadID self, KadParams kadparams, List<KadPeer> seeds) {
		return new FindValueQuery(qio, self, kadparams, this.key, seeds, this);
	}
}
