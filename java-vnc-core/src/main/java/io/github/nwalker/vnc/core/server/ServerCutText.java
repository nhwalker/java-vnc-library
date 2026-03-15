package io.github.nwalker.vnc.core.server;

import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * ServerCutText server-to-client message (Section 7.6.4 of RFC 6143).
 *
 * <p>Notifies the client that the server has new ISO 8859-1 (Latin-1) text in its cut buffer.
 * Line endings are represented by newline ({@code 0x0a}) alone; no carriage return is used.</p>
 *
 * <p>Wire format: message-type (U8=3) + 3 padding bytes + U32 length + length bytes of text.</p>
 */
public final class ServerCutText {

    /** Message type byte on the wire. */
    public static final int MESSAGE_TYPE = 3;

    private final String text; // ISO 8859-1

    private ServerCutText(Builder b) {
        this.text = b.text;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public String getText() { return text; }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /**
     * Reads a ServerCutText from {@code in}.
     * The message-type byte must already have been consumed by the caller.
     */
    public static ServerCutText parse(InputStream in) throws IOException {
        RfbIO.skipBytes(in, 3); // padding
        long len = RfbIO.readU32(in);
        byte[] textBytes = RfbIO.readBytes(in, (int) len);
        return new Builder().text(new String(textBytes, StandardCharsets.ISO_8859_1)).build();
    }

    /** Writes the complete message (including type byte) to {@code out}. */
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU8(out, MESSAGE_TYPE);
        RfbIO.writePadding(out, 3);
        byte[] textBytes = text.getBytes(StandardCharsets.ISO_8859_1);
        RfbIO.writeU32(out, textBytes.length);
        out.write(textBytes);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServerCutText)) return false;
        return Objects.equals(text, ((ServerCutText) o).text);
    }

    @Override
    public int hashCode() { return Objects.hashCode(text); }

    @Override
    public String toString() {
        return "ServerCutText{text='" + text + "'}";
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String text = "";

        public Builder text(String v) { text = v; return this; }

        public ServerCutText build() { return new ServerCutText(this); }
    }
}
