package asd.protocols.overlay.kad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import asd.protocols.overlay.kad.routing.RoutingTable;

public class KadRTTest {
    private static final int K = 20;

    static KadID self = KadID.random();
    static RoutingTable rt = new RoutingTable(K, self);

    @Test
    public void peerAddTest() {
        var pcount = 20;
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

    @Test
    public void findClosestTest() {
        var self = KadID.random();
        var rt = new RoutingTable(K, self);

        for (int i = 0; i < 10; ++i)
            for (int j = 0; j < K; ++j)
                rt.add(KadTestUtils.randomPeer(KadID.randomWithCpl(self, i)));

        assertEquals(10 * K, rt.size());

        for (int i = 0; i < 10; ++i) {
            var id = KadID.randomWithCpl(self, i);
            var peers = rt.closest(id);
            assertEquals(K, peers.size());
            for (var p : peers)
                assertEquals(i, self.cpl(p.id));
        }
    }
}
