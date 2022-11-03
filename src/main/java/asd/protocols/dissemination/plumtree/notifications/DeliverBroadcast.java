package asd.protocols.dissemination.plumtree.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.UUID;

public class DeliverBroadcast extends ProtoNotification {
	public static final short NOTIFICATION_ID = 505;

	private final String topic;
	private final UUID msgId;
	private final Host sender;
	private final byte[] msg;
	private final int hopCount;

	public DeliverBroadcast(byte[] msg, String topic, UUID msgId, Host host, int hopCount) {
		super(NOTIFICATION_ID);
		this.topic = topic;
		this.msgId = msgId;
		this.sender = host;
		this.msg = msg;
		this.hopCount = hopCount;
	}

	public UUID getMsgId() {
		return msgId;
	}

	public int getHopCount() {
		return hopCount;
	}

	public Host getSender() {
		return sender;
	}

	public String getTopic() {
		return topic;
	}

	public byte[] getMsg() {
		return msg;
	}
}
