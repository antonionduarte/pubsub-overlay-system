package asd.protocols.overlay.kad;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class MessageTracker {

    private static class State {
        public final HashSet<KadID> providers;
        public RequestState request;

        public State() {
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

    private HashMap<UUID, State> states;

    public MessageTracker() {
        this.states = new HashMap<>();
    }

    public void startTracking(UUID uuid) {
        assert !this.states.containsKey(uuid);
        this.states.put(uuid, new State());
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

    public void cleanup() {
        var now = Instant.now();
        this.states.entrySet().removeIf(entry -> {
            var state = entry.getValue();
            if (state.request == null)
                return false;
            return state.request.start.plusSeconds(5).isBefore(now);
        });
    }
}
