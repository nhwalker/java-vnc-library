package io.github.nwalker.vnc.core;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Low-level I/O helpers for reading and writing RFB primitive types. */
public final class RfbIO {

    private RfbIO() {}

    // -------------------------------------------------------------------------
    // Reading
    // -------------------------------------------------------------------------

    public static int readU8(InputStream in) throws IOException {
        int b = in.read();
        if (b < 0) throw new EOFException("Unexpected end of stream reading U8");
        return b;
    }

    public static int readU16(InputStream in) throws IOException {
        int hi = readU8(in);
        int lo = readU8(in);
        return (hi << 8) | lo;
    }

    public static long readU32(InputStream in) throws IOException {
        long b3 = readU8(in);
        long b2 = readU8(in);
        long b1 = readU8(in);
        long b0 = readU8(in);
        return (b3 << 24) | (b2 << 16) | (b1 << 8) | b0;
    }

    public static int readS32(InputStream in) throws IOException {
        return (int) readU32(in);
    }

    public static byte[] readBytes(InputStream in, int length) throws IOException {
        byte[] buf = new byte[length];
        int offset = 0;
        while (offset < length) {
            int n = in.read(buf, offset, length - offset);
            if (n < 0) throw new EOFException("Unexpected end of stream: need " + length + " bytes, got " + offset);
            offset += n;
        }
        return buf;
    }

    public static void skipBytes(InputStream in, int count) throws IOException {
        readBytes(in, count); // read and discard
    }

    // -------------------------------------------------------------------------
    // Writing
    // -------------------------------------------------------------------------

    public static void writeU8(OutputStream out, int value) throws IOException {
        out.write(value & 0xFF);
    }

    public static void writeU16(OutputStream out, int value) throws IOException {
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    public static void writeU32(OutputStream out, long value) throws IOException {
        out.write((int) ((value >>> 24) & 0xFF));
        out.write((int) ((value >>> 16) & 0xFF));
        out.write((int) ((value >>> 8) & 0xFF));
        out.write((int) (value & 0xFF));
    }

    public static void writeS32(OutputStream out, int value) throws IOException {
        writeU32(out, value & 0xFFFFFFFFL);
    }

    public static void writePadding(OutputStream out, int bytes) throws IOException {
        for (int i = 0; i < bytes; i++) {
            out.write(0);
        }
    }
}
