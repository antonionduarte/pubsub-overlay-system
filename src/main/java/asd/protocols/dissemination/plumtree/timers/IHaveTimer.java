package asd.protocols.dissemination.plumtree.timers;

import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class IHaveTimer extends ProtoTimer {

	public static final short TIMER_ID = 100;

	private final int messageId;

	public IHaveTimer(int messageId) {
		super(TIMER_ID);
		this.messageId = messageId;
	}

	public int getMessageId() {
		return messageId;
	}

	@Override
	public ProtoTimer clone() {
		return this;
	}
}
