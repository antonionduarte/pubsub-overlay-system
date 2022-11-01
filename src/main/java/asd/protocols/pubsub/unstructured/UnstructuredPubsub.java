package asd.protocols.pubsub.unstructured;

import asd.protocols.dissemination.plumtree.PlumTree;
import asd.protocols.dissemination.plumtree.ipc.Broadcast;
import asd.protocols.dissemination.plumtree.notifications.DeliverNotification;
import asd.protocols.pubsub.common.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class UnstructuredPubsub extends GenericProtocol {

	private static final Logger logger = LogManager.getLogger(UnstructuredPubsub.class);

	public static final String PROTO_NAME = "UnstructuredPubSub";
	public static final short PROTO_ID = 600;

	private final Set<String> subscribedTopics;

	public UnstructuredPubsub() throws HandlerRegistrationException {
		super(PROTO_NAME, PROTO_ID);

		this.subscribedTopics = new HashSet<>();

		registerRequestHandler(SubscriptionRequest.REQUEST_ID, this::uponSubscriptionRequest);
		registerRequestHandler(PublishRequest.REQUEST_ID, this::uponPublishRequest);
		registerRequestHandler(UnsubscriptionRequest.REQUEST_ID, this::uponUnsubscriptionRequest);

		subscribeNotification(DeliverNotification.NOTIFICATION_ID, this::uponDeliverNotification);
	}

	/*
		TODO:
		- Maybe make a Request so Hyparview can know the topics that the node is currently subscribed to?
			- Use that list to prioritize the active view neighbours of the node (the ones that are subscribed to the same topics)
			- Perhaps make it a Notification so Hyparview is always aware of the topics that the node is subscribed to.
		- Whenever a Message is Delivered we need to verify if the topic is in the current list of subscribed topics, and if so deliver it.
	 */

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
	}

	private void uponDeliverNotification(DeliverNotification notification, short sourceProto) {
		if (subscribedTopics.contains(notification.getTopic())) {
			var deliver = new DeliverNotification(notification.getMsg(), notification.getTopic(), notification.getMsgId(), notification.getSender());
			triggerNotification(deliver);
			logger.info("Delivering message to topic {}", notification.getTopic());
		}
	}

	private void uponSubscriptionRequest(SubscriptionRequest request, short sourceProto) {
		logger.info("Completed subscription to topic: " + request.getTopic());
		this.subscribedTopics.add(request.getTopic());
		sendReply(new SubscriptionReply(request.getTopic()), sourceProto);
	}

	private void uponPublishRequest(PublishRequest request, short sourceProto) {
		logger.info("Completed publication on topic " + request.getTopic() + " and id: " + request.getMsgID());
		var broadcast = new Broadcast(request.getMessage(), request.getTopic(), request.getMsgID(), request.getSender());
		sendRequest(broadcast, PlumTree.PROTOCOL_ID);
	}

	private void uponUnsubscriptionRequest(UnsubscriptionRequest request, short sourceProto) {
		logger.info("Completed unsubscription to topic: " + request.getTopic());
		this.subscribedTopics.remove(request.getTopic());
		sendReply(new UnsubscriptionReply(request.getTopic()), sourceProto);
	}
}
