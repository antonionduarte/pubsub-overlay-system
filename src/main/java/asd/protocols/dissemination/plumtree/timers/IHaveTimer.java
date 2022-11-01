package asd.protocols.dissemination.plumtree.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.UUID;

public class IHaveTimer extends ProtoTimer {

	public static final short TIMER_ID = 100;

	private final UUID msgId;

	public IHaveTimer(UUID msgId) {
		super(TIMER_ID);
		this.msgId = msgId;
	}

	public UUID getMsgId() {
		return msgId;
	}

	@Override
	public ProtoTimer clone() {
		return this;
	}
}
