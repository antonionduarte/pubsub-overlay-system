package asd.protocols.overlay.kad.ipc;

import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

public class JoinPoolReply extends ProtoReply {
    public static final short ID = Kademlia.ID + 1;

    public final String pool;

    public JoinPoolReply(String pool) {
        super(ID);
        this.pool = pool;
    }
}
