package asd.protocols.pubsub.gossipsub.messages;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.buffer.UnpooledDirectByteBuf;
import junit.framework.TestCase;

import java.io.IOException;

public class SubscribeMessageTest extends TestCase {

    public void testGetTopic() throws IOException {
        var inMsg = new SubscribeMessage("topic");
        var buf = Unpooled.buffer();
        SubscribeMessage.serializer.serialize(inMsg, buf);
        var outMsg = SubscribeMessage.serializer.deserialize(buf);
        assertEquals(inMsg.getTopic(), outMsg.getTopic());
    }
}