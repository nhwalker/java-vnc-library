package io.github.nwalker.vnc.core.client;

import io.github.nwalker.vnc.core.PixelFormat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;

import static io.github.nwalker.vnc.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for all client-to-server messages (RFC 6143 §7.5).
 *
 * <ul>
 *   <li>§7.5.1 SetPixelFormat:         type=0, 3 padding, 16-byte PIXEL_FORMAT</li>
 *   <li>§7.5.2 SetEncodings:           type=2, 1 padding, U16 count, S32[] types</li>
 *   <li>§7.5.3 FramebufferUpdateRequest: type=3, U8 incr, U16 x/y/w/h</li>
 *   <li>§7.5.4 KeyEvent:               type=4, U8 down, 2 padding, U32 key</li>
 *   <li>§7.5.5 PointerEvent:           type=5, U8 mask, U16 x/y</li>
 *   <li>§7.5.6 ClientCutText:          type=6, 3 padding, U32 len, bytes</li>
 * </ul>
 */
class ClientMessagesTest {

    // -------------------------------------------------------------------------
    // §7.5.1 SetPixelFormat (message-type = 0)
    // -------------------------------------------------------------------------

    @Test
    void setPixelFormat_write_typeByteIsZero() throws IOException {
        SetPixelFormat msg = SetPixelFormat.builder().pixelFormat(rgb888PixelFormat()).build();
        byte[] w = capture(msg::write);
        assertEquals(0x00, w[0] & 0xFF); // message-type = 0
    }

    @Test
    void setPixelFormat_write_3PaddingAfterType() throws IOException {
        byte[] w = capture(SetPixelFormat.builder().pixelFormat(rgb888PixelFormat()).build()::write);
        assertEquals(0x00, w[1]); // padding
        assertEquals(0x00, w[2]); // padding
        assertEquals(0x00, w[3]); // padding
    }

    @Test
    void setPixelFormat_write_pixelFormatAt4() throws IOException {
        byte[] w = capture(SetPixelFormat.builder().pixelFormat(rgb888PixelFormat()).build()::write);
        byte[] embeddedPf = Arrays.copyOfRange(w, 4, 20);
        assertArrayEquals(rgb888PixelFormatBytes(), embeddedPf);
    }

    @Test
    void setPixelFormat_write_totalIs20Bytes() throws IOException {
        // 1 type + 3 padding + 16 pixel-format = 20
        assertEquals(20, capture(SetPixelFormat.builder().pixelFormat(rgb888PixelFormat()).build()::write).length);
    }

    @Test
    void setPixelFormat_parse_typeAlreadyConsumed() throws IOException {
        // parse() assumes type byte already consumed; feed only padding + pixel-format
        byte[] payloadAfterType = new byte[3 + 16]; // 3 padding + 16 pixel format
        System.arraycopy(rgb888PixelFormatBytes(), 0, payloadAfterType, 3, 16);
        SetPixelFormat msg = SetPixelFormat.parse(stream(payloadAfterType));
        assertEquals(rgb888PixelFormat(), msg.getPixelFormat());
    }

    @Test
    void setPixelFormat_roundtrip() throws IOException {
        SetPixelFormat original = SetPixelFormat.builder().pixelFormat(rgb888PixelFormat()).build();
        // Round-trip via full write then parse (skipping type byte)
        byte[] wire = capture(original::write);
        SetPixelFormat parsed = SetPixelFormat.parse(stream(Arrays.copyOfRange(wire, 1, wire.length)));
        assertEquals(original, parsed);
    }

    // -------------------------------------------------------------------------
    // §7.5.2 SetEncodings (message-type = 2)
    // -------------------------------------------------------------------------

    @Test
    void setEncodings_write_typeByteIs2() throws IOException {
        byte[] w = capture(SetEncodings.builder().build()::write);
        assertEquals(0x02, w[0] & 0xFF);
    }

    @Test
    void setEncodings_write_1PaddingAfterType() throws IOException {
        byte[] w = capture(SetEncodings.builder().build()::write);
        assertEquals(0x00, w[1]); // 1 byte padding
    }

