package com.invoicestudio.service;

import com.invoicestudio.model.LabelConfig;
import com.invoicestudio.model.Template;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure (no JavaFX / no DB) math for Barcode-Mode label printing.
 * <p>
 * Layout model (matches die-cut label strips fed to thermal printers like
 * the TSC TA210):
 * <pre>
 *  |←mL→| label 1 |←gapX→| label 2 |←gapX→| label 3 |→mR|   strip = page.width
 *  |--------------- labelHeight ---------------|          page.height
 * </pre>
 * One printed page = ONE strip row carrying up to {@code columns} labels
 * (slots filled left → right from the print queue). Feed-direction gap
 * (gapY) is advanced by the printer's gap sensor, never printed.
 * <p>All methods are static and side-effect free so the whole geometry can
 * be unit-tested headlessly.</p>
 */
public final class LabelGeometryService {

    private LabelGeometryService() {}

    /** One row of the Bulk Print popup: variable values + how many copies. */
    public static class PrintLine {
        /** Variable key → value for this line (all barcode variables expected). */
        public final Map<String, String> values;
        public int copies;

        public PrintLine(Map<String, String> values, int copies) {
            this.values = values != null ? values : new LinkedHashMap<>();
            this.copies = Math.max(0, copies);
        }
    }

    /** One physical label slot the printer will output. */
    public static class LabelSlot {
        public final int pageIndex;          // 0-based strip-row page
        public final int columnIndex;        // 0-based column within the strip
        public final double xMm;             // label cell origin X on the page
        public final Map<String, String> values;

        public LabelSlot(int pageIndex, int columnIndex, double xMm, Map<String, String> values) {
            this.pageIndex = pageIndex;
            this.columnIndex = columnIndex;
            this.xMm = xMm;
            this.values = values;
        }
    }

    // ------------------------------------------------------------------
    // Geometry
    // ------------------------------------------------------------------

    /**
     * Physical label width on the strip. For 90/270 print orientation the
     * design is rotated, so the design's height becomes the physical width.
     */
    public static double physicalCellWidth(LabelConfig c) {
        return ("90".equals(c.getOrientation()) || "270".equals(c.getOrientation()))
                ? c.getLabelHeight() : c.getLabelWidth();
    }

    /** Physical label height on the strip (feed direction). */
    public static double physicalCellHeight(LabelConfig c) {
        return ("90".equals(c.getOrientation()) || "270".equals(c.getOrientation()))
                ? c.getLabelWidth() : c.getLabelHeight();
    }

    /** True when the labels + gaps + margins actually fit the strip width. */
    public static boolean fitsStrip(LabelConfig c) {
        return requiredStripWidth(c) <= c.getStripWidth() + 1e-6;
    }

    /** Exact liner width needed for the current columns/gaps/margins config. */
    public static double requiredStripWidth(LabelConfig c) {
        double cellW = physicalCellWidth(c);
        return c.getMarginL() + c.getMarginR()
                + (double) c.getColumns() * cellW
                + (double) Math.max(0, c.getColumns() - 1) * c.getGapX();
    }

    /**
     * X offset (mm) of each label cell on the printed page, left → right.
     * Size = columns. Cells are centered inside the strip if margins leave
     * slack, so a slightly-too-wide stock still prints symmetrically.
     */
    public static double[] columnOffsets(LabelConfig c) {
        double cellW = physicalCellWidth(c);
        double content = requiredStripWidth(c);
        double slack = c.getStripWidth() - content;
        double lead = c.getMarginL() + Math.max(0, slack) / 2.0; // center when slack
        double[] xs = new double[c.getColumns()];
        double x = lead;
        for (int i = 0; i < c.getColumns(); i++) {
            xs[i] = round2(x);
            x += cellW + c.getGapX();
        }
        return xs;
    }

    /** Printed page size in mm — one strip row. */
    public static double[] pageSizeMm(LabelConfig c) {
        return new double[]{c.getStripWidth(), physicalCellHeight(c)};
    }

    /** Feed pitch (physical label height + gapY) in mm — informational for the driver setup. */
    public static double feedPitchMm(LabelConfig c) {
        return physicalCellHeight(c) + c.getGapY();
    }

    // ------------------------------------------------------------------
    // Queue → slots
    // ------------------------------------------------------------------

    /** Total physical labels a queue produces. */
    public static int totalLabels(List<PrintLine> lines) {
        int n = 0;
        if (lines != null) for (PrintLine l : lines) n += l.copies;
        return n;
    }

    /** Number of strip-row pages the queue produces. */
    public static int totalPages(List<PrintLine> lines, LabelConfig c) {
        int labels = totalLabels(lines);
        if (labels == 0) return 0;
        return (labels + c.getColumns() - 1) / c.getColumns();
    }

