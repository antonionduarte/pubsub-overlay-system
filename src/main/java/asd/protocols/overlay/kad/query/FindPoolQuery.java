package asd.protocols.overlay.kad.query;

import java.util.HashSet;
import java.util.List;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;

class FindPoolQuery extends Query {
    private final FindPoolQueryCallbacks callbacks;
    private final HashSet<KadID> members;
    private final int sample_size;

    public FindPoolQuery(QueryIO qio, KadID self, KadParams kadparams, KadID pool, List<KadPeer> seeds,
            FindPoolQueryDescriptor descriptor) {
        super(qio, self, kadparams, pool, seeds);
        this.callbacks = descriptor.callbacks;
        this.members = new HashSet<>();
        this.sample_size = descriptor.sample_size.orElse(kadparams.k);
    }

    @Override
    void request(QueryIO qio, KadID peer, KadID target) {
        qio.findPoolRequest(peer, target);
    }

    @Override
    void onFinish(QPeerSet set) {
        var closest = set.closest();
        if (this.callbacks != null)
            this.callbacks.onQueryResult(closest, this.members.stream().toList());
    }

    @Override
    protected final void onFindPoolResponse(KadID from, List<KadPeer> closest, List<KadPeer> members) {
        super.onFindPoolResponse(from, closest, members);
        members.stream().map(m -> m.id).forEach(this.members::add);
        if (this.members.size() >= this.sample_size) {
            while (this.members.size() > this.sample_size)
                this.members.remove(this.members.iterator().next());
            this.finish();
        }
    }
}
