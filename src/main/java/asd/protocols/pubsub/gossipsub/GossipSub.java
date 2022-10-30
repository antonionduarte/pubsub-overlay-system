package asd.protocols.pubsub.gossipsub;

import asd.protocols.apps.AutomatedApp;
import asd.protocols.overlay.common.notifications.ChannelCreatedNotification;
import asd.protocols.overlay.kad.Kademlia;
import asd.protocols.overlay.kad.ipc.FindSwarm;
import asd.protocols.overlay.kad.ipc.FindSwarmReply;
import asd.protocols.overlay.kad.ipc.JoinSwarm;
import asd.protocols.overlay.kad.ipc.JoinSwarmReply;
import asd.protocols.pubsub.common.*;
import asd.protocols.pubsub.gossipsub.messages.*;
import asd.protocols.pubsub.gossipsub.timers.HeartbeatTimer;
import asd.protocols.pubsub.gossipsub.timers.InfoTimer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.channel.tcp.events.*;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.*;

import static asd.utils.ASDUtils.sample;

public class GossipSub extends GenericProtocol {

	private final Host self;
	private int channelId = -1;

	private final int heartbeatInterval;
	private final int heartbeatInitialDelay;
	private final int degree, degreeLow, degreeHigh;
	private final int degreeLazy;
	private final int maxIHaveLength;
	private final float gossipFactor;
	private final int peersInPrune;
	private final int fanoutTTL;

	private final Set<Host> peers; // peers with connection
	private final Set<Host> direct; //direct peers
	private final Map<String, Set<Host>> topics; //map of topics to which peers are subscribed to
	private final Set<String> subscriptions; // set of subscriptions
	private final Map<String, Set<Host>> mesh; // map of topic meshes (topic => set of peers)
	/* Map of topics to set of peers.
	 * These mesh peers are the ones to which self is publishing without a topic membership (topic => set of peers) */
	private final Map<String, Set<Host>> fanout;
	private final Map<String, Long> fanoutLastPub; // map of last publish time for fanout topics (topic => last publish time)
	private final Map<Host, IHave> pendingGossip; // map of pending messages to gossip (host => IHave messages)
	private final Map<String, Set<PublishMessage>> pendingPublishes; // map of pending publish messages
	private final MessageCache messageCache; // cache that contains the messages for last few heartbeat ticks
	private final Set<UUID> seenMessages; // set of ids of seen messages (maybe turn to cache)

	private static final Logger logger = LogManager.getLogger(GossipSub.class);

	public static final short ID = 500;
	public static final String NAME = "GossipSub";


	public GossipSub(Properties props, Host self) throws HandlerRegistrationException, IOException {
		super(NAME, ID);

		//-------------------------Initialize fields--------------------------------
		this.self = self;

		this.heartbeatInterval = Integer.parseInt(props.getProperty("hbInterval", "1000"));
		this.heartbeatInitialDelay = Integer.parseInt(props.getProperty("hbDelay", "100"));

		this.degree = Integer.parseInt(props.getProperty("D", "6"));
		this.degreeLow = Integer.parseInt(props.getProperty("Dlo", "4"));
		this.degreeHigh = Integer.parseInt(props.getProperty("Dhi", "12"));
		this.degreeLazy = Integer.parseInt(props.getProperty("Dlazy", "6"));
		this.maxIHaveLength = Integer.parseInt(props.getProperty("MaxIHaveLength", "5000"));
		this.gossipFactor = Float.parseFloat(props.getProperty("GossipFactor", "0.25"));
		this.peersInPrune = Integer.parseInt(props.getProperty("PrunePeers", "16"));
		this.fanoutTTL = Integer.parseInt(props.getProperty("FanoutTTL", "60000"));

		this.peers = new HashSet<>();
		this.direct = new HashSet<>();
		this.topics = new HashMap<>();
		this.subscriptions = new HashSet<>();
		this.mesh = new HashMap<>();
		this.fanout = new HashMap<>();
		this.fanoutLastPub = new HashMap<>();
		this.pendingGossip = new HashMap<>();
		this.pendingPublishes = new HashMap<>();

		int historyLength = Integer.parseInt(props.getProperty("HistoryLength", "5"));
		int historyGossip = Integer.parseInt(props.getProperty("HistoryGossip", "3"));
		this.messageCache = new MessageCache(historyGossip, historyLength);
		this.seenMessages = new HashSet<>();


		/*-------------------- Register Request Events ------------------------------- */
		this.registerRequestHandler(SubscriptionRequest.REQUEST_ID, this::uponSubscriptionRequest);
		this.registerRequestHandler(PublishRequest.REQUEST_ID, this::uponPublishRequest);
		this.registerRequestHandler(UnsubscriptionRequest.REQUEST_ID, this::uponUnsubscriptionRequest);

		/*-------------------- Register Reply Events ------------------------------- */
		this.registerReplyHandler(JoinSwarmReply.ID, this::onJoinSwarmReply);
		this.registerReplyHandler(FindSwarmReply.ID, this::onFindSwarmReply);

		/*-------------------- Register Timer Events ------------------------------- */
		this.registerTimerHandler(HeartbeatTimer.ID, this::onHeartbeat);
		this.registerTimerHandler(InfoTimer.ID, this::onInfoTimer);

		this.subscribeNotification(ChannelCreatedNotification.ID, this::onChannelCreated);
	}

