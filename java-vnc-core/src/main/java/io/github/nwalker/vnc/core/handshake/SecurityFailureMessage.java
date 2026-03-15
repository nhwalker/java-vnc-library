package io.github.nwalker.vnc.core.handshake;

import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Server-to-client connection failure message (Section 7.1.2 of RFC 6143).
 *
 * <p>Sent by the server when {@code number-of-security-types} is zero in the
 * {@link ServerSecurityHandshake}, indicating that the connection cannot proceed.
 * The server sends a reason string and then closes the connection.</p>
 */
public final class SecurityFailureMessage {

    private final String reason;

    private SecurityFailureMessage(Builder b) {
        this.reason = b.reason;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getReason() { return reason; }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /** Reads the reason-length (U32) followed by that many ASCII bytes from {@code in}. */
    public static SecurityFailureMessage parse(InputStream in) throws IOException {
        long len = RfbIO.readU32(in);
        byte[] reasonBytes = RfbIO.readBytes(in, (int) len);
        String reason = new String(reasonBytes, StandardCharsets.ISO_8859_1);
        return new Builder().reason(reason).build();
    }

    /** Writes the reason-length (U32) and reason bytes to {@code out}. */
    public void write(OutputStream out) throws IOException {
        byte[] reasonBytes = reason.getBytes(StandardCharsets.ISO_8859_1);
        RfbIO.writeU32(out, reasonBytes.length);
        out.write(reasonBytes);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SecurityFailureMessage)) return false;
        return Objects.equals(reason, ((SecurityFailureMessage) o).reason);
    }

    @Override
    public int hashCode() { return Objects.hashCode(reason); }

    @Override
    public String toString() {
        return "SecurityFailureMessage{reason='" + reason + "'}";
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String reason = "";

        public Builder reason(String v) { reason = v; return this; }

        public SecurityFailureMessage build() { return new SecurityFailureMessage(this); }
    }
}
