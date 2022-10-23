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
import pt.unl.fct.di.novasys.channel.tcp.TCPChannel;
import pt.unl.fct.di.novasys.channel.tcp.events.*;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.*;

public class GossipSub extends GenericProtocol {

    private final Host self;
    private int channelId;

    private int heartbeatInterval;
    private int degree, degreeLow, degreeHigh;
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

    private void uponUnsubscriptionRequest(V v, short sourceProto) {

    }

    private void uponPublishRequest(PublishRequest publish, short sourceProto) {

    }

    private void uponSubscriptionRequest(V v, short sourceProto) {
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

    private void uponPrune(Prune v, Host host, short sourceProto, int channelId) {

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
        boolean doPX;
        Set<String> prune = new HashSet<>(); //topics to prune

        for (var topic : graft.getTopics()) {
            var peersInMesh = mesh.get(topic);
            if (peersInMesh == null || peersInMesh.isEmpty()) {
                doPX = false;
                continue;
            }

            if (peersInMesh.contains(from))
                continue;

            if (direct.contains(from)) {

            }
        }
    }

    /*--------------------------------- Channel Event Handlers ---------------------------------------- */

    private void onInConnectionDown(InConnectionDown event, int channelId) {

    }

    private void onInConnectionUp(InConnectionUp event, int channelId) {

    }

    private void onOutConnectionUp(V v, int i) {
    }

    private void onOutConnectionFailed(V v, int i) {
    }

    private void onOutConnectionDown(V v, int i) {
    }
}
