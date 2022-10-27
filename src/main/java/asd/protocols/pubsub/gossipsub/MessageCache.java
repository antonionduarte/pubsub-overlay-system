package asd.protocols.pubsub.gossipsub;

import asd.protocols.pubsub.gossipsub.messages.PublishMessage;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class MessageCache {

    // msgId -> message
    private final Map<UUID, PublishMessage> messages;
    private final List<Set<Pair<UUID,String>>> history;
    private final int gossip, historyLength;

    public MessageCache(int gossip, int historyLength) {
        messages = new HashMap<>();
        history = new LinkedList<>();
        history.add(0, new HashSet<>());
        this.gossip = gossip;
        this.historyLength = historyLength;
    }

    public void put(PublishMessage publishMessage) {
        var msgId = publishMessage.getMsgId();
        if (messages.containsKey(msgId))
            return;

        messages.put(publishMessage.getMsgId(), publishMessage);
        history.get(0).add(Pair.of(msgId, publishMessage.getTopic()));

    }

    public boolean contains(UUID msgId) {
        return messages.containsKey(msgId);
    }

    public PublishMessage get(UUID msgId) {
        return messages.get(msgId);
    }

    public void shift() {
        if (history.size() == historyLength) {
            var lastEntries = history.remove(history.size() - 1);
            history.add(0, new HashSet<>());
            for (var entry : lastEntries) {
                messages.remove(entry.getLeft());
            }
        }
    }

    public Map<String, Set<UUID>> getMessageIDsByTopic(Set<String> topics) {
        Map<String, Set<UUID>> msgIdsByTopic = new HashMap<>();
        //only return 'gossip' most recent message sets in history
        for (int i = 0; i < Math.min(gossip, history.size()); i++) {
            for (var entry : history.get(i)) {
                var msgId = entry.getLeft();
                var msg = messages.get(msgId);
                if (msg != null && topics.contains(msg.getTopic())) {
                    var topic = msg.getTopic();
                    msgIdsByTopic.computeIfAbsent(topic, k -> new HashSet<>());
                    msgIdsByTopic.get(topic).add(msgId);
                }
            }
        }
        return msgIdsByTopic;
    }
}
