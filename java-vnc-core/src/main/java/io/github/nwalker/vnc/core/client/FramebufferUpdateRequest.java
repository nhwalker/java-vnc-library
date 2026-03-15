package io.github.nwalker.vnc.core.client;

import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * FramebufferUpdateRequest client-to-server message (Section 7.5.3 of RFC 6143).
 *
 * <p>Notifies the server that the client is interested in the specified area of the
 * framebuffer. When {@code incremental} is {@code false}, the client requests the full
 * contents of the area immediately; when {@code true}, the server sends changes only
 * as they occur.</p>
 *
 * <p>Wire format: message-type (U8=3) + U8 incremental + U16 x + U16 y + U16 w + U16 h.</p>
 */
public final class FramebufferUpdateRequest {

    /** Message type byte on the wire. */
    public static final int MESSAGE_TYPE = 3;

    private final boolean incremental;
    private final int x;      // U16
    private final int y;      // U16
    private final int width;  // U16
    private final int height; // U16

    private FramebufferUpdateRequest(Builder b) {
        this.incremental = b.incremental;
        this.x           = b.x;
        this.y           = b.y;
        this.width       = b.width;
        this.height      = b.height;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public boolean isIncremental() { return incremental; }
    public int getX()              { return x; }
    public int getY()              { return y; }
    public int getWidth()          { return width; }
    public int getHeight()         { return height; }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /**
     * Reads a FramebufferUpdateRequest from {@code in}.
     * The message-type byte must already have been consumed by the caller.
     */
    public static FramebufferUpdateRequest parse(InputStream in) throws IOException {
        boolean incremental = RfbIO.readU8(in) != 0;
        int x  = RfbIO.readU16(in);
        int y  = RfbIO.readU16(in);
        int w  = RfbIO.readU16(in);
        int h  = RfbIO.readU16(in);
        return new Builder()
                .incremental(incremental).x(x).y(y).width(w).height(h)
                .build();
    }

    /** Writes the complete message (including type byte) to {@code out}. */
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU8(out, MESSAGE_TYPE);
        RfbIO.writeU8(out, incremental ? 1 : 0);
        RfbIO.writeU16(out, x);
        RfbIO.writeU16(out, y);
        RfbIO.writeU16(out, width);
        RfbIO.writeU16(out, height);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FramebufferUpdateRequest)) return false;
        FramebufferUpdateRequest f = (FramebufferUpdateRequest) o;
        return incremental == f.incremental
                && x == f.x && y == f.y && width == f.width && height == f.height;
    }

    @Override
    public int hashCode() { return Objects.hash(incremental, x, y, width, height); }

    @Override
    public String toString() {
        return "FramebufferUpdateRequest{"
                + "incremental=" + incremental
                + ", x=" + x + ", y=" + y
                + ", width=" + width + ", height=" + height
                + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean incremental;
        private int x;
        private int y;
        private int width;
        private int height;

        public Builder incremental(boolean v) { incremental = v; return this; }
        public Builder x(int v)               { x = v;           return this; }
        public Builder y(int v)               { y = v;           return this; }
        public Builder width(int v)           { width = v;       return this; }
        public Builder height(int v)          { height = v;      return this; }

        public FramebufferUpdateRequest build() { return new FramebufferUpdateRequest(this); }
    }
}
