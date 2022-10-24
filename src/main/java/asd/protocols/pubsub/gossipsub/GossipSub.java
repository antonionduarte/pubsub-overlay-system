package asd.protocols.pubsub.gossipsub;

import asd.protocols.pubsub.common.DeliverNotification;
import asd.protocols.pubsub.common.PublishRequest;
import asd.protocols.pubsub.common.SubscriptionRequest;
import asd.protocols.pubsub.common.UnsubscriptionRequest;
import asd.protocols.pubsub.gossipsub.messages.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.*;
import pt.unl.fct.di.novasys.network.data.Host;

import javax.sql.rowset.FilteredRowSet;
import java.io.IOException;
import java.util.*;

public class GossipSub extends GenericProtocol {

    private final Host self;
    private int channelId;

    private int heartbeatInterval;
    private int degree, degreeLow, degreeHigh;
    private int peersInPrune;
    private int ttl;
    private Set<Host> peers;
    private Set<Host> direct; //direct peers
    private Map<String, Set<Host>> topics; //map of topics to which peers are subscribed to
    private Set<String> subscriptions; // set of subscriptions
    private Map<String, Set<Host>> mesh; // map of topic meshes (topic => set of peers)
     /* Map of topics to set of peers.
     * These mesh peers are the ones to which self is publishing without a topic membership (topic => set of peers) */
    private Map<String, Set<Host>> fanout;
    private Map<String, Set<Host>> fanoutLastPub; // map of last publish time for fanout topics (topic => last publish time)
    private Map<Host, List<IHave>> gossip; // map of pending messages to gossip (host => list of IHave messages)
    private Map<UUID, PublishMessage> messageCache; // cache that contains the messages for last few heartbeat ticks
    private Set<UUID> seenMessages; // set of ids of seen messages (maybe turn to cache)

    private static final Logger logger = LogManager.getLogger(GossipSub.class);

    public static final short ID = 300;
    public static final String NAME = "GossipSub";

    public GossipSub(Properties props, Host self) throws HandlerRegistrationException, IOException {
        super(NAME, ID);

        //-------------------------Initialize fields--------------------------------
        this.self = self;

        // ------------------------Initialize channel properties-----------------------------
        Properties channelProps = new Properties();
        channelProps.setProperty(TCPChannel.ADDRESS_KEY, props.getProperty("babel_address")); // The address to bind to
        channelProps.setProperty(TCPChannel.PORT_KEY, props.getProperty("babel_port")); // The port to bind to
        channelProps.setProperty(TCPChannel.HEARTBEAT_INTERVAL_KEY, "1000"); // Heartbeats interval for established
        // connections
        channelProps.setProperty(TCPChannel.HEARTBEAT_TOLERANCE_KEY, "3000"); // Time passed without heartbeats until
        // closing a connection
        channelProps.setProperty(TCPChannel.CONNECT_TIMEOUT_KEY, "1000"); // TCP connect timeout
        this.channelId = createChannel(TCPChannel.NAME, channelProps);

        /*---------------------- Register Message Handlers -------------------------- */
        this.registerMessageHandler(this.channelId, Graft.ID, this::uponGraft);
        this.registerMessageHandler(this.channelId, IHave.ID, this::uponIHave);
        this.registerMessageHandler(this.channelId, IWant.ID, this::uponIWant);
        this.registerMessageHandler(this.channelId, Prune.ID, this::uponPrune);
        this.registerMessageHandler(this.channelId, PublishMessage.ID, this::uponPublishMessage);
        this.registerMessageHandler(this.channelId, SubscribeMessage.ID, this::uponSubscribeMessage);
        this.registerMessageHandler(this.channelId, UnsubscribeMessage.ID, this::uponUnsubscribeMessage);

        /*-------------------- Register Channel Event ------------------------------- */
        this.registerChannelEventHandler(this.channelId, OutConnectionDown.EVENT_ID, this::onOutConnectionDown);
        this.registerChannelEventHandler(this.channelId, OutConnectionFailed.EVENT_ID, this::onOutConnectionFailed);
        this.registerChannelEventHandler(this.channelId, OutConnectionUp.EVENT_ID, this::onOutConnectionUp);
        this.registerChannelEventHandler(this.channelId, InConnectionUp.EVENT_ID, this::onInConnectionUp);
        this.registerChannelEventHandler(this.channelId, InConnectionDown.EVENT_ID, this::onInConnectionDown);

        /*-------------------- Register Request Event ------------------------------- */
        this.registerRequestHandler(SubscriptionRequest.REQUEST_ID, this::uponSubscriptionRequest);
        this.registerRequestHandler(PublishRequest.REQUEST_ID, this::uponPublishRequest);
        this.registerRequestHandler(UnsubscriptionRequest.REQUEST_ID, this::uponUnsubscriptionRequest);

    }

    @Override
    public void init(Properties properties) throws HandlerRegistrationException, IOException {

    }

    /*--------------------------------- Request Handlers ---------------------------------------- */

