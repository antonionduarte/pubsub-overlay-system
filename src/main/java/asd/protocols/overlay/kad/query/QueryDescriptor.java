package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;

import java.util.List;

interface QueryDescriptor {
	KadID getRtid();

	KadID getTarget();

	Query createQuery(QueryIO qio, KadID self, KadParams kadparams, List<KadPeer> seeds);
}
