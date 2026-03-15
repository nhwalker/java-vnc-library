package io.github.nwalker.vnc.core.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import io.github.nwalker.vnc.core.RfbIO;

/**
 * Bell server-to-client message (Section 7.6.3 of RFC 6143).
 *
 * <p>Instructs the client to make an audible signal if it is able to.
 * Wire format: message-type (U8=2) only; no additional payload.</p>
 */
public final class Bell {

    /** Message type byte on the wire. */
    public static final int MESSAGE_TYPE = 2;

    private static final Bell INSTANCE = new Bell();

    private Bell() {}

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /**
     * Returns the singleton Bell instance.
     * The message-type byte must already have been consumed by the caller.
     */
    public static Bell parse(InputStream in) throws IOException {
        return INSTANCE;
    }

    /** Writes the complete Bell message (just the type byte) to {@code out}. */
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU8(out, MESSAGE_TYPE);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) { return o instanceof Bell; }

    @Override
    public int hashCode() { return Bell.class.hashCode(); }

    @Override
    public String toString() { return "Bell{}"; }

    // Builder is trivial but provided for API consistency.
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        public Bell build() { return INSTANCE; }
    }
}
