package asd.protocols.overlay.kad.query;

import java.util.List;

import asd.protocols.overlay.kad.KadID;

public interface FindClosestQueryCallbacks {
    void onQueryResult(List<KadID> closest);
}
