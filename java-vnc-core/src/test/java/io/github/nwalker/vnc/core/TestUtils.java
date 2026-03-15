package io.github.nwalker.vnc.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Shared test helpers for constructing byte sequences and streams. */
public final class TestUtils {

    private TestUtils() {}

    /** Builds a byte array from int values (each clamped to 0-255). */
    public static byte[] bytes(int... vals) {
        byte[] b = new byte[vals.length];
        for (int i = 0; i < vals.length; i++) {
            b[i] = (byte) (vals[i] & 0xFF);
        }
        return b;
    }

    /** Wraps a byte array in an InputStream. */
    public static InputStream stream(byte... data) {
        return new ByteArrayInputStream(data);
    }

    /** Wraps an int-array of byte values in an InputStream. */
    public static InputStream stream(int... vals) {
        return stream(bytes(vals));
    }

    /** Returns a standard 32bpp BGRA pixel format commonly used in tests. */
    public static PixelFormat rgb888PixelFormat() {
        return PixelFormat.builder()
                .bitsPerPixel(32).depth(24).bigEndian(false).trueColor(true)
                .redMax(255).greenMax(255).blueMax(255)
                .redShift(16).greenShift(8).blueShift(0)
                .build();
    }

    /** Returns a standard 32bpp format where all shifts are >= 8 (high bytes). */
    public static PixelFormat rgb888HighBytesPixelFormat() {
        return PixelFormat.builder()
                .bitsPerPixel(32).depth(24).bigEndian(false).trueColor(true)
                .redMax(255).greenMax(255).blueMax(255)
                .redShift(24).greenShift(16).blueShift(8)
                .build();
    }

    /**
     * Captures the bytes written to an output stream.
     *
     * @param writer lambda that writes to an OutputStream
     */
    public static byte[] capture(IOConsumer writer) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writer.accept(out);
        return out.toByteArray();
    }

    /** Builds the canonical 16-byte wire form of the standard RGB888 pixel format. */
    public static byte[] rgb888PixelFormatBytes() {
        // bpp=32, depth=24, bigEndian=0, trueColor=1,
        // redMax=255 (0x00FF), greenMax=255, blueMax=255,
        // redShift=16, greenShift=8, blueShift=0, padding(3)
        return bytes(
                0x20, 0x18, 0x00, 0x01,
                0x00, 0xFF, 0x00, 0xFF, 0x00, 0xFF,
                0x10, 0x08, 0x00,
                0x00, 0x00, 0x00
        );
    }

    @FunctionalInterface
    public interface IOConsumer {
        void accept(java.io.OutputStream out) throws IOException;
    }
}
