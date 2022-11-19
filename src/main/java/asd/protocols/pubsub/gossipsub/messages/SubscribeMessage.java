package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import asd.utils.ASDUtils;
import io.netty.buffer.ByteBuf;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.ISerializer;

import java.io.IOException;

public class SubscribeMessage extends ProtoMessage {

	public static final short ID = GossipSub.ID + 1;
	public static ISerializer<SubscribeMessage> serializer = new ISerializer<>() {
		@Override
		public void serialize(SubscribeMessage subscribeMessage, ByteBuf byteBuf) throws IOException {
			ASDUtils.stringSerializer.serialize(subscribeMessage.topic, byteBuf);
		}

		@Override
		public SubscribeMessage deserialize(ByteBuf byteBuf) throws IOException {
			var topic = ASDUtils.stringSerializer.deserialize(byteBuf);

			return new SubscribeMessage(topic);
		}
	};
	private final String topic;

	public SubscribeMessage(String topic) {
		super(ID);
		this.topic = topic;
	}

	public String getTopic() {
		return topic;
	}
}
