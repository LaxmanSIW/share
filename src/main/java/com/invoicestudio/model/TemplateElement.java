package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TemplateElement {
    private String id;
    private String name;
    private ElementType type = ElementType.TEXT;
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

    /* ---- individual borders for shapes/rects ---- */
    private boolean individualBorders;
    private Double borderTopWidth;
    private Double borderBottomWidth;
    private Double borderLeftWidth;
    private Double borderRightWidth;
    private String borderTopColor;
    private String borderBottomColor;
    private String borderLeftColor;
    private String borderRightColor;
    private String borderTopStyle = "solid"; // solid, dashed, dotted, none
    private String borderBottomStyle = "solid";
    private String borderLeftStyle = "solid";
    private String borderRightStyle = "solid";

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
    private double borderRadius; // mm
    private double padding; // mm
    private double opacity = 1.0;

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
    public void setBorderTopActive(Boolean active) { this.borderTop = active; }

    public boolean isBorderBottom() { return borderBottom == null || borderBottom; }
    public void setBorderBottom(Boolean borderBottom) { this.borderBottom = borderBottom; }
    public void setBorderBottomActive(Boolean active) { this.borderBottom = active; }

    public boolean isBorderLeft() { return borderLeft == null || borderLeft; }
    public void setBorderLeft(Boolean borderLeft) { this.borderLeft = borderLeft; }
    public void setBorderLeftActive(Boolean active) { this.borderLeft = active; }

    public boolean isBorderRight() { return borderRight == null || borderRight; }
    public void setBorderRight(Boolean borderRight) { this.borderRight = borderRight; }
    public void setBorderRightActive(Boolean active) { this.borderRight = active; }

    public String getRowBg() { return rowBg != null && !rowBg.isBlank() ? rowBg : "#ffffff"; }
    public void setRowBg(String rowBg) { this.rowBg = rowBg; }

    public String getRowColor() { return rowColor != null && !rowColor.isBlank() ? rowColor : "#1a1a1a"; }
    public void setRowColor(String rowColor) { this.rowColor = rowColor; }

    public String getZebraColor() { return zebraColor != null && !zebraColor.isBlank() ? zebraColor : "#f8f8f8"; }
    public void setZebraColor(String zebraColor) { this.zebraColor = zebraColor; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() {
        if (name != null && !name.isBlank()) return name.trim();
        switch (getType()) {
            case TEXT -> {
                if (text != null && !text.isBlank()) {
                    String clean = text.replace("\n", " ").trim();
                    return clean.length() > 22 ? clean.substring(0, 22) + "…" : clean;
                }
                return "Text";
            }
            case TABLE -> { return "Table"; }
            case IMAGE -> { return useBusinessLogo ? "Business Logo" : "Image"; }
            case RECT -> { return "Rectangle Shape"; }
            case LINE -> { return "Line (" + ("v".equalsIgnoreCase(direction) ? "Vertical" : "Horizontal") + ")"; }
            case QRCODE -> { return "QR Code"; }
            case BARCODE -> { return "Barcode"; }
            case PAGENO -> { return "Page Number"; }
            default -> { return getType().name(); }
        }
    }

    public boolean isIndividualBorders() { return individualBorders; }
    public void setIndividualBorders(boolean individualBorders) { this.individualBorders = individualBorders; }

    public Double getBorderTopWidth() { return borderTopWidth; }
    public void setBorderTopWidth(Double borderTopWidth) { this.borderTopWidth = borderTopWidth; }

    public Double getBorderBottomWidth() { return borderBottomWidth; }
    public void setBorderBottomWidth(Double borderBottomWidth) { this.borderBottomWidth = borderBottomWidth; }

    public Double getBorderLeftWidth() { return borderLeftWidth; }
    public void setBorderLeftWidth(Double borderLeftWidth) { this.borderLeftWidth = borderLeftWidth; }

    public Double getBorderRightWidth() { return borderRightWidth; }
    public void setBorderRightWidth(Double borderRightWidth) { this.borderRightWidth = borderRightWidth; }

    public String getBorderTopColor() { return borderTopColor; }
    public void setBorderTopColor(String borderTopColor) { this.borderTopColor = borderTopColor; }

    public String getBorderBottomColor() { return borderBottomColor; }
    public void setBorderBottomColor(String borderBottomColor) { this.borderBottomColor = borderBottomColor; }

    public String getBorderLeftColor() { return borderLeftColor; }
    public void setBorderLeftColor(String borderLeftColor) { this.borderLeftColor = borderLeftColor; }

    public String getBorderRightColor() { return borderRightColor; }
    public void setBorderRightColor(String borderRightColor) { this.borderRightColor = borderRightColor; }

    public String getBorderTopStyle() { return borderTopStyle != null ? borderTopStyle : "solid"; }
    public void setBorderTopStyle(String borderTopStyle) { this.borderTopStyle = borderTopStyle; }

    public String getBorderBottomStyle() { return borderBottomStyle != null ? borderBottomStyle : "solid"; }
    public void setBorderBottomStyle(String borderBottomStyle) { this.borderBottomStyle = borderBottomStyle; }

    public String getBorderLeftStyle() { return borderLeftStyle != null ? borderLeftStyle : "solid"; }
    public void setBorderLeftStyle(String borderLeftStyle) { this.borderLeftStyle = borderLeftStyle; }

    public String getBorderRightStyle() { return borderRightStyle != null ? borderRightStyle : "solid"; }
    public void setBorderRightStyle(String borderRightStyle) { this.borderRightStyle = borderRightStyle; }

    public double getEffectiveSideWidth(String side) {
        if (!individualBorders) return borderWidth;
        Double w = switch (side.toLowerCase()) {
            case "top" -> borderTopWidth;
            case "bottom" -> borderBottomWidth;
            case "left" -> borderLeftWidth;
            case "right" -> borderRightWidth;
            default -> null;
        };
        if (w != null) return w;
        return borderWidth > 0 ? borderWidth : 1.0;
    }

    public String getEffectiveSideColor(String side) {
        if (!individualBorders) return borderColor != null ? borderColor : "#1a1a1a";
        String c = switch (side.toLowerCase()) {
            case "top" -> borderTopColor;
            case "bottom" -> borderBottomColor;
            case "left" -> borderLeftColor;
            case "right" -> borderRightColor;
            default -> null;
        };
        return (c != null && !c.isBlank()) ? c : (borderColor != null ? borderColor : "#1a1a1a");
    }

    public String getEffectiveSideStyle(String side) {
        if (!individualBorders) return "solid";
        return switch (side.toLowerCase()) {
            case "top" -> getBorderTopStyle();
            case "bottom" -> getBorderBottomStyle();
            case "left" -> getBorderLeftStyle();
            case "right" -> getBorderRightStyle();
            default -> "solid";
        };
    }

    public boolean isSideActive(String side) {
        if (!individualBorders) return borderWidth > 0;
        return switch (side.toLowerCase()) {
            case "top" -> isBorderTop() && getEffectiveSideWidth("top") > 0 && !"none".equalsIgnoreCase(getEffectiveSideStyle("top"));
            case "bottom" -> isBorderBottom() && getEffectiveSideWidth("bottom") > 0 && !"none".equalsIgnoreCase(getEffectiveSideStyle("bottom"));
            case "left" -> isBorderLeft() && getEffectiveSideWidth("left") > 0 && !"none".equalsIgnoreCase(getEffectiveSideStyle("left"));
            case "right" -> isBorderRight() && getEffectiveSideWidth("right") > 0 && !"none".equalsIgnoreCase(getEffectiveSideStyle("right"));
            default -> true;
        };
    }

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
