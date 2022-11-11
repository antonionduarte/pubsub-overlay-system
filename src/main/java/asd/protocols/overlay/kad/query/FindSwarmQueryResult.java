package asd.protocols.overlay.kad.query;

import java.util.List;

import asd.protocols.overlay.kad.KadID;

public class FindSwarmQueryResult {
    public final List<KadID> closest;
    public final List<KadID> members;

    public FindSwarmQueryResult(List<KadID> closest, List<KadID> members) {
        this.closest = closest;
        this.members = members;
    }
}
