package io.github.nwalker.vnc.core.encoding;

import io.github.nwalker.vnc.core.EncodingType;
import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * TightPNG encoding data (encoding-type = -260).
 *
 * <p>Wire format per rectangle:
 * <ol>
 *   <li>1 byte: compression-control byte (bits 3-0 = 0x09 for PNG compression)</li>
 *   <li>Compact length (1–3 bytes) encoding the PNG payload length</li>
 *   <li>PNG data bytes</li>
 * </ol>
 *
 * <p>The compact length encoding uses the high bit of each byte as a continuation flag:
 * values up to 127 fit in 1 byte, up to 16383 in 2 bytes, up to 2097151 in 3 bytes.</p>
 *
 * <p>This class stores the raw PNG bytes. PNG decoding is left to higher-level processing.</p>
 */
public final class TightPngEncodingData extends EncodingData {

    /** Compression-control nibble value indicating PNG image data. */
    public static final int PNG_COMPRESSION = 0x09;

    private final byte compressionControl;
    private final byte[] pngData;

    private TightPngEncodingData(Builder b) {
        this.compressionControl = b.compressionControl;
        this.pngData = Arrays.copyOf(b.pngData, b.pngData.length);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns the compression-control byte. For TightPNG, bits 3-0 are 0x09 (PNG). */
    public byte getCompressionControl() { return compressionControl; }

    /** Returns a defensive copy of the raw PNG bytes. */
    public byte[] getPngData() { return Arrays.copyOf(pngData, pngData.length); }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /**
     * Reads TightPNG data from {@code in}.
     * Reads the compression-control byte, compact length, then that many bytes of PNG data.
     */
    public static TightPngEncodingData parse(InputStream in) throws IOException {
        byte compressionControl = (byte) RfbIO.readU8(in);
        int length = readCompactLength(in);
        byte[] pngData = RfbIO.readBytes(in, length);
        return new Builder().compressionControl(compressionControl).pngData(pngData).build();
    }

    /**
     * Reads a compact length value (1–3 bytes).
     * Each byte's high bit is a continuation flag; the low 7 bits contribute to the value.
     */
    static int readCompactLength(InputStream in) throws IOException {
        int b0 = RfbIO.readU8(in);
        int length = b0 & 0x7F;
        if ((b0 & 0x80) != 0) {
            int b1 = RfbIO.readU8(in);
            length |= (b1 & 0x7F) << 7;
            if ((b1 & 0x80) != 0) {
                int b2 = RfbIO.readU8(in);
                length |= b2 << 14;
            }
        }
        return length;
    }

    @Override
    public EncodingType getEncodingType() { return EncodingType.TIGHT_PNG; }

    @Override
    public void write(OutputStream out) throws IOException {
        out.write(compressionControl & 0xFF);
        writeCompactLength(out, pngData.length);
        out.write(pngData);
    }

    /**
     * Writes a compact length value (1–3 bytes).
     * Each byte's high bit signals that another byte follows.
     */
    static void writeCompactLength(OutputStream out, int length) throws IOException {
        if (length < 128) {
            out.write(length);
        } else if (length < 16384) {
            out.write((length & 0x7F) | 0x80);
            out.write(length >> 7);
        } else {
            out.write((length & 0x7F) | 0x80);
            out.write(((length >> 7) & 0x7F) | 0x80);
            out.write(length >> 14);
        }
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TightPngEncodingData)) return false;
        TightPngEncodingData that = (TightPngEncodingData) o;
        return compressionControl == that.compressionControl
                && Arrays.equals(pngData, that.pngData);
    }

    @Override
    public int hashCode() {
        int result = compressionControl;
        result = 31 * result + Arrays.hashCode(pngData);
        return result;
    }

    @Override
    public String toString() {
        return "TightPngEncodingData{compressionControl=0x"
                + Integer.toHexString(compressionControl & 0xFF)
                + ", pngBytes=" + pngData.length + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private byte compressionControl = (byte) PNG_COMPRESSION;
        private byte[] pngData = new byte[0];

        public Builder compressionControl(byte v) { compressionControl = v; return this; }
        public Builder pngData(byte[] v)          { pngData = v;            return this; }

        public TightPngEncodingData build() { return new TightPngEncodingData(this); }
    }
}
