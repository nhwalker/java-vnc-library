package io.github.nwalker.vnc.core.encoding;

import io.github.nwalker.vnc.core.EncodingType;
import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * ZRLE (Zlib Run-Length Encoding) data (Section 7.7.6 of RFC 6143).
 *
 * <p>On the wire: a 4-byte U32 length followed by that many bytes of zlib-compressed data.
 * The zlib data encodes TRLE-style tiles (64x64) when decompressed.</p>
 *
 * <p>This class stores the raw compressed bytes. Decompression and tile decoding
 * are left to higher-level processing.</p>
 */
public final class ZrleEncodingData extends EncodingData {

    private final byte[] zlibData;

    private ZrleEncodingData(Builder b) {
        this.zlibData = Arrays.copyOf(b.zlibData, b.zlibData.length);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns a defensive copy of the raw zlib-compressed bytes. */
    public byte[] getZlibData() { return Arrays.copyOf(zlibData, zlibData.length); }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /**
     * Reads ZRLE data from {@code in}.
     * Reads the 4-byte length field then that many bytes of zlib data.
     */
    public static ZrleEncodingData parse(InputStream in) throws IOException {
        long length = RfbIO.readU32(in);
        byte[] data = RfbIO.readBytes(in, (int) length);
        return new Builder().zlibData(data).build();
    }

    @Override
    public EncodingType getEncodingType() { return EncodingType.ZRLE; }

    @Override
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU32(out, zlibData.length);
        out.write(zlibData);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ZrleEncodingData)) return false;
        return Arrays.equals(zlibData, ((ZrleEncodingData) o).zlibData);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(zlibData); }

    @Override
    public String toString() {
        return "ZrleEncodingData{zlibBytes=" + zlibData.length + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private byte[] zlibData = new byte[0];

        public Builder zlibData(byte[] v) { zlibData = v; return this; }

        public ZrleEncodingData build() { return new ZrleEncodingData(this); }
    }
}
