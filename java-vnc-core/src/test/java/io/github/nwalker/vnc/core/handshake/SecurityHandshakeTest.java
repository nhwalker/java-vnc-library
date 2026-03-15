package io.github.nwalker.vnc.core.handshake;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static io.github.nwalker.vnc.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for security handshake messages (RFC 6143 §7.1.2 and §7.1.3).
 *
 * <ul>
 *   <li>{@link ServerSecurityHandshake}: U8 count + count U8 security-type codes</li>
 *   <li>{@link SecurityTypeSelection}: U8 security-type</li>
 *   <li>{@link SecurityFailureMessage}: U32 reason-length + ASCII reason</li>
 *   <li>{@link SecurityResult}: U32 status (0=OK, 1=failed)</li>
 * </ul>
 */
class SecurityHandshakeTest {

    // -------------------------------------------------------------------------
    // ServerSecurityHandshake
    // -------------------------------------------------------------------------

    @Test
    void serverSecurity_parse_twoTypes() throws IOException {
        // number-of-security-types=2, types=[1 (None), 2 (VNC Auth)]
        ServerSecurityHandshake msg = ServerSecurityHandshake.parse(stream(0x02, 0x01, 0x02));
        assertEquals(2, msg.getSecurityTypes().size());
        assertEquals(1, (int) msg.getSecurityTypes().get(0));
        assertEquals(2, (int) msg.getSecurityTypes().get(1));
    }

    @Test
    void serverSecurity_parse_zeroTypes_isFailure() throws IOException {
        ServerSecurityHandshake msg = ServerSecurityHandshake.parse(stream(0x00));
        assertTrue(msg.isFailure());
        assertTrue(msg.getSecurityTypes().isEmpty());
    }

    @Test
    void serverSecurity_parse_singleType() throws IOException {
        // number-of-security-types=1, type=1 (None)
        ServerSecurityHandshake msg = ServerSecurityHandshake.parse(stream(0x01, 0x01));
        assertEquals(1, msg.getSecurityTypes().size());
        assertEquals(1, (int) msg.getSecurityTypes().get(0));
    }

    @Test
    void serverSecurity_parse_consumesExactBytes() throws IOException {
        // count=1 + type=2, then sentinel
        InputStream in = stream(0x01, 0x02, 0xAB);
        ServerSecurityHandshake.parse(in);
        assertEquals(0xAB, in.read());
    }

    @Test
    void serverSecurity_write_twoTypes() throws IOException {
        ServerSecurityHandshake msg = ServerSecurityHandshake.builder()
                .addSecurityType(1).addSecurityType(2).build();
        byte[] w = capture(msg::write);
        assertArrayEquals(bytes(0x02, 0x01, 0x02), w);
    }

    @Test
    void serverSecurity_write_zeroTypes() throws IOException {
        ServerSecurityHandshake msg = ServerSecurityHandshake.builder().build();
        byte[] w = capture(msg::write);
        assertArrayEquals(bytes(0x00), w);
    }

    @Test
    void serverSecurity_roundtrip() throws IOException {
        ServerSecurityHandshake original = ServerSecurityHandshake.builder()
                .addSecurityType(1).addSecurityType(2).build();
        byte[] wire = capture(original::write);
        assertEquals(original, ServerSecurityHandshake.parse(stream(wire)));
    }

    // -------------------------------------------------------------------------
    // SecurityTypeSelection
    // -------------------------------------------------------------------------

    @Test
    void typeSelection_parse_none() throws IOException {
        SecurityTypeSelection msg = SecurityTypeSelection.parse(stream(0x01));
        assertEquals(1, msg.getSecurityType());
    }

    @Test
    void typeSelection_parse_vncAuth() throws IOException {
        SecurityTypeSelection msg = SecurityTypeSelection.parse(stream(0x02));
        assertEquals(2, msg.getSecurityType());
    }

    @Test
    void typeSelection_write_isOneByte() throws IOException {
        byte[] w = capture(SecurityTypeSelection.builder().securityType(2).build()::write);
        assertEquals(1, w.length);
        assertEquals(2, w[0] & 0xFF);
    }

    @Test
    void typeSelection_roundtrip() throws IOException {
        SecurityTypeSelection original = SecurityTypeSelection.builder().securityType(2).build();
        byte[] wire = capture(original::write);
        assertEquals(original, SecurityTypeSelection.parse(stream(wire)));
    }

    // -------------------------------------------------------------------------
    // SecurityFailureMessage
    // -------------------------------------------------------------------------

    @Test
    void securityFailure_parse_reasonString() throws IOException {
        // U32 length=5, then "hello"
        byte[] wire = bytes(0x00, 0x00, 0x00, 0x05, 'h', 'e', 'l', 'l', 'o');
        SecurityFailureMessage msg = SecurityFailureMessage.parse(stream(wire));
        assertEquals("hello", msg.getReason());
    }

    @Test
    void securityFailure_parse_emptyReason() throws IOException {
        // U32 length=0, no bytes follow
        SecurityFailureMessage msg = SecurityFailureMessage.parse(stream(0x00, 0x00, 0x00, 0x00));
        assertEquals("", msg.getReason());
    }