    @Test
    void setEncodings_write_countIsBigEndianU16() throws IOException {
        SetEncodings msg = SetEncodings.builder()
                .addEncodingType(0).addEncodingType(1).addEncodingType(2).build();
        byte[] w = capture(msg::write);
        // bytes 2-3: U16 number-of-encodings = 3
        assertEquals(0x00, w[2]);
        assertEquals(0x03, w[3]);
    }

    @Test
    void setEncodings_write_encodingsAreS32() throws IOException {
        // Cursor pseudo-encoding = -239 = 0xFFFFFF11
        SetEncodings msg = SetEncodings.builder()
                .addEncodingType(-239).build();
        byte[] w = capture(msg::write);
        // bytes 4-7: S32(-239) = 0xFF, 0xFF, 0xFF, 0x11
        assertEquals(0xFF, w[4] & 0xFF);
        assertEquals(0xFF, w[5] & 0xFF);
        assertEquals(0xFF, w[6] & 0xFF);
        assertEquals(0x11, w[7] & 0xFF);
    }

    @Test
    void setEncodings_write_desktopSizePseudoEncoding_isNegative() throws IOException {
        // DesktopSize = -223 = 0xFFFFFF21
        SetEncodings msg = SetEncodings.builder().addEncodingType(-223).build();
        byte[] w = capture(msg::write);
        assertEquals(0xFF, w[4] & 0xFF);
        assertEquals(0xFF, w[5] & 0xFF);
        assertEquals(0xFF, w[6] & 0xFF);
        assertEquals(0x21, w[7] & 0xFF);
    }

    @Test
    void setEncodings_parse_typeAlreadyConsumed() throws IOException {
        // payload: 1 pad + U16(2) + S32(0) + S32(1)
        byte[] payload = bytes(0x00, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01);
        SetEncodings msg = SetEncodings.parse(stream(payload));
        assertEquals(2, msg.getEncodingTypes().size());
        assertEquals(0, (int) msg.getEncodingTypes().get(0));
        assertEquals(1, (int) msg.getEncodingTypes().get(1));
    }

    @Test
    void setEncodings_roundtrip() throws IOException {
        SetEncodings original = SetEncodings.builder()
                .addEncodingType(16)   // ZRLE
                .addEncodingType(15)   // TRLE
                .addEncodingType(1)    // CopyRect
                .addEncodingType(-239) // Cursor
                .addEncodingType(-223) // DesktopSize
                .build();
        byte[] wire = capture(original::write);
        SetEncodings parsed = SetEncodings.parse(stream(Arrays.copyOfRange(wire, 1, wire.length)));
        assertEquals(original, parsed);
    }

    // -------------------------------------------------------------------------
    // §7.5.3 FramebufferUpdateRequest (message-type = 3)
    // -------------------------------------------------------------------------

    @Test
    void fbUpdateRequest_write_typeByteIs3() throws IOException {
        byte[] w = capture(FramebufferUpdateRequest.builder().build()::write);
        assertEquals(0x03, w[0] & 0xFF);
    }

    @Test
    void fbUpdateRequest_write_incrementalFlag() throws IOException {
        byte[] wTrue  = capture(FramebufferUpdateRequest.builder().incremental(true).build()::write);
        byte[] wFalse = capture(FramebufferUpdateRequest.builder().incremental(false).build()::write);
        assertNotEquals(0, wTrue[1] & 0xFF);
        assertEquals(0, wFalse[1] & 0xFF);
    }

