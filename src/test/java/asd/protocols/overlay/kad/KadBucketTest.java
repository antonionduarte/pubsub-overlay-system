package asd.protocols.overlay.kad;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KadBucketTest {
	private static final int K = 20;

	@Test
	public void emptyBucketTest() {
		var bucket = new KadBucket(K);

		assertEquals(0, bucket.size());
		assertTrue(!bucket.isFull());
	}

	@Test
	public void addBucketTest() {
		var bucket = new KadBucket(K);
		for (int i = 0; i < K; ++i) {
			var added = bucket.add(KadTestUtils.randomPeer());
			assertTrue(added);
			assertEquals(i + 1, bucket.size());
		}

		for (int i = 0; i < K; ++i) {
			var added = bucket.add(KadTestUtils.randomPeer());
			assertTrue(!added);
			assertEquals(K, bucket.size());
		}
	}

	@Test
	public void addDuplicateTest() {
		var p1 = KadTestUtils.randomPeer();
		var p2 = KadTestUtils.randomPeer();
		var p3 = p1;
		var bucket = new KadBucket(K);

		assertTrue(bucket.add(p1));
		assertTrue(bucket.add(p2));
		assertFalse(bucket.add(p3));
	}

	@Test
	public void removeByIdTest() {
		var p1 = KadTestUtils.randomPeer();
		var p2 = KadTestUtils.randomPeer();
		var p3 = KadTestUtils.randomPeer();
		var p4 = KadTestUtils.randomPeer();

		var bucket = new KadBucket(K);
		bucket.add(p1);
		bucket.add(p2);
		bucket.add(p3);
		bucket.add(p4);

		assertFalse(bucket.removeByID(KadID.random()));
		assertFalse(bucket.removeByID(KadID.random()));

		assertTrue(bucket.removeByID(p1.id));
		assertFalse(bucket.removeByID(p1.id));
		assertFalse(bucket.removeByID(p1.id));

		bucket.add(p1);
		assertTrue(bucket.removeByID(p1.id));
		assertFalse(bucket.removeByID(p1.id));
		assertFalse(bucket.removeByID(p1.id));
	}

}
