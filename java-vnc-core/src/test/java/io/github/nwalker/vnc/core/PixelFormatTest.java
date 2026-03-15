package io.github.nwalker.vnc.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static io.github.nwalker.vnc.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link PixelFormat} (RFC 6143 §7.4).
 *
 * <p>Wire format (16 bytes):
 * <pre>
 *   [0]    U8  bits-per-pixel
 *   [1]    U8  depth
 *   [2]    U8  big-endian-flag
 *   [3]    U8  true-color-flag
 *   [4-5]  U16 red-max
 *   [6-7]  U16 green-max
 *   [8-9]  U16 blue-max
 *   [10]   U8  red-shift
 *   [11]   U8  green-shift
 *   [12]   U8  blue-shift
 *   [13-15] 3 padding bytes (ignored on parse)
 * </pre>
 */
class PixelFormatTest {

    @Test
    void parse_rgb888_fieldOrder() throws IOException {
        // RFC §7.4 table — fields must be read in this exact order
        byte[] wire = rgb888PixelFormatBytes();
        PixelFormat pf = PixelFormat.parse(stream(wire));

        assertEquals(32,   pf.getBitsPerPixel());
        assertEquals(24,   pf.getDepth());
        assertFalse(       pf.isBigEndian());
        assertTrue(        pf.isTrueColor());
        assertEquals(255,  pf.getRedMax());
        assertEquals(255,  pf.getGreenMax());
        assertEquals(255,  pf.getBlueMax());
        assertEquals(16,   pf.getRedShift());
        assertEquals(8,    pf.getGreenShift());
        assertEquals(0,    pf.getBlueShift());
    }

    @Test
    void parse_consumesExactly16Bytes() throws IOException {
        // 16 pixel-format bytes followed by a sentinel 0xAB
        byte[] wire = new byte[17];
        System.arraycopy(rgb888PixelFormatBytes(), 0, wire, 0, 16);
        wire[16] = (byte) 0xAB;
        InputStream in = stream(wire);
        PixelFormat.parse(in);
        // Sentinel should still be readable
        assertEquals(0xAB, in.read());
    }

    @Test
    void parse_paddingIsIgnored() throws IOException {
        // Padding bytes 13-15 with non-zero values — must be silently ignored
        byte[] wire = rgb888PixelFormatBytes().clone();
        wire[13] = (byte) 0xFF;
        wire[14] = (byte) 0xEE;
        wire[15] = (byte) 0xDD;
        PixelFormat pf = PixelFormat.parse(stream(wire));
        assertEquals(32, pf.getBitsPerPixel()); // still parses correctly
    }

    @Test
    void write_rgb888_matchesWireFormat() throws IOException {
        PixelFormat pf = rgb888PixelFormat();
        byte[] written = capture(pf::write);
        assertArrayEquals(rgb888PixelFormatBytes(), written);
    }

    @Test
    void write_is16Bytes() throws IOException {
        assertEquals(16, capture(rgb888PixelFormat()::write).length);
    }

    @Test
    void write_bigEndianFlag_nonZeroForTrue() throws IOException {
        PixelFormat pf = PixelFormat.builder()
                .bitsPerPixel(32).depth(24).bigEndian(true).trueColor(false)
                .redMax(0).greenMax(0).blueMax(0)
                .redShift(0).greenShift(0).blueShift(0)
                .build();
        byte[] w = capture(pf::write);
        // offset 2 = big-endian-flag, must be non-zero
        assertNotEquals(0, w[2] & 0xFF);
    }

    @Test
    void write_falseFlags_areZero() throws IOException {
        byte[] w = capture(rgb888PixelFormat()::write);
        assertEquals(0x00, w[2] & 0xFF); // bigEndian=false -> 0
    }

    @Test
    void write_paddingBytesAreZero() throws IOException {
        byte[] w = capture(rgb888PixelFormat()::write);
        assertEquals(0, w[13]);
        assertEquals(0, w[14]);
        assertEquals(0, w[15]);
    }

    @Test
    void write_redMaxIsBigEndian() throws IOException {
        // red-max=511 (0x01FF) at bytes [4-5] — big-endian: 0x01, 0xFF
        PixelFormat pf = PixelFormat.builder()
                .bitsPerPixel(32).depth(24).bigEndian(false).trueColor(true)
                .redMax(511).greenMax(0).blueMax(0)
                .redShift(0).greenShift(0).blueShift(0)
                .build();
        byte[] w = capture(pf::write);
        assertEquals(0x01, w[4] & 0xFF);
        assertEquals(0xFF, w[5] & 0xFF);
    }

    @Test
    void roundtrip_rgb888() throws IOException {
        PixelFormat original = rgb888PixelFormat();
        byte[] wire = capture(original::write);
        PixelFormat parsed = PixelFormat.parse(stream(wire));
        assertEquals(original, parsed);
    }

    @Test
    void roundtrip_16bpp() throws IOException {
        PixelFormat original = PixelFormat.builder()
                .bitsPerPixel(16).depth(16).bigEndian(true).trueColor(true)
                .redMax(31).greenMax(63).blueMax(31)
                .redShift(11).greenShift(5).blueShift(0)
                .build();
        PixelFormat parsed = PixelFormat.parse(stream(capture(original::write)));
        assertEquals(original, parsed);
    }

    @Test
    void getBytesPerPixel_32bpp() {
        assertEquals(4, rgb888PixelFormat().getBytesPerPixel());
    }

