package io.github.nwalker.vnc.core.handshake;

import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * ProtocolVersion handshake message (Section 7.1.1 of RFC 6143).
 *
 * <p>This 12-byte ASCII message is sent first by the server and then echoed by the client
 * (possibly with a lower version). Format: {@code "RFB xxx.yyy\n"} where xxx is the major
 * version (left-padded with zeros) and yyy is the minor version.</p>
 *
 * <p>Defined versions: 3.3, 3.7, and 3.8.</p>
 */
public final class ProtocolVersionMessage {

    /** Wire size in bytes. */
    public static final int WIRE_SIZE = 12;

    private final int major;
    private final int minor;

    private ProtocolVersionMessage(Builder b) {
        this.major = b.major;
        this.minor = b.minor;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int getMajor() { return major; }
    public int getMinor() { return minor; }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /**
     * Reads the 12-byte ProtocolVersion message from {@code in}.
     *
     * @throws IOException if the format is not {@code "RFB xxx.yyy\n"}
     */
    public static ProtocolVersionMessage parse(InputStream in) throws IOException {
        byte[] raw = RfbIO.readBytes(in, WIRE_SIZE);
        String s = new String(raw, StandardCharsets.US_ASCII);
        if (!s.startsWith("RFB ") || s.charAt(7) != '.' || s.charAt(11) != '\n') {
            throw new IOException("Invalid ProtocolVersion message: " + s.trim());
        }
        int major = Integer.parseInt(s.substring(4, 7));
        int minor = Integer.parseInt(s.substring(8, 11));
        return new Builder().major(major).minor(minor).build();
    }

    /**
     * Writes the 12-byte ProtocolVersion message to {@code out}.
     */
    public void write(OutputStream out) throws IOException {
        String s = String.format("RFB %03d.%03d\n", major, minor);
        out.write(s.getBytes(StandardCharsets.US_ASCII));
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProtocolVersionMessage)) return false;
        ProtocolVersionMessage p = (ProtocolVersionMessage) o;
        return major == p.major && minor == p.minor;
    }

    @Override
    public int hashCode() { return Objects.hash(major, minor); }

    @Override
    public String toString() {
        return "ProtocolVersionMessage{major=" + major + ", minor=" + minor + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int major = 3;
        private int minor = 8;

        public Builder major(int v) { major = v; return this; }
        public Builder minor(int v) { minor = v; return this; }

        public ProtocolVersionMessage build() { return new ProtocolVersionMessage(this); }
    }
}
