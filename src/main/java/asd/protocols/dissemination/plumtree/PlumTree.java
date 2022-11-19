package asd.protocols.dissemination.plumtree;

import asd.protocols.dissemination.plumtree.ipc.Broadcast;
import asd.protocols.dissemination.plumtree.messages.Gossip;
import asd.protocols.dissemination.plumtree.messages.Graft;
import asd.protocols.dissemination.plumtree.messages.IHave;
import asd.protocols.dissemination.plumtree.messages.Prune;
import asd.protocols.dissemination.plumtree.notifications.DeliverBroadcast;
import asd.protocols.dissemination.plumtree.timers.IHaveTimer;
import asd.protocols.overlay.common.notifications.ChannelCreatedNotification;
import asd.protocols.overlay.common.notifications.NeighbourDown;
import asd.protocols.overlay.common.notifications.NeighbourUp;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.*;

public class PlumTree extends GenericProtocol {

	public static final short PROTOCOL_ID = 400;
	public static final String PROTOCOL_NAME = "PlumTree";
	private static final Logger logger = LogManager.getLogger();
	private final Host self;
	private final long missingTimeoutMs;
	private final long missingTimeoutSecondMs;
	private final Set<Host> eagerPushPeers;
	private final Set<Host> lazyPushPeers;
	/**
	 * Integer represents the ID of a message. Gossip represents a gossip message.
	 */
	private final Map<UUID, Gossip> receivedMessages; // messageId -> gossipMessage
	private final Map<UUID, Long> missingTimers; // messageId -> timerId
	private final Map<UUID, List<Host>> haveMessage; // messageId -> host
	private int channelId;

	public PlumTree(Properties properties, Host self) throws HandlerRegistrationException {
		super(PROTOCOL_NAME, PROTOCOL_ID);

		logger.info("PlumTree protocol created");

		/*---------------------- Protocol Configuration ---------------------- */
		this.missingTimeoutMs = (long) (Double.parseDouble(properties.getProperty("plumtree_missing_timeout"))
				* 1000.0);
		this.missingTimeoutSecondMs = (long) (Double
				.parseDouble(properties.getProperty("plumtree_missing_timeout_second")) * 1000.0);

		/*--------------------- Register Request Handlers ----------------------------- */
		registerRequestHandler(Broadcast.ID, this::handleBroadcast);

		/*-------------------- Register Timer Handler ------------------------------- */
		registerTimerHandler(IHaveTimer.TIMER_ID, this::handleIHaveTimer);

		/*-------------------- Subscribe Notification ------------------------------- */
		subscribeNotification(NeighbourUp.NOTIFICATION_ID, this::handleNeighbourUp);
		subscribeNotification(NeighbourDown.NOTIFICATION_ID, this::handleNeighbourDown);
		subscribeNotification(ChannelCreatedNotification.ID, this::onChannelCreated);

		this.eagerPushPeers = new HashSet<>();
		this.lazyPushPeers = new HashSet<>();
		this.receivedMessages = new HashMap<>();
		this.self = self;
		this.haveMessage = new HashMap<>();
		this.missingTimers = new HashMap<>();
	}

	private void onChannelCreated(ChannelCreatedNotification notification, short protoID) {
		this.channelId = notification.channel_id;
		registerSharedChannel(channelId);
		logger.info("Channel created with id {}", channelId);

		try {
			/*---------------------- Register Message Serializers ---------------------- */
			registerMessageSerializer(channelId, Gossip.MSG_ID, Gossip.serializer);
			registerMessageSerializer(channelId, IHave.MSG_ID, IHave.serializer);
			registerMessageSerializer(channelId, Graft.MSG_ID, Graft.serializer);
			registerMessageSerializer(channelId, Prune.MSG_ID, Prune.serializer);

			/*---------------------- Register Message Handlers -------------------------- */
			registerMessageHandler(channelId, Gossip.MSG_ID, this::uponGossip);
			registerMessageHandler(channelId, Prune.MSG_ID, this::uponPrune);
			registerMessageHandler(channelId, Graft.MSG_ID, this::uponGraft);
			registerMessageHandler(channelId, IHave.MSG_ID, this::uponIHave);
		} catch (HandlerRegistrationException exception) {
			throw new RuntimeException(exception);
		}
	}

	@Override
	public void init(Properties properties) throws HandlerRegistrationException, IOException {
		logger.info("PlumTree protocol initialized");
	}

	/*--------------------------------- Request Handlers ---------------------------- */

