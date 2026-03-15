package io.github.nwalker.vnc.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * A single RGB entry in a color map (Section 7.6.2 of RFC 6143).
 *
 * <p>Each channel is a 16-bit unsigned value in the range [0, 65535].</p>
 */
public final class ColorMapEntry {

    private final int red;   // U16
    private final int green; // U16
    private final int blue;  // U16

    private ColorMapEntry(Builder b) {
        this.red   = b.red;
        this.green = b.green;
        this.blue  = b.blue;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int getRed()   { return red; }
    public int getGreen() { return green; }
    public int getBlue()  { return blue; }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /** Reads a 6-byte color map entry from {@code in}. */
    public static ColorMapEntry parse(InputStream in) throws IOException {
        int r = RfbIO.readU16(in);
        int g = RfbIO.readU16(in);
        int b = RfbIO.readU16(in);
        return new Builder().red(r).green(g).blue(b).build();
    }

    /** Writes this entry as 6 bytes to {@code out}. */
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU16(out, red);
        RfbIO.writeU16(out, green);
        RfbIO.writeU16(out, blue);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColorMapEntry)) return false;
        ColorMapEntry e = (ColorMapEntry) o;
        return red == e.red && green == e.green && blue == e.blue;
    }

    @Override
    public int hashCode() { return Objects.hash(red, green, blue); }

    @Override
    public String toString() {
        return "ColorMapEntry{red=" + red + ", green=" + green + ", blue=" + blue + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int red;
        private int green;
        private int blue;

        public Builder red(int v)   { red   = v; return this; }
        public Builder green(int v) { green = v; return this; }
        public Builder blue(int v)  { blue  = v; return this; }

        public ColorMapEntry build() { return new ColorMapEntry(this); }
    }
}
