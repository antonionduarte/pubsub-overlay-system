package asd.protocols.overlay.kad.query;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import java.util.Comparator;
import java.util.stream.Collectors;

import org.junit.Test;

import asd.protocols.overlay.kad.KadID;

public class QPeerSetTest {
    private static int K = 20;

    @Test
    public void getClosestTest() {
        var target = KadID.random();
        var pset = new QPeerSet(K, target);

        for (int i = 0; i < 50; ++i)
            pset.add(KadID.randomWithCpl(target, 0));

        for (int i = 0; i < 50; ++i)
            pset.add(KadID.randomWithCpl(target, 8));

        for (int i = 0; i < 50; ++i)
            pset.add(KadID.randomWithCpl(target, 0));

        var closest = pset.stream().limit(K).map(entry -> entry.getKey()).collect(Collectors.toList());
        assertEquals(20, closest.size());
        closest.forEach(id -> assertEquals(8, id.cpl(target)));

        var original = pset.stream().map(entry -> entry.getKey()).collect(Collectors.toList());
        var sorted = pset.stream().map(entry -> entry.getKey()).collect(Collectors.toList());
        assertEquals(original, sorted);
        Collections.sort(sorted, new Comparator<KadID>() {

            @Override
            public int compare(KadID o1, KadID o2) {
                return o1.distanceTo(target).compareTo(o2.distanceTo(o2));
            }

        });
        sorted.stream().limit(K).forEach(id -> assertEquals(8, id.cpl(target)));

        var first_key = pset.peers.firstKey();
        var last_key = pset.peers.lastKey();
        assertTrue(first_key.distance.compareTo(last_key.distance) < 0);
        assertEquals(8, first_key.id.cpl(target));
        assertEquals(0, last_key.id.cpl(target));

        assertEquals(8, pset.peers.firstKey().id.cpl(target));

        assertEquals(original, sorted);
    }
}
