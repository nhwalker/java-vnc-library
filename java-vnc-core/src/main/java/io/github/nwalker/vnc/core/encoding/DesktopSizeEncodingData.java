package io.github.nwalker.vnc.core.encoding;

import io.github.nwalker.vnc.core.EncodingType;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * DesktopSize pseudo-encoding data (Section 7.8.2 of RFC 6143).
 *
 * <p>This pseudo-encoding carries no additional bytes beyond the rectangle header.
 * The new framebuffer dimensions are carried in the rectangle's width and height fields.</p>
 */
public final class DesktopSizeEncodingData extends EncodingData {

    private static final DesktopSizeEncodingData INSTANCE = new DesktopSizeEncodingData();

    private DesktopSizeEncodingData() {}

    /** Returns the singleton instance (no data to differentiate instances). */
    public static DesktopSizeEncodingData parse(InputStream in) throws IOException {
        return INSTANCE;
    }

    @Override
    public EncodingType getEncodingType() { return EncodingType.DESKTOP_SIZE; }

    @Override
    public void write(OutputStream out) throws IOException {
        // no payload
    }

    @Override
    public boolean equals(Object o) { return o instanceof DesktopSizeEncodingData; }

    @Override
    public int hashCode() { return DesktopSizeEncodingData.class.hashCode(); }

    @Override
    public String toString() { return "DesktopSizeEncodingData{}"; }

    // Builder is trivial but provided for API consistency.
    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        public DesktopSizeEncodingData build() { return INSTANCE; }
    }
}
