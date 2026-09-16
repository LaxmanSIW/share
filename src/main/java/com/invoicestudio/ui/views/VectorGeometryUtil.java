package com.invoicestudio.ui.views;

import com.invoicestudio.service.AppLog;
import com.invoicestudio.model.ElementType;
import com.invoicestudio.model.TemplateElement;
import javafx.geometry.Point2D;
import javafx.scene.paint.Color;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.scene.image.Image;
import javax.imageio.ImageIO;
import javafx.embed.swing.SwingFXUtils;
import java.awt.image.BufferedImage;

/**
 * Stateless geometry, color, SVG-path and image-decoding utilities for the
 * Template Designer and its collaborators (skill rule 5.2: extract pure
 * functions first — they carry the least coupling and the most test value).
 *
 * <p>All methods are static and side-effect free. Vector parsing here is
 * directly unit-tested by {@code TemplateDesignerVectorEnhancementsTest}.</p>
 */
final class VectorGeometryUtil {

    /** Compiled once (skill rule 3.1 — never allocate Pattern in a render path). */
    private static final Pattern SVG_TOKEN =
            Pattern.compile("([a-zA-Z])|([-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?)");

    private VectorGeometryUtil() {}

    // ─── Color helpers ─────────────────────────────────────────────────────

    static String colorToHex(Color c) {
        if (c == null) return "#000000";
        return String.format(Locale.US, "#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    static Color hexToColor(String hex, Color def) {
        try {
            if (hex == null || hex.isBlank() || "transparent".equalsIgnoreCase(hex)) return def;
            return Color.web(hex);
        } catch (Exception e) {
            return def;
        }
    }

    // ─── Vector path parsing (unit-tested surface) ─────────────────────────

    static boolean isPointEditable(TemplateElement el) {
        if (el == null) return false;
        ElementType t = el.getType();
        if (t == ElementType.POLYGON || t == ElementType.POLYLINE || t == ElementType.FREEHAND) {
            return el.getPoints() != null && !el.getPoints().isBlank();
        }
        if (t == ElementType.PATH) {
            return (el.getPathData() != null && !el.getPathData().isBlank())
                    || (el.getPoints() != null && !el.getPoints().isBlank());
        }
        if (t == ElementType.SVG) {
            return (el.getSvgSource() != null && !el.getSvgSource().isBlank())
                    || (el.getPoints() != null && !el.getPoints().isBlank());
        }
        return false;
    }

    static boolean isCurved(TemplateElement el) {
        if (el == null) return false;
        if (el.getType() == ElementType.PATH && el.getPathData() != null && !el.getPathData().isBlank()) {
            String d = el.getPathData().toUpperCase(Locale.ROOT);
            return d.contains("C") || d.contains("S") || d.contains("Q") || d.contains("T");
        }
        if (el.getType() == ElementType.SVG && el.getSvgSource() != null && !el.getSvgSource().isBlank()) {
            String s = el.getSvgSource().toUpperCase(Locale.ROOT);
            return s.contains("C") || s.contains("S") || s.contains("Q") || s.contains("T");
        }
        return false;
    }

    /** Parses the editable vertex list of an element (points attr or SVG path data). */
    static List<Point2D> parseElementVertices(TemplateElement el) {
        List<Point2D> pts = new ArrayList<>();
        if (el == null) return pts;

        boolean hasCustomPoints = el.getPoints() != null && !el.getPoints().isBlank()
                && !"0,0 20,40 40,0".equals(el.getPoints().trim());

        if (el.getType() == ElementType.PATH && el.getPathData() != null && !el.getPathData().isBlank() && !hasCustomPoints) {
            pts.addAll(extractPointsFromSvgPath(el.getPathData()));
        } else if (el.getType() == ElementType.SVG && el.getSvgSource() != null && !el.getSvgSource().isBlank() && !hasCustomPoints) {
            pts.addAll(extractPointsFromSvgPath(el.getSvgSource()));
        } else if (el.getPoints() != null && !el.getPoints().isBlank()) {
            String[] tokens = el.getPoints().trim().split("[,\\s]+");
            for (int i = 0; i + 1 < tokens.length; i += 2) {
                try {
                    double x = Double.parseDouble(tokens[i]);
                    double y = Double.parseDouble(tokens[i + 1]);
                    pts.add(new Point2D(x, y));
                } catch (Exception ignored) {
            AppLog.debug(ignored);
                    // malformed pair — skip; partial vertex lists still render
                }
            }
        }
        return pts;
    }

    /** Extracts on-path vertex points from an SVG path string (M/L/H/V/C/S/Q + relatives). */
    static List<Point2D> extractPointsFromSvgPath(String d) {
        List<Point2D> pts = new ArrayList<>();
        if (d == null || d.isBlank()) return pts;
        try {
            Matcher m = SVG_TOKEN.matcher(d);
            List<String> tokens = new ArrayList<>();
            while (m.find()) {
                tokens.add(m.group());
            }
            char cmd = 'M';
            int i = 0;
            double curX = 0, curY = 0;
            while (i < tokens.size()) {
                String tok = tokens.get(i);
                if (Character.isLetter(tok.charAt(0))) {
                    cmd = tok.charAt(0);
                    i++;
                }
                switch (cmd) {
                    case 'M' -> {
                        if (i + 1 < tokens.size()) {
                            curX = Double.parseDouble(tokens.get(i++));
                            curY = Double.parseDouble(tokens.get(i++));
                            pts.add(new Point2D(curX, curY));
                        }
                    }
                    case 'm' -> {
                        if (i + 1 < tokens.size()) {
                            curX += Double.parseDouble(tokens.get(i++));
                            curY += Double.parseDouble(tokens.get(i++));
                            pts.add(new Point2D(curX, curY));
                        }
                    }
                    case 'L' -> {
                        if (i + 1 < tokens.size()) {
                            curX = Double.parseDouble(tokens.get(i++));
                            curY = Double.parseDouble(tokens.get(i++));
                            pts.add(new Point2D(curX, curY));
                        }
                    }
                    case 'l' -> {
                        if (i + 1 < tokens.size()) {
                            curX += Double.parseDouble(tokens.get(i++));
                            curY += Double.parseDouble(tokens.get(i++));
                            pts.add(new Point2D(curX, curY));
                        }
                    }
                    case 'H' -> {
                        if (i < tokens.size()) {
                            curX = Double.parseDouble(tokens.get(i++));
                            pts.add(new Point2D(curX, curY));
                        }
                    }
                    case 'h' -> {
                        if (i < tokens.size()) {
                            curX += Double.parseDouble(tokens.get(i++));
                            pts.add(new Point2D(curX, curY));
                        }
                    }
                    case 'V' -> {
                        if (i < tokens.size()) {
                            curY = Double.parseDouble(tokens.get(i++));
                            pts.add(new Point2D(curX, curY));
                        }
                    }
                    case 'v' -> {
                        if (i < tokens.size()) {
                            curY += Double.parseDouble(tokens.get(i++));
                            pts.add(new Point2D(curX, curY));
                        }
                    }
                    case 'C' -> {
                        if (i + 5 < tokens.size()) {
                            i += 4;
                            curX = Double.parseDouble(tokens.get(i++));
                            curY = Double.parseDouble(tokens.get(i++));
                            pts.add(new Point2D(curX, curY));
                        }
                    }
                    case 'c' -> {
                        if (i + 5 < tokens.size()) {
                            i += 4;
                            curX += Double.parseDouble(tokens.get(i++));
                            curY += Double.parseDouble(tokens.get(i++));
                            pts.add(new Point2D(curX, curY));
                        }
                    }
                    case 'S', 'Q' -> {
                        if (i + 3 < tokens.size()) {
                            i += 2;
                            curX = Double.parseDouble(tokens.get(i++));
                            curY = Double.parseDouble(tokens.get(i++));
                            pts.add(new Point2D(curX, curY));
                        }
                    }
                    case 's', 'q' -> {
                        if (i + 3 < tokens.size()) {
                            i += 2;
                            curX += Double.parseDouble(tokens.get(i++));
                            curY += Double.parseDouble(tokens.get(i++));
                            pts.add(new Point2D(curX, curY));
                        }
                    }
                    case 'Z', 'z' -> {
                        // Closed — no new vertex
                    }
                    default -> i++;
                }
            }
        } catch (Exception ignored) {
            AppLog.debug(ignored);
            // Best-effort parse: a bad path must never break canvas rendering.
        }
        return pts;
    }

    /** Catmull-Rom-style smooth Bezier path through the given points. */
    static String generateSmoothBezierPath(List<Point2D> pts, double tension, boolean closed) {
        if (pts == null || pts.isEmpty()) return "";
        if (pts.size() == 1) {
            Point2D p = pts.get(0);
            return String.format(Locale.US, "M %.2f,%.2f", p.getX(), p.getY());
        }
        if (pts.size() == 2) {
            Point2D p0 = pts.get(0);
            Point2D p1 = pts.get(1);
            return String.format(Locale.US, "M %.2f,%.2f L %.2f,%.2f%s",
                    p0.getX(), p0.getY(), p1.getX(), p1.getY(), closed ? " Z" : "");
        }

        double t = Math.max(0.05, Math.min(1.5, tension));
        double factor = t / 3.0;

        StringBuilder sb = new StringBuilder();
        Point2D p0 = pts.get(0);
        sb.append(String.format(Locale.US, "M %.2f,%.2f", p0.getX(), p0.getY()));

        int n = pts.size();
        int count = closed ? n : n - 1;

        for (int i = 0; i < count; i++) {
            Point2D curr = pts.get(i);
            Point2D next = pts.get((i + 1) % n);

            Point2D prev;
            if (i > 0) {
                prev = pts.get(i - 1);
            } else if (closed) {
                prev = pts.get(n - 1);
            } else {
                prev = new Point2D(curr.getX() - (next.getX() - curr.getX()), curr.getY() - (next.getY() - curr.getY()));
            }

            Point2D nextNext;
            if (i + 2 < n) {
                nextNext = pts.get(i + 2);
            } else if (closed) {
                nextNext = pts.get((i + 2) % n);
            } else {
                nextNext = new Point2D(next.getX() + (next.getX() - curr.getX()), next.getY() + (next.getY() - curr.getY()));
            }

            double cp1x = curr.getX() + factor * (next.getX() - prev.getX());
            double cp1y = curr.getY() + factor * (next.getY() - prev.getY());
            double cp2x = next.getX() - factor * (nextNext.getX() - curr.getX());
            double cp2y = next.getY() - factor * (nextNext.getY() - curr.getY());

            sb.append(String.format(Locale.US, " C %.2f,%.2f %.2f,%.2f %.2f,%.2f",
                    cp1x, cp1y, cp2x, cp2y, next.getX(), next.getY()));
        }

        if (closed) {
            sb.append(" Z");
        }
        return sb.toString();
    }

    /** Writes vertices back to an element, upgrading PATH/SVG types when curved. */
    static void syncVerticesToElement(TemplateElement el, List<Point2D> curPts, boolean curved, double tension) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < curPts.size(); i++) {
            Point2D p = curPts.get(i);
            if (i > 0) sb.append(" ");
            sb.append(String.format(Locale.US, "%.1f,%.1f", p.getX(), p.getY()));
        }
        el.setPoints(sb.toString());
        if (curved) {
            boolean closed = el.getType() != ElementType.POLYLINE && el.getType() != ElementType.FREEHAND;
            String bezierD = generateSmoothBezierPath(curPts, tension, closed);
            el.setPathData(bezierD);
            if (el.getType() == ElementType.SVG) {
                el.setSvgSource(bezierD);
            } else {
                el.setType(ElementType.PATH);
            }
        }
    }

    /** Human-readable label for a raw column key (cached variable labels override built-ins). */
    static String columnKeyToLabel(String key, Map<String, String> cachedVariableLabels) {
        if (key == null) return "";
        String lower = key.toLowerCase(Locale.ROOT).trim();
        String switchResult = switch (lower) {
            case "sr", "index", "#", "s_no", "sno" -> "Sr. No.";
            case "desc", "description", "name", "item_name" -> "Description";
            case "hsn", "sac", "hsn_sac"             -> "HSN / SAC";
            case "qty", "quantity"                    -> "Quantity";
            case "unit"                               -> "Unit";
            case "rate", "price", "unit_price"        -> "Rate / Price";
            case "gst", "tax"                         -> "GST %";
            case "disc", "discount"                   -> "Discount %";
            case "taxable", "taxable_value"           -> "Taxable Value";
            case "amount", "total", "total_amount"    -> "Amount";
            case "batch_no"                           -> "Batch No.";
            case "exp_date"                           -> "Expiry Date";
            case "mrp"                                -> "MRP";
            case "serial_no"                          -> "Serial No.";
            case "part_no"                            -> "Part No.";
            default -> null;
        };
        if (switchResult != null) return switchResult + " (" + key + ")";
        String cached = cachedVariableLabels != null ? cachedVariableLabels.get(lower) : null;
        return cached != null ? cached : key;
    }

    // ─── Image decoding (shared by image/QR/logo property builders) ────────

    /** Decodes a data-URI / file / classpath image source; null when unreadable. */
    static Image decodeFxImage(String src) {
        if (src == null || src.isBlank()) return null;
        try {
            if (src.startsWith("data:image")) {
                int comma = src.indexOf(",");
                if (comma != -1) {
                    byte[] bytes = Base64.getDecoder().decode(src.substring(comma + 1));
                    BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
                    return SwingFXUtils.toFXImage(bi, null);
                }
            } else if (src.startsWith("/") || src.startsWith("classpath:")) {
                String path = src.startsWith("classpath:") ? src.substring(10) : src;
                var in = VectorGeometryUtil.class.getResourceAsStream(path);
                if (in != null) return new Image(in);
            } else {
                File f = new File(src);
                if (f.exists()) return new Image(f.toURI().toString());
            }
        } catch (Exception e) {
            // Unreadable image source — callers render a placeholder.
        }
        return null;
    }

    /** Loads a classpath resource and returns it as a PNG data-URI (or null). */
    static String loadResourceAsBase64(String resourcePath) {
        try (var in = VectorGeometryUtil.class.getResourceAsStream(resourcePath)) {
            if (in != null) {
                byte[] bytes = in.readAllBytes();
                return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
            }
        } catch (Exception ignored) {
            AppLog.debug(ignored);
            // Missing optional resource — callers fall back to built-in defaults.
        }
        return null;
    }
}
