package asd.protocols.overlay.kad.ipc;

import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class JoinPool extends ProtoRequest {
    public static final short ID = Kademlia.ID + 9;

    public final String pool;

    public JoinPool(String pool) {
        super(ID);
        this.pool = pool;
    }
}