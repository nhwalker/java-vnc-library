package io.github.nwalker.vnc.core.encoding;

import io.github.nwalker.vnc.core.EncodingType;
import io.github.nwalker.vnc.core.PixelFormat;
import io.github.nwalker.vnc.core.RfbIO;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * TRLE (Tiled Run-Length Encoding) data (Section 7.7.5 of RFC 6143).
 *
 * <p>TRLE divides a rectangle into 16x16 tiles and encodes each with a variable
 * subencoding. This class parses the complete tile stream, collecting the raw bytes
 * needed to faithfully reconstruct the encoding.</p>
 *
 * <p>Subencoding values:</p>
 * <ul>
 *   <li>0 – Raw</li>
 *   <li>1 – Solid (single CPIXEL)</li>
 *   <li>2–16 – Packed palette</li>
 *   <li>127 – Packed palette, reuse previous palette</li>
 *   <li>128 – Plain RLE</li>
 *   <li>129 – Palette RLE, reuse previous palette</li>
 *   <li>130–255 – Palette RLE</li>
 * </ul>
 */
public final class TrleEncodingData extends EncodingData {

    private final byte[] rawBytes;

    private TrleEncodingData(Builder b) {
        this.rawBytes = Arrays.copyOf(b.rawBytes, b.rawBytes.length);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns a defensive copy of the complete TRLE byte stream. */
    public byte[] getRawBytes() { return Arrays.copyOf(rawBytes, rawBytes.length); }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /**
     * Reads the complete TRLE stream for a rectangle of the given dimensions.
     *
     * @param in          the input stream
     * @param width       rectangle width in pixels
     * @param height      rectangle height in pixels
     * @param pixelFormat the negotiated pixel format
     */
    public static TrleEncodingData parse(InputStream in, int width, int height,
                                         PixelFormat pixelFormat) throws IOException {
        int bpcp = pixelFormat.getBytesPerCPixel();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int lastPaletteSize = 0;

        for (int tileY = 0; tileY < height; tileY += 16) {
            int tileH = Math.min(16, height - tileY);
            for (int tileX = 0; tileX < width; tileX += 16) {
                int tileW = Math.min(16, width - tileX);
                lastPaletteSize = parseTile(in, buf, tileW, tileH, bpcp, lastPaletteSize);
            }
        }
        return new Builder().rawBytes(buf.toByteArray()).build();
    }

    /**
     * Parses one tile and returns the current palette size (for reuse tracking).
     */
    private static int parseTile(InputStream in, ByteArrayOutputStream buf,
                                  int tileW, int tileH, int bpcp,
                                  int lastPaletteSize) throws IOException {
        int subenc = RfbIO.readU8(in);
        buf.write(subenc);

        if (subenc == 0) {
            // Raw
            buf.write(RfbIO.readBytes(in, tileW * tileH * bpcp));
            return 0;
        }

        if (subenc == 1) {
            // Solid
            buf.write(RfbIO.readBytes(in, bpcp));
            return 1;
        }

        if (subenc >= 2 && subenc <= 16) {
            // Packed palette
            int paletteSize = subenc;
            buf.write(RfbIO.readBytes(in, paletteSize * bpcp));
            int packedBytes = packedPixelBytes(tileW, tileH, paletteSize);
            buf.write(RfbIO.readBytes(in, packedBytes));
            return paletteSize;
        }

        if (subenc == 127) {
            // Packed palette, reuse previous palette
            int packedBytes = packedPixelBytes(tileW, tileH, lastPaletteSize);
            buf.write(RfbIO.readBytes(in, packedBytes));
            return lastPaletteSize;
        }

        if (subenc == 128) {
            // Plain RLE
            readRleRuns(in, buf, tileW * tileH, bpcp);
            return 0;
        }

        if (subenc == 129) {
            // Palette RLE, reuse previous palette
            readPaletteRleRuns(in, buf, tileW * tileH);
            return lastPaletteSize;
        }

        if (subenc >= 130 && subenc <= 255) {
            // Palette RLE
            int paletteSize = subenc - 128;
            buf.write(RfbIO.readBytes(in, paletteSize * bpcp));
            readPaletteRleRuns(in, buf, tileW * tileH);
            return paletteSize;
        }

        // 17–126 are unused; treat as no data (per spec)
        return lastPaletteSize;
    }

    /** Number of bytes for packed pixels given palette size and tile dimensions. */
    private static int packedPixelBytes(int w, int h, int paletteSize) {
        if (paletteSize <= 2) return ((w + 7) / 8) * h;
        if (paletteSize <= 4) return ((w + 3) / 4) * h;
        return ((w + 1) / 2) * h;
    }

    /** Reads plain-RLE runs (each: bytesPerCPixel pixel + length encoding) until pixelsLeft consumed. */
    private static void readRleRuns(InputStream in, ByteArrayOutputStream buf,
                                    int pixelsLeft, int bpcp) throws IOException {
        while (pixelsLeft > 0) {
            buf.write(RfbIO.readBytes(in, bpcp));
            int runLen = readRleLength(in, buf);
            pixelsLeft -= runLen;
        }
    }

    /** Reads palette-RLE runs (each: 1-byte palette index, possibly with length) until pixelsLeft consumed. */
    private static void readPaletteRleRuns(InputStream in, ByteArrayOutputStream buf,
                                           int pixelsLeft) throws IOException {
        while (pixelsLeft > 0) {
            int idx = RfbIO.readU8(in);
            buf.write(idx);
            int runLen;
            if ((idx & 0x80) != 0) {
                runLen = readRleLength(in, buf);
            } else {
                runLen = 1;
            }
            pixelsLeft -= runLen;
        }
    }

    /**
     * Reads a TRLE run-length value from {@code in}, writes the length bytes to {@code buf},
     * and returns the decoded run length.
     *
     * <p>Format: one or more bytes; value 255 means "add 255 and read another byte".
     * Final byte gives remainder. The run length = 1 + sum of all bytes read.</p>
     */
    private static int readRleLength(InputStream in, ByteArrayOutputStream buf) throws IOException {
        int runLen = 1;
        int b;
        do {
            b = RfbIO.readU8(in);
            buf.write(b);
            runLen += b;
        } while (b == 255);
        return runLen;
    }

    @Override
    public EncodingType getEncodingType() { return EncodingType.TRLE; }

    @Override
    public void write(OutputStream out) throws IOException {
        out.write(rawBytes);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TrleEncodingData)) return false;
        return Arrays.equals(rawBytes, ((TrleEncodingData) o).rawBytes);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(rawBytes); }

    @Override
    public String toString() {
        return "TrleEncodingData{rawBytes=" + rawBytes.length + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private byte[] rawBytes = new byte[0];

        public Builder rawBytes(byte[] v) { rawBytes = v; return this; }

        public TrleEncodingData build() { return new TrleEncodingData(this); }
    }
}
