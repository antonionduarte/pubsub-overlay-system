package protocols.pubsub.common;

import pt.unl.fct.di.novasys.babel.generic.ProtoReply;

public class UnsubscriptionReply extends ProtoReply {

	public static final short REQUEST_ID = 207;

	private final String topic;

	public UnsubscriptionReply(String topic) {
		super(REQUEST_ID);
		this.topic = topic;
	}

	public String getTopic() {
		return this.topic;
	}

}
