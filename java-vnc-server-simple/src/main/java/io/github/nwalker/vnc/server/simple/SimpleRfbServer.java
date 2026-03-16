package io.github.nwalker.vnc.server.simple;

import io.github.nwalker.vnc.core.EncodingType;
import io.github.nwalker.vnc.core.PixelFormat;
import io.github.nwalker.vnc.core.RfbAuthenticationException;
import io.github.nwalker.vnc.core.RfbServer;
import io.github.nwalker.vnc.core.SecurityType;
import io.github.nwalker.vnc.core.VncServerProtocol;
import io.github.nwalker.vnc.core.client.FramebufferUpdateRequest;
import io.github.nwalker.vnc.core.client.SetEncodings;
import io.github.nwalker.vnc.core.client.SetPixelFormat;
import io.github.nwalker.vnc.core.encoding.RawEncodingData;
import io.github.nwalker.vnc.core.handshake.ProtocolVersionMessage;
import io.github.nwalker.vnc.core.handshake.SecurityResult;
import io.github.nwalker.vnc.core.handshake.ServerSecurityHandshake;
import io.github.nwalker.vnc.core.handshake.VncAuthChallenge;
import io.github.nwalker.vnc.core.handshake.VncAuthResponse;
import io.github.nwalker.vnc.core.init.ClientInit;
import io.github.nwalker.vnc.core.init.ServerInit;
import io.github.nwalker.vnc.core.server.FramebufferUpdate;
import io.github.nwalker.vnc.core.server.Rectangle;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Per-connection {@link RfbServer} implementation used by {@link SimpleVncServer}.
 *
 * <p>This class is package-private; all public API is on {@link SimpleVncServer}.</p>
 *
 * <p>Security: No authentication — only {@link SecurityType#NONE} is offered.</p>
 * <p>Encoding: Only RAW — {@link SetEncodings} and {@link SetPixelFormat} messages
 * from the client are accepted but ignored; all updates are always sent in
 * {@link #PIXEL_FORMAT}.</p>
 */
final class SimpleRfbServer implements RfbServer {

    /**
     * Fixed wire pixel format used for all outbound FramebufferUpdate messages.
     *
     * <p>32bpp, depth 24, little-endian, true-colour:
     * pixel value = (R &lt;&lt; 16) | (G &lt;&lt; 8) | B,
     * transmitted LSB-first as bytes [B, G, R, 0x00].</p>
     */
    static final PixelFormat PIXEL_FORMAT = PixelFormat.builder()
            .bitsPerPixel(32).depth(24)
            .bigEndian(false).trueColor(true)
            .redMax(255).greenMax(255).blueMax(255)
            .redShift(16).greenShift(8).blueShift(0)
            .build();

    private static final ProtocolVersionMessage SERVER_VERSION =
            ProtocolVersionMessage.builder().major(3).minor(8).build();

    private static final ServerSecurityHandshake NO_AUTH_HANDSHAKE =
            ServerSecurityHandshake.builder()
                    .addSecurityType(SecurityType.NONE.getCode())
                    .build();

    // -------------------------------------------------------------------------

    private final SimpleVncServer owner;
    private VncServerProtocol protocol;

    SimpleRfbServer(SimpleVncServer owner) {
        this.owner = owner;
    }

    /**
     * Runs the full RFB session (handshake + receive loop) on the given streams.
     * Blocks until the client disconnects or an error occurs.
     */
    void run(InputStream in, OutputStream out) throws IOException {
        protocol = new VncServerProtocol(in, out, this);
        protocol.handshake();
        protocol.receiveLoop();
    }

    // -------------------------------------------------------------------------
    // RfbServer — handshake
    // -------------------------------------------------------------------------

    @Override
    public ProtocolVersionMessage getServerProtocolVersion() {
        return SERVER_VERSION;
    }

    @Override
    public void onClientProtocolVersion(ProtocolVersionMessage clientVersion) {
        // Accept any version the client chooses.
    }

    @Override
    public ServerSecurityHandshake createSecurityHandshake() {
        return NO_AUTH_HANDSHAKE;
    }

    @Override
    public void onClientInit(ClientInit clientInit) {
        // Shared-session flag is informational; single framebuffer is always shared.
    }

    @Override
    public ServerInit createServerInit() {
        BufferedImage img = owner.getImage();
        return ServerInit.builder()
                .framebufferWidth(img.getWidth())
                .framebufferHeight(img.getHeight())
                .pixelFormat(PIXEL_FORMAT)
                .name(owner.getDesktopName())
                .build();
    }

    // -------------------------------------------------------------------------
    // RfbServer — client-to-server messages
    // -------------------------------------------------------------------------

    @Override
    public void onSetPixelFormat(SetPixelFormat msg) {
        // We always send in PIXEL_FORMAT regardless of what the client requests.
    }

    @Override
    public void onSetEncodings(SetEncodings msg) {
        // We always use RAW encoding regardless of client preferences.
    }

    @Override
    public void onFramebufferUpdateRequest(FramebufferUpdateRequest request) {
        try {
            protocol.sendFramebufferUpdate(buildUpdate(request));
        } catch (IOException e) {
            // The next read in receiveLoop will surface the same failure.
        }
    }

    // -------------------------------------------------------------------------
    // RfbServer — lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void onDisconnected(Exception cause) {
        owner.onClientDisconnected(this, cause);
    }

    // -------------------------------------------------------------------------
    // Pixel encoding
    // -------------------------------------------------------------------------

    /**
     * Encodes the requested region of the current image as a single-rectangle
     * RAW {@link FramebufferUpdate}.
     *
     * <p>The request coordinates are clamped to the image bounds so that a
     * stale request referencing pixels outside the current image does not throw.</p>
     */
    private FramebufferUpdate buildUpdate(FramebufferUpdateRequest request) {
        BufferedImage img = owner.getImage();

        int imgW = img.getWidth();
        int imgH = img.getHeight();

        // Clamp request region to actual image bounds.
        int x = Math.min(request.getX(), imgW);
        int y = Math.min(request.getY(), imgH);
        int w = Math.min(request.getWidth(),  imgW - x);
        int h = Math.min(request.getHeight(), imgH - y);

        byte[] pixels = (w == 0 || h == 0)
                ? new byte[0]
                : encodePixels(img.getRGB(x, y, w, h, null, 0, w));

        Rectangle rect = Rectangle.builder()
                .x(x).y(y).width(w).height(h)
                .encodingCode(EncodingType.RAW.getCode())
                .encodingData(RawEncodingData.builder().pixels(pixels).build())
                .build();

        return FramebufferUpdate.builder().addRectangle(rect).build();
    }

    /**
     * Converts an array of ARGB integers (from {@link BufferedImage#getRGB}) to
     * the wire byte sequence for {@link #PIXEL_FORMAT}.
     *
     * <p>Each ARGB int {@code 0xAARRGGBB} is written as four bytes:
     * {@code [B, G, R, 0x00]} — little-endian pixel value {@code (R<<16)|(G<<8)|B}.</p>
     */
    private static byte[] encodePixels(int[] argbPixels) {
        byte[] bytes = new byte[argbPixels.length * 4];
        for (int i = 0; i < argbPixels.length; i++) {
            int argb = argbPixels[i];
            bytes[i * 4]     = (byte)  (argb        & 0xFF); // B — bits  0–7
            bytes[i * 4 + 1] = (byte) ((argb >>  8) & 0xFF); // G — bits  8–15
            bytes[i * 4 + 2] = (byte) ((argb >> 16) & 0xFF); // R — bits 16–23
            bytes[i * 4 + 3] = 0;                             //   — bits 24–31 unused
        }
        return bytes;
    }
}
