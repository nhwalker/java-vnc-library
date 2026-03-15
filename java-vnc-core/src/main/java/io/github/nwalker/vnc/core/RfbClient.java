package io.github.nwalker.vnc.core;

import io.github.nwalker.vnc.core.handshake.ProtocolVersionMessage;
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

/**
 * Application-level callbacks for the RFB client protocol (RFC 6143).
 *
 * <p>{@link VncClientProtocol} drives the connection and delegates all
 * server-initiated events and handshake decisions to this interface.
 * Implementations describe <em>what</em> a VNC client should do; the
 * protocol class handles <em>how</em> the bytes are exchanged.</p>
 *
 * <h2>Connection lifecycle</h2>
 * <ol>
 *   <li>Server sends ProtocolVersion → {@link #selectProtocolVersion} is called.</li>
 *   <li>Server sends SecurityHandshake → {@link #selectSecurityType} is called.</li>
 *   <li>If VNC Authentication was selected → {@link #provideVncAuthResponse} is called.</li>
 *   <li>Client sends ClientInit (via {@link #createClientInit}).</li>
 *   <li>Server sends ServerInit → {@link #onServerInit} is called; handshake is complete.</li>
 *   <li>Server-to-client messages arrive → the corresponding {@code on*} methods are called.</li>
 *   <li>The connection ends (cleanly or with an error) → {@link #onDisconnected} is called.</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 * <p>All methods on this interface are called from the single thread running
 * {@link VncClientProtocol#receiveLoop()}, except for the handshake methods which
 * are called from the thread that invokes {@link VncClientProtocol#handshake()}.
 * Implementations must not block these calls for extended periods.</p>
 */
public interface RfbClient {

    // -------------------------------------------------------------------------
    // Handshake decisions — called once during connection setup
    // -------------------------------------------------------------------------

    /**
     * Called when the server announces its supported protocol version.
     *
     * <p>The implementation should return the version it wishes to use, which
     * must be less than or equal to {@code serverVersion}. Most implementations
     * should return {@code serverVersion} to use the highest mutually supported
     * version, or a fixed minimum version they require.</p>
     *
     * @param serverVersion the protocol version offered by the server
     * @return the protocol version the client wants to use
     */
    ProtocolVersionMessage selectProtocolVersion(ProtocolVersionMessage serverVersion);

    /**
     * Called when the server announces the security types it supports.
     *
     * <p>The implementation must return a selection whose
     * {@code securityType} code is among the types in {@code handshake}.</p>
     *
     * @param handshake the server's security handshake listing available types
     * @return the security type the client selects
     */
    SecurityTypeSelection selectSecurityType(ServerSecurityHandshake handshake);

    /**
     * Called when VNC Authentication (security type 2) has been selected and
     * the server sends its 16-byte DES challenge.
     *
     * <p>The implementation must encrypt the challenge with the user's password
     * using DES (RFC 6143 §7.2.2) and return the 16-byte response.
     * The default implementation throws {@link UnsupportedOperationException};
     * override this method if your client supports VNC Authentication.</p>
     *
     * @param challenge the 16-byte DES challenge from the server
     * @return the 16-byte encrypted response to send back
     */
    default VncAuthResponse provideVncAuthResponse(VncAuthChallenge challenge) {
        throw new UnsupportedOperationException(
                "VNC Authentication not supported by this client");
    }

    /**
     * Called after security negotiation succeeds and before ServerInit is received.
     *
     * <p>The returned {@link ClientInit} conveys whether the client wants an
     * exclusive ({@code shared=false}) or shared ({@code shared=true}) session.</p>
     *
     * @return the client's initialisation message
     */
    ClientInit createClientInit();

    /**
     * Called when the server sends its {@link ServerInit} message, which concludes
     * the handshake and describes the remote framebuffer.
     *
     * <p>After this method returns, {@link VncClientProtocol#receiveLoop()} may be
     * started and client-to-server messages (SetEncodings, FramebufferUpdateRequest,
     * etc.) may be sent.</p>
     *
     * @param serverInit the server's initialisation message
     */
    void onServerInit(ServerInit serverInit);

    // -------------------------------------------------------------------------
    // Server-to-client messages — called from the receive loop
    // -------------------------------------------------------------------------

    /**
     * Called when the server sends a {@link FramebufferUpdate} containing one or
     * more encoded rectangles of pixel data (RFC 6143 §7.6.1).
     *
     * @param update the framebuffer update message
     */
    void onFramebufferUpdate(FramebufferUpdate update);

    /**
     * Called when the server sends a {@link SetColorMapEntries} message defining
     * colour-map entries for non-true-colour pixel formats (RFC 6143 §7.6.2).
     *
     * <p>The default implementation is a no-op; override if your client uses
     * indexed colour.</p>
     *
     * @param entries the colour-map entries sent by the server
     */
    default void onSetColorMapEntries(SetColorMapEntries entries) {}

    /**
     * Called when the server sends a {@link Bell} message requesting an audible
     * signal (RFC 6143 §7.6.3).
     *
     * <p>The default implementation is a no-op; override to produce a system bell.</p>
     *
     * @param bell the bell message
     */
    default void onBell(Bell bell) {}

    /**
     * Called when the server sends a {@link ServerCutText} message containing
     * text to place on the client clipboard (RFC 6143 §7.6.4).
     *
     * @param cutText the server cut text message
     */
    void onServerCutText(ServerCutText cutText);

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
