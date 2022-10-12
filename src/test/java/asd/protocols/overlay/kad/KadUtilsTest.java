package asd.protocols.overlay.kad;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class KadUtilsTest {
    @Test
    public void leadingZeroesTest() {
        assertEquals(0, KadUtils.byteLeadingZeroes((byte) 0b10000000));
        assertEquals(1, KadUtils.byteLeadingZeroes((byte) 0b01000000));
        assertEquals(2, KadUtils.byteLeadingZeroes((byte) 0b00100000));
        assertEquals(3, KadUtils.byteLeadingZeroes((byte) 0b00010000));
        assertEquals(4, KadUtils.byteLeadingZeroes((byte) 0b00001000));
        assertEquals(5, KadUtils.byteLeadingZeroes((byte) 0b00000100));
        assertEquals(6, KadUtils.byteLeadingZeroes((byte) 0b00000010));
        assertEquals(7, KadUtils.byteLeadingZeroes((byte) 0b00000001));
        assertEquals(8, KadUtils.byteLeadingZeroes((byte) 0b00000000));

        assertEquals(0, KadUtils.byteLeadingZeroes((byte) 0b11111111));
        assertEquals(1, KadUtils.byteLeadingZeroes((byte) 0b01111111));
        assertEquals(2, KadUtils.byteLeadingZeroes((byte) 0b00111111));
        assertEquals(3, KadUtils.byteLeadingZeroes((byte) 0b00011111));
        assertEquals(4, KadUtils.byteLeadingZeroes((byte) 0b00001111));
        assertEquals(5, KadUtils.byteLeadingZeroes((byte) 0b00000111));
        assertEquals(6, KadUtils.byteLeadingZeroes((byte) 0b00000011));
        assertEquals(7, KadUtils.byteLeadingZeroes((byte) 0b00000001));
        assertEquals(8, KadUtils.byteLeadingZeroes((byte) 0b00000000));

        assertEquals(0, KadUtils.byteLeadingZeroes((byte) 0b10010011));
        assertEquals(0, KadUtils.byteLeadingZeroes((byte) 0b10010010));
        assertEquals(1, KadUtils.byteLeadingZeroes((byte) 0b01100110));
        assertEquals(2, KadUtils.byteLeadingZeroes((byte) 0b00100010));
        assertEquals(3, KadUtils.byteLeadingZeroes((byte) 0b00010101));
        assertEquals(4, KadUtils.byteLeadingZeroes((byte) 0b00001001));
        assertEquals(5, KadUtils.byteLeadingZeroes((byte) 0b00000100));
        assertEquals(6, KadUtils.byteLeadingZeroes((byte) 0b00000011));
        assertEquals(7, KadUtils.byteLeadingZeroes((byte) 0b00000001));
        assertEquals(8, KadUtils.byteLeadingZeroes((byte) 0b00000000));

        assertEquals(0, KadUtils.byteLeadingZeroes((byte) 0b11111001));
        assertEquals(1, KadUtils.byteLeadingZeroes((byte) 0b01010111));
        assertEquals(2, KadUtils.byteLeadingZeroes((byte) 0b00111100));
        assertEquals(3, KadUtils.byteLeadingZeroes((byte) 0b00011010));
        assertEquals(4, KadUtils.byteLeadingZeroes((byte) 0b00001101));
        assertEquals(5, KadUtils.byteLeadingZeroes((byte) 0b00000101));
        assertEquals(6, KadUtils.byteLeadingZeroes((byte) 0b00000011));
        assertEquals(7, KadUtils.byteLeadingZeroes((byte) 0b00000001));
        assertEquals(8, KadUtils.byteLeadingZeroes((byte) 0b00000000));
    }
}
