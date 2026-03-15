package io.github.nwalker.vnc.core;

import io.github.nwalker.vnc.core.client.ClientCutText;
import io.github.nwalker.vnc.core.client.FramebufferUpdateRequest;
import io.github.nwalker.vnc.core.client.KeyEvent;
import io.github.nwalker.vnc.core.client.PointerEvent;
import io.github.nwalker.vnc.core.client.SetEncodings;
import io.github.nwalker.vnc.core.client.SetPixelFormat;
import io.github.nwalker.vnc.core.handshake.ProtocolVersionMessage;
import io.github.nwalker.vnc.core.handshake.SecurityFailureMessage;
import io.github.nwalker.vnc.core.handshake.SecurityResult;
import io.github.nwalker.vnc.core.handshake.SecurityTypeSelection;
import io.github.nwalker.vnc.core.handshake.ServerSecurityHandshake;
import io.github.nwalker.vnc.core.handshake.VncAuthChallenge;
import io.github.nwalker.vnc.core.handshake.VncAuthResponse;
import io.github.nwalker.vnc.core.init.ServerInit;
import io.github.nwalker.vnc.core.server.Bell;
import io.github.nwalker.vnc.core.server.FramebufferUpdate;
import io.github.nwalker.vnc.core.server.ServerCutText;
import io.github.nwalker.vnc.core.server.SetColorMapEntries;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Implements the RFB protocol state machine for a VNC client (RFC 6143).
 *
 * <p>This class owns the transport streams and drives the protocol through its
 * three phases:</p>
 * <ol>
 *   <li><strong>Handshake</strong> – version negotiation, security, and initialisation
 *       (run by calling {@link #handshake()}).</li>
 *   <li><strong>Normal operation</strong> – the caller may send client-to-server
 *       messages at any time via the {@code send*()} methods after handshake
 *       completes.</li>
 *   <li><strong>Receive loop</strong> – {@link #receiveLoop()} reads server-to-client
 *       messages and dispatches them to the {@link RfbClient} until the connection
 *       closes or an error occurs.</li>
 * </ol>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * Socket socket = new Socket("vnc-host", 5900);
 * VncClientProtocol protocol = new VncClientProtocol(
 *         socket.getInputStream(), socket.getOutputStream(), myRfbClient);
 *
 * // Phase 1: negotiate and authenticate (blocking)
 * protocol.handshake();
 *
 * // Phase 2: configure the session
 * protocol.sendSetEncodings(SetEncodings.builder()
 *         .addEncodingType(EncodingType.ZRLE.getCode())
 *         .addEncodingType(EncodingType.COPY_RECT.getCode())
 *         .addEncodingType(EncodingType.RAW.getCode())
 *         .build());
 * protocol.sendFramebufferUpdateRequest(FramebufferUpdateRequest.builder()
 *         .incremental(false).x(0).y(0)
 *         .width(serverInit.getFramebufferWidth())
 *         .height(serverInit.getFramebufferHeight())
 *         .build());
 *
 * // Phase 3: receive loop (typically run in a dedicated thread)
 * protocol.receiveLoop();
 * }</pre>
 *
 * <h2>Thread safety</h2>
 * <p>The {@code send*()} methods are individually synchronised on the output
 * stream so that they may be called safely from multiple threads concurrently
 * with each other and with {@link #receiveLoop()}. The receive loop itself must
 * run on exactly one thread.</p>
 */
public final class VncClientProtocol {

    private final InputStream in;
    private final OutputStream out;
    private final RfbClient client;

    /**
     * The pixel format currently in effect for parsing incoming
     * {@link FramebufferUpdate} messages. Set from {@code ServerInit} and updated
     * whenever the client sends a {@link SetPixelFormat} message.
     */
    private volatile PixelFormat pixelFormat;

    /**
     * Creates a new protocol handler.
     *
     * @param in     the input stream connected to the VNC server
     * @param out    the output stream connected to the VNC server
     * @param client the application callback object that handles events and
     *               makes handshake decisions
     */
    public VncClientProtocol(InputStream in, OutputStream out, RfbClient client) {
        if (in == null)     throw new IllegalArgumentException("in must not be null");
        if (out == null)    throw new IllegalArgumentException("out must not be null");
        if (client == null) throw new IllegalArgumentException("client must not be null");
        this.in  = in;
        this.out = out;
        this.client = client;
    }

    // -------------------------------------------------------------------------
    // Phase 1: Handshake
    // -------------------------------------------------------------------------

    /**
     * Executes the complete RFB handshake: version negotiation, security
     * negotiation, authentication, and initialisation.
     *
     * <p>On success the session is ready for normal operation and
     * {@link RfbClient#onServerInit(io.github.nwalker.vnc.core.init.ServerInit)} will
     * have been called. On failure an exception is thrown; the caller should
     * close the underlying streams.</p>
     *
     * @throws RfbAuthenticationException if the server rejects the client's credentials
     * @throws IOException                if a protocol error or I/O failure occurs
     */
    public void handshake() throws IOException {
        negotiateVersion();
        int selectedSecurityType = negotiateSecurity();
        exchangeSecurityData(selectedSecurityType);
        exchangeInit();
    }

    /**
     * Phase 1a: exchange ProtocolVersion messages.
     */
    private void negotiateVersion() throws IOException {
        ProtocolVersionMessage serverVersion = ProtocolVersionMessage.parse(in);
        ProtocolVersionMessage clientVersion = client.selectProtocolVersion(serverVersion);
        clientVersion.write(out);
        out.flush();
    }

    /**
     * Phase 1b: exchange security-type handshake and selection.
     *
     * @return the numeric code of the security type the client selected
     */
    private int negotiateSecurity() throws IOException {
        ServerSecurityHandshake handshake = ServerSecurityHandshake.parse(in);

        if (handshake.isFailure()) {
            // Count=0 signals that the server is refusing the connection;
            // for protocol >= 3.8 a reason string follows.
            SecurityFailureMessage failure = SecurityFailureMessage.parse(in);
            throw new IOException("Server refused connection: " + failure.getReason());
        }

        SecurityTypeSelection selection = client.selectSecurityType(handshake);
        selection.write(out);
        out.flush();
        return selection.getSecurityType();
    }

    /**
     * Phase 1c: perform the chosen security exchange and read SecurityResult.
     *
     * @param securityType the numeric code of the chosen security type
     */
    private void exchangeSecurityData(int securityType) throws IOException {
        SecurityType type = SecurityType.fromCode(securityType);

        if (type == SecurityType.NONE) {
            // RFC 6143 §7.2.1: None — no data exchanged, no SecurityResult
            return;
        }

        if (type == SecurityType.VNC_AUTHENTICATION) {
            // RFC 6143 §7.2.2: read 16-byte challenge, write 16-byte response
            VncAuthChallenge challenge = VncAuthChallenge.parse(in);
            VncAuthResponse response = client.provideVncAuthResponse(challenge);
            response.write(out);
            out.flush();
        }
        // For unknown types fall through: SecurityResult is expected below.

        // Read SecurityResult (required for all security types except None)
        SecurityResult result = SecurityResult.parse(in);
        if (!result.isOk()) {
            // RFC 6143 §7.1.3: for protocol >= 3.8 a reason string follows
            String reason = "";
            try {
                SecurityFailureMessage failure = SecurityFailureMessage.parse(in);
                reason = failure.getReason();
            } catch (EOFException ignored) {
                // Protocol < 3.8 or server did not send a reason
            }
            throw new RfbAuthenticationException(reason);
        }
    }

    /**
     * Phase 1d: exchange ClientInit / ServerInit messages.
     */
    private void exchangeInit() throws IOException {
        client.createClientInit().write(out);
        out.flush();

        ServerInit serverInit = ServerInit.parse(in);
        pixelFormat = serverInit.getPixelFormat();
        client.onServerInit(serverInit);
    }

    // -------------------------------------------------------------------------
    // Phase 3: Receive loop
    // -------------------------------------------------------------------------

    /**
     * Reads server-to-client messages in a loop, dispatching each to the
     * appropriate {@link RfbClient} callback method.
     *
     * <p>This method blocks until the connection is closed or an error occurs.
     * When it returns (for any reason), {@link RfbClient#onDisconnected(Exception)}
     * will already have been called with {@code null} (clean close) or the causing
     * exception.</p>
     *
     * <p>This method is intended to run on a dedicated thread after
     * {@link #handshake()} has completed successfully.</p>
     *
     * @throws IOException if an I/O error occurs (the same exception that was
     *                     passed to {@link RfbClient#onDisconnected(Exception)})
     */
    public void receiveLoop() throws IOException {
        try {
            while (true) {
                int messageType = in.read();
                if (messageType == -1) {
                    client.onDisconnected(null);
                    return;
                }
                dispatchServerMessage(messageType);
            }
        } catch (IOException e) {
            client.onDisconnected(e);
            throw e;
        }
    }

    /**
     * Routes one server-to-client message to the appropriate callback.
     *
     * @param messageType the message-type byte already read from the stream
     */
    private void dispatchServerMessage(int messageType) throws IOException {
        switch (messageType) {
            case FramebufferUpdate.MESSAGE_TYPE:
                client.onFramebufferUpdate(FramebufferUpdate.parse(in, pixelFormat));
                break;
            case SetColorMapEntries.MESSAGE_TYPE:
                client.onSetColorMapEntries(SetColorMapEntries.parse(in));
                break;
            case Bell.MESSAGE_TYPE:
                client.onBell(Bell.parse(in));
                break;
            case ServerCutText.MESSAGE_TYPE:
                client.onServerCutText(ServerCutText.parse(in));
                break;
            default:
                throw new IOException(
                        "Unknown server-to-client message type: " + messageType);
        }
    }

    // -------------------------------------------------------------------------
    // Phase 2: Client-to-server messages
    // -------------------------------------------------------------------------

    /**
     * Sends a {@link SetPixelFormat} message to the server, instructing it to
     * use the given pixel format for subsequent framebuffer updates (RFC 6143 §7.5.1).
     *
     * <p>This method also updates the internal pixel format used to parse
     * incoming {@link FramebufferUpdate} messages.</p>
     *
     * @param msg the SetPixelFormat message to send
     * @throws IOException if the write fails
     */
    public void sendSetPixelFormat(SetPixelFormat msg) throws IOException {
        synchronized (out) {
            msg.write(out);
            out.flush();
        }
        pixelFormat = msg.getPixelFormat();
    }

    /**
     * Sends a {@link SetEncodings} message to the server listing the encoding
     * types the client supports, in order of preference (RFC 6143 §7.5.2).
     *
     * @param msg the SetEncodings message to send
     * @throws IOException if the write fails
     */
    public void sendSetEncodings(SetEncodings msg) throws IOException {
        synchronized (out) {
            msg.write(out);
            out.flush();
        }
    }

    /**
     * Sends a {@link FramebufferUpdateRequest} asking the server to send pixel
     * data for a region of the framebuffer (RFC 6143 §7.5.3).
     *
     * @param msg the update request to send
     * @throws IOException if the write fails
     */
    public void sendFramebufferUpdateRequest(FramebufferUpdateRequest msg) throws IOException {
        synchronized (out) {
            msg.write(out);
            out.flush();
        }
    }

    /**
     * Sends a {@link KeyEvent} representing a key press or release
     * (RFC 6143 §7.5.4).
     *
     * @param msg the key event to send
     * @throws IOException if the write fails
     */
    public void sendKeyEvent(KeyEvent msg) throws IOException {
        synchronized (out) {
            msg.write(out);
            out.flush();
        }
    }

    /**
     * Sends a {@link PointerEvent} representing mouse movement or a button
     * press / release (RFC 6143 §7.5.5).
     *
     * @param msg the pointer event to send
     * @throws IOException if the write fails
     */
    public void sendPointerEvent(PointerEvent msg) throws IOException {
        synchronized (out) {
            msg.write(out);
            out.flush();
        }
    }

    /**
     * Sends a {@link ClientCutText} message placing text on the server's
     * clipboard (RFC 6143 §7.5.6).
     *
     * @param msg the cut text message to send
     * @throws IOException if the write fails
     */
    public void sendClientCutText(ClientCutText msg) throws IOException {
        synchronized (out) {
            msg.write(out);
            out.flush();
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the pixel format currently in effect.
     *
     * <p>This is set from {@link io.github.nwalker.vnc.core.init.ServerInit} after
     * the handshake and updated whenever {@link #sendSetPixelFormat(SetPixelFormat)}
     * is called. Returns {@code null} before {@link #handshake()} completes.</p>
     */
    public PixelFormat getPixelFormat() {
        return pixelFormat;
    }
}
