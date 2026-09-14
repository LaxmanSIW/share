package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Label / barcode stock configuration for templates saved in
 * {@code mode = "label"} (Barcode Mode — thermal label printers like the
 * TSC TA210).
 *
 * <p>Mental model (matches die-cut label stock on a roll/strip):</p>
 * <pre>
 *  ←marginL→[ label ][ gapX ][ label ]←marginR→   ← strip (liner) width
 *            ↑ labelHeight, feed gap = gapY (handled by the printer's
 *              gap sensor; included here for sheet layout + preview)
 * </pre>
 *
 * <p>The designer canvas is ALWAYS one label cell ({@code labelWidth} x
 * {@code labelHeight} mm); printing tiles the design across the columns of
 * the strip and substitutes variables per slot from the Bulk Print queue.</p>
 *
 * <p>All existing templates (no {@code labelConfig} in JSON) keep working:
 * Jackson leaves this field null and {@link Template#labelOrNew()} falls back
 * to a default instance. Same trick for {@code mode}: null/absent = "bill".</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LabelConfig {

    /** Total liner/strip width in mm (the full web the printer prints across). */
    private double stripWidth = 100.0;

    /** How many die-cut labels sit across the strip (1 = standard roll labels). */
    private int columns = 1;

    /** Single label cell width in mm (canvas size in Barcode Mode). */
    private double labelWidth = 50.0;

    /** Single label cell height in mm (canvas height in Barcode Mode). */
    private double labelHeight = 25.0;

    /** Horizontal gap between label columns in mm. */
    private double gapX = 3.0;

    /** Vertical feed gap between label rows in mm (gap-sensor pitch). */
    private double gapY = 3.0;

    /** Die-cut corner radius of the label in mm (preview + cut guides). */
    private double cornerRadius = 2.0;

    /**
     * Extra rotation applied to the artwork at print time — one of
     * "0", "90", "180", "270". Lets a label designed one way print rotated
     * (e.g. sideways on a vertical dispenser) without re-designing it.
     */
    private String orientation = "0";

    /** Left strip margin in mm (unprintable/holder area). */
    private double marginL = 0.0;

    /** Right strip margin in mm. */
    private double marginR = 0.0;

    /**
     * Print style: "gap" (die-cut roll, printer advances via gap sensor) or
     * "continuous" (receipt-style stock). Purely informational for the driver
     * dialog; kept so shops can record their stock per template.
     */
    private String stockType = "gap";

    public LabelConfig() {}

    /** Deep copy — used by print pipelines that tweak values without touching the saved template. */
    public LabelConfig copy() {
        LabelConfig c = new LabelConfig();
        c.stripWidth = this.stripWidth;
        c.columns = this.columns;
        c.labelWidth = this.labelWidth;
        c.labelHeight = this.labelHeight;
        c.gapX = this.gapX;
        c.gapY = this.gapY;
        c.cornerRadius = this.cornerRadius;
        c.orientation = this.orientation;
        c.marginL = this.marginL;
        c.marginR = this.marginR;
        c.stockType = this.stockType;
        return c;
    }

    /** Clamps negatives / nonsense values so a bad JSON can never crash the renderer. */
    public void sanitize() {
        if (stripWidth <= 0) stripWidth = 100.0;
        if (columns < 1) columns = 1;
        if (columns > 8) columns = 8;
        if (labelWidth <= 0) labelWidth = 50.0;
        if (labelHeight <= 0) labelHeight = 25.0;
        if (gapX < 0) gapX = 0;
        if (gapY < 0) gapY = 0;
        if (cornerRadius < 0) cornerRadius = 0;
        if (marginL < 0) marginL = 0;
        if (marginR < 0) marginR = 0;
        if (orientation == null) orientation = "0";
        if (!List.of("0", "90", "180", "270").contains(orientation)) orientation = "0";
        if (stockType == null || stockType.isBlank()) stockType = "gap";
    }

    // --- getters / setters ---

    public double getStripWidth() { return stripWidth; }
    public void setStripWidth(double stripWidth) { this.stripWidth = stripWidth; }

    public int getColumns() { return columns; }
    public void setColumns(int columns) { this.columns = columns; }

    public double getLabelWidth() { return labelWidth; }
    public void setLabelWidth(double labelWidth) { this.labelWidth = labelWidth; }

    public double getLabelHeight() { return labelHeight; }
    public void setLabelHeight(double labelHeight) { this.labelHeight = labelHeight; }

    public double getGapX() { return gapX; }
    public void setGapX(double gapX) { this.gapX = gapX; }

    public double getGapY() { return gapY; }
    public void setGapY(double gapY) { this.gapY = gapY; }

    public double getCornerRadius() { return cornerRadius; }
    public void setCornerRadius(double cornerRadius) { this.cornerRadius = cornerRadius; }

    public String getOrientation() { return orientation != null ? orientation : "0"; }
    public void setOrientation(String orientation) { this.orientation = orientation; }

    public double getMarginL() { return marginL; }
    public void setMarginL(double marginL) { this.marginL = marginL; }

    public double getMarginR() { return marginR; }
    public void setMarginR(double marginR) { this.marginR = marginR; }

    public String getStockType() { return stockType != null ? stockType : "gap"; }
    public void setStockType(String stockType) { this.stockType = stockType; }
}
