package asd.protocols.pubsub.unstructured;

import asd.protocols.pubsub.common.PublishRequest;
import asd.protocols.pubsub.common.SubscriptionRequest;
import asd.protocols.pubsub.common.UnsubscriptionRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class StructuredOverlayPubSub extends GenericProtocol {

	private static final Logger logger = LogManager.getLogger(StructuredOverlayPubSub.class);

	public static final String PROTO_NAME = "EmptyPubSub";
	public static final short PROTO_ID = 200;

	private final Set<String> subscribedTopics;

	public StructuredOverlayPubSub() throws HandlerRegistrationException {
		super(PROTO_NAME, PROTO_ID);

		this.subscribedTopics = new HashSet<>();

		registerRequestHandler(SubscriptionRequest.REQUEST_ID, this::uponSubscriptionRequest);
		registerRequestHandler(PublishRequest.REQUEST_ID, this::uponPublishRequest);
		registerRequestHandler(UnsubscriptionRequest.REQUEST_ID, this::uponUnsubscriptionRequest);
	}

	/*
		TODO:
		- Register the topics that the Node is Subscribed to.
		- Maybe make a Request so Hyparview can know the topics that the node is currently subscribed to?
			- Use that list to prioritize the active view neighbours of the node (the ones that are subscribed to the same topics)
			- Perhaps make it a Notification so Hyparview is always aware of the topics that the node is subscribed to.
		- Whenever we need to Publish a Message to a Topic we need to ask PlumTree to Broadcast it to the current neighbours.
		- Whenever a Message is Delivered we need to verify if the topic is in the current list of subscribed topics, and if so deliver it.
	 */

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {

	}

	private void uponSubscriptionRequest(SubscriptionRequest request, short sourceProto) {
		logger.info("Completed subscription to topic: " + request.getTopic());
		this.subscribedTopics.add(request.getTopic());
	}

	private void uponPublishRequest(PublishRequest request, short sourceProto) {
		logger.info("Completed publication on topic " + request.getTopic() + " and id: " + request.getMsgID());
		// make a request to the PlumTree to broadcast the message to the current neighbours.
	}

	private void uponUnsubscriptionRequest(UnsubscriptionRequest request, short sourceProto) {
		logger.info("Completed unsubscription to topic: " + request.getTopic());
		this.subscribedTopics.remove(request.getTopic());
	}
}
