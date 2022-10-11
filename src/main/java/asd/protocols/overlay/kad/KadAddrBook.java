package asd.protocols.overlay.kad;

import java.util.HashMap;

import pt.unl.fct.di.novasys.network.data.Host;

public class KadAddrBook {
    private final HashMap<Host, KadID> host2id;
    private final HashMap<KadID, Host> id2host;

    public KadAddrBook() {
        this.host2id = new HashMap<>();
        this.id2host = new HashMap<>();
    }

    public KadPeer getPeerFromID(KadID id) {
        var host = this.getHostFromID(id);
        if (host == null)
            return null;
        return new KadPeer(id, host);
    }

    public KadPeer getPeerFromHost(Host host) {
        var id = this.getIdFromHost(host);
        if (id == null)
            return null;
        return new KadPeer(id, host);
    }

    public Host getHostFromID(KadID id) {
        return this.id2host.get(id);
    }

    public KadID getIdFromHost(Host host) {
        return this.host2id.get(host);
    }

    public void add(KadPeer peer) {
        this.add(peer.id, peer.host);
    }

    public void add(KadID id, Host host) {
        this.host2id.put(host, id);
        this.id2host.put(id, host);
    }

    public boolean contains(KadID id) {
        return this.id2host.containsKey(id);
    }

    public boolean contains(Host host) {
        return this.host2id.containsKey(host);
    }

    public void remove(KadID id) {
        var host = this.getHostFromID(id);
        if (host == null)
            return;
        this.host2id.remove(host);
        this.id2host.remove(id);
    }

    public void remove(Host host) {
        var id = this.getIdFromHost(host);
        if (id == null)
            return;
        this.host2id.remove(host);
        this.id2host.remove(id);
    }
}
