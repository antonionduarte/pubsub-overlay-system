package asd.protocols.overlay.kad.bcast;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import asd.protocols.overlay.kad.KadID;

public class RequestTracker {

	public static record ExpiredRequest(KadID rtid, UUID uuid) {
	}

	public static record ExpiredRequest(KadID rtid, UUID uuid) {
	}

	private static class State {
		public final KadID rtid;
		public final HashSet<KadID> providers;
		public RequestState request;

		public State(KadID rtid) {
			this.rtid = rtid;
			this.providers = new HashSet<>();
			this.request = null;
		}
	}

	private static class RequestState {
		public final KadID peer;
		public final Instant start;

		public RequestState(KadID peer) {
			this.peer = peer;
			this.start = Instant.now();
		}
	}

	private final HashMap<UUID, State> states;
	private final Duration requestTimeout;

	public RequestTracker() {
		this(Duration.ofSeconds(10));
	}

	public RequestTracker(Duration timeout) {
		this.states = new HashMap<>();
		this.requestTimeout = timeout;
	}

	public void startTracking(KadID rtid, UUID uuid) {
		assert !this.states.containsKey(uuid);
		this.states.put(uuid, new State(rtid));
	}

	public void stopTracking(UUID uuid) {
		assert this.states.containsKey(uuid);
		this.states.remove(uuid);
	}

	public boolean isTracking(UUID uuid) {
		return this.states.containsKey(uuid);
	}

	public boolean isRequesting(UUID uuid) {
		var state = this.states.get(uuid);
		return state != null && state.request != null;
	}

	public void addProvider(UUID uuid, KadID id) {
		assert this.states.containsKey(uuid);
		this.states.get(uuid).providers.add(id);
	}

	public KadID getProvider(UUID uuid) {
		assert this.states.containsKey(uuid);
		var state = this.states.get(uuid);
		if (state.providers.isEmpty())
			return null;
		return state.providers.iterator().next();
	}

	public void beginRequest(UUID uuid, KadID id) {
		assert this.states.containsKey(uuid);
		var state = this.states.get(uuid);
		assert state.request == null;
		state.request = new RequestState(id);
	}

	public void endRequest(UUID uuid) {
		this.states.remove(uuid);
	}

	public void failedRequest(UUID uuid) {
		assert this.states.containsKey(uuid);
		var state = this.states.get(uuid);
		assert state.request != null;
		state.providers.remove(state.request.peer);
		state.request = null;
	}

	public List<ExpiredRequest> checkTimeouts() {
		var now = Instant.now();
		var expired = new ArrayList<ExpiredRequest>();
		this.states.entrySet().removeIf(entry -> {
			var state = entry.getValue();
			if (state.request == null)
				return false;
			var remove = state.request.start.plus(this.requestTimeout).isBefore(now);
			if (remove)
				expired.add(new ExpiredRequest(entry.getValue().rtid, entry.getKey()));
			return remove;
		});
		return expired;
	}
}
