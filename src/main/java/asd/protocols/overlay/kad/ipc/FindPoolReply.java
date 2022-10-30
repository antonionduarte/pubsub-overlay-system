package asd.protocols.overlay.kad.ipc;

import java.util.List;

import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import pt.unl.fct.di.novasys.network.data.Host;

public class FindPoolReply extends ProtoRequest {

    public static final short ID = Kademlia.ID + 1;

    public final String pool;
    public final List<Host> members;

    public FindPoolReply(String pool, List<Host> members) {
        super(ID);
        this.pool = pool;
        this.members = members;
    }
}
