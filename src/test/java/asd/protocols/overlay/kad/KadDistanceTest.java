package asd.protocols.overlay.kad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KadDistanceTest {

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
	public void distanceTest() {
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
}
