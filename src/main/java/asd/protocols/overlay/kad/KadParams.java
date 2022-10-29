package asd.protocols.overlay.kad;

import java.time.Duration;

public class KadParams {
    public final int k;
    public final int alpha;
    public final Duration swarmttl;

    public KadParams(int k, int alpha, Duration swarmttl) {
        this.k = k;
        this.alpha = alpha;
        this.swarmttl = swarmttl;
    }
}
