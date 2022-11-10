package asd.protocols.overlay.kad;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import asd.metrics.Metrics;
import asd.metrics.MetricsProtoMessage;
import asd.metrics.Profiling;
import asd.protocols.overlay.common.notifications.ChannelCreatedNotification;
import asd.protocols.overlay.common.notifications.NeighbourDown;
import asd.protocols.overlay.common.notifications.NeighbourUp;
import asd.protocols.overlay.kad.bcast.HaveTracker;
import asd.protocols.overlay.kad.bcast.Message;
import asd.protocols.overlay.kad.bcast.MessageCache;
import asd.protocols.overlay.kad.bcast.RequestTracker;
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
import asd.protocols.overlay.kad.query.BasicQueryManager;
import asd.protocols.overlay.kad.query.CachedQueryManager;
import asd.protocols.overlay.kad.query.QueryManager;
import asd.protocols.overlay.kad.query.QueryManagerIO;
import asd.protocols.overlay.kad.routing.RoutingTables;
import asd.protocols.overlay.kad.routing.SwarmTracker;
import asd.protocols.overlay.kad.timers.CheckMessageTimeoutsTimer;
import asd.protocols.overlay.kad.timers.CheckQueryTimeoutsTimer;
import asd.protocols.overlay.kad.timers.MetricDebugTimer;
import asd.protocols.overlay.kad.timers.RefreshRoutingTable;
import asd.utils.ASDUtils;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.ChannelMetrics;
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
	private final RoutingTables rts;
	private final KadStorage storage;
	private final KadAddrBook addrbook;
	private final QueryManager query_manager;
	private final SwarmTracker swarm_tracker;
	private final KadParams params;
	private final SwarmTracker pool_tracker;
	private final ConnectionFlags conn_flags;
	private final MessageCache msg_cache;
	private final RequestTracker msg_tracker;
	private final HaveTracker have_tracker;
	private final Duration routing_table_refresh;

	public Kademlia(Properties props, Host self) throws IOException, HandlerRegistrationException {
		super(NAME, ID);

		// Create a properties object to setup channel-specific properties. See the
		// channel description for more details.
		Properties channel_props = new Properties();
		channel_props.setProperty(TCPChannel.ADDRESS_KEY, props.getProperty("babel_address")); // The address to bind to
		channel_props.setProperty(TCPChannel.PORT_KEY, props.getProperty("babel_port")); // The port to bind to
		channel_props.setProperty(TCPChannel.METRICS_INTERVAL_KEY, "10000"); // The interval to receive channel metrics
		channel_props.setProperty(TCPChannel.HEARTBEAT_INTERVAL_KEY, "1000"); // Heartbeats interval for established
																				// connections
		channel_props.setProperty(TCPChannel.HEARTBEAT_TOLERANCE_KEY, "3000"); // Time passed without heartbeats until
																				// closing a connection
		channel_props.setProperty(TCPChannel.CONNECT_TIMEOUT_KEY, "1000"); // TCP connect timeout

		var k = Integer.parseInt(props.getProperty("kad_k"));
		var alpha = Integer.parseInt(props.getProperty("kad_alpha"));
		var routing_table_refresh = Duration.parse(props.getProperty("kad_rt_refresh"));
		var query_request_timeout = Duration.parse(props.getProperty("kad_query_request_timeout"));
		var query_cache_ttl = Duration.parse(props.getProperty("kad_query_cache_ttl"));
		var swarmttl = Duration.parse(props.getProperty("kad_swarm_ttl"));
		var pubsub_msg_timeout = Duration.parse(props.getProperty("kad_pubsub_msg_timeout"));
		var pubsub_k = Integer.parseInt(props.getProperty("kad_pubsub_k"));
		var pubsub_rfac = Integer.parseInt(props.getProperty("kad_pubsub_rfac"));
		var pubsub_have_ttl = Duration.parse(props.getProperty("kad_pubsub_have_ttl"));
		var params = new KadParams(k, alpha, query_request_timeout, swarmttl, pubsub_msg_timeout, pubsub_k,
				pubsub_rfac);

		this.channel_id = createChannel(TCPChannel.NAME, channel_props); // Create the channel with the given properties
		this.self = new KadPeer(KadID.random(), self);
		this.rts = new RoutingTables(params, this.self.id);
		this.storage = new KadStorage();
		this.addrbook = new KadAddrBook();
		this.swarm_tracker = new SwarmTracker(params);
		this.params = params;
		this.pool_tracker = new SwarmTracker(params);
		this.conn_flags = new ConnectionFlags();
		this.query_manager = new CachedQueryManager(
				new BasicQueryManager(params, this.rts, this.self.id, this),
				query_cache_ttl);
		this.msg_cache = new MessageCache();
		this.msg_tracker = new RequestTracker();
		this.have_tracker = new HaveTracker(pubsub_have_ttl);
		this.routing_table_refresh = routing_table_refresh;

		Metrics.kademliaIdentifier(this.self.id);

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
		this.registerChannelEventHandler(this.channel_id, ChannelMetrics.EVENT_ID, this::onChannelMetrics);

		/*-------------------- Register Timer Events ------------------------------- */
		this.registerTimerHandler(CheckMessageTimeoutsTimer.ID, this::onCheckMessageTimeouts);
		this.registerTimerHandler(CheckQueryTimeoutsTimer.ID, this::onCheckQueryTimeouts);
		this.registerTimerHandler(RefreshRoutingTable.ID, this::onRefreshRoutingTable);
		this.registerTimerHandler(MetricDebugTimer.ID, this::onMetricDebug);
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
		this.setupTimer(new RefreshRoutingTable(), (5 + (long) (Math.random() * 30)) * 1000);
		this.setupPeriodicTimer(new MetricDebugTimer(), 0, 2 * 1000);
	}

	/*--------------------------------- Public Helpers ---------------------------------------- */

	public void printRoutingTable() {
		System.out.println(this.rts.main());
	}

	public void printPoolRoutingTable(String pool) {
		var rt = this.rts.get(KadID.ofData(pool));
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

	private void kadSendMessage(MetricsProtoMessage msg, Host host) {
		if (!this.conn_flags.test(host, ConnectionFlags.IS_ATTEMPTING_CONNECT | ConnectionFlags.SENT_HANDSHAKE))
			this.kadConnect(host);
		this.loggedSendMessage(msg, host);
	}

	// Called when a peer has established a connection to us
	private void onPeerConnect(KadPeer peer) {
		if (peer.host.getPort() == 5000)
			System.out.println("Peer connected: " + peer);
		if (this.rts.main().add(peer)) {
			System.out.println("Added peer to routing table: " + peer);
			logger.debug("Added " + peer + " to our routing table");
		}
		this.triggerNotification(new NeighbourUp(peer.host));
		this.kadConnect(peer.host);
	}

	// Call when a peer has closed a connection to us
	private void onPeerDisconnect(KadPeer peer) {
		logger.debug("Connection to " + peer + " is down");
		this.rts.remove(peer.id);
		this.triggerNotification(new NeighbourDown(peer.host));
	}

	private void ensureConnectionInEstablished(MetricsProtoMessage msg, Host from, short source_proto,
			int channel_id) {
		assert channel_id == this.channel_id;
		assert source_proto == ID;

		if (!this.conn_flags.test(from, ConnectionFlags.RECEIVED_HANDSHAKE))
			throw new IllegalStateException("Received message from " + from + " but connection is not established: "
					+ msg.getClass().getName());
	}

	private void loggedSendMessage(MetricsProtoMessage msg, Host destination) {
		Metrics.messageSent(destination, msg.serializeToMetric());
		this.sendMessage(msg, destination);
	}

	/*--------------------------------- Message Handlers ---------------------------------------- */
	private void onBroadcastHave(BroadcastHave msg, Host from, short source_proto, int channel_id) {
		Metrics.messageReceived(from, msg);

		try (var __ = Profiling.span("onBroadcastHave")) {
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);

			if (!this.rts.contains(msg.rtid))
				return;

			var peer = this.addrbook.getPeerFromHost(from);
			this.have_tracker.add(msg.uuid, peer.id);

			if (this.msg_cache.contains(msg.uuid))
				return;

			if (!this.msg_tracker.isTracking(msg.uuid))
				this.msg_tracker.startTracking(msg.rtid, msg.uuid);

			this.msg_tracker.addProvider(msg.uuid, peer.id);
			if (!this.msg_tracker.isRequesting(msg.uuid)) {
				this.msg_tracker.beginRequest(msg.uuid, peer.id);
				this.kadSendMessage(new BroadcastWant(msg.rtid, msg.uuid), from);
			}
		}
	}

	private void onBroadcastMessage(BroadcastMessage msg, Host from, short source_proto, int channel_id) {
		Metrics.messageReceived(from, msg);

		try (var __ = Profiling.span("onBroadcastMessage")) {
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);

			if (!this.rts.contains(msg.rtid)) {
				Metrics.pubMessageReceived(from, msg.uuid, "unknown", 0, false);
				return;
			}

			if (this.msg_cache.contains(msg.uuid)) {
				Metrics.pubMessageReceived(from, msg.uuid, TopicRegistry.lookup(msg.rtid), msg.hop_count, false);
				return;
			}

			var extra_hops = 1;
			if (this.msg_tracker.isTracking(msg.uuid)) {
				extra_hops = 3; // Add the hops from Have/Want messages
				this.msg_tracker.endRequest(msg.uuid);
			}

			var message = new Message(msg.rtid, msg.uuid, msg.origin, msg.payload, msg.hop_count + extra_hops);
			this.msg_cache.add(message);

			this.triggerNotification(
					new BroadcastReceived(TopicRegistry.lookup(msg.rtid), msg.uuid, msg.origin, msg.payload));
			Metrics.pubMessageReceived(from, msg.uuid, TopicRegistry.lookup(msg.rtid), message.hop_count, true);

			this.broadcastMessageAsSubscriber(message, msg.ceil, msg.apply_redundancy);
		}
	}

	private void onBroadcastWant(BroadcastWant msg, Host from, short source_proto, int channel_id) {
		Metrics.messageReceived(from, msg);

		try (var __ = Profiling.span("onBroadcastWant")) {
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);

			var m = this.msg_cache.get(msg.uuid);
			if (m == null)
				return;

			var rt = this.rts.get(msg.rtid);
			var peer_id = this.addrbook.getIdFromHost(from);
			var peer_cpl = this.self.id.cpl(peer_id);
			var ceil = Math.min(rt.buckets(), peer_cpl + 1);
			var bmessage = new BroadcastMessage(m.rtid, m.uuid, m.origin, m.hop_count, ceil, false, m.payload);
			this.kadSendMessage(bmessage, from);
		}
	}

	private void onFindNodeRequest(FindNodeRequest msg, Host from, short source_proto, int channel_id) {
		Metrics.messageReceived(from, msg);

		try (var __ = Profiling.span("onFindNodeRequest")) {
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);

			var rt = this.rts.get(msg.rtid);
			var closest = rt == null ? List.<KadPeer>of() : rt.closest(msg.target);
			logger.debug(
					"Received FindNodeRequest from " + from + " I am " + this.self.host + " with target " + msg.target
							+ " and rtid " + msg.rtid + ". closest = " + closest);
			if (rt != null) {
				var peer = this.addrbook.getPeerFromHost(from);
				if (peer != null)
					rt.add(peer);
			}
			this.kadSendMessage(new FindNodeResponse(msg.context, closest, msg.rtid), from);
		}
	}

	private void onFindNodeResponse(FindNodeResponse msg, Host from, short source_proto, int channel_id) {
		Metrics.messageReceived(from, msg);

		try (var __ = Profiling.span("onFindNodeResponse")) {
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);

			msg.peers.forEach(p -> this.addrbook.add(p));

			var peer = this.addrbook.getPeerFromHost(from);
			this.query_manager.onFindNodeResponse(msg.context, peer.id, msg.peers);
		}
	}

	public void onFindPoolRequest(FindPoolRequest msg, Host from, short source_proto, int channel_id) {
		Metrics.messageReceived(from, msg);

		try (var __ = Profiling.span("onFindPoolRequest")) {
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);
			logger.debug("Received FindPoolRequest from " + from + " I am " + this.self.host + " with pool " + msg.pool
					+ " and context " + msg.context);

			var closest = this.rts.closest(msg.pool);
			var members = this.addrbook.idsToPeers(this.pool_tracker.getSwarmSample(msg.pool));
			this.kadSendMessage(new FindPoolResponse(msg.context, closest, members), from);
		}
	}

	public void onFindPoolResponse(FindPoolResponse msg, Host from, short source_proto, int channel_id) {
		Metrics.messageReceived(from, msg);

		try (var __ = Profiling.span("onFindPoolResponse")) {
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);
			logger.debug("Received FindPoolResponse from " + from + " I am " + this.self.host);

			msg.peers.forEach(p -> this.addrbook.add(p));
			msg.members.forEach(p -> this.addrbook.add(p));

			var peer = this.addrbook.getPeerFromHost(from);
			this.query_manager.onFindPoolResponse(msg.context, peer.id, msg.peers, msg.members);
		}
	}

	private void onFindSwarmRequest(FindSwarmRequest msg, Host from, short source_proto, int channel_id) {
		Metrics.messageReceived(from, msg);

		try (var __ = Profiling.span("onFindSwarmRequest")) {
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);

			var closest = this.rts.closest(msg.swarm);
			var members = this.swarm_tracker.getSwarmSample(msg.swarm);
			this.kadSendMessage(new FindSwarmResponse(msg.context, closest, this.addrbook.idsToPeers(members)), from);

			logger.debug(
					"Received FindSwarmRequest from " + from + " I am " + this.self.host + " with swarm " + msg.swarm +
							" and sending " + members.size() + " members and " + closest.size() + " closest peers");
		}
	}

	private void onFindSwarmResponse(FindSwarmResponse msg, Host from, short source_proto, int channel_id) {
		Metrics.messageReceived(from, msg);

		try (var __ = Profiling.span("onFindSwarmResponse")) {
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);

			msg.peers.forEach(p -> this.addrbook.add(p));
			msg.members.forEach(p -> this.addrbook.add(p));

			var peer = this.addrbook.getPeerFromHost(from);
			this.query_manager.onFindSwarmResponse(msg.context, peer.id, msg.peers, msg.members);
		}
	}

	private void onFindValueRequest(FindValueRequest msg, Host from, short source_proto, int channel_id) {
		Metrics.messageReceived(from, msg);

		try (var __ = Profiling.span("onFindValueRequest")) {
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);

			var closest = this.rts.closest(msg.key);
			var value = this.storage.get(msg.key);
			var response = new FindValueResponse(msg.context, closest, value);
			this.kadSendMessage(response, from);
		}
	}

	private void onFindValueResponse(FindValueResponse msg, Host from, short source_proto, int channel_id) {
		Metrics.messageReceived(from, msg);

		try (var __ = Profiling.span("onFindValueResponse")) {
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);

			msg.peers.forEach(p -> this.addrbook.add(p));

			var peer = this.addrbook.getPeerFromHost(from);
			this.query_manager.onFindValueResponse(msg.context, peer.id, msg.peers, msg.value);
		}
	}

	private void onHandshake(Handshake msg, Host from, short source_proto, int channel_id) {
		Metrics.messageReceived(from, msg);

		try (var __ = Profiling.span("onHandshake")) {
			logger.debug("Received handshake from " + from + ": " + msg);

			var peer = new KadPeer(msg.id, from);
			this.addrbook.add(msg.id, from);
			this.conn_flags.set(from, ConnectionFlags.RECEIVED_HANDSHAKE);
			this.onPeerConnect(peer);
		}
	}

	private void onJoinPoolRequest(JoinPoolRequest msg, Host from, short source_proto, int channel_id) {
		Metrics.messageReceived(from, msg);

		try (var __ = Profiling.span("onJoinPoolRequest")) {
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);
			logger.debug(
					"Received JoinPoolRequest from " + from + " I am " + this.self.host + " with pool " + msg.rtid);

			var peer = this.addrbook.getPeerFromHost(from);
			this.pool_tracker.add(msg.rtid, peer.id);
		}
	}

	private void onJoinSwarmRequest(JoinSwarmRequest msg, Host from, short source_proto, int channel_id) {
		Metrics.messageReceived(from, msg);

		try (var __ = Profiling.span("onJoinSwarmRequest")) {
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);
			logger.debug(
					"Received JoinSwarmRequest from " + from + " I am " + this.self.host + " with swarm " + msg.swarm);

			var peer = this.addrbook.getPeerFromHost(from);
			this.swarm_tracker.add(msg.swarm, peer.id);
		}
	}

	private void onStoreRequest(StoreRequest msg, Host from, short source_proto, int channel_id) {
		Metrics.messageReceived(from, msg);

		try (var __ = Profiling.span("onStoreRequest")) {
			this.ensureConnectionInEstablished(msg, from, source_proto, channel_id);
			logger.debug("Received StoreRequest from " + from + " I am " + this.self.host + " with key " + msg.key);

			this.storage.store(msg.key, msg.value);
		}
	}

	private void broadcastMessage(Message message) {
		if (!this.rts.contains(message.rtid))
			this.broadcastMessageAsNonSubscriber(message);
		else
			this.broadcastMessageAsSubscriber(message, 0, true);
	}

	private void broadcastMessageAsSubscriber(Message message, int ceil, boolean apply_redundancy) {
		var rt = this.rts.get(message.rtid);
		var redundancy = apply_redundancy ? this.params.pubsub_rfac : 1;
		var rbucket_index = rt.buckets() - 1;

		// Only broadcast the full message if ceil == 0, that means we are the source of
		// the message and no one else has broadcasted it yet.
		for (int i = rbucket_index; i >= ceil; --i) {
			var bucket_index = i;
			var bucket = rt.bucket(bucket_index);
			var broadcast_ceil = bucket_index + 1;

			Runnable broadcast = () -> {
				var bucket_size = bucket.size();
				var bucket_n = bucket_index == rbucket_index ? bucket_size : Math.min(bucket_size, redundancy);
				for (int j = 0; j < bucket_n; ++j) {
					var peer = bucket.get(j);

					MetricsProtoMessage bmessage = null;
					if (ceil == 0)
						bmessage = new BroadcastMessage(message.rtid, message.uuid, message.origin, message.hop_count,
								broadcast_ceil, false, message.payload);
					else
						bmessage = new BroadcastHave(message.rtid, message.uuid);

					if (!this.have_tracker.contains(message.uuid, peer.id))
						this.kadSendMessage(bmessage, peer.host);
				}
			};

			if (bucket.isEmpty()) {
				this.query_manager.findClosest(message.rtid, KadID.randomWithCpl(this.self.id, i), (closest) -> {
					closest.stream().map(this.addrbook::getPeerFromID).filter(Objects::nonNull).forEach(rt::add);
					broadcast.run();
				});
			} else {
				broadcast.run();
			}
		}
	}

	private void broadcastMessageAsNonSubscriber(Message message) {
		var redundancy = this.params.pubsub_rfac;
		this.query_manager.findPool(message.rtid, (closest, members) -> {
			var targets = ASDUtils.sample(redundancy, Set.copyOf(members));
			var bmessage = new BroadcastMessage(message.rtid, message.uuid, message.origin, message.hop_count, 0, true,
					message.payload);
			for (var target : targets)
				this.kadSendMessage(bmessage, this.addrbook.getHostFromID(target));
		});
	}

	/*--------------------------------- Request Handlers ---------------------------------------- */
	private void onBroadcast(Broadcast msg, short source_proto) {
		var rtid = KadID.ofData(msg.topic);
		var rt = this.rts.get(rtid);
		var message = new Message(rtid, msg.uuid, this.self, msg.payload, 0);
		var deliver = rt != null; // Are we subscribed to this topic

		this.msg_cache.add(message);
		if (deliver) {
			Metrics.pubMessageReceived(this.self.host, message.uuid, msg.topic, 0, true);
			this.triggerNotification(new BroadcastReceived(msg.topic, msg.uuid, message.origin, msg.payload));
		}

		Metrics.pubMessageSent(this.self.host, msg.uuid, msg.topic, deliver);
		this.broadcastMessage(message);
	}

	private void onFindClosest(FindClosest msg, short source_proto) {
		this.query_manager.findClosest(msg.rtid, msg.target, closest -> {
			var closest_peers = this.addrbook.idsToPeers(closest);
			var reply = new FindClosestReply(msg.target, closest_peers);
			this.sendReply(reply, source_proto);
		});
	}

	private void onFindPool(FindPool msg, short source_proto) {
		var pool_id = KadID.ofData(msg.pool);
		this.query_manager.findPool(pool_id, (__, members) -> {
			this.sendReply(new FindPoolReply(msg.pool, this.addrbook.idsToPeers(members)), source_proto);
		});
	}

	private void onFindSwarm(FindSwarm msg, short source_proto) {
		var k = this.params.k;
		var swarm_id = KadID.ofData(msg.swarm);
		this.query_manager.findSwarm(swarm_id, msg.sample_size.orElse(k), (__, members) -> {
			var reply = new FindSwarmReply(msg.swarm, this.addrbook.idsToPeers(members));
			this.sendReply(reply, source_proto);
		});
	}

	private void onFindValue(FindValue msg, short source_proto) {
		this.query_manager.findValue(msg.key, (closest, value) -> {
			if (closest.isPresent() && value.isPresent()) {
				var host = this.addrbook.getHostFromID(closest.get());
				var request = new StoreRequest(msg.key, value.get());
				this.kadSendMessage(request, host);
			}
			var reply = new FindValueReply(value);
			this.sendReply(reply, source_proto);
		});
	}

	private void onJoinPool(JoinPool msg, short source_proto) {
		var pool_id = KadID.ofData(msg.pool);
		if (this.rts.contains(pool_id)) {
			this.sendReply(new JoinPoolReply(msg.pool), source_proto);
			return;
		}

		TopicRegistry.register(pool_id, msg.pool);
		var pool = this.rts.create(pool_id);
		this.setupTimer(new RefreshRoutingTable(pool_id), 1000);
		this.query_manager.findPool(pool_id, (closest, members) -> {
			this.addrbook.idsToPeers(members).forEach(pool::add);
			for (var peer : closest) {
				var host = this.addrbook.getHostFromID(peer);
				if (host == null)
					continue;
				var request = new JoinPoolRequest(pool_id);
				this.kadSendMessage(request, host);
			}
			Metrics.subscribedTopic(msg.pool);
			this.sendReply(new JoinPoolReply(msg.pool), source_proto);
		});
	}

	private void onJoinSwarm(JoinSwarm msg, short source_proto) {
		var k = this.params.k;
		var swarm_id = KadID.ofData(msg.swarm);
		this.query_manager.findSwarm(swarm_id, msg.sample_size.orElse(k), (closest, members) -> {
			for (var peer : closest) {
				var host = this.addrbook.getHostFromID(peer);
				if (host == null)
					continue;
				var request = new JoinSwarmRequest(swarm_id);
				this.kadSendMessage(request, host);
			}

			var reply = new JoinSwarmReply(msg.swarm, this.addrbook.idsToPeers(members));
			this.sendReply(reply, source_proto);
		});
	}

	private void onStoreValue(StoreValue msg, short source_proto) {
		this.query_manager.findClosest(msg.key, closest -> {
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
	}

	/*--------------------------------- Channel Event Handlers ---------------------------------------- */
	private void onOutConnectionDown(OutConnectionDown event, int channel_id) {
		assert channel_id == this.channel_id;
		logger.warn("Outgoing connection to " + event.getNode() + " is down");
		this.conn_flags.unset(event.getNode(), ConnectionFlags.SENT_HANDSHAKE | ConnectionFlags.IS_ATTEMPTING_CONNECT);
	}

	private void onOutConnectionFailed(OutConnectionFailed<ProtoMessage> event, int channel_id) {
		assert channel_id == this.channel_id;
		logger.warn("Failed to connect to " + event.getNode());
		this.conn_flags.unset(event.getNode(), ConnectionFlags.IS_ATTEMPTING_CONNECT);
	}

	private void onOutConnectionUp(OutConnectionUp event, int channel_id) {
		assert channel_id == this.channel_id;
		logger.debug("Out connection up");
		this.conn_flags.unset(event.getNode(), ConnectionFlags.IS_ATTEMPTING_CONNECT);
	}

	private void onInConnectionUp(InConnectionUp event, int channel_id) {
		assert channel_id == this.channel_id;
		logger.debug("In connection up");
	}

	private void onInConnectionDown(InConnectionDown event, int channel_id) {
		assert channel_id == this.channel_id;
		logger.warn("In connection down");
		this.conn_flags.unset(event.getNode(), ConnectionFlags.RECEIVED_HANDSHAKE);
		var peer = this.addrbook.getPeerFromHost(event.getNode());
		if (peer != null && this.conn_flags.test(event.getNode(), ConnectionFlags.RECEIVED_HANDSHAKE)) {
			this.onPeerDisconnect(peer);
		}
	}

	private void onChannelMetrics(ChannelMetrics event, int channel_id) {
		assert channel_id == this.channel_id;
		Metrics.network(event);
	}

	/*--------------------------------- Timer Handlers ---------------------------------------- */
	private void onCheckMessageTimeouts(CheckMessageTimeoutsTimer timer, long timer_id) {
		var expired = this.msg_tracker.checkTimeouts();
		for (var exp : expired) {
			var provider = this.msg_tracker.getProvider(exp.uuid());
			if (provider == null) {
				this.msg_tracker.stopTracking(exp.uuid());
				continue;
			}
			this.msg_tracker.beginRequest(exp.uuid(), provider);
			this.kadSendMessage(new BroadcastWant(exp.rtid(), exp.uuid()), this.addrbook.getHostFromID(provider));
		}
	}

	private void onCheckQueryTimeouts(CheckQueryTimeoutsTimer timer, long timer_id) {
		this.query_manager.checkTimeouts();

		// This could probably go somewhere else
		this.have_tracker.checkTimeouts();
	}

	private void onRefreshRoutingTable(RefreshRoutingTable timer, long timer_id) {
		logger.debug("Refreshing routing table " + timer.rtid);
		var rt = this.rts.get(timer.rtid);
		if (rt == null)
			return;

		Function<List<KadID>, Void> refresh = (members) -> {
			members.stream().map(this.addrbook::getPeerFromID).filter(Objects::nonNull).forEach(rt::add);

			var next_refresh = this.routing_table_refresh.getSeconds() * 1000
					+ this.routing_table_refresh.getNano() / 1000000;
			if (rt.size() < this.params.k / 4)
				next_refresh = Math.min(2000, next_refresh);

			this.setupTimer(new RefreshRoutingTable(timer.rtid), next_refresh);
			return null;
		};

		if (timer.rtid != KadID.DEFAULT_RTID && rt.isEmpty())
			this.query_manager.findPool(timer.rtid, (closest, members) -> refresh.apply(members));
		else
			this.query_manager.findClosest(timer.rtid, this.self.id, closest -> refresh.apply(closest));
	}

	private void onMetricDebug(MetricDebugTimer timer, long timer_id) {
		if (Metrics.level() >= Metrics.METRIC_LEVEL_DETAILED) {
			Metrics.routingTable("", this.rts.main().dumpForMetrics());
			for (var rt : this.rts.allButTheMainOne())
				Metrics.routingTable(TopicRegistry.lookup(rt.getKey()), rt.getValue().dumpForMetrics());
			System.out
					.println("Outputing routing tables, there are " + this.rts.allButTheMainOne().size() + " of them");
		}
	}

	/*--------------------------------- QueryManagerIO ---------------------------------------- */

	@Override
	public void discover(KadPeer peer) {
		this.addrbook.add(peer);
	}

	@Override
	public void findNodeRequest(long context, KadID id, KadID rtid, KadID target) {
		var host = this.addrbook.getHostFromID(id);
		if (host == null) {
			logger.warn("Could not find host for peer " + id + " while sending FindNodeRequest");
			return;
		}
		var request = new FindNodeRequest(context, rtid, target);
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
