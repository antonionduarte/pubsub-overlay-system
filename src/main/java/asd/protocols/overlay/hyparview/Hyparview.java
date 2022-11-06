package asd.protocols.overlay.hyparview;

import asd.protocols.overlay.common.notifications.ChannelCreatedNotification;
import asd.protocols.overlay.common.notifications.NeighbourDown;
import asd.protocols.overlay.common.notifications.NeighbourUp;
import asd.protocols.overlay.hyparview.messages.*;
import asd.protocols.overlay.hyparview.timers.ShuffleTimer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.*;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

public class Hyparview extends GenericProtocol {

	private static final Logger logger = LogManager.getLogger();

	public static final short PROTOCOL_ID = 200;
	public static final String PROTOCOL_NAME = "Hyparview";
	public static final String CONTACT_PROPERTY = "hypar_bootstrap";

	public final int ARWL;
	public final int PRWL;

	private final View passiveView;
	private final View activeView;

	private final short kActive;
	private final short kPassive;
	private final short shufflePeriod;

	private final Host self;

	private final Set<Host> pending; // The nodes that are pending to be added into the activeView.
	private Map<UUID, Set<Host>> currentShuffles; // TODO;

	/**
	 * General Ideas uwu:
	 * - Subscribe to a notification from the upper layer, so you always know what topics you have.
	 * - Disseminate the topics you have through PlumTree.
	 * - When you receive a Topic Dessimination message from a peer
	 */

	private final int channelId;

