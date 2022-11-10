package asd.protocols.overlay.kad.routing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;

public class RoutingTables {
    private final KadParams params;
    private final KadID self;
    private final HashMap<KadID, RoutingTable> tables;

    public RoutingTables(KadParams params, KadID self) {
        this.params = params;
        this.self = self;
        this.tables = new HashMap<>();

        this.tables.put(KadID.DEFAULT_RTID, new RoutingTable(this.params.k, this.self));
    }

    public RoutingTable create(KadID rtid) {
        if (!this.contains(rtid))
            this.tables.put(rtid, new RoutingTable(this.params.pubsub_k, this.self));
        return this.tables.get(rtid);
    }

    public boolean contains(KadID rtid) {
        return this.tables.containsKey(rtid);
    }

    public RoutingTable get(KadID rtid) {
        return this.tables.get(rtid);
    }

    public void remove(KadID rtid) {
        if (!rtid.equals(KadID.DEFAULT_RTID))
            this.tables.remove(rtid);
    }

    public void removePeer(KadID peer) {
        for (RoutingTable rt : this.tables.values())
            rt.remove(peer);
    }

    public RoutingTable main() {
        return this.tables.get(KadID.DEFAULT_RTID);
    }

    public List<KadPeer> sample(KadID rtid) {
        RoutingTable rt = this.tables.get(rtid);
        if (rt == null)
            return new ArrayList<>();
        return rt.getSample(this.params.k);
    }

    public List<KadPeer> closest(KadID rtid, KadID target) {
        RoutingTable rt = this.tables.get(rtid);
        if (rt == null)
            return new ArrayList<>();
        return rt.closest(target);
    }

    public List<KadPeer> closest(KadID target) {
        return this.closest(KadID.DEFAULT_RTID, target);
    }

    public List<KadPeer> closestWithIgnore(KadID rtid, KadID target, KadID ignore) {
        RoutingTable rt = this.tables.get(rtid);
        if (rt == null)
            return new ArrayList<>();
        return rt.closest(target, ignore);
    }

    public List<KadPeer> closestWithIgnore(KadID target, KadID ignore) {
        RoutingTable rt = this.tables.get(KadID.DEFAULT_RTID);
        if (rt == null)
            return new ArrayList<>();
        return rt.closest(target, ignore);
    }

    // Metrics helping crap
    public List<Map.Entry<KadID, RoutingTable>> allButTheMainOne() {
        List<Map.Entry<KadID, RoutingTable>> allButTheMainOne = new ArrayList<>();
        for (Map.Entry<KadID, RoutingTable> entry : this.tables.entrySet()) {
            if (!entry.getKey().equals(KadID.DEFAULT_RTID)) {
                allButTheMainOne.add(entry);
            }
        }
        return allButTheMainOne;
    }
}
