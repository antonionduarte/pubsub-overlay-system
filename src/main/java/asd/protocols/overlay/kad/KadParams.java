package asd.protocols.overlay.kad;

import java.time.Duration;

public class KadParams {
	public final int k;
	public final int alpha;
	public final Duration query_request_timeout;
	public final Duration swarmttl;
	public final Duration pubsub_msg_timeout;
	public final int pubsub_k;
	public final int pubsub_rfac;

	public KadParams(int k, int alpha, Duration query_request_timeout, Duration swarmttl, Duration pubsub_msg_timeout,
	                 int pubsub_k, int pubsub_rfac) {
		this.k = k;
		this.alpha = alpha;
		this.query_request_timeout = query_request_timeout;
		this.swarmttl = swarmttl;
		this.pubsub_msg_timeout = pubsub_msg_timeout;
		this.pubsub_k = pubsub_k;
		this.pubsub_rfac = pubsub_rfac;
	}
}
