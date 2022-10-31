package asd.protocols.overlay.common.notifications;

import pt.unl.fct.di.novasys.babel.generic.ProtoNotification;

public class ChannelCreatedNotification extends ProtoNotification {
    public static final short ID = 1000;

    public final int channel_id;

    public ChannelCreatedNotification(int channel_id) {
        super(ID);
        this.channel_id = channel_id;
    }
}
