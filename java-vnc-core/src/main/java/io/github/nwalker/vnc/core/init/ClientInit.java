package io.github.nwalker.vnc.core.init;

import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * ClientInit message (Section 7.3.1 of RFC 6143).
 *
 * <p>The first message sent by the client after the security handshake.
 * {@code sharedFlag} is {@code true} if the server should leave other clients connected,
 * or {@code false} if it should disconnect all other clients to give exclusive access.</p>
 *
 * <p>Wire format: 1 byte (U8 shared-flag, non-zero = shared).</p>
 */
public final class ClientInit {

    private final boolean shared;

    private ClientInit(Builder b) {
        this.shared = b.shared;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public boolean isShared() { return shared; }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /** Reads the 1-byte ClientInit from {@code in}. */
    public static ClientInit parse(InputStream in) throws IOException {
        boolean shared = RfbIO.readU8(in) != 0;
        return new Builder().shared(shared).build();
    }

    /** Writes the 1-byte ClientInit to {@code out}. */
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU8(out, shared ? 1 : 0);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ClientInit)) return false;
        return shared == ((ClientInit) o).shared;
    }

    @Override
    public int hashCode() { return Objects.hash(shared); }

    @Override
    public String toString() {
        return "ClientInit{shared=" + shared + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean shared = true;

        public Builder shared(boolean v) { shared = v; return this; }

        public ClientInit build() { return new ClientInit(this); }
    }
}
