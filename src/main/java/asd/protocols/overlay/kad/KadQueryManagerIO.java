package asd.protocols.overlay.kad;

import java.util.ArrayDeque;
import java.util.Queue;

import asd.protocols.overlay.kad.messages.FindNodeRequest;
import asd.protocols.overlay.kad.messages.FindPoolRequest;
import asd.protocols.overlay.kad.messages.FindSwarmRequest;
import asd.protocols.overlay.kad.messages.FindValueRequest;
import asd.protocols.overlay.kad.query.QueryManagerIO;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;

class KadQueryManagerIO implements QueryManagerIO {
    public static class Request {
        public final long context;
        public final KadID destination;
        public final ProtoMessage message;

        Request(long context, KadID destination, ProtoMessage message) {
            this.context = context;
            this.destination = destination;
            this.message = message;
        }
    }

    private final KadAddrBook addrbook;
    private final Queue<Request> requests;

    public KadQueryManagerIO(KadAddrBook addrbook) {
        this.addrbook = addrbook;
        this.requests = new ArrayDeque<>();
    }

    @Override
    public void discover(KadPeer peer) {
        this.addrbook.add(peer);
    }

    @Override
    public void findNodeRequest(long context, KadID id, KadID target) {
        var message = new FindNodeRequest(context, target);
        this.requests.add(new Request(context, id, message));
    }

    @Override
    public void findValueRequest(long context, KadID id, KadID key) {
        var message = new FindValueRequest(context, key);
        this.requests.add(new Request(context, id, message));
    }

    @Override
    public void findSwarmRequest(long context, KadID id, KadID swarm) {
        var message = new FindSwarmRequest(context, swarm);
        this.requests.add(new Request(context, id, message));
    }

    @Override
    public void findPoolRequest(long context, KadID id, KadID pool) {
        var message = new FindPoolRequest(context, pool);
        this.requests.add(new Request(context, id, message));
    }

    public Request pop() {
        return this.requests.poll();
    }
}
