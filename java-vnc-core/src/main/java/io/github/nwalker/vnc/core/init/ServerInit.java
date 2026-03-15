package io.github.nwalker.vnc.core.init;

import io.github.nwalker.vnc.core.PixelFormat;
import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * ServerInit message (Section 7.3.2 of RFC 6143).
 *
 * <p>Sent by the server after receiving {@link ClientInit}. Communicates the
 * framebuffer dimensions, the server's natural pixel format, and the desktop name.</p>
 *
 * <p>Wire format:</p>
 * <pre>
 *   U16  framebuffer-width
 *   U16  framebuffer-height
 *   16b  server-pixel-format (PIXEL_FORMAT)
 *   U32  name-length
 *   U8[] name-string (ISO 8859-1)
 * </pre>
 */
public final class ServerInit {

    private final int framebufferWidth;  // U16
    private final int framebufferHeight; // U16
    private final PixelFormat pixelFormat;
    private final String name;

    private ServerInit(Builder b) {
        this.framebufferWidth  = b.framebufferWidth;
        this.framebufferHeight = b.framebufferHeight;
        this.pixelFormat       = b.pixelFormat;
        this.name              = b.name;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int getFramebufferWidth()  { return framebufferWidth; }
    public int getFramebufferHeight() { return framebufferHeight; }
    public PixelFormat getPixelFormat() { return pixelFormat; }
    public String getName() { return name; }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /** Reads a ServerInit message from {@code in}. */
    public static ServerInit parse(InputStream in) throws IOException {
        int w  = RfbIO.readU16(in);
        int h  = RfbIO.readU16(in);
        PixelFormat pf = PixelFormat.parse(in);
        long nameLen = RfbIO.readU32(in);
        byte[] nameBytes = RfbIO.readBytes(in, (int) nameLen);
        String name = new String(nameBytes, StandardCharsets.ISO_8859_1);
        return new Builder()
                .framebufferWidth(w)
                .framebufferHeight(h)
                .pixelFormat(pf)
                .name(name)
                .build();
    }

    /** Writes the ServerInit message to {@code out}. */
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU16(out, framebufferWidth);
        RfbIO.writeU16(out, framebufferHeight);
        pixelFormat.write(out);
        byte[] nameBytes = name.getBytes(StandardCharsets.ISO_8859_1);
        RfbIO.writeU32(out, nameBytes.length);
        out.write(nameBytes);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ServerInit)) return false;
        ServerInit s = (ServerInit) o;
        return framebufferWidth  == s.framebufferWidth
                && framebufferHeight == s.framebufferHeight
                && Objects.equals(pixelFormat, s.pixelFormat)
                && Objects.equals(name, s.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(framebufferWidth, framebufferHeight, pixelFormat, name);
    }

    @Override
    public String toString() {
        return "ServerInit{"
                + "framebufferWidth=" + framebufferWidth
                + ", framebufferHeight=" + framebufferHeight
                + ", pixelFormat=" + pixelFormat
                + ", name='" + name + '\''
                + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int framebufferWidth;
        private int framebufferHeight;
        private PixelFormat pixelFormat;
        private String name = "";

        public Builder framebufferWidth(int v)  { framebufferWidth  = v; return this; }
        public Builder framebufferHeight(int v) { framebufferHeight = v; return this; }
        public Builder pixelFormat(PixelFormat v){ pixelFormat = v;      return this; }
        public Builder name(String v)           { name = v;             return this; }

        public ServerInit build() { return new ServerInit(this); }
    }
}
