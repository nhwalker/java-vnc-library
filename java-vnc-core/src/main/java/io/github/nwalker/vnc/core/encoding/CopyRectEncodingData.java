package io.github.nwalker.vnc.core.encoding;

import io.github.nwalker.vnc.core.EncodingType;
import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * CopyRect encoding data (Section 7.7.2 of RFC 6143).
 *
 * <p>Specifies the (srcX, srcY) position in the framebuffer from which the client should copy
 * the rectangle of pixel data. Wire format: 4 bytes (U16 srcX, U16 srcY).</p>
 */
public final class CopyRectEncodingData extends EncodingData {

    private final int srcX; // U16
    private final int srcY; // U16

    private CopyRectEncodingData(Builder b) {
        this.srcX = b.srcX;
        this.srcY = b.srcY;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int getSrcX() { return srcX; }
    public int getSrcY() { return srcY; }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /** Reads 4 bytes of CopyRect data from {@code in}. */
    public static CopyRectEncodingData parse(InputStream in) throws IOException {
        int x = RfbIO.readU16(in);
        int y = RfbIO.readU16(in);
        return new Builder().srcX(x).srcY(y).build();
    }

    @Override
    public EncodingType getEncodingType() { return EncodingType.COPY_RECT; }

    @Override
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU16(out, srcX);
        RfbIO.writeU16(out, srcY);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CopyRectEncodingData)) return false;
        CopyRectEncodingData c = (CopyRectEncodingData) o;
        return srcX == c.srcX && srcY == c.srcY;
    }

    @Override
    public int hashCode() { return Objects.hash(srcX, srcY); }

    @Override
    public String toString() {
        return "CopyRectEncodingData{srcX=" + srcX + ", srcY=" + srcY + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int srcX;
        private int srcY;

        public Builder srcX(int v) { srcX = v; return this; }
        public Builder srcY(int v) { srcY = v; return this; }

        public CopyRectEncodingData build() { return new CopyRectEncodingData(this); }
    }
}
