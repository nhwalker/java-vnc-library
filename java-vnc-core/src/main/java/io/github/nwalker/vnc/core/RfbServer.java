package io.github.nwalker.vnc.core;

import io.github.nwalker.vnc.core.client.ClientCutText;
import io.github.nwalker.vnc.core.client.FramebufferUpdateRequest;
import io.github.nwalker.vnc.core.client.KeyEvent;
import io.github.nwalker.vnc.core.client.PointerEvent;
import io.github.nwalker.vnc.core.client.SetEncodings;
import io.github.nwalker.vnc.core.client.SetPixelFormat;
import io.github.nwalker.vnc.core.handshake.ProtocolVersionMessage;
import io.github.nwalker.vnc.core.handshake.SecurityResult;
import io.github.nwalker.vnc.core.handshake.ServerSecurityHandshake;
import io.github.nwalker.vnc.core.handshake.VncAuthChallenge;
import io.github.nwalker.vnc.core.handshake.VncAuthResponse;
import io.github.nwalker.vnc.core.init.ClientInit;
import io.github.nwalker.vnc.core.init.ServerInit;

/**
 * Application-level callbacks for the RFB server protocol (RFC 6143).
 *
 * <p>{@link VncServerProtocol} drives the connection and delegates all
 * client-initiated events and handshake decisions to this interface.
 * Implementations describe <em>what</em> a VNC server should do; the
 * protocol class handles <em>how</em> the bytes are exchanged.</p>
 *
 * <h2>Connection lifecycle</h2>
 * <ol>
 *   <li>Server sends ProtocolVersion (from {@link #getServerProtocolVersion()}).</li>
 *   <li>Client sends ProtocolVersion → {@link #onClientProtocolVersion} is called.</li>
 *   <li>Server sends SecurityHandshake (from {@link #createSecurityHandshake()}).
 *       Return a handshake with zero security types to refuse the connection.</li>
 *   <li>Client sends SecurityTypeSelection.</li>
 *   <li>If VNC Authentication was selected:
 *     <ul>
 *       <li>Server sends challenge (from {@link #createVncAuthChallenge()}).</li>
 *       <li>Client sends response → {@link #authenticateVncAuth} decides the result.</li>
 *     </ul>
 *   </li>
 *   <li>Server sends SecurityResult (unless security type is None).</li>
 *   <li>Client sends ClientInit → {@link #onClientInit} is called.</li>
 *   <li>Server sends ServerInit (from {@link #createServerInit()});
 *       handshake is complete.</li>
 *   <li>Client-to-server messages arrive → the corresponding {@code on*} methods
 *       are called.</li>
 *   <li>The connection ends → {@link #onDisconnected} is called.</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 * <p>All methods on this interface are called from the single thread running
 * {@link VncServerProtocol#receiveLoop()}, except for the handshake methods which
 * are called from the thread that invokes {@link VncServerProtocol#handshake()}.
 * Implementations must not block these calls for extended periods.</p>
 */
public interface RfbServer {

    // -------------------------------------------------------------------------
    // Handshake decisions — called once during connection setup
    // -------------------------------------------------------------------------

    /**
     * Returns the {@link ProtocolVersionMessage} the server sends to the client
     * as the first message of the connection (RFC 6143 §7.1.1).
     *
     * <p>This should reflect the highest RFB version the server supports. The
     * client may negotiate down to an older version.</p>
     *
     * @return the server's protocol version advertisement
     */
    ProtocolVersionMessage getServerProtocolVersion();

    /**
     * Called when the client responds with its chosen protocol version.
     *
     * <p>The implementation may record this value for diagnostic purposes or
     * throw {@link java.io.IOException} to abort the connection if the client's
     * version is unacceptable.</p>
     *
     * @param clientVersion the protocol version the client selected
     * @throws java.io.IOException if the client's version is not acceptable
     */
    void onClientProtocolVersion(ProtocolVersionMessage clientVersion) throws java.io.IOException;

    /**
     * Returns the {@link ServerSecurityHandshake} listing the security types
     * this server supports (RFC 6143 §7.1.2).
     *
     * <p>Return a handshake with zero security types to refuse the connection;
     * {@link VncServerProtocol} will then write a {@code SecurityFailure} reason
     * and close the exchange.</p>
     *
     * @return the server's security type advertisement
     */
    ServerSecurityHandshake createSecurityHandshake();

    /**
     * Returns the reason string to send to the client when the connection is
     * refused via a zero-type {@link ServerSecurityHandshake}.
     *
     * <p>The default implementation returns an empty string. Override to supply
     * a human-readable message.</p>
     *
     * @return the refusal reason, or an empty string
     */
    default String getConnectionRefusalReason() {
        return "";
    }

    /**
     * Called when VNC Authentication (security type 2) is selected and the server
     * needs to supply a 16-byte DES challenge to send to the client (RFC 6143 §7.2.2).
     *
     * <p>The default implementation throws {@link UnsupportedOperationException};
     * override this method if your server supports VNC Authentication.</p>
     *
     * @return the 16-byte challenge to send to the client
     */
    default VncAuthChallenge createVncAuthChallenge() {
        throw new UnsupportedOperationException(
                "VNC Authentication not supported by this server");
    }