	public Hyparview(Properties properties, Host self) throws IOException, HandlerRegistrationException {
		super(PROTOCOL_NAME, PROTOCOL_ID);

		var channelMetricsInterval = properties.getProperty("channel_metrics_interval", "10000"); // 10 seconds

		/*---------------------- Channel Configuration ---------------------- */
		Properties channelProps = new Properties();
		channelProps.setProperty(TCPChannel.ADDRESS_KEY, properties.getProperty("babel_address")); // The address to bind to
		channelProps.setProperty(TCPChannel.PORT_KEY, properties.getProperty("babel_port")); // The port to bind to
		channelProps.setProperty(TCPChannel.METRICS_INTERVAL_KEY, channelMetricsInterval); // The interval to receive channel metrics
		channelProps.setProperty(TCPChannel.HEARTBEAT_INTERVAL_KEY, "1000"); // Heartbeats interval for established connections
		channelProps.setProperty(TCPChannel.HEARTBEAT_TOLERANCE_KEY, "3000"); // Time passed without heartbeats until closing a connection
		channelProps.setProperty(TCPChannel.CONNECT_TIMEOUT_KEY, "1000"); // TCP connect timeout
		this.channelId = createChannel(TCPChannel.NAME, channelProps); // Create the channel with the given properties

		/*---------------------- Protocol Configuration ---------------------- */
		this.kActive = (short) Integer.parseInt(properties.getProperty("k_active", "2"));
		this.kPassive = (short) Integer.parseInt(properties.getProperty("k_passive", "3"));
		this.shufflePeriod = (short) Integer.parseInt(properties.getProperty("shuffle_period", "2000"));
		this.ARWL = Integer.parseInt(properties.getProperty("arwl", "4"));
		this.PRWL = Integer.parseInt(properties.getProperty("prwl", "2"));
		short passiveViewCapacity = (short) Integer.parseInt(properties.getProperty("passive_view_capacity", "7"));
		short activeViewCapacity = (short) Integer.parseInt(properties.getProperty("active_view_capacity", "4"));

		/*---------------------- Register Message Serializers ---------------------- */
		registerMessageSerializer(this.channelId, Disconnect.MESSAGE_ID, Disconnect.serializer);
		registerMessageSerializer(this.channelId, ForwardJoin.MESSAGE_ID, ForwardJoin.serializer);
		registerMessageSerializer(this.channelId, Join.MESSAGE_ID, Join.serializer);
		registerMessageSerializer(this.channelId, JoinReply.MESSAGE_ID, JoinReply.serializer);
		registerMessageSerializer(this.channelId, Shuffle.MESSAGE_ID, Shuffle.serializer);
		registerMessageSerializer(this.channelId, ShuffleReply.MESSAGE_ID, ShuffleReply.serializer);
		registerMessageSerializer(this.channelId, Neighbor.MESSAGE_ID, Neighbor.serializer);
		registerMessageSerializer(this.channelId, NeighborReply.MESSAGE_ID, NeighborReply.serializer);

		/*---------------------- Register Message Handlers -------------------------- */
		registerMessageHandler(this.channelId, ForwardJoin.MESSAGE_ID, this::uponForwardJoin);
		registerMessageHandler(this.channelId, Join.MESSAGE_ID, this::uponJoin);
		registerMessageHandler(this.channelId, Disconnect.MESSAGE_ID, this::uponDisconnect);
		registerMessageHandler(this.channelId, Neighbor.MESSAGE_ID, this::uponNeighbor);
		registerMessageHandler(this.channelId, NeighborReply.MESSAGE_ID, this::uponNeighborReply);
		registerMessageHandler(this.channelId, JoinReply.MESSAGE_ID, this::uponJoinReply);
		registerMessageHandler(this.channelId, Shuffle.MESSAGE_ID, this::uponShuffle);
		registerMessageHandler(this.channelId, ShuffleReply.MESSAGE_ID, this::uponShuffleReply);

		/*--------------------- Register Request Handlers ----------------------------- */

		/*-------------------- Register Channel Event ------------------------------- */
		registerChannelEventHandler(channelId, OutConnectionDown.EVENT_ID, this::uponOutConnectionDown);
		registerChannelEventHandler(channelId, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);
		registerChannelEventHandler(channelId, OutConnectionUp.EVENT_ID, this::uponOutConnectionUp);
		registerChannelEventHandler(channelId, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
		registerChannelEventHandler(channelId, InConnectionDown.EVENT_ID, this::uponInConnectionDown);

		/*-------------------- Register Timer Handler ------------------------------- */
		registerTimerHandler(ShuffleTimer.TIMER_ID, this::uponShuffleTimer);

		this.self = self;
		this.passiveView = new View(passiveViewCapacity, self);
		this.activeView = new View(activeViewCapacity, self);
		this.pending = new HashSet<>();
	}

	@Override
	public void init(Properties properties) throws HandlerRegistrationException, IOException {
		try {
			this.triggerNotification(new ChannelCreatedNotification(this.channelId));

			if (properties.containsKey(CONTACT_PROPERTY)) {
				var contact = properties.getProperty(CONTACT_PROPERTY);
				var hostElements = contact.split(":");
				var contactHost = new Host(InetAddress.getByName(hostElements[0]), Short.parseShort(hostElements[1]));

				handleActiveAddition(contactHost);
				openConnection(contactHost);
				sendMessage(new Join(), contactHost);
			}

			setupPeriodicTimer(new ShuffleTimer(), shufflePeriod, shufflePeriod);
		} catch (Exception exception) {
			logger.error("Invalid contact on configuration: '" + properties.getProperty(CONTACT_PROPERTY));
			exception.printStackTrace();
			System.exit(-1);
		}
	}

	/*--------------------------------- Messages ---------------------------------------- */

	private void uponJoin(Join msg, Host from, short sourceProtocol, int channelId) {
		handleActiveAddition(from);
		sendMessage(new JoinReply(), from);

		logger.info("Received Join from " + from);

		for (var node : activeView.getView()) {
			if (!node.equals(from)) {
				var toSend = new ForwardJoin(from, ARWL);
				sendMessage(toSend, node);
			}
		}
	}

	private void uponJoinReply(JoinReply msg, Host from, short sourceProtocol, int channelId) {
		handleActiveAddition(from);
	}

	private void uponForwardJoin(ForwardJoin msg, Host from, short sourceProtocol, int channelId) {
		if ((msg.getTimeToLive() - 1) == 0 || activeView.getSize() == 1) {
			handleActiveAddition(msg.getNewNode());
			sendMessage(new JoinReply(), msg.getNewNode());
			logger.info("Received ForwardJoin from " + from + " and sent JoinReply to " + msg.getNewNode());
		} else {
			var randomNode = activeView.selectRandomDiffPeer(from);
			var toSend = new ForwardJoin(msg.getNewNode(), msg.getTimeToLive() - 1);

			if (msg.getTimeToLive() == PRWL) {
				this.passiveView.addPeer(msg.getNewNode());
			}

			sendMessage(toSend, randomNode);
			logger.info("Received ForwardJoin from " + from + " and sent ForwardJoin to " + randomNode);
		}
	}

	private void uponDisconnect(Disconnect msg, Host from, short sourceProtocol, int channelId) {
		if (activeView.getView().contains(from)) {
			triggerNotification(new NeighbourDown(from));
			this.activeView.removePeer(from);
			this.passiveView.addPeer(from);

			var toAdd = this.passiveView.selectRandomDiffPeer(from);
			this.handleRequestNeighbour(toAdd);

			logger.info("Node " + from + " disconnected");
		}
	}

	private void uponNeighbor(Neighbor msg, Host from, short sourceProtocol, int channelId) {
		if (msg.getPriority() == Neighbor.Priority.HIGH) {
			handleActiveAddition(from);
			sendMessage(new NeighborReply(true), from);
		} else if (!activeView.isFull()) {
			handleActiveAddition(from);
			sendMessage(new NeighborReply(true), from);
		} else {
			singleShotMessage(new NeighborReply(false), from);
		}
	}

	private void uponNeighborReply(NeighborReply msg, Host from, short sourceProtocol, int channelId) {
		if (msg.isNeighbourAccepted()) {
			handleActiveAddition(from);
		} else {
			var toPromote = passiveView.selectRandomPeer();

			this.pending.remove(from);
			handleRequestNeighbour(toPromote);
			closeConnection(from);
		}
	}

	private void uponShuffle(Shuffle msg, Host from, short sourceProtocol, int channelId) {
		var timeToLive = msg.getTimeToLive() - 1;

		logger.info("Shuffle received from " + from + " with TTL " + timeToLive);

		if (timeToLive > 0 && activeView.getSize() > 1) {
			var node = activeView.selectRandomDiffPeer(from);
			var toSend = new Shuffle(msg.getTimeToLive() - 1, msg.getShuffleSet(), msg.getOriginalNode());

			logger.info("Shuffle forwarded to " + node + " with TTL " + timeToLive);

			sendMessage(toSend, node);
		} else {
			var numberNodes = msg.getShuffleSet().size();
			var replyList = passiveView.subsetRandomElements(numberNodes);

			logger.info("");
			logger.info("Shuffle reply sent to " + from + " with " + replyList.size() + " nodes");
			logger.info("ShuffleSet: {}", msg.getShuffleSet());
			logger.info("ReplyList: {}\n", replyList);

			singleShotMessage(new ShuffleReply(replyList), msg.getOriginalNode());

			for (Host node : msg.getShuffleSet()) {
				if (!node.equals(self) && !activeView.getView().contains(node)) {
					this.passiveView.addPeer(node);
				}
			}
		}
	}

	private void uponShuffleReply(ShuffleReply msg, Host from, short sourceProtocol, int channelId) {
		var shuffleSet = msg.getShuffleSet();

		logger.info("Shuffle reply received from " + from + " with " + shuffleSet.size() + " nodes");

		for (Host node : shuffleSet) {
			if (!node.equals(self) && !activeView.getView().contains(node)) {
				var dropped = this.passiveView.addPeer(node);
				if (dropped != null)
					logger.info("Dropped " + dropped + " from passive view");
				logger.info("Node " + node + " added to passive view");
			}
		}
	}

	/*--------------------------------- TCPChannel Events ---------------------------- */

	// An out connection is down.
	private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
		if (activeView.removePeer(event.getNode())) {
			var toPromote = passiveView.selectRandomPeer();
			this.pending.add(toPromote);
			handleRequestNeighbour(toPromote);
			triggerNotification(new NeighbourDown(event.getNode()));
		}
		this.passiveView.addPeer(event.getNode());
	}

