package asd.protocols.overlay.kad.query;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.KadParams;
import asd.protocols.overlay.kad.KadPeer;

abstract class Query {
    private static final Logger logger = LogManager.getLogger(Query.class);

    private static class ActiveRequest {
        public final KadID peer;
        public final Instant start;

        public ActiveRequest(KadID peer) {
            this.peer = peer;
            this.start = Instant.now();
        }
    }

    private final QueryIO qio;
    private final KadID self;
    private final KadParams kadparams;
    private final KadID target;
    private final QPeerSet peers;
    private final ArrayList<ActiveRequest> active_requests;

    private boolean finished;

    Query(QueryIO qio, KadID self, KadParams kadparams, KadID target, List<KadPeer> seeds) {
        this.qio = qio;
        this.self = self;
        this.kadparams = kadparams;
        this.target = target;
        this.peers = new QPeerSet(this.kadparams.k, target);
        this.finished = false;
        this.active_requests = new ArrayList<>();

        this.addExtraPeers(seeds);
    }

    public final void start() {
        this.makeRequests();
    }

    public final boolean isFinished() {
        return this.finished;
    }

    abstract void request(QueryIO qio, KadID peer, KadID target);

    abstract void onFinish(QPeerSet set);

    protected void onFindNodeResponse(KadID from, List<KadPeer> closest) {
        if (!(this.peers.isInState(from, QPeerSet.State.INPROGRESS)
                || this.peers.isInState(from, QPeerSet.State.FAILED)))
            throw new IllegalStateException("Received FindNodeResponse from peer that was not requested: " + from
                    + ". Peer state is " + this.peers.getState(from));
        if (this.isFinished())
            return;
        this.peers.markFinished(from);
        this.removeActiveRequest(from);
        this.addExtraPeers(closest);
        this.makeRequests();
    }

    protected void onFindValueResponse(KadID from, List<KadPeer> closest, Optional<byte[]> value) {
        if (!(this.peers.isInState(from, QPeerSet.State.INPROGRESS)
                || this.peers.isInState(from, QPeerSet.State.FAILED)))
            throw new IllegalStateException("Received FindValueResponse from peer that was not requested: " + from
                    + ". Peer state is " + this.peers.getState(from));
        if (this.isFinished())
            return;
        this.peers.markFinished(from);
        this.removeActiveRequest(from);
        this.addExtraPeers(closest);
        this.makeRequests();
    }

    protected void onFindSwarmResponse(KadID from, List<KadPeer> closest, List<KadPeer> members) {
        if (!(this.peers.isInState(from, QPeerSet.State.INPROGRESS)
                || this.peers.isInState(from, QPeerSet.State.FAILED)))
            throw new IllegalStateException("Received FindSwarmResponse from peer that was not requested: " + from
                    + ". Peer state is " + this.peers.getState(from));
        if (this.isFinished())
            return;
        this.peers.markFinished(from);
        this.removeActiveRequest(from);
        this.addExtraPeers(closest);
        this.makeRequests();
    }

    protected void onFindPoolResponse(KadID from, List<KadPeer> closest, List<KadPeer> members) {
        if (!(this.peers.isInState(from, QPeerSet.State.INPROGRESS)
                || this.peers.isInState(from, QPeerSet.State.FAILED)))
            throw new IllegalStateException("Received FindPoolResponse from peer that was not requested: " + from
                    + ". Peer state is " + this.peers.getState(from));
        if (this.isFinished())
            return;
        this.peers.markFinished(from);
        this.removeActiveRequest(from);
        this.addExtraPeers(closest);
        this.makeRequests();
    }

    final void onPeerError(KadID peer) {
        if (this.isFinished())
            return;
        if (!this.peers.contains(peer))
            return;
        if (this.peers.isInState(peer, QPeerSet.State.FINISHED))
            return;
        logger.info("Peer {} failed", peer);
        this.removeActiveRequest(peer);
        this.peers.markFailed(peer);
        this.makeRequests();
    }

    final void checkTimeouts() {
        if (this.isFinished())
            return;

        var timedout = new ArrayList<KadID>();
        var now = Instant.now();

        for (var req : this.active_requests) {
            var elapsed = now.toEpochMilli() - req.start.toEpochMilli();
            if (elapsed > 3000)
                timedout.add(req.peer);
        }

        for (var peer : timedout)
            this.onPeerError(peer);
    }

    protected final void finish() {
        assert !this.finished;
        this.finished = true;
        this.onFinish(this.peers);
    }

    private void makeRequests() {
        assert !this.isFinished();

        while (this.peers.getNumInProgress() < this.kadparams.alpha && this.peers.getNumCandidates() > 0) {
            var candidate = this.peers.getCandidate();
            assert candidate != null;
            this.peers.markInProgress(candidate);
            this.addActiveRequest(candidate);
            this.request(this.qio, candidate, this.target);
        }

        if (this.peers.getNumInProgress() == 0 && this.peers.getNumCandidates() == 0)
            this.finish();
    }

    private void addExtraPeers(List<KadPeer> peers) {
        for (var peer : peers) {
            if (!peer.id.equals(this.self)) {
                this.qio.discover(peer);
                this.peers.add(peer.id);
            }
        }
    }

    private void addActiveRequest(KadID peer) {
        assert this.active_requests.size() <= this.kadparams.alpha;
        this.active_requests.add(new ActiveRequest(peer));
    }

    private void removeActiveRequest(KadID peer) {
        assert this.active_requests.size() <= this.kadparams.alpha;
        var removed = this.active_requests.removeIf(r -> r.peer.equals(peer));
        assert removed;
    }
}