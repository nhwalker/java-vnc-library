package io.github.nwalker.vnc.core.encoding;

import io.github.nwalker.vnc.core.EncodingType;
import io.github.nwalker.vnc.core.PixelFormat;
import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * RRE (Rise-and-Run-length Encoding) data (Section 7.7.3 of RFC 6143).
 *
 * <p>Consists of a background pixel value and a list of colored subrectangles
 * that overlay the background.</p>
 */
public final class RreEncodingData extends EncodingData {

    private final byte[] backgroundPixel; // PIXEL
    private final List<RreSubrectangle> subrectangles;

    private RreEncodingData(Builder b) {
        this.backgroundPixel = Arrays.copyOf(b.backgroundPixel, b.backgroundPixel.length);
        this.subrectangles   = Collections.unmodifiableList(new ArrayList<>(b.subrectangles));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns a defensive copy of the background pixel value. */
    public byte[] getBackgroundPixel() {
        return Arrays.copyOf(backgroundPixel, backgroundPixel.length);
    }

    /** Returns an unmodifiable view of the subrectangle list. */
    public List<RreSubrectangle> getSubrectangles() { return subrectangles; }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /**
     * Reads RRE-encoded data from {@code in}.
     *
     * @param in          the input stream
     * @param pixelFormat the negotiated pixel format (needed for bytesPerPixel)
     */
    public static RreEncodingData parse(InputStream in, PixelFormat pixelFormat) throws IOException {
        long n = RfbIO.readU32(in);
        int bpp = pixelFormat.getBytesPerPixel();
        byte[] bg = RfbIO.readBytes(in, bpp);
        List<RreSubrectangle> subs = new ArrayList<>();
        for (long i = 0; i < n; i++) {
            subs.add(RreSubrectangle.parse(in, bpp));
        }
        return new Builder().backgroundPixel(bg).subrectangles(subs).build();
    }

    @Override
    public EncodingType getEncodingType() { return EncodingType.RRE; }

    @Override
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU32(out, subrectangles.size());
        out.write(backgroundPixel);
        for (RreSubrectangle sub : subrectangles) {
            sub.write(out);
        }
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RreEncodingData)) return false;
        RreEncodingData r = (RreEncodingData) o;
        return Arrays.equals(backgroundPixel, r.backgroundPixel)
                && subrectangles.equals(r.subrectangles);
    }

    @Override
    public int hashCode() {
        int result = Arrays.hashCode(backgroundPixel);
        result = 31 * result + Objects.hashCode(subrectangles);
        return result;
    }

    @Override
    public String toString() {
        return "RreEncodingData{subrectangles=" + subrectangles.size()
                + ", backgroundPixel=" + Arrays.toString(backgroundPixel) + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private byte[] backgroundPixel = new byte[0];
        private List<RreSubrectangle> subrectangles = new ArrayList<>();

        public Builder backgroundPixel(byte[] v)         { backgroundPixel = v;  return this; }
        public Builder subrectangles(List<RreSubrectangle> v) { subrectangles = v; return this; }
        public Builder addSubrectangle(RreSubrectangle v) { subrectangles.add(v); return this; }

        public RreEncodingData build() { return new RreEncodingData(this); }
    }
}