	// An out connection fails to be established.
	private void uponOutConnectionFailed(OutConnectionFailed<ProtoMessage> event, int channelId) {
		if (pending.contains(event.getNode())) {
			this.pending.remove(event.getNode());
			var toPromote = passiveView.selectRandomPeer();
			handleRequestNeighbour(toPromote);
		}
	}

	private void uponOutConnectionUp(OutConnectionUp event, int channelId) {
		// nothing;
	}

	private void uponInConnectionUp(InConnectionUp event, int channelId) {
		// nothing;
	}

	private void uponInConnectionDown(InConnectionDown event, int channelId) {
		// nothing;
	}

	/*--------------------------------- Timers ---------------------------- */

	private void uponShuffleTimer(ShuffleTimer timer, long timerId) {
		var subsetActive = activeView.subsetRandomElements(kActive);
		var subsetPassive = passiveView.subsetRandomElements(kPassive);
		var shuffleSet = new HashSet<Host>();

		shuffleSet.add(self);
		shuffleSet.addAll(subsetPassive);
		shuffleSet.addAll(subsetActive);

		var toSend = new Shuffle(this.PRWL, shuffleSet, self);
		var shuffleNode = activeView.selectRandomPeer();

		logger.info("");
		logger.info("Shuffle timer triggered. Sending shuffle message to {}", shuffleNode);
		logger.info("ActiveView size: {}", activeView.getSize());
		logger.info("PassiveView size: {}", passiveView.getSize());
		logger.info("ShuffleSet size: {}", shuffleSet.size());

		logger.info("ShuffleSet: {}", shuffleSet);

		logger.info("ActiveView: {}", activeView.getView());
		logger.info("PassiveView: {}\n", passiveView.getView());

		sendMessage(toSend, shuffleNode);
	}

