package asd.protocols.overlay.kad.ipc;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class StoreValue extends ProtoRequest {
    public static final short ID = Kademlia.ID + 13;

    public final KadID key;
    public final byte[] value;

    public StoreValue(KadID key, byte[] value) {
        super(ID);
        this.key = key;
        this.value = value;
    }

}
