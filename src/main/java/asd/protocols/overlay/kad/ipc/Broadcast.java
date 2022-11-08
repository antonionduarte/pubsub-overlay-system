package asd.protocols.overlay.kad.ipc;

import java.util.UUID;

import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class Broadcast extends ProtoRequest {
    public static final short ID = Kademlia.ID + 100;

    public final String topic;
    public final UUID uuid;
    public final byte[] payload;

    public Broadcast(String pool, UUID uuid, byte[] payload) {
        super(ID);
        this.topic = pool;
        this.uuid = uuid;
        this.payload = payload;
    }
}
