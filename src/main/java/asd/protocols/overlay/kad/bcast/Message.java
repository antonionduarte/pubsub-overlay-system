package asd.protocols.overlay.kad.bcast;

import java.util.UUID;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadPeer;

public class Message {
    public final KadID rtid;
    public final UUID uuid;
    public final KadPeer origin;
    public final int hop_count;
    public final byte[] payload;

    public Message(KadID rtid, UUID uuid, KadPeer origin, byte[] payload, int hop_count) {
        this.rtid = rtid;
        this.uuid = uuid;
        this.origin = origin;
        this.hop_count = hop_count;
        this.payload = payload;
    }
}
