package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadPeer;

import java.util.List;
import java.util.Optional;

public abstract class QueryManager {
	public void findClosest(KadID target) {
		this.findClosest(KadID.DEFAULT_RTID, target, null);
	}

	public void findClosest(KadID target, FindClosestQueryCallbacks callbacks) {
		this.findClosest(KadID.DEFAULT_RTID, target, callbacks);
	}

	public abstract void findClosest(KadID rtid, KadID target, FindClosestQueryCallbacks callbacks);

	public void findPool(KadID rtid) {
		this.findPool(rtid, null);
	}

	public abstract void findPool(KadID rtid, FindPoolQueryCallbacks callbacks);

	public void findSwarm(KadID swarm_id) {
		this.findSwarm(swarm_id, null);
	}

	public void findSwarm(KadID swarm_id, FindSwarmQueryCallbacks callbacks) {
		this.findSwarm(swarm_id, 20, callbacks);
	}

	public abstract void findSwarm(KadID swarm_id, int sample_size, FindSwarmQueryCallbacks callbacks);

	public void findValue(KadID key) {
		this.findValue(key, null);
	}

	public abstract void findValue(KadID key, FindValueQueryCallbacks callbacks);

	public abstract void onFindNodeResponse(long context, KadID from, List<KadPeer> closest);

	public abstract void onFindValueResponse(long context, KadID from, List<KadPeer> closest, Optional<byte[]> value);

	public abstract void onFindSwarmResponse(long context, KadID from, List<KadPeer> closest, List<KadPeer> members);

	public abstract void onFindPoolResponse(long context, KadID from, List<KadPeer> closest, List<KadPeer> members);

	public abstract void onPeerError(long context, KadID peer);

	public abstract void checkTimeouts();
}
