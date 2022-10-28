package asd.protocols.dissemination.plumtree;

import asd.protocols.dissemination.plumtree.ipc.Broadcast;
import asd.protocols.dissemination.plumtree.ipc.Deliver;
import asd.protocols.dissemination.plumtree.messages.Gossip;
import asd.protocols.dissemination.plumtree.messages.Graft;
import asd.protocols.dissemination.plumtree.messages.IHave;
import asd.protocols.dissemination.plumtree.messages.Prune;
import asd.protocols.dissemination.plumtree.timers.IHaveTimer;
import asd.protocols.overlay.common.notifications.NeighbourDown;
import asd.protocols.overlay.common.notifications.NeighbourUp;
import asd.utils.HashProducer;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.*;

public class PlumTree extends GenericProtocol {

	private static final Logger logger = LogManager.getLogger(PlumTree.class);

	public static final short PROTOCOL_ID = 400;
	public static final String PROTOCOL_NAME = "PlumTree";

	private final int channelId;

	private final Host self;

	private final HashProducer hashProducer;

	private final Set<Host> eagerPushPeers;
	private final Set<Host> lazyPushPeers;

	/**
	 * Integer represents the ID of a message. Gossip represents a gossip message.
	 */
	private final Map<Integer, Gossip> receivedMessages; // messageId -> gossipMessage
	private Map<Integer, Long> missingTimers; // messageId -> timerId
	private Map<Integer, List<Host>> haveMessage; // messageId -> host

	// TODO: This isn't very good yet, I should do LazyPush with some kind of policy instead of just pushing all messages everytime
	public PlumTree(Properties properties, Host self) throws IOException, HandlerRegistrationException {
		super(PROTOCOL_NAME, PROTOCOL_ID);

		var channelMetricsInterval = properties.getProperty("channel_metrics_interval", "10000"); // 10 seconds

		/*---------------------- Channel Configuration ---------------------- */
		Properties channelProps = new Properties();
		channelProps.setProperty(TCPChannel.ADDRESS_KEY, properties.getProperty("address")); // The address to bind to
		channelProps.setProperty(TCPChannel.PORT_KEY, properties.getProperty("port")); // The port to bind to
		channelProps.setProperty(TCPChannel.METRICS_INTERVAL_KEY, channelMetricsInterval); // The interval to receive channel metrics
		channelProps.setProperty(TCPChannel.HEARTBEAT_INTERVAL_KEY, "1000"); // Heartbeats interval for established connections
		channelProps.setProperty(TCPChannel.HEARTBEAT_TOLERANCE_KEY, "3000"); // Time passed without heartbeats until closing a connection
		channelProps.setProperty(TCPChannel.CONNECT_TIMEOUT_KEY, "1000"); // TCP connect timeout
		this.channelId = createChannel(TCPChannel.NAME, channelProps); // Create the channel with the given properties

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

		/*--------------------- Register Request Handlers ----------------------------- */
		registerRequestHandler(Broadcast.ID, this::handleBroadcast);

		/*-------------------- Register Timer Handler ------------------------------- */
		registerTimerHandler(IHaveTimer.TIMER_ID, this::handleIHaveTimer);

		/*-------------------- Subscribe Notification ------------------------------- */
		subscribeNotification(NeighbourUp.NOTIFICATION_ID, this::handleNeighbourUp);
		subscribeNotification(NeighbourDown.NOTIFICATION_ID, this::handleNeighbourDown);

		this.eagerPushPeers = new HashSet<>();
		this.lazyPushPeers = new HashSet<>();
		this.receivedMessages = new HashMap<>();
		this.hashProducer = new HashProducer(self);
		this.self = self;
	}

	@Override
	public void init(Properties properties) throws HandlerRegistrationException, IOException {
	}

	/*--------------------------------- Request Handlers ---------------------------- */

	private void handleBroadcast(Broadcast request, short sourceProto) {
		var messageId = hashProducer.hash(request.getMsg()) + hashProducer.hash();
		var gossip = new Gossip(messageId, request.getMsg());

		receivedMessages.put(messageId, gossip);

		sendPush(gossip, eagerPushPeers, self);
		sendPush(new IHave(messageId), lazyPushPeers, self);
		sendReply(new Deliver(request.getMsg()), sourceProto); // TODO: PubSub layer will need to filter the replies
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
		if (receivedMessages.containsKey(msg.getMessageId())) {
			this.eagerPushPeers.remove(from);
			this.lazyPushPeers.add(from);
			sendMessage(new Prune(), from);
		} else {
			var timerId = missingTimers.remove(msg.getMessageId());
			if (timerId != null) {
				cancelTimer(timerId);
			}

			sendPush(new IHave(msg.getMessageId()), lazyPushPeers, from);
			sendPush(new Gossip(msg.getMessageId(), msg.getMsg()), eagerPushPeers, from);

			this.receivedMessages.put(msg.getMessageId(), msg);
			this.haveMessage.remove(msg.getMessageId());
			this.lazyPushPeers.remove(from);
			this.eagerPushPeers.add(from);

			sendReply(new Deliver(msg.getMsg()), sourceProto); // TODO: PubSub layer will need to filter the replies
		}
	}

	private void uponIHave(IHave msg, Host from, short sourceProto, int channelId) {
		if (!receivedMessages.containsKey(msg.getMessageId())) {
			if (!missingTimers.containsKey(msg.getMessageId())) {
				this.missingTimers.put(msg.getMessageId(), setupTimer(new IHaveTimer(msg.getMessageId()), 1000)); // TODO: Change timeout 1
			}
			this.haveMessage.computeIfAbsent(msg.getMessageId(), k -> {
				return new LinkedList<>();
			}).add(from);
		}
	}

	private void uponPrune(Prune msg, Host from, short sourceProto, int channelId) {
		this.eagerPushPeers.remove(from);
		this.lazyPushPeers.add(from);
	}

	private void uponGraft(Graft msg, Host from, short sourceProto, int channelId) {
		this.lazyPushPeers.remove(from);
		this.eagerPushPeers.add(from);

		if (receivedMessages.containsKey(msg.getMessageId())) {
			sendMessage(receivedMessages.get(msg.getMessageId()), from);
		}
	}

	/*--------------------------------- Timer Handlers ---------------------------- */

	private void handleIHaveTimer(IHaveTimer timer, long timerId) {
		var messageId = timer.getMessageId();

		if (!receivedMessages.containsKey(messageId)) {
			if (missingTimers.containsKey(messageId)) {
				var peer = haveMessage.get(timer.getMessageId()).remove(0);
				var message = new Graft(messageId);
				this.missingTimers.put(messageId, setupTimer(new IHaveTimer(messageId), 1000)); // TODO: Change timeout - timeout 2
				sendMessage(message, peer);
			}
		}
	}

	/*--------------------------------- Helpers ---------------------------- */

	private void sendPush(ProtoMessage msg, Set<Host> peers, Host from) {
		for (var peer : peers) {
			if (!peer.equals(from)) {
				sendMessage(msg, peer);
			}
		}
	}

}
