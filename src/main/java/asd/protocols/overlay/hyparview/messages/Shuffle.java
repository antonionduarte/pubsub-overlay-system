package asd.protocols.overlay.hyparview.messages;

import asd.protocols.overlay.hyparview.Hyparview;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.Set;

public class Shuffle extends ProtoMessage {

	public static final short MESSAGE_ID = Hyparview.PROTOCOL_ID + 5;

	private final Set<Host> shuffleList;
	private final Host originalNode;
	private final int timeToLive;

	public Shuffle(int timeToLive, Set<Host> shuffleList, Host originalNode) {
		super(MESSAGE_ID);
		this.timeToLive = timeToLive;
		this.shuffleList = shuffleList;
		this.originalNode = originalNode;
	}

	public Host getOriginalNode() {
		return originalNode;
	}

	public Set<Host> getShuffleList() {
		return shuffleList;
	}

	public int getTimeToLive() {
		return timeToLive;
	}
}
