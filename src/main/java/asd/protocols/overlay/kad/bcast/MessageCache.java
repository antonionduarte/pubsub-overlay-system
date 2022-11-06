package asd.protocols.overlay.kad.bcast;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.UUID;

public class MessageCache {
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

    public void add(Message message) {
        this.clean();
        this.queue.add(new QueueItem(message.uuid, Instant.now().plusSeconds(10 * 60)));
        this.messages.put(message.uuid, message);
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
