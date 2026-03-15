package io.github.nwalker.vnc.core.encoding;

import io.github.nwalker.vnc.core.EncodingType;
import io.github.nwalker.vnc.core.PixelFormat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static io.github.nwalker.vnc.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for encoding data classes (RFC 6143 §7.7 and §7.8).
 *
 * <p>Tests verify that each encoding's parse() correctly reads the bytes
 * specified by the RFC and that write() produces exactly those bytes.
 */
class EncodingDataTest {

    // -------------------------------------------------------------------------
    // §7.7.1 RawEncodingData (encoding-type = 0)
    // -------------------------------------------------------------------------

    @Test
    void raw_parse_readsWidthTimesHeightTimesBytes() throws IOException {
        // 2x2 tile, 32bpp = 16 bytes
        byte[] pixels = bytes(
                0x01, 0x02, 0x03, 0x04,  // pixel (0,0)
                0x05, 0x06, 0x07, 0x08,  // pixel (1,0)
                0x09, 0x0A, 0x0B, 0x0C,  // pixel (0,1)
                0x0D, 0x0E, 0x0F, 0x10   // pixel (1,1)
        );
        RawEncodingData data = RawEncodingData.parse(stream(pixels), 2, 2, rgb888PixelFormat());
        assertArrayEquals(pixels, data.getPixels());
    }

    @Test
    void raw_parse_1x1_32bpp_reads4Bytes() throws IOException {
        byte[] pixel = bytes(0xFF, 0x00, 0xFF, 0x00);
        RawEncodingData data = RawEncodingData.parse(stream(pixel), 1, 1, rgb888PixelFormat());
        assertArrayEquals(pixel, data.getPixels());
    }

    @Test
    void raw_parse_1x1_16bpp_reads2Bytes() throws IOException {
        PixelFormat pf16 = PixelFormat.builder()
                .bitsPerPixel(16).depth(16).bigEndian(false).trueColor(true)
                .redMax(31).greenMax(63).blueMax(31)
                .redShift(11).greenShift(5).blueShift(0).build();
        byte[] pixel = bytes(0x12, 0x34);
        RawEncodingData data = RawEncodingData.parse(stream(pixel), 1, 1, pf16);
        assertArrayEquals(pixel, data.getPixels());
    }

    @Test
    void raw_write_exactPixelBytes() throws IOException {
        byte[] pixels = bytes(0xDE, 0xAD, 0xBE, 0xEF);
        RawEncodingData data = RawEncodingData.builder().pixels(pixels).build();
        assertArrayEquals(pixels, capture(data::write));
    }

    @Test
    void raw_getEncodingType() {
        assertEquals(EncodingType.RAW,
                RawEncodingData.builder().pixels(new byte[4]).build().getEncodingType());
    }

    @Test
    void raw_parse_consumesExactBytes() throws IOException {
        // 1 pixel (4 bytes) then sentinel
        InputStream in = stream(0x01, 0x02, 0x03, 0x04, 0xAB);
        RawEncodingData.parse(in, 1, 1, rgb888PixelFormat());
        assertEquals(0xAB, in.read());
    }

    @Test
    void raw_roundtrip() throws IOException {
        byte[] pixels = bytes(0xAA, 0xBB, 0xCC, 0xDD);
        RawEncodingData original = RawEncodingData.builder().pixels(pixels).build();
        byte[] wire = capture(original::write);
        assertEquals(original, RawEncodingData.parse(stream(wire), 1, 1, rgb888PixelFormat()));
    }

    // -------------------------------------------------------------------------
    // §7.7.2 CopyRectEncodingData (encoding-type = 1)
    // -------------------------------------------------------------------------

    @Test
    void copyRect_parse_srcXsrcY_areBigEndianU16() throws IOException {
        // srcX=256 (0x01,0x00), srcY=512 (0x02,0x00)
        CopyRectEncodingData data = CopyRectEncodingData.parse(stream(0x01, 0x00, 0x02, 0x00));
        assertEquals(256, data.getSrcX());
        assertEquals(512, data.getSrcY());
    }

