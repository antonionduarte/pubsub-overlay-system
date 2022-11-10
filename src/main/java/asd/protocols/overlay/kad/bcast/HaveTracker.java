package asd.protocols.overlay.kad.bcast;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.UUID;

import asd.protocols.overlay.kad.KadID;

/**
 * Tracks received Have messages.
 */
public class HaveTracker {
    private record HaveRecord(UUID uuid, KadID peer) {
    }

    private record TimestampedRecord(Instant instant, HaveRecord record) {
    }

    private final HashSet<HaveRecord> records;
    private final Deque<TimestampedRecord> queue;
    private final Duration record_ttl;

    public HaveTracker(Duration ttl) {
        this.records = new HashSet<>();
        this.queue = new ArrayDeque<>();
        this.record_ttl = ttl;
    }

    public void add(UUID uuid, KadID peer) {
        var record = new HaveRecord(uuid, peer);
        var now = Instant.now();
        this.records.add(record);
        this.queue.push(new TimestampedRecord(now, record));
    }

    public boolean contains(UUID uuid, KadID peer) {
        return this.records.contains(new HaveRecord(uuid, peer));
    }

    public void checkTimeouts() {
        var now = Instant.now();
        while (!this.queue.isEmpty() && this.queue.peek().instant.plus(this.record_ttl).isBefore(now))
            this.queue.pop();
    }
}
