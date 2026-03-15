package io.github.nwalker.vnc.core.client;

import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * KeyEvent client-to-server message (Section 7.5.4 of RFC 6143).
 *
 * <p>Indicates a key press or release. The key is identified by an X Window System
 * keysym value. {@code down} is {@code true} if the key is now pressed.</p>
 *
 * <p>Wire format: message-type (U8=4) + U8 down-flag + 2 padding bytes + U32 key.</p>
 */
public final class KeyEvent {

    /** Message type byte on the wire. */
    public static final int MESSAGE_TYPE = 4;

    // Common keysym constants for convenience
    public static final long KEY_BACKSPACE = 0xFF08L;
    public static final long KEY_TAB       = 0xFF09L;
    public static final long KEY_RETURN    = 0xFF0DL;
    public static final long KEY_ESCAPE    = 0xFF1BL;
    public static final long KEY_INSERT    = 0xFF63L;
    public static final long KEY_DELETE    = 0xFFFFL;
    public static final long KEY_HOME      = 0xFF50L;
    public static final long KEY_END       = 0xFF57L;
    public static final long KEY_PAGE_UP   = 0xFF55L;
    public static final long KEY_PAGE_DOWN = 0xFF56L;
    public static final long KEY_LEFT      = 0xFF51L;
    public static final long KEY_UP        = 0xFF52L;
    public static final long KEY_RIGHT     = 0xFF53L;
    public static final long KEY_DOWN      = 0xFF54L;
    public static final long KEY_F1        = 0xFFBEL;
    public static final long KEY_F12       = 0xFFC9L;
    public static final long KEY_SHIFT_L   = 0xFFE1L;
    public static final long KEY_SHIFT_R   = 0xFFE2L;
    public static final long KEY_CTRL_L    = 0xFFE3L;
    public static final long KEY_CTRL_R    = 0xFFE4L;
    public static final long KEY_META_L    = 0xFFE7L;
    public static final long KEY_META_R    = 0xFFE8L;
    public static final long KEY_ALT_L     = 0xFFE9L;
    public static final long KEY_ALT_R     = 0xFFEAL;

    private final boolean down; // U8 (non-zero = pressed)
    private final long key;     // U32 keysym value

    private KeyEvent(Builder b) {
        this.down = b.down;
        this.key  = b.key;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public boolean isDown() { return down; }
    public long getKey()    { return key; }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /**
     * Reads a KeyEvent from {@code in}.
     * The message-type byte must already have been consumed by the caller.
     */
    public static KeyEvent parse(InputStream in) throws IOException {
        boolean down = RfbIO.readU8(in) != 0;
        RfbIO.skipBytes(in, 2); // padding
        long key = RfbIO.readU32(in);
        return new Builder().down(down).key(key).build();
    }

    /** Writes the complete message (including type byte) to {@code out}. */
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU8(out, MESSAGE_TYPE);
        RfbIO.writeU8(out, down ? 1 : 0);
        RfbIO.writePadding(out, 2);
        RfbIO.writeU32(out, key);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof KeyEvent)) return false;
        KeyEvent e = (KeyEvent) o;
        return down == e.down && key == e.key;
    }

    @Override
    public int hashCode() { return Objects.hash(down, key); }

    @Override
    public String toString() {
        return "KeyEvent{down=" + down + ", key=0x" + Long.toHexString(key) + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean down;
        private long key;

        public Builder down(boolean v) { down = v; return this; }
        public Builder key(long v)     { key  = v; return this; }

        public KeyEvent build() { return new KeyEvent(this); }
    }
}