    @Test
    void copyRect_parse_is4Bytes() throws IOException {
        InputStream in = stream(0x00, 0x0A, 0x00, 0x14, 0xAB); // srcX=10, srcY=20, sentinel
        CopyRectEncodingData.parse(in);
        assertEquals(0xAB, in.read());
    }

    @Test
    void copyRect_write_srcXsrcY_bigEndian() throws IOException {
        CopyRectEncodingData data = CopyRectEncodingData.builder().srcX(10).srcY(20).build();
        byte[] w = capture(data::write);
        assertEquals(4, w.length);
        // srcX=10: 0x00, 0x0A
        assertEquals(0x00, w[0]);
        assertEquals(0x0A, w[1]);
        // srcY=20: 0x00, 0x14
        assertEquals(0x00, w[2]);
        assertEquals(0x14, w[3]);
    }

    @Test
    void copyRect_getEncodingType() {
        assertEquals(EncodingType.COPY_RECT,
                CopyRectEncodingData.builder().srcX(0).srcY(0).build().getEncodingType());
    }

    @Test
    void copyRect_roundtrip() throws IOException {
        CopyRectEncodingData original = CopyRectEncodingData.builder().srcX(100).srcY(200).build();
        byte[] wire = capture(original::write);
        assertEquals(original, CopyRectEncodingData.parse(stream(wire)));
    }

    // -------------------------------------------------------------------------
    // §7.7.3 RreEncodingData (encoding-type = 2)
    // -------------------------------------------------------------------------

    @Test
    void rre_parse_headerFields() throws IOException {
        // U32 nSubrects=1, PIXEL bgPixel (4 bytes for 32bpp), then 1 subrect
        // Subrect: PIXEL (4 bytes) + U16 x + U16 y + U16 w + U16 h
        byte[] payload = bytes(
                0x00, 0x00, 0x00, 0x01,  // nSubrects=1
                0x00, 0x00, 0x00, 0xFF,  // background pixel
                0xFF, 0xFF, 0xFF, 0x00,  // subrect pixel
                0x00, 0x05,              // subrect x=5
                0x00, 0x0A,              // subrect y=10
                0x00, 0x14,              // subrect w=20
                0x00, 0x0F               // subrect h=15
        );
        RreEncodingData data = RreEncodingData.parse(stream(payload), rgb888PixelFormat());
        assertArrayEquals(bytes(0x00, 0x00, 0x00, 0xFF), data.getBackgroundPixel());
        assertEquals(1, data.getSubrectangles().size());
        RreSubrectangle sub = data.getSubrectangles().get(0);
        assertArrayEquals(bytes(0xFF, 0xFF, 0xFF, 0x00), sub.getPixelValue());
        assertEquals(5,  sub.getX());
        assertEquals(10, sub.getY());
        assertEquals(20, sub.getWidth());
        assertEquals(15, sub.getHeight());
    }

    @Test
    void rre_parse_zeroSubrects() throws IOException {
        // U32 nSubrects=0, PIXEL bgPixel (4 bytes)
        byte[] payload = bytes(0x00, 0x00, 0x00, 0x00, 0xAA, 0xBB, 0xCC, 0xDD);
        RreEncodingData data = RreEncodingData.parse(stream(payload), rgb888PixelFormat());
        assertEquals(0, data.getSubrectangles().size());
        assertArrayEquals(bytes(0xAA, 0xBB, 0xCC, 0xDD), data.getBackgroundPixel());
    }

    @Test
    void rre_write_nSubrectsIsU32BigEndian() throws IOException {
        RreEncodingData data = RreEncodingData.builder()
                .backgroundPixel(bytes(0x00, 0x00, 0x00, 0x00))
                .addSubrectangle(RreSubrectangle.builder()
                        .pixelValue(bytes(0xFF, 0xFF, 0xFF, 0xFF))
                        .x(0).y(0).width(1).height(1).build())
                .build();
        byte[] w = capture(data::write);
        // bytes 0-3: nSubrects=1 (big-endian U32)
        assertEquals(0x00, w[0]);
        assertEquals(0x00, w[1]);
        assertEquals(0x00, w[2]);
        assertEquals(0x01, w[3]);
    }

