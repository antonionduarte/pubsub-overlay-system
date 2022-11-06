package asd.protocols.overlay.kad.query;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadPeer;

class QMQueryIO implements QueryIO {
    private final QueryManagerIO qmio;
    private final long context;

    public QMQueryIO(QueryManagerIO qmio, long context) {
        this.qmio = qmio;
        this.context = context;
    }

    @Override
    public void discover(KadPeer peer) {
        this.qmio.discover(peer);
    }

    @Override
    public void findNodeRequest(KadID id, KadID rtid, KadID target) {
        this.qmio.findNodeRequest(this.context, id, rtid, target);
    }

    @Override
    public void findValueRequest(KadID id, KadID key) {
        this.qmio.findValueRequest(this.context, id, key);
    }

    @Override
    public void findSwarmRequest(KadID id, KadID swarm) {
        this.qmio.findSwarmRequest(this.context, id, swarm);
    }

    @Override
    public void findPoolRequest(KadID id, KadID pool) {
        this.qmio.findPoolRequest(this.context, id, pool);
    }
}
