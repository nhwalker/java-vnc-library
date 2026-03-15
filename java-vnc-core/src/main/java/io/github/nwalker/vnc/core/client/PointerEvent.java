package io.github.nwalker.vnc.core.client;

import io.github.nwalker.vnc.core.RfbIO;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;

/**
 * PointerEvent client-to-server message (Section 7.5.5 of RFC 6143).
 *
 * <p>Indicates pointer movement or a button press/release. The {@code buttonMask}
 * byte has bits 0–7 corresponding to buttons 1–8 (1=pressed, 0=released). Buttons
 * 1, 2, and 3 are left, middle, and right on a conventional mouse; buttons 4 and 5
 * are wheel up and wheel down.</p>
 *
 * <p>Wire format: message-type (U8=5) + U8 button-mask + U16 x + U16 y.</p>
 */
public final class PointerEvent {

    /** Message type byte on the wire. */
    public static final int MESSAGE_TYPE = 5;

    /** Bitmask constant for the left mouse button (button 1). */
    public static final int BUTTON_LEFT   = 0x01;
    /** Bitmask constant for the middle mouse button (button 2). */
    public static final int BUTTON_MIDDLE = 0x02;
    /** Bitmask constant for the right mouse button (button 3). */
    public static final int BUTTON_RIGHT  = 0x04;
    /** Bitmask constant for scroll wheel up (button 4). */
    public static final int BUTTON_WHEEL_UP   = 0x08;
    /** Bitmask constant for scroll wheel down (button 5). */
    public static final int BUTTON_WHEEL_DOWN = 0x10;

    private final int buttonMask; // U8
    private final int x;          // U16
    private final int y;          // U16

    private PointerEvent(Builder b) {
        this.buttonMask = b.buttonMask;
        this.x          = b.x;
        this.y          = b.y;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    public int getButtonMask() { return buttonMask; }
    public int getX()          { return x; }
    public int getY()          { return y; }

    // -------------------------------------------------------------------------
    // Parse / write
    // -------------------------------------------------------------------------

    /**
     * Reads a PointerEvent from {@code in}.
     * The message-type byte must already have been consumed by the caller.
     */
    public static PointerEvent parse(InputStream in) throws IOException {
        int mask = RfbIO.readU8(in);
        int x    = RfbIO.readU16(in);
        int y    = RfbIO.readU16(in);
        return new Builder().buttonMask(mask).x(x).y(y).build();
    }

    /** Writes the complete message (including type byte) to {@code out}. */
    public void write(OutputStream out) throws IOException {
        RfbIO.writeU8(out, MESSAGE_TYPE);
        RfbIO.writeU8(out, buttonMask);
        RfbIO.writeU16(out, x);
        RfbIO.writeU16(out, y);
    }

    // -------------------------------------------------------------------------
    // Object methods
    // -------------------------------------------------------------------------

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PointerEvent)) return false;
        PointerEvent p = (PointerEvent) o;
        return buttonMask == p.buttonMask && x == p.x && y == p.y;
    }

    @Override
    public int hashCode() { return Objects.hash(buttonMask, x, y); }

    @Override
    public String toString() {
        return "PointerEvent{buttonMask=0x" + Integer.toHexString(buttonMask)
                + ", x=" + x + ", y=" + y + '}';
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int buttonMask;
        private int x;
        private int y;

        public Builder buttonMask(int v) { buttonMask = v; return this; }
        public Builder x(int v)          { x = v;          return this; }
        public Builder y(int v)          { y = v;          return this; }

        public PointerEvent build() { return new PointerEvent(this); }
    }
}