    @Test
    void rre_getEncodingType() {
        assertEquals(EncodingType.RRE,
                RreEncodingData.builder()
                        .backgroundPixel(new byte[4]).build().getEncodingType());
    }

    @Test
    void rre_roundtrip() throws IOException {
        RreEncodingData original = RreEncodingData.builder()
                .backgroundPixel(bytes(0x10, 0x20, 0x30, 0x40))
                .addSubrectangle(RreSubrectangle.builder()
                        .pixelValue(bytes(0xAA, 0xBB, 0xCC, 0xDD))
                        .x(1).y(2).width(3).height(4).build())
                .build();
        byte[] wire = capture(original::write);
        assertEquals(original, RreEncodingData.parse(stream(wire), rgb888PixelFormat()));
    }

    // -------------------------------------------------------------------------
    // §7.7.4 HextileEncodingData (encoding-type = 5)
    // -------------------------------------------------------------------------

    @Test
    void hextile_parse_rawSubencoding_1x1_32bpp() throws IOException {
        // 1x1 tile, subencoding=RAW (0x01), then 4 bytes for 1 pixel
        byte[] payload = bytes(0x01, 0xDE, 0xAD, 0xBE, 0xEF);
        HextileEncodingData data = HextileEncodingData.parse(stream(payload), 1, 1, rgb888PixelFormat());
        assertArrayEquals(payload, data.getRawBytes());
    }

    @Test
    void hextile_parse_backgroundSubencoding_1x1() throws IOException {
        // 1x1 tile, subencoding=BACKGROUND (0x02), then 4 background bytes
        byte[] payload = bytes(0x02, 0x00, 0x00, 0xFF, 0xFF);
        HextileEncodingData data = HextileEncodingData.parse(stream(payload), 1, 1, rgb888PixelFormat());
        assertArrayEquals(payload, data.getRawBytes());
    }

    @Test
    void hextile_parse_noSubrects_1x1() throws IOException {
        // Subencoding with neither RAW nor ANY_SUBRECTS: just BACKGROUND (0x02)
        // Tile uses previous colors if BACKGROUND and FOREGROUND not set, no subrects
        byte[] payload = bytes(0x00); // subencoding=0 (nothing set, reuse all previous)
        HextileEncodingData data = HextileEncodingData.parse(stream(payload), 1, 1, rgb888PixelFormat());
        assertArrayEquals(payload, data.getRawBytes());
    }

    @Test
    void hextile_parse_16x16_rawSubencoding() throws IOException {
        // 16x16 tile with RAW subencoding: 1 subencoding byte + 16*16*4 = 1025 bytes
        byte[] payload = new byte[1025];
        payload[0] = 0x01; // RAW
        // rest are pixel bytes (all zero)
        HextileEncodingData data = HextileEncodingData.parse(stream(payload), 16, 16, rgb888PixelFormat());
        assertArrayEquals(payload, data.getRawBytes());
    }

    @Test
    void hextile_parse_twoTiles_17wide() throws IOException {
        // Width=17, height=1: 2 tiles (16-wide + 1-wide)
        // Tile 1: 16x1 RAW: 1 + 16*4 = 65 bytes
        // Tile 2: 1x1 RAW: 1 + 1*4 = 5 bytes
        byte[] tile1 = new byte[65];
        tile1[0] = 0x01; // RAW
        byte[] tile2 = new byte[5];
        tile2[0] = 0x01; // RAW
        byte[] payload = new byte[70];
        System.arraycopy(tile1, 0, payload, 0, 65);
        System.arraycopy(tile2, 0, payload, 65, 5);
        HextileEncodingData data = HextileEncodingData.parse(stream(payload), 17, 1, rgb888PixelFormat());
        assertArrayEquals(payload, data.getRawBytes());
    }

