package asd.protocols.overlay.kad;

import java.util.HashMap;
import java.util.List;

public class PoolsRT {
    private final KadParams params;
    private final KadID self;
    private final HashMap<KadID, KadRT> pools;

    public PoolsRT(KadParams params, KadID self) {
        this.params = params;
        this.self = self;
        this.pools = new HashMap<>();
    }

    public void createPool(KadID pool) {
        if (!this.containsPool(pool))
            this.pools.put(pool, new KadRT(this.params.k, this.self));
    }

    public KadRT getPool(KadID pool) {
        assert this.containsPool(pool);
        return this.pools.get(pool);
    }

    public boolean containsPool(KadID pool) {
        return this.pools.containsKey(pool);
    }

    public List<KadPeer> getPoolSample(KadID pool) {
        KadRT rt = pools.get(pool);
        if (rt == null)
            return List.of();
        return rt.getSample(this.params.k);
    }
}
