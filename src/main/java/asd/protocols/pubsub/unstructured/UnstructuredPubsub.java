package asd.protocols.pubsub.unstructured;

import asd.metrics.Metrics;
import asd.protocols.dissemination.plumtree.PlumTree;
import asd.protocols.dissemination.plumtree.ipc.Broadcast;
import asd.protocols.dissemination.plumtree.notifications.DeliverBroadcast;
import asd.protocols.pubsub.common.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public class UnstructuredPubsub extends GenericProtocol {

	private static final Logger logger = LogManager.getLogger(UnstructuredPubsub.class);

	public static final String PROTO_NAME = "Unstructured PubSub";
	public static final short PROTO_ID = 800;

	private final Set<String> subscribedTopics;

	private final Set<UUID> seenMessages;

	public UnstructuredPubsub() throws HandlerRegistrationException {
		super(PROTO_NAME, PROTO_ID);

		this.subscribedTopics = new HashSet<>();
		this.seenMessages = new HashSet<>();

		registerRequestHandler(SubscriptionRequest.REQUEST_ID, this::uponSubscriptionRequest);
		registerRequestHandler(PublishRequest.REQUEST_ID, this::uponPublishRequest);
		registerRequestHandler(UnsubscriptionRequest.REQUEST_ID, this::uponUnsubscriptionRequest);
		subscribeNotification(DeliverBroadcast.NOTIFICATION_ID, this::uponDeliverBroadcast);
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

	private void uponDeliverBroadcast(DeliverBroadcast deliverBroadcast, short sourceProto) {
		if (subscribedTopics.contains(deliverBroadcast.getTopic())) {
			var msgId = deliverBroadcast.getMsgId();
			var topic = deliverBroadcast.getTopic();
			var hopCount = deliverBroadcast.getHopCount();

			if (!seenMessages.contains(deliverBroadcast.getMsgId())) {
				logger.info("Delivering Broadcast to topic: " + deliverBroadcast.getTopic());
				var deliver = new DeliverNotification(
						deliverBroadcast.getTopic(),
						deliverBroadcast.getMsgId(),
						deliverBroadcast.getSender(),
						deliverBroadcast.getMsg());
				triggerNotification(deliver);

				Metrics.pubMessageReceived(msgId, topic, hopCount, true);
			} else {
				Metrics.pubMessageReceived(msgId, topic, hopCount, false);
			}
		}
	}

	private void uponSubscriptionRequest(SubscriptionRequest request, short sourceProto) {
		logger.info("Completed subscription to topic: " + request.getTopic());
		this.subscribedTopics.add(request.getTopic());
		sendReply(new SubscriptionReply(request.getTopic()), sourceProto);

		Metrics.subscribedTopic(request.getTopic());
	}

	private void uponPublishRequest(PublishRequest request, short sourceProto) {
		logger.info("Completed publication on topic " + request.getTopic() + " and id: " + request.getMsgID());
		sendRequest(new Broadcast(request.getMessage(), request.getTopic(), request.getMsgID(), request.getSender()), PlumTree.PROTOCOL_ID);
	}

	private void uponUnsubscriptionRequest(UnsubscriptionRequest request, short sourceProto) {
		logger.info("Completed unsubscription to topic: " + request.getTopic());
		this.subscribedTopics.remove(request.getTopic());
		sendReply(new UnsubscriptionReply(request.getTopic()), sourceProto);

		Metrics.unsubscribedTopic(request.getTopic());
	}
}
