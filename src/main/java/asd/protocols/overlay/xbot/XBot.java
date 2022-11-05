package asd.protocols.overlay.xbot;

import asd.protocols.dissemination.plumtree.messages.Gossip;
import asd.protocols.dissemination.plumtree.messages.Graft;
import asd.protocols.dissemination.plumtree.messages.IHave;
import asd.protocols.dissemination.plumtree.messages.Prune;
import asd.protocols.overlay.common.notifications.ChannelCreatedNotification;
import asd.protocols.overlay.hyparview.Hyparview;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pt.unl.fct.di.novasys.babel.exceptions.HandlerRegistrationException;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.util.Properties;

public class XBot extends Hyparview {

	private static final String PROTOCOL_NAME = "XBot";

	private static final Logger logger = LogManager.getLogger();

	private int channelId;

	public XBot(Properties properties, Host self) throws IOException, HandlerRegistrationException {
		super(properties, self);
	}

	private void onChannelCreated(ChannelCreatedNotification notification, short protoID) {
		this.channelId = notification.channel_id;
		registerSharedChannel(channelId);
		logger.info("Channel created with id {}", channelId);

		/*---------------------- Register Message Serializers ---------------------- */
		registerMessageSerializer(channelId, Gossip.MSG_ID, Gossip.serializer);
		registerMessageSerializer(channelId, IHave.MSG_ID, IHave.serializer);
		registerMessageSerializer(channelId, Graft.MSG_ID, Graft.serializer);
		registerMessageSerializer(channelId, Prune.MSG_ID, Prune.serializer);

		/*---------------------- Register Message Handlers -------------------------- */
		//registerMessageHandler(channelId, Gossip.MSG_ID, this::uponGossip);
		//registerMessageHandler(channelId, Prune.MSG_ID, this::uponPrune);
		//registerMessageHandler(channelId, Graft.MSG_ID, this::uponGraft);
		//registerMessageHandler(channelId, IHave.MSG_ID, this::uponIHave);
	}



}
