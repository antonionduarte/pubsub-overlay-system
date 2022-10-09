package asd.protocols.overlay.kad;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import asd.protocols.overlay.common.ChannelCreatedNotification;
import asd.protocols.overlay.kad.messages.FindNodeRequest;
import asd.protocols.overlay.kad.messages.FindNodeResponse;
import asd.protocols.overlay.kad.messages.Handshake;
import asd.utils.ASDUtils;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.InConnectionDown;
import pt.unl.fct.di.novasys.channel.tcp.events.InConnectionUp;
import pt.unl.fct.di.novasys.channel.tcp.events.OutConnectionDown;
import pt.unl.fct.di.novasys.channel.tcp.events.OutConnectionFailed;
import pt.unl.fct.di.novasys.channel.tcp.events.OutConnectionUp;
import pt.unl.fct.di.novasys.network.data.Host;

public class Kademlia extends GenericProtocol {
	private static final Logger logger = LogManager.getLogger(Kademlia.class);

	public static final short ID = 100;
	public static final String NAME = "Kademlia";

	private final int channel_id;
	private final KadPeer self;
	private final KadRT rt;

	// Hosts with a connection established and handshaked
	private final HashMap<Host, KadID> established_peers;

	public Kademlia(Properties props, Host self) throws IOException, HandlerRegistrationException {
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

		this.established_peers = new HashMap<>();

		this.registerMessageSerializer(this.channel_id, FindNodeRequest.ID, FindNodeRequest.serializer);
		this.registerMessageSerializer(this.channel_id, Handshake.ID, Handshake.serializer);

		this.registerMessageHandler(this.channel_id, FindNodeRequest.ID, this::onFindNodeRequest);
		this.registerMessageHandler(this.channel_id, Handshake.ID, this::onHandshake);

		this.registerChannelEventHandler(this.channel_id, OutConnectionDown.EVENT_ID, this::onOutConnectionDown);
		this.registerChannelEventHandler(this.channel_id, OutConnectionFailed.EVENT_ID, this::onOutConnectionFailed);
		this.registerChannelEventHandler(this.channel_id, OutConnectionUp.EVENT_ID, this::onOutConnectionUp);
		this.registerChannelEventHandler(this.channel_id, InConnectionUp.EVENT_ID, this::onInConnectionUp);
		this.registerChannelEventHandler(this.channel_id, InConnectionDown.EVENT_ID, this::onInConnectionDown);
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		this.triggerNotification(new ChannelCreatedNotification(this.channel_id));

		if (props.containsKey("kad_bootstrap")) {
			var bootstrap_host = ASDUtils.hostFromProp(props.getProperty("kad_bootstrap"));
			logger.info("Connecting to boostrap node at " + bootstrap_host);
			this.openConnection(bootstrap_host);
		}
	}

	private void onPeerConnect(KadPeer peer) {
		if (this.rt.add(peer))
			logger.info("Added " + peer + " to our routing table");
	}

	private void onPeerDisconnect(KadPeer peer) {
		logger.info("Connection to " + peer + " is down");
		this.rt.remove(peer.id);
	}

	/*--------------------------------- Helpers ---------------------------------------- */

	private void sendHandshake(Host other) {
		logger.info("Sending handshake to " + other);
		this.sendMessage(new Handshake(this.self.id), other);
	}

	private boolean isEstablished(Host remote) {
		return this.established_peers.containsKey(remote);
	}

	/*--------------------------------- Message Handlers ---------------------------------------- */
	private void onFindNodeRequest(FindNodeRequest msg, Host from, short source_proto, int channel_id) {
		assert channel_id == this.channel_id;
		assert source_proto == ID;

		if (!this.isEstablished(from))
			throw new IllegalStateException("Received FindNodeRequest from a non-handshaked peer");

		logger.info("Received FindNodeRequest from " + from + " with target " + msg.target);
		var closest = this.rt.closest(msg.target);
		this.sendMessage(new FindNodeResponse(closest), from);
	}

	private void onHandshake(Handshake msg, Host from, short source_proto, int channel_id) {
		assert channel_id == this.channel_id;
		assert source_proto == ID;

		if (this.isEstablished(from))
			throw new IllegalStateException("Received Handshake from a handshaked peer");

		var remote = new KadPeer(msg.id, from);
		logger.info("Received handshake from " + remote);
		this.established_peers.put(from, msg.id);
		this.onPeerConnect(remote);
	}

	/*--------------------------------- Channel Event Handlers ---------------------------------------- */
	private void onOutConnectionDown(OutConnectionDown event, int channel_id) {
		assert channel_id == this.channel_id;

		var remote_id = this.established_peers.get(event.getNode());
		if (remote_id != null) {
			var remote = new KadPeer(remote_id, event.getNode());
			this.onPeerDisconnect(remote);
			this.established_peers.remove(event.getNode());
		}
	}

	private void onOutConnectionFailed(OutConnectionFailed<ProtoMessage> event, int channel_id) {
		assert channel_id == this.channel_id;
		assert event.getPendingMessages().size() == 0;
	}

	private void onOutConnectionUp(OutConnectionUp event, int channel_id) {
		assert channel_id == this.channel_id;
		logger.info("Out connection up");
		this.sendHandshake(event.getNode());
	}

	private void onInConnectionUp(InConnectionUp event, int channel_id) {
		assert channel_id == this.channel_id;
		logger.info("In connection up");
		this.openConnection(event.getNode());
	}

	private void onInConnectionDown(InConnectionDown event, int channel_id) {
		assert channel_id == this.channel_id;
		this.closeConnection(event.getNode());
	}
}
