package com.invoicestudio.service;

import com.invoicestudio.model.LabelConfig;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Pure (no JavaFX / no I/O) builder for TSPL/TSPL2 print scripts — the
 * native command language of TSC printers such as the TA210.
 * <p>
 * Why a native TSPL path exists: the JavaFX/driver path lets the Windows
 * spooler decide paper size and feed length from the driver's stock
 * settings, so any mismatch between our strip row and the driver stock
 * makes a gap-sensor printer feed EXTRA blank labels (or rotate the page).
 * A TSPL script sent as RAW data bypasses GDI entirely: <b>we</b> declare
 * {@code SIZE} (the strip row, in dots), {@code GAP} (the feed gap, in
 * dots) and {@code PRINT} (exactly how many copies) — so one selected
 * label feeds exactly one label, and the bitmap we render IS the printed
 * page (WYSIWYG with the strip preview).
 * <p>
 * Command syntax verified against the official "TSPL/TSPL2 Programming
 * Language" manual (TSC Auto ID, 2014):
 * <ul>
 *   <li>{@code SIZE m dot[,n dot]} — label width/length in dots (8 dots/mm
 *       on the 203-dpi TA210; manual p.1). Dot units avoid all mm→dots
 *       rounding drift between the declared stock and the bitmap.</li>
 *   <li>{@code GAP m,n} — feed gap; {@code GAP 0,0} = continuous stock
 *       (manual p.2).</li>
 *   <li>{@code DIRECTION 1} — image y=0 is the leading edge, text reads
 *       upright after tearing (manual p.12 illustration "DIRECTION 1,0").</li>
 *   <li>{@code CLS} — clears the image buffer (manual p.15).</li>
 *   <li>{@code BITMAP X,Y,width,height,mode,data} — width is BYTES per row,
 *       height is DOTS, data is MSB-first rows padded to the byte boundary,
 *       bit 0 = black dot (burned), bit 1 = white dot (empty); the raw
 *       bytes follow the final comma directly and are terminated by CRLF
 *       (manual p.45–46 hex example — its illustration shades the 0 cells).
 *       Polarity additionally verified on real hardware and independent
 *       TSPL rasterizers.</li>
 *   <li>{@code PRINT m[,n]} — m sets × n copies each; {@code PRINT 1,1}
 *       prints exactly one label (manual p.24).</li>
 * </ul>
 * All methods are static and side-effect free so the whole script can be
 * unit-tested headlessly.
 */
public final class TsplCommandBuilder {

    private TsplCommandBuilder() {}

    /** TSC TA210/TA200 (2-inch) max print area across the head, in mm. */
    public static final double TA210_MAX_PRINT_MM = 54.0;

    /** One rendered strip-row page: 1-bit bitmap + its dot dimensions. */
    public record TsplPage(byte[] mono, int widthBytes, int heightDots) {}

    /** How the driver is fed — mirrors {@link LabelConfig#getStockType()}. */
    private static String sensorLine(LabelConfig cfg, int gapDots) {
        if ("continuous".equalsIgnoreCase(cfg.getStockType())) {
            return "GAP 0,0"; // manual: continuous label
        }
        // "gap" (and anything unknown) — die-cut roll, gap sensor pitch.
        return "GAP " + Math.max(0, gapDots) + " dot,0 dot";
    }

    /**
     * The setup header: SIZE / GAP / DIRECTION. One printed TSPL "label"
     * = ONE strip row (web width × row height) exactly as the strip
     * preview draws it.
     *
     * @param stripWidthDots page width across the head (dots)
     * @param rowHeightDots  page length along the feed (dots)
     * @param gapDots        feed gap between rows (dots)
     * @param direction      0/1 — 1 = preview-upright printout
     */
    public static String header(LabelConfig cfg, int stripWidthDots, int rowHeightDots,
                                int gapDots, int direction) {
        StringBuilder sb = new StringBuilder();
        sb.append("SIZE ").append(Math.max(1, stripWidthDots)).append(" dot,")
          .append(Math.max(1, rowHeightDots)).append(" dot\r\n");
        sb.append(sensorLine(cfg, gapDots)).append("\r\n");
        sb.append("DIRECTION ").append(direction == 0 ? 0 : 1).append("\r\n");
        return sb.toString();
    }

    /** BITMAP command line WITHOUT the binary payload (payload follows the comma directly). */
    public static String bitmapCommand(int widthBytes, int heightDots) {
        return "BITMAP 0,0," + Math.max(1, widthBytes) + "," + Math.max(1, heightDots) + ",0,";
    }

