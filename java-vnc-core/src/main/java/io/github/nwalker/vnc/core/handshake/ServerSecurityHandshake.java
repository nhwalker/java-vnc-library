package io.github.nwalker.vnc.core.handshake;

import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Server-to-client security handshake (Section 7.1.2 of RFC 6143).
 *
 * <p>The server sends a list of supported security type codes. If the list is empty
 * ({@code number-of-security-types == 0}) the connection has failed and the server
 * follows with a {@link SecurityFailureMessage} before closing the connection. Use
 * {@link #isFailure()} to detect this case.</p>
 */
public final class ServerSecurityHandshake {

    private final List<Integer> securityTypes; // U8 values

    private ServerSecurityHandshake(Builder b) {
        this.securityTypes = Collections.unmodifiableList(new ArrayList<>(b.securityTypes));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the list of security type codes offered by the server.
     * An empty list means the connection failed; {@link #isFailure()} returns {@code true}.
     */
    public List<Integer> getSecurityTypes() { return securityTypes; }

    /** Returns {@code true} when the server advertised zero security types (connection failure). */
    public boolean isFailure() { return securityTypes.isEmpty(); }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /** Reads the server security handshake from {@code in}. */
    public static ServerSecurityHandshake parse(InputStream in) throws IOException {
        int n = RfbIO.readU8(in);
        List<Integer> types = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            types.add(RfbIO.readU8(in));
        }
        return new Builder().securityTypes(types).build();
    }

    /** Writes the server security handshake to {@code out}. */
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU8(out, securityTypes.size());
        for (int type : securityTypes) {
            RfbIO.writeU8(out, type);
        }
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServerSecurityHandshake)) return false;
        return securityTypes.equals(((ServerSecurityHandshake) o).securityTypes);
    }

    @Override
    public int hashCode() { return Objects.hashCode(securityTypes); }

    @Override
    public String toString() {
        return "ServerSecurityHandshake{securityTypes=" + securityTypes + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private List<Integer> securityTypes = new ArrayList<>();

        public Builder securityTypes(List<Integer> v) { securityTypes = v; return this; }
        public Builder addSecurityType(int v)          { securityTypes.add(v); return this; }

        public ServerSecurityHandshake build() { return new ServerSecurityHandshake(this); }
    }
}
