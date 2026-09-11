package com.invoicestudio.service;

import com.invoicestudio.model.TemplateElement;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SvgVectorParser {

    public static class SvgSubShape {
        public String pathData;
        public String fillColor;
        public String strokeColor;
        public Double strokeWidth;
        public String fillRule;

        public SvgSubShape(String pathData, String fillColor, String strokeColor, Double strokeWidth, String fillRule) {
            this.pathData = pathData;
            this.fillColor = fillColor;
            this.strokeColor = strokeColor;
            this.strokeWidth = strokeWidth;
            this.fillRule = fillRule;
        }
    }

    public static class ParsedSvg {
        public double minX = 0;
        public double minY = 0;
        public double width = 100;
        public double height = 100;
        public boolean hasViewBox = false;
        public final List<SvgSubShape> shapes = new ArrayList<>();
    }

    public static ParsedSvg parseSvg(String content) {
        return parseSvg(content, 100.0, 100.0);
    }

    public static ParsedSvg parseSvg(String content, double defaultW, double defaultH) {
        ParsedSvg result = new ParsedSvg();
        result.width = defaultW > 0 ? defaultW : 100;
        result.height = defaultH > 0 ? defaultH : 100;

        if (content == null || content.isBlank()) {
            return result;
        }

        String raw = content.trim();

        // If not full SVG markup, treat as direct path data string
        if (!raw.toLowerCase(Locale.ROOT).contains("<svg")) {
            result.shapes.add(new SvgSubShape(raw, null, null, null, "nonzero"));
            return result;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
            Element root = doc.getDocumentElement();

            // Extract viewBox
            String vb = root.getAttribute("viewBox");
            if (vb != null && !vb.isBlank()) {
                String[] parts = vb.trim().split("[\\s,]+");
                if (parts.length >= 4) {
                    try {
                        result.minX = Double.parseDouble(parts[0]);
                        result.minY = Double.parseDouble(parts[1]);
                        result.width = Double.parseDouble(parts[2]);
                        result.height = Double.parseDouble(parts[3]);
                        result.hasViewBox = true;
                    } catch (Exception ignored) {}
                }
            } else {
                String wAttr = root.getAttribute("width").replaceAll("[^0-9.]", "");
                String hAttr = root.getAttribute("height").replaceAll("[^0-9.]", "");
                if (!wAttr.isBlank() && !hAttr.isBlank()) {
                    try {
                        result.width = Double.parseDouble(wAttr);
                        result.height = Double.parseDouble(hAttr);
                        result.hasViewBox = true;
                    } catch (Exception ignored) {}
                }
            }

            // Extract paths & shapes recursively
            extractElements(root, result);

        } catch (Exception e) {
            // Fallback: regex extraction of d="..." attributes
            Pattern dPattern = Pattern.compile("d\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
            Matcher matcher = dPattern.matcher(raw);
            while (matcher.find()) {
                String d = matcher.group(1);
                if (!d.isBlank()) {
                    result.shapes.add(new SvgSubShape(d, null, null, null, "nonzero"));
                }
            }
        }

        return result;
    }

    private static void extractElements(Element parent, ParsedSvg result) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) {
                String tag = el.getTagName().toLowerCase(Locale.ROOT);
                Map<String, String> styles = parseStyles(el);

                String fill = styles.getOrDefault("fill", el.getAttribute("fill"));
                String stroke = styles.getOrDefault("stroke", el.getAttribute("stroke"));
                String strokeWStr = styles.getOrDefault("stroke-width", el.getAttribute("stroke-width"));
                String fillRule = styles.getOrDefault("fill-rule", el.getAttribute("fill-rule"));
                Double strokeW = null;
                if (!strokeWStr.isBlank()) {
                    try { strokeW = Double.parseDouble(strokeWStr.replaceAll("[^0-9.]", "")); } catch (Exception ignored) {}
                }

                switch (tag) {
                    case "path" -> {
                        String d = el.getAttribute("d");
                        if (!d.isBlank()) {
                            result.shapes.add(new SvgSubShape(d, fill, stroke, strokeW, fillRule));
                        }
                    }
                    case "rect" -> {
                        double rx = parseDouble(el.getAttribute("x"), 0);
                        double ry = parseDouble(el.getAttribute("y"), 0);
                        double rw = parseDouble(el.getAttribute("width"), 0);
                        double rh = parseDouble(el.getAttribute("height"), 0);
                        if (rw > 0 && rh > 0) {
                            String d = String.format(Locale.US, "M %.2f %.2f h %.2f v %.2f h %.2f Z", rx, ry, rw, rh, -rw);
                            result.shapes.add(new SvgSubShape(d, fill, stroke, strokeW, fillRule));
                        }
                    }
                    case "circle" -> {
                        double cx = parseDouble(el.getAttribute("cx"), 0);
                        double cy = parseDouble(el.getAttribute("cy"), 0);
                        double r = parseDouble(el.getAttribute("r"), 0);
                        if (r > 0) {
                            String d = String.format(Locale.US,
                                    "M %.2f %.2f m -%.2f, 0 a %.2f,%.2f 0 1,0 %.2f,0 a %.2f,%.2f 0 1,0 -%.2f,0",
                                    cx, cy, r, r, r, (r * 2), r, r, (r * 2));
                            result.shapes.add(new SvgSubShape(d, fill, stroke, strokeW, fillRule));
                        }
                    }
                    case "ellipse" -> {
                        double cx = parseDouble(el.getAttribute("cx"), 0);
                        double cy = parseDouble(el.getAttribute("cy"), 0);
                        double rx = parseDouble(el.getAttribute("rx"), 0);
                        double ry = parseDouble(el.getAttribute("ry"), 0);
                        if (rx > 0 && ry > 0) {
                            String d = String.format(Locale.US,
                                    "M %.2f %.2f m -%.2f, 0 a %.2f,%.2f 0 1,0 %.2f,0 a %.2f,%.2f 0 1,0 -%.2f,0",
                                    cx, cy, rx, rx, ry, (rx * 2), rx, ry, (rx * 2));
                            result.shapes.add(new SvgSubShape(d, fill, stroke, strokeW, fillRule));
                        }
                    }
                    case "polygon" -> {
                        String pts = el.getAttribute("points");
                        String d = pointsToPath(pts, true);
                        if (d != null) result.shapes.add(new SvgSubShape(d, fill, stroke, strokeW, fillRule));
                    }
                    case "polyline" -> {
                        String pts = el.getAttribute("points");
                        String d = pointsToPath(pts, false);
                        if (d != null) result.shapes.add(new SvgSubShape(d, fill, stroke, strokeW, fillRule));
                    }
                    case "line" -> {
                        double x1 = parseDouble(el.getAttribute("x1"), 0);
                        double y1 = parseDouble(el.getAttribute("y1"), 0);
                        double x2 = parseDouble(el.getAttribute("x2"), 0);
                        double y2 = parseDouble(el.getAttribute("y2"), 0);
                        String d = String.format(Locale.US, "M %.2f %.2f L %.2f %.2f", x1, y1, x2, y2);
                        result.shapes.add(new SvgSubShape(d, "none", stroke != null && !stroke.isBlank() ? stroke : "#000000", strokeW != null ? strokeW : 1.0, fillRule));
                    }
                    case "g" -> extractElements(el, result);
                }
            }
        }
    }

    private static String pointsToPath(String points, boolean close) {
        if (points == null || points.isBlank()) return null;
        String[] tokens = points.trim().split("[\\s,]+");
        if (tokens.length < 4) return null;
        StringBuilder sb = new StringBuilder();
        sb.append("M ").append(tokens[0]).append(" ").append(tokens[1]);
        for (int i = 2; i < tokens.length - 1; i += 2) {
            sb.append(" L ").append(tokens[i]).append(" ").append(tokens[i + 1]);
        }
        if (close) sb.append(" Z");
        return sb.toString();
    }

    private static Map<String, String> parseStyles(Element el) {
        Map<String, String> map = new HashMap<>();
        String style = el.getAttribute("style");
        if (style != null && !style.isBlank()) {
            for (String part : style.split(";")) {
                int idx = part.indexOf(":");
                if (idx > 0) {
                    map.put(part.substring(0, idx).trim().toLowerCase(Locale.ROOT), part.substring(idx + 1).trim());
                }
            }
        }
        return map;
    }

    private static double parseDouble(String str, double def) {
        if (str == null || str.isBlank()) return def;
        try {
            return Double.parseDouble(str.replaceAll("[^0-9.-]", ""));
        } catch (Exception e) {
            return def;
        }
    }

    /**
     * Renders parsed SVG elements into a JavaFX Node, scaled to fit w and h.
     */
    public static Node renderToJavaFx(TemplateElement el, double targetW, double targetH) {
        String content = el.getSvgSource() != null && !el.getSvgSource().isBlank() ? el.getSvgSource() : el.getPathData();
        ParsedSvg parsed = parseSvg(content, targetW, targetH);

        Group group = new Group();

        if (parsed.shapes.isEmpty()) {
            // Default placeholder vector icon
            SVGPath defPath = new SVGPath();
            defPath.setContent("M 0 0 L " + targetW + " 0 L " + (targetW / 2.0) + " " + targetH + " Z");
            defPath.setFill(Color.web(el.getEffectiveFillColor()));
            group.getChildren().add(defPath);
            return group;
        }

        for (SvgSubShape shape : parsed.shapes) {
            SVGPath path = new SVGPath();
            path.setContent(shape.pathData);

            if ("evenodd".equalsIgnoreCase(shape.fillRule)) {
                path.setFillRule(FillRule.EVEN_ODD);
            }

            // Fill determination
            if (shape.fillColor != null && !shape.fillColor.isBlank()) {
                if ("none".equalsIgnoreCase(shape.fillColor) || "transparent".equalsIgnoreCase(shape.fillColor)) {
                    path.setFill(Color.TRANSPARENT);
                } else {
                    path.setFill(DesignObjectRenderer.parseColorSafe(shape.fillColor, Color.web(el.getEffectiveFillColor())));
                }
            } else {
                path.setFill(Color.web(el.getEffectiveFillColor()));
            }

            // Stroke determination
            if (shape.strokeColor != null && !shape.strokeColor.isBlank() && !"none".equalsIgnoreCase(shape.strokeColor)) {
                path.setStroke(DesignObjectRenderer.parseColorSafe(shape.strokeColor, Color.web(el.getEffectiveStrokeColor())));
                path.setStrokeWidth(shape.strokeWidth != null ? shape.strokeWidth : 1.0);
            } else if (el.isStrokeEnabled() || el.getBorderWidth() > 0) {
                path.setStroke(Color.web(el.getEffectiveStrokeColor()));
                path.setStrokeWidth(el.getEffectiveStrokeWidth());
            }

            group.getChildren().add(path);
        }

        // Apply scale transform to fit target bounds
        if (parsed.hasViewBox && parsed.width > 0 && parsed.height > 0) {
            double sx = targetW / parsed.width;
            double sy = targetH / parsed.height;
            group.getTransforms().add(new Scale(sx, sy));
            if (parsed.minX != 0 || parsed.minY != 0) {
                group.getTransforms().add(new Translate(-parsed.minX, -parsed.minY));
            }
        }

        return group;
    }

    /**
     * Renders parsed SVG vector elements into Java2D Graphics2D for high-res PDF generation.
     */
    public static void renderToGraphics2D(Graphics2D g2, TemplateElement el, double x, double y, double w, double h) {
        String content = el.getSvgSource() != null && !el.getSvgSource().isBlank() ? el.getSvgSource() : el.getPathData();
        ParsedSvg parsed = parseSvg(content, w, h);

        AffineTransform orig = g2.getTransform();

        g2.translate(x, y);

        double sx = (parsed.hasViewBox && parsed.width > 0) ? (w / parsed.width) : 1.0;
        double sy = (parsed.hasViewBox && parsed.height > 0) ? (h / parsed.height) : 1.0;

        if (sx != 1.0 || sy != 1.0) {
            g2.scale(sx, sy);
        }
        if (parsed.minX != 0 || parsed.minY != 0) {
            g2.translate(-parsed.minX, -parsed.minY);
        }

        for (SvgSubShape shape : parsed.shapes) {
            Path2D path = PdfExportService.parseSvgPathToAwt(shape.pathData);
            if (path == null) continue;

            if ("evenodd".equalsIgnoreCase(shape.fillRule)) {
                path.setWindingRule(Path2D.WIND_EVEN_ODD);
            }

            // Fill
            boolean hasFill = true;
            if (shape.fillColor != null) {
                if ("none".equalsIgnoreCase(shape.fillColor) || "transparent".equalsIgnoreCase(shape.fillColor)) {
                    hasFill = false;
                } else {
                    java.awt.Color c = PdfExportService.parseColor(shape.fillColor, null);
                    if (c != null) g2.setColor(c);
                }
            } else {
                java.awt.Color c = PdfExportService.parseColor(el.getEffectiveFillColor(), java.awt.Color.WHITE);
                g2.setColor(c);
            }

            if (hasFill) {
                g2.fill(path);
            }

            // Stroke
            if (shape.strokeColor != null && !shape.strokeColor.isBlank() && !"none".equalsIgnoreCase(shape.strokeColor)) {
                java.awt.Color c = PdfExportService.parseColor(shape.strokeColor, java.awt.Color.BLACK);
                g2.setColor(c);
                float sw = shape.strokeWidth != null ? shape.strokeWidth.floatValue() : 1.0f;
                g2.setStroke(new BasicStroke(sw));
                g2.draw(path);
            } else if (el.isStrokeEnabled() || el.getBorderWidth() > 0) {
                java.awt.Color c = PdfExportService.parseColor(el.getEffectiveStrokeColor(), java.awt.Color.BLACK);
                g2.setColor(c);
                g2.setStroke(PdfExportService.buildStroke2D(el));
                g2.draw(path);
            }
        }

        g2.setTransform(orig);
    }
}
