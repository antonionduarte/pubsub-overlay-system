package asd.protocols.overlay.kad;

import java.io.IOException;

import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

public class KadPeer {
	public final KadID id;
	public final Host host;

	public KadPeer(KadID id, Host host) {
		this.id = id;
		this.host = host;
	}

	@Override
	public String toString() {
		return "[" + this.host.toString() + " " + this.id.toString() + "]";
	}

	public static final ISerializer<KadPeer> serializer = new ISerializer<KadPeer>() {
		@Override
		public KadPeer deserialize(ByteBuf buf) throws IOException {
			var id = KadID.serializer.deserialize(buf);
			var host = Host.serializer.deserialize(buf);
			return new KadPeer(id, host);
		}

		@Override
		public void serialize(KadPeer peer, ByteBuf buf) throws IOException {
			KadID.serializer.serialize(peer.id, buf);
			Host.serializer.serialize(peer.host, buf);
		}
	};
}
