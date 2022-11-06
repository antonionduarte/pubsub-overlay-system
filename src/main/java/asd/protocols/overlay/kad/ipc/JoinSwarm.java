package asd.protocols.overlay.kad.ipc;

import java.util.Optional;

import asd.protocols.overlay.kad.Kademlia;
import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class JoinSwarm extends ProtoRequest {
    public static final short ID = Kademlia.ID + 11;

    public final String swarm;
    public final Optional<Integer> sample_size;

    public JoinSwarm(String swarm) {
        super(ID);
        this.swarm = swarm;
        this.sample_size = Optional.empty();
    }

    public JoinSwarm(String swarm, int sample_size) {
        super(ID);
        this.swarm = swarm;
        this.sample_size = Optional.of(sample_size);
    }

}