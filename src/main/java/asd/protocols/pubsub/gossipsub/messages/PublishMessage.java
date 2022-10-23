package asd.protocols.pubsub.gossipsub.messages;

import asd.protocols.pubsub.gossipsub.GossipSub;
import pt.unl.fct.di.novasys.babel.generic.ProtoMessage;
import pt.unl.fct.di.novasys.network.data.Host;

import java.util.UUID;

public class PublishMessage extends ProtoMessage {

    public static final short ID = GossipSub.ID + 3;

    private final Host propagationSource;
    private final String topic;
    private final UUID msgId;
    private final byte[] msg;

    public PublishMessage(Host propagationSource, String topic, UUID msgId, byte[] msg) {
        super(ID);
        this.propagationSource = propagationSource;
        this.topic = topic;
        this.msgId = msgId;
        this.msg = msg;
    }

    public Host getPropagationSource() {
        return propagationSource;
    }

    public String getTopic() {
        return topic;
    }

    public UUID getMsgId() {
        return msgId;
    }

    public byte[] getMsg() {
        return msg;
    }
}
