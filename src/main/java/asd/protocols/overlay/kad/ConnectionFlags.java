package asd.protocols.overlay.kad;

import java.util.HashMap;

import pt.unl.fct.di.novasys.network.data.Host;

public class ConnectionFlags {
    public static final int RECEIVED_HANDSHAKE = (1 << 0);
    public static final int SENT_HANDSHAKE = (1 << 1);
    public static final int IS_ATTEMPTING_CONNECT = (1 << 2);

    private final HashMap<Host, Integer> conns;

    public ConnectionFlags() {
        this.conns = new HashMap<>();
    }

    public boolean test(Host host, int flag) {
        return (this.getFlags(host) & flag) != 0;
    }

    public void set(Host host, int flag) {
        var f = this.getFlags(host) | flag;
        this.conns.put(host, f);
    }

    public void unset(Host host, int flag) {
        var f = this.getFlags(host) & ~flag;
        this.conns.put(host, f);
    }

    public void reset(Host host, int flag) {
        this.conns.put(host, flag);
    }

    public void clear(Host host) {
        this.conns.put(host, 0);
    }

    private int getFlags(Host host) {
        return this.conns.getOrDefault(host, 0);
    }
}
