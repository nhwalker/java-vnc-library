package io.github.nwalker.vnc.core.encoding;

import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * A single subrectangle within an RRE-encoded rectangle (Section 7.7.3 of RFC 6143).
 *
 * <p>Each subrectangle has a pixel value (bytesPerPixel bytes) and position/size fields.</p>
 */
public final class RreSubrectangle {

    private final byte[] pixelValue; // PIXEL (bytesPerPixel bytes)
    private final int x;             // U16
    private final int y;             // U16
    private final int width;         // U16
    private final int height;        // U16

    private RreSubrectangle(Builder b) {
        this.pixelValue = Arrays.copyOf(b.pixelValue, b.pixelValue.length);
        this.x          = b.x;
        this.y          = b.y;
        this.width      = b.width;
        this.height     = b.height;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns a defensive copy of the pixel value bytes. */
    public byte[] getPixelValue() { return Arrays.copyOf(pixelValue, pixelValue.length); }
    public int getX()      { return x; }
    public int getY()      { return y; }
    public int getWidth()  { return width; }
    public int getHeight() { return height; }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    public static RreSubrectangle parse(InputStream in, int bytesPerPixel) throws IOException {
        byte[] pv = RfbIO.readBytes(in, bytesPerPixel);
        int x  = RfbIO.readU16(in);
        int y  = RfbIO.readU16(in);
        int w  = RfbIO.readU16(in);
        int h  = RfbIO.readU16(in);
        return new Builder().pixelValue(pv).x(x).y(y).width(w).height(h).build();
    }

    public void write(OutputStream out) throws IOException {
        out.write(pixelValue);
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
        if (!(o instanceof RreSubrectangle)) return false;
        RreSubrectangle s = (RreSubrectangle) o;
        return x == s.x && y == s.y && width == s.width && height == s.height
                && Arrays.equals(pixelValue, s.pixelValue);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(x, y, width, height);
        result = 31 * result + Arrays.hashCode(pixelValue);
        return result;
    }

    @Override
    public String toString() {
        return "RreSubrectangle{x=" + x + ", y=" + y
                + ", width=" + width + ", height=" + height
                + ", pixelValue=" + Arrays.toString(pixelValue) + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private byte[] pixelValue = new byte[0];
        private int x;
        private int y;
        private int width;
        private int height;

        public Builder pixelValue(byte[] v) { pixelValue = v; return this; }
        public Builder x(int v)             { x = v;          return this; }
        public Builder y(int v)             { y = v;          return this; }
        public Builder width(int v)         { width = v;      return this; }
        public Builder height(int v)        { height = v;     return this; }

        public RreSubrectangle build() { return new RreSubrectangle(this); }
    }
}
