package asd.protocols.overlay.kad.ipc;

import java.util.List;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

public class FindClosestReply extends ProtoReply {
    public static final short ID = Kademlia.ID + 2;

    public final KadID target;
    public final List<KadPeer> closest;

    public FindClosestReply(KadID target, List<KadPeer> closest) {
        super(ID);
        this.target = target;
        this.closest = closest;
    }

}
