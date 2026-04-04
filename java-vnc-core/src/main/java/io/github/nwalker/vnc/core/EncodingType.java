package io.github.nwalker.vnc.core;

/**
 * Encoding types defined by RFC 6143 (Sections 7.7 and 7.8, and Table in 8.4.2).
 */
public enum EncodingType {

    RAW(0),
    COPY_RECT(1),
    RRE(2),
    HEXTILE(5),
    TRLE(15),
    ZRLE(16),
    /** Cursor pseudo-encoding (Section 7.8.1). */
    CURSOR(-239),
    /** DesktopSize pseudo-encoding (Section 7.8.2). */
    DESKTOP_SIZE(-223),
    /** TightPNG encoding — PNG-compressed rectangles (encoding-type = -260). */
    TIGHT_PNG(-260);

    private final int code;

    EncodingType(int code) { this.code = code; }

    /** The S32 wire value for this encoding. */
    public int getCode() { return code; }

    /** Returns the EncodingType for the given S32 wire code, or {@code null} if unknown. */
    public static EncodingType fromCode(int code) {
        for (EncodingType t : values()) {
            if (t.code == code) return t;
        }
        return null;
    }
}