    @Test
    void fbUpdateRequest_write_xyzwAreBigEndianU16() throws IOException {
        FramebufferUpdateRequest msg = FramebufferUpdateRequest.builder()
                .incremental(true).x(256).y(512).width(1024).height(768).build();
        byte[] w = capture(msg::write);
        // offset 2-3: x=256 = 0x01, 0x00
        assertEquals(0x01, w[2] & 0xFF);
        assertEquals(0x00, w[3] & 0xFF);
        // offset 4-5: y=512 = 0x02, 0x00
        assertEquals(0x02, w[4] & 0xFF);
        assertEquals(0x00, w[5] & 0xFF);
        // offset 6-7: width=1024 = 0x04, 0x00
        assertEquals(0x04, w[6] & 0xFF);
        assertEquals(0x00, w[7] & 0xFF);
        // offset 8-9: height=768 = 0x03, 0x00
        assertEquals(0x03, w[8] & 0xFF);
        assertEquals(0x00, w[9] & 0xFF);
    }

    @Test
    void fbUpdateRequest_write_totalIs10Bytes() throws IOException {
        // 1 type + 1 incr + 2 x + 2 y + 2 w + 2 h = 10
        assertEquals(10, capture(FramebufferUpdateRequest.builder().build()::write).length);
    }

    @Test
    void fbUpdateRequest_parse_typeAlreadyConsumed() throws IOException {
        // incr=1, x=100, y=200, w=640, h=480
        byte[] payload = bytes(0x01,           // incremental
                               0x00, 0x64,     // x = 100
                               0x00, 0xC8,     // y = 200
                               0x02, 0x80,     // w = 640
                               0x01, 0xE0);    // h = 480
        FramebufferUpdateRequest msg = FramebufferUpdateRequest.parse(stream(payload));
        assertTrue(msg.isIncremental());
        assertEquals(100, msg.getX());
        assertEquals(200, msg.getY());
        assertEquals(640, msg.getWidth());
        assertEquals(480, msg.getHeight());
    }

    @Test
    void fbUpdateRequest_roundtrip() throws IOException {
        FramebufferUpdateRequest original = FramebufferUpdateRequest.builder()
                .incremental(true).x(10).y(20).width(800).height(600).build();
        byte[] wire = capture(original::write);
        FramebufferUpdateRequest parsed = FramebufferUpdateRequest.parse(
                stream(Arrays.copyOfRange(wire, 1, wire.length)));
        assertEquals(original, parsed);
    }

    // -------------------------------------------------------------------------
    // §7.5.4 KeyEvent (message-type = 4)
    // -------------------------------------------------------------------------

    @Test
    void keyEvent_write_typeByteIs4() throws IOException {
        byte[] w = capture(KeyEvent.builder().down(true).key(0x41).build()::write);
        assertEquals(0x04, w[0] & 0xFF);
    }

    @Test
    void keyEvent_write_downFlagAtByte1() throws IOException {
        byte[] wDown = capture(KeyEvent.builder().down(true).key(0x41).build()::write);
        byte[] wUp   = capture(KeyEvent.builder().down(false).key(0x41).build()::write);
        assertNotEquals(0, wDown[1] & 0xFF);
        assertEquals(0, wUp[1] & 0xFF);
    }

    @Test
    void keyEvent_write_2PaddingBytesAtOffset2and3() throws IOException {
        byte[] w = capture(KeyEvent.builder().down(true).key(0x41).build()::write);
        assertEquals(0x00, w[2]); // padding
        assertEquals(0x00, w[3]); // padding
    }

    @Test
    void keyEvent_write_keyIsU32BigEndianAtOffset4() throws IOException {
        // key = 0xFF08 (BackSpace from RFC table)
        byte[] w = capture(KeyEvent.builder().down(true).key(0xFF08L).build()::write);
        assertEquals(0x00, w[4] & 0xFF);
        assertEquals(0x00, w[5] & 0xFF);
        assertEquals(0xFF, w[6] & 0xFF);
        assertEquals(0x08, w[7] & 0xFF);
    }

    @Test
    void keyEvent_write_totalIs8Bytes() throws IOException {
        // 1 type + 1 down + 2 padding + 4 key = 8
        assertEquals(8, capture(KeyEvent.builder().down(true).key(0x41).build()::write).length);
    }

