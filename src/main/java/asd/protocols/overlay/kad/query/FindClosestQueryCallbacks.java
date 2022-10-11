package asd.protocols.overlay.kad.query;

import java.util.List;

import asd.protocols.overlay.kad.KadPeer;

public interface FindClosestQueryCallbacks {
    void onQueryResult(List<KadPeer> closest);
}
