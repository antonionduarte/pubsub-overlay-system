package asd.protocols.overlay.kad.messages;

import asd.metrics.MetricsProtoMessage;
import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;

public class LeftPoolMessage extends MetricsProtoMessage {
	public static final short ID = Kademlia.ID + 24;

	public final long context;
	public final KadID pool;

	public LeftPoolMessage(long context, KadID pool) {
		super(ID);
		this.context = context;
		this.pool = pool;
	}
}