    /**
     * Expands the print queue into physical slots, filling each strip row
     * left → right before starting the next page. The last page keeps only
     * the slots it needs (remaining columns stay blank).
     */
    public static List<LabelSlot> expandSlots(List<PrintLine> lines, LabelConfig c) {
        List<LabelSlot> slots = new ArrayList<>();
        if (lines == null || lines.isEmpty() || c.getColumns() < 1) return slots;

        double[] xs = columnOffsets(c);
        int labels = totalLabels(lines);
        int pages = totalPages(lines, c);

        int page = 0, col = 0;
        for (PrintLine line : lines) {
            for (int k = 0; k < line.copies; k++) {
                if (page >= pages) return slots; // safety
                slots.add(new LabelSlot(page, col, xs[col], line.values));
                col++;
                if (col == c.getColumns()) { col = 0; page++; }
            }
        }
        return slots;
    }

    /** Groups slots by page index in order (for the print loop). */
    public static Map<Integer, List<LabelSlot>> slotsByPage(List<LabelSlot> slots) {
        Map<Integer, List<LabelSlot>> byPage = new LinkedHashMap<>();
        for (LabelSlot s : slots) {
            byPage.computeIfAbsent(s.pageIndex, k -> new ArrayList<>()).add(s);
        }
        return byPage;
    }

    // ------------------------------------------------------------------
    // Design rotation — one-shot 90° design-space conversion
    // ------------------------------------------------------------------

    /**
     * Maps one element's geometry from a W×H design canvas into the same
     * canvas rotated 90° CLOCKWISE (the rotated canvas is H wide × W high).
     * Returns {@code {x, y, w, h}} in the rotated canvas.
     * <p>Pure math so the designer's "Rotate Design 90°" action lands every
     * element exactly where it visually was — just spun into the print
     * orientation — and so the whole transform is unit-testable.</p>
     */
    public static double[] rotateElement90CW(double x, double y, double w, double h,
                                             double canvasHeightMm) {
        return new double[]{
                round2(canvasHeightMm - y - h),
                round2(x),
                round2(h),
                round2(w)
        };
    }

    /** Element's own rotation after the canvas spun 90° CW (wraps at 360°). */
    public static double rotateElementRotation90CW(double rotation) {
        return ((rotation % 360.0) + 90.0) % 360.0;
    }

    /** Human one-line summary of the queue for the history table. */
    public static String summarize(List<PrintLine> lines, List<String> variableOrder) {
        StringBuilder sb = new StringBuilder();
        if (lines == null) return "";
        for (int i = 0; i < lines.size(); i++) {
            PrintLine l = lines.get(i);
            if (i > 0) sb.append("; ");
            sb.append("(");
            if (variableOrder != null) {
                boolean first = true;
                for (String key : variableOrder) {
                    String v = l.values.get(key);
                    if (v == null || v.isBlank()) continue;
                    if (!first) sb.append(" · ");
                    sb.append(v);
                    first = false;
                }
            }
            sb.append(") × ").append(l.copies);
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // Serialization helpers (history lines JSON)
    // ------------------------------------------------------------------

    /** Serializes print lines to a compact JSON (kept free of Jackson deps for testability). */
    public static String linesToJson(List<PrintLine> lines, List<String> variableOrder) {
        StringBuilder sb = new StringBuilder("[");
        if (lines != null) {
            for (int i = 0; i < lines.size(); i++) {
                PrintLine l = lines.get(i);
                if (i > 0) sb.append(",");
                sb.append("{");
                boolean first = true;
                if (variableOrder != null) {
                    for (String key : variableOrder) {
                        String v = l.values.get(key);
                        if (v == null) v = "";
                        if (!first) sb.append(",");
                        sb.append(quote(key)).append(":").append(quote(v));
                        first = false;
                    }
                }
                if (!first) sb.append(",");
                sb.append("\"copies\":").append(l.copies);
                sb.append("}");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static String quote(String s) {
        String safe = s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
        return "\"" + safe + "\"";
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    /** Warning list for the Label Settings dialog — empty = all good. */
    public static List<String> validate(Template template) {
        List<String> warn = new ArrayList<>();
        if (template == null || !template.isLabelMode()) return warn;
        LabelConfig c = template.labelOrNew();
        c.sanitize();
        if (!fitsStrip(c)) {
            warn.add(String.format(java.util.Locale.US,
                    "Labels do not fit the strip: need %.1f mm but strip is %.1f mm (labels will overflow).",
                    requiredStripWidth(c), c.getStripWidth()));
        }
        if (c.getStripWidth() > 118.0) {
            warn.add("Strip width above 118 mm exceeds even 4-inch thermal printers; "
                    + "note the TSC TA210 (2-inch) prints at most 54 mm across.");
        } else if (c.getStripWidth() > TsplCommandBuilder.TA210_MAX_PRINT_MM) {
            warn.add(String.format(java.util.Locale.US,
                    "Strip width %.1f mm exceeds the TSC TA210 print head (54 mm) — "
                            + "use 1-up stock or a 4-inch printer.", c.getStripWidth()));
        }
        boolean hasBarcode = template.getElements() != null && template.getElements().stream()
                .anyMatch(e -> e.getType() == com.invoicestudio.model.ElementType.BARCODE
                        || e.getType() == com.invoicestudio.model.ElementType.QRCODE);
        if (!hasBarcode) {
            warn.add("This label has no Barcode/QR element yet — add one from the Code menu.");
        }
        return warn;
    }
}
