package asd.protocols.overlay.kad.timers;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class RefreshRoutingTable extends ProtoTimer {
    public static final short ID = Kademlia.ID + 2;

    public final KadID rtid;

    public RefreshRoutingTable() {
        super(ID);
        this.rtid = KadID.DEFAULT_RTID;
    }

    public RefreshRoutingTable(KadID rtid) {
        super(ID);
        this.rtid = rtid;
    }

    @Override
    public ProtoTimer clone() {
        return this;
    }
}
