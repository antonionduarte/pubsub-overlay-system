package asd.protocols.overlay.kad.messages;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

public class LeftPoolMessage extends ProtoMessage {
    public static final short ID = Kademlia.ID + 24;

    public final long context;
    public final KadID pool;

    public LeftPoolMessage(long context, KadID pool) {
        super(ID);
        this.context = context;
        this.pool = pool;
    }
}
