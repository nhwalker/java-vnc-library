package io.github.nwalker.vnc.core;

import java.io.IOException;

/**
 * Thrown when a VNC server rejects the client's authentication attempt.
 *
 * <p>The {@link #getReason()} method returns the human-readable reason string
 * sent by the server in the {@code SecurityFailure} message (RFC 6143 §7.1.3),
 * or an empty string if the server did not supply one.</p>
 */
public final class RfbAuthenticationException extends IOException {

    private final String reason;

    /**
     * Constructs the exception with a server-supplied reason string.
     *
     * @param reason the reason string from the server SecurityFailure message
     */
    public RfbAuthenticationException(String reason) {
        super("VNC authentication failed: " + reason);
        this.reason = reason;
    }

    /**
     * Returns the reason string supplied by the server, or an empty string
     * if none was provided (e.g. protocol version &lt; 3.8).
     */
    public String getReason() {
        return reason;
    }
}
