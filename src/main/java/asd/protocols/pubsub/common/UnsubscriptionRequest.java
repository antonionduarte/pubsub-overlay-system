package protocols.pubsub.common;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class UnsubscriptionRequest extends ProtoRequest {

	public static final short REQUEST_ID = 206;

	private final String topic;

	public UnsubscriptionRequest(String topic) {
		super(REQUEST_ID);
		this.topic = topic;
	}

	public String getTopic() {
		return this.topic;
	}

}
