package asd.protocols.overlay.hyparview;

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
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

public class Hyparview extends GenericProtocol {

	private static final Logger logger = LogManager.getLogger();

	public static final short PROTOCOL_ID = 200;
	public static final String PROTOCOL_NAME = "Hyparview";
	public static final String CONTACT_PROPERTY = "contact";

	public final int ARWL;
	public final int PRWL;

	private final View passiveView;
	private final View activeView;

	private final short kActive;
	private final short kPassive;
	private final short shufflePeriod;

	private final short passiveViewCapacity;
	private final short activeViewCapacity;

	private final Host self;

	private final Set<Host> pending; // The nodes that are pending to be added into the activeView.

	private final int channelId;

	public Hyparview(Properties properties, Host self) throws IOException, HandlerRegistrationException {
		super(PROTOCOL_NAME, PROTOCOL_ID);

		/*
		 * TODO: NeighborUp and NeighborDown notifications for other protocols to use. (particularly PlumTree).
		 */

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

		/*---------------------- Protocol Configuration ---------------------- */
		this.kActive = (short) Integer.parseInt(properties.getProperty("k_active", "6"));
		this.kPassive = (short) Integer.parseInt(properties.getProperty("k_passive", "6"));
		this.shufflePeriod = (short) Integer.parseInt(properties.getProperty("shuffle_period", "10000"));
		this.ARWL = Integer.parseInt(properties.getProperty("arwl", "3"));
		this.PRWL = Integer.parseInt(properties.getProperty("prwl", "3"));
		this.passiveViewCapacity = (short) Integer.parseInt(properties.getProperty("passive_view_capacity", "100"));
		this.activeViewCapacity = (short) Integer.parseInt(properties.getProperty("active_view_capacity", "100"));

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
		this.registerMessageHandler(this.channelId, ForwardJoin.MESSAGE_ID, this::uponForwardJoin);
		this.registerMessageHandler(this.channelId, Join.MESSAGE_ID, this::uponJoin);
		this.registerMessageHandler(this.channelId, Disconnect.MESSAGE_ID, this::uponDisconnect);
		this.registerMessageHandler(this.channelId, Neighbor.MESSAGE_ID, this::uponNeighbor);
		this.registerMessageHandler(this.channelId, NeighborReply.MESSAGE_ID, this::uponNeighborReply);
		this.registerMessageHandler(this.channelId, JoinReply.MESSAGE_ID, this::uponJoinReply);
		this.registerMessageHandler(this.channelId, Shuffle.MESSAGE_ID, this::uponShuffle);
		this.registerMessageHandler(this.channelId, ShuffleReply.MESSAGE_ID, this::uponShuffleReply);

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
			if (properties.containsKey(CONTACT_PROPERTY)) {
				var contact = properties.getProperty(CONTACT_PROPERTY);
				var hostElements = contact.split(":");
				var contactHost = new Host(InetAddress.getByName(hostElements[0]), Short.parseShort(hostElements[1]));
				handleActiveAddition(contactHost);
				openConnection(contactHost);
				sendMessage(new Join(), contactHost);
				setupPeriodicTimer(new ShuffleTimer(), shufflePeriod, shufflePeriod);
			}
		} catch (Exception exception) {
			logger.error("Invalid contact on configuration: '" + properties.getProperty(CONTACT_PROPERTY));
			exception.printStackTrace();
			System.exit(-1);
		}
	}

	/*--------------------------------- Messages ---------------------------------------- */

	private void uponJoin(Join msg, Host from, short sourceProtocol, int channelId) {
		handleActiveAddition(from);

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
		if (msg.getTimeToLive() == 0 || activeView.getSize() == 1) {
			handleActiveAddition(msg.getNewNode());
			sendMessage(new JoinReply(), msg.getNewNode());
		} else {
			if (msg.getTimeToLive() == PRWL)
				passiveView.addNode(msg.getNewNode());
			var randomNode = activeView.selectRandomDiffNode(from);
			openConnection(msg.getNewNode());
			var toSend = new ForwardJoin(msg.getNewNode(), msg.getTimeToLive() - 1);
			sendMessage(toSend, randomNode);
		}
	}

		private void uponDisconnect(Disconnect msg, Host from, short sourceProtocol, int channelId) {
		if (activeView.getView().contains(from)) {
			activeView.removeNode(from);
			// TODO: Notification neighbor down
			triggerNotification(new NeighbourDown(from));
			passiveView.addNode(from);
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
			openConnection(from);
			sendMessage(new NeighborReply(false), from);
			closeConnection(from);
		}
	}

	private void uponNeighborReply(NeighborReply msg, Host from, short sourceProtocol, int channelId) {
		if (msg.isNeighbourAccepted())
			handleActiveAddition(from);
		else {
			closeConnection(from);
			pending.remove(from);
			var toPromote = passiveView.dropRandomElement();
			handleRequestNeighbour(toPromote);
		}
	}

	private void uponShuffle(Shuffle msg, Host from, short sourceProtocol, int channelId) {
		var timeToLive = msg.getTimeToLive() - 1;
		if (timeToLive > 0 && activeView.getSize() > 1) {
			var node = activeView.selectRandomDiffNode(from);
			var toSend = new Shuffle(msg.getTimeToLive() - 1, msg.getShuffleSet(), msg.getOriginalNode());
			sendMessage(toSend, node);
		} else {
			var numberNodes = msg.getShuffleSet().size();
			var replyList = passiveView.subsetRandomElements(numberNodes);
			openConnection(msg.getOriginalNode());
			sendMessage(new ShuffleReply(replyList), msg.getOriginalNode());
			closeConnection(msg.getOriginalNode());
			for (Host node : msg.getShuffleSet()) {
				if (!node.equals(self) && !activeView.getView().contains(node))
					passiveView.addNode(node);
			}
		}
	}

	private void uponShuffleReply(ShuffleReply msg, Host from, short sourceProtocol, int channelId) {
		var shuffleSet = msg.getShuffleSet();
		for (Host node : shuffleSet) {
			if (!node.equals(self) && !activeView.getView().contains(node))
				passiveView.addNode(node);
		}
	}

	/*--------------------------------- TCPChannel Events ---------------------------- */

	// An out connection is down.
	private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
		if (activeView.removeNode(event.getNode())) {
			var toPromote = passiveView.dropRandomElement();
			pending.add(toPromote);
			handleRequestNeighbour(toPromote);
			// TODO: Notification neighbor down.
			triggerNotification(new NeighbourDown(event.getNode()));
		}
	}

	// An out connection fails to be established.
	private void uponOutConnectionFailed(OutConnectionFailed<ProtoMessage> event, int channelId) {
		if (pending.contains(event.getNode())) {
			pending.remove(event.getNode());
			var toPromote = passiveView.dropRandomElement();
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
		var shuffleSet = new HashSet<>(subsetPassive);

		shuffleSet.add(self);
		shuffleSet.addAll(subsetActive);
		var toSend = new Shuffle(this.PRWL, shuffleSet, self);
		var shuffleNode = activeView.selectRandomNode();
		sendMessage(toSend, shuffleNode);
	}

	/*--------------------------------- Procedures ---------------------------- */

	private void handleDropConnection(Host toDrop) {
		sendMessage(new Disconnect(), toDrop);
		closeConnection(toDrop);
	}

	private void handleActiveAddition(Host toAdd) {
		var dropped = activeView.addNode(toAdd);
		// TODO: Notification neighbor up.
		triggerNotification(new NeighbourUp(toAdd));
		openConnection(toAdd);
		passiveView.removeNode(toAdd);
		if (dropped != null) {
			passiveView.addNode(dropped);
			handleDropConnection(dropped);
		}
	}

	private void handleRequestNeighbour(Host toRequest) {
		pending.add(toRequest);
		openConnection(toRequest);
		var priority = activeView.getSize() == 0 ? Neighbor.Priority.HIGH : Neighbor.Priority.LOW;
		sendMessage(new Neighbor(priority), toRequest);
	}
}