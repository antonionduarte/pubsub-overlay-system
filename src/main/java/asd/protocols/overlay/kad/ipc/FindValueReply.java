package asd.protocols.overlay.kad.ipc;

import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

import java.util.Optional;

public class FindValueReply extends ProtoReply {

	public static final short ID = Kademlia.ID + 8;

	public final Optional<byte[]> value;

	public FindValueReply(Optional<byte[]> value) {
		super(ID);
		this.value = value;
	}
}