    @Test
    void hextile_parse_subrectColored_1x1() throws IOException {
        // subencoding = BACKGROUND(0x02) | FOREGROUND(0x04) | ANY_SUBRECTS(0x08) | SUBRECTS_COLORED(0x10) = 0x1E
        // background pixel (4), foreground NOT specified since FOREGROUND not set alone
        // Wait: BACKGROUND=0x02 means background IS specified, FOREGROUND=0x04 means fg IS specified
        // So: subencoding=0x1E, bgPixel(4), fgPixel(4), nSubrects(1), coloredSubrect: pixel(4)+subrectXY(1)+subrectWH(1)
        // For a 16x16 tile with 1 colored subrect
        byte[] payload = bytes(
                0x1E,           // subencoding: BG+FG+anySubrects+subrectColored
                0x00, 0x00, 0x00, 0x01,  // bg pixel
                0x00, 0x00, 0xFF, 0xFF,  // fg pixel
                0x01,                   // nSubrects=1
                0xFF, 0xFF, 0xFF, 0xFF,  // subrect pixel (colored)
                0x00,                   // subrect xy: x=0, y=0 (packed as nibbles)
                0x00                    // subrect wh: w=1, h=1 (packed as nibbles, wh-1)
        );
        HextileEncodingData data = HextileEncodingData.parse(stream(payload), 16, 16, rgb888PixelFormat());
        assertArrayEquals(payload, data.getRawBytes());
    }

    @Test
    void hextile_write_returnsExactRawBytes() throws IOException {
        byte[] rawBytes = bytes(0x01, 0xDE, 0xAD, 0xBE, 0xEF);
        HextileEncodingData data = HextileEncodingData.builder().rawBytes(rawBytes).build();
        assertArrayEquals(rawBytes, capture(data::write));
    }

    @Test
    void hextile_getEncodingType() {
        assertEquals(EncodingType.HEXTILE,
                HextileEncodingData.builder().rawBytes(new byte[0]).build().getEncodingType());
    }

    @Test
    void hextile_roundtrip_raw() throws IOException {
        byte[] payload = bytes(0x01, 0xAA, 0xBB, 0xCC, 0xDD);
        HextileEncodingData original = HextileEncodingData.parse(stream(payload), 1, 1, rgb888PixelFormat());
        assertEquals(original, HextileEncodingData.parse(stream(capture(original::write)), 1, 1, rgb888PixelFormat()));
    }

    // -------------------------------------------------------------------------
    // §7.7.5 TrleEncodingData (encoding-type = 15)
    // -------------------------------------------------------------------------

    @Test
    void trle_parse_solid_1x1() throws IOException {
        // Solid subencoding (1): 1 subencoding byte + 1 cpixel (3 bytes for RGB888)
        // CPIXEL for RGB888 (shift 0,8,16, depth 24) = 3 bytes
        byte[] payload = bytes(0x01, 0xFF, 0x00, 0xFF); // solid, magenta
        TrleEncodingData data = TrleEncodingData.parse(stream(payload), 1, 1, rgb888PixelFormat());
        assertArrayEquals(payload, data.getRawBytes());
    }

    @Test
    void trle_parse_raw_1x1() throws IOException {
        // Raw subencoding (0): 1 subencoding byte + w*h cpixels
        // For 1x1 with 3-byte CPIXEL
        byte[] payload = bytes(0x00, 0x01, 0x02, 0x03); // raw, 1 cpixel
        TrleEncodingData data = TrleEncodingData.parse(stream(payload), 1, 1, rgb888PixelFormat());
        assertArrayEquals(payload, data.getRawBytes());
    }

    @Test
    void trle_parse_plainRle_1x1() throws IOException {
        // Plain RLE subencoding (128): pairs of (cpixel, run-length)
        // For 1x1: cpixel (3 bytes) + run-length (1 byte = 0 meaning 1)
        // run-length encoding: value 0 means run of 1
        byte[] payload = bytes(0x80, 0xFF, 0x00, 0x00, 0x00); // 0x80=128, pixel, runLen=0(→1)
        TrleEncodingData data = TrleEncodingData.parse(stream(payload), 1, 1, rgb888PixelFormat());
        assertArrayEquals(payload, data.getRawBytes());
    }

