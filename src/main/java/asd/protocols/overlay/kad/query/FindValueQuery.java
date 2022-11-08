package asd.protocols.overlay.kad.query;

import java.util.List;
import java.util.Optional;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;

class FindValueQuery extends Query {

    private final FindValueQueryCallbacks callbacks;
    private KadID provider;
    private Optional<byte[]> value;

    public FindValueQuery(QueryIO qio, KadID self, KadParams kadparams, KadID target, List<KadPeer> seeds,
            FindValueQueryDescriptor descriptor) {
        super(qio, self, kadparams, target, seeds);
        this.callbacks = descriptor.callbacks;
        this.provider = null;
        this.value = Optional.empty();
    }

    @Override
    void request(QueryIO qio, KadID peer, KadID target) {
        qio.findValueRequest(peer, target);
    }

    @Override
    void onFinish(QPeerSet set) {
        if (this.callbacks == null)
            return;
        var cache_target = set.stream().filter(entry -> entry.getValue() == QPeerSet.State.FINISHED)
                .filter(entry -> !entry.getKey().equals(this.provider)).findFirst();
        this.callbacks.onQueryResult(cache_target.map(entry -> entry.getKey()), this.value);
    }

    @Override
    protected final void onFindValueResponse(KadID from, List<KadPeer> closest, Optional<byte[]> value) {
        if (value.isPresent()) {
            this.provider = from;
            this.value = value;
            this.finish();
        }
        super.onFindValueResponse(from, closest, value);
    }
}