	private void handleBroadcast(Broadcast request, short sourceProto) {
		var messageId = request.getMsgId();
		var gossip = new Gossip(request.getMsg(), request.getTopic(), request.getMsgId(), request.getSender(), 0);

		receivedMessages.put(request.getMsgId(), gossip);

		logger.info("Received broadcast request from {} with message id {}", request.getSender(), request.getMsgId());

		sendPush(gossip, eagerPushPeers, self);
		sendPush(new IHave(messageId), lazyPushPeers, self);
	}

	/*--------------------------------- Notification Handlers ---------------------------- */

	private void handleNeighbourUp(NeighbourUp notification, short sourceProto) {
		this.eagerPushPeers.add(notification.getNeighbour());
	}

	private void handleNeighbourDown(NeighbourDown notification, short sourceProto) {
		this.eagerPushPeers.remove(notification.getNeighbour());
		this.lazyPushPeers.remove(notification.getNeighbour());

		for (var entry : haveMessage.entrySet()) {
			entry.getValue().remove(notification.getNeighbour());
		}
	}

	/*--------------------------------- Message Handlers ---------------------------- */

	private void uponGossip(Gossip msg, Host from, short sourceProto, int channelId) {
		var deliver = new DeliverBroadcast(
				msg.getMsg(),
				msg.getTopic(),
				msg.getMsgId(),
				msg.getSender(),
				msg.getHopCount(),
				from);

		triggerNotification(deliver);

		if (receivedMessages.containsKey(msg.getMsgId())) {
			this.eagerPushPeers.remove(from);
			this.lazyPushPeers.add(from);

			sendMessage(new Prune(), from);
		} else {
			var timerId = missingTimers.remove(msg.getMsgId());
			var gossip = new Gossip(msg.getMsg(), msg.getTopic(), msg.getMsgId(), msg.getSender(),
					msg.getHopCount() + 1);

			if (timerId != null) {
				cancelTimer(timerId);
			}

			sendPush(new IHave(msg.getMsgId()), lazyPushPeers, from);
			sendPush(gossip, eagerPushPeers, from);

			this.receivedMessages.put(msg.getMsgId(), msg);
			this.haveMessage.remove(msg.getMsgId());
			this.lazyPushPeers.remove(from);
			this.eagerPushPeers.add(from);
		}

		logger.info("Received gossip message with topic: {}", msg.getTopic());
		logger.info("Lazy push peers size {}", lazyPushPeers.size());
		logger.info("Eager push peers size {}", eagerPushPeers.size());
	}

	private void uponIHave(IHave msg, Host from, short sourceProto, int channelId) {
		logger.info("Received IHave message with messageId: {}", msg.getMsgId());

		if (!receivedMessages.containsKey(msg.getMsgId())) {
			if (!missingTimers.containsKey(msg.getMsgId())) {
				this.missingTimers.put(msg.getMsgId(), setupTimer(new IHaveTimer(msg.getMsgId()), missingTimeoutMs));
			}
			this.haveMessage.computeIfAbsent(msg.getMsgId(), k -> {
				return new LinkedList<>();
			}).add(from);
		}
	}

	private void uponPrune(Prune msg, Host from, short sourceProto, int channelId) {
		logger.info("Received Prune message from {}", from);

		this.eagerPushPeers.remove(from);
		this.lazyPushPeers.add(from);
	}

	private void uponGraft(Graft msg, Host from, short sourceProto, int channelId) {
		this.lazyPushPeers.remove(from);
		this.eagerPushPeers.add(from);

		logger.info("Received graft message with ID: {}", msg.getMsgId());

		if (receivedMessages.containsKey(msg.getMsgId())) {
			sendMessage(receivedMessages.get(msg.getMsgId()), from);
		}
	}

	/*--------------------------------- Timer Handlers ---------------------------- */

	private void handleIHaveTimer(IHaveTimer timer, long timerId) {
		if (missingTimers.containsKey(timer.getMsgId())) {
			this.missingTimers.remove(timer.getMsgId());
			var messageId = timer.getMsgId();

			if (!receivedMessages.containsKey(messageId)) {
				if (missingTimers.containsKey(messageId)) {
					var peer = haveMessage.get(timer.getMsgId()).remove(0);
					var message = new Graft(messageId);
					this.missingTimers.put(messageId, setupTimer(new IHaveTimer(messageId), missingTimeoutSecondMs));
					sendMessage(message, peer);
				}
			}
		}
	}

	/*--------------------------------- Helpers ---------------------------- */

	private void sendPush(ProtoMessage msg, Set<Host> peers, Host from) {
		for (var peer : peers) {
			if (!peer.equals(from)) {
				sendMessage(msg, peer);

				if (eagerPushPeers.contains(peer)) {
					logger.info("Sent eager push message to {}", peer);
				} else {
					logger.info("Sent lazy push message to {}", peer);
				}
			}
		}
	}

}
