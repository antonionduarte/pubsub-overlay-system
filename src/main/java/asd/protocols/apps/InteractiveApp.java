package asd.protocols.apps;

import asd.protocols.pubsub.common.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;

import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Random;
import java.util.Scanner;

public class InteractiveApp extends GenericProtocol {
	private static final Logger logger = LogManager.getLogger(InteractiveApp.class);

	//Protocol information, to register in babel
	public static final String PROTO_NAME = "InteractivePubSubApp";
	public static final short PROTO_ID = 300;

	private final short pubSubProtoId;

	//Size of the payload of each message (in bytes)
	private final int payloadSize;

	private final Host self;


	public InteractiveApp(Host self, Properties properties, short pubSubProtoId) throws HandlerRegistrationException {
		super(PROTO_NAME, PROTO_ID);
		this.pubSubProtoId = pubSubProtoId;
		this.self = self;

		//Read configurations
		this.payloadSize = Integer.parseInt(properties.getProperty("payload_size", "1024"));

		//Setup handlers
		subscribeNotification(DeliverNotification.NOTIFICATION_ID, this::uponDeliver);

		registerReplyHandler(SubscriptionReply.REQUEST_ID, this::uponSubscriptionReply);
		registerReplyHandler(PublishReply.REQUEST_ID, this::uponPublishReply);

	}

	@Override
	public void init(Properties props) {

		Thread interactiveThread = new Thread(new Runnable() {

			@Override
			public void run() {
				String line;
				String[] components;
				Scanner sc = new Scanner(System.in);
				while (true) {
					System.out.print("> ");
					System.out.flush();
					line = sc.nextLine();
					components = line.split(" ");
					switch (components[0]) {
						case "subscribe":
							if (components.length != 2) {
								logger.error("Usage: subscribe <topic>");
							} else {
								SubscriptionRequest sr = new SubscriptionRequest(components[1]);
								sendRequest(sr, pubSubProtoId);
							}
							break;
						case "unsubscribe":
							if (components.length != 2) {
								logger.error("Usage: unsubscribe <topic>");
							} else {
								UnsubscriptionRequest usr = new UnsubscriptionRequest(components[1]);
								sendRequest(usr, pubSubProtoId);
							}
							break;
						case "publish":
							if (components.length < 2 || components.length > 3) {
								logger.error("Usage: publish <topic> [msg-with-no-spaces]");
							} else {
								PublishRequest pr = new PublishRequest(components[1], self, (components.length == 3 ? components[2].getBytes() : randomCapitalLetters(payloadSize).getBytes()));
								sendRequest(pr, pubSubProtoId);
							}
							break;
						case "exit":
							if (components.length != 1) {
								logger.error("Usage: exit");
							} else {
								sc.close();
								System.exit(0);
							}
							break;
						case "help":
						default:
							logger.error("Commands:");
							logger.error("subscribe [topic]");
							logger.error("publish [topic] <msg-with-no-spaces>");
							logger.error("unsubscribe [topic]");
							logger.error("exit");
							break;
					}
				}
			}
		});
		interactiveThread.start();
	}

	private void uponDeliver(DeliverNotification reply, short sourceProto) {
		//Upon receiving a message, simply print it
		logger.info("Received {}:{} - {} ({}) from {}", reply.getTopic(), reply.getMsgId(),
				new String(reply.getMsg(), StandardCharsets.US_ASCII), reply.getMsg().length, reply.getSender());
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
