package protocols.pubsub.common;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

public class SubscriptionReply extends ProtoReply {

	public static final short REQUEST_ID = 205;

	private final String topic;

	public SubscriptionReply(String topic) {
		super(REQUEST_ID);
		this.topic = topic;
	}

	public String getTopic() {
		return this.topic;
	}

}
