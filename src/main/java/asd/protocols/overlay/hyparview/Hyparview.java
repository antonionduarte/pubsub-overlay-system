package asd.protocols.overlay.hyparview;

import asd.protocols.overlay.hyparview.messages.Disconnect;
import asd.protocols.overlay.hyparview.messages.ForwardJoin;
import asd.protocols.overlay.hyparview.messages.Join;
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

public class Hyparview extends GenericProtocol  {

	private static final Logger logger = LogManager.getLogger();

	public static final short PROTOCOL_ID = 200;
	public static final String PROTOCOL_NAME = "Hyparview";
	public static final String CONTACT_PROPERTY = "contact";

	public static final int ACTIVE_RANDOM_WALK_LENGTH = 5;
	public static final int PASSIVE_RANDOM_WALK_LENGTH = 5;

	private final Host self;

	private View passiveView;
	private View activeView;

	private final int channelId;

	public Hyparview(Properties properties, Host self) throws IOException, HandlerRegistrationException {
		super(PROTOCOL_NAME, PROTOCOL_ID);

		this.self = self;
		this.pending = new HashSet<>();
		this.passiveView = new View(0); // TODO: Change size
		this.activeView = new View(0); // TODO: Change size

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
				this.activeView.addNode(contactHost);
				openConnection(contactHost);
			}
		} catch (Exception exception) {
			logger.error("Invalid contact on configuration: '" + properties.getProperty(CONTACT_PROPERTY));
			exception.printStackTrace();
			System.exit(-1);
		}
	}

	/*--------------------------------- Messages ---------------------------------------- */

	public void uponJoin(Join msg, Host from, short sourceProtocol, int channelId) {
		this.activeView.addNode(from);
		openConnection(from);

		for (var node : activeView.getView()) {
			if (!node.equals(from)) {
				// Send Forward Join.
			}
		}
	}

	public void uponForwardJoin(ForwardJoin msg, Host from, short sourceProtocol, int channelId) {
		if (msg.getTimeToLive() == 0 || activeView.getSize() == 0)
			activeView.addNode(msg.getNewNode());
		else {
			if (msg.getTimeToLive() == PASSIVE_RANDOM_WALK_LENGTH)
				this.passiveView.addNode(msg.getNewNode());
		}

		for (var node : activeView.getView()) {
			if (!node.equals(from)) {
				var toSend = new ForwardJoin(msg.getNewNode(), msg.getTimeToLive() - 1);
				sendMessage(toSend, node);
			}
		}
	}

	public void uponDisconnect(Disconnect msg, Host from, short sourceProtocol, int channelId) {
		
	}

	/*--------------------------------- TCPChannel Events ---------------------------- */

	public void uponOutConnectionDown(OutConnectionDown event, int channelId) {

	}

	public void uponOutConnectionFailed(OutConnectionFailed<ProtoMessage> event, int channelId) {

	}

	public void uponOutConnectionUp(OutConnectionUp event, int channelId) {

	}

	public void uponInConnectionUp(InConnectionUp event, int channelId) {

	}

	public void uponInConnectionDown(InConnectionDown event, int channelId) {

	}

}
