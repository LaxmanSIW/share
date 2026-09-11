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
    private double lineSpacing = 0;
    private double letterSpacing = 0;
    private double wordSpacing = 0;
    private String textTransform = "none"; // none, uppercase, lowercase, capitalize
    private String groupName = "";

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

    /* ---- fill & gradient ---- */
    private String fillType = "solid"; // solid, linear, radial, none
    private String gradientStartColor = "#4f46e5";
    private String gradientEndColor = "#06b6d4";
    private double gradientAngle = 45.0; // deg
    private double gradientCenterX = 0.5;
    private double gradientCenterY = 0.5;
    private double gradientRadius = 0.5;

    /* ---- stroke & dash ---- */
    private boolean strokeEnabled = false;
    private String strokeType = "centered"; // inside, centered, outside
    private String lineCap = "butt"; // butt, round, square
    private String lineJoin = "miter"; // miter, round, bevel
    private String dashPattern = ""; // e.g. "5,3" or "4,4"
    private double dashOffset = 0.0;

    /* ---- individual corner radii ---- */
    private Double topLeftRadius;
    private Double topRightRadius;
    private Double bottomRightRadius;
    private Double bottomLeftRadius;

    /* ---- universal geometry ---- */
    private double radius = 15.0; // mm for circle
    private double radiusX = 20.0; // mm for ellipse
    private double radiusY = 12.0; // mm for ellipse
    private String points = "0,0 20,40 40,0"; // for polygon/polyline
    private double startAngle = 0.0; // deg for arc
    private double arcLength = 90.0; // deg for arc
    private String arcType = "open"; // open, chord, round
    private String pathData = "M 0 0 L 30 0 L 15 30 Z"; // SVG path commands
    private int starPoints = 5;
    private double innerRadius = 6.0; // mm for star
    private double outerRadius = 15.0; // mm for star
    private double arrowShaftWidth = 2.0; // mm
    private double arrowHeadLength = 6.0; // mm
    private double arrowHeadWidth = 6.0; // mm
    private String arrowHeadStyle = "triangle"; // triangle, open, v, round
    private String dividerOrientation = "h"; // h, v
    private String dividerStyle = "solid"; // solid, dashed, dotted, double
    private String watermarkText = "CONFIDENTIAL";
    private double watermarkOpacity = 0.15;
    private double watermarkAngle = -35.0;
    private String svgSource = "";
    private String iconName = "star";

    /* ---- effects & transforms ---- */
    private boolean shadowEnabled = false;
    private String shadowColor = "#000000";
    private double shadowBlur = 8.0;
    private double shadowOffsetX = 2.0;
    private double shadowOffsetY = 2.0;
    private double shadowOpacity = 0.35;
    private boolean blurEnabled = false;
    private double blurRadius = 4.0;
    private double scaleX = 1.0;
    private double scaleY = 1.0;
    private boolean flipHorizontal = false;
    private boolean flipVertical = false;

    /* ---- binding & conditions ---- */
    private String binding = "";
    private String visibleCondition = "";
    private boolean clipEnabled = false;
    private String clipShape = "none"; // none, circle, rounded_rect
    private String groupId = "";
    private String componentType = "";

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

    public double getLineSpacing() { return lineSpacing; }
    public void setLineSpacing(double lineSpacing) { this.lineSpacing = lineSpacing; }

    public double getLetterSpacing() { return letterSpacing; }
    public void setLetterSpacing(double letterSpacing) { this.letterSpacing = letterSpacing; }

    public double getWordSpacing() { return wordSpacing; }
    public void setWordSpacing(double wordSpacing) { this.wordSpacing = wordSpacing; }

    public String getTextTransform() { return textTransform != null ? textTransform : "none"; }
    public void setTextTransform(String textTransform) { this.textTransform = textTransform; }

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
            case CIRCLE -> { return "Circle"; }
            case ELLIPSE -> { return "Ellipse"; }
            case POLYLINE -> { return "Polyline"; }
            case POLYGON -> { return "Polygon"; }
            case ARC -> { return "Arc Shape"; }
            case PATH -> { return "Custom Path"; }
            case STAR -> { return "Star Shape"; }
            case ARROW -> { return "Arrow"; }
            case DIVIDER -> { return "Divider"; }
            case FREEHAND -> { return "Freehand / Signature"; }
            case WATERMARK -> { return "Watermark"; }
            case SVG -> { return "SVG Vector"; }
            case ICON -> { return "Icon"; }
            case GROUP -> { return "Group"; }
            case COMPONENT -> { return "Component"; }
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

    public String getEffectiveFillColor() {
        if (bg != null && !bg.isBlank()) return bg;
        return "#ffffff";
    }

    public String getEffectiveStrokeColor() {
        if (borderColor != null && !borderColor.isBlank()) return borderColor;
        return "#1a1a1a";
    }

    public double getEffectiveStrokeWidth() {
        return borderWidth > 0 ? borderWidth : 0.5;
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

    /* ---- New universal getters & setters ---- */

    public String getFillType() { return fillType != null ? fillType : "solid"; }
    public void setFillType(String fillType) { this.fillType = fillType; }

    public String getGradientStartColor() { return gradientStartColor != null ? gradientStartColor : "#4f46e5"; }
    public void setGradientStartColor(String gradientStartColor) { this.gradientStartColor = gradientStartColor; }

    public String getGradientEndColor() { return gradientEndColor != null ? gradientEndColor : "#06b6d4"; }
    public void setGradientEndColor(String gradientEndColor) { this.gradientEndColor = gradientEndColor; }

    public double getGradientAngle() { return gradientAngle; }
    public void setGradientAngle(double gradientAngle) { this.gradientAngle = gradientAngle; }

    public double getGradientCenterX() { return gradientCenterX; }
    public void setGradientCenterX(double gradientCenterX) { this.gradientCenterX = gradientCenterX; }

    public double getGradientCenterY() { return gradientCenterY; }
    public void setGradientCenterY(double gradientCenterY) { this.gradientCenterY = gradientCenterY; }

    public double getGradientRadius() { return gradientRadius; }
    public void setGradientRadius(double gradientRadius) { this.gradientRadius = gradientRadius; }

    public boolean isStrokeEnabled() { return strokeEnabled || borderWidth > 0; }
    public void setStrokeEnabled(boolean strokeEnabled) { this.strokeEnabled = strokeEnabled; }

    public String getStrokeType() { return strokeType != null ? strokeType : "centered"; }
    public void setStrokeType(String strokeType) { this.strokeType = strokeType; }

    public String getLineCap() { return lineCap != null ? lineCap : "butt"; }
    public void setLineCap(String lineCap) { this.lineCap = lineCap; }

    public String getLineJoin() { return lineJoin != null ? lineJoin : "miter"; }
    public void setLineJoin(String lineJoin) { this.lineJoin = lineJoin; }

    public String getDashPattern() { return dashPattern != null ? dashPattern : ""; }
    public void setDashPattern(String dashPattern) { this.dashPattern = dashPattern; }

    public double getDashOffset() { return dashOffset; }
    public void setDashOffset(double dashOffset) { this.dashOffset = dashOffset; }

    public Double getTopLeftRadius() { return topLeftRadius != null ? topLeftRadius : borderRadius; }
    public void setTopLeftRadius(Double topLeftRadius) { this.topLeftRadius = topLeftRadius; }

    public Double getTopRightRadius() { return topRightRadius != null ? topRightRadius : borderRadius; }
    public void setTopRightRadius(Double topRightRadius) { this.topRightRadius = topRightRadius; }

    public Double getBottomRightRadius() { return bottomRightRadius != null ? bottomRightRadius : borderRadius; }
    public void setBottomRightRadius(Double bottomRightRadius) { this.bottomRightRadius = bottomRightRadius; }

    public Double getBottomLeftRadius() { return bottomLeftRadius != null ? bottomLeftRadius : borderRadius; }
    public void setBottomLeftRadius(Double bottomLeftRadius) { this.bottomLeftRadius = bottomLeftRadius; }

    public double getRadius() { return radius; }
    public void setRadius(double radius) { this.radius = radius; }

    public double getRadiusX() { return radiusX; }
    public void setRadiusX(double radiusX) { this.radiusX = radiusX; }

    public double getRadiusY() { return radiusY; }
    public void setRadiusY(double radiusY) { this.radiusY = radiusY; }

    public String getPoints() { return points != null ? points : ""; }
    public void setPoints(String points) { this.points = points; }

    public double getStartAngle() { return startAngle; }
    public void setStartAngle(double startAngle) { this.startAngle = startAngle; }

    public double getArcLength() { return arcLength; }
    public void setArcLength(double arcLength) { this.arcLength = arcLength; }

    public String getArcType() { return arcType != null ? arcType : "open"; }
    public void setArcType(String arcType) { this.arcType = arcType; }

    public String getPathData() { return pathData != null ? pathData : ""; }
    public void setPathData(String pathData) { this.pathData = pathData; }

    public int getStarPoints() { return Math.max(3, starPoints); }
    public void setStarPoints(int starPoints) { this.starPoints = starPoints; }

    public double getInnerRadius() { return innerRadius; }
    public void setInnerRadius(double innerRadius) { this.innerRadius = innerRadius; }

    public double getOuterRadius() { return outerRadius; }
    public void setOuterRadius(double outerRadius) { this.outerRadius = outerRadius; }

    public double getArrowShaftWidth() { return arrowShaftWidth; }
    public void setArrowShaftWidth(double arrowShaftWidth) { this.arrowShaftWidth = arrowShaftWidth; }

    public double getArrowHeadLength() { return arrowHeadLength; }
    public void setArrowHeadLength(double arrowHeadLength) { this.arrowHeadLength = arrowHeadLength; }

    public double getArrowHeadWidth() { return arrowHeadWidth; }
    public void setArrowHeadWidth(double arrowHeadWidth) { this.arrowHeadWidth = arrowHeadWidth; }

    public String getArrowHeadStyle() { return arrowHeadStyle != null ? arrowHeadStyle : "triangle"; }
    public void setArrowHeadStyle(String arrowHeadStyle) { this.arrowHeadStyle = arrowHeadStyle; }

    public String getDividerOrientation() { return dividerOrientation != null ? dividerOrientation : "h"; }
    public void setDividerOrientation(String dividerOrientation) { this.dividerOrientation = dividerOrientation; }

    public String getDividerStyle() { return dividerStyle != null ? dividerStyle : "solid"; }
    public void setDividerStyle(String dividerStyle) { this.dividerStyle = dividerStyle; }

    public String getWatermarkText() { return watermarkText != null ? watermarkText : "CONFIDENTIAL"; }
    public void setWatermarkText(String watermarkText) { this.watermarkText = watermarkText; }

    public double getWatermarkOpacity() { return watermarkOpacity; }
    public void setWatermarkOpacity(double watermarkOpacity) { this.watermarkOpacity = watermarkOpacity; }

    public double getWatermarkAngle() { return watermarkAngle; }
    public void setWatermarkAngle(double watermarkAngle) { this.watermarkAngle = watermarkAngle; }

    public String getSvgSource() { return svgSource != null ? svgSource : ""; }
    public void setSvgSource(String svgSource) { this.svgSource = svgSource; }

    public String getIconName() { return iconName != null ? iconName : "star"; }
    public void setIconName(String iconName) { this.iconName = iconName; }

    public boolean isShadowEnabled() { return shadowEnabled; }
    public void setShadowEnabled(boolean shadowEnabled) { this.shadowEnabled = shadowEnabled; }

    public String getShadowColor() { return shadowColor != null ? shadowColor : "#000000"; }
    public void setShadowColor(String shadowColor) { this.shadowColor = shadowColor; }

    public double getShadowBlur() { return shadowBlur; }
    public void setShadowBlur(double shadowBlur) { this.shadowBlur = shadowBlur; }

    public double getShadowOffsetX() { return shadowOffsetX; }
    public void setShadowOffsetX(double shadowOffsetX) { this.shadowOffsetX = shadowOffsetX; }

    public double getShadowOffsetY() { return shadowOffsetY; }
    public void setShadowOffsetY(double shadowOffsetY) { this.shadowOffsetY = shadowOffsetY; }

    public double getShadowOpacity() { return shadowOpacity; }
    public void setShadowOpacity(double shadowOpacity) { this.shadowOpacity = shadowOpacity; }

    public boolean isBlurEnabled() { return blurEnabled; }
    public void setBlurEnabled(boolean blurEnabled) { this.blurEnabled = blurEnabled; }

    public double getBlurRadius() { return blurRadius; }
    public void setBlurRadius(double blurRadius) { this.blurRadius = blurRadius; }

    public double getScaleX() { return scaleX; }
    public void setScaleX(double scaleX) { this.scaleX = scaleX; }

    public double getScaleY() { return scaleY; }
    public void setScaleY(double scaleY) { this.scaleY = scaleY; }

    public boolean isFlipHorizontal() { return flipHorizontal; }
    public void setFlipHorizontal(boolean flipHorizontal) { this.flipHorizontal = flipHorizontal; }

    public boolean isFlipVertical() { return flipVertical; }
    public void setFlipVertical(boolean flipVertical) { this.flipVertical = flipVertical; }

    public String getBinding() { return binding != null ? binding : ""; }
    public void setBinding(String binding) { this.binding = binding; }

    public String getVisibleCondition() { return visibleCondition != null ? visibleCondition : ""; }
    public void setVisibleCondition(String visibleCondition) { this.visibleCondition = visibleCondition; }

    public boolean isClipEnabled() { return clipEnabled; }
    public void setClipEnabled(boolean clipEnabled) { this.clipEnabled = clipEnabled; }

    public String getClipShape() { return clipShape != null ? clipShape : "none"; }
    public void setClipShape(String clipShape) { this.clipShape = clipShape; }

    public String getGroupId() { return groupId != null ? groupId : ""; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getGroupName() { return groupName != null ? groupName : ""; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public boolean isGrouped() { return groupId != null && !groupId.isBlank(); }

    public String getComponentType() { return componentType != null ? componentType : ""; }
    public void setComponentType(String componentType) { this.componentType = componentType; }
}
