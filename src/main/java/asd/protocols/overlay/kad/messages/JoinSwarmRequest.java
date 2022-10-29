package asd.protocols.overlay.kad.messages;

import java.io.IOException;

import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

public class JoinSwarmRequest extends ProtoMessage {
    public static final short ID = Kademlia.ID + 8;

    public final KadID swarm;

    public JoinSwarmRequest(KadID swarm) {
        super(ID);
        this.swarm = swarm;
    }

    @Override
    public String toString() {
        return "JoinSwarmRequest [swarm=" + swarm + "]";
    }

    public static final ISerializer<JoinSwarmRequest> serializer = new ISerializer<JoinSwarmRequest>() {
        @Override
        public void serialize(JoinSwarmRequest t, ByteBuf out) throws IOException {
            KadID.serializer.serialize(t.swarm, out);
        }

        @Override
        public JoinSwarmRequest deserialize(ByteBuf in) throws IOException {
            return new JoinSwarmRequest(KadID.serializer.deserialize(in));
        }
    };
}