	/*--------------------------------- Procedures ---------------------------- */

	private void handleDropConnection(Host toDrop) {
		sendMessage(new Disconnect(), toDrop);
		closeConnection(toDrop);

		if (activeView.removePeer(toDrop)) {
			triggerNotification(new NeighbourDown(toDrop));
			logger.info("Peer removed from active view: {}", toDrop);
		}

		this.passiveView.addPeer(toDrop);
	}

	private void singleShotMessage(ProtoMessage msg, Host host) {
		openConnection(host);
		sendMessage(msg, host);

		if (!activeView.getView().contains(host)) {
			closeConnection(host);
		}
	}

	private void handleActiveAddition(Host toAdd) {;
		var dropped = activeView.addPeer(toAdd);

		this.passiveView.removePeer(toAdd);
		this.pending.remove(toAdd);

		triggerNotification(new NeighbourUp(toAdd));
		openConnection(toAdd);

		if (dropped != null) {
			this.passiveView.addPeer(dropped);
			handleDropConnection(dropped);

			logger.info("Peer DROPPED DROPPED DROPPED. Dropped: {}", dropped);
			logger.info("Peer added to passive view: {}", dropped);
		}

		logger.info("Peer added to active view: {}", toAdd.toString());
	}

	private void handleRequestNeighbour(Host toRequest) {
		var priority = activeView.getSize() == 0 ? Neighbor.Priority.HIGH : Neighbor.Priority.LOW;
		this.pending.add(toRequest);

		openConnection(toRequest);
		sendMessage(new Neighbor(priority), toRequest);
	}
}