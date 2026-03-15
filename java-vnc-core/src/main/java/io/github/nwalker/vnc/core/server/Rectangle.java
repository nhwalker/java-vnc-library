package io.github.nwalker.vnc.core.server;

import io.github.nwalker.vnc.core.EncodingType;
import io.github.nwalker.vnc.core.PixelFormat;
import io.github.nwalker.vnc.core.RfbIO;
import io.github.nwalker.vnc.core.encoding.CopyRectEncodingData;
import io.github.nwalker.vnc.core.encoding.CursorEncodingData;
import io.github.nwalker.vnc.core.encoding.DesktopSizeEncodingData;
import io.github.nwalker.vnc.core.encoding.EncodingData;
import io.github.nwalker.vnc.core.encoding.HextileEncodingData;
import io.github.nwalker.vnc.core.encoding.RawEncodingData;
import io.github.nwalker.vnc.core.encoding.RreEncodingData;
import io.github.nwalker.vnc.core.encoding.TrleEncodingData;
import io.github.nwalker.vnc.core.encoding.ZrleEncodingData;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * A single rectangle within a {@link FramebufferUpdate} (Section 7.6.1 of RFC 6143).
 *
 * <p>Each rectangle has a position, size, encoding type, and encoding-specific data.
 * For pseudo-encodings (Cursor, DesktopSize), the x/y/width/height fields have
 * encoding-specific meanings documented in the respective {@link EncodingData} subclass.</p>
 */
public final class Rectangle {

    private final int x;            // U16
    private final int y;            // U16
    private final int width;        // U16
    private final int height;       // U16
    private final int encodingCode; // S32 raw wire value
    private final EncodingData encodingData;

    private Rectangle(Builder b) {
        this.x            = b.x;
        this.y            = b.y;
        this.width        = b.width;
        this.height       = b.height;
        this.encodingCode = b.encodingCode;
        this.encodingData = b.encodingData;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int getX()      { return x; }
    public int getY()      { return y; }
    public int getWidth()  { return width; }
    public int getHeight() { return height; }
    /** Raw S32 encoding-type code as transmitted on the wire. */
    public int getEncodingCode() { return encodingCode; }
    public EncodingData getEncodingData() { return encodingData; }
    /**
     * Returns the known {@link EncodingType} for this rectangle, or {@code null} if the
     * encoding code is not one of the values defined in the RFC.
     */
    public EncodingType getEncodingType() { return EncodingType.fromCode(encodingCode); }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /**
     * Reads a rectangle header and its encoding data from {@code in}.
     *
     * @param in          the input stream
     * @param pixelFormat the currently negotiated pixel format (needed for encoding data parsing)
     */
    public static Rectangle parse(InputStream in, PixelFormat pixelFormat) throws IOException {
        int x    = RfbIO.readU16(in);
        int y    = RfbIO.readU16(in);
        int w    = RfbIO.readU16(in);
        int h    = RfbIO.readU16(in);
        int encCode = RfbIO.readS32(in);

        EncodingData data = parseEncodingData(in, encCode, w, h, pixelFormat);
        return new Builder().x(x).y(y).width(w).height(h)
                .encodingCode(encCode).encodingData(data).build();
    }

    private static EncodingData parseEncodingData(InputStream in, int encCode,
                                                   int w, int h,
                                                   PixelFormat pf) throws IOException {
        EncodingType known = EncodingType.fromCode(encCode);
        if (known != null) {
            switch (known) {
                case RAW:          return RawEncodingData.parse(in, w, h, pf);
                case COPY_RECT:    return CopyRectEncodingData.parse(in);
                case RRE:          return RreEncodingData.parse(in, pf);
                case HEXTILE:      return HextileEncodingData.parse(in, w, h, pf);
                case TRLE:         return TrleEncodingData.parse(in, w, h, pf);
                case ZRLE:         return ZrleEncodingData.parse(in);
                case CURSOR:       return CursorEncodingData.parse(in, w, h, pf);
                case DESKTOP_SIZE: return DesktopSizeEncodingData.parse(in);
            }
        }
        // Unknown encoding: store no bytes; the caller cannot safely continue parsing
        // the stream for further rectangles after this. Callers should handle this case.
        return new UnknownEncodingData(encCode);
    }

    /** Writes the rectangle header and encoding data to {@code out}. */
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU16(out, x);
        RfbIO.writeU16(out, y);
        RfbIO.writeU16(out, width);
        RfbIO.writeU16(out, height);
        RfbIO.writeS32(out, encodingCode);
        encodingData.write(out);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Rectangle)) return false;
        Rectangle r = (Rectangle) o;
        return x == r.x && y == r.y && width == r.width && height == r.height
                && encodingCode == r.encodingCode
                && Objects.equals(encodingData, r.encodingData);
    }

    @Override
    public int hashCode() { return Objects.hash(x, y, width, height, encodingCode, encodingData); }

    @Override
    public String toString() {
        EncodingType et = getEncodingType();
        String encStr = et != null ? et.name() : ("0x" + Integer.toHexString(encodingCode));
        return "Rectangle{x=" + x + ", y=" + y + ", width=" + width + ", height=" + height
                + ", encoding=" + encStr + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int x;
        private int y;
        private int width;
        private int height;
        private int encodingCode;
        private EncodingData encodingData;

        public Builder x(int v)                       { x = v;             return this; }
        public Builder y(int v)                       { y = v;             return this; }
        public Builder width(int v)                   { width = v;         return this; }
        public Builder height(int v)                  { height = v;        return this; }
        public Builder encodingCode(int v)            { encodingCode = v;  return this; }
        public Builder encodingData(EncodingData v)   {
            encodingData = v;
            if (v != null && v.getEncodingType() != null) {
                encodingCode = v.getEncodingType().getCode();
            }
            return this;
        }

        public Rectangle build() { return new Rectangle(this); }
    }

    // -------------------------------------------------------------------------
    // Package-private: unknown encoding placeholder
    // -------------------------------------------------------------------------

    /**
     * Placeholder for encoding types not defined by RFC 6143.
     * Carries no payload bytes; only the encoding code is preserved.
     */
    static final class UnknownEncodingData extends EncodingData {

        private final int code;

        UnknownEncodingData(int code) { this.code = code; }

        @Override
        public EncodingType getEncodingType() { return null; }

        public int getCode() { return code; }

        @Override
        public void write(OutputStream out) throws IOException {
            // No payload for unknown encoding; encoding code written by Rectangle.write()
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof UnknownEncodingData)) return false;
            return code == ((UnknownEncodingData) o).code;
        }

        @Override
        public int hashCode() { return code; }

        @Override
        public String toString() { return "UnknownEncodingData{code=" + code + '}'; }
    }
}
