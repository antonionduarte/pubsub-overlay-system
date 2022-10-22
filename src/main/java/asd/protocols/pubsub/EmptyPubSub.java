package protocols.pubsub;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import protocols.pubsub.common.PublishRequest;
import protocols.pubsub.common.SubscriptionRequest;
import protocols.pubsub.common.UnsubscriptionRequest;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;

import java.io.IOException;
import java.util.Properties;

public class EmptyPubSub extends GenericProtocol {

	private static final Logger logger = LogManager.getLogger(EmptyPubSub.class);

	public static final String PROTO_NAME = "EmptyPubSub";
	public static final short PROTO_ID = 200;

	public EmptyPubSub() throws HandlerRegistrationException {
		super(PROTO_NAME, PROTO_ID);

		registerRequestHandler(SubscriptionRequest.REQUEST_ID, this::uponSubscriptionRequest);
		registerRequestHandler(PublishRequest.REQUEST_ID, this::uponPublishRequest);
		registerRequestHandler(UnsubscriptionRequest.REQUEST_ID, this::uponUnsubscriptionRequest);
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {

	}

	private void uponSubscriptionRequest(SubscriptionRequest request, short sourceProto) {
		logger.info("Completed subscription to topic: " + request.getTopic());
	}

	private void uponPublishRequest(PublishRequest request, short sourceProto) {
		logger.info("Completed publication on topic " + request.getTopic() + " and id: " + request.getMsgID());
	}

	private void uponUnsubscriptionRequest(UnsubscriptionRequest request, short sourceProto) {
		logger.info("Completed unsubscription to topic: " + request.getTopic());
	}

}
