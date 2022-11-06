package asd.protocols.overlay.kad.query;

import java.util.List;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;

interface QueryDescriptor {
    KadID getRtid();

    KadID getTarget();

    Query createQuery(QueryIO qio, KadID self, KadParams kadparams, List<KadPeer> seeds);
}
