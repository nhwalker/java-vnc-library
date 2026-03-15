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
 * Hextile encoding data (Section 7.7.4 of RFC 6143).
 *
 * <p>Hextile splits a rectangle into 16x16 tiles. Each tile begins with a
 * subencoding mask byte that determines what data follows. This class parses
 * the complete tile stream and stores the raw bytes for faithful reproduction.</p>
 *
 * <p>Subencoding mask bits:</p>
 * <ul>
 *   <li>0x01 – Raw: tile is width*height*bytesPerPixel raw pixels</li>
 *   <li>0x02 – BackgroundSpecified: background pixel value follows</li>
 *   <li>0x04 – ForegroundSpecified: foreground pixel value follows</li>
 *   <li>0x08 – AnySubrects: subrect count byte follows, then subrects</li>
 *   <li>0x10 – SubrectsColored: each subrect is preceded by a pixel value</li>
 * </ul>
 */
public final class HextileEncodingData extends EncodingData {

    private final byte[] rawBytes;

    private HextileEncodingData(Builder b) {
        this.rawBytes = Arrays.copyOf(b.rawBytes, b.rawBytes.length);
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns a defensive copy of the complete hextile byte stream. */
    public byte[] getRawBytes() { return Arrays.copyOf(rawBytes, rawBytes.length); }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /**
     * Reads the complete hextile stream for a rectangle of the given dimensions.
     *
     * @param in          the input stream
     * @param width       rectangle width in pixels
     * @param height      rectangle height in pixels
     * @param pixelFormat the negotiated pixel format
     */
    public static HextileEncodingData parse(InputStream in, int width, int height,
                                            PixelFormat pixelFormat) throws IOException {
        int bpp = pixelFormat.getBytesPerPixel();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        for (int tileY = 0; tileY < height; tileY += 16) {
            int tileH = Math.min(16, height - tileY);
            for (int tileX = 0; tileX < width; tileX += 16) {
                int tileW = Math.min(16, width - tileX);
                parseTile(in, buf, tileW, tileH, bpp);
            }
        }
        return new Builder().rawBytes(buf.toByteArray()).build();
    }

    private static void parseTile(InputStream in, ByteArrayOutputStream buf,
                                  int tileW, int tileH, int bpp) throws IOException {
        int subenc = RfbIO.readU8(in);
        buf.write(subenc);

        if ((subenc & 0x01) != 0) {
            // Raw tile
            byte[] pixels = RfbIO.readBytes(in, tileW * tileH * bpp);
            buf.write(pixels);
            return;
        }

        if ((subenc & 0x02) != 0) {
            // BackgroundSpecified
            byte[] bg = RfbIO.readBytes(in, bpp);
            buf.write(bg);
        }

        if ((subenc & 0x04) != 0) {
            // ForegroundSpecified
            byte[] fg = RfbIO.readBytes(in, bpp);
            buf.write(fg);
        }

        if ((subenc & 0x08) != 0) {
            // AnySubrects
            int nSubrects = RfbIO.readU8(in);
            buf.write(nSubrects);
            int subrectBytes;
            if ((subenc & 0x10) != 0) {
                // SubrectsColored: each subrect has pixel + 2 position bytes
                subrectBytes = nSubrects * (bpp + 2);
            } else {
                // Uniform color subrects: 2 position bytes each
                subrectBytes = nSubrects * 2;
            }
            byte[] subrectData = RfbIO.readBytes(in, subrectBytes);
            buf.write(subrectData);
        }
    }

    @Override
    public EncodingType getEncodingType() { return EncodingType.HEXTILE; }

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
        if (!(o instanceof HextileEncodingData)) return false;
        return Arrays.equals(rawBytes, ((HextileEncodingData) o).rawBytes);
    }

    @Override
    public int hashCode() { return Arrays.hashCode(rawBytes); }

    @Override
    public String toString() {
        return "HextileEncodingData{rawBytes=" + rawBytes.length + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private byte[] rawBytes = new byte[0];

        public Builder rawBytes(byte[] v) { rawBytes = v; return this; }

        public HextileEncodingData build() { return new HextileEncodingData(this); }
    }
}
