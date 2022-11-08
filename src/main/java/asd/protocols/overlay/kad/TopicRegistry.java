package asd.protocols.overlay.kad;

import java.util.HashMap;
import java.util.Map;

public class TopicRegistry {
    private static final Map<KadID, String> topics = new HashMap<>();

    private TopicRegistry() {
    }

    public static void register(KadID id, String topic) {
        topics.put(id, topic);
    }

    public static String lookup(KadID id) {
        return topics.get(id);
    }
}
