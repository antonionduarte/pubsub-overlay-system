package asd.protocols.overlay.kad.ipc;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class FindValue extends ProtoRequest {
    public static final short ID = Kademlia.ID + 7;

    public final KadID key;

    public FindValue(KadID key) {
        super(ID);
        this.key = key;
    }

}
