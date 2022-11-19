package asd.protocols.dissemination.plumtree.messages;

import asd.protocols.dissemination.plumtree.PlumTree;
import asd.utils.ASDUtils;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.UUID;

public class Gossip extends ProtoMessage {
	public static final short MSG_ID = PlumTree.PROTOCOL_ID + 1;
	public static ISerializer<Gossip> serializer = new ISerializer<Gossip>() {
		@Override
		public void serialize(Gossip gossip, ByteBuf byteBuf) throws IOException {
			Host.serializer.serialize(gossip.sender, byteBuf);
			ASDUtils.stringSerializer.serialize(gossip.topic, byteBuf);
			byteBuf.writeLong(gossip.msgId.getMostSignificantBits());
			byteBuf.writeLong(gossip.msgId.getLeastSignificantBits());
			byteBuf.writeInt(gossip.msg.length);
			byteBuf.writeBytes(gossip.msg);
			byteBuf.writeInt(gossip.hopCount);
		}

		@Override
		public Gossip deserialize(ByteBuf byteBuf) throws IOException {
			var sender = Host.serializer.deserialize(byteBuf);
			var topic = ASDUtils.stringSerializer.deserialize(byteBuf);
			var mostSigBits = byteBuf.readLong();
			var leastSigBits = byteBuf.readLong();
			var msgId = new UUID(mostSigBits, leastSigBits);
			var lenMsg = byteBuf.readInt();
			var msg = new byte[lenMsg];
			byteBuf.readBytes(msg);
			var hopCount = byteBuf.readInt();

			return new Gossip(msg, topic, msgId, sender, hopCount);
		}
	};
	private final String topic;
	private final UUID msgId;
	private final Host sender;
	private final byte[] msg;
	private final int hopCount;

	public Gossip(byte[] msg, String topic, UUID id, Host host, int hopCount) {
		super(MSG_ID);
		this.msg = msg;
		this.topic = topic;
		this.msgId = id;
		this.sender = host;
		this.hopCount = hopCount;
	}

	public int getHopCount() {
		return hopCount;
	}

	public String getTopic() {
		return topic;
	}

	public UUID getMsgId() {
		return msgId;
	}

	public Host getSender() {
		return sender;
	}

	public byte[] getMsg() {
		return msg;
	}
}
