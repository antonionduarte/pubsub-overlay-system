package asd.protocols.overlay.kad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KadRTTest {
    static KadID self = KadID.random();
    static KadRT rt = new KadRT(20, self);

    @Test
    public void peerAddTest() {
        var pcount = 50;
        var peers = new KadPeer[pcount];
        for (int i = 0; i < pcount; ++i) {
            peers[i] = KadTestUtils.randomPeer();
            rt.add(peers[i]);
        }

        assertEquals(pcount, rt.size());
        for (int i = 0; i < pcount; ++i) {
            assertTrue(rt.contains(peers[i].id));
        }

    }
}
