package io.github.nwalker.vnc.core.handshake;

import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * Client-to-server security type selection (Section 7.1.2 of RFC 6143).
 *
 * <p>After receiving the server's {@link ServerSecurityHandshake}, the client sends
 * back a single byte indicating the chosen security type.</p>
 */
public final class SecurityTypeSelection {

    private final int securityType; // U8

    private SecurityTypeSelection(Builder b) {
        this.securityType = b.securityType;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int getSecurityType() { return securityType; }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /** Reads a 1-byte security type selection from {@code in}. */
    public static SecurityTypeSelection parse(InputStream in) throws IOException {
        int type = RfbIO.readU8(in);
        return new Builder().securityType(type).build();
    }

    /** Writes the 1-byte security type selection to {@code out}. */
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU8(out, securityType);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SecurityTypeSelection)) return false;
        return securityType == ((SecurityTypeSelection) o).securityType;
    }

    @Override
    public int hashCode() { return Objects.hash(securityType); }

    @Override
    public String toString() {
        return "SecurityTypeSelection{securityType=" + securityType + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int securityType;

        public Builder securityType(int v) { securityType = v; return this; }

        public SecurityTypeSelection build() { return new SecurityTypeSelection(this); }
    }
}
