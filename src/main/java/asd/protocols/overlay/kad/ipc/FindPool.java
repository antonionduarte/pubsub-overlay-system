package asd.protocols.overlay.kad.ipc;

import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class FindPool extends ProtoRequest {
    public static final short ID = Kademlia.ID + 3;

    public final String pool;

    public FindPool(String pool) {
        super(ID);
        this.pool = pool;
    }
}