    private void uponUnsubscriptionRequest(UnsubscriptionRequest unsub, short sourceProto) {
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
        var msgId = publish.getMsgID();
        var topic = publish.getTopic();

        if(seenMessages.contains(msgId)) {
            logger.error("Duplicate message");
            return;
        }

        var toSend = selectPeersToPublish(topic);
    }

    private void uponSubscriptionRequest(SubscriptionRequest sub, short sourceProto) {
        var topic = sub.getTopic();

        if (!subscriptions.contains(topic)) {
            subscriptions.add(topic);

            for (var peer : peers) {
                sendSubscriptions(peer, Set.of(topic), true);
            }
        }
        join(topic);
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

        if (!seenMessages.contains(msgId)) {
            seenMessages.add(msgId);
            messageCache.put(msgId, publish);
            deliverMessage(publish);
            forwardMessage(publish);
        }
    }

    private void uponIWant(IWant iWant, Host from, short sourceProto, int channelId) {
        Set<PublishMessage> toSend = new HashSet<>();
        for (UUID msgId: iWant.getMessageIds()) {
            if(!messageCache.containsKey(msgId))
                continue;
            toSend.add(messageCache.get(msgId));
        }

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
                if(!seenMessages.contains(msgId)) {
                    iWant.add(msgId);
                }
            }
        }
        if (iWant.isEmpty())
            return;

        // maybe later limit iWants to send here
        sendMessage(new IWant(iWant), from);
    }

    private void uponGraft(Graft graft, Host from, short sourceProto, int channelId) {
        //shouldn't happen
        if (direct.contains(from))  {
            logger.error("GRAFT from direct peer");
            return;
        }

        for (var topic : graft.getTopics()) {
            var peersInMesh = mesh.get(topic);
            if (peersInMesh == null || peersInMesh.isEmpty())
                continue;

            if (peersInMesh.contains(from))
                continue;

            peersInMesh.add(from);
        }
    }

    private void uponPrune(Prune prune, Host from, short sourceProto, int channelId) {
        var topic  = prune.getTopic();

        var peersInMesh = mesh.get(topic);
        if (peersInMesh == null || peersInMesh.isEmpty())
            return;

        peersInMesh.remove(from);

        // PX
        for (var peer : prune.getPeers()) {
            if (!this.peers.contains(peer))
                openConnection(peer);
        }
    }

    /*--------------------------------- Channel Event Handlers ---------------------------------------- */

    private void onInConnectionDown(InConnectionDown event, int channelId) {

    }

    private void onInConnectionUp(InConnectionUp event, int channelId) {

    }

    private void onOutConnectionUp(OutConnectionUp event, int channelId) {
    }

    private void onOutConnectionFailed(OutConnectionFailed<ProtoMessage> event, int channelId) {

    }

    private void onOutConnectionDown(OutConnectionDown event, int channelId) {
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
        for (Host peer : toSend) {
            sendMessage(publish, peer);
        }
    }

    private Set<Host> selectPeersToForward(String topic, Host source) {
        Set<Host> toSend = new HashSet<>();

        var peersInTopic = topics.get(topic);
        for (Host directPeer : direct) {
            if (peersInTopic.contains(directPeer) && !source.equals(directPeer)) {
                toSend.add(directPeer);
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
        var fanoutPeers = this.fanout.get(topic);
        if (fanoutPeers != null && fanoutPeers.isEmpty()) {
            // Remove fanout entry and the last published time
            this.fanout.remove(topic);
            //this.fanoutLastpub.delete(topic)

            for (var peer : fanoutPeers) {
                if (!direct.contains(peer))
                    toAdd.add(peer);
            }
        }

        // check if we need to get more peers, which we randomly select
        if (toAdd.size() < degree) {
            //exclude = toAdd U direct
            Set<Host> exclude = new HashSet<>(toAdd);
            exclude.addAll(direct);

            var newPeers = getRandomGossipPeers(topic, degree - toAdd.size(), exclude);
            toAdd.addAll(newPeers);
        }

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
            if(!exclude.contains(peer) && !this.direct.contains(peer))
                peersToReturn.add(peer);
        }

        return peersToReturn; //TODO: Sample with count elements of this set
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

        return new Prune(topic, peersWithPrune);
    }

    private Set<Host> selectPeersToPublish(String topic) {
        Set<Host> toSend = new HashSet<>();

        var peersInTopic = topics.get(topic);
        if (peersInTopic != null && !peersInTopic.isEmpty()) {
            // send to direct peers and some mesh peers above publishThreshold

            // direct peers (if subscribed)
            for (var peer : direct) {
                if (peersInTopic.contains(peer)) {
                    toSend.add(peer);
                }
            }

            // GossipSub peers handling
            var meshPeers = this.mesh.get(topic);
            if (meshPeers != null && !meshPeers.isEmpty()) {
                toSend.addAll(meshPeers);
            } else { // not in the mesh for topic, use fanout peers
                var fanoutPeers = this.fanout.get(topic);
                if (fanoutPeers != null && !fanoutPeers.isEmpty()) {
                    toSend.addAll(fanoutPeers);
                } else {

                }

            }


        }
    }
}