    @Test
    void trle_parse_twoTiles_17wide() throws IOException {
        // Width=17, height=1: TRLE uses 16x16 tiles (RFC §7.7.5)
        //   Tile 1: x=0..15, y=0..0 → 16x1, Solid (sub=1) + 3-byte cpixel = 4 bytes
        //   Tile 2: x=16..16, y=0..0 → 1x1,  Solid (sub=1) + 3-byte cpixel = 4 bytes
        byte[] payload = bytes(
                0x01, 0x00, 0x80, 0x00,  // tile1: solid, pixel
                0x01, 0x00, 0x80, 0x00   // tile2: solid, pixel
        );
        TrleEncodingData data = TrleEncodingData.parse(stream(payload), 17, 1, rgb888PixelFormat());
        assertArrayEquals(payload, data.getRawBytes());
    }

    @Test
    void trle_parse_packedPalette2_1x1() throws IOException {
        // Packed palette subencoding 2: 2-color palette
        // subencoding=2, palette of 2 cpixels (6 bytes), then packed indices
        // For 1x1: 1 bit per pixel, 1 byte (ceil(1/8) = 1)
        byte[] payload = bytes(
                0x02,                   // packed palette, palette size=2
                0x00, 0x00, 0x00,       // color 0
                0xFF, 0xFF, 0xFF,       // color 1
                0x00                    // packed indices: 1 byte for 1 pixel
        );
        TrleEncodingData data = TrleEncodingData.parse(stream(payload), 1, 1, rgb888PixelFormat());
        assertArrayEquals(payload, data.getRawBytes());
    }

    @Test
    void trle_write_returnsExactRawBytes() throws IOException {
        byte[] rawBytes = bytes(0x01, 0x10, 0x20, 0x30);
        TrleEncodingData data = TrleEncodingData.builder().rawBytes(rawBytes).build();
        assertArrayEquals(rawBytes, capture(data::write));
    }

    @Test
    void trle_getEncodingType() {
        assertEquals(EncodingType.TRLE,
                TrleEncodingData.builder().rawBytes(new byte[0]).build().getEncodingType());
    }

    @Test
    void trle_roundtrip_solid() throws IOException {
        byte[] payload = bytes(0x01, 0xAA, 0xBB, 0xCC);
        TrleEncodingData original = TrleEncodingData.parse(stream(payload), 1, 1, rgb888PixelFormat());
        assertEquals(original, TrleEncodingData.parse(stream(capture(original::write)), 1, 1, rgb888PixelFormat()));
    }

    // -------------------------------------------------------------------------
    // §7.7.6 ZrleEncodingData (encoding-type = 16)
    // -------------------------------------------------------------------------

    @Test
    void zrle_parse_lengthPrefixedZlibData() throws IOException {
        // U32 length=4, then 4 bytes of zlib data
        byte[] zlibData = bytes(0x78, 0x9C, 0x03, 0x00); // minimal zlib stream
        byte[] payload = bytes(0x00, 0x00, 0x00, 0x04, 0x78, 0x9C, 0x03, 0x00);
        ZrleEncodingData data = ZrleEncodingData.parse(stream(payload));
        assertArrayEquals(zlibData, data.getZlibData());
    }

    @Test
    void zrle_parse_emptyZlibData() throws IOException {
        byte[] payload = bytes(0x00, 0x00, 0x00, 0x00);
        ZrleEncodingData data = ZrleEncodingData.parse(stream(payload));
        assertEquals(0, data.getZlibData().length);
    }

    @Test
    void zrle_parse_lengthIsBigEndianU32() throws IOException {
        // U32 = 0x00000002 = 2 bytes follow
        byte[] payload = bytes(0x00, 0x00, 0x00, 0x02, 0xAB, 0xCD);
        ZrleEncodingData data = ZrleEncodingData.parse(stream(payload));
        assertEquals(2, data.getZlibData().length);
        assertEquals((byte) 0xAB, data.getZlibData()[0]);
        assertEquals((byte) 0xCD, data.getZlibData()[1]);
    }

