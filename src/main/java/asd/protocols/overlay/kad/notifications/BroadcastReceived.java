package asd.protocols.overlay.kad.notifications;

import java.util.UUID;

import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;

public class BroadcastReceived extends ProtoNotification {
    public static final short ID = Kademlia.ID + 1;

    public final UUID uuid;
    public final byte[] payload;

    public BroadcastReceived(UUID uuid, byte[] payload) {
        super(ID);
        this.uuid = uuid;
        this.payload = payload;
    }
}
