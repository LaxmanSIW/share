package com.invoicestudio.service;

import com.invoicestudio.model.LabelConfig;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless tests for the TSPL/TSPL2 script builder used by the native
 * TSC print path (TA210): setup header, BITMAP framing, run-length PRINT
 * compression and 1-bit packing. Syntax expectations follow the official
 * TSPL/TSPL2 Programming Manual (TSC Auto ID, 2014).
 */
class TsplCommandBuilderTest {

    private static LabelConfig gapStock() {
        LabelConfig c = new LabelConfig();
        c.setStripWidth(54.0);
        c.setColumns(1);
        c.setLabelWidth(50.0);
        c.setLabelHeight(25.0);
        c.setGapY(3.0);
        c.setStockType("gap");
        return c;
    }

    // ------------------------------------------------------------------
    // Header
    // ------------------------------------------------------------------

    @Test
    void testHeaderUsesDotUnitsAndCrLf() {
        String h = TsplCommandBuilder.header(gapStock(), 432, 200, 24, 1);
        String[] lines = h.split("\r\n", -1);
        assertEquals("SIZE 432 dot,200 dot", lines[0]);
        assertEquals("GAP 24 dot,0 dot", lines[1]);
        assertEquals("DIRECTION 1", lines[2]);
        assertTrue(h.endsWith("\r\n"), "header must be CRLF terminated");
        assertFalse(h.contains("\n\n"), "no bare LF line endings");
    }

    @Test
    void testHeaderContinuousStockIsGapZeroZero() {
        LabelConfig c = gapStock();
        c.setStockType("continuous");
        String h = TsplCommandBuilder.header(c, 432, 200, 24, 1);
        assertTrue(h.contains("GAP 0,0"), "manual: continuous stock = GAP 0,0");
        assertFalse(h.contains("GAP 24"), "feed gap must not be declared for continuous");
    }

    @Test
    void testHeaderDirectionZeroFlip() {
        String h = TsplCommandBuilder.header(gapStock(), 432, 200, 24, 0);
        assertTrue(h.contains("DIRECTION 0"));
    }

    @Test
    void testHeaderClampsNonsenseDotCounts() {
        String h = TsplCommandBuilder.header(gapStock(), 0, -5, -1, 1);
        assertTrue(h.contains("SIZE 1 dot,1 dot"));
        assertTrue(h.contains("GAP 0 dot,0 dot"));
    }

    // ------------------------------------------------------------------
    // BITMAP framing
    // ------------------------------------------------------------------

    @Test
    void testBitmapCommandLineWidthInBytesHeightInDots() {
        assertEquals("BITMAP 0,0,54,240,0,", TsplCommandBuilder.bitmapCommand(54, 240));
        assertEquals("BITMAP 0,0,1,8,0,", TsplCommandBuilder.bitmapCommand(1, 8));
    }

    @Test
    void testWidthBytesRoundsUpToFullBytes() {
        assertEquals(54, TsplCommandBuilder.widthBytes(432));
        assertEquals(1, TsplCommandBuilder.widthBytes(8));
        assertEquals(2, TsplCommandBuilder.widthBytes(9));
        assertEquals(0, TsplCommandBuilder.widthBytes(0));
    }

    // ------------------------------------------------------------------
    // Run-length compression of identical consecutive pages
    // ------------------------------------------------------------------

    private static TsplCommandBuilder.TsplPage page(long seed, int wBytes, int hDots) {
        byte[] data = new byte[wBytes * hDots];
        for (int i = 0; i < data.length; i++) data[i] = (byte) (seed + i);
        return new TsplCommandBuilder.TsplPage(data, wBytes, hDots);
    }

    @Test
    void testRunLengthsGroupsIdenticalConsecutivePages() {
        List<TsplCommandBuilder.TsplPage> pages = List.of(
                page(1, 2, 2), page(1, 2, 2), page(1, 2, 2),
                page(9, 2, 2),
                page(1, 2, 2));
        assertArrayEquals(new int[]{3, 1, 1}, TsplCommandBuilder.runLengths(pages));
    }

    @Test
    void testRunLengthsEmptyAndSingle() {
        assertEquals(0, TsplCommandBuilder.runLengths(List.of()).length);
        assertArrayEquals(new int[]{1},
                TsplCommandBuilder.runLengths(List.of(page(5, 1, 1))));
    }

    // ------------------------------------------------------------------
    // Full script assembly
    // ------------------------------------------------------------------

    @Test
    void testBuildSinglePageScriptStructure() {
        TsplCommandBuilder.TsplPage p = page(7, 2, 16);
        byte[] script = TsplCommandBuilder.build(gapStock(), List.of(p), 432, 200, 24, 1);
        String ascii = new String(script, StandardCharsets.ISO_8859_1);

        assertTrue(ascii.startsWith("SIZE 432 dot,200 dot\r\nGAP 24 dot,0 dot\r\nDIRECTION 1\r\n"),
                "script opens with SIZE/GAP/DIRECTION: " + ascii);
        assertTrue(ascii.contains("CLS\r\nBITMAP 0,0,2,16,0,"), "CLS + BITMAP command present");
        assertTrue(ascii.endsWith("PRINT 1,1\r\n"), "exactly one label printed: " + ascii);

        // Binary payload sits between the BITMAP comma and the CRLF that
        // precedes PRINT — 2 bytes × 16 dots = 32 bytes.
        int payloadStart = ascii.indexOf("BITMAP 0,0,2,16,0,") + "BITMAP 0,0,2,16,0,".length();
        int printIdx = ascii.lastIndexOf("PRINT 1,1\r\n");
        assertEquals(32 + 2, printIdx - payloadStart,
                "payload is raw binary followed by CRLF");
        assertArrayEquals(p.mono(), java.util.Arrays.copyOfRange(script, payloadStart, payloadStart + 32));
    }

