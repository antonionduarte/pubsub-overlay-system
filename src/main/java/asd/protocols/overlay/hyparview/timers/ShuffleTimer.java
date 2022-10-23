package asd.protocols.overlay.hyparview.timers;

import asd.protocols.overlay.hyparview.Hyparview;
import pt.unl.fct.di.novasys.babel.generic.ProtoTimer;

public class ShuffleTimer extends ProtoTimer {

	public static final short TIMER_ID = Hyparview.PROTOCOL_ID + 1;

	public ShuffleTimer() {
		super(TIMER_ID);
	}

	@Override
	public ProtoTimer clone() {
		return this;
	}
}
