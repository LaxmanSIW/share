package com.invoicestudio.service;

import java.util.List;
import java.util.Locale;

/**
 * Curated label-size presets for the TSC TA210 thermal printer, shown in the
 * Label Stock dialog as a selectable "gold rectangle" selector.
 *
 * <p>Every preset is a PHYSICAL die-cut label dimension (as it sits on the
 * roll) plus suggested spacing. All dimensions were checked against the
 * TA210's official media envelope:</p>
 * <ul>
 *   <li>Media width 25.4 – 118 mm (die-cut liner)</li>
 *   <li>Label length 10 – 2794 mm (feed direction)</li>
 *   <li>Max print width 108 mm (the 203-dpi print head)</li>
 *   <li>Resolution 203 dpi = 8 dots/mm</li>
 *   <li>Typical die-cut gap ≥ 2 mm (smaller gaps on some commercial stock —
 *       suggested values below follow the stock, not the minimum)</li>
 * </ul>
 *
 * <p>Presets are SUGGESTIONS: selecting one fills the dialog's spinners,
 * which stay fully editable. Custom dimensions are always allowed.</p>
 */
public final class LabelPresets {

    /** One selectable label-stock preset. */
    public record Preset(String name, String note,
                         double w, double h,
                         double marginLR, double rowGap, double colGap) {

        /** The exact display format requested:
         *  {@code [W×H]  |  L/R: xmm  |  Row Gap: ymm  |  Col Gap: zmm} */
        public String spec() {
            String col = colGap > 0 ? compact(colGap) + "mm" : "—";
            return "[" + compact(w) + "×" + compact(h) + "]  |  L/R: " + compact(marginLR)
                    + "mm  |  Row Gap: " + compact(rowGap) + "mm  |  Col Gap: " + col;
        }

        /** 50.0 → "50", 1.5 → "1.5" — compact dims for the selector text. */
        private String compact(double v) {
            String s = String.format(Locale.US, "%.4f", v);
            if (s.contains(".")) {
                s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
            }
            return s;
        }
    }

    // ── TA210 hardware envelope (validation targets) ──────────────────
    public static final double MEDIA_WIDTH_MIN = 25.4;   // mm
    public static final double MEDIA_WIDTH_MAX = 118.0;  // mm
    public static final double PRINT_WIDTH_MAX = 108.0;  // mm (print head)
    public static final double LENGTH_MIN = 10.0;        // mm
    public static final double LENGTH_MAX = 2794.0;      // mm
    public static final int    RESOLUTION_DPI = 203;     // = 8 dots/mm
    public static final double GAP_MIN_TYPICAL = 2.0;    // mm (die-cut)

    /** The 17 TA210 presets, in the agreed order (largest heights first). */
    public static final List<Preset> TA210 = List.of(
        new Preset("108 × 2794", "Max width × max length (continuous)", 108, 2794, 1.0, 2.0, 0),
        new Preset("108 × 150", "Standard 4×6 shipping", 108, 150, 1.0, 2.0, 3.0),
        new Preset("108 × 100", "", 108, 100, 1.0, 2.0, 3.0),
        new Preset("100 × 150", "Standard shipping label", 100, 150, 1.0, 2.0, 3.0),
        new Preset("100 × 100", "", 100, 100, 1.0, 2.0, 3.0),
        new Preset("100 × 80", "", 100, 80, 1.0, 2.0, 3.0),
        new Preset("75 × 50", "3″×2″ barcode label", 75, 50, 1.0, 1.5, 2.0),
        new Preset("60 × 40", "", 60, 40, 1.0, 1.5, 2.0),
        new Preset("50 × 50", "", 50, 50, 1.0, 1.5, 2.0),
        new Preset("50 × 40", "", 50, 40, 1.0, 1.5, 2.0),
        new Preset("50 × 30", "", 50, 30, 1.0, 1.5, 2.0),
        new Preset("50 × 25", "", 50, 25, 1.0, 1.5, 2.0),
        new Preset("45 × 30", "", 45, 30, 1.0, 1.0, 1.5),
        new Preset("40 × 60", "Vertical orientation", 40, 60, 1.0, 1.0, 1.5),
        new Preset("40 × 30", "", 40, 30, 1.0, 1.0, 1.5),
        new Preset("32 × 20", "", 32, 20, 0.8, 1.0, 1.5),
        new Preset("25.4 × 10", "Minimum supported size", 25.4, 10, 0.5, 0.5, 1.0)
    );

    private LabelPresets() {}

    /**
     * Hardware-envelope validation for hand-typed dimensions.
     * Returns {@code null} when the size is printable, otherwise a short
     * human explanation of the first violated constraint.
     */
    public static String validate(double w, double h) {
        if (w < MEDIA_WIDTH_MIN || w > MEDIA_WIDTH_MAX) {
            return String.format(Locale.US,
                    "Width %.1f mm outside TA210 media range %.1f–%g mm", w, MEDIA_WIDTH_MIN, MEDIA_WIDTH_MAX);
        }
        if (h < LENGTH_MIN || h > LENGTH_MAX) {
            return String.format(Locale.US,
                    "Length %.1f mm outside TA210 range %g–%g mm", h, LENGTH_MIN, LENGTH_MAX);
        }
        if (w > PRINT_WIDTH_MAX) {
            return String.format(Locale.US,
                    "Width %.1f mm exceeds the %g mm print head — outer %.1f mm would not print",
                    w, PRINT_WIDTH_MAX, w - PRINT_WIDTH_MAX);
        }
        return null;
    }
}
