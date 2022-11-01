package asd.protocols.dissemination.plumtree.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.UUID;

public class DeliverNotification extends ProtoNotification {

	public static final short NOTIFICATION_ID = 201;

	private final String topic;
	private final UUID id;
	private final Host sender;
	private final byte[] msg;

	public DeliverNotification(byte[] msg, String topic, UUID id, Host host) {
		super(NOTIFICATION_ID);
		this.topic = topic;
		this.id = id;
		this.sender = host;
		this.msg = msg;
	}

	public String getTopic() {
		return topic;
	}

	public Host getSender() {
		return sender;
	}

	public UUID getMsgId() {
		return id;
	}

	public byte[] getMsg() {
		return msg;
	}
}
