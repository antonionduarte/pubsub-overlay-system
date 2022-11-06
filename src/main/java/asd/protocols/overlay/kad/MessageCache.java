package asd.protocols.overlay.kad;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.UUID;

public class MessageCache {
    public static class Message {
        public final UUID uuid;
        public final int depth;
        public final KadPeer origin;
        public final byte[] payload;

        public Message(UUID uuid, int depth, KadPeer origin, byte[] payload) {
            this.uuid = uuid;
            this.depth = depth;
            this.origin = origin;
            this.payload = payload;
        }
    }

    private static class QueueItem {
        public final UUID uuid;
        public final Instant expire;

        public QueueItem(UUID uuid, Instant expire) {
            this.uuid = uuid;
            this.expire = expire;
        }
    }

    public final ArrayDeque<QueueItem> queue;
    public final HashMap<UUID, Message> messages;

    public MessageCache() {
        this.queue = new ArrayDeque<>();
        this.messages = new HashMap<>();
    }

    public void add(UUID uuid, int depth, KadPeer origin, byte[] payload) {
        this.clean();
        this.queue.add(new QueueItem(uuid, Instant.now().plusSeconds(10 * 60)));
        this.messages.put(uuid, new Message(uuid, depth, origin, payload));
    }

    public Message get(UUID uuid) {
        return this.messages.get(uuid);
    }

    public boolean contains(UUID uuid) {
        return this.messages.containsKey(uuid);
    }

    private void clean() {
        var now = Instant.now();
        while (!this.queue.isEmpty()) {
            var item = this.queue.peek();
            if (item.expire.isAfter(now))
                break;

            this.queue.remove();
            this.messages.remove(item.uuid);
        }
    }
}