    @Test
    void zrle_write_prefixesLength() throws IOException {
        byte[] zlibData = bytes(0x01, 0x02, 0x03);
        ZrleEncodingData data = ZrleEncodingData.builder().zlibData(zlibData).build();
        byte[] w = capture(data::write);
        // First 4 bytes: U32 length=3
        assertEquals(0x00, w[0]);
        assertEquals(0x00, w[1]);
        assertEquals(0x00, w[2]);
        assertEquals(0x03, w[3]);
        // Then data
        assertEquals(0x01, w[4] & 0xFF);
        assertEquals(0x02, w[5] & 0xFF);
        assertEquals(0x03, w[6] & 0xFF);
    }

    @Test
    void zrle_getEncodingType() {
        assertEquals(EncodingType.ZRLE,
                ZrleEncodingData.builder().zlibData(new byte[0]).build().getEncodingType());
    }

    @Test
    void zrle_roundtrip() throws IOException {
        ZrleEncodingData original = ZrleEncodingData.builder()
                .zlibData(bytes(0xDE, 0xAD, 0xBE, 0xEF)).build();
        byte[] wire = capture(original::write);
        assertEquals(original, ZrleEncodingData.parse(stream(wire)));
    }

    // -------------------------------------------------------------------------
    // §7.8.1 CursorEncodingData (pseudo-encoding, type = -239)
    // -------------------------------------------------------------------------

    @Test
    void cursor_parse_pixelsAndBitmask() throws IOException {
        // 2x2 cursor, 32bpp: 4 pixels * 4 bytes = 16 pixel bytes
        // Bitmask: ceil(2/8)*2 = 1*2 = 2 bytes
        byte[] pixels  = bytes(0x01, 0x02, 0x03, 0x04,
                               0x05, 0x06, 0x07, 0x08,
                               0x09, 0x0A, 0x0B, 0x0C,
                               0x0D, 0x0E, 0x0F, 0x10);
        byte[] bitmask = bytes(0xC0, 0xC0); // top-left 2 bits set in each row
        byte[] payload = new byte[18];
        System.arraycopy(pixels,  0, payload, 0,  16);
        System.arraycopy(bitmask, 0, payload, 16, 2);
        CursorEncodingData data = CursorEncodingData.parse(stream(payload), 2, 2, rgb888PixelFormat());
        assertArrayEquals(pixels,  data.getPixels());
        assertArrayEquals(bitmask, data.getBitmask());
    }

    @Test
    void cursor_parse_1x1() throws IOException {
        // 1x1 cursor: 4 pixel bytes + 1 bitmask byte (ceil(1/8)*1)
        byte[] payload = bytes(0xFF, 0x00, 0xFF, 0x00, 0x80); // pixel + mask
        CursorEncodingData data = CursorEncodingData.parse(stream(payload), 1, 1, rgb888PixelFormat());
        assertArrayEquals(bytes(0xFF, 0x00, 0xFF, 0x00), data.getPixels());
        assertArrayEquals(bytes(0x80), data.getBitmask());
    }

    @Test
    void cursor_bitmaskWidth_roundsUpToBytes() throws IOException {
        // 9x1 cursor, 32bpp: 9 pixels * 4 = 36 pixel bytes
        // Bitmask: ceil(9/8)*1 = 2 bytes
        byte[] pixels  = new byte[36];
        byte[] bitmask = bytes(0xFF, 0x80); // all 9 bits set
        byte[] payload = new byte[38];
        System.arraycopy(pixels, 0, payload, 0, 36);
        System.arraycopy(bitmask, 0, payload, 36, 2);
        CursorEncodingData data = CursorEncodingData.parse(stream(payload), 9, 1, rgb888PixelFormat());
        assertEquals(36, data.getPixels().length);
        assertEquals(2,  data.getBitmask().length);
    }

