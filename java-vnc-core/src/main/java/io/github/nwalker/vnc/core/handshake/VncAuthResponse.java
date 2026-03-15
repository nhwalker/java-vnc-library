package io.github.nwalker.vnc.core.handshake;

import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * VNC Authentication response (Section 7.2.2 of RFC 6143).
 *
 * <p>The client encrypts the server's {@link VncAuthChallenge} with DES using the
 * user's password (truncated or zero-padded to 8 bytes) as the key, and sends the
 * resulting 16-byte response.</p>
 */
public final class VncAuthResponse {

    /** Length of the response in bytes. */
    public static final int RESPONSE_LENGTH = 16;

    private final byte[] response;

    private VncAuthResponse(Builder b) {
        this.response = Arrays.copyOf(b.response, b.response.length);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns a defensive copy of the 16-byte DES-encrypted response. */
    public byte[] getResponse() { return Arrays.copyOf(response, response.length); }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /** Reads the 16-byte VNC auth response from {@code in}. */
    public static VncAuthResponse parse(InputStream in) throws IOException {
        byte[] resp = RfbIO.readBytes(in, RESPONSE_LENGTH);
        return new Builder().response(resp).build();
    }

    /** Writes the 16-byte response to {@code out}. */
    public void write(OutputStream out) throws IOException {
        out.write(response);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VncAuthResponse)) return false;
        return Arrays.equals(response, ((VncAuthResponse) o).response);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(response); }

    @Override
    public String toString() {
        return "VncAuthResponse{response=" + Arrays.toString(response) + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private byte[] response = new byte[RESPONSE_LENGTH];

        public Builder response(byte[] v) { response = v; return this; }

        public VncAuthResponse build() { return new VncAuthResponse(this); }
    }
}
