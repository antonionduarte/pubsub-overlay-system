package asd.protocols.dissemination.plumtree.ipc;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.UUID;

public class Broadcast extends ProtoRequest {

	public static final short ID = 100;

	private final String topic;
	private final UUID msgId;
	private final Host sender;
	private final byte[] msg;

	public Broadcast(byte[] msg, String topic, UUID id, Host host) {
		super(ID);
		this.msg = msg;
		this.topic = topic;
		this.msgId = id;
		this.sender = host;
	}

	public UUID getMsgId() {
		return msgId;
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
