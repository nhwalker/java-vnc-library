package io.github.nwalker.vnc.core.encoding;

import io.github.nwalker.vnc.core.EncodingType;
import io.github.nwalker.vnc.core.PixelFormat;
import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * Raw encoding data (Section 7.7.1 of RFC 6143).
 *
 * <p>Contains {@code width * height * bytesPerPixel} bytes of raw pixel data
 * in left-to-right, top-to-bottom scan line order.</p>
 */
public final class RawEncodingData extends EncodingData {

    private final byte[] pixels;

    private RawEncodingData(Builder b) {
        this.pixels = Arrays.copyOf(b.pixels, b.pixels.length);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns a defensive copy of the raw pixel bytes. */
    public byte[] getPixels() { return Arrays.copyOf(pixels, pixels.length); }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /**
     * Reads {@code width * height * bytesPerPixel} bytes of raw pixel data from {@code in}.
     *
     * @param in          the input stream
     * @param width       rectangle width in pixels
     * @param height      rectangle height in pixels
     * @param pixelFormat the negotiated pixel format (needed for bytesPerPixel)
     */
    public static RawEncodingData parse(InputStream in, int width, int height,
                                        PixelFormat pixelFormat) throws IOException {
        int size = width * height * pixelFormat.getBytesPerPixel();
        byte[] data = RfbIO.readBytes(in, size);
        return new Builder().pixels(data).build();
    }

    @Override
    public EncodingType getEncodingType() { return EncodingType.RAW; }

    @Override
    public void write(OutputStream out) throws IOException {
        out.write(pixels);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RawEncodingData)) return false;
        return Arrays.equals(pixels, ((RawEncodingData) o).pixels);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(pixels); }

    @Override
    public String toString() {
        return "RawEncodingData{pixelBytes=" + pixels.length + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private byte[] pixels = new byte[0];

        public Builder pixels(byte[] v) { pixels = v; return this; }

        public RawEncodingData build() { return new RawEncodingData(this); }
    }
}
