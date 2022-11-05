package asd.protocols.pubsub.gossipsub.messages;

import io.netty.buffer.Unpooled;
import junit.framework.TestCase;
import pt.unl.fct.di.novasys.network.data.Host;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;

public class GossipSubSerializerTests extends TestCase {

    public void testSubscribe() throws IOException {
        var inMsg = new SubscribeMessage("topic");
        var buf = Unpooled.buffer();
        SubscribeMessage.serializer.serialize(inMsg, buf);
        var outMsg = SubscribeMessage.serializer.deserialize(buf);
        assertEquals(inMsg.getTopic(), outMsg.getTopic());
    }

    public void testUnsubscribe() throws IOException {
        var inMsg = new UnsubscribeMessage("topic");
        var buf = Unpooled.buffer();
        UnsubscribeMessage.serializer.serialize(inMsg, buf);
        var outMsg = UnsubscribeMessage.serializer.deserialize(buf);
        assertEquals(inMsg.getTopic(), outMsg.getTopic());
    }

    public void testPublish() throws IOException {
        var inMsg = new PublishMessage(
                new Host(InetAddress.getLocalHost(), 2444),
                "topic", UUID.randomUUID()
                , new byte[] {0x01, 0x02, 0x03});
        var buf = Unpooled.buffer();
        PublishMessage.serializer.serialize(inMsg, buf);
        var outMsg = PublishMessage.serializer.deserialize(buf);
        assertEquals(inMsg.getTopic(), outMsg.getTopic());
        assertEquals(inMsg.getMsgId(), outMsg.getMsgId());
        assertEquals(inMsg.getPropagationSource(), outMsg.getPropagationSource());
        assertEquals(inMsg.getHopCount(), outMsg.getHopCount());
        assertArrayEquals(inMsg.getMsg(), outMsg.getMsg());
    }

    public void testPrune() throws IOException {
        var inMsg = new Prune(Map.of(
                "t1", Set.of(new Host(InetAddress.getLocalHost(), 1001), new Host(InetAddress.getLocalHost(), 1002)),
                "t2", Set.of(new Host(InetAddress.getLocalHost(), 1001), new Host(InetAddress.getLocalHost(), 1002))));
        var buf = Unpooled.buffer();
        Prune.serializer.serialize(inMsg, buf);
        var outMsg = Prune.serializer.deserialize(buf);
        assertEquals(inMsg.getPeersPerTopic(), outMsg.getPeersPerTopic());
    }

    public void testIWant() throws IOException {
        var inMsg = new IWant(Set.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()));
        var buf = Unpooled.buffer();
        IWant.serializer.serialize(inMsg, buf);
        var outMsg = IWant.serializer.deserialize(buf);
        assertEquals(inMsg.getMessageIds(), outMsg.getMessageIds());
    }

    public void testIHave() throws IOException {
        var inMsg = new IHave(Map.of(
                "t1", Set.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                "t2", Set.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()),
                "t3", Set.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())));
        var buf = Unpooled.buffer();
        IHave.serializer.serialize(inMsg, buf);
        var outMsg = IHave.serializer.deserialize(buf);
        assertEquals(inMsg.getMsgIdsPerTopic(), outMsg.getMsgIdsPerTopic());
    }

    public void testGraft() throws IOException {
        var inMsg = new Graft(Set.of("t1","t2","t3","t4","t5"));
        var buf = Unpooled.buffer();
        Graft.serializer.serialize(inMsg, buf);
        var outMsg = Graft.serializer.deserialize(buf);
        assertEquals(inMsg.getTopics(), outMsg.getTopics());
    }
}