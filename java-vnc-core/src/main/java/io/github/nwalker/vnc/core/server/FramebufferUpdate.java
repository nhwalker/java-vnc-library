package io.github.nwalker.vnc.core.server;

import io.github.nwalker.vnc.core.PixelFormat;
import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * FramebufferUpdate server-to-client message (Section 7.6.1 of RFC 6143).
 *
 * <p>Carries one or more {@link Rectangle}s of pixel data in response to a
 * {@link io.github.nwalker.vnc.core.client.FramebufferUpdateRequest}.</p>
 *
 * <p>Wire format: message-type (U8=0) + 1 padding byte + U16 number-of-rectangles
 * + that many rectangles (each with a header and encoding data).</p>
 *
 * <p>Parsing requires the currently negotiated {@link PixelFormat} because encoding data
 * sizes depend on {@code bytesPerPixel}. Pass the pixel format in effect at the time
 * the message arrives.</p>
 */
public final class FramebufferUpdate {

    /** Message type byte on the wire. */
    public static final int MESSAGE_TYPE = 0;

    private final List<Rectangle> rectangles;

    private FramebufferUpdate(Builder b) {
        this.rectangles = Collections.unmodifiableList(new ArrayList<>(b.rectangles));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns an unmodifiable list of rectangles contained in this update. */
    public List<Rectangle> getRectangles() { return rectangles; }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /**
     * Reads a FramebufferUpdate from {@code in}.
     * The message-type byte must already have been consumed by the caller.
     *
     * @param in          the input stream
     * @param pixelFormat the pixel format currently in effect
     */
    public static FramebufferUpdate parse(InputStream in, PixelFormat pixelFormat) throws IOException {
        RfbIO.skipBytes(in, 1); // padding
        int count = RfbIO.readU16(in);
        List<Rectangle> rects = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rects.add(Rectangle.parse(in, pixelFormat));
        }
        return new Builder().rectangles(rects).build();
    }

    /** Writes the complete message (including type byte) to {@code out}. */
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU8(out, MESSAGE_TYPE);
        RfbIO.writePadding(out, 1);
        RfbIO.writeU16(out, rectangles.size());
        for (Rectangle rect : rectangles) {
            rect.write(out);
        }
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FramebufferUpdate)) return false;
        return rectangles.equals(((FramebufferUpdate) o).rectangles);
    }

    @Override
    public int hashCode() { return Objects.hashCode(rectangles); }

    @Override
    public String toString() {
        return "FramebufferUpdate{rectangles=" + rectangles.size() + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private List<Rectangle> rectangles = new ArrayList<>();

        public Builder rectangles(List<Rectangle> v) { rectangles = v; return this; }
        public Builder addRectangle(Rectangle v)      { rectangles.add(v); return this; }

        public FramebufferUpdate build() { return new FramebufferUpdate(this); }
    }
}
