package asd.protocols.overlay.kad.query;

import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;

import asd.protocols.overlay.kad.KadAddrBook;
import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;
import asd.protocols.overlay.kad.messages.FindNodeRequest;
import asd.protocols.overlay.kad.messages.FindNodeResponse;
import asd.protocols.overlay.kad.messages.FindValueRequest;
import asd.protocols.overlay.kad.messages.FindValueResponse;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

abstract class Query {
    private final int context;
    private final KadParams kadparams;
    private final KadID target;
    private final KadAddrBook addrbook;
    private final Queue<QueryMessage> queue;
    private final KadID self;
    private final QPeerSet peers;

    private boolean finished;

    Query(int context, KadParams kadparams, KadID target, List<KadPeer> seeds, KadAddrBook addrbook,
            Queue<QueryMessage> queue, KadID self) {
        this.context = context;
        this.kadparams = kadparams;
        this.target = target;
        this.addrbook = addrbook;
        this.queue = queue;
        this.self = self;
        this.peers = new QPeerSet(this.kadparams.k, target);
        this.finished = false;

        this.addExtraPeers(seeds);
    }

    public final void start() {
        this.makeRequests();
    }

    public final boolean isFinished() {
        return this.finished;
    }

    abstract void request(KadID target, KadID peer);

    abstract void onFinish(QPeerSet set, List<KadPeer> closest);

    protected void onFindNodeResponse(FindNodeResponse msg, KadPeer from) {
        assert msg.context == this.context;
        if (!this.peers.isInState(from.id, QPeerSet.State.INPROGRESS))
            throw new IllegalStateException("Received FindNodeResponse from peer that was not requested: " + from
                    + ". Peer state is " + this.peers.getState(from.id));
        if (this.isFinished())
            return;
        this.peers.markFinished(from.id);
        this.addExtraPeers(msg.peers);
        this.makeRequests();
    }

    protected void onFindValueResponse(FindValueResponse msg, KadPeer from) {
        assert msg.context == this.context;
        if (!this.peers.isInState(from.id, QPeerSet.State.INPROGRESS))
            throw new IllegalStateException("Received FindValueResponse from peer that was not requested: " + from
                    + ". Peer state is " + this.peers.getState(from.id));
        if (this.isFinished())
            return;
        this.peers.markFinished(from.id);
        this.addExtraPeers(msg.peers);
        this.makeRequests();
    }

    protected final int getContext() {
        return this.context;
    }

    protected final void finish() {
        assert !this.finished;
        this.finished = true;
        var closest = this.peers.streamFinishedKClosest().map(id -> this.addrbook.getPeerFromID(id))
                .collect(Collectors.toList());
        this.onFinish(this.peers, closest);
    }

    protected final void sendMessage(KadID destination, FindNodeRequest msg) {
        this.sendMessage(destination, msg.context, msg);
    }

    protected final void sendMessage(KadID destination, FindValueRequest msg) {
        this.sendMessage(destination, msg.context, msg);
    }

    private void sendMessage(KadID destination, int context, ProtoMessage msg) {
        if (context != this.context)
            throw new IllegalArgumentException("Message context does not match query context");
        var host = this.addrbook.getHostFromID(destination);
        if (host == null)
            throw new IllegalArgumentException("Destination ID is not in address book");
        this.queue.add(new QueryMessage(host, msg));
    }

    private void makeRequests() {
        assert !this.isFinished();

        while (this.peers.getNumInProgress() < this.kadparams.alpha && this.peers.getNumCandidates() > 0) {
            var candidate = this.peers.getCandidate();
            assert candidate != null;
            this.peers.markInProgress(candidate);
            this.request(this.target, candidate);
        }

        if (this.peers.getNumInProgress() == 0 && this.peers.getNumCandidates() == 0)
            this.finish();
    }

    private void addExtraPeers(List<KadPeer> peers) {
        for (var peer : peers)
            if (!peer.id.equals(this.self))
                this.peers.add(peer.id);
    }
}