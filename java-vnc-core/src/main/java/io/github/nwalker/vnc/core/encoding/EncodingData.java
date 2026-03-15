package io.github.nwalker.vnc.core.encoding;

import io.github.nwalker.vnc.core.EncodingType;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Base class for the encoding-specific data that follows a rectangle header in a
 * FramebufferUpdate message (Section 7.6.1 of RFC 6143).
 */
public abstract class EncodingData {

    /** The encoding type represented by this data object. */
    public abstract EncodingType getEncodingType();

    /**
     * Writes the encoding-specific bytes to {@code out}.
     * Does not write the rectangle header; that is handled by {@link io.github.nwalker.vnc.core.server.Rectangle}.
     */
    public abstract void write(OutputStream out) throws IOException;
}
