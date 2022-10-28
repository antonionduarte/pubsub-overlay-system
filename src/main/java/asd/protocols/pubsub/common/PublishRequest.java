package asd.protocols.pubsub.common;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.UUID;

public class PublishRequest extends ProtoRequest {

	public static final short REQUEST_ID = 202;

	private final String topic;
	private final UUID msgId;
	private final Host sender;
	private final byte[] msg;


	public PublishRequest(String topic, UUID id, Host h, byte[] msg) {
		super(REQUEST_ID);
		this.topic = topic;
		this.msgId = id;
		this.sender = h;
		this.msg = new byte[msg.length];
		System.arraycopy(msg, 0, this.msg, 0, msg.length);
	}

	public PublishRequest(String topic, Host h, byte[] msg) {
		this(topic, UUID.randomUUID(), h, msg);
	}

	public String getTopic() {
		return this.topic;
	}

	public UUID getMsgID() {
		return this.msgId;
	}

	public Host getSender() {
		return this.sender;
	}

	public byte[] getMessage() {
		return this.msg;
	}
}
