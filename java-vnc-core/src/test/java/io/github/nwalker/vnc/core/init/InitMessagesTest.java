package io.github.nwalker.vnc.core.init;

import io.github.nwalker.vnc.core.PixelFormat;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static io.github.nwalker.vnc.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for initialization messages (RFC 6143 §7.3).
 *
 * <ul>
 *   <li>{@link ClientInit} (§7.3.1): U8 shared-flag</li>
 *   <li>{@link ServerInit} (§7.3.2): U16 w + U16 h + PIXEL_FORMAT + U32 name-len + name</li>
 * </ul>
 */
class InitMessagesTest {

    // -------------------------------------------------------------------------
    // ClientInit (§7.3.1)
    // -------------------------------------------------------------------------

    @Test
    void clientInit_parse_sharedTrue_nonZeroByte() throws IOException {
        ClientInit msg = ClientInit.parse(stream(0x01));
        assertTrue(msg.isShared());
    }

    @Test
    void clientInit_parse_sharedFalse_zeroByte() throws IOException {
        ClientInit msg = ClientInit.parse(stream(0x00));
        assertFalse(msg.isShared());
    }

    @Test
    void clientInit_parse_anyNonZero_isShared() throws IOException {
        // RFC: "non-zero (true)"
        ClientInit msg = ClientInit.parse(stream(0xFF));
        assertTrue(msg.isShared());
    }

    @Test
    void clientInit_write_shared_isOne() throws IOException {
        byte[] w = capture(ClientInit.builder().shared(true).build()::write);
        assertEquals(1, w.length);
        assertNotEquals(0, w[0] & 0xFF);
    }

    @Test
    void clientInit_write_notShared_isZero() throws IOException {
        byte[] w = capture(ClientInit.builder().shared(false).build()::write);
        assertEquals(1, w.length);
        assertEquals(0, w[0] & 0xFF);
    }

    @Test
    void clientInit_roundtrip_shared() throws IOException {
        ClientInit original = ClientInit.builder().shared(true).build();
        byte[] wire = capture(original::write);
        assertEquals(original, ClientInit.parse(stream(wire)));
    }

    @Test
    void clientInit_roundtrip_notShared() throws IOException {
        ClientInit original = ClientInit.builder().shared(false).build();
        byte[] wire = capture(original::write);
        assertEquals(original, ClientInit.parse(stream(wire)));
    }

    // -------------------------------------------------------------------------
    // ServerInit (§7.3.2)
    // -------------------------------------------------------------------------

    /**
     * Builds a valid ServerInit wire buffer.
     * Layout: U16 width, U16 height, 16-byte PIXEL_FORMAT, U32 name-length, name bytes.
     */
    private byte[] buildServerInitWire(int width, int height, byte[] pixelFormatBytes,
                                        String name) throws IOException {
        byte[] nameBytes = name.getBytes(StandardCharsets.ISO_8859_1);
        byte[] buf = new byte[2 + 2 + 16 + 4 + nameBytes.length];
        // U16 width (big-endian)
        buf[0] = (byte) ((width >>> 8) & 0xFF);
        buf[1] = (byte) (width & 0xFF);
        // U16 height (big-endian)
        buf[2] = (byte) ((height >>> 8) & 0xFF);
        buf[3] = (byte) (height & 0xFF);
        // 16-byte pixel format
        System.arraycopy(pixelFormatBytes, 0, buf, 4, 16);
        // U32 name-length (big-endian)
        buf[20] = (byte) ((nameBytes.length >>> 24) & 0xFF);
        buf[21] = (byte) ((nameBytes.length >>> 16) & 0xFF);
        buf[22] = (byte) ((nameBytes.length >>> 8) & 0xFF);
        buf[23] = (byte) (nameBytes.length & 0xFF);
        // name bytes
        System.arraycopy(nameBytes, 0, buf, 24, nameBytes.length);
        return buf;
    }

    @Test
    void serverInit_parse_fieldOrder() throws IOException {
        byte[] wire = buildServerInitWire(1920, 1080, rgb888PixelFormatBytes(), "My Desktop");
        ServerInit msg = ServerInit.parse(stream(wire));

        assertEquals(1920, msg.getFramebufferWidth());
        assertEquals(1080, msg.getFramebufferHeight());
        assertEquals("My Desktop", msg.getName());
        assertNotNull(msg.getPixelFormat());
        assertEquals(32, msg.getPixelFormat().getBitsPerPixel());
    }

    @Test
    void serverInit_parse_widthHeightAreBigEndianU16() throws IOException {
        // width=0x0100 (256), height=0x0200 (512) to verify byte order
        byte[] wire = buildServerInitWire(256, 512, rgb888PixelFormatBytes(), "test");
        ServerInit msg = ServerInit.parse(stream(wire));
        assertEquals(256, msg.getFramebufferWidth());
        assertEquals(512, msg.getFramebufferHeight());
    }

    @Test
    void serverInit_parse_emptyName() throws IOException {
        byte[] wire = buildServerInitWire(800, 600, rgb888PixelFormatBytes(), "");
        ServerInit msg = ServerInit.parse(stream(wire));
        assertEquals("", msg.getName());
    }

    @Test
    void serverInit_parse_nameLengthIsBigEndianU32() throws IOException {
        // Manually set name-length to 0x00000003 = 3 and name to "abc"
        byte[] wire = buildServerInitWire(100, 100, rgb888PixelFormatBytes(), "abc");
        ServerInit msg = ServerInit.parse(stream(wire));
        assertEquals("abc", msg.getName());
    }

    @Test
    void serverInit_write_fieldOrder() throws IOException {
        PixelFormat pf = rgb888PixelFormat();
        ServerInit msg = ServerInit.builder()
                .framebufferWidth(1920).framebufferHeight(1080)
                .pixelFormat(pf).name("Desktop").build();

        byte[] w = capture(msg::write);

        // bytes 0-1: U16 width = 1920 = 0x07, 0x80
        assertEquals(0x07, w[0] & 0xFF);
        assertEquals(0x80, w[1] & 0xFF);
        // bytes 2-3: U16 height = 1080 = 0x04, 0x38
        assertEquals(0x04, w[2] & 0xFF);
        assertEquals(0x38, w[3] & 0xFF);
        // bytes 4-19: pixel format (16 bytes)
        byte[] writtenPf = new byte[16];
        System.arraycopy(w, 4, writtenPf, 0, 16);
        assertArrayEquals(rgb888PixelFormatBytes(), writtenPf);
        // bytes 20-23: U32 name-length = 7
        assertEquals(0x00, w[20]);
        assertEquals(0x00, w[21]);
        assertEquals(0x00, w[22]);
        assertEquals(0x07, w[23]);
        // bytes 24+: name
        assertEquals('D', (char) w[24]);
    }

    @Test
    void serverInit_roundtrip() throws IOException {
        ServerInit original = ServerInit.builder()
                .framebufferWidth(1280).framebufferHeight(720)
                .pixelFormat(rgb888PixelFormat())
                .name("RFB Test Desktop")
                .build();
        byte[] wire = capture(original::write);
        assertEquals(original, ServerInit.parse(stream(wire)));
    }

    @Test
    void serverInit_parse_consumesExactBytes() throws IOException {
        byte[] wire = buildServerInitWire(640, 480, rgb888PixelFormatBytes(), "Hi");
        byte[] withSentinel = new byte[wire.length + 1];
        System.arraycopy(wire, 0, withSentinel, 0, wire.length);
        withSentinel[wire.length] = (byte) 0xAB;
        InputStream in = stream(withSentinel);
        ServerInit.parse(in);
        assertEquals(0xAB, in.read());
    }
}
