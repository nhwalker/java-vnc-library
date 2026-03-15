package io.github.nwalker.vnc.core;

/**
 * Security types defined by RFC 6143 (Section 7.1.2 and Section 8.1.2).
 */
public enum SecurityType {

    INVALID(0),
    NONE(1),
    VNC_AUTHENTICATION(2);

    private final int code;

    SecurityType(int code) { this.code = code; }

    /** The numeric code transmitted on the wire. */
    public int getCode() { return code; }

    /** Returns the SecurityType for the given wire code, or {@code null} if unknown. */
    public static SecurityType fromCode(int code) {
        for (SecurityType t : values()) {
            if (t.code == code) return t;
        }
        return null;
    }
}
