package asd.protocols.overlay.kad.query;

import java.util.List;

import asd.protocols.overlay.kad.KadID;

public class FindPoolQueryResult {
    public final List<KadID> closest;
    public final List<KadID> members;

    public FindPoolQueryResult(List<KadID> closest, List<KadID> members) {
        this.closest = closest;
        this.members = members;
    }
}
