package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadPeer;

public interface QueryIO {
    void discover(KadPeer peer);

    void findNodeRequest(KadID id, KadID target);

    void findValueRequest(KadID id, KadID key);

    void findSwarmRequest(KadID id, KadID swarm);
}
