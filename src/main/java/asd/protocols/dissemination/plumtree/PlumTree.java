package asd.protocols.dissemination.plumtree;

import asd.protocols.dissemination.plumtree.ipc.Broadcast;
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
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.*;

public class PlumTree extends GenericProtocol {

	private static final Logger logger = LogManager.getLogger(PlumTree.class);

	public static final short PROTOCOL_ID = 400;
	public static final String PROTOCOL_NAME = "PlumTree";

	// TODO; missing timer

	private int channelId;

	private Host self;

	private HashProducer hashProducer;

	private Set<Host> eagerPushPeers;
	private Set<Host> lazyPushPeers;
	private Set<Integer> missing; // TODO: might be useless

	/**
	 * Integer represents the ID of a message. Gossip represents a gossip message.
	 */
	private Map<Integer, Gossip> receivedMessages;
	private Map<Integer, Long> missingTimers;

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
		var mid = hashProducer.hash(request.getMsg()) + hashProducer.hash();
		var gossip = new Gossip(mid, request.getMsg());
	}

	/*--------------------------------- Notification Handlers ---------------------------- */

	private void handleNeighbourUp(NeighbourUp notification, short sourceProto) {
		this.eagerPushPeers.add(notification.getNeighbour());
	}

	private void handleNeighbourDown(NeighbourDown notification, short sourceProto) {
		this.eagerPushPeers.remove(notification.getNeighbour());
		this.lazyPushPeers.remove(notification.getNeighbour());
		// TODO; Clear IHave messages from List
	}

	/*--------------------------------- Message Handlers ---------------------------- */

	private void uponGossip(Gossip msg, Host from, short sourceProto, int channelId) {
		// TODO;
		if (receivedMessages.containsKey(msg.getMessageId())) {
			eagerPushPeers.remove(from);
			lazyPushPeers.add(from);
		} else {
			// TODO: Cancel missing timer if the node already received IHave message from that messageId
			// TODO: Trigger Deliver(msg)
			missing.remove(msg.getMessageId());
			var tid = missingTimers.remove(msg.getMessageId());
			if (tid != null) cancelTimer(tid);
		}


	}

	private void uponIHave(IHave msg, Host from, short sourceProto, int channelId) {
		if (!receivedMessages.containsKey(msg.getMessageId())) {
			if (!missingTimers.containsKey(msg.getMessageId())) {
				var tid = setupTimer(new IHaveTimer(msg.getMessageId()), 1000);
				missingTimers.put(msg.getMessageId(), tid);
			}
		}
	}

	private void uponPrune(Prune msg, Host from, short sourceProto, int channelId) {
		// TODO;
	}

	private void uponGraft(Graft msg, Host from, short sourceProto, int channelId) {
		// TODO;
	}

	/*--------------------------------- Timer Handlers ---------------------------- */

	private void handleIHaveTimer(IHaveTimer timer, long timerId) {
		var messageId = timer.getMessageId();

		if (!receivedMessages.containsKey(messageId)) {
			if (missing.contains(messageId)) {
				// TODO: Ask peer that sent the IHave message for the message payload
			}
		}
	}

	/*--------------------------------- Helpers ---------------------------- */

	private void sendEagerPush(Gossip msg) {
		for (var peer : eagerPushPeers) {
			sendMessage(msg, peer);
		}
	}

	private void sendLazyPush(Gossip msg) {
		for (var peer : lazyPushPeers) {
			sendMessage(msg, peer);
		}
	}

}
