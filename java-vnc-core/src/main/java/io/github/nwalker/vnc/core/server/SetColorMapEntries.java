package io.github.nwalker.vnc.core.server;

import io.github.nwalker.vnc.core.ColorMapEntry;
import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * SetColorMapEntries server-to-client message (Section 7.6.2 of RFC 6143).
 *
 * <p>When the pixel format uses a color map, this message updates a range of color map
 * entries starting at {@code firstColor}. Each entry contains 16-bit R, G, B values
 * in the range [0, 65535].</p>
 *
 * <p>Wire format: message-type (U8=1) + 1 padding byte + U16 first-color +
 * U16 number-of-colors + number-of-colors × 6-byte RGB entries.</p>
 */
public final class SetColorMapEntries {

    /** Message type byte on the wire. */
    public static final int MESSAGE_TYPE = 1;

    private final int firstColor; // U16
    private final List<ColorMapEntry> colors;

    private SetColorMapEntries(Builder b) {
        this.firstColor = b.firstColor;
        this.colors     = Collections.unmodifiableList(new ArrayList<>(b.colors));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int getFirstColor() { return firstColor; }

    /** Returns an unmodifiable list of color map entries. */
    public List<ColorMapEntry> getColors() { return colors; }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /**
     * Reads a SetColorMapEntries message from {@code in}.
     * The message-type byte must already have been consumed by the caller.
     */
    public static SetColorMapEntries parse(InputStream in) throws IOException {
        RfbIO.skipBytes(in, 1); // padding
        int firstColor = RfbIO.readU16(in);
        int count      = RfbIO.readU16(in);
        List<ColorMapEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(ColorMapEntry.parse(in));
        }
        return new Builder().firstColor(firstColor).colors(entries).build();
    }

    /** Writes the complete message (including type byte) to {@code out}. */
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU8(out, MESSAGE_TYPE);
        RfbIO.writePadding(out, 1);
        RfbIO.writeU16(out, firstColor);
        RfbIO.writeU16(out, colors.size());
        for (ColorMapEntry entry : colors) {
            entry.write(out);
        }
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SetColorMapEntries)) return false;
        SetColorMapEntries s = (SetColorMapEntries) o;
        return firstColor == s.firstColor && colors.equals(s.colors);
    }

    @Override
    public int hashCode() { return Objects.hash(firstColor, colors); }

    @Override
    public String toString() {
        return "SetColorMapEntries{firstColor=" + firstColor + ", count=" + colors.size() + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int firstColor;
        private List<ColorMapEntry> colors = new ArrayList<>();

        public Builder firstColor(int v)               { firstColor = v; return this; }
        public Builder colors(List<ColorMapEntry> v)   { colors = v;     return this; }
        public Builder addColor(ColorMapEntry v)        { colors.add(v);  return this; }

        public SetColorMapEntries build() { return new SetColorMapEntries(this); }
    }
}
