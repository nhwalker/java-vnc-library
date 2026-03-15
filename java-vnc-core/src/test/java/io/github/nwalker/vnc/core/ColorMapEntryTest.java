package io.github.nwalker.vnc.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;

import static io.github.nwalker.vnc.core.TestUtils.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ColorMapEntry} (RFC 6143 §7.6.2).
 *
 * <p>Wire format: U16 red + U16 green + U16 blue = 6 bytes, all big-endian.</p>
 */
class ColorMapEntryTest {

    @Test
    void parse_fieldOrder_redGreenBlue() throws IOException {
        // red=256 (0x01,0x00), green=512 (0x02,0x00), blue=1024 (0x04,0x00)
        ColorMapEntry entry = ColorMapEntry.parse(stream(0x01, 0x00, 0x02, 0x00, 0x04, 0x00));
        assertEquals(256,  entry.getRed());
        assertEquals(512,  entry.getGreen());
        assertEquals(1024, entry.getBlue());
    }

    @Test
    void parse_allZero() throws IOException {
        ColorMapEntry entry = ColorMapEntry.parse(stream(0x00, 0x00, 0x00, 0x00, 0x00, 0x00));
        assertEquals(0, entry.getRed());
        assertEquals(0, entry.getGreen());
        assertEquals(0, entry.getBlue());
    }

    @Test
    void parse_allMax_65535() throws IOException {
        // U16 max = 65535 = 0xFF, 0xFF
        ColorMapEntry entry = ColorMapEntry.parse(stream(0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF));
        assertEquals(65535, entry.getRed());
        assertEquals(65535, entry.getGreen());
        assertEquals(65535, entry.getBlue());
    }

    @Test
    void parse_consumesExactly6Bytes() throws IOException {
        InputStream in = stream(0x00, 0x01, 0x00, 0x02, 0x00, 0x03, 0xAB);
        ColorMapEntry.parse(in);
        assertEquals(0xAB, in.read()); // sentinel still readable
    }

    @Test
    void parse_channelsAreBigEndian() throws IOException {
        // 0x01, 0x00 = 256 (not 1), verifying big-endian
        ColorMapEntry entry = ColorMapEntry.parse(stream(0x01, 0x00, 0x00, 0x00, 0x00, 0x00));
        assertEquals(256, entry.getRed());
        assertEquals(0, entry.getGreen());
        assertEquals(0, entry.getBlue());
    }

    @Test
    void write_fieldOrder_redGreenBlue() throws IOException {
        ColorMapEntry entry = ColorMapEntry.builder().red(256).green(512).blue(1024).build();
        byte[] w = capture(entry::write);
        // red=256: 0x01, 0x00
        assertEquals(0x01, w[0] & 0xFF);
        assertEquals(0x00, w[1] & 0xFF);
        // green=512: 0x02, 0x00
        assertEquals(0x02, w[2] & 0xFF);
        assertEquals(0x00, w[3] & 0xFF);
        // blue=1024: 0x04, 0x00
        assertEquals(0x04, w[4] & 0xFF);
        assertEquals(0x00, w[5] & 0xFF);
    }

    @Test
    void write_is6Bytes() throws IOException {
        ColorMapEntry entry = ColorMapEntry.builder().red(0).green(0).blue(0).build();
        assertEquals(6, capture(entry::write).length);
    }

    @Test
    void write_maxValues_allFF() throws IOException {
        ColorMapEntry entry = ColorMapEntry.builder().red(65535).green(65535).blue(65535).build();
        byte[] w = capture(entry::write);
        for (byte b : w) assertEquals((byte) 0xFF, b);
    }

    @Test
    void roundtrip() throws IOException {
        ColorMapEntry original = ColorMapEntry.builder().red(1000).green(2000).blue(3000).build();
        byte[] wire = capture(original::write);
        assertEquals(original, ColorMapEntry.parse(stream(wire)));
    }

    @Test
    void equals_sameValues() {
        assertEquals(
            ColorMapEntry.builder().red(100).green(200).blue(300).build(),
            ColorMapEntry.builder().red(100).green(200).blue(300).build()
        );
    }

    @Test
    void equals_differentRed() {
        assertNotEquals(
            ColorMapEntry.builder().red(100).green(200).blue(300).build(),
            ColorMapEntry.builder().red(101).green(200).blue(300).build()
        );
    }

    @Test
    void hashCode_consistent() {
        ColorMapEntry a = ColorMapEntry.builder().red(100).green(200).blue(300).build();
        assertEquals(a.hashCode(), ColorMapEntry.builder().red(100).green(200).blue(300).build().hashCode());
    }

    @Test
    void toString_containsChannelValues() {
        String s = ColorMapEntry.builder().red(1).green(2).blue(3).build().toString();
        assertTrue(s.contains("1"));
        assertTrue(s.contains("2"));
        assertTrue(s.contains("3"));
    }
}
