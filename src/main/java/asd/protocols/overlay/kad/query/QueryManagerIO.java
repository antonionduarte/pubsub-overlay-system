package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadPeer;

public interface QueryManagerIO {
    void discover(KadPeer peer);

    void findNodeRequest(int context, KadID id, KadID target);

    void findValueRequest(int context, KadID id, KadID key);
}
