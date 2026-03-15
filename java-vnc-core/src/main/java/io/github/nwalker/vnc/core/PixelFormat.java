package io.github.nwalker.vnc.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * The PIXEL_FORMAT data structure (Section 7.4 of RFC 6143).
 *
 * <p>Describes the way a pixel value is transmitted on the wire. Transmitted as 16 bytes.</p>
 */
public final class PixelFormat {

    private final int bitsPerPixel;  // U8
    private final int depth;         // U8
    private final boolean bigEndian; // U8 (non-zero = true)
    private final boolean trueColor; // U8 (non-zero = true)
    private final int redMax;        // U16
    private final int greenMax;      // U16
    private final int blueMax;       // U16
    private final int redShift;      // U8
    private final int greenShift;    // U8
    private final int blueShift;     // U8
    // 3 padding bytes not stored

    private PixelFormat(Builder b) {
        this.bitsPerPixel = b.bitsPerPixel;
        this.depth        = b.depth;
        this.bigEndian    = b.bigEndian;
        this.trueColor    = b.trueColor;
        this.redMax       = b.redMax;
        this.greenMax     = b.greenMax;
        this.blueMax      = b.blueMax;
        this.redShift     = b.redShift;
        this.greenShift   = b.greenShift;
        this.blueShift    = b.blueShift;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int getBitsPerPixel() { return bitsPerPixel; }
    public int getDepth()        { return depth; }
    public boolean isBigEndian() { return bigEndian; }
    public boolean isTrueColor() { return trueColor; }
    public int getRedMax()       { return redMax; }
    public int getGreenMax()     { return greenMax; }
    public int getBlueMax()      { return blueMax; }
    public int getRedShift()     { return redShift; }
    public int getGreenShift()   { return greenShift; }
    public int getBlueShift()    { return blueShift; }

    /** Bytes per pixel (bitsPerPixel / 8). */
    public int getBytesPerPixel() { return bitsPerPixel / 8; }

    /**
     * Bytes per CPIXEL as used by TRLE and ZRLE encodings.
     *
     * <p>CPIXEL is 3 bytes when: trueColor is set, bitsPerPixel is 32, depth is 24 or less,
     * and all R/G/B bit-fields reside in either the least-significant or most-significant 3 bytes.
     * Otherwise it equals bytesPerPixel.</p>
     */
    public int getBytesPerCPixel() {
        if (trueColor && bitsPerPixel == 32 && depth <= 24) {
            boolean inLow  = redShift <= 7 && greenShift <= 7 && blueShift <= 7;
            boolean inHigh = redShift >= 8 && greenShift >= 8 && blueShift >= 8;
            if (inLow || inHigh) return 3;
        }
        return getBytesPerPixel();
    }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /** Reads a 16-byte PIXEL_FORMAT structure from {@code in}. */
    public static PixelFormat parse(InputStream in) throws IOException {
        int bpp       = RfbIO.readU8(in);
        int depth     = RfbIO.readU8(in);
        boolean be    = RfbIO.readU8(in) != 0;
        boolean tc    = RfbIO.readU8(in) != 0;
        int rMax      = RfbIO.readU16(in);
        int gMax      = RfbIO.readU16(in);
        int bMax      = RfbIO.readU16(in);
        int rShift    = RfbIO.readU8(in);
        int gShift    = RfbIO.readU8(in);
        int bShift    = RfbIO.readU8(in);
        RfbIO.skipBytes(in, 3); // padding
        return new Builder()
                .bitsPerPixel(bpp).depth(depth).bigEndian(be).trueColor(tc)
                .redMax(rMax).greenMax(gMax).blueMax(bMax)
                .redShift(rShift).greenShift(gShift).blueShift(bShift)
                .build();
    }

    /** Writes this structure as 16 bytes to {@code out}. */
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU8(out, bitsPerPixel);
        RfbIO.writeU8(out, depth);
        RfbIO.writeU8(out, bigEndian ? 1 : 0);
        RfbIO.writeU8(out, trueColor ? 1 : 0);
        RfbIO.writeU16(out, redMax);
        RfbIO.writeU16(out, greenMax);
        RfbIO.writeU16(out, blueMax);
        RfbIO.writeU8(out, redShift);
        RfbIO.writeU8(out, greenShift);
        RfbIO.writeU8(out, blueShift);
        RfbIO.writePadding(out, 3);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PixelFormat)) return false;
        PixelFormat pf = (PixelFormat) o;
        return bitsPerPixel == pf.bitsPerPixel
                && depth       == pf.depth
                && bigEndian   == pf.bigEndian
                && trueColor   == pf.trueColor
                && redMax      == pf.redMax
                && greenMax    == pf.greenMax
                && blueMax     == pf.blueMax
                && redShift    == pf.redShift
                && greenShift  == pf.greenShift
                && blueShift   == pf.blueShift;
    }

    @Override
    public int hashCode() {
        return Objects.hash(bitsPerPixel, depth, bigEndian, trueColor,
                redMax, greenMax, blueMax, redShift, greenShift, blueShift);
    }

    @Override
    public String toString() {
        return "PixelFormat{"
                + "bitsPerPixel=" + bitsPerPixel
                + ", depth=" + depth
                + ", bigEndian=" + bigEndian
                + ", trueColor=" + trueColor
                + ", redMax=" + redMax
                + ", greenMax=" + greenMax
                + ", blueMax=" + blueMax
                + ", redShift=" + redShift
                + ", greenShift=" + greenShift
                + ", blueShift=" + blueShift
                + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int bitsPerPixel;
        private int depth;
        private boolean bigEndian;
        private boolean trueColor;
        private int redMax;
        private int greenMax;
        private int blueMax;
        private int redShift;
        private int greenShift;
        private int blueShift;

        public Builder bitsPerPixel(int v) { bitsPerPixel = v; return this; }
        public Builder depth(int v)        { depth = v;        return this; }
        public Builder bigEndian(boolean v){ bigEndian = v;    return this; }
        public Builder trueColor(boolean v){ trueColor = v;    return this; }
        public Builder redMax(int v)       { redMax = v;       return this; }
        public Builder greenMax(int v)     { greenMax = v;     return this; }
        public Builder blueMax(int v)      { blueMax = v;      return this; }
        public Builder redShift(int v)     { redShift = v;     return this; }
        public Builder greenShift(int v)   { greenShift = v;   return this; }
        public Builder blueShift(int v)    { blueShift = v;    return this; }

        public PixelFormat build() { return new PixelFormat(this); }
    }
}