    @Test
    void keyEvent_parse_typeAlreadyConsumed() throws IOException {
        // down=1, padding(2), key=0xFFFF (Delete)
        byte[] payload = bytes(0x01, 0x00, 0x00, 0x00, 0x00, 0xFF, 0xFF);
        KeyEvent msg = KeyEvent.parse(stream(payload));
        assertTrue(msg.isDown());
        assertEquals(0xFFFFL, msg.getKey());
    }

    @Test
    void keyEvent_rfcKeysymConstants() {
        // Verify RFC table values (§7.5.4)
        assertEquals(0xFF08L, KeyEvent.KEY_BACKSPACE);
        assertEquals(0xFF09L, KeyEvent.KEY_TAB);
        assertEquals(0xFF0DL, KeyEvent.KEY_RETURN);
        assertEquals(0xFF1BL, KeyEvent.KEY_ESCAPE);
        assertEquals(0xFF63L, KeyEvent.KEY_INSERT);
        assertEquals(0xFFFFL, KeyEvent.KEY_DELETE);
        assertEquals(0xFF50L, KeyEvent.KEY_HOME);
        assertEquals(0xFF57L, KeyEvent.KEY_END);
        assertEquals(0xFF55L, KeyEvent.KEY_PAGE_UP);
        assertEquals(0xFF56L, KeyEvent.KEY_PAGE_DOWN);
        assertEquals(0xFF51L, KeyEvent.KEY_LEFT);
        assertEquals(0xFF52L, KeyEvent.KEY_UP);
        assertEquals(0xFF53L, KeyEvent.KEY_RIGHT);
        assertEquals(0xFF54L, KeyEvent.KEY_DOWN);
        assertEquals(0xFFBEL, KeyEvent.KEY_F1);
        assertEquals(0xFFC9L, KeyEvent.KEY_F12);
        assertEquals(0xFFE1L, KeyEvent.KEY_SHIFT_L);
        assertEquals(0xFFE2L, KeyEvent.KEY_SHIFT_R);
        assertEquals(0xFFE3L, KeyEvent.KEY_CTRL_L);
        assertEquals(0xFFE4L, KeyEvent.KEY_CTRL_R);
        assertEquals(0xFFE7L, KeyEvent.KEY_META_L);
        assertEquals(0xFFE8L, KeyEvent.KEY_META_R);
        assertEquals(0xFFE9L, KeyEvent.KEY_ALT_L);
        assertEquals(0xFFEAL, KeyEvent.KEY_ALT_R);
    }

    @Test
    void keyEvent_roundtrip() throws IOException {
        KeyEvent original = KeyEvent.builder().down(true).key(KeyEvent.KEY_RETURN).build();
        byte[] wire = capture(original::write);
        KeyEvent parsed = KeyEvent.parse(stream(Arrays.copyOfRange(wire, 1, wire.length)));
        assertEquals(original, parsed);
    }

    // -------------------------------------------------------------------------
    // §7.5.5 PointerEvent (message-type = 5)
    // -------------------------------------------------------------------------

    @Test
    void pointerEvent_write_typeByteIs5() throws IOException {
        byte[] w = capture(PointerEvent.builder().buttonMask(0).x(0).y(0).build()::write);
        assertEquals(0x05, w[0] & 0xFF);
    }

    @Test
    void pointerEvent_write_buttonMaskAtByte1() throws IOException {
        // Left+Right buttons pressed: 0x01 | 0x04 = 0x05
        byte[] w = capture(PointerEvent.builder()
                .buttonMask(PointerEvent.BUTTON_LEFT | PointerEvent.BUTTON_RIGHT)
                .x(0).y(0).build()::write);
        assertEquals(0x05, w[1] & 0xFF);
    }

    @Test
    void pointerEvent_write_xyAreBigEndianU16AtOffset2() throws IOException {
        byte[] w = capture(PointerEvent.builder().buttonMask(0).x(256).y(512).build()::write);
        // x=256 at bytes 2-3 (big-endian): 0x01, 0x00
        assertEquals(0x01, w[2] & 0xFF);
        assertEquals(0x00, w[3] & 0xFF);
        // y=512 at bytes 4-5 (big-endian): 0x02, 0x00
        assertEquals(0x02, w[4] & 0xFF);
        assertEquals(0x00, w[5] & 0xFF);
    }