    @Test
    void testBuildCompressesIdenticalConsecutivePagesIntoOnePrint() {
        TsplCommandBuilder.TsplPage a = page(1, 2, 8);
        TsplCommandBuilder.TsplPage b = page(2, 2, 8);
        byte[] script = TsplCommandBuilder.build(gapStock(), List.of(a, a, b, a), 432, 200, 24, 1);
        String ascii = new String(script, StandardCharsets.ISO_8859_1);

        assertEquals(3, ascii.split("PRINT ", -1).length - 1, "three PRINT commands");
        assertTrue(ascii.contains("PRINT 2,1\r\n"), "identical run compressed to PRINT 2,1");
        assertEquals(3, ascii.split("CLS\r\n", -1).length - 1, "each RUN gets one CLS+BITMAP");
        assertEquals(3, ascii.split("BITMAP ", -1).length - 1);
    }

    @Test
    void testBuildContinuousStockScript() {
        LabelConfig c = gapStock();
        c.setStockType("continuous");
        byte[] script = TsplCommandBuilder.build(c, List.of(page(0, 1, 8)), 432, 200, 24, 1);
        assertTrue(new String(script, StandardCharsets.ISO_8859_1).contains("GAP 0,0\r\n"));
    }

    // ------------------------------------------------------------------
    // 1-bit packing (MSB first, rows padded to byte boundary)
    // ------------------------------------------------------------------

    @Test
    void testPackBitsMsbFirst() {
        // 9-dot-wide rows: dot 0 and dot 8 black (each lands on a byte MSB).
        boolean[] black = new boolean[18]; // 2 rows × 9 dots
        black[0] = true;      // row 1, dot 0
        black[8] = true;      // row 1, dot 8 (first dot of the second byte)
        black[9 + 8] = true;  // row 2, dot 8
        byte[] packed = TsplCommandBuilder.packBits(black, 9, 2);

        assertEquals(4, packed.length, "9 dots → 2 bytes per row × 2 rows");
        assertEquals((byte) 0x80, packed[0], "row 1: leftmost dot = MSB of byte 0");
        assertEquals((byte) 0x80, packed[1], "row 1: 9th dot = MSB of byte 1");
        assertEquals(0, packed[2] & 0x7F, "row 2 first byte: only MSB set");
        assertEquals((byte) 0x80, packed[3], "row 2: 9th dot");
    }

    @Test
    void testPackBitsEmptyGridIsAllZero() {
        byte[] packed = TsplCommandBuilder.packBits(new boolean[8 * 8], 8, 8);
        for (byte b : packed) assertEquals(0, b);
    }

    @Test
    void testPackBitsNullSafe() {
        byte[] packed = TsplCommandBuilder.packBits(null, 16, 16);
        assertEquals(32, packed.length, "16 dots → 2 bytes × 16 rows");
        for (byte b : packed) assertEquals(0, b);
    }

    // ------------------------------------------------------------------
    // MonoImage helpers
    // ------------------------------------------------------------------

    @Test
    void testDownsample2xAveragesQuads() {
        int[] gray = new int[4 * 4];
        java.util.Arrays.fill(gray, 200);
        gray[0] = 0; // one dark pixel in the first 2×2 quad
        int[] dots = MonoImage.downsample2x(gray, 4, 4);
        assertEquals(4, dots.length);
        assertEquals(150, dots[0], "(0+200+200+200)/4 = 150");
        assertEquals(200, dots[1]);
    }

    @Test
    void testDownsample2xQuadrantValues() {
        int[] gray = new int[4 * 4];
        java.util.Arrays.fill(gray, 100);
        java.util.Arrays.fill(gray, 0, 8, 0); // top half all black (rows 0–1)
        int[] dots = MonoImage.downsample2x(gray, 4, 4);
        assertEquals(0, dots[0]);
        assertEquals(0, dots[1]);
        assertEquals(100, dots[2]);
        assertEquals(100, dots[3]);
    }

    @Test
    void testLuminanceMonochromeTones() {
        assertEquals(0, MonoImage.luminance(0xFF000000));
        assertEquals(255, MonoImage.luminance(0xFFFFFFFF));
        assertEquals(128, MonoImage.luminance(0xFF808080));
    }

    @Test
    void testThresholdBurnsDarkDots() {
        int[] dots = {10, 200, 150, 249};
        boolean[] black = MonoImage.threshold(dots, 2, 2, 150);
        assertTrue(black[0]);
        assertFalse(black[1]);
        assertTrue(black[2]);
        assertFalse(black[3]);
    }

    @Test
    void testTa210MaxWidthConstant() {
        assertEquals(54.0, TsplCommandBuilder.TA210_MAX_PRINT_MM, 1e-9);
    }
}
