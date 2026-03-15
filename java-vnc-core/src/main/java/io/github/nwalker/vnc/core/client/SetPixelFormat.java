package io.github.nwalker.vnc.core.client;

import io.github.nwalker.vnc.core.PixelFormat;
import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * SetPixelFormat client-to-server message (Section 7.5.1 of RFC 6143).
 *
 * <p>Sets the format in which pixel values should be sent in FramebufferUpdate messages.</p>
 *
 * <p>Wire format: message-type (U8=0) + 3 padding bytes + 16-byte PIXEL_FORMAT.</p>
 */
public final class SetPixelFormat {

    /** Message type byte on the wire. */
    public static final int MESSAGE_TYPE = 0;

    private final PixelFormat pixelFormat;

    private SetPixelFormat(Builder b) {
        this.pixelFormat = b.pixelFormat;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public PixelFormat getPixelFormat() { return pixelFormat; }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /**
     * Reads a SetPixelFormat message from {@code in}.
     * The message-type byte must already have been consumed by the caller.
     */
    public static SetPixelFormat parse(InputStream in) throws IOException {
        RfbIO.skipBytes(in, 3); // padding
        PixelFormat pf = PixelFormat.parse(in);
        return new Builder().pixelFormat(pf).build();
    }

    /** Writes the complete SetPixelFormat message (including type byte) to {@code out}. */
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU8(out, MESSAGE_TYPE);
        RfbIO.writePadding(out, 3);
        pixelFormat.write(out);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SetPixelFormat)) return false;
        return Objects.equals(pixelFormat, ((SetPixelFormat) o).pixelFormat);
    }

    @Override
    public int hashCode() { return Objects.hashCode(pixelFormat); }

    @Override
    public String toString() {
        return "SetPixelFormat{pixelFormat=" + pixelFormat + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private PixelFormat pixelFormat;

        public Builder pixelFormat(PixelFormat v) { pixelFormat = v; return this; }

        public SetPixelFormat build() { return new SetPixelFormat(this); }
    }
}
