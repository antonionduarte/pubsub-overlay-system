package asd.protocols.overlay.kad.query;

import java.util.HashSet;
import java.util.List;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;

class FindSwarmQuery extends Query {
    private final FindSwarmQueryCallbacks callbacks;
    private final HashSet<KadID> members;

    public FindSwarmQuery(QueryIO qio, KadID self, KadParams kadparams, KadID swarm, List<KadPeer> seeds,
            FindSwarmQueryDescriptor descriptor) {
        super(qio, self, kadparams, swarm, seeds);
        this.callbacks = descriptor.callbacks;
        this.members = new HashSet<>();
    }

    @Override
    void request(QueryIO qio, KadID peer, KadID target) {
        qio.findSwarmRequest(peer, target);
    }

    @Override
    void onFinish(QPeerSet set) {
        var closest = set.closest();
        if (this.callbacks != null)
            this.callbacks.onQueryResult(closest, this.members.stream().toList());
    }

    @Override
    protected final void onFindSwarmResponse(KadID from, List<KadPeer> closest, List<KadPeer> members) {
        super.onFindSwarmResponse(from, closest, members);
        members.stream().map(m -> m.id).forEach(this.members::add);
        if (this.members.size() >= 20) {
            this.finish();
        }
    }
}
