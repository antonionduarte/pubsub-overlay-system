package asd.protocols.overlay.kad;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import asd.metrics.Metrics;
import asd.metrics.Profiling;
import asd.protocols.overlay.common.notifications.ChannelCreatedNotification;
import asd.protocols.overlay.common.notifications.NeighbourDown;
import asd.protocols.overlay.common.notifications.NeighbourUp;
import asd.protocols.overlay.kad.ipc.Broadcast;
import asd.protocols.overlay.kad.ipc.FindClosest;
import asd.protocols.overlay.kad.ipc.FindClosestReply;
import asd.protocols.overlay.kad.ipc.FindPool;
import asd.protocols.overlay.kad.ipc.FindPoolReply;
import asd.protocols.overlay.kad.ipc.FindSwarm;
import asd.protocols.overlay.kad.ipc.FindSwarmReply;
import asd.protocols.overlay.kad.ipc.FindValue;
import asd.protocols.overlay.kad.ipc.FindValueReply;
import asd.protocols.overlay.kad.ipc.JoinPool;
import asd.protocols.overlay.kad.ipc.JoinPoolReply;
import asd.protocols.overlay.kad.ipc.JoinSwarm;
import asd.protocols.overlay.kad.ipc.JoinSwarmReply;
import asd.protocols.overlay.kad.ipc.StoreValue;
import asd.protocols.overlay.kad.messages.BroadcastHave;
import asd.protocols.overlay.kad.messages.BroadcastMessage;
import asd.protocols.overlay.kad.messages.BroadcastWant;
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
import asd.protocols.overlay.kad.notifications.BroadcastReceived;
import asd.protocols.overlay.kad.query.FindClosestQueryDescriptor;
import asd.protocols.overlay.kad.query.FindPoolQueryDescriptor;
import asd.protocols.overlay.kad.query.FindSwarmQueryDescriptor;
import asd.protocols.overlay.kad.query.FindValueQueryDescriptor;
import asd.protocols.overlay.kad.query.QueryManager;
import asd.protocols.overlay.kad.query.QueryManagerIO;
import asd.protocols.overlay.kad.timers.CheckQueryTimeoutsTimer;
import asd.protocols.overlay.kad.timers.RefreshRTTimer;
import asd.utils.ASDUtils;
import asd.utils.Callback;
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

public class Kademlia extends GenericProtocol implements QueryManagerIO {
	private static final Logger logger = LogManager.getLogger(Kademlia.class);

	public static final short ID = 100;
	public static final String NAME = "Kademlia";

	private final int channel_id;
	private final KadPeer self;
	private final KadRT rt;
	private final KadStorage storage;
	private final KadAddrBook addrbook;
	private final QueryManager query_manager;
	private final SwarmTracker swarm_tracker;
	private final KadParams params;
	private final PoolsRT pools_rt;
	private final SwarmTracker pool_tracker;
	private final ConnectionFlags conn_flags;
	private final MessageCache msg_cache;
	private final MessageTracker msg_tracker;
	private final HashMap<KadID, String> pool_id_to_string;

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
		this.swarm_tracker = new SwarmTracker(params);
		this.params = params;
		this.pools_rt = new PoolsRT(params, this.self.id);
		this.pool_tracker = new SwarmTracker(params);
		this.conn_flags = new ConnectionFlags();
		this.query_manager = new QueryManager(params, this.rt, this.pools_rt, this.self.id, this);
		this.msg_cache = new MessageCache();
		this.msg_tracker = new MessageTracker();
		this.pool_id_to_string = new HashMap<>();

		/*---------------------- Register Message Serializers ---------------------- */
		this.registerMessageSerializer(this.channel_id, BroadcastHave.ID, BroadcastHave.serializer);
		this.registerMessageSerializer(this.channel_id, BroadcastMessage.ID, BroadcastMessage.serializer);
		this.registerMessageSerializer(this.channel_id, BroadcastWant.ID, BroadcastWant.serializer);
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
		this.registerMessageSerializer(this.channel_id, JoinPoolRequest.ID, JoinPoolRequest.serializer);
		this.registerMessageSerializer(this.channel_id, StoreRequest.ID, StoreRequest.serializer);

