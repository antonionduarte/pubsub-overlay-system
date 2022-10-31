package asd.protocols.overlay.kad.query;

import java.util.List;

import asd.protocols.overlay.kad.KadID;

public interface FindPoolQueryCallbacks {
    void onQueryResult(List<KadID> closest, List<KadID> members);
}
