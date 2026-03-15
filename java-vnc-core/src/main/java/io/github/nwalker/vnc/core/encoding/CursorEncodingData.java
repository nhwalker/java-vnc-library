package io.github.nwalker.vnc.core.encoding;

import io.github.nwalker.vnc.core.EncodingType;
import io.github.nwalker.vnc.core.PixelFormat;
import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Cursor pseudo-encoding data (Section 7.8.1 of RFC 6143).
 *
 * <p>The rectangle header's x/y position gives the hotspot; width and height give
 * the cursor dimensions. Data consists of:</p>
 * <ul>
 *   <li>{@code width * height * bytesPerPixel} bytes of raw pixel data</li>
 *   <li>{@code div(width+7,8) * height} bytes of bitmask</li>
 * </ul>
 */
public final class CursorEncodingData extends EncodingData {

    private final byte[] pixels;  // width * height * bytesPerPixel
    private final byte[] bitmask; // div(width+7,8) * height

    private CursorEncodingData(Builder b) {
        this.pixels  = Arrays.copyOf(b.pixels, b.pixels.length);
        this.bitmask = Arrays.copyOf(b.bitmask, b.bitmask.length);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns a defensive copy of the cursor pixel data. */
    public byte[] getPixels()  { return Arrays.copyOf(pixels, pixels.length); }

    /** Returns a defensive copy of the cursor bitmask. */
    public byte[] getBitmask() { return Arrays.copyOf(bitmask, bitmask.length); }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /**
     * Reads cursor pseudo-encoding data from {@code in}.
     *
     * @param in          the input stream
     * @param width       cursor width in pixels (from rectangle header)
     * @param height      cursor height in pixels (from rectangle header)
     * @param pixelFormat the negotiated pixel format
     */
    public static CursorEncodingData parse(InputStream in, int width, int height,
                                           PixelFormat pixelFormat) throws IOException {
        int pixelBytes   = width * height * pixelFormat.getBytesPerPixel();
        int bitmaskBytes = ((width + 7) / 8) * height;
        byte[] px = RfbIO.readBytes(in, pixelBytes);
        byte[] bm = RfbIO.readBytes(in, bitmaskBytes);
        return new Builder().pixels(px).bitmask(bm).build();
    }

    @Override
    public EncodingType getEncodingType() { return EncodingType.CURSOR; }

    @Override
    public void write(OutputStream out) throws IOException {
        out.write(pixels);
        out.write(bitmask);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CursorEncodingData)) return false;
        CursorEncodingData c = (CursorEncodingData) o;
        return Arrays.equals(pixels, c.pixels) && Arrays.equals(bitmask, c.bitmask);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(pixels);
        result = 31 * result + Arrays.hashCode(bitmask);
        return result;
    }

    @Override
    public String toString() {
        return "CursorEncodingData{pixelBytes=" + pixels.length
                + ", bitmaskBytes=" + bitmask.length + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private byte[] pixels  = new byte[0];
        private byte[] bitmask = new byte[0];

        public Builder pixels(byte[] v)  { pixels  = v; return this; }
        public Builder bitmask(byte[] v) { bitmask = v; return this; }

        public CursorEncodingData build() { return new CursorEncodingData(this); }
    }
}
