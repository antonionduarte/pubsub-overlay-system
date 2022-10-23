package asd.protocols.apps;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Random;

import asd.protocols.apps.timers.DisseminationTimer;
import asd.protocols.apps.timers.ExitTimer;
import asd.protocols.apps.timers.StartTimer;
import asd.protocols.apps.timers.StopTimer;
import asd.protocols.pubsub.common.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;

public class AutomatedApp extends GenericProtocol {
    private static final Logger logger = LogManager.getLogger(AutomatedApp.class);

    //Protocol information, to register in babel
    public static final String PROTO_NAME = "AutomatedPubSubApp";
    public static final short PROTO_ID = 300;

    private final short pubSubProtoId;

    //Size of the payload of each message (in bytes)
    private final int payloadSize;
    //Time to wait until starting sending messages
    private final int prepareTime;
    //Time to run before shutting down
    private final int runTime;
    //Time to wait until starting sending messages
    private final int cooldownTime;
    //Interval between each broadcast
    private final int disseminationInterval;
    //Number of different topics to be generated;
    private final int nTopics;
    //Number of topics to subscribe
    private final int topicsToSubscribe;
    //Number of topics to publish to
    private final int topicsToPublish;
    
    //random seed for topic generation
    private final int randomSeed;

    private final Host self;

    private long broadCastTimer;
    
    private ArrayList<String> topics;
    private ArrayList<String> publishTopics;
    private ArrayList<String> subscribeTopics;
    
    private Random r;
    

    public AutomatedApp(Host self, Properties properties, short pubSubProtoId) throws HandlerRegistrationException {
        super(PROTO_NAME, PROTO_ID);
        this.pubSubProtoId = pubSubProtoId;
        this.self = self;

        //Read configurations
        this.payloadSize = Integer.parseInt(properties.getProperty("payload_size"));
        this.prepareTime = Integer.parseInt(properties.getProperty("prepare_time")); //in seconds
        this.cooldownTime = Integer.parseInt(properties.getProperty("cooldown_time")); //in seconds
        this.runTime = Integer.parseInt(properties.getProperty("run_time")); //in seconds
        this.disseminationInterval = Integer.parseInt(properties.getProperty("broadcast_interval")); //in milliseconds
        this.nTopics = Integer.parseInt(properties.getProperty("n_topics")); 
        this.topicsToSubscribe = Integer.parseInt(properties.getProperty("sub_topics"));
        this.topicsToPublish = Integer.parseInt(properties.getProperty("pub_topics"));
        this.randomSeed = Integer.parseInt(properties.getProperty("random_seed","10000"));
        
        this.topics = new ArrayList<String>(nTopics);
        this.publishTopics = new ArrayList<String>();
        this.subscribeTopics = new ArrayList<String>();
        
        //Generate Topics
        r = new Random(this.randomSeed);
        for(int i = 0; i < this.nTopics; i++) {
        	this.topics.add("Topic_" + r.nextInt());
        }
        
        r = new Random(new HashProducer(self).hash());
        
        ArrayList<String> temp = new ArrayList<String>(this.topics);
        
        for(int i = 0; i < this.topicsToPublish; i++) {
        	this.publishTopics.add(temp.remove(r.nextInt(temp.size())));
        }
        
        temp = new ArrayList<String>(this.topics);
        
        for(int i = 0; i < this.topicsToSubscribe; i++) {
        	this.subscribeTopics.add(temp.remove(r.nextInt(temp.size())));
        }
        
        //Setup handlers
        subscribeNotification(DeliverNotification.NOTIFICATION_ID, this::uponDeliver);
        
        registerTimerHandler(DisseminationTimer.TIMER_ID, this::uponBroadcastTimer);
        registerTimerHandler(StartTimer.TIMER_ID, this::uponStartTimer);
        registerTimerHandler(StopTimer.TIMER_ID, this::uponStopTimer);
        registerTimerHandler(ExitTimer.TIMER_ID, this::uponExitTimer);
        
        registerReplyHandler(SubscriptionReply.REQUEST_ID, this::uponSubscriptionReply);
        registerReplyHandler(PublishReply.REQUEST_ID, this::uponPublishReply);
        
    }

    @Override
    public void init(Properties props) {
        //Wait prepareTime seconds before starting
        logger.info("Waiting...");
        setupTimer(new StartTimer(), prepareTime * 1000);
        
        logger.info("Issuing subscriptions...");
        for(String topic: this.subscribeTopics) {
        	SubscriptionRequest sreq = new SubscriptionRequest(topic);
        	sendRequest(sreq, pubSubProtoId);
        	logger.info("Requested subscription to topic: " + topic);
        }
    }

    private void uponStartTimer(StartTimer startTimer, long timerId) {
        logger.info("Starting publications");
        //Start broadcasting periodically
        broadCastTimer = setupPeriodicTimer(new DisseminationTimer(), 0, disseminationInterval);
        //And setup the stop timer
        setupTimer(new StopTimer(), runTime * 1000);
    }

    private void uponBroadcastTimer(DisseminationTimer broadcastTimer, long timerId) {
        //Upon triggering the broadcast timer, create a new message
        String toSend = randomCapitalLetters(Math.max(0, payloadSize));
        //ASCII encodes each character as 1 byte
        byte[] payload = toSend.getBytes(StandardCharsets.US_ASCII);

        PublishRequest request = new PublishRequest(this.publishTopics.get(r.nextInt(this.publishTopics.size())), self, payload);
        logger.info("Sending: {}:{} - {} ({})", request.getTopic(), request.getMsgID(), toSend, payload.length);
        //And send it to the dissemination protocol
        sendRequest(request, pubSubProtoId);
    }

    private void uponDeliver(DeliverNotification reply, short sourceProto) {
        //Upon receiving a message, simply print it
        logger.info("Received {}:{} - {} ({}) from {}", reply.getTopic(), reply.getMsgId(),
                new String(reply.getMsg(), StandardCharsets.US_ASCII), reply.getMsg().length, reply.getSender());
    }

    private void uponStopTimer(StopTimer stopTimer, long timerId) {
        logger.info("Stopping publications");
        this.cancelTimer(broadCastTimer);
        setupTimer(new ExitTimer(), cooldownTime * 1000);
    }
    
    private void uponExitTimer(ExitTimer exitTimer, long timerId) {
        logger.info("Exiting...");
        System.exit(0);
    }

    private void uponSubscriptionReply(SubscriptionReply reply, short sourceProto) {
    	logger.info("Completed subscription to topic: " + reply.getTopic());
    }
    
    private void uponPublishReply(PublishReply reply, short sourceProto) {
    	logger.info("Completed publication on topic " + reply.getTopic() + " and id: " + reply.getMsgID());
    }
    
    
    public static String randomCapitalLetters(int length) {
        int leftLimit = 65; // letter 'A'
        int rightLimit = 90; // letter 'Z'
        Random random = new Random();
        return random.ints(leftLimit, rightLimit + 1).limit(length)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }
}
