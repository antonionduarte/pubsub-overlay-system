package asd.protocols.overlay.kad;

import java.time.Duration;

public class KadParams {
    public final int k;
    public final int alpha;
    public final Duration swarmttl;
    public final int pubsub_fanout;
    public final Duration pubsub_msg_timeout;
    public final int pubsub_k;

    public KadParams(int k, int alpha, Duration swarmttl, int pubsub_fanout, Duration pubsub_msg_timeout,
            int pubsub_k) {
        this.k = k;
        this.alpha = alpha;
        this.swarmttl = swarmttl;
        this.pubsub_fanout = pubsub_fanout;
        this.pubsub_msg_timeout = pubsub_msg_timeout;
        this.pubsub_k = pubsub_k;
    }
}
