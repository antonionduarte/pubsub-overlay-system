package asd.protocols.overlay.kad.notifications;

import java.util.UUID;

import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;

public class BroadcastReceived extends ProtoNotification {
    public static final short ID = Kademlia.ID + 1;

    public final String pool;
    public final UUID uuid;
    public final byte[] payload;
    public final KadPeer origin;

    public BroadcastReceived(String pool, UUID uuid, KadPeer origin, byte[] payload) {
        super(ID);
        this.pool = pool;
        this.uuid = uuid;
        this.payload = payload;
        this.origin = origin;
    }
}
