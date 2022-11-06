package asd.protocols.overlay.kad.query;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.function.Consumer;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadPeer;

public class CachedQueryManager extends QueryManager {

    private static record FindClosestKey(KadID rtid, KadID target) {
    }

    private static record FindPoolKey(KadID rtid, KadID target) {
    }

    private static record findSwarmKey(KadID rtid, Integer sample_size, KadID target) {
    }

    private static record FindValueKey(KadID rtid, KadID target) {
    }

    private static class CacheEntry {
        public boolean in_progress;
        public Instant timestamp;
        // Receives a queue of callbacks and executes them
        public Consumer<Object> executor;
        public Queue<Object> callbacks;

        public CacheEntry() {
            this.in_progress = false;
            this.timestamp = null;
            this.executor = null;
            this.callbacks = new ArrayDeque<>();
        }
    }

    private final QueryManager inner;
    private final HashMap<Object, CacheEntry> entries;
    private final Duration ttl;

    public CachedQueryManager(QueryManager qm, Duration ttl) {
        this.inner = qm;
        this.entries = new HashMap<>();
        this.ttl = ttl;
    }

    @Override
    public void findClosest(KadID rtid, KadID target, FindClosestQueryCallbacks callbacks) {
        var key = new FindClosestKey(rtid, target);
        var entry = this.getOrCreateEntry(key);

        if (callbacks != null)
            entry.callbacks.add(callbacks);

        if (entry.executor != null) {
            entry.executor.accept(entry.callbacks);
            return;
        }

        if (!entry.in_progress) {
            entry.in_progress = true;
            this.inner.findClosest(rtid, target, (closest) -> {
                entry.timestamp = Instant.now();
                entry.executor = (queue) -> {
                    @SuppressWarnings("unchecked")
                    var q = (Queue<FindClosestQueryCallbacks>) queue;
                    while (!q.isEmpty()) {
                        var cb = q.poll();
                        cb.onQueryResult(closest);
                    }
                };
                entry.executor.accept(entry.callbacks);
            });
        }
    }

    @Override
    public void findPool(KadID rtid, FindPoolQueryCallbacks callbacks) {
        var key = new FindPoolKey(rtid, KadID.DEFAULT_RTID);
        var entry = this.getOrCreateEntry(key);

        if (callbacks != null)
            entry.callbacks.add(callbacks);

        if (entry.executor != null) {
            entry.executor.accept(entry.callbacks);
            return;
        }

        if (!entry.in_progress) {
            entry.in_progress = true;
            this.inner.findPool(rtid, (closest, members) -> {
                entry.timestamp = Instant.now();
                entry.executor = (queue) -> {
                    @SuppressWarnings("unchecked")
                    var q = (Queue<FindPoolQueryCallbacks>) queue;
                    while (!q.isEmpty()) {
                        var cb = q.poll();
                        cb.onQueryResult(closest, members);
                    }
                };
                entry.executor.accept(entry.callbacks);
            });
        }
    }

    public void findSwarm(KadID swarm_id, int sample_size, FindSwarmQueryCallbacks callbacks) {
        var key = new findSwarmKey(swarm_id, sample_size, KadID.DEFAULT_RTID);
        var entry = this.getOrCreateEntry(key);

        if (callbacks != null)
            entry.callbacks.add(callbacks);

        if (entry.executor != null) {
            entry.executor.accept(entry.callbacks);
            return;
        }

        if (!entry.in_progress) {
            entry.in_progress = true;
            this.inner.findSwarm(swarm_id, sample_size, (closest, members) -> {
                entry.timestamp = Instant.now();
                entry.executor = (queue) -> {
                    @SuppressWarnings("unchecked")
                    var q = (Queue<FindSwarmQueryCallbacks>) queue;
                    while (!q.isEmpty()) {
                        var cb = q.poll();
                        cb.onQueryResult(closest, members);
                    }
                };
                entry.executor.accept(entry.callbacks);
            });
        }
    }

    @Override
    public void findValue(KadID key, FindValueQueryCallbacks callbacks) {
        var k = new FindValueKey(key, KadID.DEFAULT_RTID);
        var entry = this.getOrCreateEntry(k);

        if (callbacks != null)
            entry.callbacks.add(callbacks);

        if (entry.executor != null) {
            entry.executor.accept(entry.callbacks);
            return;
        }

        if (!entry.in_progress) {
            entry.in_progress = true;
            this.inner.findValue(key, (closest, value) -> {
                entry.timestamp = Instant.now();
                entry.executor = (queue) -> {
                    @SuppressWarnings("unchecked")
                    var q = (Queue<FindValueQueryCallbacks>) queue;
                    while (!q.isEmpty()) {
                        var cb = q.poll();
                        cb.onQueryResult(closest, value);
                    }
                };
                entry.executor.accept(entry.callbacks);
            });
        }
    }

    @Override
    public void onFindNodeResponse(long context, KadID from, List<KadPeer> closest) {
        this.inner.onFindNodeResponse(context, from, closest);
    }

    @Override
    public void onFindValueResponse(long context, KadID from, List<KadPeer> closest, Optional<byte[]> value) {
        this.inner.onFindValueResponse(context, from, closest, value);
    }

    @Override
    public void onFindSwarmResponse(long context, KadID from, List<KadPeer> closest, List<KadPeer> members) {
        this.inner.onFindSwarmResponse(context, from, closest, members);
    }

    @Override
    public void onFindPoolResponse(long context, KadID from, List<KadPeer> closest, List<KadPeer> members) {
        this.inner.onFindPoolResponse(context, from, closest, members);
    }

    @Override
    public void onPeerError(long context, KadID peer) {
        this.inner.onPeerError(context, peer);
    }

    @Override
    public void checkTimeouts() {
        this.inner.checkTimeouts();
    }

    private CacheEntry getOrCreateEntry(Object key) {
        var entry = this.entries.computeIfAbsent(key, (k) -> new CacheEntry());
        if (entry.timestamp != null && entry.timestamp.isBefore(Instant.now().minus(this.ttl))) {
            entry.in_progress = false;
            entry.timestamp = null;
            entry.executor = null;
            entry.callbacks.clear();
        }
        return entry;
    }
}
