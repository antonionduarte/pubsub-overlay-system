package asd.protocols.overlay.hyparview;

import asd.protocols.overlay.hyparview.messages.*;
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
import java.util.Properties;
import java.util.Set;

public class Hyparview extends GenericProtocol {

	private static final Logger logger = LogManager.getLogger();

	public static final short PROTOCOL_ID = 200;
	public static final String PROTOCOL_NAME = "Hyparview";
	public static final String CONTACT_PROPERTY = "contact";

	public static final int ACTIVE_RANDOM_WALK_LENGTH = 5;
	public static final int PASSIVE_RANDOM_WALK_LENGTH = 5;

	private View passiveView;
	private View activeView;

	private Host self;

	private Set<Host> pending; // The nodes that are pending to be added into the activeView.

	private final int channelId;

	public Hyparview(Properties properties, Host self) throws IOException, HandlerRegistrationException {
		super(PROTOCOL_NAME, PROTOCOL_ID);

		this.self = self;
		this.passiveView = new View(0, self); // TODO: Change size
		this.activeView = new View(0, self); // TODO: Change size

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
		this.registerMessageHandler(this.channelId, ForwardJoin.MESSAGE_ID, this::uponForwardJoin);
		this.registerMessageHandler(this.channelId, Join.MESSAGE_ID, this::uponJoin);
		this.registerMessageHandler(this.channelId, Disconnect.MESSAGE_ID, this::uponDisconnect);
		this.registerMessageHandler(this.channelId, Neighbor.MESSAGE_ID, this::uponNeighbor);
		this.registerMessageHandler(this.channelId, NeighborReply.MESSAGE_ID, this::uponNeighborReply);
		this.registerMessageHandler(this.channelId, JoinReply.MESSAGE_ID, this::uponJoinReply);

		/*--------------------- Register Request Handlers ----------------------------- */

		/*-------------------- Register Channel Event ------------------------------- */
		registerChannelEventHandler(channelId, OutConnectionDown.EVENT_ID, this::uponOutConnectionDown);
		registerChannelEventHandler(channelId, OutConnectionFailed.EVENT_ID, this::uponOutConnectionFailed);
		registerChannelEventHandler(channelId, OutConnectionUp.EVENT_ID, this::uponOutConnectionUp);
		registerChannelEventHandler(channelId, InConnectionUp.EVENT_ID, this::uponInConnectionUp);
		registerChannelEventHandler(channelId, InConnectionDown.EVENT_ID, this::uponInConnectionDown);

		/*-------------------- Register Timer Handler ------------------------------- */

	}

	@Override
	public void init(Properties properties) throws HandlerRegistrationException, IOException {
		try {
			if (properties.containsKey(CONTACT_PROPERTY)) {
				var contact = properties.getProperty(CONTACT_PROPERTY);
				var hostElements = contact.split(":");
				var contactHost = new Host(InetAddress.getByName(hostElements[0]), Short.parseShort(hostElements[1]));
				activeView.addNode(contactHost);
				openConnection(contactHost);
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
				var toSend = new ForwardJoin(from, ACTIVE_RANDOM_WALK_LENGTH);
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
			if (msg.getTimeToLive() == PASSIVE_RANDOM_WALK_LENGTH)
				passiveView.addNode(msg.getNewNode());
			var randomNode = activeView.selectRandomNode();
			while (randomNode == from)
				randomNode = activeView.selectRandomNode();
			openConnection(msg.getNewNode());
			var toSend = new ForwardJoin(msg.getNewNode(), msg.getTimeToLive() - 1);
			sendMessage(toSend, randomNode);
		}
	}

	private void uponDisconnect(Disconnect msg, Host from, short sourceProtocol, int channelId) {
		if (activeView.getView().contains(from)) {
			activeView.removeNode(from);
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

	/*--------------------------------- TCPChannel Events ---------------------------- */

	// An out connection is down.
	private void uponOutConnectionDown(OutConnectionDown event, int channelId) {
		if (activeView.removeNode(event.getNode())) {
			var toPromote = passiveView.dropRandomElement();
			pending.add(toPromote);
			handleRequestNeighbour(toPromote);
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

	/*--------------------------------- Procedures ---------------------------- */

	private void handleDropConnection(Host toDrop) {
		sendMessage(new Disconnect(), toDrop);
		closeConnection(toDrop);
	}

	private void handleActiveAddition(Host toAdd) {
		var dropped = activeView.addNode(toAdd);
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
