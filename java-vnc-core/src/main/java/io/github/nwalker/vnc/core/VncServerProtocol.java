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
import io.github.nwalker.vnc.core.init.ClientInit;
import io.github.nwalker.vnc.core.init.ServerInit;
import io.github.nwalker.vnc.core.server.Bell;
import io.github.nwalker.vnc.core.server.FramebufferUpdate;
import io.github.nwalker.vnc.core.server.ServerCutText;
import io.github.nwalker.vnc.core.server.SetColorMapEntries;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Implements the RFB protocol state machine for a VNC server (RFC 6143).
 *
 * <p>This class owns the transport streams for one client connection and drives
 * the protocol through its three phases:</p>
 * <ol>
 *   <li><strong>Handshake</strong> – version negotiation, security, and initialisation
 *       (run by calling {@link #handshake()}).</li>
 *   <li><strong>Normal operation</strong> – the server may push server-to-client
 *       messages at any time via the {@code send*()} methods after the handshake
 *       completes.</li>
 *   <li><strong>Receive loop</strong> – {@link #receiveLoop()} reads client-to-server
 *       messages and dispatches them to the {@link RfbServer} until the connection
 *       closes or an error occurs.</li>
 * </ol>
 *
 * <h2>Typical usage</h2>
 * <pre>{@code
 * ServerSocket serverSocket = new ServerSocket(5900);
 * Socket socket = serverSocket.accept();
 *
 * VncServerProtocol protocol = new VncServerProtocol(
 *         socket.getInputStream(), socket.getOutputStream(), myRfbServer);
 *
 * // Phase 1: negotiate and authenticate (blocking)
 * protocol.handshake();
 *
 * // Phase 2: push an initial full-screen update in a background thread
 * executorService.submit(() -> {
 *     protocol.sendFramebufferUpdate(buildFullScreenUpdate());
 * });
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
public final class VncServerProtocol {

    private final InputStream in;
    private final OutputStream out;
    private final RfbServer server;

    /**
     * The pixel format currently requested by the connected client. Set from
     * {@link ServerInit} after the handshake and updated when the client sends
     * a {@link SetPixelFormat} message.
     */
    private volatile PixelFormat pixelFormat;

    /**
     * Creates a new server-side protocol handler for one client connection.
     *
     * @param in     the input stream connected to the VNC client
     * @param out    the output stream connected to the VNC client
     * @param server the application callback object that handles events and
     *               makes handshake decisions
     */
    public VncServerProtocol(InputStream in, OutputStream out, RfbServer server) {
        if (in == null)     throw new IllegalArgumentException("in must not be null");
        if (out == null)    throw new IllegalArgumentException("out must not be null");
        if (server == null) throw new IllegalArgumentException("server must not be null");
        this.in     = in;
        this.out    = out;
        this.server = server;
    }

    // -------------------------------------------------------------------------
    // Phase 1: Handshake
    // -------------------------------------------------------------------------

    /**
     * Executes the complete RFB handshake: version negotiation, security
     * negotiation, optional authentication, and initialisation.
     *
     * <p>On success the session is ready for normal operation and
     * {@link RfbServer#createServerInit()} will have been called. On failure an
     * exception is thrown; the caller should close the underlying streams.</p>
     *
     * @throws RfbAuthenticationException if the client fails authentication
     * @throws IOException                if a protocol error or I/O failure occurs
     */
    public void handshake() throws IOException {
        negotiateVersion();
        int selectedSecurityType = negotiateSecurity();
        exchangeSecurityData(selectedSecurityType);
        exchangeInit();
    }

    /**
     * Phase 1a: send server ProtocolVersion, then read and record the client's choice.
     */
    private void negotiateVersion() throws IOException {
        server.getServerProtocolVersion().write(out);
        out.flush();

        ProtocolVersionMessage clientVersion = ProtocolVersionMessage.parse(in);
        server.onClientProtocolVersion(clientVersion);
    }

    /**
     * Phase 1b: send SecurityHandshake, then read and return the client's selection.
     *
     * <p>If the server returns a zero-type handshake the connection is refused:
     * a {@link SecurityFailureMessage} is written and an {@link IOException} is thrown.</p>
     *
     * @return the numeric code of the security type the client selected
     */
    private int negotiateSecurity() throws IOException {
        ServerSecurityHandshake handshake = server.createSecurityHandshake();
        handshake.write(out);
        out.flush();

        if (handshake.isFailure()) {
            // Zero-type handshake: send the reason string and refuse
            SecurityFailureMessage.builder()
                    .reason(server.getConnectionRefusalReason())
                    .build()
                    .write(out);
            out.flush();
            throw new IOException(
                    "Connection refused by server: " + server.getConnectionRefusalReason());
        }

        SecurityTypeSelection selection = SecurityTypeSelection.parse(in);
        return selection.getSecurityType();
    }

    /**
     * Phase 1c: perform the chosen security exchange and send SecurityResult.
     *
     * @param securityType the numeric code of the chosen security type
     */
    private void exchangeSecurityData(int securityType) throws IOException {
        SecurityType type = SecurityType.fromCode(securityType);

        if (type == SecurityType.NONE) {
            // RFC 6143 §7.2.1: None — no data exchanged, no SecurityResult
            return;
        }

        SecurityResult result;

        if (type == SecurityType.VNC_AUTHENTICATION) {
            // RFC 6143 §7.2.2: send challenge, read response, authenticate
            VncAuthChallenge challenge = server.createVncAuthChallenge();
            challenge.write(out);
            out.flush();

            VncAuthResponse response = VncAuthResponse.parse(in);
            result = server.authenticateVncAuth(challenge, response);
        } else {
            // Unknown security type: report failure
            result = SecurityResult.builder().status(SecurityResult.STATUS_FAILED).build();
        }

        result.write(out);

        if (!result.isOk()) {
            // RFC 6143 §7.1.3: send reason string then throw
            SecurityFailureMessage.builder()
                    .reason(server.getAuthenticationFailureReason())
                    .build()
                    .write(out);
            out.flush();
            throw new RfbAuthenticationException(server.getAuthenticationFailureReason());
        }

        out.flush();
    }

    /**
     * Phase 1d: read ClientInit, then send ServerInit.
     */
    private void exchangeInit() throws IOException {
        ClientInit clientInit = ClientInit.parse(in);
        server.onClientInit(clientInit);

        ServerInit serverInit = server.createServerInit();
        serverInit.write(out);
        out.flush();

        pixelFormat = serverInit.getPixelFormat();
    }

    // -------------------------------------------------------------------------
    // Phase 3: Receive loop
    // -------------------------------------------------------------------------

    /**
     * Reads client-to-server messages in a loop, dispatching each to the
     * appropriate {@link RfbServer} callback method.
     *
     * <p>This method blocks until the connection is closed or an error occurs.
     * When it returns (for any reason), {@link RfbServer#onDisconnected(Exception)}
     * will already have been called with {@code null} (clean close) or the causing
     * exception.</p>
     *
     * <p>This method is intended to run on a dedicated thread after
     * {@link #handshake()} has completed successfully.</p>
     *
     * @throws IOException if an I/O error occurs (the same exception that was
     *                     passed to {@link RfbServer#onDisconnected(Exception)})
     */
    public void receiveLoop() throws IOException {
        try {
            while (true) {
                int messageType = in.read();
                if (messageType == -1) {
                    server.onDisconnected(null);
                    return;
                }
                dispatchClientMessage(messageType);
            }
        } catch (IOException e) {
            server.onDisconnected(e);
            throw e;
        }
    }

    /**
     * Routes one client-to-server message to the appropriate callback.
     *
     * @param messageType the message-type byte already read from the stream
     */
    private void dispatchClientMessage(int messageType) throws IOException {
        switch (messageType) {
            case SetPixelFormat.MESSAGE_TYPE:
                SetPixelFormat spf = SetPixelFormat.parse(in);
                pixelFormat = spf.getPixelFormat();
                server.onSetPixelFormat(spf);
                break;
            case SetEncodings.MESSAGE_TYPE:
                server.onSetEncodings(SetEncodings.parse(in));
                break;
            case FramebufferUpdateRequest.MESSAGE_TYPE:
                server.onFramebufferUpdateRequest(FramebufferUpdateRequest.parse(in));
                break;
            case KeyEvent.MESSAGE_TYPE:
                server.onKeyEvent(KeyEvent.parse(in));
                break;
            case PointerEvent.MESSAGE_TYPE:
                server.onPointerEvent(PointerEvent.parse(in));
                break;
            case ClientCutText.MESSAGE_TYPE:
                server.onClientCutText(ClientCutText.parse(in));
                break;
            default:
                throw new IOException(
                        "Unknown client-to-server message type: " + messageType);
        }
    }

    // -------------------------------------------------------------------------
    // Phase 2: Server-to-client messages
    // -------------------------------------------------------------------------

    /**
     * Sends a {@link FramebufferUpdate} carrying one or more encoded rectangles
     * of pixel data in response to a client's update request (RFC 6143 §7.6.1).
     *
     * @param update the framebuffer update to send
     * @throws IOException if the write fails
     */
    public void sendFramebufferUpdate(FramebufferUpdate update) throws IOException {
        synchronized (out) {
            update.write(out);
            out.flush();
        }
    }

    /**
     * Sends a {@link SetColorMapEntries} message defining colour-map entries for
     * non-true-colour pixel formats (RFC 6143 §7.6.2).
     *
     * @param entries the colour-map entries to send
     * @throws IOException if the write fails
     */
    public void sendSetColorMapEntries(SetColorMapEntries entries) throws IOException {
        synchronized (out) {
            entries.write(out);
            out.flush();
        }
    }

    /**
     * Sends a {@link Bell} message requesting an audible signal on the client
     * (RFC 6143 §7.6.3).
     *
     * @throws IOException if the write fails
     */
    public void sendBell() throws IOException {
        synchronized (out) {
            Bell.builder().build().write(out);
            out.flush();
        }
    }

    /**
     * Sends a {@link ServerCutText} message placing text on the client's clipboard
     * (RFC 6143 §7.6.4).
     *
     * @param text the cut text message to send
     * @throws IOException if the write fails
     */
    public void sendServerCutText(ServerCutText text) throws IOException {
        synchronized (out) {
            text.write(out);
            out.flush();
        }
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /**
     * Returns the pixel format currently requested by the connected client.
     *
     * <p>Initialised from {@link ServerInit#getPixelFormat()} after the handshake
     * and updated whenever the client sends a {@link SetPixelFormat} message.
     * Returns {@code null} before {@link #handshake()} completes.</p>
     */
    public PixelFormat getPixelFormat() {
        return pixelFormat;
    }
}