    /**
     * Groups consecutive identical pages into single {@code PRINT k,1}
     * commands — the printer repeats its buffer, so a 100-copy queue
     * becomes ONE bitmap download and one PRINT, not 100 round-trips.
     * Pure and unit tested.
     *
     * @return run lengths in page order, e.g. pages [A,A,B,A] → [2,1,1]
     */
    public static int[] runLengths(List<TsplPage> pages) {
        int n = pages == null ? 0 : pages.size();
        int[] runs = new int[n];
        int out = 0;
        int i = 0;
        while (i < n) {
            int j = i + 1;
            while (j < n && samePage(pages.get(i), pages.get(j))) j++;
            runs[out++] = j - i;
            i = j;
        }
        return Arrays.copyOf(runs, out);
    }

    private static boolean samePage(TsplPage a, TsplPage b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.widthBytes() == b.widthBytes()
                && a.heightDots() == b.heightDots()
                && Arrays.equals(a.mono(), b.mono());
    }

    /**
     * Builds the complete TSPL script: header + per-page CLS/BITMAP/PRINT
     * with identical consecutive pages run-length compressed.
     *
     * @param cfg        label stock config (feed sensor type comes from here)
     * @param pages      rendered strip-row bitmaps in feed order
     * @param stripWidthDots page width in dots (drives SIZE)
     * @param rowHeightDots  page height in dots (drives SIZE)
     * @param gapDots    feed gap in dots
     * @param direction  1 = preview-upright (default), 0 flips 180°
     * @return the exact bytes to hand to the spooler as a RAW job
     */
    public static byte[] build(LabelConfig cfg, List<TsplPage> pages,
                               int stripWidthDots, int rowHeightDots,
                               int gapDots, int direction) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        out.writeBytes(header(cfg, stripWidthDots, rowHeightDots, gapDots, direction)
                .getBytes(StandardCharsets.ISO_8859_1));

        int[] runs = runLengths(pages);
        int idx = 0;
        for (int run : runs) {
            TsplPage p = pages.get(idx);
            idx += run;
            out.writeBytes("CLS\r\n".getBytes(StandardCharsets.ISO_8859_1));
            out.writeBytes(bitmapCommand(p.widthBytes(), p.heightDots())
                    .getBytes(StandardCharsets.ISO_8859_1));
            out.writeBytes(p.mono());
            out.writeBytes("\r\n".getBytes(StandardCharsets.ISO_8859_1));
            out.writeBytes(("PRINT " + run + ",1\r\n").getBytes(StandardCharsets.ISO_8859_1));
        }
        return out.toByteArray();
    }

    /**
     * Packs a boolean grid (true = burn black) into TSPL BITMAP rows:
     * MSB first, each row padded to the byte boundary.
     * <p>
     * <b>Bit polarity:</b> the TSC thermal head burns where the BITMAP bit
     * is 0 and leaves paper where the bit is 1 — a WHITE dot is a set bit,
     * a BLACK dot a cleared bit. (Verified against the official manual's
     * example illustration, field prints on a TA210 and independent TSPL
     * rasterizers; sending ink as set bits inverts the whole label — a
     * black page with white content.)
     * Pure and unit tested.
     *
     * @param black       row-major black flags, length widthDots × heightDots
     * @param widthDots   bitmap width in dots
     * @param heightDots  bitmap height in dots
     */
    public static byte[] packBits(boolean[] black, int widthDots, int heightDots) {
        int widthBytes = (widthDots + 7) / 8;
        byte[] out = new byte[widthBytes * Math.max(0, heightDots)];
        if (black == null) return out;
        // White paper encodes as 1-bits: start every byte fully white, then
        // CLEAR the bits where ink burns (bit 0 = black dot on TSC heads).
        // Padding dots beyond widthDots stay white (1) — they only ever sit
        // in the unused tail of a row's last byte.
        java.util.Arrays.fill(out, (byte) 0xFF);
        for (int y = 0; y < heightDots; y++) {
            int rowBase = y * widthBytes;
            for (int x = 0; x < widthDots; x++) {
                if (black[y * widthDots + x]) {
                    int idx = rowBase + (x >> 3);
                    out[idx] &= (byte) ~(0x80 >> (x & 7));
                }
            }
        }
        return out;
    }

    /** Bytes per BITMAP row for a dot width (rounded up to full bytes). */
    public static int widthBytes(int widthDots) {
        return (Math.max(0, widthDots) + 7) / 8;
    }
}
