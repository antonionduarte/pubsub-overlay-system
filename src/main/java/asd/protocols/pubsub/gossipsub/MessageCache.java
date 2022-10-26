package asd.protocols.pubsub.gossipsub;

import asd.protocols.pubsub.gossipsub.messages.PublishMessage;
import org.apache.commons.lang3.tuple.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MessageCache {

    // msgId -> (message, iWantCounts)
    private final Map<UUID, Pair<PublishMessage, Integer>> messages;
    private final Map<String, Set<UUID>> msgIdsPerTopic;

    public MessageCache(int ttl, int capacity) {
        messages = new HashMap<>();
        msgIdsPerTopic = new HashMap<>();
    }


    public void put(UUID msgId, PublishMessage publishMessage) {
        messages.put(msgId, Pair.of(publishMessage, 0));
    }

    public boolean contains(UUID msgId) {
        return messages.containsKey(msgId);
    }

    public PublishMessage get(UUID msgId) {
        var messageEntry = messages.get(msgId);
        messageEntry.setValue(messageEntry.getRight()+1);
        return messages.get(msgId).getLeft();
    }

    public Map<String, Set<UUID>> getMessageIDsByTopic(Set<String> topics) {
        //TODO
    }
}
