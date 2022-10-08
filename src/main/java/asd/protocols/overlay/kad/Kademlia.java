package asd.protocols.overlay.kad;

import java.io.IOException;
import java.util.Properties;

import asd.utils.ASDUtils;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.network.data.Host;

public class Kademlia extends GenericProtocol {
	public static final short ID = 100;
	public static final String NAME = "Kademlia";

	private final int channel_id;
	private final KadPeer self;
	private final KadRT rt;

	public Kademlia(Properties props, Host self) throws IOException {
		super(NAME, ID);

		// Create a properties object to setup channel-specific properties. See the
		// channel description for more details.
		Properties channel_props = new Properties();
		channel_props.setProperty(TCPChannel.ADDRESS_KEY, props.getProperty("babel_address")); // The address to bind to
		channel_props.setProperty(TCPChannel.PORT_KEY, props.getProperty("babel_port")); // The port to bind to
		channel_props.setProperty(TCPChannel.HEARTBEAT_INTERVAL_KEY, "1000"); // Heartbeats interval for established
																				// connections
		channel_props.setProperty(TCPChannel.HEARTBEAT_TOLERANCE_KEY, "3000"); // Time passed without heartbeats until
																				// closing a connection
		channel_props.setProperty(TCPChannel.CONNECT_TIMEOUT_KEY, "1000"); // TCP connect timeout
		this.channel_id = createChannel(TCPChannel.NAME, channel_props); // Create the channel with the given properties
		this.self = new KadPeer(KadID.random(), self);
		this.rt = new KadRT(Integer.parseInt(props.getProperty("kad_k", "20")), this.self.id);
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		if (props.containsKey("kad_bootstrap")) {
			var bootstrap_host = ASDUtils.hostFromProp(props.getProperty("kad_bootstrap"));
			openConnection(bootstrap_host);
		}
	}

}
