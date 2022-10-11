package asd.protocols.overlay.kad.query;

import java.util.List;
import java.util.Optional;
import java.util.Queue;

import asd.protocols.overlay.kad.KadAddrBook;
import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.messages.FindValueRequest;
import asd.protocols.overlay.kad.messages.FindValueResponse;

class FindValueQuery extends Query {

    private final FindValueQueryCallbacks callbacks;
    private KadID provider;
    private Optional<byte[]> value;

    public FindValueQuery(int context, KadParams kadparams, KadID target, List<KadPeer> seeds, KadAddrBook addrbook,
            Queue<QueryMessage> queue, KadID self, FindValueQueryDescriptor descriptor) {
        super(context, kadparams, target, seeds, addrbook, queue, self);
        this.callbacks = descriptor.callbacks;
        this.provider = null;
        this.value = Optional.empty();
    }

    @Override
    void request(KadID target, KadID peer) {
        this.sendMessage(peer, new FindValueRequest(this.getContext(), target));
    }

    @Override
    void onFinish(QPeerSet set, List<KadPeer> closest) {
        if (this.callbacks == null)
            return;
        var cache_target = set.stream().filter(entry -> entry.getValue() == QPeerSet.State.FINISHED)
                .filter(entry -> !entry.getKey().equals(this.provider)).findFirst();
        this.callbacks.onQueryResult(cache_target.map(entry -> entry.getKey()), this.value);
    }

    @Override
    protected final void onFindValueResponse(FindValueResponse msg, KadPeer from) {
        super.onFindValueResponse(msg, from);
        if (msg.value.isPresent()) {
            this.provider = from.id;
            this.value = msg.value;
            this.finish();
        }
    }
}
