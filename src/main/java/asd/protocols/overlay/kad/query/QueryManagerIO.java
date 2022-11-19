package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadPeer;

public interface QueryManagerIO {
	void discover(KadPeer peer);

	void findNodeRequest(long context, KadID id, KadID rtid, KadID target);

	void findValueRequest(long context, KadID id, KadID key);

	void findSwarmRequest(long context, KadID id, KadID swarm);

	void findPoolRequest(long context, KadID id, KadID pool);
}