	private void onChannelCreated(ChannelCreatedNotification notification, short protoID) {
		this.channelId = notification.channel_id;
		registerSharedChannel(channelId);

		try {
			/*---------------------- Register Message Handlers -------------------------- */
			this.registerMessageHandler(this.channelId, Graft.ID, this::uponGraft);
			this.registerMessageHandler(this.channelId, IHave.ID, this::uponIHave);
			this.registerMessageHandler(this.channelId, IWant.ID, this::uponIWant);
			this.registerMessageHandler(this.channelId, Prune.ID, this::uponPrune);
			this.registerMessageHandler(this.channelId, PublishMessage.ID, this::uponPublishMessage);
			this.registerMessageHandler(this.channelId, SubscribeMessage.ID, this::uponSubscribeMessage);
			this.registerMessageHandler(this.channelId, UnsubscribeMessage.ID, this::uponUnsubscribeMessage);

			/*---------------------- Register Message Serializers -------------------------- */
			this.registerMessageSerializer(this.channelId, Graft.ID, Graft.serializer);
			this.registerMessageSerializer(this.channelId, IHave.ID, IHave.serializer);
			this.registerMessageSerializer(this.channelId, IWant.ID, IWant.serializer);
			this.registerMessageSerializer(this.channelId, Prune.ID, Prune.serializer);
			this.registerMessageSerializer(this.channelId, PublishMessage.ID, PublishMessage.serializer);
			this.registerMessageSerializer(this.channelId, SubscribeMessage.ID, SubscribeMessage.serializer);
			this.registerMessageSerializer(this.channelId, UnsubscribeMessage.ID, UnsubscribeMessage.serializer);

			/*-------------------- Register Channel Events ------------------------------- */
			this.registerChannelEventHandler(this.channelId, OutConnectionDown.EVENT_ID, this::onOutConnectionDown);
			this.registerChannelEventHandler(this.channelId, OutConnectionFailed.EVENT_ID, this::onOutConnectionFailed);
			this.registerChannelEventHandler(this.channelId, OutConnectionUp.EVENT_ID, this::onOutConnectionUp);
			this.registerChannelEventHandler(this.channelId, InConnectionUp.EVENT_ID, this::onInConnectionUp);
			this.registerChannelEventHandler(this.channelId, InConnectionDown.EVENT_ID, this::onInConnectionDown);
		} catch (HandlerRegistrationException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void init(Properties properties) throws HandlerRegistrationException, IOException {
		logger.info("GossipSub starting");

		//TODO init direct peers ?

		setupPeriodicTimer(new HeartbeatTimer(), heartbeatInitialDelay, heartbeatInterval);
		setupPeriodicTimer(new InfoTimer(), 5000, 5000);
	}

	/*--------------------------------- Request Handlers ---------------------------------------- */

	private void uponUnsubscriptionRequest(UnsubscriptionRequest unsub, short sourceProto) {
		if (channelId == -1) return;

		var topic = unsub.getTopic();

		var wasSubscribed = this.subscriptions.remove(topic);
		logger.trace("unsubscribe from {} - am subscribed {}", topic, wasSubscribed);
		if (wasSubscribed) {
			for (var peer : this.peers) {
				this.sendSubscriptions(peer, Set.of(topic), false);
			}
		}
		leave(topic);
	}


	private void uponPublishRequest(PublishRequest publish, short sourceProto) {
		if (channelId == -1) return;

		var msgId = publish.getMsgID();
		var topic = publish.getTopic();

		if (seenMessages.contains(msgId)) {
			logger.error("Duplicate message");
			return;
		}
		var publishMessage = new PublishMessage(self, topic, msgId, publish.getMessage());
		var toSend = selectPeersToPublish(topic);
		if (toSend.isEmpty()) {
			logger.error("No peers to publish :(, trying to find with Kademlia");
			pendingPublishes.computeIfAbsent(topic, k -> new HashSet<>());
			pendingPublishes.get(topic).add(publishMessage);
			sendRequest(new FindSwarm(topic, degree), Kademlia.ID);
			return;
		}
		logger.trace("publish message {} to topic {}", msgId, topic);
		seenMessages.add(msgId);
		messageCache.put(publishMessage);
		for (var peer : toSend) {
			sendMessage(publishMessage, peer);
		}
		sendReply(new PublishReply(topic, msgId), AutomatedApp.PROTO_ID);
	}

	private void uponSubscriptionRequest(SubscriptionRequest sub, short sourceProto) {
		if (channelId == -1) return;

		var topic = sub.getTopic();

		logger.trace("subscribe to topic {}", sub.getTopic());
		if (subscriptions.add(topic)) {
			for (var peer : this.peers) {
				sendSubscriptions(peer, Set.of(topic), true);
			}
		}
		join(topic);
		sendReply(new SubscriptionReply(topic), AutomatedApp.PROTO_ID);
	}

	private void onJoinSwarmReply(JoinSwarmReply reply, short protoID) {
		Set<Host> swarmPeers = new HashSet<>(reply.peers.stream().map(kp -> kp.host).toList());
		var topic = reply.swarm;

		this.mesh.put(topic, swarmPeers);

		for (var peer : swarmPeers) {
			logger.trace("JOIN: Add mesh link to {} in {}", peer, topic);
			sendMessage(new Graft(Set.of(topic)), peer);
		}
	}

	private void onFindSwarmReply(FindSwarmReply reply, short protoID) {
		Set<Host> swarmPeers = new HashSet<>(reply.peers.stream().map(kp -> kp.host).toList());
		var topic = reply.swarm;
		if (!swarmPeers.isEmpty()) {
			var publishMessages = pendingPublishes.get(topic);

			for (var publishMessage : publishMessages) {
				logger.trace("publish message {} to topic {}", publishMessage.getMsgId(), topic);
				seenMessages.add(publishMessage.getMsgId());
				messageCache.put(publishMessage);
				for (var peer : swarmPeers) {
					sendMessage(publishMessage, peer);
				}
			}
		}
		pendingPublishes.remove(topic);
	}


	/*--------------------------------- Timer Handlers ---------------------------------------- */

	private void onHeartbeat(HeartbeatTimer timer, long timerId) {
		Map<Host, Set<String>> toGraft = new HashMap<>();
		Map<Host, Set<String>> toPrune = new HashMap<>();

		Map<String, Set<Host>> peersToGossipByTopic = new HashMap<>();

		// maintain the mesh for topics we have joined
		for (var meshEntry : mesh.entrySet()) {
			var topic = meshEntry.getKey();
			var meshPeers = meshEntry.getValue();

			var peersInTopic = this.topics.get(topic);
			Set<Host> candidateMeshPeers = new HashSet<>();
			Set<Host> peersToGossip = new HashSet<>();
			peersToGossipByTopic.put(topic, peersToGossip);

			if (peersInTopic != null && !peersInTopic.isEmpty()) {
				for (var peer : peersInTopic) {
					if (!meshPeers.contains(peer) && !this.direct.contains(peer)) {
						candidateMeshPeers.add(peer);
						// instead of having to find gossip peers after heartbeat which require another loop
						// we prepare peers to gossip in a topic within heartbeat to improve performance
						peersToGossip.add(peer);
					}
				}
			}
			// not enough peers
			if (meshPeers.size() < degreeLow) {
				var iNeed = degree - meshPeers.size();
				var newMeshPeers = sample(iNeed, candidateMeshPeers);
				for (var peer : newMeshPeers) {
					graftPeer(peer, topic, meshPeers, peersToGossip, toGraft);
				}
			}
			// too much peers
			if (meshPeers.size() > degreeHigh) {
				var excess = meshPeers.size() - degree;
				var someMeshPeers = sample(excess, meshPeers);
				for (var peer : someMeshPeers) {
					prunePeer(peer, topic, meshPeers, toPrune);
				}
			}
		}
		// expire fanout for topics we haven't published to in a while
		fanoutLastPub.entrySet().removeIf((entry) -> {
			var topic = entry.getKey();
			var lastPubTime = entry.getValue();
			if (lastPubTime + fanoutTTL < getMillisSinceBabelStart()) {
				fanout.remove(topic);
				return true;
			} else
				return false;
		});
		// maintain our fanout for topics we are publishing, but we have not joined
		for (var entry : fanout.entrySet()) {
			var topic = entry.getKey();
			var fanoutPeers = entry.getValue();
			var peersInTopic = topics.get(topic);
			// checks whether our peers are still in the topic
			fanoutPeers.removeIf((peer) -> peersInTopic == null || !peersInTopic.contains(peer));
			Set<Host> candidateFanoutPeers = new HashSet<>();
			Set<Host> peersToGossip = new HashSet<>();
			peersToGossipByTopic.put(topic, peersToGossip);

			if (peersInTopic != null && !peersInTopic.isEmpty()) {
				for (var peer : peersInTopic) {
					if (!fanoutPeers.contains(peer) && !this.direct.contains(peer)) {
						candidateFanoutPeers.add(peer);
						// instead of having to find gossip peers after heartbeat which require another loop
						// we prepare peers to gossip in a topic within heartbeat to improve performance
						peersToGossip.add(peer);
					}
				}
			}
			// do we need more peers?
			if (fanoutPeers.size() < degree) {
				var iNeed = degree - fanoutPeers.size();
				var newFanoutPeers = sample(iNeed, candidateFanoutPeers);
				for (var peer : newFanoutPeers) {
					fanoutPeers.add(peer);
					peersToGossip.remove(peer);
				}
			}
		}
		emitGossip(peersToGossipByTopic);
		// send GRAFT and PRUNE messages (no piggyback)
		sendGrafts(toGraft);
		sendPrunes(toPrune);
		// flush all gossip (IWANTs) (not piggybacking here)
		flush();
		// advance the message history window
		messageCache.shift();
	}

	private void onInfoTimer(InfoTimer timer, long timerId) {
		logger.debug("subscriptions:\n{}", subscriptions);
		logger.debug("peers with connection:\n{}", peers);
		logger.debug("topics:\n{}", topics);
		logger.debug("mesh:\n{}", mesh);
		logger.debug("fanout:\n{}", fanout);
		logger.debug("seen messages:\n{}", seenMessages);
	}

	/*--------------------------------- Message Handlers ---------------------------------------- */

	private void uponSubscribeMessage(SubscribeMessage subscribe, Host from, short sourceProto, int channelId) {
		String topic = subscribe.getTopic();
		logger.trace("subscription add from {} topic {}", from, topic);
		var topicsSet = this.topics.computeIfAbsent(topic, k -> new HashSet<>());
		topicsSet.add(from);
	}

	private void uponUnsubscribeMessage(UnsubscribeMessage unsubscribe, Host from, short sourceProto, int channelId) {
		String topic = unsubscribe.getTopic();
		logger.trace("subscription delete from {} topic {}", from, topic);
		var topicsSet = this.topics.computeIfAbsent(topic, k -> new HashSet<>());
		topicsSet.remove(from);
	}

	private void uponPublishMessage(PublishMessage publish, Host from, short sourceProto, int channelId) {
		var msgId = publish.getMsgId();

		if (seenMessages.add(msgId)) {
			messageCache.put(publish);
			deliverMessage(publish);
			forwardMessage(publish);
		}
	}

	private void uponIWant(IWant iWant, Host from, short sourceProto, int channelId) {
		Set<PublishMessage> toSend = new HashSet<>();
		for (UUID msgId : iWant.getMessageIds()) {
			var msg = messageCache.get(msgId);
			if (msg == null)
				continue;
			toSend.add(msg);
		}

		if (toSend.isEmpty()) {
			logger.trace("IWANT: Could not provide any wanted messages to {}", from);
			return;
		} else
			logger.trace("IWANT: Sending {} messages to {}", toSend.size(), from);

		for (var message : toSend) {
			sendMessage(message, from);
		}
	}

	private void uponIHave(IHave iHave, Host from, short sourceProto, int channelId) {
		Set<UUID> iWant = new HashSet<>();
		var msgIdsPerTopic = iHave.getMsgIdsPerTopic();
		for (var topic : msgIdsPerTopic.keySet()) {
			var msgIds = msgIdsPerTopic.get(topic);
			if (msgIds == null || msgIds.isEmpty() || !mesh.containsKey(topic))
				continue;
			for (UUID msgId : msgIds) {
				if (!seenMessages.contains(msgId)) {
					iWant.add(msgId);
				}
			}
		}
		if (iWant.isEmpty())
			return;

		var iAsk = Math.min(iWant.size(), maxIHaveLength);
		if(iAsk > iWant.size()) {
			iWant = sample(iAsk, iWant);
		}
		logger.trace("IHAVE: Asking for {} out of {} messages from {}", iAsk, iWant.size(), from);
		sendMessage(new IWant(iWant), from);
	}

	private void uponGraft(Graft graft, Host from, short sourceProto, int channelId) {
		//shouldn't happen
		if (this.direct.contains(from)) {
			logger.error("GRAFT from direct peer");
			return;
		}

		for (var topic : graft.getTopics()) {
			var peersInMesh = mesh.get(topic);
			if (peersInMesh == null || peersInMesh.isEmpty())
				continue;
			if (peersInMesh.contains(from))
				continue;
			logger.trace("GRAFT: Add mesh link from {} in {}", from, topic);
			peersInMesh.add(from);
		}
	}

	private void uponPrune(Prune prune, Host from, short sourceProto, int channelId) {
		for (var entry : prune.getPeersPerTopic().entrySet()) {
			var topic = entry.getKey();
			var peersPX = entry.getValue();

			var peersInMesh = mesh.get(topic);
			if (peersInMesh == null || peersInMesh.isEmpty())
				return;

			logger.trace("PRUNE: Remove mesh link to {} in {}", from, topic);
			peersInMesh.remove(from);

			// PX TODO: not sure this is correct
			for (var peer : peersPX) {
				if (!this.peers.contains(peer))
					openConnection(peer);
			}
		}
	}

	/*--------------------------------- Channel Event Handlers ---------------------------------------- */

	private void onInConnectionDown(InConnectionDown event, int channelId) {
		var peer = event.getNode();
		removePeer(peer);
	}

	private void onInConnectionUp(InConnectionUp event, int channelId) {
		var peer = event.getNode();
		addPeer(peer);
		openConnection(peer);
	}

	private void onOutConnectionUp(OutConnectionUp event, int channelId) {
		var peer = event.getNode();
		addPeer(peer);
		sendSubscriptions(peer, subscriptions, true);
	}

	private void onOutConnectionFailed(OutConnectionFailed<ProtoMessage> event, int channelId) {
		var peer = event.getNode();
		removePeer(peer);
	}

	private void onOutConnectionDown(OutConnectionDown event, int channelId) {
		var peer = event.getNode();
		removePeer(peer);
	}

	/*--------------------------------- Helpers ---------------------------------------- */

	private void sendSubscriptions(Host peer, Set<String> topics, boolean subscribe) {
		for (var topic : topics) {
			if (subscribe)
				sendMessage(new SubscribeMessage(topic), peer);
			else
				sendMessage(new UnsubscribeMessage(topic), peer);
		}
	}

	private void deliverMessage(PublishMessage publish) {
		var topic = publish.getTopic();
		var source = publish.getPropagationSource();
		if (subscriptions.contains(topic) && !self.equals(source)) {
			triggerNotification(new DeliverNotification(topic, publish.getMsgId(), source, publish.getMsg()));
		}
	}

	private void forwardMessage(PublishMessage publish) {
		var topic = publish.getTopic();
		var source = publish.getPropagationSource();
		var toSend = selectPeersToForward(topic, source);
		for (var peer : toSend) {
			sendMessage(publish, peer);
		}
	}

	private Set<Host> selectPeersToForward(String topic, Host source) {
		Set<Host> toSend = new HashSet<>();

		var peersInTopic = topics.get(topic);
		for (var peer : this.direct) {
			if (peersInTopic.contains(peer) && !source.equals(peer)) {
				toSend.add(peer);
			}
		}
		return toSend;
	}

	private void join(String topic) {
		if (this.mesh.containsKey(topic))
			return;

		logger.trace("JOIN {}", topic);

		Set<Host> toAdd = new HashSet<>();

		// check if we have mesh_n peers in fanout[topic] and add them to the mesh if we do,
		// removing the fanout entry.
		var fanoutPeers = fanout.get(topic);
		if (fanoutPeers != null && fanoutPeers.isEmpty()) {
			// Remove fanout entry and the last published time
			fanout.remove(topic);
			fanoutLastPub.remove(topic);

			for (var peer : fanoutPeers) {
				if (!this.direct.contains(peer))
					toAdd.add(peer);
			}
		}

		// check if we need to get more peers, which we randomly select
		if (toAdd.size() < degree) {
			//exclude = toAdd U direct
			Set<Host> exclude = new HashSet<>(toAdd);
			exclude.addAll(this.direct);

			var newPeers = getRandomGossipPeers(topic, degree - toAdd.size(), exclude);
			toAdd.addAll(newPeers);
		}

		// if not enough peers in toAdd, get peers from Kademlia
		if (toAdd.size() < degree)
			sendRequest(new JoinSwarm(topic, degree - toAdd.size()), Kademlia.ID);

		this.mesh.put(topic, toAdd);

		for (var peer : toAdd) {
			logger.trace("JOIN: Add mesh link to {} in {}", peer, topic);
			sendMessage(new Graft(Set.of(topic)), peer);
		}
	}

	private Set<Host> getRandomGossipPeers(String topic, int count, Set<Host> exclude) {
		var peersInTopic = this.topics.get(topic);

		if (peersInTopic == null || peersInTopic.isEmpty()) {
			return new HashSet<>();
		}

		Set<Host> peersToReturn = new HashSet<>();
		for (var peer : peersInTopic) {
			if (exclude == null || !exclude.contains(peer))
				peersToReturn.add(peer);
		}

		return sample(count, peersToReturn);
	}

	private void leave(String topic) {
		logger.trace("LEAVE {}", topic);

		// Send PRUNE to mesh peers
		var meshPeers = this.mesh.get(topic);
		if (meshPeers != null && !meshPeers.isEmpty()) {
			for (var peer : meshPeers) {
				logger.trace("LEAVE: Remove mesh link to {} in {}", peer, topic);
				sendMessage(makePrune(peer, topic), peer);
			}
			this.mesh.remove(topic);
		}
	}

	private Prune makePrune(Host sendTo, String topic) {
		var peersWithPrune = getRandomGossipPeers(topic, peersInPrune, Set.of(sendTo));

		return new Prune(Map.of(topic, peersWithPrune));
	}

	private Set<Host> selectPeersToPublish(String topic) {
		Set<Host> toSend = new HashSet<>();

		var peersInTopic = topics.get(topic);
		if (peersInTopic != null && !peersInTopic.isEmpty()) {
			// send to direct peers and some mesh peers above publishThreshold

			// direct peers (if subscribed)
			for (var peer : this.direct) {
				if (peersInTopic.contains(peer)) {
					toSend.add(peer);
				}
			}

			// GossipSub peers handling
			var meshPeers = this.mesh.get(topic);
			if (meshPeers != null) {
				toSend.addAll(meshPeers);
			} else { // not in the mesh for topic, use fanout peers
				var fanoutPeers = this.fanout.get(topic);
				if (fanoutPeers != null && !fanoutPeers.isEmpty()) {
					toSend.addAll(fanoutPeers);
					// no fanout peers, select degree of them and add them to the fanout, picking
					// peers in topic above the publishThreshold
				} else {
					var newFanoutPeers = getRandomGossipPeers(topic, degree, null);
					if (!newFanoutPeers.isEmpty()) {
						fanout.put(topic, newFanoutPeers);
						toSend.addAll(newFanoutPeers);
					}
				}
				fanoutLastPub.put(topic, this.getMillisSinceBabelStart());
			}
		}

		return toSend;
	}

	private void addPeer(Host peer) {
		if (this.peers.add(peer)) {
			logger.trace("new peer {}", peer);
		}
	}

	private void removePeer(Host peer) {
		if (this.peers.remove(peer)) {
			//closeConnection(peer);
			// remove peer from topics map
			for (var topicPeers : topics.values()) {
				topicPeers.remove(peer);
			}
			// Remove this peer from the mesh
			for (var meshPeers : mesh.values()) {
				meshPeers.remove(peer);
			}
			// Remove this peer from the fanout
			for (var fanoutPeers : fanout.values()) {
				fanoutPeers.remove(peer);
			}
			pendingGossip.remove(peer);

			logger.trace("deleted peer {}", peer);
		}
	}

	private void prunePeer(Host peer, String topic, Set<Host> meshPeers, Map<Host, Set<String>> toPrune) {
		logger.trace("HEARTBEAT: Remove mesh link to {} in {}", peer, topic);
		// remove peer from mesh
		meshPeers.remove(peer);
		// add to toPrune
		toPrune.computeIfAbsent(peer, k -> new HashSet<>());
		toPrune.get(peer).add(topic);
	}

	private void graftPeer(Host peer, String topic, Set<Host> peerMesh, Set<Host> peersToGossip, Map<Host, Set<String>> toGraft) {
		logger.trace("HEARTBEAT: Add mesh link to {} in {}", peer, topic);
		// add peer to mesh
		peerMesh.add(peer);
		// when we add a new mesh peer, we don't want to gossip messages to it
		peersToGossip.remove(peer);
		// add to toGraft
		toGraft.computeIfAbsent(peer, k -> new HashSet<>());
		toGraft.get(peer).add(topic);
	}

	private void emitGossip(Map<String, Set<Host>> peersToGossipByTopic) {
		var msgIdsByTopic = messageCache.getMessageIDsByTopic(peersToGossipByTopic.keySet());
		for (var entry : peersToGossipByTopic.entrySet()) {
			var topic = entry.getKey();
			var peersToGossip = entry.getValue();
			var msgIds = msgIdsByTopic.get(topic);
			if (msgIds != null)
				doEmitGossip(topic, peersToGossip, msgIdsByTopic.get(topic));
		}
	}

	/**
	 * Send gossip messages to GossipFactor peers above threshold with a minimum of D_lazy
	 * Peers are randomly selected from the heartbeat which exclude mesh + fanout peers
	 * We also exclude direct peers, as there is no reason to emit gossip to them
	 *
	 * @param topic - topic to gossip
	 * @param candidatesToGossip - peers to gossip
	 * @param msgIds - message ids to gossip
	 */
	private void doEmitGossip(String topic, Set<Host> candidatesToGossip, Set<UUID> msgIds) {
		// Emit the IHAVE gossip to the selected peers
		if (candidatesToGossip.isEmpty()) return;
		var target = degreeLazy;
		var factor = gossipFactor * candidatesToGossip.size();
		Set<Host> peersToGossip = new HashSet<>(candidatesToGossip);
		if (factor > target) target = Math.round(factor);
		if (target <= candidatesToGossip.size())
			peersToGossip = sample(target, peersToGossip);

		for (var peer : peersToGossip) {
			Set<UUID> peerMsgIds = new HashSet<>(msgIds);
			if (msgIds.size() > maxIHaveLength)
				peerMsgIds = sample(maxIHaveLength, peerMsgIds);
			pushGossip(peer, topic, peerMsgIds);
		}
	}

	/**
	 * Adds new IHAVE messages to pending gossip
	 */
	private void pushGossip(Host peer, String topic, Set<UUID> msgIds) {
		logger.trace("Add gossip to {}", peer);
		pendingGossip.computeIfAbsent(peer, k -> new IHave());
		var iHave = this.pendingGossip.get(peer);
		iHave.put(topic, msgIds);
	}

	private void sendGrafts(Map<Host, Set<String>> toGraft) {
		for (var entry : toGraft.entrySet()) {
			var peer = entry.getKey();
			var topics = entry.getValue();
			var graft = new Graft(topics);
			sendMessage(graft, peer);
		}
	}

	private void sendPrunes(Map<Host, Set<String>> toPrune) {
		for (var entry : toPrune.entrySet()) {
			var peer = entry.getKey();
			var topics = entry.getValue();
			var prune = new Prune(new HashMap<>());
			for (var topic : topics) {
				prune.append(makePrune(peer, topic));
			}
			sendMessage(prune, peer);
		}
	}

	/**
	 * Flush gossip messages
	 */
	private void flush() {
		for (var entry : pendingGossip.entrySet()) {
			var peer = entry.getKey();
			var iHave = entry.getValue();
			sendMessage(iHave, peer);
		}
		pendingGossip.clear();
	}
}
