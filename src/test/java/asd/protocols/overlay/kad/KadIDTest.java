package asd.protocols.overlay.kad;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KadIDTest {

    @Test
    public void testRandomWithCpl() {
        for (int i = 0; i < 10000; ++i) {
            var cpl = (int) (Math.random() * 160);
            var id1 = KadID.random();
            var id2 = KadID.randomWithCpl(id1, cpl);
            assertEquals(cpl, id1.cpl(id2));
        }
    }
}
