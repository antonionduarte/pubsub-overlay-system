package asd.protocols.overlay.hyparview.messages;

import asd.protocols.overlay.hyparview.Hyparview;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.Set;

public class ShuffleReply extends ProtoMessage {

	public static final short MESSAGE_ID = Hyparview.PROTOCOL_ID + 8;

	private final Set<Host> shuffleSet;

	public ShuffleReply(Set<Host> shuffleSet) {
		super(MESSAGE_ID);
		this.shuffleSet = shuffleSet;
	}

	public Set<Host> getShuffleSet() {
		return shuffleSet;
	}
}
