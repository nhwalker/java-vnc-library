package io.github.nwalker.vnc.core.server;

import io.github.nwalker.vnc.core.ColorMapEntry;
import io.github.nwalker.vnc.core.EncodingType;
import io.github.nwalker.vnc.core.encoding.CopyRectEncodingData;
import io.github.nwalker.vnc.core.encoding.DesktopSizeEncodingData;
import io.github.nwalker.vnc.core.encoding.RawEncodingData;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static io.github.nwalker.vnc.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for server-to-client messages (RFC 6143 §7.6).
 *
 * <ul>
 *   <li>§7.6.1 FramebufferUpdate: type=0, 1 pad, U16 count, rectangles</li>
 *   <li>§7.6.2 SetColorMapEntries: type=1, 1 pad, U16 first-color, U16 count, entries</li>
 *   <li>§7.6.3 Bell: type=2, no payload</li>
 *   <li>§7.6.4 ServerCutText: type=3, 3 pad, U32 len, text</li>
 * </ul>
 *
 * <p>All parse() methods assume the message-type byte has already been consumed
 * by the caller (the dispatcher reads it first to route to the correct class).</p>
 */
class ServerMessagesTest {

    // -------------------------------------------------------------------------
    // §7.6.1 FramebufferUpdate (message-type = 0)
    // -------------------------------------------------------------------------

    @Test
    void fbUpdate_write_typeByteIs0() throws IOException {
        byte[] w = capture(FramebufferUpdate.builder().build()::write);
        assertEquals(0x00, w[0] & 0xFF);
    }

    @Test
    void fbUpdate_write_1PaddingAfterType() throws IOException {
        byte[] w = capture(FramebufferUpdate.builder().build()::write);
        assertEquals(0x00, w[1]);
    }

    @Test
    void fbUpdate_write_countIsBigEndianU16() throws IOException {
        // Two rectangles
        byte[] w = capture(FramebufferUpdate.builder()
                .addRectangle(Rectangle.builder().x(0).y(0).width(1).height(1)
                        .encodingCode(EncodingType.RAW.getCode())
                        .encodingData(RawEncodingData.builder().pixels(new byte[4]).build())
                        .build())
                .addRectangle(Rectangle.builder().x(1).y(0).width(1).height(1)
                        .encodingCode(EncodingType.RAW.getCode())
                        .encodingData(RawEncodingData.builder().pixels(new byte[4]).build())
                        .build())
                .build()::write);
        // bytes 2-3: count=2
        assertEquals(0x00, w[2]);
        assertEquals(0x02, w[3]);
    }

    @Test
    void fbUpdate_write_emptyUpdate_is4Bytes() throws IOException {
        // 1 type + 1 padding + 2 count = 4 bytes when no rectangles
        byte[] w = capture(FramebufferUpdate.builder().build()::write);
        assertEquals(4, w.length);
    }

    @Test
    void fbUpdate_parse_typeAlreadyConsumed() throws IOException {
        // 1 padding + U16(0) = no rectangles
        FramebufferUpdate msg = FramebufferUpdate.parse(
                stream(0x00, 0x00, 0x00), rgb888PixelFormat());
        assertEquals(0, msg.getRectangles().size());
    }

    @Test
    void fbUpdate_parse_oneRectangle() throws IOException {
        // 1 pad + U16(1) + x=10(U16) + y=20(U16) + w=1(U16) + h=1(U16) + S32(0=RAW) + 4 pixel bytes
        byte[] payload = bytes(
                0x00,           // padding
                0x00, 0x01,     // count=1
                0x00, 0x0A,     // x=10
                0x00, 0x14,     // y=20
                0x00, 0x01,     // w=1
                0x00, 0x01,     // h=1
                0x00, 0x00, 0x00, 0x00, // encoding=0 (RAW)
                0x01, 0x02, 0x03, 0x04  // 1x1 32bpp raw pixel
        );
        FramebufferUpdate msg = FramebufferUpdate.parse(stream(payload), rgb888PixelFormat());
        assertEquals(1, msg.getRectangles().size());
        Rectangle r = msg.getRectangles().get(0);
        assertEquals(10, r.getX());
        assertEquals(20, r.getY());
        assertEquals(1, r.getWidth());
        assertEquals(1, r.getHeight());
        assertEquals(EncodingType.RAW, r.getEncodingType());
    }

    @Test
    void fbUpdate_roundtrip_empty() throws IOException {
        FramebufferUpdate original = FramebufferUpdate.builder().build();
        byte[] wire = capture(original::write);
        FramebufferUpdate parsed = FramebufferUpdate.parse(
                stream(Arrays.copyOfRange(wire, 1, wire.length)), rgb888PixelFormat());
        assertEquals(original, parsed);
    }

