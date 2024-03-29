package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import asd.utils.ASDUtils;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Prune extends ProtoMessage {

	public static final short ID = GossipSub.ID + 7;
	public static ISerializer<Prune> serializer = new ISerializer<>() {
		@Override
		public void serialize(Prune prune, ByteBuf byteBuf) throws IOException {
			byteBuf.writeInt(prune.peersPerTopic.entrySet().size());
			for (var entry : prune.peersPerTopic.entrySet()) {
				var topic = entry.getKey();
				ASDUtils.stringSerializer.serialize(topic, byteBuf);

				var peers = entry.getValue();
				byteBuf.writeInt(peers.size());
				for (var peer : peers) {
					Host.serializer.serialize(peer, byteBuf);
				}
			}
		}

		@Override
		public Prune deserialize(ByteBuf byteBuf) throws IOException {
			int numEntries = byteBuf.readInt();
			Map<String, Set<Host>> peersPerTopic = new HashMap<>(numEntries);

			for (int i = 0; i < numEntries; i++) {
				var topic = ASDUtils.stringSerializer.deserialize(byteBuf);

				var numPeers = byteBuf.readInt();
				Set<Host> peers = new HashSet<>(numPeers);
				for (int j = 0; j < numPeers; j++) {
					peers.add(Host.serializer.deserialize(byteBuf));
				}
				peersPerTopic.put(topic, peers);
			}
			return new Prune(peersPerTopic);
		}
	};
	private final Map<String, Set<Host>> peersPerTopic;

	public Prune(Map<String, Set<Host>> peersPerTopic) {
		super(ID);
		this.peersPerTopic = peersPerTopic;
	}

	public void append(Prune prune) {
		for (var entry : prune.getPeersPerTopic().entrySet()) {
			peersPerTopic.putIfAbsent(entry.getKey(), entry.getValue());
		}
	}

	public Map<String, Set<Host>> getPeersPerTopic() {
		return peersPerTopic;
	}
}