		/*---------------------- Register Message Handlers -------------------------- */
		this.registerMessageHandler(this.channel_id, BroadcastHave.ID, this::onBroadcastHave);
		this.registerMessageHandler(this.channel_id, BroadcastMessage.ID, this::onBroadcastMessage);
		this.registerMessageHandler(this.channel_id, BroadcastWant.ID, this::onBroadcastWant);
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
		this.registerRequestHandler(Broadcast.ID, this::onBroadcast);
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
			var bootstrap_host = ASDUtils.hostsFromProp(props.getProperty("kad_bootstrap")).get(0);
			logger.debug("Connecting to boostrap node at " + bootstrap_host);
			System.out.println("Connecting to boostrap node at " + bootstrap_host);
			this.kadConnect(bootstrap_host);
		}

		this.setupPeriodicTimer(new CheckQueryTimeoutsTimer(), 1 * 1000, 1 * 1000);
		this.setupPeriodicTimer(new RefreshRTTimer(), (5 + (long) (Math.random() * 30)) * 1000, 60 * 1000);
	}

	/*--------------------------------- Public Helpers ---------------------------------------- */

	public void printRoutingTable() {
		System.out.println(this.rt);
	}

	public void printPoolRoutingTable(String pool) {
		var id = KadID.ofData(pool);
		var rt = this.pools_rt.getPool(id);
		if (rt == null) {
			System.out.println("No routing table for pool " + pool);
		} else {
			System.out.println(rt);
		}
	}

	public KadID getID() {
		return this.self.id;
	}

	/*--------------------------------- Helpers ---------------------------------------- */

	private void kadConnect(Host host) {
		if (this.conn_flags.test(host, ConnectionFlags.IS_ATTEMPTING_CONNECT | ConnectionFlags.SENT_HANDSHAKE)) {
			return;
		}
		this.conn_flags.set(host, ConnectionFlags.IS_ATTEMPTING_CONNECT | ConnectionFlags.SENT_HANDSHAKE);
		this.openConnection(host);
		this.loggedSendMessage(new Handshake(this.self.id), host);
	}

	private void kadSendMessage(ProtoMessage msg, Host host) {
		if (!this.conn_flags.test(host, ConnectionFlags.IS_ATTEMPTING_CONNECT | ConnectionFlags.SENT_HANDSHAKE))
			this.kadConnect(host);
		this.loggedSendMessage(msg, host);
	}

	// Called when a peer has established a connection to us
	private void onPeerConnect(KadPeer peer) {
		if (peer.host.getPort() == 5000)
			System.out.println("Peer connected: " + peer);
		if (this.rt.add(peer)) {
			System.out.println("Added peer to routing table: " + peer);
			logger.debug("Added " + peer + " to our routing table");
		}
		this.triggerNotification(new NeighbourUp(peer.host));
		this.kadConnect(peer.host);
	}

	// Call when a peer has closed a connection to us
	private void onPeerDisconnect(KadPeer peer) {
		logger.debug("Connection to " + peer + " is down");
		this.rt.remove(peer.id);
		this.pools_rt.removePeer(peer.id);
		this.triggerNotification(new NeighbourDown(peer.host));
	}

	private void ensureConnectionInEstablished(ProtoMessage msg, Host from, short source_proto,
			int channel_id) {
		assert channel_id == this.channel_id;
		assert source_proto == ID;

		if (!this.conn_flags.test(from, ConnectionFlags.RECEIVED_HANDSHAKE))
			throw new IllegalStateException("Received message from " + from + " but connection is not established: "
					+ msg.getClass().getName());
	}

	private void loggedSendMessage(ProtoMessage msg, Host destination) {
		Metrics.kadSendMessage(destination, msg.getClass().getTypeName());
		this.sendMessage(msg, destination);
	}

	private void startQuery(FindClosestQueryDescriptor descriptor) {
		this.query_manager.startQuery(descriptor);
	}

	private void startQuery(FindValueQueryDescriptor descriptor) {
		this.query_manager.startQuery(descriptor);
	}

	private void startQuery(FindSwarmQueryDescriptor descriptor) {
		this.query_manager.startQuery(descriptor);
	}

	private void startQuery(FindPoolQueryDescriptor descriptor) {
		this.query_manager.startQuery(descriptor);
	}

	private KadRT getRtFromPool(Optional<KadID> pool) {
		if (pool.isEmpty())
			return this.rt;
		return this.pools_rt.getPool(pool.get());
	}

	private void refreshPool(KadID pool_id) {
		this.refreshPool(pool_id, null);
	}

	private void refreshPool(KadID pool_id, Callback callback) {
		var pool = this.pools_rt.getPool(pool_id);
		if (pool == null)
			return;

		if (pool.isEmpty()) {
			this.startQuery(new FindPoolQueryDescriptor(pool_id, (closest, members) -> {
				this.addrbook.idsToPeers(members).forEach(pool::add);
				for (var peer_id : members) {
					var peer = this.addrbook.getPeerFromID(peer_id);
					if (peer == null)
						continue;
					pool.add(peer);
				}
				System.out.println("Refreshed empty pool with " + members.size() + " peers");
				if (callback != null)
					callback.execute();
			}));
		} else {
			this.startQuery(new FindClosestQueryDescriptor(this.self.id, pool_id, (members) -> {
				for (var id : members) {
					var peer = this.addrbook.getPeerFromID(id);
					if (peer == null)
						continue;
					pool.add(peer);
				}
				if (callback != null)
					callback.execute();
			}));
		}
	}

	/*--------------------------------- Message Handlers ---------------------------------------- */
	private void onBroadcastHave(BroadcastHave msg, Host from, short source_proto, int channel_id) {
		try (var __ = Profiling.span("onBroadcastHave")) {
			Metrics.kadReceiveMessage(from, "BroadcastHave");
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);

			if (!this.pools_rt.containsPool(msg.pool))
				return;
			if (this.msg_cache.contains(msg.uuid))
				return;

			var peer = this.addrbook.getPeerFromHost(from);
			if (!this.msg_tracker.isTracking(msg.uuid))
				this.msg_tracker.startTracking(msg.uuid);

			this.msg_tracker.addProvider(msg.uuid, peer.id);
			if (!this.msg_tracker.isRequesting(msg.uuid)) {
				this.msg_tracker.beginRequest(msg.uuid, peer.id);
				this.kadSendMessage(new BroadcastWant(msg.pool, msg.uuid), from);
			}
		}
	}

	private void onBroadcastMessage(BroadcastMessage msg, Host from, short source_proto, int channel_id) {
		try (var __ = Profiling.span("onBroadcastMessage")) {
			Metrics.kadReceiveMessage(from, "BroadcastRequest");
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);

			// Note: using msg.depth as hopCount is wrong

			if (!this.pools_rt.containsPool(msg.pool)) {
				Metrics.pubMessageReceived(msg.uuid, "unknown", 0, false);
				return;
			}
			if (this.msg_cache.contains(msg.uuid)) {
				Metrics.pubMessageReceived(msg.uuid, this.pool_id_to_string.get(msg.pool), 0, false);
				return;
			}

			var pool = this.pools_rt.getPool(msg.pool);
			var peers = pool.getBroadcastSample(msg.depth, 10);
			for (var peer : peers)
				this.kadSendMessage(new BroadcastHave(msg.pool, msg.uuid), peer.host);

			this.msg_cache.add(msg.uuid, msg.depth, msg.origin, msg.payload);
			var notif = new BroadcastReceived(this.pool_id_to_string.get(msg.pool), msg.uuid, msg.origin, msg.payload);
			this.triggerNotification(notif);
			Metrics.pubMessageReceived(msg.uuid, this.pool_id_to_string.get(msg.pool), 0, true);
		}
	}

	private void onBroadcastWant(BroadcastWant msg, Host from, short source_proto, int channel_id) {
		try (var __ = Profiling.span("onBroadcastWant")) {
			Metrics.kadReceiveMessage(from, "BroadcastWant");
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);

			var m = this.msg_cache.get(msg.uuid);
			if (m == null)
				return;

			this.kadSendMessage(new BroadcastMessage(msg.pool, m.depth + 1, m.uuid, m.origin, m.payload), from);
		}
	}

	private void onFindNodeRequest(FindNodeRequest msg, Host from, short source_proto, int channel_id) {
		try (var __ = Profiling.span("onFindNodeRequest")) {
			Metrics.kadReceiveMessage(from, "FindNodeRequest");
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);

			var rt = this.getRtFromPool(msg.pool);
			var closest = rt == null ? List.<KadPeer>of() : rt.closest(msg.target);
			logger.debug(
					"Received FindNodeRequest from " + from + " I am " + this.self.host + " with target " + msg.target
							+ " and pool " + msg.pool + ". closest = " + closest);
			if (msg.pool.isPresent()) {
				var pool_id = msg.pool.get();
				var pool = this.pools_rt.getPool(pool_id);
				var peer = this.addrbook.getPeerFromHost(from);
				if (pool != null && peer != null)
					pool.add(peer);
			}
			this.kadSendMessage(new FindNodeResponse(msg.context, closest, msg.pool), from);
		}
	}

	private void onFindNodeResponse(FindNodeResponse msg, Host from, short source_proto, int channel_id) {
		try (var __ = Profiling.span("onFindNodeResponse")) {
			Metrics.kadReceiveMessage(from, "FindNodeResponse");
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);

			msg.peers.forEach(p -> this.addrbook.add(p));

			var peer = this.addrbook.getPeerFromHost(from);
			this.query_manager.onFindNodeResponse(msg.context, peer.id, msg.peers);
		}
	}

	public void onFindPoolRequest(FindPoolRequest msg, Host from, short source_proto, int channel_id) {
		try (var __ = Profiling.span("onFindPoolRequest")) {
			Metrics.kadReceiveMessage(from, "FindPoolRequest");
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);
			logger.debug("Received FindPoolRequest from " + from + " I am " + this.self.host + " with pool " + msg.pool
					+ " and context " + msg.context);

			var closest = this.rt.closest(msg.pool);
			var members = this.addrbook.idsToPeers(this.pool_tracker.getSwarmSample(msg.pool));
			this.kadSendMessage(new FindPoolResponse(msg.context, closest, members), from);
		}
	}

	public void onFindPoolResponse(FindPoolResponse msg, Host from, short source_proto, int channel_id) {
		try (var __ = Profiling.span("onFindPoolResponse")) {
			Metrics.kadReceiveMessage(from, "FindPoolResponse");
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);
			logger.debug("Received FindPoolResponse from " + from + " I am " + this.self.host);

			msg.peers.forEach(p -> this.addrbook.add(p));
			msg.members.forEach(p -> this.addrbook.add(p));

			var peer = this.addrbook.getPeerFromHost(from);
			this.query_manager.onFindPoolResponse(msg.context, peer.id, msg.peers, msg.members);
		}
	}

	private void onFindSwarmRequest(FindSwarmRequest msg, Host from, short source_proto, int channel_id) {
		Metrics.kadReceiveMessage(from, "FindSwarmRequest");
		this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);

		var closest = this.rt.closest(msg.swarm);
		var members = this.swarm_tracker.getSwarmSample(msg.swarm);
		this.kadSendMessage(new FindSwarmResponse(msg.context, closest, this.addrbook.idsToPeers(members)), from);

		logger.debug("Received FindSwarmRequest from " + from + " I am " + this.self.host + " with swarm " + msg.swarm +
				" and sending " + members.size() + " members and " + closest.size() + " closest peers");
	}

	private void onFindSwarmResponse(FindSwarmResponse msg, Host from, short source_proto, int channel_id) {
		Metrics.kadReceiveMessage(from, "FindSwarmResponse");
		this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);

		msg.peers.forEach(p -> this.addrbook.add(p));
		msg.members.forEach(p -> this.addrbook.add(p));

		var peer = this.addrbook.getPeerFromHost(from);
		this.query_manager.onFindSwarmResponse(msg.context, peer.id, msg.peers, msg.members);
	}

	private void onFindValueRequest(FindValueRequest msg, Host from, short source_proto, int channel_id) {
		Metrics.kadReceiveMessage(from, "FindValueRequest");
		this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);

		var closest = this.rt.closest(msg.key);
		var value = this.storage.get(msg.key);
		var response = new FindValueResponse(msg.context, closest, value);
		this.kadSendMessage(response, from);
	}

	private void onFindValueResponse(FindValueResponse msg, Host from, short source_proto, int channel_id) {
		Metrics.kadReceiveMessage(from, "FindValueResponse");
		this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);

		msg.peers.forEach(p -> this.addrbook.add(p));

		var peer = this.addrbook.getPeerFromHost(from);
		this.query_manager.onFindValueResponse(msg.context, peer.id, msg.peers, msg.value);
	}

	private void onHandshake(Handshake msg, Host from, short source_proto, int channel_id) {
		Metrics.kadReceiveMessage(from, "Handshake");
		logger.debug("Received handshake from " + from + ": " + msg);

		var peer = new KadPeer(msg.id, from);
		this.addrbook.add(msg.id, from);
		this.conn_flags.set(from, ConnectionFlags.RECEIVED_HANDSHAKE);
		this.onPeerConnect(peer);
	}

	private void onJoinPoolRequest(JoinPoolRequest msg, Host from, short source_proto, int channel_id) {
		try (var __ = Profiling.span("onJoinPoolRequest")) {
			Metrics.kadReceiveMessage(from, "JoinPoolRequest");
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);
			logger.debug(
					"Received JoinPoolRequest from " + from + " I am " + this.self.host + " with pool " + msg.pool);

			var peer = this.addrbook.getPeerFromHost(from);
			this.pool_tracker.add(msg.pool, peer.id);
		}
	}

	private void onJoinSwarmRequest(JoinSwarmRequest msg, Host from, short source_proto, int channel_id) {
		Metrics.kadReceiveMessage(from, "JoinSwarmRequest");
		this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);
		logger.debug("Received JoinSwarmRequest from " + from + " I am " + this.self.host + " with swarm " + msg.swarm);

		var peer = this.addrbook.getPeerFromHost(from);
		this.swarm_tracker.add(msg.swarm, peer.id);
	}

	private void onStoreRequest(StoreRequest msg, Host from, short source_proto, int channel_id) {
		Metrics.kadReceiveMessage(from, "StoreRequest");
		this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);
		logger.debug("Received StoreRequest from " + from + " I am " + this.self.host + " with key " + msg.key);

		this.storage.store(msg.key, msg.value);
	}

	/*--------------------------------- Request Handlers ---------------------------------------- */
	private void onBroadcast(Broadcast msg, short source_proto) {
		var pool_id = KadID.ofData(msg.pool);
		var pool = this.pools_rt.getPool(pool_id);

		this.msg_cache.add(msg.uuid, 0, this.self, msg.payload);
		System.out.println("Routing table size is " + this.rt.size());

		if (pool == null) {
			this.startQuery(new FindPoolQueryDescriptor(pool_id, (__, members) -> {
				for (var member : members) {
					var peer = this.addrbook.getPeerFromID(member);
					if (peer == null)
						continue;
					this.kadSendMessage(new BroadcastMessage(pool_id, 0, msg.uuid, this.self, msg.payload), peer.host);
				}
				System.out.println(
						"Queried for pool " + pool_id + " and found " + members.size() + " members to send to");
				Metrics.pubMessageSent(msg.uuid, msg.pool, true, members.size());
			}));
		} else {
			Callback do_broadcast = () -> {
				var peers = pool.getBroadcastSample(0, 10);
				for (var peer : peers)
					this.kadSendMessage(new BroadcastMessage(pool_id, 1, msg.uuid, this.self, msg.payload), peer.host);
				Metrics.pubMessageSent(msg.uuid, msg.pool, true, peers.size());
			};

			if (pool.isEmpty())
				this.refreshPool(pool_id, do_broadcast);
			else
				do_broadcast.execute();
		}
	}

	private void onFindClosest(FindClosest msg, short source_proto) {
		this.startQuery(new FindClosestQueryDescriptor(msg.target, msg.pool, closest -> {
			var closest_peers = this.addrbook.idsToPeers(closest);
			var reply = new FindClosestReply(msg.target, closest_peers);
			this.sendReply(reply, source_proto);
		}));
	}

	private void onFindPool(FindPool msg, short source_proto) {
		var pool_id = KadID.ofData(msg.pool);
		this.startQuery(new FindPoolQueryDescriptor(pool_id, (__, members) -> {
			this.sendReply(new FindPoolReply(msg.pool, this.addrbook.idsToPeers(members)), source_proto);
		}));
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
				this.kadSendMessage(request, host);
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

		this.pool_id_to_string.put(pool_id, msg.pool);
		var pool = this.pools_rt.createPool(pool_id);
		this.startQuery(new FindPoolQueryDescriptor(pool_id, (closest, members) -> {
			this.addrbook.idsToPeers(members).forEach(pool::add);
			System.out.println("onJoinPool, size is " + pool.size() + " and closest is " + closest.size()
					+ " and rt size is " + this.rt.size());
			for (var peer : closest) {
				var host = this.addrbook.getHostFromID(peer);
				if (host == null)
					continue;
				var request = new JoinPoolRequest(pool_id);
				this.kadSendMessage(request, host);
			}
			Metrics.subscribedTopic(msg.pool);
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
				this.kadSendMessage(request, host);
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
				logger.debug("Sending StoreRequest to " + host);
				this.kadSendMessage(request, host);
			}
		});
		this.startQuery(descriptor);
	}

	/*--------------------------------- Channel Event Handlers ---------------------------------------- */
	private void onOutConnectionDown(OutConnectionDown event, int channel_id) {
		assert channel_id == this.channel_id;
		Metrics.connectionEvent(event.getNode(), "OutConnectionDown");
		logger.warn("Outgoing connection to " + event.getNode() + " is down");
		this.conn_flags.unset(event.getNode(), ConnectionFlags.SENT_HANDSHAKE | ConnectionFlags.IS_ATTEMPTING_CONNECT);
	}

	private void onOutConnectionFailed(OutConnectionFailed<ProtoMessage> event, int channel_id) {
		assert channel_id == this.channel_id;
		Metrics.connectionEvent(event.getNode(), "OutConnectionFailed");
		logger.warn("Failed to connect to " + event.getNode());
		this.conn_flags.unset(event.getNode(), ConnectionFlags.IS_ATTEMPTING_CONNECT);
	}

	private void onOutConnectionUp(OutConnectionUp event, int channel_id) {
		assert channel_id == this.channel_id;
		Metrics.connectionEvent(event.getNode(), "OutConnectionUp");
		logger.debug("Out connection up");
		this.conn_flags.unset(event.getNode(), ConnectionFlags.IS_ATTEMPTING_CONNECT);
	}

	private void onInConnectionUp(InConnectionUp event, int channel_id) {
		assert channel_id == this.channel_id;
		Metrics.connectionEvent(event.getNode(), "InConnectionUp");
		logger.debug("In connection up");
	}

	private void onInConnectionDown(InConnectionDown event, int channel_id) {
		assert channel_id == this.channel_id;
		Metrics.connectionEvent(event.getNode(), "InConnectionDown");
		logger.warn("In connection down");
		this.conn_flags.unset(event.getNode(), ConnectionFlags.RECEIVED_HANDSHAKE);
		var peer = this.addrbook.getPeerFromHost(event.getNode());
		if (peer != null && this.conn_flags.test(event.getNode(), ConnectionFlags.RECEIVED_HANDSHAKE)) {
			this.onPeerDisconnect(peer);
		}
	}

	/*--------------------------------- Timer Handlers ---------------------------------------- */
	private void onCheckQueryTimeouts(CheckQueryTimeoutsTimer timer, long timer_id) {
		this.query_manager.checkTimeouts();
	}

	private void onRefreshRT(RefreshRTTimer timer, long timer_id) {
		logger.debug("Refreshing routing tables");
		this.startQuery(new FindClosestQueryDescriptor(this.self.id));

		var iter = this.pools_rt.iterator();
		while (iter.hasNext()) {
			var item = iter.next();
			var pool_id = item.getKey();
			this.refreshPool(pool_id);
		}
	}

	/*--------------------------------- QueryManagerIO ---------------------------------------- */

	@Override
	public void discover(KadPeer peer) {
		this.addrbook.add(peer);
	}

	@Override
	public void findNodeRequest(long context, KadID id, Optional<KadID> pool, KadID target) {
		var host = this.addrbook.getHostFromID(id);
		if (host == null) {
			logger.warn("Could not find host for peer " + id + " while sending FindNodeRequest");
			return;
		}
		var request = new FindNodeRequest(context, target, pool);
		this.kadSendMessage(request, host);
	}

	@Override
	public void findValueRequest(long context, KadID id, KadID key) {
		var host = this.addrbook.getHostFromID(id);
		if (host == null) {
			logger.warn("Could not find host for peer " + id + " while sending FindValueRequest");
			return;
		}
		var request = new FindValueRequest(context, key);
		this.kadSendMessage(request, host);
	}

	@Override
	public void findSwarmRequest(long context, KadID id, KadID swarm) {
		var host = this.addrbook.getHostFromID(id);
		if (host == null) {
			logger.warn("Could not find host for peer " + id + " while sending FindSwarmRequest");
			return;
		}
		var request = new FindSwarmRequest(context, swarm);
		this.kadSendMessage(request, host);
	}

	@Override
	public void findPoolRequest(long context, KadID id, KadID pool) {
		var host = this.addrbook.getHostFromID(id);
		if (host == null) {
			logger.warn("Could not find host for peer " + id + " while sending FindPoolRequest");
			return;
		}
		var request = new FindPoolRequest(context, pool);
		this.kadSendMessage(request, host);
	}
}
