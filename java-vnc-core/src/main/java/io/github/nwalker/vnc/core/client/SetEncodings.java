package io.github.nwalker.vnc.core.client;

import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * SetEncodings client-to-server message (Section 7.5.2 of RFC 6143).
 *
 * <p>Tells the server which pixel encodings (and pseudo-encodings) the client supports,
 * in order of preference. Each encoding is represented as a signed 32-bit integer (S32).</p>
 *
 * <p>Wire format: message-type (U8=2) + 1 padding byte + U16 count + count×S32 encodings.</p>
 */
public final class SetEncodings {

    /** Message type byte on the wire. */
    public static final int MESSAGE_TYPE = 2;

    private final List<Integer> encodingTypes; // S32 values

    private SetEncodings(Builder b) {
        this.encodingTypes = Collections.unmodifiableList(new ArrayList<>(b.encodingTypes));
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** Returns an unmodifiable list of S32 encoding type codes, in preference order. */
    public List<Integer> getEncodingTypes() { return encodingTypes; }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /**
     * Reads a SetEncodings message from {@code in}.
     * The message-type byte must already have been consumed by the caller.
     */
    public static SetEncodings parse(InputStream in) throws IOException {
        RfbIO.skipBytes(in, 1); // padding
        int count = RfbIO.readU16(in);
        List<Integer> types = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            types.add(RfbIO.readS32(in));
        }
        return new Builder().encodingTypes(types).build();
    }

    /** Writes the complete SetEncodings message (including type byte) to {@code out}. */
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU8(out, MESSAGE_TYPE);
        RfbIO.writePadding(out, 1);
        RfbIO.writeU16(out, encodingTypes.size());
        for (int type : encodingTypes) {
            RfbIO.writeS32(out, type);
        }
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SetEncodings)) return false;
        return encodingTypes.equals(((SetEncodings) o).encodingTypes);
    }

    @Override
    public int hashCode() { return Objects.hashCode(encodingTypes); }

    @Override
    public String toString() {
        return "SetEncodings{encodingTypes=" + encodingTypes + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private List<Integer> encodingTypes = new ArrayList<>();

        public Builder encodingTypes(List<Integer> v) { encodingTypes = v; return this; }
        public Builder addEncodingType(int v)          { encodingTypes.add(v); return this; }

        public SetEncodings build() { return new SetEncodings(this); }
    }
}
