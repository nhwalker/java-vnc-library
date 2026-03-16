package io.github.nwalker.vnc.server.simple;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Tests for {@link SimpleVncServer#insertRows(int[], int)}. */
class SimpleVncServerInsertRowsTest {

    private static final int W = 4;
    private static final int H = 3;

    /** Creates a WxH image filled with a single ARGB colour. */
    private static BufferedImage solidImage(int argb) {
        BufferedImage img = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        int[] pixels = new int[W * H];
        Arrays.fill(pixels, argb);
        img.setRGB(0, 0, W, H, pixels, 0, W);
        return img;
    }

    @Test
    void insertOneRow_shiftsExistingDown_dropsBottomRow() {
        // Fill with 0xFF0000 (red)
        BufferedImage initial = solidImage(0xFF0000);
        SimpleVncServer server = new SimpleVncServer(5999, initial);

        // Insert one row of blue pixels
        int[] blueRow = new int[W];
        Arrays.fill(blueRow, 0x0000FF);
        server.insertRows(blueRow, 1);

        BufferedImage result = server.getImage();
        assertEquals(W, result.getWidth());
        assertEquals(H, result.getHeight());

        // Row 0 should be blue
        for (int x = 0; x < W; x++) {
            assertEquals(0x0000FF, result.getRGB(x, 0) & 0xFFFFFF,
                    "pixel (" + x + ",0) should be blue");
        }
        // Rows 1 and 2 should be red (shifted from original rows 0 and 1)
        for (int y = 1; y < H; y++) {
            for (int x = 0; x < W; x++) {
                assertEquals(0xFF0000, result.getRGB(x, y) & 0xFFFFFF,
                        "pixel (" + x + "," + y + ") should be red");
            }
        }
    }

    @Test
    void insertAllRows_replacesEntireFramebuffer() {
        BufferedImage initial = solidImage(0xFF0000);
        SimpleVncServer server = new SimpleVncServer(5999, initial);

        int[] greenPixels = new int[W * H];
        Arrays.fill(greenPixels, 0x00FF00);
        server.insertRows(greenPixels, H);

        BufferedImage result = server.getImage();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                assertEquals(0x00FF00, result.getRGB(x, y) & 0xFFFFFF,
                        "pixel (" + x + "," + y + ") should be green");
            }
        }
    }

    @Test
    void insertMultipleRows_middleCase() {
        BufferedImage initial = solidImage(0xAAAAAA);
        SimpleVncServer server = new SimpleVncServer(5999, initial);

        // Insert 2 rows of white
        int[] whiteRows = new int[W * 2];
        Arrays.fill(whiteRows, 0xFFFFFF);
        server.insertRows(whiteRows, 2);

        BufferedImage result = server.getImage();
        // Rows 0-1 should be white
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < W; x++) {
                assertEquals(0xFFFFFF, result.getRGB(x, y) & 0xFFFFFF);
            }
        }
        // Row 2 should be the old row 0 (grey)
        for (int x = 0; x < W; x++) {
            assertEquals(0xAAAAAA, result.getRGB(x, 2) & 0xFFFFFF);
        }
    }

    @Test
    void rejectsNullPixels() {
        SimpleVncServer server = new SimpleVncServer(5999, solidImage(0));
        assertThrows(IllegalArgumentException.class,
                () -> server.insertRows(null, 1));
    }

    @Test
    void rejectsZeroRowCount() {
        SimpleVncServer server = new SimpleVncServer(5999, solidImage(0));
        assertThrows(IllegalArgumentException.class,
                () -> server.insertRows(new int[W], 0));
    }

    @Test
    void rejectsRowCountExceedingHeight() {
        SimpleVncServer server = new SimpleVncServer(5999, solidImage(0));
        assertThrows(IllegalArgumentException.class,
                () -> server.insertRows(new int[W * (H + 1)], H + 1));
    }

    @Test
    void rejectsWrongPixelArrayLength() {
        SimpleVncServer server = new SimpleVncServer(5999, solidImage(0));
        assertThrows(IllegalArgumentException.class,
                () -> server.insertRows(new int[W + 1], 1));
    }

    @Test
    void dimensionsUnchangedAfterInsert() {
        SimpleVncServer server = new SimpleVncServer(5999, solidImage(0));
        server.insertRows(new int[W], 1);

        assertEquals(W, server.getWidth());
        assertEquals(H, server.getHeight());
    }
}
