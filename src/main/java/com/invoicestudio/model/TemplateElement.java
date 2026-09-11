package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TemplateElement {
    private String id;
    private ElementType type = ElementType.TEXT;
    private String name; // optional object name (Layers panel; auto-generated when null)
    private double x; // mm
    private double y; // mm
    private double w = 40; // mm
    private double h = 10; // mm
    private int zIndex;
    private boolean locked;
    private boolean hidden;
    private boolean repeatOnPages;
    private double rotation; // -180..180 deg
    private boolean hideWhenBlank;

    /* ---- text ---- */
    private String text = "";
    private String fontFamily = "Segoe UI";
    private double fontSize = 10; // pt
    private int fontWeight = 400; // 400 or 700
    private boolean italic;
    private boolean underline;
    private boolean uppercase;
    private String color = "#1a1a1a";
    private String align = "left"; // left, center, right
    private String vAlign = "top"; // top, middle, bottom
    private double lineHeight = 1.25;
    private double letterSpacing = 0;

    /* ---- appearance ---- */
    private String bg = "transparent";
    private double borderWidth; // mm
    private String borderColor = "#1a1a1a";
    private String strokeStyle = "solid"; // solid, dashed, dotted, double (shapes & lines)
    private double borderRadius; // mm
    private double padding; // mm
    private double opacity = 1.0;

    /* ---- per-side stroke overrides (RECT; null/blank = inherit global) ---- */
    private Double borderTopWidth;    // mm; null → borderWidth
    private Double borderBottomWidth;
    private Double borderLeftWidth;
    private Double borderRightWidth;
    private String borderTopColor;    // hex; null/blank → borderColor
    private String borderBottomColor;
    private String borderLeftColor;
    private String borderRightColor;

    /* ---- star shape ---- */
    private int starPoints = 5;
    private double starInnerRatio = 0.45;

    /* ---- image ---- */
    private String src = "";
    private String objectFit = "contain"; // contain, cover, fill
    private boolean useBusinessLogo;

    /* ---- qrcode ---- */
    private String qrSource = "upi_amount"; // upi_amount, upi, custom
    private String qrCustom = "";
    private String qrColor = "#111111";

    /* ---- barcode ---- */
    private String barcodeData = "{{invoice_no}}";
    private String barcodeColor = "#111111";
    private boolean barcodeShowText = true;

    /* ---- line ---- */
    private String direction = "h"; // h, v

    /* ---- table ---- */
    private List<TableColumn> columns = new ArrayList<>();
    private String headerBg = "#efe9db";
    private String headerColor = "#1a1a1a";
    private double rowHeight = 7.0; // mm
    private String borderStyle = "grid"; // grid, rows, outline, none
    private boolean showZebra = true;
    private String tableBorderColor = "#c8c8c8";
    private double tableBorderWidth = 0.26; // mm (~1px @96dpi)
    private Boolean borderTop = true;
    private Boolean borderBottom = true;
    private Boolean borderLeft = true;
    private Boolean borderRight = true;
    private String rowBg = "#ffffff";
    private String rowColor = "#1a1a1a";
    private String zebraColor = "#f8f8f8";

    public TemplateElement() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    /** Layers-panel display name; falls back to the element type. */
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String displayName() {
        return name != null && !name.isBlank() ? name : getType().name();
    }

    public ElementType getType() { return type != null ? type : ElementType.TEXT; }
    public void setType(ElementType type) { this.type = type; }

    public double getX() { return x; }
    public void setX(double x) { this.x = x; }

    public double getY() { return y; }
    public void setY(double y) { this.y = y; }

    public double getW() { return w; }
    public void setW(double w) { this.w = w; }

    public double getH() { return h; }
    public void setH(double h) { this.h = h; }

    public int getZIndex() { return zIndex; }
    public void setZIndex(int zIndex) { this.zIndex = zIndex; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public boolean isHidden() { return hidden; }
    public void setHidden(boolean hidden) { this.hidden = hidden; }

    public boolean isRepeatOnPages() { return repeatOnPages; }
    public void setRepeatOnPages(boolean repeatOnPages) { this.repeatOnPages = repeatOnPages; }

    public double getRotation() { return rotation; }
    public void setRotation(double rotation) { this.rotation = rotation; }

    public boolean isHideWhenBlank() { return hideWhenBlank; }
    public void setHideWhenBlank(boolean hideWhenBlank) { this.hideWhenBlank = hideWhenBlank; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getFontFamily() { return fontFamily; }
    public void setFontFamily(String fontFamily) { this.fontFamily = fontFamily; }

    public double getFontSize() { return fontSize; }
    public void setFontSize(double fontSize) { this.fontSize = fontSize; }

    public int getFontWeight() { return fontWeight; }
    public void setFontWeight(int fontWeight) { this.fontWeight = fontWeight; }

    public boolean isItalic() { return italic; }
    public void setItalic(boolean italic) { this.italic = italic; }

    public boolean isUnderline() { return underline; }
    public void setUnderline(boolean underline) { this.underline = underline; }

    public boolean isUppercase() { return uppercase; }
    public void setUppercase(boolean uppercase) { this.uppercase = uppercase; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getAlign() { return align != null ? align : "left"; }
    public void setAlign(String align) { this.align = align; }

    public String getVAlign() { return vAlign != null ? vAlign : "top"; }
    public void setVAlign(String vAlign) { this.vAlign = vAlign; }

    public double getLineHeight() { return lineHeight; }
    public void setLineHeight(double lineHeight) { this.lineHeight = lineHeight; }

    public double getLetterSpacing() { return letterSpacing; }
    public void setLetterSpacing(double letterSpacing) { this.letterSpacing = letterSpacing; }

    public String getBg() { return bg; }
    public void setBg(String bg) { this.bg = bg; }

    public double getBorderWidth() { return borderWidth; }
    public void setBorderWidth(double borderWidth) { this.borderWidth = borderWidth; }

    public String getBorderColor() { return borderColor; }
    public void setBorderColor(String borderColor) { this.borderColor = borderColor; }

    public double getBorderRadius() { return borderRadius; }
    public void setBorderRadius(double borderRadius) { this.borderRadius = borderRadius; }

    public String getStrokeStyle() { return strokeStyle != null && !strokeStyle.isBlank() ? strokeStyle : "solid"; }
    public void setStrokeStyle(String strokeStyle) { this.strokeStyle = strokeStyle; }

    /* Per-side stroke: null/blank side values inherit the global border color/width. */
    public Double getBorderTopWidth() { return borderTopWidth; }
    public void setBorderTopWidth(Double v) { this.borderTopWidth = v; }
    public Double getBorderBottomWidth() { return borderBottomWidth; }
    public void setBorderBottomWidth(Double v) { this.borderBottomWidth = v; }
    public Double getBorderLeftWidth() { return borderLeftWidth; }
    public void setBorderLeftWidth(Double v) { this.borderLeftWidth = v; }
    public Double getBorderRightWidth() { return borderRightWidth; }
    public void setBorderRightWidth(Double v) { this.borderRightWidth = v; }

    public String getBorderTopColor() { return borderTopColor; }
    public void setBorderTopColor(String v) { this.borderTopColor = v; }
    public String getBorderBottomColor() { return borderBottomColor; }
    public void setBorderBottomColor(String v) { this.borderBottomColor = v; }
    public String getBorderLeftColor() { return borderLeftColor; }
    public void setBorderLeftColor(String v) { this.borderLeftColor = v; }
    public String getBorderRightColor() { return borderRightColor; }
    public void setBorderRightColor(String v) { this.borderRightColor = v; }

    /** Effective side width (mm) — side override or global border width. */
    public double sideWidthMm(char side) {
        Double v = switch (side) {
            case 'T' -> borderTopWidth;
            case 'B' -> borderBottomWidth;
            case 'L' -> borderLeftWidth;
            case 'R' -> borderRightWidth;
            default -> null;
        };
        return v != null ? v : borderWidth;
    }

    /** Effective side color (hex) — side override or global border color. */
    public String sideColorHex(char side) {
        String v = switch (side) {
            case 'T' -> borderTopColor;
            case 'B' -> borderBottomColor;
            case 'L' -> borderLeftColor;
            case 'R' -> borderRightColor;
            default -> null;
        };
        return v != null && !v.isBlank() ? v : (borderColor != null ? borderColor : "#1a1a1a");
    }

    /** True when any per-side customization is in effect (forces 4-side rendering). */
    public boolean hasPerSideStroke() {
        return !(isBorderTop() && isBorderBottom() && isBorderLeft() && isBorderRight())
                || borderTopWidth != null || borderBottomWidth != null
                || borderLeftWidth != null || borderRightWidth != null
                || (borderTopColor != null && !borderTopColor.isBlank())
                || (borderBottomColor != null && !borderBottomColor.isBlank())
                || (borderLeftColor != null && !borderLeftColor.isBlank())
                || (borderRightColor != null && !borderRightColor.isBlank());
    }

    public int getStarPoints() { return starPoints > 2 ? starPoints : 5; }
    public void setStarPoints(int starPoints) { this.starPoints = starPoints; }

    public double getStarInnerRatio() { return starInnerRatio > 0.05 && starInnerRatio < 0.95 ? starInnerRatio : 0.45; }
    public void setStarInnerRatio(double starInnerRatio) { this.starInnerRatio = starInnerRatio; }

    public double getPadding() { return padding; }
    public void setPadding(double padding) { this.padding = padding; }

    public double getOpacity() { return opacity; }
    public void setOpacity(double opacity) { this.opacity = opacity; }

    public String getSrc() { return src; }
    public void setSrc(String src) { this.src = src; }

    public String getObjectFit() { return objectFit; }
    public void setObjectFit(String objectFit) { this.objectFit = objectFit; }

    public boolean isUseBusinessLogo() { return useBusinessLogo; }
    public void setUseBusinessLogo(boolean useBusinessLogo) { this.useBusinessLogo = useBusinessLogo; }

    public String getQrSource() { return qrSource; }
    public void setQrSource(String qrSource) { this.qrSource = qrSource; }

    public String getQrCustom() { return qrCustom; }
    public void setQrCustom(String qrCustom) { this.qrCustom = qrCustom; }

    public String getQrColor() { return qrColor; }
    public void setQrColor(String qrColor) { this.qrColor = qrColor; }

    public String getBarcodeData() { return barcodeData; }
    public void setBarcodeData(String barcodeData) { this.barcodeData = barcodeData; }

    public String getBarcodeColor() { return barcodeColor; }
    public void setBarcodeColor(String barcodeColor) { this.barcodeColor = barcodeColor; }

    public boolean isBarcodeShowText() { return barcodeShowText; }
    public void setBarcodeShowText(boolean barcodeShowText) { this.barcodeShowText = barcodeShowText; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public List<TableColumn> getColumns() { return columns; }
    public void setColumns(List<TableColumn> columns) { this.columns = columns != null ? columns : new ArrayList<>(); }

    public String getHeaderBg() { return headerBg; }
    public void setHeaderBg(String headerBg) { this.headerBg = headerBg; }

    public String getHeaderColor() { return headerColor; }
    public void setHeaderColor(String headerColor) { this.headerColor = headerColor; }

    public double getRowHeight() { return rowHeight; }
    public void setRowHeight(double rowHeight) { this.rowHeight = rowHeight; }

    public String getBorderStyle() { return borderStyle != null ? borderStyle : "grid"; }
    public void setBorderStyle(String borderStyle) { this.borderStyle = borderStyle; }

    public boolean isShowZebra() { return showZebra; }
    public void setShowZebra(boolean showZebra) { this.showZebra = showZebra; }

    public String getTableBorderColor() { return tableBorderColor != null && !tableBorderColor.isBlank() ? tableBorderColor : "#c8c8c8"; }
    public void setTableBorderColor(String tableBorderColor) { this.tableBorderColor = tableBorderColor; }

    public double getTableBorderWidth() { return tableBorderWidth; }
    public void setTableBorderWidth(double tableBorderWidth) { this.tableBorderWidth = tableBorderWidth; }

    /** Outer border sides — null-safe, default true for legacy templates. */
    public boolean isBorderTop() { return borderTop == null || borderTop; }
    public void setBorderTop(Boolean borderTop) { this.borderTop = borderTop; }

    public boolean isBorderBottom() { return borderBottom == null || borderBottom; }
    public void setBorderBottom(Boolean borderBottom) { this.borderBottom = borderBottom; }

    public boolean isBorderLeft() { return borderLeft == null || borderLeft; }
    public void setBorderLeft(Boolean borderLeft) { this.borderLeft = borderLeft; }

    public boolean isBorderRight() { return borderRight == null || borderRight; }
    public void setBorderRight(Boolean borderRight) { this.borderRight = borderRight; }

    public String getRowBg() { return rowBg != null && !rowBg.isBlank() ? rowBg : "#ffffff"; }
    public void setRowBg(String rowBg) { this.rowBg = rowBg; }

    public String getRowColor() { return rowColor != null && !rowColor.isBlank() ? rowColor : "#1a1a1a"; }
    public void setRowColor(String rowColor) { this.rowColor = rowColor; }

    public String getZebraColor() { return zebraColor != null && !zebraColor.isBlank() ? zebraColor : "#f8f8f8"; }
    public void setZebraColor(String zebraColor) { this.zebraColor = zebraColor; }

    /**
     * Scale factor for table fonts relative to the legacy default (7.5pt), so
     * existing templates render exactly as before while the Font Size spinner
     * now visibly affects canvas, preview and PDF. Clamped to [0.5, 3.0].
     */
    public double tableFontScale() {
        double s = fontSize > 0 ? fontSize / 7.5 : 1.0;
        return Math.max(0.5, Math.min(3.0, s));
    }
}
