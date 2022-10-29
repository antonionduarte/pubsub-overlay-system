package asd.protocols.overlay.kad.ipc;

import java.util.Optional;

import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

public class FindValueReply extends ProtoReply {

    public static final short ID = Kademlia.ID + 6;

    public final Optional<byte[]> value;

    public FindValueReply(Optional<byte[]> value) {
        super(ID);
        this.value = value;
    }
}
