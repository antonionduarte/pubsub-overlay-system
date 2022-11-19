package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class IWant extends ProtoMessage {

	public static final short ID = GossipSub.ID + 6;
	public static ISerializer<IWant> serializer = new ISerializer<>() {
		@Override
		public void serialize(IWant iWant, ByteBuf byteBuf) throws IOException {
			byteBuf.writeInt(iWant.messageIds.size());
			for (var msgId : iWant.messageIds) {
				byteBuf.writeLong(msgId.getMostSignificantBits());
				byteBuf.writeLong(msgId.getLeastSignificantBits());
			}
		}

		@Override
		public IWant deserialize(ByteBuf byteBuf) throws IOException {
			var numIds = byteBuf.readInt();
			Set<UUID> msgIds = new HashSet<>(numIds);

			for (int i = 0; i < numIds; i++) {
				var mostSigBits = byteBuf.readLong();
				var leastSigBits = byteBuf.readLong();
				msgIds.add(new UUID(mostSigBits, leastSigBits));
			}
			return new IWant(msgIds);
		}
	};
	private final Set<UUID> messageIds;

	public IWant(Set<UUID> messageIds) {
		super(ID);
		this.messageIds = messageIds;
	}

	public Set<UUID> getMessageIds() {
		return messageIds;
	}
}
