package asd.protocols.overlay.kad.ipc;

import java.util.Optional;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class FindClosest extends ProtoRequest {
    public static final short ID = Kademlia.ID + 1;

    public final KadID target;
    public final Optional<KadID> pool;

    public FindClosest(KadID target) {
        super(ID);
        this.target = target;
        this.pool = Optional.empty();
    }

    public FindClosest(KadID target, String pool) {
        super(ID);
        this.target = target;
        this.pool = Optional.of(KadID.ofData(pool));
    }

}
