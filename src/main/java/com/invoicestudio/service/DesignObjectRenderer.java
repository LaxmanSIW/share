package com.invoicestudio.service;

import com.invoicestudio.model.ElementType;
import com.invoicestudio.model.TemplateElement;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.*;
import javafx.scene.shape.*;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class DesignObjectRenderer {

    public static final double MM_PX = 3.7795275591; // 96 DPI screen pixels per mm

    public static Node render(TemplateElement el, RenderContext ctx, double w, double h) {
        if (el == null) return new Pane();

        Node node = switch (el.getType()) {
            case RECT -> renderRectangle(el, w, h);
            case CIRCLE -> renderCircle(el, w, h);
            case ELLIPSE -> renderEllipse(el, w, h);
            case LINE -> renderLine(el, w, h);
            case POLYLINE -> renderPolyline(el, w, h);
            case POLYGON -> renderPolygon(el, w, h);
            case ARC -> renderArc(el, w, h);
            case PATH, SVG -> renderPath(el, w, h);
            case STAR -> renderStar(el, w, h);
            case ARROW -> renderArrow(el, w, h);
            case DIVIDER -> renderDivider(el, w, h);
            case FREEHAND -> renderFreehand(el, w, h);
            case WATERMARK -> renderWatermark(el, ctx, w, h);
            case TEXT, PAGENO -> renderText(el, ctx, w, h);
            case IMAGE -> renderImage(el, ctx, w, h);
            case QRCODE -> renderQrCode(el, ctx, w, h);
            case BARCODE -> renderBarcode(el, ctx, w, h);
            case ICON -> renderIcon(el, w, h);
            default -> renderRectangle(el, w, h);
        };

        if (node != null) {
            applyEffectsAndTransforms(node, el);
        }
        return node != null ? node : new Pane();
    }

    private static Node renderRectangle(TemplateElement el, double w, double h) {
        if (el.isIndividualBorders()) {
            Region reg = new Region();
            reg.setPrefSize(w, h);
            reg.setMinSize(w, h);
            reg.setMaxSize(w, h);

            String bg = (el.getBg() != null && !el.getBg().isBlank() && !"transparent".equalsIgnoreCase(el.getBg()))
                    ? el.getBg() : "transparent";

            double topW = el.isSideActive("top") ? el.getEffectiveSideWidth("top") * MM_PX : 0;
            double rightW = el.isSideActive("right") ? el.getEffectiveSideWidth("right") * MM_PX : 0;
            double bottomW = el.isSideActive("bottom") ? el.getEffectiveSideWidth("bottom") * MM_PX : 0;
            double leftW = el.isSideActive("left") ? el.getEffectiveSideWidth("left") * MM_PX : 0;

            String topC = el.isSideActive("top") ? el.getEffectiveSideColor("top") : "transparent";
            String rightC = el.isSideActive("right") ? el.getEffectiveSideColor("right") : "transparent";
            String bottomC = el.isSideActive("bottom") ? el.getEffectiveSideColor("bottom") : "transparent";
            String leftC = el.isSideActive("left") ? el.getEffectiveSideColor("left") : "transparent";

            String topS = el.getEffectiveSideStyle("top");
            String rightS = el.getEffectiveSideStyle("right");
            String bottomS = el.getEffectiveSideStyle("bottom");
            String leftS = el.getEffectiveSideStyle("left");

            double r = el.getBorderRadius() > 0 ? el.getBorderRadius() * MM_PX : 0;

            reg.setStyle(String.format(java.util.Locale.US,
                    "-fx-background-color: %s; -fx-background-radius: %.1f; "
                    + "-fx-border-width: %.2f %.2f %.2f %.2f; "
                    + "-fx-border-color: %s %s %s %s; "
                    + "-fx-border-style: %s %s %s %s; "
                    + "-fx-border-radius: %.1f;",
                    bg, r, topW, rightW, bottomW, leftW,
                    topC, rightC, bottomC, leftC,
                    topS, rightS, bottomS, leftS, r));
            return reg;
        } else {
            Rectangle r = new Rectangle(w, h);
            r.setFill(buildPaint(el, w, h));
            applyStroke(r, el);

            double cornerR = el.getBorderRadius() > 0 ? el.getBorderRadius() * MM_PX : 0;
            if (cornerR > 0) {
                r.setArcWidth(cornerR * 2);
                r.setArcHeight(cornerR * 2);
            }
            return r;
        }
    }

    private static Node renderCircle(TemplateElement el, double w, double h) {
        double radiusPx = Math.min(w, h) / 2.0;
        Circle c = new Circle(w / 2.0, h / 2.0, radiusPx);
        c.setFill(buildPaint(el, w, h));
        applyStroke(c, el);
        return c;
    }

    private static Node renderEllipse(TemplateElement el, double w, double h) {
        Ellipse e = new Ellipse(w / 2.0, h / 2.0, w / 2.0, h / 2.0);
        e.setFill(buildPaint(el, w, h));
        applyStroke(e, el);
        return e;
    }

    private static Node renderLine(TemplateElement el, double w, double h) {
        Line l = new Line();
        if ("v".equalsIgnoreCase(el.getDirection())) {
            l.setStartX(w / 2.0); l.setStartY(0);
            l.setEndX(w / 2.0); l.setEndY(h);
        } else {
            l.setStartX(0); l.setStartY(h / 2.0);
            l.setEndX(w); l.setEndY(h / 2.0);
        }
        l.setStroke(parseColorSafe(el.getBorderColor(), Color.web("#1a1a1a")));
        l.setStrokeWidth(Math.max(1, (el.getBorderWidth() > 0 ? el.getBorderWidth() : 0.5) * MM_PX));
        applyStrokeCapJoinDash(l, el);
        return l;
    }

    private static Node renderPolyline(TemplateElement el, double w, double h) {
        Polyline pl = new Polyline();
        parsePoints(el.getPoints(), pl.getPoints(), w, h);
        pl.setFill(Color.TRANSPARENT);
        applyStroke(pl, el);
        return pl;
    }

    private static Node renderPolygon(TemplateElement el, double w, double h) {
        Polygon pg = new Polygon();
        parsePoints(el.getPoints(), pg.getPoints(), w, h);
        pg.setFill(buildPaint(el, w, h));
        applyStroke(pg, el);
        return pg;
    }

    private static Node renderArc(TemplateElement el, double w, double h) {
        Arc arc = new Arc(w / 2.0, h / 2.0, w / 2.0, h / 2.0, el.getStartAngle(), el.getArcLength());
        arc.setType(switch (el.getArcType().toLowerCase()) {
            case "chord" -> ArcType.CHORD;
            case "round" -> ArcType.ROUND;
            default -> ArcType.OPEN;
        });
        arc.setFill(arc.getType() == ArcType.OPEN ? Color.TRANSPARENT : buildPaint(el, w, h));
        applyStroke(arc, el);
        return arc;
    }

    private static Node renderPath(TemplateElement el, double w, double h) {
        String data = el.getSvgSource() != null && !el.getSvgSource().isBlank() ? el.getSvgSource() : el.getPathData();
        if (data != null && data.trim().toLowerCase(java.util.Locale.ROOT).contains("<svg")) {
            return SvgVectorParser.renderToJavaFx(el, w, h);
        }
        if (data == null || data.isBlank()) data = "M 0 0 L " + w + " 0 L " + (w / 2.0) + " " + h + " Z";

        SVGPath path = new SVGPath();
        path.setContent(data);
        path.setFill(buildPaint(el, w, h));
        applyStroke(path, el);

        if (el.getPoints() != null && !el.getPoints().isBlank()) {
            path.getTransforms().add(new javafx.scene.transform.Scale(MM_PX, MM_PX, 0, 0));
        }
        return path;
    }

    private static Node renderStar(TemplateElement el, double w, double h) {
        int n = el.getStarPoints();
        Polygon star = new Polygon();
        double cx = w / 2.0;
        double cy = h / 2.0;
        double rOuter = Math.min(w, h) / 2.0;
        double rInner = rOuter * (el.getInnerRadius() > 0 ? (el.getInnerRadius() / Math.max(1, el.getOuterRadius())) : 0.45);

        double step = Math.PI / n;
        double rot = -Math.PI / 2.0; // Point up

        for (int i = 0; i < 2 * n; i++) {
            double r = (i % 2 == 0) ? rOuter : rInner;
            double angle = rot + i * step;
            star.getPoints().add(cx + r * Math.cos(angle));
            star.getPoints().add(cy + r * Math.sin(angle));
        }

        star.setFill(buildPaint(el, w, h));
        applyStroke(star, el);
        return star;
    }

    private static Node renderArrow(TemplateElement el, double w, double h) {
        Polygon arrow = new Polygon();
        double headL = Math.max(4, el.getArrowHeadLength() * MM_PX);
        double headW = Math.max(4, el.getArrowHeadWidth() * MM_PX);
        double shaftW = Math.max(1, el.getArrowShaftWidth() * MM_PX);

        double cy = h / 2.0;
        double startX = 0;
        double endX = w;

        // Draw horizontal right-pointing arrow scaled to w, h
        double shaftTop = cy - shaftW / 2.0;
        double shaftBottom = cy + shaftW / 2.0;
        double headBaseX = Math.max(0, endX - headL);
        double headTop = cy - headW / 2.0;
        double headBottom = cy + headW / 2.0;

        arrow.getPoints().addAll(
                startX, shaftTop,
                headBaseX, shaftTop,
                headBaseX, headTop,
                endX, cy,
                headBaseX, headBottom,
                headBaseX, shaftBottom,
                startX, shaftBottom
        );

        arrow.setFill(buildPaint(el, w, h));
        applyStroke(arrow, el);
        return arrow;
    }

    private static Node renderDivider(TemplateElement el, double w, double h) {
        Group group = new Group();
        boolean isVert = "v".equalsIgnoreCase(el.getDividerOrientation());
        double thick = Math.max(1, (el.getBorderWidth() > 0 ? el.getBorderWidth() : 0.5) * MM_PX);
        Color col = parseColorSafe(el.getBorderColor(), Color.web("#cbd5e1"));
        String style = el.getDividerStyle().toLowerCase();

        if ("double".equals(style)) {
            double gap = thick * 2;
            if (isVert) {
                Line l1 = new Line(w / 2.0 - gap / 2.0, 0, w / 2.0 - gap / 2.0, h);
                Line l2 = new Line(w / 2.0 + gap / 2.0, 0, w / 2.0 + gap / 2.0, h);
                l1.setStroke(col); l1.setStrokeWidth(thick);
                l2.setStroke(col); l2.setStrokeWidth(thick);
                group.getChildren().addAll(l1, l2);
            } else {
                Line l1 = new Line(0, h / 2.0 - gap / 2.0, w, h / 2.0 - gap / 2.0);
                Line l2 = new Line(0, h / 2.0 + gap / 2.0, w, h / 2.0 + gap / 2.0);
                l1.setStroke(col); l1.setStrokeWidth(thick);
                l2.setStroke(col); l2.setStrokeWidth(thick);
                group.getChildren().addAll(l1, l2);
            }
        } else {
            Line line = new Line(
                    isVert ? w / 2.0 : 0,
                    isVert ? 0 : h / 2.0,
                    isVert ? w / 2.0 : w,
                    isVert ? h : h / 2.0
            );
            line.setStroke(col);
            line.setStrokeWidth(thick);
            if ("dashed".equals(style)) {
                line.getStrokeDashArray().addAll(thick * 3, thick * 2);
            } else if ("dotted".equals(style)) {
                line.setStrokeLineCap(StrokeLineCap.ROUND);
                line.getStrokeDashArray().addAll(thick, thick * 2);
            }
            group.getChildren().add(line);
        }
        return group;
    }

    private static Node renderFreehand(TemplateElement el, double w, double h) {
        Polyline line = new Polyline();
        parsePoints(el.getPoints(), line.getPoints(), w, h);
        line.setFill(Color.TRANSPARENT);
        line.setStroke(parseColorSafe(el.getBorderColor(), Color.web("#1e293b")));
        line.setStrokeWidth(Math.max(1, el.getBorderWidth() * MM_PX));
        line.setStrokeLineCap(StrokeLineCap.ROUND);
        line.setStrokeLineJoin(StrokeLineJoin.ROUND);
        return line;
    }

    private static Node renderWatermark(TemplateElement el, RenderContext ctx, double w, double h) {
        String txt = el.getWatermarkText();
        if (ctx != null) txt = ctx.resolveText(txt);
        Label lbl = new Label(txt);
        lbl.setPrefSize(w, h);
        lbl.setAlignment(Pos.CENTER);
        lbl.setStyle(String.format(java.util.Locale.US,
                "-fx-font-size: %.1fpx; -fx-font-weight: bold; -fx-text-fill: %s; -fx-font-family: '%s';",
                Math.max(16, h * 0.6), el.getColor() != null ? el.getColor() : "#94a3b8",
                el.getFontFamily() != null ? el.getFontFamily() : "Segoe UI"));
        lbl.setRotate(el.getWatermarkAngle());
        lbl.setOpacity(el.getWatermarkOpacity() > 0 ? el.getWatermarkOpacity() : 0.15);
        return lbl;
    }

    private static Node renderText(TemplateElement el, RenderContext ctx, double w, double h) {
        String raw = el.getText() != null ? el.getText() : "";
        String resolved = ctx != null ? ctx.resolveText(raw) : raw;
        resolved = applyTextTransform(resolved, el.getTextTransform(), el.isUppercase());

        String tracked = applyTypographyTracking(resolved, el.getLetterSpacing(), el.getWordSpacing());
        Label lbl = new Label(tracked);
        lbl.setPrefSize(w, h);
        lbl.setMinSize(w, h);
        lbl.setWrapText(true);

        double fontSizePx = el.getFontSize() * 1.333;
        double lhMult = el.getLineHeight() > 0 ? el.getLineHeight() : 1.25;
        // JavaFX default line spacing is 0 (which corresponds to ~1.20x native font leading).
        // Extra spacing in px: (lhMult - 1.20) * fontSizePx + (el.getLineSpacing() * 1.333)
        double effectiveLineSpacing = (lhMult - 1.20) * fontSizePx + (el.getLineSpacing() * 1.333);
        lbl.setLineSpacing(Math.max(-fontSizePx * 0.4, effectiveLineSpacing));

        String colorHex = el.getColor() != null && !el.getColor().isBlank() ? el.getColor() : "#1a1a1a";
        String bgStyle = "";
        if (el.getBg() != null && !el.getBg().isBlank() && !"transparent".equalsIgnoreCase(el.getBg())) {
            bgStyle = "-fx-background-color: " + el.getBg() + ";";
        }
        String borderStyle = "";
        if (el.getBorderWidth() > 0 && el.getBorderColor() != null) {
            borderStyle = "-fx-border-color: " + el.getBorderColor() + "; -fx-border-width: " + (el.getBorderWidth() * MM_PX) + ";";
        }
        if (el.getBorderRadius() > 0) {
            borderStyle += "-fx-background-radius: " + (el.getBorderRadius() * MM_PX) + "; -fx-border-radius: " + (el.getBorderRadius() * MM_PX) + ";";
        }

        int weight = el.getFontWeight() > 0 ? el.getFontWeight() : (el.isBold() ? 700 : 400);
        if (el.isBold() && weight < 700) weight = 700;
        String fw = String.valueOf(weight);
        String fs = el.isItalic() ? "italic" : "normal";
        String family = el.getFontFamily() != null ? el.getFontFamily() : "Segoe UI";

        lbl.setStyle("-fx-text-fill: " + colorHex + "; -fx-fill: " + colorHex +
                "; -fx-font-size: " + (el.getFontSize() * 1.3) + "px; -fx-font-family: '" + family +
                "'; -fx-font-weight: " + fw + "; -fx-font-style: " + fs + "; " + bgStyle + " " + borderStyle);

        lbl.setUnderline(el.isUnderline());

        Pos alignment = Pos.TOP_LEFT;
        if ("center".equalsIgnoreCase(el.getAlign())) alignment = Pos.TOP_CENTER;
        else if ("right".equalsIgnoreCase(el.getAlign())) alignment = Pos.TOP_RIGHT;

        if ("middle".equalsIgnoreCase(el.getVAlign()) || "center".equalsIgnoreCase(el.getVAlign())) {
            if ("center".equalsIgnoreCase(el.getAlign())) alignment = Pos.CENTER;
            else if ("right".equalsIgnoreCase(el.getAlign())) alignment = Pos.CENTER_RIGHT;
            else alignment = Pos.CENTER_LEFT;
        } else if ("bottom".equalsIgnoreCase(el.getVAlign())) {
            if ("center".equalsIgnoreCase(el.getAlign())) alignment = Pos.BOTTOM_CENTER;
            else if ("right".equalsIgnoreCase(el.getAlign())) alignment = Pos.BOTTOM_RIGHT;
            else alignment = Pos.BOTTOM_LEFT;
        }
        lbl.setAlignment(alignment);

        if (el.isStrikethrough()) {
            StackPane sp = new StackPane();
            sp.setPrefSize(w, h);
            sp.setMinSize(w, h);
            sp.setMaxSize(w, h);
            Line strikeLine = new Line(0, 0, Math.max(10, w - 8), 0);
            strikeLine.setStroke(parseColorSafe(colorHex, Color.BLACK));
            strikeLine.setStrokeWidth(Math.max(1.0, el.getFontSize() * 0.08));
            sp.getChildren().addAll(lbl, strikeLine);
            StackPane.setAlignment(strikeLine, Pos.CENTER);
            return sp;
        }

        return lbl;
    }

    public static String applyTypographyTracking(String text, double letterSpacing, double wordSpacing) {
        if (text == null || text.isEmpty()) return "";
        if (letterSpacing <= 0.05 && wordSpacing <= 0.05) return text;

        String letterSpacer = "";
        if (letterSpacing >= 16.0) {
            letterSpacer = "\u2003"; // Em space
        } else if (letterSpacing >= 10.0) {
            letterSpacer = "\u2002"; // En space
        } else if (letterSpacing >= 6.0) {
            letterSpacer = "\u2004"; // 1/3 em
        } else if (letterSpacing >= 3.5) {
            letterSpacer = "\u2005"; // 1/4 em
        } else if (letterSpacing >= 1.8) {
            letterSpacer = "\u2009"; // Thin space
        } else if (letterSpacing >= 0.4) {
            letterSpacer = "\u200A"; // Hair space
        }

        String extraWordSpace = "";
        if (wordSpacing >= 12.0) {
            extraWordSpace = "   ";
        } else if (wordSpacing >= 6.0) {
            extraWordSpace = "  ";
        } else if (wordSpacing >= 1.5) {
            extraWordSpace = " ";
        } else if (wordSpacing >= 0.5) {
            extraWordSpace = "\u2009";
        }

        if (letterSpacer.isEmpty() && extraWordSpace.isEmpty()) {
            return text;
        }

        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\n", -1);
        for (int l = 0; l < lines.length; l++) {
            if (l > 0) sb.append("\n");
            String line = lines[l];
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                sb.append(ch);
                if (i < line.length() - 1) {
                    if (ch == ' ') {
                        if (!extraWordSpace.isEmpty()) {
                            sb.append(extraWordSpace);
                        }
                    } else if (line.charAt(i + 1) != ' ' && !letterSpacer.isEmpty()) {
                        sb.append(letterSpacer);
                    }
                }
            }
        }
        return sb.toString();
    }

    public static String applyTextTransform(String text, String transform, boolean legacyUppercase) {
        if (text == null || text.isEmpty()) return "";
        if (legacyUppercase || "uppercase".equalsIgnoreCase(transform)) {
            return text.toUpperCase(java.util.Locale.ROOT);
        }
        if ("lowercase".equalsIgnoreCase(transform)) {
            return text.toLowerCase(java.util.Locale.ROOT);
        }
        if ("capitalize".equalsIgnoreCase(transform)) {
            return capitalizeText(text);
        }
        return text;
    }

    public static String capitalizeText(String text) {
        if (text == null || text.isBlank()) return text;
        char[] chars = text.toCharArray();
        boolean startWord = true;
        for (int i = 0; i < chars.length; i++) {
            if (Character.isWhitespace(chars[i])) {
                startWord = true;
            } else if (startWord) {
                chars[i] = Character.toTitleCase(chars[i]);
                startWord = false;
            } else {
                chars[i] = Character.toLowerCase(chars[i]);
            }
        }
        return new String(chars);
    }

    private static Node renderImage(TemplateElement el, RenderContext ctx, double w, double h) {
        ImageView iv = new ImageView();
        iv.setFitWidth(w);
        iv.setFitHeight(h);
        iv.setPreserveRatio(!"fill".equalsIgnoreCase(el.getObjectFit()));
        iv.setSmooth(true);

        Image img = null;
        if (el.isUseBusinessLogo() && ctx != null && ctx.getSettings() != null) {
            String logo = ctx.getSettings().getBusiness().getLogo();
            if (logo != null && !logo.isBlank()) {
                img = decodeFxImage(logo);
            }
        } else if (el.getSrc() != null && !el.getSrc().isBlank()) {
            img = decodeFxImage(el.getSrc());
        }

        if (img != null) {
            iv.setImage(img);
        } else {
            try {
                iv.setImage(new Image(DesignObjectRenderer.class.getResourceAsStream("/icons/Invoicewhitebackground.png")));
            } catch (Exception ignored) {}
        }

        StackPane sp = new StackPane(iv);
        sp.setPrefSize(w, h);
        sp.setAlignment(Pos.CENTER);

        if (el.isClipEnabled() && "circle".equalsIgnoreCase(el.getClipShape())) {
            Circle clip = new Circle(w / 2.0, h / 2.0, Math.min(w, h) / 2.0);
            sp.setClip(clip);
        }
        return sp;
    }

    private static Node renderQrCode(TemplateElement el, RenderContext ctx, double w, double h) {
        String payload = ctx != null ? ctx.getQrPayload(el) : "InvoiceStudio-QR";
        int size = (int) Math.min(w, h);
        Image qrImg = BarcodeService.generateQrFxImage(payload, Math.max(20, size * 2));
        ImageView iv = new ImageView(qrImg);
        iv.setFitWidth(size);
        iv.setFitHeight(size);
        iv.setPreserveRatio(true);
        StackPane sp = new StackPane(iv);
        sp.setPrefSize(w, h);
        sp.setAlignment(Pos.CENTER);
        return sp;
    }

    private static Node renderBarcode(TemplateElement el, RenderContext ctx, double w, double h) {
        String payload = ctx != null ? ctx.getBarcodePayload(el) : "INV-0001";
        Image barImg = BarcodeService.generateBarcodeFxImage(payload, (int) (w * 2), (int) (h * 2), el.isBarcodeShowText());
        ImageView iv = new ImageView(barImg);
        iv.setFitWidth(w);
        iv.setFitHeight(h);
        iv.setPreserveRatio(true);
        StackPane sp = new StackPane(iv);
        sp.setPrefSize(w, h);
        sp.setAlignment(Pos.CENTER);
        return sp;
    }

    private static Node renderIcon(TemplateElement el, double w, double h) {
        // Render simple vector icon glyph
        Label lbl = new Label("★");
        String name = el.getIconName().toLowerCase();
        switch (name) {
            case "check" -> lbl.setText("✓");
            case "cross" -> lbl.setText("✕");
            case "phone" -> lbl.setText("☎");
            case "email" -> lbl.setText("✉");
            case "location" -> lbl.setText("📍");
            case "heart" -> lbl.setText("♥");
            case "arrow" -> lbl.setText("➔");
            default -> lbl.setText("★");
        }
        lbl.setPrefSize(w, h);
        lbl.setAlignment(Pos.CENTER);
        lbl.setStyle("-fx-font-size: " + (Math.min(w, h) * 0.7) + "px; -fx-text-fill: "
                + (el.getColor() != null ? el.getColor() : "#f59e0b") + ";");
        return lbl;
    }

    private static Paint buildPaint(TemplateElement el, double w, double h) {
        String type = el.getFillType().toLowerCase();
        if ("none".equals(type) || "transparent".equals(type)) {
            return Color.TRANSPARENT;
        }
        if ("linear".equals(type)) {
            Color start = parseColorSafe(el.getGradientStartColor(), Color.web("#4f46e5"));
            Color end = parseColorSafe(el.getGradientEndColor(), Color.web("#06b6d4"));
            double rad = Math.toRadians(el.getGradientAngle());
            double sx = 0.5 - 0.5 * Math.cos(rad);
            double sy = 0.5 - 0.5 * Math.sin(rad);
            double ex = 0.5 + 0.5 * Math.cos(rad);
            double ey = 0.5 + 0.5 * Math.sin(rad);
            return new LinearGradient(sx, sy, ex, ey, true, CycleMethod.NO_CYCLE,
                    new Stop(0, start), new Stop(1, end));
        }
        if ("radial".equals(type)) {
            Color start = parseColorSafe(el.getGradientStartColor(), Color.web("#4f46e5"));
            Color end = parseColorSafe(el.getGradientEndColor(), Color.web("#06b6d4"));
            return new RadialGradient(0, 0, 0.5, 0.5, 0.5, true, CycleMethod.NO_CYCLE,
                    new Stop(0, start), new Stop(1, end));
        }
        String bg = el.getBg();
        if (bg != null && !bg.isBlank() && !"transparent".equalsIgnoreCase(bg)) {
            return parseColorSafe(bg, Color.TRANSPARENT);
        }
        return Color.TRANSPARENT;
    }

    private static void applyStroke(Shape s, TemplateElement el) {
        if (el.isStrokeEnabled()) {
            s.setStroke(parseColorSafe(el.getBorderColor(), Color.BLACK));
            s.setStrokeWidth(Math.max(0.5, el.getBorderWidth() * MM_PX));
            s.setStrokeType(switch (el.getStrokeType().toLowerCase()) {
                case "inside" -> StrokeType.INSIDE;
                case "outside" -> StrokeType.OUTSIDE;
                default -> StrokeType.CENTERED;
            });
            applyStrokeCapJoinDash(s, el);
        } else {
            s.setStroke(Color.TRANSPARENT);
        }
    }

    private static void applyStrokeCapJoinDash(Shape s, TemplateElement el) {
        s.setStrokeLineCap(switch (el.getLineCap().toLowerCase()) {
            case "round" -> StrokeLineCap.ROUND;
            case "square" -> StrokeLineCap.SQUARE;
            default -> StrokeLineCap.BUTT;
        });
        s.setStrokeLineJoin(switch (el.getLineJoin().toLowerCase()) {
            case "round" -> StrokeLineJoin.ROUND;
            case "bevel" -> StrokeLineJoin.BEVEL;
            default -> StrokeLineJoin.MITER;
        });

        String dash = el.getDashPattern();
        if (dash != null && !dash.isBlank() && !"none".equalsIgnoreCase(dash)) {
            String[] parts = dash.split("[,\\s]+");
            List<Double> pattern = new ArrayList<>();
            for (String p : parts) {
                try {
                    pattern.add(Double.parseDouble(p.trim()) * MM_PX);
                } catch (NumberFormatException ignored) {}
            }
            if (!pattern.isEmpty()) {
                s.getStrokeDashArray().addAll(pattern);
            }
        }
    }

    public static void applyEffectsAndTransforms(Node node, TemplateElement el) {
        if (el.isShadowEnabled()) {
            DropShadow ds = new DropShadow();
            ds.setBlurType(BlurType.GAUSSIAN);
            ds.setColor(parseColorSafe(el.getShadowColor(), Color.BLACK).deriveColor(0, 1, 1, el.getShadowOpacity()));
            ds.setRadius(el.getShadowBlur());
            ds.setOffsetX(el.getShadowOffsetX());
            ds.setOffsetY(el.getShadowOffsetY());
            node.setEffect(ds);
        } else if (el.isBlurEnabled()) {
            node.setEffect(new GaussianBlur(el.getBlurRadius()));
        }

        if (el.getOpacity() < 1.0 && el.getOpacity() >= 0.0) {
            node.setOpacity(el.getOpacity());
        }

        if (el.getRotation() != 0) {
            node.setRotate(el.getRotation());
        }

        if (el.isFlipHorizontal()) {
            node.setScaleX(-el.getScaleX());
        } else if (el.getScaleX() != 1.0) {
            node.setScaleX(el.getScaleX());
        }

        if (el.isFlipVertical()) {
            node.setScaleY(-el.getScaleY());
        } else if (el.getScaleY() != 1.0) {
            node.setScaleY(el.getScaleY());
        }
    }

    private static void parsePoints(String str, List<Double> out, double w, double h) {
        if (str == null || str.isBlank()) {
            out.addAll(List.of(0.0, 0.0, w, 0.0, w / 2.0, h));
            return;
        }
        String[] tokens = str.trim().split("[,\\s]+");
        for (int i = 0; i < tokens.length; i++) {
            try {
                double val = Double.parseDouble(tokens[i]);
                out.add(val * MM_PX);
            } catch (Exception ignored) {}
        }
        if (out.size() < 4) {
            out.clear();
            out.addAll(List.of(0.0, 0.0, w, 0.0, w / 2.0, h));
        }
    }

    public static Color parseColorSafe(String hex, Color fallback) {
        if (hex == null || hex.isBlank()) return fallback;
        try {
            return Color.web(hex.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    public static Image decodeFxImage(String src) {
        if (src == null || src.isBlank()) return null;
        try {
            if (src.startsWith("data:image")) {
                int comma = src.indexOf(",");
                if (comma != -1) {
                    byte[] bytes = Base64.getDecoder().decode(src.substring(comma + 1));
                    return new Image(new ByteArrayInputStream(bytes));
                }
            }
            if (src.startsWith("file:") || src.startsWith("http:") || src.startsWith("https:")) {
                return new Image(src);
            }
            byte[] bytes = Base64.getDecoder().decode(src);
            return new Image(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null;
        }
    }
}