    @Test
    void fbUpdate_roundtrip_withCopyRectRectangle() throws IOException {
        Rectangle rect = Rectangle.builder()
                .x(5).y(10).width(20).height(30)
                .encodingCode(EncodingType.COPY_RECT.getCode())
                .encodingData(CopyRectEncodingData.builder().srcX(0).srcY(0).build())
                .build();
        FramebufferUpdate original = FramebufferUpdate.builder().addRectangle(rect).build();
        byte[] wire = capture(original::write);
        FramebufferUpdate parsed = FramebufferUpdate.parse(
                stream(Arrays.copyOfRange(wire, 1, wire.length)), rgb888PixelFormat());
        assertEquals(original, parsed);
    }

    // -------------------------------------------------------------------------
    // Rectangle header (§7.6.1)
    // -------------------------------------------------------------------------

    @Test
    void rectangle_write_xywhAreBigEndianU16() throws IOException {
        Rectangle r = Rectangle.builder()
                .x(256).y(512).width(1024).height(768)
                .encodingCode(EncodingType.DESKTOP_SIZE.getCode())
                .encodingData(DesktopSizeEncodingData.builder().build())
                .build();
        byte[] w = capture(r::write);
        // x=256: 0x01,0x00
        assertEquals(0x01, w[0] & 0xFF);
        assertEquals(0x00, w[1] & 0xFF);
        // y=512: 0x02,0x00
        assertEquals(0x02, w[2] & 0xFF);
        assertEquals(0x00, w[3] & 0xFF);
        // w=1024: 0x04,0x00
        assertEquals(0x04, w[4] & 0xFF);
        assertEquals(0x00, w[5] & 0xFF);
        // h=768: 0x03,0x00
        assertEquals(0x03, w[6] & 0xFF);
        assertEquals(0x00, w[7] & 0xFF);
    }

    @Test
    void rectangle_write_encodingIsS32BigEndian() throws IOException {
        // DesktopSize = -223 = 0xFFFFFF21
        Rectangle r = Rectangle.builder()
                .x(0).y(0).width(100).height(100)
                .encodingCode(EncodingType.DESKTOP_SIZE.getCode())
                .encodingData(DesktopSizeEncodingData.builder().build())
                .build();
        byte[] w = capture(r::write);
        // bytes 8-11: S32(-223) = 0xFF, 0xFF, 0xFF, 0x21
        assertEquals(0xFF, w[8]  & 0xFF);
        assertEquals(0xFF, w[9]  & 0xFF);
        assertEquals(0xFF, w[10] & 0xFF);
        assertEquals(0x21, w[11] & 0xFF);
    }

    @Test
    void rectangle_write_cursorEncoding_codeIsNegative239() throws IOException {
        // Cursor = -239 = 0xFFFFFF11 (RFC §7.8.1)
        assertEquals(-239, EncodingType.CURSOR.getCode());
    }

    @Test
    void rectangle_parse_unknownEncoding_doesNotThrow() throws IOException {
        // encoding=99 (unknown), then DesktopSize-like (no data) — just the header
        // Unknown encoding data has no payload
        byte[] payload = bytes(
                0x00, 0x0A,         // x=10
                0x00, 0x0A,         // y=10
                0x00, 0x0A,         // w=10
                0x00, 0x0A,         // h=10
                0x00, 0x00, 0x00, 0x63  // encoding=99 (unknown)
                // no payload bytes since unknown encoding has none
        );
        Rectangle r = Rectangle.parse(stream(payload), rgb888PixelFormat());
        assertEquals(99, r.getEncodingCode());
        assertNull(r.getEncodingType());
    }

    // -------------------------------------------------------------------------
    // §7.6.2 SetColorMapEntries (message-type = 1)
    // -------------------------------------------------------------------------

    @Test
    void colorMap_write_typeByteIs1() throws IOException {
        byte[] w = capture(SetColorMapEntries.builder().firstColor(0).build()::write);
        assertEquals(0x01, w[0] & 0xFF);
    }

    @Test
    void colorMap_write_1PaddingAfterType() throws IOException {
        byte[] w = capture(SetColorMapEntries.builder().firstColor(0).build()::write);
        assertEquals(0x00, w[1]);
    }

    @Test
    void colorMap_write_firstColorIsBigEndianU16() throws IOException {
        // first-color=256 = 0x01, 0x00
        byte[] w = capture(SetColorMapEntries.builder().firstColor(256).build()::write);
        assertEquals(0x01, w[2] & 0xFF);
        assertEquals(0x00, w[3] & 0xFF);
    }

    @Test
    void colorMap_write_countIsBigEndianU16() throws IOException {
        SetColorMapEntries msg = SetColorMapEntries.builder()
                .firstColor(0)
                .addColor(ColorMapEntry.builder().red(0).green(0).blue(0).build())
                .addColor(ColorMapEntry.builder().red(65535).green(65535).blue(65535).build())
                .build();
        byte[] w = capture(msg::write);
        // bytes 4-5: count=2
        assertEquals(0x00, w[4]);
        assertEquals(0x02, w[5]);
    }

