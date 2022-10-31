package asd.protocols.pubsub.kadpubsub;

import java.io.IOException;
import java.util.Properties;

import asd.protocols.overlay.common.notifications.ChannelCreatedNotification;
import asd.protocols.pubsub.common.PublishRequest;
import asd.protocols.pubsub.common.SubscriptionRequest;
import asd.protocols.pubsub.common.UnsubscriptionRequest;
import pt.unl.fct.di.novasys.babel.core.GenericProtocol;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;

public class KadPubSub extends GenericProtocol {
    public static final short ID = 400;
    public static final String NAME = "KadPubSub";

    public KadPubSub(Properties props, Host self) throws HandlerRegistrationException, IOException {
        super(NAME, ID);

        this.registerRequestHandler(PublishRequest.REQUEST_ID, this::onPublishRequest);
        this.registerRequestHandler(SubscriptionRequest.REQUEST_ID, this::onSubscriptionRequest);
        this.registerRequestHandler(UnsubscriptionRequest.REQUEST_ID, this::onUnsubscriptionRequest);

        this.subscribeNotification(ChannelCreatedNotification.ID, this::onChannelCreated);
    }

    @Override
    public void init(Properties props) throws HandlerRegistrationException, IOException {
    }

    /*--------------------------------- Notification Handlers ---------------------------------------- */
    private void onPublishRequest(PublishRequest request, short source_proto) {
    }

    private void onSubscriptionRequest(SubscriptionRequest request, short source_proto) {
    }

    private void onUnsubscriptionRequest(UnsubscriptionRequest request, short source_proto) {
    }

    /*--------------------------------- Notification Handlers ---------------------------------------- */

    private void onChannelCreated(ChannelCreatedNotification notification, short source_proto) {
    }

}
