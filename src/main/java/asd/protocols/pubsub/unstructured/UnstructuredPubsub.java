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
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public class UnstructuredPubsub extends GenericProtocol {

	private static final Logger logger = LogManager.getLogger(UnstructuredPubsub.class);

	public static final String PROTO_NAME = "Unstructured PubSub";
	public static final short PROTO_ID = 800;

	private final Host self;
	private final Set<String> subscribedTopics;
	private final Set<UUID> seenMessages;

	public UnstructuredPubsub(Host self) throws HandlerRegistrationException {
		super(PROTO_NAME, PROTO_ID);

		this.self = self;
		this.subscribedTopics = new HashSet<>();
		this.seenMessages = new HashSet<>();

		registerRequestHandler(SubscriptionRequest.REQUEST_ID, this::uponSubscriptionRequest);
		registerRequestHandler(PublishRequest.REQUEST_ID, this::uponPublishRequest);
		registerRequestHandler(UnsubscriptionRequest.REQUEST_ID, this::uponUnsubscriptionRequest);
		subscribeNotification(DeliverBroadcast.NOTIFICATION_ID, this::uponDeliverBroadcast);
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {

	}

	private void uponDeliverBroadcast(DeliverBroadcast deliverBroadcast, short sourceProto) {
		var msgId = deliverBroadcast.getMsgId();
		var topic = deliverBroadcast.getTopic();
		var hopCount = deliverBroadcast.getHopCount();

		if (subscribedTopics.contains(deliverBroadcast.getTopic())) {
			if (!seenMessages.contains(deliverBroadcast.getMsgId())) {
				logger.info("Delivering Broadcast to topic: " + deliverBroadcast.getTopic());
				var deliver = new DeliverNotification(
						deliverBroadcast.getTopic(),
						deliverBroadcast.getMsgId(),
						deliverBroadcast.getSender(),
						deliverBroadcast.getMsg());
				triggerNotification(deliver);

				Metrics.pubMessageReceived(deliverBroadcast.getReceivedFrom(), msgId, topic, hopCount, true);
			} else {
				Metrics.pubMessageReceived(deliverBroadcast.getReceivedFrom(), msgId, topic, hopCount, false);
			}
		} else {
			Metrics.pubMessageReceived(deliverBroadcast.getReceivedFrom(), msgId, topic, hopCount, false);
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
		sendRequest(new Broadcast(request.getMessage(), request.getTopic(), request.getMsgID(), request.getSender()),
				PlumTree.PROTOCOL_ID);

		if (subscribedTopics.contains(request.getTopic())) {
			var deliver = new DeliverNotification(
					request.getTopic(),
					request.getMsgID(),
					request.getSender(),
					request.getMessage());
			triggerNotification(deliver);

			Metrics.pubMessageSent(this.self, request.getMsgID(), request.getTopic(), true);
		} else {
			Metrics.pubMessageSent(this.self, request.getMsgID(), request.getTopic(), false);
		}
	}

	private void uponUnsubscriptionRequest(UnsubscriptionRequest request, short sourceProto) {
		logger.info("Completed unsubscription to topic: " + request.getTopic());
		this.subscribedTopics.remove(request.getTopic());
		sendReply(new UnsubscriptionReply(request.getTopic()), sourceProto);

		Metrics.unsubscribedTopic(request.getTopic());
	}
}
