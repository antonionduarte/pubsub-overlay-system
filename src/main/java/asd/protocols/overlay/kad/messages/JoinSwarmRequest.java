package asd.protocols.overlay.kad.messages;

import asd.metrics.MetricsProtoMessage;
import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class JoinSwarmRequest extends MetricsProtoMessage {
	public static final short ID = Kademlia.ID + 8;
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
	public final KadID swarm;

	public JoinSwarmRequest(KadID swarm) {
		super(ID);
		this.swarm = swarm;
	}

	@Override
	public String toString() {
		return "JoinSwarmRequest [swarm=" + swarm + "]";
	}
}