    @Test
    void pointerEvent_write_totalIs6Bytes() throws IOException {
        assertEquals(6, capture(PointerEvent.builder().build()::write).length);
    }

    @Test
    void pointerEvent_buttonMaskConstants() {
        // RFC §7.5.5: buttons 1-8 in bits 0-7
        assertEquals(0x01, PointerEvent.BUTTON_LEFT);
        assertEquals(0x02, PointerEvent.BUTTON_MIDDLE);
        assertEquals(0x04, PointerEvent.BUTTON_RIGHT);
        assertEquals(0x08, PointerEvent.BUTTON_WHEEL_UP);
        assertEquals(0x10, PointerEvent.BUTTON_WHEEL_DOWN);
    }

    @Test
    void pointerEvent_parse_typeAlreadyConsumed() throws IOException {
        // mask=0x03 (left+middle), x=100, y=200
        byte[] payload = bytes(0x03, 0x00, 0x64, 0x00, 0xC8);
        PointerEvent msg = PointerEvent.parse(stream(payload));
        assertEquals(0x03, msg.getButtonMask());
        assertEquals(100, msg.getX());
        assertEquals(200, msg.getY());
    }

    @Test
    void pointerEvent_roundtrip() throws IOException {
        PointerEvent original = PointerEvent.builder()
                .buttonMask(PointerEvent.BUTTON_LEFT).x(1280).y(720).build();
        byte[] wire = capture(original::write);
        PointerEvent parsed = PointerEvent.parse(stream(Arrays.copyOfRange(wire, 1, wire.length)));
        assertEquals(original, parsed);
    }

    // -------------------------------------------------------------------------
    // §7.5.6 ClientCutText (message-type = 6)
    // -------------------------------------------------------------------------

    @Test
    void clientCutText_write_typeByteIs6() throws IOException {
        byte[] w = capture(ClientCutText.builder().text("hi").build()::write);
        assertEquals(0x06, w[0] & 0xFF);
    }

    @Test
    void clientCutText_write_3PaddingAtOffset1() throws IOException {
        byte[] w = capture(ClientCutText.builder().text("hi").build()::write);
        assertEquals(0x00, w[1]);
        assertEquals(0x00, w[2]);
        assertEquals(0x00, w[3]);
    }

    @Test
    void clientCutText_write_lengthIsU32BigEndianAtOffset4() throws IOException {
        // "abc" = 3 bytes
        byte[] w = capture(ClientCutText.builder().text("abc").build()::write);
        assertEquals(0x00, w[4]);
        assertEquals(0x00, w[5]);
        assertEquals(0x00, w[6]);
        assertEquals(0x03, w[7]);
    }

    @Test
    void clientCutText_write_textBytesAfterLength() throws IOException {
        byte[] w = capture(ClientCutText.builder().text("AB").build()::write);
        assertEquals('A', w[8]);
        assertEquals('B', w[9]);
    }

    @Test
    void clientCutText_parse_typeAlreadyConsumed() throws IOException {
        // 3 padding + U32(5) + "hello"
        byte[] payload = bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x05,
                               'h', 'e', 'l', 'l', 'o');
        ClientCutText msg = ClientCutText.parse(stream(payload));
        assertEquals("hello", msg.getText());
    }

    @Test
    void clientCutText_parse_emptyText() throws IOException {
        // 3 padding + U32(0)
        byte[] payload = bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00);
        ClientCutText msg = ClientCutText.parse(stream(payload));
        assertEquals("", msg.getText());
    }

    @Test
    void clientCutText_roundtrip() throws IOException {
        ClientCutText original = ClientCutText.builder().text("Hello RFB").build();
        byte[] wire = capture(original::write);
        ClientCutText parsed = ClientCutText.parse(stream(Arrays.copyOfRange(wire, 1, wire.length)));
        assertEquals(original, parsed);
    }
}