    @Test
    void colorMap_write_eachEntryIs6Bytes() throws IOException {
        // 1 type + 1 pad + 2 first-color + 2 count + 2 entries * 6 = 18
        SetColorMapEntries msg = SetColorMapEntries.builder()
                .firstColor(0)
                .addColor(ColorMapEntry.builder().red(0).green(0).blue(0).build())
                .addColor(ColorMapEntry.builder().red(1).green(2).blue(3).build())
                .build();
        assertEquals(18, capture(msg::write).length);
    }

    @Test
    void colorMap_parse_typeAlreadyConsumed() throws IOException {
        // 1 pad + U16(10) + U16(2) + entry1 + entry2
        byte[] payload = bytes(
                0x00,           // padding
                0x00, 0x0A,     // first-color=10
                0x00, 0x02,     // count=2
                0x00, 0x01, 0x00, 0x02, 0x00, 0x03,  // entry1: r=1,g=2,b=3
                0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF    // entry2: r=g=b=65535
        );
        SetColorMapEntries msg = SetColorMapEntries.parse(stream(payload));
        assertEquals(10, msg.getFirstColor());
        assertEquals(2, msg.getColors().size());
        assertEquals(1,     msg.getColors().get(0).getRed());
        assertEquals(2,     msg.getColors().get(0).getGreen());
        assertEquals(3,     msg.getColors().get(0).getBlue());
        assertEquals(65535, msg.getColors().get(1).getRed());
    }

    @Test
    void colorMap_roundtrip() throws IOException {
        SetColorMapEntries original = SetColorMapEntries.builder()
                .firstColor(5)
                .addColor(ColorMapEntry.builder().red(100).green(200).blue(300).build())
                .build();
        byte[] wire = capture(original::write);
        SetColorMapEntries parsed = SetColorMapEntries.parse(
                stream(Arrays.copyOfRange(wire, 1, wire.length)));
        assertEquals(original, parsed);
    }

    // -------------------------------------------------------------------------
    // §7.6.3 Bell (message-type = 2)
    // -------------------------------------------------------------------------

    @Test
    void bell_write_typeByteIs2() throws IOException {
        byte[] w = capture(Bell.builder().build()::write);
        assertEquals(0x02, w[0] & 0xFF);
    }

    @Test
    void bell_write_is1Byte() throws IOException {
        // Bell has no payload, just the type byte
        assertEquals(1, capture(Bell.builder().build()::write).length);
    }

    @Test
    void bell_parse_typeAlreadyConsumed_noBytes() throws IOException {
        // parse() consumes no bytes; stream is empty — should not throw
        Bell bell = Bell.parse(stream());
        assertNotNull(bell);
    }

    @Test
    void bell_parse_doesNotConsumeExtraBytes() throws IOException {
        InputStream in = stream(0xAB);
        Bell.parse(in);
        assertEquals(0xAB, in.read()); // sentinel untouched
    }

    @Test
    void bell_equals_sameInstance() {
        Bell a = Bell.builder().build();
        Bell b = Bell.builder().build();
        assertEquals(a, b);
    }

    // -------------------------------------------------------------------------
    // §7.6.4 ServerCutText (message-type = 3)
    // -------------------------------------------------------------------------

    @Test
    void serverCutText_write_typeByteIs3() throws IOException {
        byte[] w = capture(ServerCutText.builder().text("hi").build()::write);
        assertEquals(0x03, w[0] & 0xFF);
    }

    @Test
    void serverCutText_write_3PaddingAfterType() throws IOException {
        byte[] w = capture(ServerCutText.builder().text("hi").build()::write);
        assertEquals(0x00, w[1]);
        assertEquals(0x00, w[2]);
        assertEquals(0x00, w[3]);
    }

    @Test
    void serverCutText_write_lengthIsU32BigEndian() throws IOException {
        // "abc" = 3 bytes
        byte[] w = capture(ServerCutText.builder().text("abc").build()::write);
        assertEquals(0x00, w[4]);
        assertEquals(0x00, w[5]);
        assertEquals(0x00, w[6]);
        assertEquals(0x03, w[7]);
    }

    @Test
    void serverCutText_write_textBytesAfterLength() throws IOException {
        byte[] w = capture(ServerCutText.builder().text("RFB").build()::write);
        assertEquals('R', w[8]);
        assertEquals('F', w[9]);
        assertEquals('B', w[10]);
    }

    @Test
    void serverCutText_parse_typeAlreadyConsumed() throws IOException {
        // 3 padding + U32(5) + "world"
        byte[] payload = bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x05,
                               'w', 'o', 'r', 'l', 'd');
        ServerCutText msg = ServerCutText.parse(stream(payload));
        assertEquals("world", msg.getText());
    }

    @Test
    void serverCutText_parse_emptyText() throws IOException {
        byte[] payload = bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
        ServerCutText msg = ServerCutText.parse(stream(payload));
        assertEquals("", msg.getText());
    }

    @Test
    void serverCutText_roundtrip() throws IOException {
        ServerCutText original = ServerCutText.builder().text("Hello, VNC!").build();
        byte[] wire = capture(original::write);
        ServerCutText parsed = ServerCutText.parse(
                stream(Arrays.copyOfRange(wire, 1, wire.length)));
        assertEquals(original, parsed);
    }
}
