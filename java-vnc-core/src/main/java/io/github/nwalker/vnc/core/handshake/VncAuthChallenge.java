package io.github.nwalker.vnc.core.handshake;

import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * VNC Authentication challenge (Section 7.2.2 of RFC 6143).
 *
 * <p>After the client selects {@code VNC Authentication} as the security type,
 * the server sends a random 16-byte challenge. The client must encrypt it with
 * DES using the user's password as the key and send back the result as a
 * {@link VncAuthResponse}.</p>
 */
public final class VncAuthChallenge {

    /** Length of the challenge and response in bytes. */
    public static final int CHALLENGE_LENGTH = 16;

    private final byte[] challenge;

    private VncAuthChallenge(Builder b) {
        this.challenge = Arrays.copyOf(b.challenge, b.challenge.length);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns a defensive copy of the 16-byte challenge. */
    public byte[] getChallenge() { return Arrays.copyOf(challenge, challenge.length); }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /** Reads the 16-byte VNC auth challenge from {@code in}. */
    public static VncAuthChallenge parse(InputStream in) throws IOException {
        byte[] ch = RfbIO.readBytes(in, CHALLENGE_LENGTH);
        return new Builder().challenge(ch).build();
    }

    /** Writes the 16-byte challenge to {@code out}. */
    public void write(OutputStream out) throws IOException {
        out.write(challenge);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VncAuthChallenge)) return false;
        return Arrays.equals(challenge, ((VncAuthChallenge) o).challenge);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(challenge); }

    @Override
    public String toString() {
        return "VncAuthChallenge{challenge=" + Arrays.toString(challenge) + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private byte[] challenge = new byte[CHALLENGE_LENGTH];

        public Builder challenge(byte[] v) { challenge = v; return this; }

        public VncAuthChallenge build() { return new VncAuthChallenge(this); }
    }
}
