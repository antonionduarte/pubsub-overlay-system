package asd.protocols.overlay.kad;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KadDistanceTest {
	@Test
	public void compareTest() {
		var d1 = new KadDistance(new byte[] { 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
		var d2 = new KadDistance(new byte[] { 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
		var d3 = new KadDistance(new byte[] { 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 });
		var d4 = new KadDistance(new byte[] { 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 });

		assertEquals(1, d1.compareTo(d2));
		assertEquals(1, d1.compareTo(d3));
		assertEquals(1, d1.compareTo(d4));
		assertEquals(1, d2.compareTo(d3));
		assertEquals(1, d2.compareTo(d4));
		assertEquals(1, d3.compareTo(d4));

		assertEquals(-1, d2.compareTo(d1));
		assertEquals(-1, d3.compareTo(d1));
		assertEquals(-1, d4.compareTo(d1));
		assertEquals(-1, d3.compareTo(d2));
		assertEquals(-1, d4.compareTo(d2));
		assertEquals(-1, d4.compareTo(d3));

		assertEquals(0, d1.compareTo(d1));
		assertEquals(0, d2.compareTo(d2));
		assertEquals(0, d3.compareTo(d3));
		assertEquals(0, d4.compareTo(d4));
	}
}
