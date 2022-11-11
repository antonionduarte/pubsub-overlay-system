package asd.protocols.overlay.kad.query;

import java.util.List;

import asd.protocols.overlay.kad.KadID;

public class FindClosestQueryResult {
    public final List<KadID> closest;

    public FindClosestQueryResult(List<KadID> closest) {
        this.closest = closest;
    }
}