    @Test
    void securityFailure_write_lengthPrefixed() throws IOException {
        SecurityFailureMessage msg = SecurityFailureMessage.builder().reason("hi").build();
        byte[] w = capture(msg::write);
        // U32 length=2, then 'h', 'i'
        assertEquals(6, w.length);
        assertEquals(0x00, w[0]);
        assertEquals(0x00, w[1]);
        assertEquals(0x00, w[2]);
        assertEquals(0x02, w[3]);
        assertEquals('h', w[4]);
        assertEquals('i', w[5]);
    }

    @Test
    void securityFailure_roundtrip() throws IOException {
        SecurityFailureMessage original = SecurityFailureMessage.builder()
                .reason("Too many connections").build();
        byte[] wire = capture(original::write);
        assertEquals(original, SecurityFailureMessage.parse(stream(wire)));
    }

    // -------------------------------------------------------------------------
    // SecurityResult
    // -------------------------------------------------------------------------

    @Test
    void securityResult_parse_ok() throws IOException {
        // Status=0 (OK) — U32 big-endian
        SecurityResult msg = SecurityResult.parse(stream(0x00, 0x00, 0x00, 0x00));
        assertEquals(SecurityResult.STATUS_OK, msg.getStatus());
        assertTrue(msg.isOk());
    }

    @Test
    void securityResult_parse_failed() throws IOException {
        // Status=1 (failed) — U32 big-endian
        SecurityResult msg = SecurityResult.parse(stream(0x00, 0x00, 0x00, 0x01));
        assertEquals(SecurityResult.STATUS_FAILED, msg.getStatus());
        assertFalse(msg.isOk());
    }

    @Test
    void securityResult_write_ok_is4BytesZero() throws IOException {
        byte[] w = capture(SecurityResult.builder().status(SecurityResult.STATUS_OK).build()::write);
        assertArrayEquals(bytes(0x00, 0x00, 0x00, 0x00), w);
    }

    @Test
    void securityResult_write_failed_is4BytesOne() throws IOException {
        byte[] w = capture(SecurityResult.builder().status(SecurityResult.STATUS_FAILED).build()::write);
        assertArrayEquals(bytes(0x00, 0x00, 0x00, 0x01), w);
    }

    @Test
    void securityResult_roundtrip_ok() throws IOException {
        SecurityResult original = SecurityResult.builder().status(SecurityResult.STATUS_OK).build();
        byte[] wire = capture(original::write);
        assertEquals(original, SecurityResult.parse(stream(wire)));
    }

    @Test
    void securityResult_roundtrip_failed() throws IOException {
        SecurityResult original = SecurityResult.builder().status(SecurityResult.STATUS_FAILED).build();
        byte[] wire = capture(original::write);
        assertEquals(original, SecurityResult.parse(stream(wire)));
    }

    // -------------------------------------------------------------------------
    // VncAuthChallenge and VncAuthResponse
    // -------------------------------------------------------------------------

    @Test
    void vncAuthChallenge_parse_16Bytes() throws IOException {
        byte[] challengeBytes = new byte[16];
        for (int i = 0; i < 16; i++) challengeBytes[i] = (byte) (i + 1);
        VncAuthChallenge msg = VncAuthChallenge.parse(stream(challengeBytes));
        assertArrayEquals(challengeBytes, msg.getChallenge());
    }

    @Test
    void vncAuthChallenge_write_is16Bytes() throws IOException {
        VncAuthChallenge msg = VncAuthChallenge.builder().challenge(new byte[16]).build();
        assertEquals(16, capture(msg::write).length);
    }

    @Test
    void vncAuthChallenge_parse_consumesExactly16Bytes() throws IOException {
        byte[] wire = new byte[17];
        wire[16] = (byte) 0xAB;
        InputStream in = stream(wire);
        VncAuthChallenge.parse(in);
        assertEquals(0xAB, in.read());
    }

    @Test
    void vncAuthChallenge_roundtrip() throws IOException {
        byte[] ch = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        VncAuthChallenge original = VncAuthChallenge.builder().challenge(ch).build();
        byte[] wire = capture(original::write);
        assertEquals(original, VncAuthChallenge.parse(stream(wire)));
    }

    @Test
    void vncAuthResponse_parse_16Bytes() throws IOException {
        byte[] responseBytes = new byte[16];
        for (int i = 0; i < 16; i++) responseBytes[i] = (byte) (i + 0x10);
        VncAuthResponse msg = VncAuthResponse.parse(stream(responseBytes));
        assertArrayEquals(responseBytes, msg.getResponse());
    }

    @Test
    void vncAuthResponse_write_is16Bytes() throws IOException {
        VncAuthResponse msg = VncAuthResponse.builder().response(new byte[16]).build();
        assertEquals(16, capture(msg::write).length);
    }

    @Test
    void vncAuthResponse_roundtrip() throws IOException {
        byte[] resp = {0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70, (byte) 0x80,
                       (byte) 0x90, (byte) 0xA0, (byte) 0xB0, (byte) 0xC0,
                       (byte) 0xD0, (byte) 0xE0, (byte) 0xF0, 0x00};
        VncAuthResponse original = VncAuthResponse.builder().response(resp).build();
        byte[] wire = capture(original::write);
        assertEquals(original, VncAuthResponse.parse(stream(wire)));
    }
}