    @Test
    void cursor_write_pixelsFirstThenBitmask() throws IOException {
        byte[] pixels  = bytes(0x01, 0x02, 0x03, 0x04);
        byte[] bitmask = bytes(0x80);
        CursorEncodingData data = CursorEncodingData.builder()
                .pixels(pixels).bitmask(bitmask).build();
        byte[] w = capture(data::write);
        assertEquals(5, w.length);
        assertEquals(0x01, w[0] & 0xFF);
        assertEquals(0x80, w[4] & 0xFF);
    }

    @Test
    void cursor_getEncodingType() {
        assertEquals(EncodingType.CURSOR,
                CursorEncodingData.builder().pixels(new byte[0]).bitmask(new byte[0])
                        .build().getEncodingType());
    }

    @Test
    void cursor_roundtrip() throws IOException {
        byte[] pixels  = bytes(0xAA, 0xBB, 0xCC, 0xDD);
        byte[] bitmask = bytes(0xFF);
        CursorEncodingData original = CursorEncodingData.builder()
                .pixels(pixels).bitmask(bitmask).build();
        byte[] wire = capture(original::write);
        CursorEncodingData parsed = CursorEncodingData.parse(stream(wire), 1, 1, rgb888PixelFormat());
        assertEquals(original, parsed);
    }

    // -------------------------------------------------------------------------
    // §7.8.2 DesktopSizeEncodingData (pseudo-encoding, type = -223)
    // -------------------------------------------------------------------------

    @Test
    void desktopSize_parse_noPayload() throws IOException {
        // DesktopSize has zero payload bytes; parse() consumes nothing
        InputStream in = stream(0xAB);
        DesktopSizeEncodingData data = DesktopSizeEncodingData.parse(in);
        assertNotNull(data);
        assertEquals(0xAB, in.read()); // sentinel untouched
    }

    @Test
    void desktopSize_write_noBytes() throws IOException {
        // write() produces zero bytes
        assertEquals(0, capture(DesktopSizeEncodingData.builder().build()::write).length);
    }

    @Test
    void desktopSize_getEncodingType() {
        assertEquals(EncodingType.DESKTOP_SIZE,
                DesktopSizeEncodingData.builder().build().getEncodingType());
    }

    @Test
    void desktopSize_equals_alwaysTrue() {
        assertEquals(DesktopSizeEncodingData.builder().build(),
                     DesktopSizeEncodingData.builder().build());
    }

    // -------------------------------------------------------------------------
    // EncodingType constants — verifying RFC §7.7 and §7.8 wire codes
    // -------------------------------------------------------------------------

    @Test
    void encodingType_wireCodes_matchRFC() {
        assertEquals(0,    EncodingType.RAW.getCode());
        assertEquals(1,    EncodingType.COPY_RECT.getCode());
        assertEquals(2,    EncodingType.RRE.getCode());
        assertEquals(5,    EncodingType.HEXTILE.getCode());
        assertEquals(15,   EncodingType.TRLE.getCode());
        assertEquals(16,   EncodingType.ZRLE.getCode());
        assertEquals(-239, EncodingType.CURSOR.getCode());       // §7.8.1
        assertEquals(-223, EncodingType.DESKTOP_SIZE.getCode()); // §7.8.2
    }

    @Test
    void encodingType_fromCode_knownCodes() {
        assertEquals(EncodingType.RAW,          EncodingType.fromCode(0));
        assertEquals(EncodingType.COPY_RECT,    EncodingType.fromCode(1));
        assertEquals(EncodingType.RRE,          EncodingType.fromCode(2));
        assertEquals(EncodingType.HEXTILE,      EncodingType.fromCode(5));
        assertEquals(EncodingType.TRLE,         EncodingType.fromCode(15));
        assertEquals(EncodingType.ZRLE,         EncodingType.fromCode(16));
        assertEquals(EncodingType.CURSOR,       EncodingType.fromCode(-239));
        assertEquals(EncodingType.DESKTOP_SIZE, EncodingType.fromCode(-223));
    }

    @Test
    void encodingType_fromCode_unknownReturnsNull() {
        assertNull(EncodingType.fromCode(99));
        assertNull(EncodingType.fromCode(-1));
    }
}
