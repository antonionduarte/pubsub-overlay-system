package asd.protocols.overlay.kad.messages;

import java.io.IOException;

import asd.metrics.MetricsProtoMessage;
import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

public class FindSwarmRequest extends MetricsProtoMessage {
    public static final short ID = Kademlia.ID + 3;

    public final long context;
    public final KadID swarm;

    public FindSwarmRequest(long context, KadID swarm) {
        super(ID);
        this.context = context;
        this.swarm = swarm;
    }

    @Override
    public String toString() {
        return "FindSwarmRequest [context=" + context + ", swarm=" + swarm + "]";
    }

    public static final ISerializer<FindSwarmRequest> serializer = new ISerializer<FindSwarmRequest>() {
        @Override
        public void serialize(FindSwarmRequest t, ByteBuf out) throws IOException {
            out.writeLong(t.context);
            KadID.serializer.serialize(t.swarm, out);
        }

        @Override
        public FindSwarmRequest deserialize(ByteBuf in) throws IOException {
            return new FindSwarmRequest(in.readLong(), KadID.serializer.deserialize(in));
        }
    };
}
