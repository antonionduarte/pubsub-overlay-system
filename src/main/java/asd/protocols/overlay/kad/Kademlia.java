package asd.protocols.overlay.kad;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import asd.protocols.overlay.common.notifications.ChannelCreatedNotification;
import asd.protocols.overlay.common.notifications.NeighbourDown;
import asd.protocols.overlay.common.notifications.NeighbourUp;
import asd.protocols.overlay.kad.ipc.FindClosest;
import asd.protocols.overlay.kad.ipc.FindClosestReply;
import asd.protocols.overlay.kad.ipc.FindPool;
import asd.protocols.overlay.kad.ipc.FindSwarm;
import asd.protocols.overlay.kad.ipc.FindSwarmReply;
import asd.protocols.overlay.kad.ipc.FindValue;
import asd.protocols.overlay.kad.ipc.FindValueReply;
import asd.protocols.overlay.kad.ipc.JoinPool;
import asd.protocols.overlay.kad.ipc.JoinPoolReply;
import asd.protocols.overlay.kad.ipc.JoinSwarm;
import asd.protocols.overlay.kad.ipc.JoinSwarmReply;
import asd.protocols.overlay.kad.ipc.StoreValue;
import asd.protocols.overlay.kad.messages.FindNodeRequest;
import asd.protocols.overlay.kad.messages.FindNodeResponse;
import asd.protocols.overlay.kad.messages.FindPoolRequest;
import asd.protocols.overlay.kad.messages.FindPoolResponse;
import asd.protocols.overlay.kad.messages.FindSwarmRequest;
import asd.protocols.overlay.kad.messages.FindSwarmResponse;
import asd.protocols.overlay.kad.messages.FindValueRequest;
import asd.protocols.overlay.kad.messages.FindValueResponse;
import asd.protocols.overlay.kad.messages.Handshake;
import asd.protocols.overlay.kad.messages.JoinPoolRequest;
import asd.protocols.overlay.kad.messages.JoinSwarmRequest;
import asd.protocols.overlay.kad.messages.StoreRequest;
import asd.protocols.overlay.kad.query.FindClosestQueryDescriptor;
import asd.protocols.overlay.kad.query.FindPoolQueryDescriptor;
import asd.protocols.overlay.kad.query.FindSwarmQueryDescriptor;
import asd.protocols.overlay.kad.query.FindValueQueryDescriptor;
import asd.protocols.overlay.kad.query.QueryManager;
import asd.protocols.overlay.kad.timers.CheckQueryTimeoutsTimer;
import asd.protocols.overlay.kad.timers.RefreshRTTimer;
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
	private final KadStorage storage;
	private final KadAddrBook addrbook;
	private final QueryManager query_manager;
	private final KadQueryManagerIO query_manager_io;
	private final SwarmTracker swarm_tracker;
	private final KadParams params;
	private final PoolsRT pools_rt;
	private final SwarmTracker pool_tracker;

	private final HashSet<Host> open_connections;
	// Messages that are waiting for a connection to be established
	private final HashMap<Host, List<ProtoMessage>> pending_messages;

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

		var k = Integer.parseInt(props.getProperty("kad_k", "20"));
		var alpha = Integer.parseInt(props.getProperty("kad_alpha", "3"));
		var swarmttl = Duration.parse(props.getProperty("kad_swarm_ttl", "PT10M"));
		var params = new KadParams(k, alpha, swarmttl);

		this.channel_id = createChannel(TCPChannel.NAME, channel_props); // Create the channel with the given properties
		this.self = new KadPeer(KadID.random(), self);
		this.rt = new KadRT(params.k, this.self.id);
		this.storage = new KadStorage();
		this.addrbook = new KadAddrBook();
		var query_manager_io = new KadQueryManagerIO(this.addrbook);
		this.query_manager = new QueryManager(params, this.rt, this.self.id, query_manager_io);
		this.query_manager_io = query_manager_io;
		this.swarm_tracker = new SwarmTracker(params);
		this.params = params;
		this.pools_rt = new PoolsRT(params, this.self.id);
		this.pool_tracker = new SwarmTracker(params);

		this.open_connections = new HashSet<>();
		this.pending_messages = new HashMap<>();

		/*---------------------- Register Message Serializers ---------------------- */
		this.registerMessageSerializer(this.channel_id, FindNodeRequest.ID, FindNodeRequest.serializer);
		this.registerMessageSerializer(this.channel_id, FindNodeResponse.ID, FindNodeResponse.serializer);
		this.registerMessageSerializer(this.channel_id, FindPoolRequest.ID, FindPoolRequest.serializer);
		this.registerMessageSerializer(this.channel_id, FindPoolResponse.ID, FindPoolResponse.serializer);
		this.registerMessageSerializer(this.channel_id, FindSwarmRequest.ID, FindSwarmRequest.serializer);
		this.registerMessageSerializer(this.channel_id, FindSwarmResponse.ID, FindSwarmResponse.serializer);
		this.registerMessageSerializer(this.channel_id, FindValueRequest.ID, FindValueRequest.serializer);
		this.registerMessageSerializer(this.channel_id, FindValueResponse.ID, FindValueResponse.serializer);
		this.registerMessageSerializer(this.channel_id, Handshake.ID, Handshake.serializer);
		this.registerMessageSerializer(this.channel_id, JoinSwarmRequest.ID, JoinSwarmRequest.serializer);
		this.registerMessageSerializer(this.channel_id, StoreRequest.ID, StoreRequest.serializer);

		/*---------------------- Register Message Handlers -------------------------- */
		this.registerMessageHandler(this.channel_id, FindNodeRequest.ID, this::onFindNodeRequest);
		this.registerMessageHandler(this.channel_id, FindNodeResponse.ID, this::onFindNodeResponse);
		this.registerMessageHandler(this.channel_id, FindPoolRequest.ID, this::onFindPoolRequest);
		this.registerMessageHandler(this.channel_id, FindPoolResponse.ID, this::onFindPoolResponse);
		this.registerMessageHandler(this.channel_id, FindSwarmRequest.ID, this::onFindSwarmRequest);
		this.registerMessageHandler(this.channel_id, FindSwarmResponse.ID, this::onFindSwarmResponse);
		this.registerMessageHandler(this.channel_id, FindValueRequest.ID, this::onFindValueRequest);
		this.registerMessageHandler(this.channel_id, FindValueResponse.ID, this::onFindValueResponse);
		this.registerMessageHandler(this.channel_id, Handshake.ID, this::onHandshake);
		this.registerMessageHandler(this.channel_id, JoinPoolRequest.ID, this::onJoinPoolRequest);
		this.registerMessageHandler(this.channel_id, JoinSwarmRequest.ID, this::onJoinSwarmRequest);
		this.registerMessageHandler(this.channel_id, StoreRequest.ID, this::onStoreRequest);

		/*--------------------- Register Request Handlers ----------------------------- */
		this.registerRequestHandler(FindClosest.ID, this::onFindClosest);
		this.registerRequestHandler(FindPool.ID, this::onFindPool);
		this.registerRequestHandler(FindSwarm.ID, this::onFindSwarm);
		this.registerRequestHandler(FindValue.ID, this::onFindValue);
		this.registerRequestHandler(JoinPool.ID, this::onJoinPool);
		this.registerRequestHandler(JoinSwarm.ID, this::onJoinSwarm);
		this.registerRequestHandler(StoreValue.ID, this::onStoreValue);

		/*-------------------- Register Channel Event ------------------------------- */
		this.registerChannelEventHandler(this.channel_id, OutConnectionDown.EVENT_ID, this::onOutConnectionDown);
		this.registerChannelEventHandler(this.channel_id, OutConnectionFailed.EVENT_ID, this::onOutConnectionFailed);
		this.registerChannelEventHandler(this.channel_id, OutConnectionUp.EVENT_ID, this::onOutConnectionUp);
		this.registerChannelEventHandler(this.channel_id, InConnectionUp.EVENT_ID, this::onInConnectionUp);
		this.registerChannelEventHandler(this.channel_id, InConnectionDown.EVENT_ID, this::onInConnectionDown);

		/*-------------------- Register Channel Events ------------------------------- */
		this.registerTimerHandler(CheckQueryTimeoutsTimer.ID, this::onCheckQueryTimeouts);
		this.registerTimerHandler(RefreshRTTimer.ID, this::onRefreshRT);
	}

	@Override
	public void init(Properties props) throws HandlerRegistrationException, IOException {
		this.triggerNotification(new ChannelCreatedNotification(this.channel_id));

		if (props.containsKey("kad_bootstrap")) {
			var bootstrap_host = ASDUtils.hostFromProp(props.getProperty("kad_bootstrap"));
			logger.info("Connecting to boostrap node at " + bootstrap_host);
			this.openConnection(bootstrap_host);
		}

		this.setupPeriodicTimer(new CheckQueryTimeoutsTimer(), 1 * 1000, 1 * 1000);
		this.setupPeriodicTimer(new RefreshRTTimer(), (5 + (long) (Math.random() * 30)) * 1000, 60 * 1000);
	}

	private void onPeerConnect(KadPeer peer) {
		this.open_connections.add(peer.host);
		if (this.rt.add(peer))
			logger.info("Added " + peer + " to our routing table");

		var pending_messages = this.pending_messages.remove(peer.host);
		if (pending_messages != null) {
			for (var msg : pending_messages)
				this.sendMessage(msg, peer.host);
		}

		this.triggerNotification(new NeighbourUp(peer.host));
	}

	private void onPeerDisconnect(KadPeer peer) {
		this.open_connections.remove(peer.host);
		logger.info("Connection to " + peer + " is down");
		this.rt.remove(peer.id);

		this.triggerNotification(new NeighbourDown(peer.host));
	}

	/*--------------------------------- Public Helpers ---------------------------------------- */

	public void printRoutingTable() {
		System.out.println(this.rt);
	}

	public KadID getID() {
		return this.self.id;
	}

	/*--------------------------------- Helpers ---------------------------------------- */

	private void sendHandshake(Host other) {
		logger.info("Sending handshake to " + other);
		this.sendMessage(new Handshake(this.self.id), other);
	}

	private boolean isEstablished(Host remote) {
		return this.open_connections.contains(remote);
	}

	private void flushQueryMessages() {
		while (true) {
			var request = this.query_manager_io.pop();
			if (request == null)
				break;

			var host = this.addrbook.getHostFromID(request.destination);
			if (host == null) {
				this.query_manager.onPeerError(request.context, request.destination);
				continue;
			}

			if (this.isEstablished(host)) {
				this.sendMessage(request.message, host);
			} else {
				this.openConnection(host);
				this.pending_messages.computeIfAbsent(host, k -> new ArrayList<>()).add(request.message);
			}
		}
	}

	private void startQuery(FindClosestQueryDescriptor descriptor) {
		this.query_manager.startQuery(descriptor);
		this.flushQueryMessages();
	}

	private void startQuery(FindValueQueryDescriptor descriptor) {
		this.query_manager.startQuery(descriptor);
		this.flushQueryMessages();
	}

	private void startQuery(FindSwarmQueryDescriptor descriptor) {
		this.query_manager.startQuery(descriptor);
		this.flushQueryMessages();
	}

	private void startQuery(FindPoolQueryDescriptor descriptor) {
		this.query_manager.startQuery(descriptor);
		this.flushQueryMessages();
	}

	/*--------------------------------- Message Handlers ---------------------------------------- */
	private void onFindNodeRequest(FindNodeRequest msg, Host from, short source_proto, int channel_id) {
		assert channel_id == this.channel_id;
		assert source_proto == ID;

		if (!this.isEstablished(from))
			throw new IllegalStateException("Received FindNodeRequest from a non-handshaked peer");

		logger.info("Received FindNodeRequest from " + from + " with target " + msg.target);
		var closest = this.rt.closest(msg.target);
		this.sendMessage(new FindNodeResponse(msg.context, closest), from);
	}

	private void onFindNodeResponse(FindNodeResponse msg, Host from, short source_proto, int channel_id) {
		assert channel_id == this.channel_id;
		assert source_proto == ID;

		if (!this.isEstablished(from))
			throw new IllegalStateException("Received FindNodeResponse from a non-handshaked peer");

		for (var peer : msg.peers)
			this.addrbook.add(peer);

		var peer = this.addrbook.getPeerFromHost(from);
		this.query_manager.onFindNodeResponse(msg.context, peer.id, msg.peers);
		this.flushQueryMessages();
	}

	public void onFindPoolRequest(FindPoolRequest msg, Host from, short source_proto, int channel_id) {
		assert channel_id == this.channel_id;
		assert source_proto == ID;

		if (!this.isEstablished(from))
			throw new IllegalStateException("Received FindPoolRequest from a non-handshaked peer");

		logger.info("Received FindPoolRequest from " + from + " with pool " + msg.pool);
		var closest = this.rt.closest(msg.pool);
		var members = this.addrbook.idsToPeers(this.pool_tracker.getSwarmSample(msg.pool));
		this.sendMessage(new FindPoolResponse(msg.context, closest, members), from);
	}

	public void onFindPoolResponse(FindPoolResponse msg, Host from, short source_proto, int channel_id) {
		assert channel_id == this.channel_id;
		assert source_proto == ID;

		if (!this.isEstablished(from))
			throw new IllegalStateException("Received FindPoolResponse from a non-handshaked peer");

		for (var peer : msg.peers)
			this.addrbook.add(peer);
		for (var peer : msg.members)
			this.addrbook.add(peer);

		logger.info("Received FindPoolResponse from " + from);
		var peer = this.addrbook.getPeerFromHost(from);
		this.query_manager.onFindPoolResponse(channel_id, peer.id, msg.peers, msg.members);
		this.flushQueryMessages();
	}

	private void onFindSwarmRequest(FindSwarmRequest msg, Host from, short source_proto, int channel_id) {
		assert channel_id == this.channel_id;
		assert source_proto == ID;

		if (!this.isEstablished(from))
			throw new IllegalStateException("Received FindSwarmRequest from a non-handshaked peer");

		var closest = this.rt.closest(msg.swarm);
		var members = this.swarm_tracker.getSwarmSample(msg.swarm);
		this.sendMessage(new FindSwarmResponse(msg.context, closest, this.addrbook.idsToPeers(members)), from);
		logger.info("Received FindSwarmRequest from " + from + " with swarm " + msg.swarm +
				" and sending " + members.size() + " members and " + closest.size() + " closest peers");
	}

	private void onFindSwarmResponse(FindSwarmResponse msg, Host from, short source_proto, int channel_id) {
		assert channel_id == this.channel_id;
		assert source_proto == ID;

		if (!this.isEstablished(from))
			throw new IllegalStateException("Received FindSwarmResponse from a non-handshaked peer");

		for (var peer : msg.peers)
			this.addrbook.add(peer);
		for (var peer : msg.members)
			this.addrbook.add(peer);

		var peer = this.addrbook.getPeerFromHost(from);
		this.query_manager.onFindSwarmResponse(msg.context, peer.id, msg.peers, msg.members);
		this.flushQueryMessages();
	}

	private void onFindValueRequest(FindValueRequest msg, Host from, short source_proto, int channel_id) {
		assert channel_id == this.channel_id;
		assert source_proto == ID;

		if (!this.isEstablished(from))
			throw new IllegalStateException("Received FindNodeResponse from a non-handshaked peer");

		var closest = this.rt.closest(msg.key);
		var value = this.storage.get(msg.key);
		var response = new FindValueResponse(msg.context, closest, value);
		this.sendMessage(response, from);
	}

	private void onFindValueResponse(FindValueResponse msg, Host from, short source_proto, int channel_id) {
		assert channel_id == this.channel_id;
		assert source_proto == ID;

		if (!this.isEstablished(from))
			throw new IllegalStateException("Received FindNodeResponse from a non-handshaked peer");

		for (var peer : msg.peers)
			this.addrbook.add(peer);

		var peer = this.addrbook.getPeerFromHost(from);
		this.query_manager.onFindValueResponse(msg.context, peer.id, msg.peers, msg.value);
		this.flushQueryMessages();
	}

	private void onHandshake(Handshake msg, Host from, short source_proto, int channel_id) {
		assert channel_id == this.channel_id;
		assert source_proto == ID;

		if (this.isEstablished(from))
			throw new IllegalStateException("Received Handshake from a handshaked peer");

		var peer = new KadPeer(msg.id, from);
		logger.info("Received handshake from " + peer);
		this.addrbook.add(msg.id, from);
		this.onPeerConnect(peer);
	}

	private void onJoinPoolRequest(JoinPoolRequest msg, Host from, short source_proto, int channel_id) {
		assert channel_id == this.channel_id;
		assert source_proto == ID;

		if (!this.isEstablished(from))
			throw new IllegalStateException("Received JoinPoolRequest from a non-handshaked peer");

		logger.info("Received JoinPoolRequest from " + from + " with pool " + msg.pool);
		var peer = this.addrbook.getPeerFromHost(from);
		this.pool_tracker.add(msg.pool, peer.id);
	}

	private void onJoinSwarmRequest(JoinSwarmRequest msg, Host from, short source_proto, int channel_id) {
		assert channel_id == this.channel_id;
		assert source_proto == ID;

		if (!this.isEstablished(from))
			throw new IllegalStateException("Received JoinSwarmRequest from a non-handshaked peer");

		logger.info("Received JoinSwarmRequest from " + from + " with swarm " + msg.swarm);
		var peer = this.addrbook.getPeerFromHost(from);
		this.swarm_tracker.add(msg.swarm, peer.id);
	}

	private void onStoreRequest(StoreRequest msg, Host from, short source_proto, int channel_id) {
		assert channel_id == this.channel_id;
		assert source_proto == ID;

		if (!this.isEstablished(from))
			throw new IllegalStateException("Received FindNodeResponse from a non-handshaked peer");

		logger.info("Received StoreRequest from " + from + " with key " + msg.key);

		this.storage.store(msg.key, msg.value);
	}

	/*--------------------------------- Request Handlers ---------------------------------------- */
	private void onFindClosest(FindClosest msg, short source_proto) {
		this.startQuery(new FindClosestQueryDescriptor(msg.target, closest -> {
			var closest_peers = this.addrbook.idsToPeers(closest);
			var reply = new FindClosestReply(msg.target, closest_peers);
			this.sendReply(reply, source_proto);
		}));
	}

	private void onFindPool(FindPool msg, short source_proto) {
		// TODO: maybe remove this
	}

	private void onFindSwarm(FindSwarm msg, short source_proto) {
		var k = this.params.k;
		var swarm_id = KadID.ofData(msg.swarm);
		this.startQuery(new FindSwarmQueryDescriptor(swarm_id, msg.sample_size.orElse(k), (__, members) -> {
			var reply = new FindSwarmReply(msg.swarm, this.addrbook.idsToPeers(members));
			this.sendReply(reply, source_proto);
		}));
	}

	private void onFindValue(FindValue msg, short source_proto) {
		this.startQuery(new FindValueQueryDescriptor(msg.key, (closest, value) -> {
			if (closest.isPresent() && value.isPresent()) {
				var host = this.addrbook.getHostFromID(closest.get());
				var request = new StoreRequest(msg.key, value.get());
				this.sendMessage(request, host);
			}
			var reply = new FindValueReply(value);
			this.sendReply(reply, source_proto);
		}));
	}

	private void onJoinPool(JoinPool msg, short source_proto) {
		var pool_id = KadID.ofData(msg.pool);
		if (this.pools_rt.containsPool(pool_id)) {
			this.sendReply(new JoinPoolReply(msg.pool), source_proto);
			return;
		}

		this.pools_rt.createPool(pool_id);
		var pool = this.pools_rt.getPool(pool_id);
		this.startQuery(new FindPoolQueryDescriptor(pool_id, (__, members) -> {
			this.addrbook.idsToPeers(members).forEach(pool::add);
			this.sendReply(new JoinPoolReply(msg.pool), source_proto);
		}));
	}

	private void onJoinSwarm(JoinSwarm msg, short source_proto) {
		var k = this.params.k;
		var swarm_id = KadID.ofData(msg.swarm);
		this.startQuery(new FindSwarmQueryDescriptor(swarm_id, msg.sample_size.orElse(k), (closest, members) -> {
			for (var peer : closest) {
				var host = this.addrbook.getHostFromID(peer);
				if (host == null)
					continue;
				var request = new JoinSwarmRequest(swarm_id);
				this.sendMessage(request, host);
			}

			var reply = new JoinSwarmReply(msg.swarm, this.addrbook.idsToPeers(members));
			this.sendReply(reply, source_proto);
		}));
	}

	private void onStoreValue(StoreValue msg, short source_proto) {
		var descriptor = new FindClosestQueryDescriptor(msg.key, closest -> {
			var request = new StoreRequest(msg.key, msg.value);
			for (var id : closest) {
				var host = this.addrbook.getHostFromID(id);
				if (host == null) {
					logger.warn("Could not find host for peer " + id + " while storing value");
					continue;
				}
				logger.info("Sending StoreRequest to " + host);
				this.sendMessage(request, host);
			}
		});
		this.startQuery(descriptor);
	}

	/*--------------------------------- Channel Event Handlers ---------------------------------------- */
	private void onOutConnectionDown(OutConnectionDown event, int channel_id) {
		assert channel_id == this.channel_id;

		logger.info("Outgoing connection to " + event.getNode() + " is down");
		var peer = this.addrbook.getPeerFromHost(event.getNode());
		if (peer != null) {
			assert peer.host.equals(event.getNode());
			this.onPeerDisconnect(peer);
			this.addrbook.remove(event.getNode());
		}
		this.pending_messages.remove(event.getNode());
	}

	private void onOutConnectionFailed(OutConnectionFailed<ProtoMessage> event, int channel_id) {
		assert channel_id == this.channel_id;
		assert event.getPendingMessages().size() == 0;
		logger.info("Failed to connect to " + event.getNode());
		this.pending_messages.remove(event.getNode());
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
		logger.info("In connection down");
		this.closeConnection(event.getNode());
	}

	/*--------------------------------- Timer Handlers ---------------------------------------- */
	private void onCheckQueryTimeouts(CheckQueryTimeoutsTimer timer, long timer_id) {
		this.query_manager.checkTimeouts();
	}

	private void onRefreshRT(RefreshRTTimer timer, long timer_id) {
		logger.info("Refreshing routing table");
		this.startQuery(new FindClosestQueryDescriptor(this.self.id));
	}
}
