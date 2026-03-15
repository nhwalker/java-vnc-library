package io.github.nwalker.vnc.core.handshake;

import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * SecurityResult handshake message (Section 7.1.3 of RFC 6143).
 *
 * <p>Sent by the server after the security handshaking phase.
 * Status {@code 0} means OK; status {@code 1} means failed.
 * On failure the server follows with a reason string (see {@link SecurityFailureMessage})
 * before closing the connection.</p>
 */
public final class SecurityResult {

    /** Status code indicating successful authentication. */
    public static final long STATUS_OK     = 0L;
    /** Status code indicating failed authentication. */
    public static final long STATUS_FAILED = 1L;

    private final long status; // U32

    private SecurityResult(Builder b) {
        this.status = b.status;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public long getStatus() { return status; }

    /** Returns {@code true} when the security handshake succeeded. */
    public boolean isOk() { return status == STATUS_OK; }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /** Reads the 4-byte SecurityResult from {@code in}. */
    public static SecurityResult parse(InputStream in) throws IOException {
        long status = RfbIO.readU32(in);
        return new Builder().status(status).build();
    }

    /** Writes the 4-byte SecurityResult to {@code out}. */
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU32(out, status);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SecurityResult)) return false;
        return status == ((SecurityResult) o).status;
    }

    @Override
    public int hashCode() { return Objects.hash(status); }

    @Override
    public String toString() {
        return "SecurityResult{status=" + status + " (" + (isOk() ? "OK" : "FAILED") + ")}";
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private long status = STATUS_OK;

        public Builder status(long v) { status = v; return this; }

        public SecurityResult build() { return new SecurityResult(this); }
    }
}
