package io.github.nwalker.vnc.core;

import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.IOException;

import static io.github.nwalker.vnc.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RfbIO} primitive read/write operations.
 * The RFC mandates big-endian byte order for all multi-byte integers.
 */
class RfbIOTest {

    // -------------------------------------------------------------------------
    // U8
    // -------------------------------------------------------------------------

    @Test
    void readU8_zero() throws IOException {
        assertEquals(0, RfbIO.readU8(stream(0x00)));
    }

    @Test
    void readU8_maxValue() throws IOException {
        assertEquals(255, RfbIO.readU8(stream(0xFF)));
    }

    @Test
    void readU8_midValue() throws IOException {
        assertEquals(127, RfbIO.readU8(stream(0x7F)));
    }

    @Test
    void readU8_throwsEOF_onEmptyStream() {
        assertThrows(EOFException.class, () -> RfbIO.readU8(stream()));
    }

    @Test
    void writeU8_zero() throws IOException {
        assertArrayEquals(bytes(0x00), capture(out -> RfbIO.writeU8(out, 0)));
    }

    @Test
    void writeU8_maxValue() throws IOException {
        assertArrayEquals(bytes(0xFF), capture(out -> RfbIO.writeU8(out, 255)));
    }

    // -------------------------------------------------------------------------
    // U16 — big-endian (RFC §7, "most significant byte first")
    // -------------------------------------------------------------------------

    @Test
    void readU16_zero() throws IOException {
        assertEquals(0, RfbIO.readU16(stream(0x00, 0x00)));
    }

    @Test
    void readU16_maxValue() throws IOException {
        assertEquals(65535, RfbIO.readU16(stream(0xFF, 0xFF)));
    }

    @Test
    void readU16_bigEndianByteOrder() throws IOException {
        // 0x01, 0x00 in big-endian is 256
        assertEquals(256, RfbIO.readU16(stream(0x01, 0x00)));
    }

    @Test
    void readU16_sampleValue() throws IOException {
        // 0x1F, 0x40 = 8000
        assertEquals(8000, RfbIO.readU16(stream(0x1F, 0x40)));
    }

    @Test
    void readU16_throwsEOF_onShortStream() {
        assertThrows(EOFException.class, () -> RfbIO.readU16(stream(0x01)));
    }

    @Test
    void writeU16_bigEndianByteOrder() throws IOException {
        // 256 = 0x01, 0x00 in big-endian
        assertArrayEquals(bytes(0x01, 0x00), capture(out -> RfbIO.writeU16(out, 256)));
    }

    @Test
    void writeU16_maxValue() throws IOException {
        assertArrayEquals(bytes(0xFF, 0xFF), capture(out -> RfbIO.writeU16(out, 65535)));
    }

    @Test
    void writeU16_sampleValue() throws IOException {
        // 1920 = 0x07, 0x80
        assertArrayEquals(bytes(0x07, 0x80), capture(out -> RfbIO.writeU16(out, 1920)));
    }

    // -------------------------------------------------------------------------
    // U32 — big-endian
    // -------------------------------------------------------------------------

    @Test
    void readU32_zero() throws IOException {
        assertEquals(0L, RfbIO.readU32(stream(0x00, 0x00, 0x00, 0x00)));
    }

    @Test
    void readU32_maxValue() throws IOException {
        assertEquals(0xFFFFFFFFL, RfbIO.readU32(stream(0xFF, 0xFF, 0xFF, 0xFF)));
    }

    @Test
    void readU32_bigEndianByteOrder() throws IOException {
        // 0x00, 0x01, 0x00, 0x00 = 65536
        assertEquals(65536L, RfbIO.readU32(stream(0x00, 0x01, 0x00, 0x00)));
    }

    @Test
    void readU32_throwsEOF_onShortStream() {
        assertThrows(EOFException.class, () -> RfbIO.readU32(stream(0x00, 0x00, 0x00)));
    }

    @Test
    void writeU32_bigEndianByteOrder() throws IOException {
        // 65536 = 0x00, 0x01, 0x00, 0x00
        assertArrayEquals(bytes(0x00, 0x01, 0x00, 0x00), capture(out -> RfbIO.writeU32(out, 65536L)));
    }

    @Test
    void writeU32_maxUnsignedValue() throws IOException {
        assertArrayEquals(bytes(0xFF, 0xFF, 0xFF, 0xFF), capture(out -> RfbIO.writeU32(out, 0xFFFFFFFFL)));
    }

    // -------------------------------------------------------------------------
    // S32 — signed 32-bit, big-endian
    // -------------------------------------------------------------------------

    @Test
    void readS32_positiveValue() throws IOException {
        assertEquals(1, RfbIO.readS32(stream(0x00, 0x00, 0x00, 0x01)));
    }

    @Test
    void readS32_negativeValue_minusOne() throws IOException {
        // -1 in two's-complement 32-bit = 0xFF, 0xFF, 0xFF, 0xFF
        assertEquals(-1, RfbIO.readS32(stream(0xFF, 0xFF, 0xFF, 0xFF)));
    }

    @Test
    void readS32_negativeValue_cursor_pseudo_encoding() throws IOException {
        // Cursor pseudo-encoding = -239 = 0xFFFF_FF11 in two's complement
        assertEquals(-239, RfbIO.readS32(stream(0xFF, 0xFF, 0xFF, 0x11)));
    }

    @Test
    void readS32_negativeValue_desktopSize() throws IOException {
        // DesktopSize pseudo-encoding = -223 = 0xFFFF_FF21
        assertEquals(-223, RfbIO.readS32(stream(0xFF, 0xFF, 0xFF, 0x21)));
    }

    @Test
    void writeS32_negativeValue_cursor() throws IOException {
        // -239 = 0xFFFFFF11
        assertArrayEquals(bytes(0xFF, 0xFF, 0xFF, 0x11), capture(out -> RfbIO.writeS32(out, -239)));
    }

    @Test
    void writeS32_positiveValue() throws IOException {
        assertArrayEquals(bytes(0x00, 0x00, 0x00, 0x10), capture(out -> RfbIO.writeS32(out, 16)));
    }

    // -------------------------------------------------------------------------
    // readBytes / skipBytes
    // -------------------------------------------------------------------------

    @Test
    void readBytes_exactAmount() throws IOException {
        byte[] result = RfbIO.readBytes(stream(0x01, 0x02, 0x03, 0x04), 4);
        assertArrayEquals(bytes(0x01, 0x02, 0x03, 0x04), result);
    }

    @Test
    void readBytes_throwsEOF_onShortStream() {
        assertThrows(EOFException.class, () -> RfbIO.readBytes(stream(0x01, 0x02), 5));
    }

    @Test
    void readBytes_zero_returnsEmpty() throws IOException {
        assertArrayEquals(new byte[0], RfbIO.readBytes(stream(0x01), 0));
    }

    @Test
    void writePadding_producesZeroBytes() throws IOException {
        byte[] result = capture(out -> RfbIO.writePadding(out, 3));
        assertArrayEquals(bytes(0x00, 0x00, 0x00), result);
    }
}
