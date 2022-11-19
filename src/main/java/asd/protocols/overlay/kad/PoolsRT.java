package asd.protocols.overlay.kad;

import asd.protocols.overlay.kad.routing.RoutingTable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

public class PoolsRT {
	private final KadParams params;
	private final KadID self;
	private final HashMap<KadID, RoutingTable> pools;

	public PoolsRT(KadParams params, KadID self) {
		this.params = params;
		this.self = self;
		this.pools = new HashMap<>();
	}

	public RoutingTable createPool(KadID pool) {
		if (!this.containsPool(pool)) {
			this.pools.put(pool, new RoutingTable(this.params.k, this.self));
		}
		return this.pools.get(pool);
	}

	public RoutingTable getPool(KadID pool) {
		return this.pools.get(pool);
	}

	public boolean containsPool(KadID pool) {
		return this.pools.containsKey(pool);
	}

	public List<KadPeer> getPoolSample(KadID pool) {
		RoutingTable rt = pools.get(pool);
		if (rt == null) {
			return List.of();
		}
		return rt.getSample(this.params.k);
	}

	public Iterator<Entry<KadID, RoutingTable>> iterator() {
		return this.pools.entrySet().iterator();
	}

	public void removePeer(KadID peer) {
		for (RoutingTable rt : this.pools.values())
			rt.remove(peer);
	}
}
