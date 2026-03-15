package io.github.nwalker.vnc.core.handshake;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static io.github.nwalker.vnc.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ProtocolVersionMessage} (RFC 6143 §7.1.1).
 *
 * <p>Wire format: exactly 12 ASCII bytes {@code "RFB xxx.yyy\n"} where xxx and yyy
 * are the major and minor version numbers left-padded with zeros.</p>
 */
class ProtocolVersionMessageTest {

    // Exact bytes for "RFB 003.008\n" from the RFC example (§7.1.1)
    private static final byte[] WIRE_3_8 = {
        0x52, 0x46, 0x42, 0x20,  // "RFB "
        0x30, 0x30, 0x33,        // "003"
        0x2E,                    // "."
        0x30, 0x30, 0x38,        // "008"
        0x0A                     // "\n"
    };

    @Test
    void parse_rfcExampleBytes_version38() throws IOException {
        ProtocolVersionMessage msg = ProtocolVersionMessage.parse(stream(WIRE_3_8));
        assertEquals(3, msg.getMajor());
        assertEquals(8, msg.getMinor());
    }

    @Test
    void parse_version33() throws IOException {
        byte[] wire = "RFB 003.003\n".getBytes(StandardCharsets.US_ASCII);
        ProtocolVersionMessage msg = ProtocolVersionMessage.parse(stream(wire));
        assertEquals(3, msg.getMajor());
        assertEquals(3, msg.getMinor());
    }

    @Test
    void parse_version37() throws IOException {
        byte[] wire = "RFB 003.007\n".getBytes(StandardCharsets.US_ASCII);
        ProtocolVersionMessage msg = ProtocolVersionMessage.parse(stream(wire));
        assertEquals(3, msg.getMajor());
        assertEquals(7, msg.getMinor());
    }

    @Test
    void parse_consumesExactly12Bytes() throws IOException {
        byte[] wireAndSentinel = new byte[13];
        System.arraycopy(WIRE_3_8, 0, wireAndSentinel, 0, 12);
        wireAndSentinel[12] = (byte) 0xAB;
        InputStream in = stream(wireAndSentinel);
        ProtocolVersionMessage.parse(in);
        assertEquals(0xAB, in.read());
    }

    @Test
    void parse_invalidFormat_throws() {
        // Missing trailing newline
        byte[] bad = "RFB 003.008 ".getBytes(StandardCharsets.US_ASCII);
        assertThrows(IOException.class, () -> ProtocolVersionMessage.parse(stream(bad)));
    }

    @Test
    void parse_invalidPrefix_throws() {
        byte[] bad = "VNC 003.008\n".getBytes(StandardCharsets.US_ASCII);
        assertThrows(IOException.class, () -> ProtocolVersionMessage.parse(stream(bad)));
    }

    @Test
    void write_version38_producesRfcExactBytes() throws IOException {
        ProtocolVersionMessage msg = ProtocolVersionMessage.builder().major(3).minor(8).build();
        byte[] written = capture(msg::write);
        assertArrayEquals(WIRE_3_8, written);
    }

    @Test
    void write_is12Bytes() throws IOException {
        byte[] written = capture(ProtocolVersionMessage.builder().major(3).minor(8).build()::write);
        assertEquals(12, written.length);
    }

    @Test
    void write_version33_leftPadsWithZeros() throws IOException {
        ProtocolVersionMessage msg = ProtocolVersionMessage.builder().major(3).minor(3).build();
        byte[] written = capture(msg::write);
        String s = new String(written, StandardCharsets.US_ASCII);
        assertEquals("RFB 003.003\n", s);
    }

    @Test
    void write_trailingNewline() throws IOException {
        byte[] written = capture(ProtocolVersionMessage.builder().major(3).minor(8).build()::write);
        assertEquals(0x0A, written[11]);
    }

    @Test
    void roundtrip_38() throws IOException {
        ProtocolVersionMessage original = ProtocolVersionMessage.builder().major(3).minor(8).build();
        ProtocolVersionMessage parsed = ProtocolVersionMessage.parse(stream(capture(original::write)));
        assertEquals(original, parsed);
    }

    @Test
    void equals_and_hashCode() {
        ProtocolVersionMessage a = ProtocolVersionMessage.builder().major(3).minor(8).build();
        ProtocolVersionMessage b = ProtocolVersionMessage.builder().major(3).minor(8).build();
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