    /**
     * Called to validate the client's VNC Authentication response against the
     * challenge the server sent (RFC 6143 §7.2.2).
     *
     * <p>The implementation should verify the client's DES-encrypted response and
     * return {@link SecurityResult#STATUS_OK} if authentication succeeds or
     * {@link SecurityResult#STATUS_FAILED} otherwise. If the result is failure,
     * {@link VncServerProtocol} will send the result and throw
     * {@link RfbAuthenticationException}.</p>
     *
     * <p>The default implementation throws {@link UnsupportedOperationException};
     * override together with {@link #createVncAuthChallenge()} if supporting VNC
     * Authentication.</p>
     *
     * @param challenge the challenge originally sent to the client
     * @param response  the response received from the client
     * @return a {@link SecurityResult} with {@link SecurityResult#STATUS_OK} or
     *         {@link SecurityResult#STATUS_FAILED}
     */
    default SecurityResult authenticateVncAuth(VncAuthChallenge challenge,
                                               VncAuthResponse response) {
        throw new UnsupportedOperationException(
                "VNC Authentication not supported by this server");
    }

    /**
     * Returns the reason string to include when authentication fails.
     *
     * <p>This is sent to the client as part of the {@code SecurityFailure} message
     * after a failed authentication (RFC 6143 §7.1.3). The default implementation
     * returns {@code "Authentication failed"}.</p>
     *
     * @return the authentication failure reason
     */
    default String getAuthenticationFailureReason() {
        return "Authentication failed";
    }

    /**
     * Called when the client sends its {@link ClientInit} message, which carries
     * the shared-session flag (RFC 6143 §7.3.1).
     *
     * @param clientInit the client's initialisation message
     */
    void onClientInit(ClientInit clientInit);

    /**
     * Returns the {@link ServerInit} message to send to the client, which
     * describes the remote framebuffer dimensions, pixel format, and desktop name
     * (RFC 6143 §7.3.2). This concludes the handshake.
     *
     * @return the server's initialisation message
     */
    ServerInit createServerInit();

    // -------------------------------------------------------------------------
    // Client-to-server messages — called from the receive loop
    // -------------------------------------------------------------------------

    /**
     * Called when the client sends a {@link SetPixelFormat} message requesting
     * that the server use a different pixel format for subsequent updates
     * (RFC 6143 §7.5.1).
     *
     * @param msg the SetPixelFormat message received from the client
     */
    void onSetPixelFormat(SetPixelFormat msg);

    /**
     * Called when the client sends a {@link SetEncodings} message listing the
     * encoding types it supports, in order of preference (RFC 6143 §7.5.2).
     *
     * @param msg the SetEncodings message received from the client
     */
    void onSetEncodings(SetEncodings msg);

    /**
     * Called when the client sends a {@link FramebufferUpdateRequest} asking for
     * pixel data for a region of the framebuffer (RFC 6143 §7.5.3).
     *
     * <p>The server should respond with a {@link io.github.nwalker.vnc.core.server.FramebufferUpdate}
     * via {@link VncServerProtocol#sendFramebufferUpdate}.</p>
     *
     * @param request the update request received from the client
     */
    void onFramebufferUpdateRequest(FramebufferUpdateRequest request);

    /**
     * Called when the client sends a {@link KeyEvent} representing a key press
     * or release (RFC 6143 §7.5.4).
     *
     * <p>The default implementation is a no-op; override to forward input events
     * to the hosted application.</p>
     *
     * @param event the key event received from the client
     */
    default void onKeyEvent(KeyEvent event) {}

    /**
     * Called when the client sends a {@link PointerEvent} representing mouse
     * movement or a button state change (RFC 6143 §7.5.5).
     *
     * <p>The default implementation is a no-op; override to forward input events
     * to the hosted application.</p>
     *
     * @param event the pointer event received from the client
     */
    default void onPointerEvent(PointerEvent event) {}

    /**
     * Called when the client sends a {@link ClientCutText} message placing text
     * on the server's clipboard (RFC 6143 §7.5.6).
     *
     * <p>The default implementation is a no-op; override to update the hosted
     * application's clipboard.</p>
     *
     * @param text the cut text message received from the client
     */
    default void onClientCutText(ClientCutText text) {}

    // -------------------------------------------------------------------------
    // Connection lifecycle
    // -------------------------------------------------------------------------

    /**
     * Called when the connection ends, either cleanly or due to an error.
     *
     * <p>A clean disconnect (remote end closed the stream) is signalled with a
     * {@code null} cause. An error condition passes the {@link Exception} that
     * caused the failure.</p>
     *
     * @param cause the exception that terminated the connection, or {@code null}
     *              for a clean disconnect
     */
    void onDisconnected(Exception cause);
}
