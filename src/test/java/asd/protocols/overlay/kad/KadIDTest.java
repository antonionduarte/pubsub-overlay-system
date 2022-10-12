package asd.protocols.overlay.kad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KadIDTest {

    @Test
    public void testKeyEquals() {
        var k1 = KadID.create(1, 2, 3);
        var k2 = KadID.create(1, 2, 3);
        var k3 = KadID.create(1, 2, 3, 4);

        assertEquals(k1, k2);
        assertEquals(k1.hashCode(), k2.hashCode());
        assertEquals(k1, k1);
        assertEquals(k1.hashCode(), k1.hashCode());
        assertEquals(k2, k2);
        assertEquals(k2.hashCode(), k2.hashCode());
        assertEquals(k3, k3);
        assertEquals(k3.hashCode(), k3.hashCode());
        assertNotEquals(k1, k3);
        assertNotEquals(k2, k3);
    }

    @Test
    public void randomWithCplTest() {
        for (int i = 0; i < 10000; ++i) {
            var cpl = (int) (Math.random() * 160);
            var id1 = KadID.random();
            var id2 = KadID.randomWithCpl(id1, cpl);
            assertEquals(cpl, id1.cpl(id2));
        }
    }

    @Test
    public void zeroIdDistance() {
        var id1 = KadID.zero();
        var id2 = KadID.zero();
        var dist = id1.distanceTo(id2);

        assertTrue("Distance should be zero", dist.isZero());
    }

    @Test
    public void cplTest() {
        var id0 = KadID.create(0b00000000);
        var id1 = KadID.create(0b10000000);
        var id2 = KadID.create(0b01000000);
        var id3 = KadID.create(0b00100000);

        assertEquals(0, id0.cpl(id1));
        assertEquals(1, id0.cpl(id2));
        assertEquals(2, id0.cpl(id3));

        assertEquals(0, id1.cpl(id0));
        assertEquals(1, id2.cpl(id0));
        assertEquals(2, id3.cpl(id0));

        assertEquals(KadID.ID_LENGTH * 8, id0.cpl(id0));

        var id4 = KadID.create(0b0000_0000, 0b0010_0000);
        var id5 = KadID.create(0b0000_0000, 0b0001_0000);

        assertEquals(10, id4.cpl(id5));
    }

    @Test
    public void cplDistanceTest() {
        for (int i = 0; i < 10000; ++i) {
            var id0 = KadID.random();
            var id1 = KadID.random();
            var id2 = KadID.random();

            var id1_cpl = id0.cpl(id1);
            var id2_cpl = id0.cpl(id2);
            var id1_dist = id0.distanceTo(id1);
            var id2_dist = id0.distanceTo(id2);
            var id_cmp = id1_dist.compareTo(id2_dist);

            // id0: 01010011 00111010
            // id1: 00100110 01001100
            // id1 dist: 01110101 01110110 cpl = 1
            // id2 dist: 00001011 00001000 cpl = 4
            // id2: 01011000 00110010
            // id0: 01010011 00111010

            if (id1_cpl < id2_cpl) {
                // System.err.println("Distance1: " + id1_dist);
                // System.err.println("Distance2: " + id2_dist);
                // System.err.println("Distance cmp: " + id_cmp);

                assertTrue("id1_cpl(" + id1_cpl + ") < id2_cpl(" + id2_cpl + ")", id_cmp > 0);
            } else if (id1_cpl > id2_cpl) {
                // System.err.println("Distance1: " + id1_dist);
                // System.err.println("Distance2: " + id2_dist);
                // System.err.println("Distance cmp: " + id_cmp);

                assertTrue("id1_cpl(" + id1_cpl + ") > id2_cpl(" + id2_cpl + ")", id_cmp < 0);
            }
        }
    }

    @Test
    public void distanceTest() {
        {
            var id0 = KadID.create(0b01010110);
            var id1 = KadID.create(0b01010001);
            var id2 = KadID.create(0b01010100);

            assertTrue(id0.distanceTo(id0).isZero());
            assertTrue(id1.distanceTo(id1).isZero());
            assertTrue(id2.distanceTo(id2).isZero());

            var d1 = id0.distanceTo(id1);
            var d2 = id0.distanceTo(id2);

            assertTrue(d1.compareTo(d2) > 0);
        }
        {
            var id0 = KadID.random();
            var id1 = KadID.randomWithCpl(id0, 5);
            var id2 = KadID.randomWithCpl(id0, 10);

            var d1 = id0.distanceTo(id1);
            var d2 = id0.distanceTo(id2);

            assertTrue(d1.compareTo(d2) > 0);
        }
    }

    @Test
    public void distanceSymmetryTest() {
        for (int i = 0; i < 10000; ++i) {
            var id1 = KadID.random();
            var id2 = KadID.random();

            var d1 = id1.distanceTo(id2);
            var d2 = id2.distanceTo(id1);

            assertEquals(d1, d2);
        }
    }

}
