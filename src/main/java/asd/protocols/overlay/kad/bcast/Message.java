package asd.protocols.overlay.kad.bcast;

import java.util.UUID;

import asd.protocols.overlay.kad.KadPeer;

public class Message {
    public final UUID uuid;
    public final int depth;
    public final KadPeer origin;
    public final byte[] payload;
    public final String topic;
    public final int hop_count;

    public Message(UUID uuid, int depth, KadPeer origin, byte[] payload, String topic, int hop_count) {
        this.uuid = uuid;
        this.depth = depth;
        this.origin = origin;
        this.payload = payload;
        this.topic = topic;
        this.hop_count = hop_count;
    }
}