    @Test
    void getBytesPerPixel_16bpp() {
        PixelFormat pf = PixelFormat.builder().bitsPerPixel(16).depth(16)
                .bigEndian(false).trueColor(true)
                .redMax(31).greenMax(63).blueMax(31)
                .redShift(11).greenShift(5).blueShift(0).build();
        assertEquals(2, pf.getBytesPerPixel());
    }

    // -------------------------------------------------------------------------
    // getBytesPerCPixel — RFC §7.7.5 CPIXEL rules
    // -------------------------------------------------------------------------

    @Test
    void getBytesPerCPixel_rgb888LowBytes_is3() {
        // 32bpp, depth<=24, true-color, all components fit in bits 0-23
        // red=[16,23], green=[8,15], blue=[0,7] — all within bits 0-23
        assertEquals(3, rgb888PixelFormat().getBytesPerCPixel());
    }

    @Test
    void getBytesPerCPixel_rgb888HighBytes_is3() {
        // Shifts: red=24, green=16, blue=8 — all >= 8 (fit in bits 8-31)
        assertEquals(3, rgb888HighBytesPixelFormat().getBytesPerCPixel());
    }

    @Test
    void getBytesPerCPixel_16bpp_is2() {
        // 16bpp — condition bitsPerPixel==32 not met, so falls back to bytesPerPixel
        PixelFormat pf = PixelFormat.builder()
                .bitsPerPixel(16).depth(16).bigEndian(false).trueColor(true)
                .redMax(31).greenMax(63).blueMax(31)
                .redShift(11).greenShift(5).blueShift(0).build();
        assertEquals(2, pf.getBytesPerCPixel());
    }

    @Test
    void getBytesPerCPixel_8bpp_is1() {
        PixelFormat pf = PixelFormat.builder()
                .bitsPerPixel(8).depth(8).bigEndian(false).trueColor(false)
                .redMax(0).greenMax(0).blueMax(0)
                .redShift(0).greenShift(0).blueShift(0).build();
        assertEquals(1, pf.getBytesPerCPixel());
    }

    @Test
    void getBytesPerCPixel_notTrueColor_is4() {
        // true-color-flag=false: condition not met, bytesPerPixel=4
        PixelFormat pf = PixelFormat.builder()
                .bitsPerPixel(32).depth(8).bigEndian(false).trueColor(false)
                .redMax(0).greenMax(0).blueMax(0)
                .redShift(0).greenShift(0).blueShift(0).build();
        assertEquals(4, pf.getBytesPerCPixel());
    }

    @Test
    void getBytesPerCPixel_depth25_is4() {
        // depth > 24: condition not met even though bpp=32 and trueColor=true
        PixelFormat pf = PixelFormat.builder()
                .bitsPerPixel(32).depth(25).bigEndian(false).trueColor(true)
                .redMax(255).greenMax(255).blueMax(255)
                .redShift(16).greenShift(8).blueShift(0).build();
        assertEquals(4, pf.getBytesPerCPixel());
    }

    @Test
    void getBytesPerCPixel_channelSpillsOutOfLow3Bytes_is4() {
        // red starts at shift=17 with 8 bits → occupies bits 17-24
        // bit 24 is outside the low 3 bytes (bits 0-23), so CPIXEL cannot be 3 bytes
        // green shift=9 (8-bit, bits 9-16, OK), blue shift=1 (8-bit, bits 1-8, OK)
        // but red overflows → should fall back to 4
        PixelFormat pf = PixelFormat.builder()
                .bitsPerPixel(32).depth(24).bigEndian(false).trueColor(true)
                .redMax(255).greenMax(255).blueMax(255)
                .redShift(17).greenShift(9).blueShift(1).build();
        // redShift=17, bitsForMax(255)=8, so 17+8=25 > 24 → not in low bytes
        // redShift=17 >= 8, greenShift=9 >= 8, blueShift=1 < 8 → not in high bytes
        assertEquals(4, pf.getBytesPerCPixel());
    }

    @Test
    void getBytesPerCPixel_mixedHighLow_is4() {
        // Red is in high bytes (shift=24) but blue is in low (shift=0)
        // Neither all-low nor all-high → should be 4
        PixelFormat pf = PixelFormat.builder()
                .bitsPerPixel(32).depth(24).bigEndian(false).trueColor(true)
                .redMax(255).greenMax(255).blueMax(255)
                .redShift(24).greenShift(16).blueShift(0).build();
        // inHigh: blue shift=0 < 8 → false; inLow: red 24+8=32 > 24 → false
        assertEquals(4, pf.getBytesPerCPixel());
    }

    // -------------------------------------------------------------------------
    // equals / hashCode / toString
    // -------------------------------------------------------------------------

    @Test
    void equals_sameValues() {
        assertEquals(rgb888PixelFormat(), rgb888PixelFormat());
    }

    @Test
    void equals_differentShift() {
        PixelFormat a = rgb888PixelFormat();
        PixelFormat b = PixelFormat.builder()
                .bitsPerPixel(32).depth(24).bigEndian(false).trueColor(true)
                .redMax(255).greenMax(255).blueMax(255)
                .redShift(0).greenShift(8).blueShift(16).build();
        assertNotEquals(a, b);
    }

    @Test
    void hashCode_consistent() {
        assertEquals(rgb888PixelFormat().hashCode(), rgb888PixelFormat().hashCode());
    }

    @Test
    void toString_containsKeyFields() {
        String s = rgb888PixelFormat().toString();
        assertTrue(s.contains("bitsPerPixel=32"));
        assertTrue(s.contains("depth=24"));
    }
}
