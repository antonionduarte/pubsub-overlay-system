package asd.protocols.dissemination.plumtree;

import asd.protocols.overlay.hyparview.messages.*;
import asd.protocols.overlay.hyparview.timers.ShuffleTimer;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.*;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.Properties;

public class PlumTree extends GenericProtocol {

	public static final short PROTOCOL_ID = 400;

	private int channelId;

	public PlumTree(String protoName, Host self) {
		super(protoName, PROTOCOL_ID);
	}

	@Override
	public void init(Properties properties) throws HandlerRegistrationException, IOException {
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

		/*--------------------- Register Request Handlers ----------------------------- */

		/*-------------------- Register Channel Event ------------------------------- */

		/*-------------------- Register Timer Handler ------------------------------- */

	}


}
