package asd.protocols.pubsub.kadpubsub;

import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;

import asd.metrics.Metrics;
import asd.protocols.overlay.kad.KadID;
import asd.protocols.overlay.kad.Kademlia;
import asd.protocols.overlay.kad.ipc.Broadcast;
import asd.protocols.overlay.kad.ipc.JoinPool;
import asd.protocols.overlay.kad.ipc.JoinPoolReply;
import asd.protocols.overlay.kad.notifications.BroadcastReceived;
import asd.protocols.pubsub.common.DeliverNotification;
import asd.protocols.pubsub.common.PublishRequest;
import asd.protocols.pubsub.common.SubscriptionReply;
import asd.protocols.pubsub.common.SubscriptionRequest;
import asd.protocols.pubsub.common.UnsubscriptionRequest;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;

public class KadPubSub extends GenericProtocol {
    public static final short ID = 400;
    public static final String NAME = "KadPubSub";

    private final short app_proto;

    public KadPubSub(Properties props, Host self, short app_proto) throws HandlerRegistrationException, IOException {
        super(NAME, ID);
        this.app_proto = app_proto;
        this.registerRequestHandler(PublishRequest.REQUEST_ID, this::onPublishRequest);
        this.registerRequestHandler(SubscriptionRequest.REQUEST_ID, this::onSubscriptionRequest);
        this.registerRequestHandler(UnsubscriptionRequest.REQUEST_ID, this::onUnsubscriptionRequest);
        this.registerReplyHandler(JoinPoolReply.ID, this::onJoinPoolReply);
        this.subscribeNotification(BroadcastReceived.ID, this::onBroadcastReceived);
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
    }

    /*--------------------------------- Notification Handlers ---------------------------------------- */
    private void onPublishRequest(PublishRequest request, short source_proto) {
        System.out.println("Publishing " + request.getTopic());
        this.sendRequest(new Broadcast(request.getTopic(), request.getMsgID(), request.getMessage()), Kademlia.ID);
    }

    private void onSubscriptionRequest(SubscriptionRequest request, short source_proto) {
        System.out.println("Received subscription request for topic " + request.getTopic());
        this.sendRequest(new JoinPool(request.getTopic()), Kademlia.ID);
    }

    private void onUnsubscriptionRequest(UnsubscriptionRequest request, short source_proto) {
    }

    private void onJoinPoolReply(JoinPoolReply reply, short source_proto) {
        this.sendReply(new SubscriptionReply(reply.pool), this.app_proto);
    }

    private void onBroadcastReceived(BroadcastReceived notification, short source_proto) {
        System.out.println("Received broadcast for topic " + notification.pool);
        this.triggerNotification(new DeliverNotification(notification.pool, notification.uuid, notification.origin.host,
                notification.payload));
    }
}
