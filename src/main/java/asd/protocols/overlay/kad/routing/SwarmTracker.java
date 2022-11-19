package asd.protocols.overlay.kad.routing;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.utils.ASDUtils;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class SwarmTracker {
	private final KadParams params;
	private final HashMap<KadID, Swarm> swarms;

	public SwarmTracker(KadParams params) {
		this.params = params;
		this.swarms = new HashMap<>();
	}

	public void add(KadID swarm_id, KadID peer) {
		var swarm = this.getOrCreateSwarm(swarm_id);
		swarm.addMember(peer);
	}

	public List<KadID> getSwarmSample(KadID swarm) {
		var swarmObj = this.swarms.get(swarm);
		if (swarmObj == null) {
			return List.of();
		}
		return swarmObj.sample();
	}

	private Swarm getOrCreateSwarm(KadID swarm_id) {
		var swarm = this.swarms.get(swarm_id);
		if (swarm == null) {
			swarm = new Swarm(swarm_id);
			this.swarms.put(swarm_id, swarm);
		}
		return swarm;
	}

	private static class QueueItem {
		public final KadID id;
		public final Instant expire;

		public QueueItem(KadID id, Instant expire) {
			this.id = id;
			this.expire = expire;
		}
	}

	private static class Swarm {
		public final KadID id;
		public final ArrayDeque<QueueItem> queue;
		public final HashSet<KadID> members;

		public Swarm(KadID id) {
			this.id = id;
			this.queue = new ArrayDeque<>();
			this.members = new HashSet<>();
		}

		public void addMember(KadID id) {
			this.clean();
			this.queue.add(new QueueItem(id, Instant.now().plusSeconds(10 * 60)));
			this.members.add(id);
		}

		public List<KadID> sample() {
			return ASDUtils.sample(20, this.members).stream().toList();
		}

		private void clean() {
			var now = Instant.now();
			while (!this.queue.isEmpty()) {
				var item = this.queue.peek();
				if (item.expire.isAfter(now)) {
					break;
				}
				this.queue.remove();
				this.members.remove(item.id);
			}
		}
	}
}
