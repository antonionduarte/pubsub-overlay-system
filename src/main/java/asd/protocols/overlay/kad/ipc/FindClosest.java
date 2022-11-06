package asd.protocols.overlay.kad.ipc;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class FindClosest extends ProtoRequest {
    public static final short ID = Kademlia.ID + 1;

    public final KadID rtid;
    public final KadID target;

    public FindClosest(KadID target) {
        super(ID);
        this.rtid = KadID.DEFAULT_RTID;
        this.target = target;
    }

    public FindClosest(String namespace, KadID target) {
        super(ID);
        this.rtid = KadID.ofData(namespace);
        this.target = target;
    }

}
