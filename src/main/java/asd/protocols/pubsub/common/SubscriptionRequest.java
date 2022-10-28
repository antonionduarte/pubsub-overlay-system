package asd.protocols.pubsub.common;

import pt.unl.fct.di.novasys.babel.generic.ProtoRequest;

public class SubscriptionRequest extends ProtoRequest {

	public static final short REQUEST_ID = 204;

	private final String topic;

	public SubscriptionRequest(String topic) {
		super(REQUEST_ID);
		this.topic = topic;
	}

	public String getTopic() {
		return this.topic;
	}
}
