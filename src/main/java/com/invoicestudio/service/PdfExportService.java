package com.invoicestudio.service;

import com.invoicestudio.service.AppLog;
import com.invoicestudio.model.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.*;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint.CycleMethod;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PdfExportService {

    private static final double DPI = 300.0;
    private static final double PX_PER_MM = DPI / 25.4; // ~11.811 px/mm
    private static final double MM_TO_PT = 72.0 / 25.4; // ~2.8346 pt/mm

    public static void exportBillPdf(Bill bill, Template template, Settings settings, File dest, int copies) throws IOException {
        if (template == null) template = PresetTemplates.buildClassic();
        if (settings == null) settings = new Settings();
        int totalCopies = Math.max(1, copies);

        double widthMm = template.getPage() != null ? template.getPage().getWidth() : 210.0;
        double heightMm = template.getPage() != null ? template.getPage().getHeight() : 297.0;

        if (template.getPage() != null && template.getPage().isAutoHeight()) {
            int itemCount = bill != null && bill.getItems() != null ? bill.getItems().size() : 1;
            heightMm = calculateEffectiveHeight(template, itemCount);
        }

        float ptW = (float) (widthMm * MM_TO_PT);
        float ptH = (float) (heightMm * MM_TO_PT);

        int imgW = (int) Math.max(100, Math.round(widthMm * PX_PER_MM));
        int imgH = (int) Math.max(100, Math.round(heightMm * PX_PER_MM));

        try (PDDocument doc = new PDDocument()) {
            for (int copyIdx = 0; copyIdx < totalCopies; copyIdx++) {
                PDPage page = new PDPage(new PDRectangle(ptW, ptH));
                doc.addPage(page);

                BufferedImage bi = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = bi.createGraphics();
                setupGraphicsHints(g2);

                // White background
                g2.setColor(Color.WHITE);
                g2.fillRect(0, 0, imgW, imgH);

                RenderContext ctx = new RenderContext(bill, settings, copyIdx, 1, 1);
                renderTemplateToGraphics(g2, template, bill, settings, ctx, imgW, imgH);

                g2.dispose();

                PDImageXObject pdImage = LosslessFactory.createFromImage(doc, bi);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.OVERWRITE, true, true)) {
                    cs.drawImage(pdImage, 0, 0, ptW, ptH);
                }
            }

            if (dest.getParentFile() != null && !dest.getParentFile().exists()) {
                dest.getParentFile().mkdirs();
            }
            doc.save(dest);
        }
    }

    public static void exportReceiptPdf(Bill bill, BillPayment payment, String receiptNo, Settings settings, File dest) throws IOException {
        if (settings == null) settings = new Settings();
        if (payment == null) {
            payment = new BillPayment("pay_1", bill != null ? bill.getDate() : BillingService.todayISO(),
                    bill != null && bill.getTotals() != null ? bill.getTotals().getGrandTotal() : 0,
                    PaymentMethod.CASH, "", "");
        }
        if (receiptNo == null || receiptNo.isBlank()) {
            receiptNo = "RCP-" + (bill != null ? bill.getBillNo() : "001");
        }

        // A5 size: 148mm x 210mm
        double widthMm = 148.0;
        double heightMm = 210.0;
        float ptW = (float) (widthMm * MM_TO_PT);
        float ptH = (float) (heightMm * MM_TO_PT);

        int imgW = (int) Math.round(widthMm * PX_PER_MM);
        int imgH = (int) Math.round(heightMm * PX_PER_MM);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(ptW, ptH));
            doc.addPage(page);

            BufferedImage bi = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = bi.createGraphics();
            setupGraphicsHints(g2);

            g2.setColor(Color.WHITE);
            g2.fillRect(0, 0, imgW, imgH);

            renderReceiptToGraphics(g2, bill, payment, receiptNo, settings, imgW, imgH);
            g2.dispose();

            PDImageXObject pdImage = LosslessFactory.createFromImage(doc, bi);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page, PDPageContentStream.AppendMode.OVERWRITE, true, true)) {
                cs.drawImage(pdImage, 0, 0, ptW, ptH);
            }

            if (dest.getParentFile() != null && !dest.getParentFile().exists()) {
                dest.getParentFile().mkdirs();
            }
            doc.save(dest);
        }
    }

    private static void setupGraphicsHints(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }

    /** Effective page height for auto-height (thermal roll) templates. Public: reused by the MCP template preview. */
    public static double calculateEffectiveHeight(Template template, int itemCount) {
        double maxBottom = 0;
        double tableY = 0;
        double rowHeight = 7.0;
        boolean hasTable = false;

        for (TemplateElement el : template.getElements()) {
            if (el.getType() == ElementType.TABLE) {
                hasTable = true;
                tableY = el.getY();
                rowHeight = el.getRowHeight() > 0 ? el.getRowHeight() : 7.0;
                double h = 7.0 + (Math.max(1, itemCount) * rowHeight);
                maxBottom = Math.max(maxBottom, tableY + h);
            } else {
                maxBottom = Math.max(maxBottom, el.getY() + el.getH());
            }
        }
        if (hasTable) {
            double shift = Math.max(0, (itemCount - 1) * rowHeight);
            for (TemplateElement el : template.getElements()) {
                if (el.getType() != ElementType.TABLE && el.getY() > tableY + 2) {
                    maxBottom = Math.max(maxBottom, el.getY() + shift + el.getH());
                }
            }
        }
        return Math.max(80.0, maxBottom + 12.0);
    }

    /** Draws a whole template onto a Graphics2D surface. Public: reused by the MCP template preview (headless PNG). */
    public static void renderTemplateToGraphics(Graphics2D g2, Template template, Bill bill, Settings settings,
                                                RenderContext ctx, int imgW, int imgH) {
        List<TemplateElement> elements = new ArrayList<>(template.getElements());
        elements.sort((a, b) -> Integer.compare(a.getZIndex(), b.getZIndex()));

        double offXMm = settings != null ? settings.getPrintOffsetX() : 0;
        double offYMm = settings != null ? settings.getPrintOffsetY() : 0;

        boolean isRoll = template.getPage() != null && template.getPage().isAutoHeight();
        double tableY = 0;
        double shift = 0;
        int itemCount = bill != null && bill.getItems() != null ? bill.getItems().size() : 1;

        if (isRoll) {
            for (TemplateElement el : elements) {
                if (el.getType() == ElementType.TABLE) {
                    tableY = el.getY();
                    double rh = el.getRowHeight() > 0 ? el.getRowHeight() : 7.0;
                    shift = Math.max(0, (itemCount - 1) * rh);
                    break;
                }
            }
        }

        for (TemplateElement el : elements) {
            if (el.isHidden()) continue;
            if (el.isHideWhenBlank() && ctx.isTextBlank(el)) continue;

            double elY = el.getY();
            if (isRoll && shift > 0 && el.getType() != ElementType.TABLE && elY > tableY + 2) {
                elY += shift;
            }

            double x = (el.getX() + offXMm) * PX_PER_MM;
            double y = (elY + offYMm) * PX_PER_MM;
            double w = el.getW() * PX_PER_MM;
            double h = el.getH() * PX_PER_MM;

            if (el.getType() == ElementType.TABLE && isRoll) {
                double rh = el.getRowHeight() > 0 ? el.getRowHeight() : 7.0;
                h = (7.0 + (Math.max(1, itemCount) * rh)) * PX_PER_MM;
            }

            renderSingleElement(g2, el, ctx, bill, settings, x, y, w, h);
        }

        // Status Watermark Stamp (PAID / CANCELLED)
        if (bill != null && (bill.getStatus() == BillStatus.PAID || bill.getStatus() == BillStatus.CANCELLED)) {
            renderStatusStamp(g2, bill.getStatus(), imgW, imgH);
        }
    }

    private static void renderSingleElement(Graphics2D g2, TemplateElement el, RenderContext ctx,
                                             Bill bill, Settings settings, double x, double y, double w, double h) {
        boolean isMono = settings != null && settings.isMonochromePrint();
        if (isMono) {
            TemplateElement mono = el.copy();
            if (mono.getType() == ElementType.TEXT || mono.getType() == ElementType.PAGENO) {
                mono.setColor("#000000");
                if (mono.getBg() != null && !mono.getBg().isBlank() && !"transparent".equalsIgnoreCase(mono.getBg())) {
                    mono.setBg("#ffffff");
                }
            } else if (mono.getType() == ElementType.RECT) {
                if (mono.getBg() != null && !mono.getBg().isBlank() && !"transparent".equalsIgnoreCase(mono.getBg())) {
                    mono.setBg("#ffffff");
                }
                mono.setBorderColor("#000000");
                if (mono.getBorderWidth() <= 0) mono.setBorderWidth(0.5);
            } else if (mono.getType() == ElementType.LINE || mono.getType() == ElementType.DIVIDER) {
                mono.setColor("#000000");
                mono.setBorderColor("#000000");
            }
            el = mono;
        }

        AffineTransform origTx = g2.getTransform();
        Composite origComp = g2.getComposite();
        Shape origClip = g2.getClip();

        if (el.getRotation() != 0) {
            g2.rotate(Math.toRadians(el.getRotation()), x + w / 2.0, y + h / 2.0);
        }

        if (el.isFlipHorizontal() || el.isFlipVertical() || el.getScaleX() != 1.0 || el.getScaleY() != 1.0) {
            double sx = el.isFlipHorizontal() ? -el.getScaleX() : el.getScaleX();
            double sy = el.isFlipVertical() ? -el.getScaleY() : el.getScaleY();
            g2.translate(x + w / 2.0, y + h / 2.0);
            g2.scale(sx, sy);
            g2.translate(-(x + w / 2.0), -(y + h / 2.0));
        }

        if (el.getOpacity() < 1.0 && el.getOpacity() >= 0.0) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) el.getOpacity()));
        }

        if (el.isClipEnabled()) {
            String shape = el.getClipShape() != null ? el.getClipShape().toUpperCase() : "RECTANGLE";
            Shape clipShape;
            if ("CIRCLE".equals(shape)) {
                clipShape = new Ellipse2D.Double(x, y, Math.min(w, h), Math.min(w, h));
            } else if ("ROUNDED_RECT".equals(shape)) {
                double r = el.getBorderRadius() > 0 ? el.getBorderRadius() * PX_PER_MM : Math.min(w, h) * 0.15;
                clipShape = new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2);
            } else {
                clipShape = new Rectangle2D.Double(x, y, w, h);
            }
            g2.clip(clipShape);
        }

        switch (el.getType()) {
            case RECT -> {
                Paint p = buildPaint2D(el, x, y, w, h);
                if (p != null) {
                    g2.setPaint(p);
                    if (el.getBorderRadius() > 0) {
                        double r = el.getBorderRadius() * PX_PER_MM;
                        g2.fill(new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2));
                    } else {
                        g2.fill(new Rectangle2D.Double(x, y, w, h));
                    }
                }
                if (el.isIndividualBorders()) {
                    String[] sides = {"top", "bottom", "left", "right"};
                    for (String side : sides) {
                        if (el.isSideActive(side)) {
                            float bw = (float) Math.max(0.5, el.getEffectiveSideWidth(side) * PX_PER_MM);
                            String style = el.getEffectiveSideStyle(side);
                            BasicStroke stroke;
                            if ("dashed".equalsIgnoreCase(style)) {
                                stroke = new BasicStroke(bw, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, new float[]{bw * 3f, bw * 2f}, 0.0f);
                            } else if ("dotted".equalsIgnoreCase(style)) {
                                stroke = new BasicStroke(bw, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, new float[]{bw, bw * 2f}, 0.0f);
                            } else {
                                stroke = new BasicStroke(bw);
                            }
                            g2.setStroke(stroke);
                            g2.setColor(parseColor(el.getEffectiveSideColor(side), Color.BLACK));
                            if ("top".equals(side)) g2.draw(new Line2D.Double(x, y, x + w, y));
                            else if ("bottom".equals(side)) g2.draw(new Line2D.Double(x, y + h, x + w, y + h));
                            else if ("left".equals(side)) g2.draw(new Line2D.Double(x, y, x, y + h));
                            else if ("right".equals(side)) g2.draw(new Line2D.Double(x + w, y, x + w, y + h));
                        }
                    }
                } else if (el.isStrokeEnabled() || (el.getBorderWidth() > 0 && el.getBorderColor() != null)) {
                    g2.setColor(parseColor(el.getBorderColor(), Color.BLACK));
                    g2.setStroke(buildStroke2D(el));
                    if (el.getBorderRadius() > 0) {
                        double r = el.getBorderRadius() * PX_PER_MM;
                        g2.draw(new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2));
                    } else {
                        g2.draw(new Rectangle2D.Double(x, y, w, h));
                    }
                }
            }
            case CIRCLE -> {
                double rad = Math.min(w, h);
                double cx = x + (w - rad) / 2.0;
                double cy = y + (h - rad) / 2.0;
                Ellipse2D.Double circle = new Ellipse2D.Double(cx, cy, rad, rad);
                Paint p = buildPaint2D(el, cx, cy, rad, rad);
                if (p != null) {
                    g2.setPaint(p);
                    g2.fill(circle);
                }
                if (el.isStrokeEnabled() || el.getBorderWidth() > 0) {
                    g2.setColor(parseColor(el.getBorderColor(), Color.BLACK));
                    g2.setStroke(buildStroke2D(el));
                    g2.draw(circle);
                }
            }
            case ELLIPSE -> {
                Ellipse2D.Double ellipse = new Ellipse2D.Double(x, y, w, h);
                Paint p = buildPaint2D(el, x, y, w, h);
                if (p != null) {
                    g2.setPaint(p);
                    g2.fill(ellipse);
                }
                if (el.isStrokeEnabled() || el.getBorderWidth() > 0) {
                    g2.setColor(parseColor(el.getBorderColor(), Color.BLACK));
                    g2.setStroke(buildStroke2D(el));
                    g2.draw(ellipse);
                }
            }
            case LINE -> {
                g2.setColor(parseColor(el.getBorderColor(), Color.BLACK));
                g2.setStroke(buildStroke2D(el));
                if ("v".equalsIgnoreCase(el.getDirection())) {
                    g2.draw(new Line2D.Double(x + w / 2.0, y, x + w / 2.0, y + h));
                } else {
                    g2.draw(new Line2D.Double(x, y + h / 2.0, x + w, y + h / 2.0));
                }
            }
            case POLYLINE -> {
                Path2D.Double pl = parsePoints2D(el.getPoints(), x, y, w, h);
                g2.setColor(parseColor(el.getBorderColor(), Color.BLACK));
                g2.setStroke(buildStroke2D(el));
                g2.draw(pl);
            }
            case POLYGON -> {
                Path2D.Double pg = parsePoints2D(el.getPoints(), x, y, w, h);
                pg.closePath();
                Paint p = buildPaint2D(el, x, y, w, h);
                if (p != null) {
                    g2.setPaint(p);
                    g2.fill(pg);
                }
                if (el.isStrokeEnabled() || el.getBorderWidth() > 0) {
                    g2.setColor(parseColor(el.getBorderColor(), Color.BLACK));
                    g2.setStroke(buildStroke2D(el));
                    g2.draw(pg);
                }
            }
            case ARC -> {
                int type = switch (el.getArcType().toLowerCase()) {
                    case "chord" -> Arc2D.CHORD;
                    case "round" -> Arc2D.PIE;
                    default -> Arc2D.OPEN;
                };
                Arc2D.Double arc = new Arc2D.Double(x, y, w, h, el.getStartAngle(), el.getArcLength(), type);
                if (type != Arc2D.OPEN) {
                    Paint p = buildPaint2D(el, x, y, w, h);
                    if (p != null) {
                        g2.setPaint(p);
                        g2.fill(arc);
                    }
                }
                if (el.isStrokeEnabled() || el.getBorderWidth() > 0) {
                    g2.setColor(parseColor(el.getBorderColor(), Color.BLACK));
                    g2.setStroke(buildStroke2D(el));
                    g2.draw(arc);
                }
            }
            case PATH, SVG -> {
                String d = el.getSvgSource() != null && !el.getSvgSource().isBlank() ? el.getSvgSource() : el.getPathData();
                if (d != null && d.trim().toLowerCase(java.util.Locale.ROOT).contains("<svg")) {
                    SvgVectorParser.renderToGraphics2D(g2, el, x, y, w, h);
                } else {
                    Path2D.Double path = parseSvgPathToAwt(d, x, y, PX_PER_MM);
                    Paint p = buildPaint2D(el, x, y, w, h);
                    if (p != null) {
                        g2.setPaint(p);
                        g2.fill(path);
                    }
                    if (el.isStrokeEnabled() || el.getBorderWidth() > 0) {
                        g2.setColor(parseColor(el.getBorderColor(), Color.BLACK));
                        g2.setStroke(buildStroke2D(el));
                        g2.draw(path);
                    }
                }
            }
            case STAR -> {
                double ratio = el.getInnerRadius() > 0 ? (el.getInnerRadius() / Math.max(1, el.getOuterRadius())) : 0.45;
                Path2D.Double star = createStar2D(x, y, w, h, el.getStarPoints(), ratio);
                Paint p = buildPaint2D(el, x, y, w, h);
                if (p != null) {
                    g2.setPaint(p);
                    g2.fill(star);
                }
                if (el.isStrokeEnabled() || el.getBorderWidth() > 0) {
                    g2.setColor(parseColor(el.getBorderColor(), Color.BLACK));
                    g2.setStroke(buildStroke2D(el));
                    g2.draw(star);
                }
            }
            case ARROW -> {
                double shaftW = Math.max(1, el.getArrowShaftWidth() * PX_PER_MM);
                double headL = Math.max(4, el.getArrowHeadLength() * PX_PER_MM);
                double headW = Math.max(4, el.getArrowHeadWidth() * PX_PER_MM);
                Path2D.Double arrow = createArrow2D(x, y, w, h, shaftW, headL, headW);
                Paint p = buildPaint2D(el, x, y, w, h);
                if (p != null) {
                    g2.setPaint(p);
                    g2.fill(arrow);
                }
                if (el.isStrokeEnabled() || el.getBorderWidth() > 0) {
                    g2.setColor(parseColor(el.getBorderColor(), Color.BLACK));
                    g2.setStroke(buildStroke2D(el));
                    g2.draw(arrow);
                }
            }
            case DIVIDER -> {
                boolean isVert = "v".equalsIgnoreCase(el.getDividerOrientation());
                g2.setColor(parseColor(el.getBorderColor(), new Color(203, 213, 225)));
                g2.setStroke(buildStroke2D(el));
                if (isVert) {
                    g2.draw(new Line2D.Double(x + w / 2.0, y, x + w / 2.0, y + h));
                } else {
                    g2.draw(new Line2D.Double(x, y + h / 2.0, x + w, y + h / 2.0));
                }
            }
            case FREEHAND -> {
                Path2D.Double fh = parsePoints2D(el.getPoints(), x, y, w, h);
                g2.setColor(parseColor(el.getBorderColor(), Color.BLACK));
                g2.setStroke(buildStroke2D(el));
                g2.draw(fh);
            }
            case WATERMARK -> {
                String wm = el.getWatermarkText();
                if (ctx != null) wm = ctx.resolveText(wm);
                AffineTransform wmTx = g2.getTransform();
                g2.rotate(Math.toRadians(el.getWatermarkAngle()), x + w / 2.0, y + h / 2.0);
                g2.setColor(parseColor(el.getColor(), new Color(148, 163, 184)));
                int fontSizePx = (int) Math.max(16, Math.round(h * 0.5));
                g2.setFont(new Font("SansSerif", Font.BOLD, fontSizePx));
                Composite wmComp = g2.getComposite();
                float wmAlpha = (float) (el.getWatermarkOpacity() > 0 ? el.getWatermarkOpacity() : 0.15f);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, wmAlpha));
                drawCellText(g2, wm, x, y, w, h, "center");
                g2.setComposite(wmComp);
                g2.setTransform(wmTx);
            }
            case ICON -> {
                String glyph = switch (el.getIconName().toLowerCase()) {
                    case "check" -> "✓";
                    case "cross" -> "✕";
                    case "phone" -> "☎";
                    case "email" -> "✉";
                    case "location" -> "📍";
                    case "heart" -> "♥";
                    case "arrow" -> "➔";
                    default -> "★";
                };
                g2.setColor(parseColor(el.getColor(), new Color(245, 158, 11)));
                int iconSize = (int) Math.max(12, Math.min(w, h) * 0.7);
                g2.setFont(new Font("SansSerif", Font.BOLD, iconSize));
                drawCellText(g2, glyph, x, y, w, h, "center");
            }
            case TEXT, PAGENO -> {
                // Background & Border
                if (el.getBg() != null && !el.getBg().isBlank() && !"transparent".equalsIgnoreCase(el.getBg())) {
                    g2.setColor(parseColor(el.getBg(), Color.WHITE));
                    if (el.getBorderRadius() > 0) {
                        double r = el.getBorderRadius() * PX_PER_MM;
                        g2.fill(new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2));
                    } else {
                        g2.fill(new Rectangle2D.Double(x, y, w, h));
                    }
                }
                if (el.getBorderWidth() > 0 && el.getBorderColor() != null) {
                    g2.setColor(parseColor(el.getBorderColor(), Color.BLACK));
                    float bw = (float) Math.max(1, el.getBorderWidth() * PX_PER_MM);
                    g2.setStroke(new BasicStroke(bw));
                    if (el.getBorderRadius() > 0) {
                        double r = el.getBorderRadius() * PX_PER_MM;
                        g2.draw(new RoundRectangle2D.Double(x, y, w, h, r * 2, r * 2));
                    } else {
                        g2.draw(new Rectangle2D.Double(x, y, w, h));
                    }
                }

                String text = ctx.resolveText(el.getText());
                text = DesignObjectRenderer.applyTextTransform(text, el.getTextTransform(), el.isUppercase());

                int fontStyle = Font.PLAIN;
                if (el.getFontWeight() >= 700 || el.isBold()) fontStyle |= Font.BOLD;
                if (el.isItalic()) fontStyle |= Font.ITALIC;

                String fontName = el.getFontFamily() != null ? el.getFontFamily() : "SansSerif";
                int fontSizePx = (int) Math.max(8, Math.round(el.getFontSize() * (DPI / 72.0)));
                Font font = new Font(fontName, fontStyle, fontSizePx);
                Map<java.awt.font.TextAttribute, Object> attr = new HashMap<>();
                if (el.getLetterSpacing() != 0) {
                    attr.put(java.awt.font.TextAttribute.TRACKING, el.getLetterSpacing() / 10.0);
                }
                if (el.isUnderline()) {
                    attr.put(java.awt.font.TextAttribute.UNDERLINE, java.awt.font.TextAttribute.UNDERLINE_ON);
                }
                if (el.isStrikethrough()) {
                    attr.put(java.awt.font.TextAttribute.STRIKETHROUGH, java.awt.font.TextAttribute.STRIKETHROUGH_ON);
                }
                if (el.getFontWeight() > 0) {
                    float awtWeight = switch (Math.min(9, Math.max(1, el.getFontWeight() / 100))) {
                        case 1 -> java.awt.font.TextAttribute.WEIGHT_EXTRA_LIGHT;
                        case 2 -> java.awt.font.TextAttribute.WEIGHT_LIGHT;
                        case 3 -> java.awt.font.TextAttribute.WEIGHT_DEMILIGHT;
                        case 4 -> java.awt.font.TextAttribute.WEIGHT_REGULAR;
                        case 5 -> java.awt.font.TextAttribute.WEIGHT_MEDIUM;
                        case 6 -> java.awt.font.TextAttribute.WEIGHT_SEMIBOLD;
                        case 7 -> java.awt.font.TextAttribute.WEIGHT_BOLD;
                        case 8 -> java.awt.font.TextAttribute.WEIGHT_EXTRABOLD;
                        case 9 -> java.awt.font.TextAttribute.WEIGHT_ULTRABOLD;
                        default -> java.awt.font.TextAttribute.WEIGHT_REGULAR;
                    };
                    attr.put(java.awt.font.TextAttribute.WEIGHT, awtWeight);
                }
                if (!attr.isEmpty()) {
                    font = font.deriveFont(attr);
                }
                g2.setFont(font);
                g2.setColor(parseColor(el.getColor(), Color.BLACK));

                drawWrappedText(g2, text, x, y, w, h, el.getAlign(), el.getVAlign(), el.getLineHeight(), el.getLineSpacing(), el.getWordSpacing());
            }
            case IMAGE -> {
                BufferedImage img = null;
                if (el.isUseBusinessLogo()) {
                    String logo = settings != null && settings.getBusiness() != null ? settings.getBusiness().getLogo() : null;
                    if (logo != null && !logo.isBlank()) {
                        img = decodeBase64Image(logo);
                    }
                } else if (el.getSrc() != null && !el.getSrc().isBlank()) {
                    img = decodeBase64Image(el.getSrc());
                }

                if (img != null) {
                    double drawW = w;
                    double drawH = h;
                    double drawX = x;
                    double drawY = y;

                    if (!"fill".equalsIgnoreCase(el.getObjectFit())) {
                        double imgAspect = (double) img.getWidth() / (double) img.getHeight();
                        double boxAspect = w / h;
                        if (imgAspect > boxAspect) {
                            drawW = w;
                            drawH = w / imgAspect;
                            drawY = y + (h - drawH) / 2.0;
                        } else {
                            drawH = h;
                            drawW = h * imgAspect;
                            drawX = x + (w - drawW) / 2.0;
                        }
                    }
                    g2.drawImage(img, (int) Math.round(drawX), (int) Math.round(drawY), (int) Math.round(drawW), (int) Math.round(drawH), null);
                }
            }
            case QRCODE -> {
                String payload = ctx.getQrPayload(el);
                int qrSize = (int) Math.min(w, h);
                BufferedImage qr = BarcodeService.generateQrBufferedImage(payload, qrSize);
                if (qr != null) {
                    g2.drawImage(qr, (int) Math.round(x + (w - qrSize) / 2.0), (int) Math.round(y + (h - qrSize) / 2.0), qrSize, qrSize, null);
                }
            }
            case BARCODE -> {
                String payload = ctx.getBarcodePayload(el);
                BufferedImage bar = BarcodeService.generateBarcodeBufferedImage(payload, (int) Math.round(w), (int) Math.round(h), el.isBarcodeShowText(), el.getBarcodeFormat());
                if (bar != null) {
                    g2.drawImage(bar, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), null);
                }
            }
            case TABLE -> {
                renderTableToGraphics(g2, el, bill, settings, x, y, w, h);
            }
        }

        g2.setClip(origClip);
        g2.setComposite(origComp);
        g2.setTransform(origTx);
    }

    private static Paint buildPaint2D(TemplateElement el, double x, double y, double w, double h) {
        String type = el.getFillType().toLowerCase();
        if ("none".equals(type) || "transparent".equals(type)) {
            return null;
        }
        if ("linear".equals(type)) {
            Color start = parseColor(el.getGradientStartColor(), new Color(79, 70, 229));
            Color end = parseColor(el.getGradientEndColor(), new Color(6, 182, 212));
            double rad = Math.toRadians(el.getGradientAngle());
            float x1 = (float) (x + w * (0.5 - 0.5 * Math.cos(rad)));
            float y1 = (float) (y + h * (0.5 - 0.5 * Math.sin(rad)));
            float x2 = (float) (x + w * (0.5 + 0.5 * Math.cos(rad)));
            float y2 = (float) (y + h * (0.5 + 0.5 * Math.sin(rad)));
            if (Point2D.distance(x1, y1, x2, y2) < 0.1) {
                x2 = x1 + 1.0f;
            }
            return new LinearGradientPaint(x1, y1, x2, y2, new float[]{0.0f, 1.0f}, new Color[]{start, end});
        }
        if (el.getBg() != null && !el.getBg().isBlank() && !"transparent".equalsIgnoreCase(el.getBg())) {
            return parseColor(el.getBg(), Color.WHITE);
        }
        return null;
    }

    public static BasicStroke buildStroke2D(TemplateElement el) {
        float bw = (float) Math.max(0.5, (el.getBorderWidth() > 0 ? el.getBorderWidth() : 0.5) * PX_PER_MM);
        int cap = switch (el.getLineCap().toLowerCase()) {
            case "round" -> BasicStroke.CAP_ROUND;
            case "square" -> BasicStroke.CAP_SQUARE;
            default -> BasicStroke.CAP_BUTT;
        };
        int join = switch (el.getLineJoin().toLowerCase()) {
            case "round" -> BasicStroke.JOIN_ROUND;
            case "bevel" -> BasicStroke.JOIN_BEVEL;
            default -> BasicStroke.JOIN_MITER;
        };

        String dash = el.getDashPattern();
        if (dash != null && !dash.isBlank() && !"none".equalsIgnoreCase(dash)) {
            String[] parts = dash.split("[,\\s]+");
            List<Float> list = new ArrayList<>();
            for (String p : parts) {
                try {
                    list.add((float) (Double.parseDouble(p.trim()) * PX_PER_MM));
                } catch (Exception ignored) {
            AppLog.debug(ignored); }
            }
            if (!list.isEmpty()) {
                float[] dashes = new float[list.size()];
                for (int i = 0; i < list.size(); i++) dashes[i] = list.get(i);
                return new BasicStroke(bw, cap, join, 10.0f, dashes, 0.0f);
            }
        }
        return new BasicStroke(bw, cap, join);
    }

    private static Path2D.Double parsePoints2D(String str, double x, double y, double w, double h) {
        Path2D.Double path = new Path2D.Double();
        if (str == null || str.isBlank()) {
            path.moveTo(x, y);
            path.lineTo(x + w, y);
            path.lineTo(x + w / 2.0, y + h);
            path.closePath();
            return path;
        }
        String[] tokens = str.trim().split("[,\\s]+");
        List<Double> coords = new ArrayList<>();
        for (String t : tokens) {
            try { coords.add(Double.parseDouble(t) * PX_PER_MM); } catch (Exception ignored) {
            AppLog.debug(ignored); }
        }
        if (coords.size() >= 4) {
            path.moveTo(x + coords.get(0), y + coords.get(1));
            for (int i = 2; i + 1 < coords.size(); i += 2) {
                path.lineTo(x + coords.get(i), y + coords.get(i + 1));
            }
        } else {
            path.moveTo(x, y);
            path.lineTo(x + w, y);
            path.lineTo(x + w / 2.0, y + h);
            path.closePath();
        }
        return path;
    }

    private static Path2D.Double createStar2D(double x, double y, double w, double h, int points, double innerRatio) {
        Path2D.Double p = new Path2D.Double();
        int n = Math.max(3, points);
        double cx = x + w / 2.0;
        double cy = y + h / 2.0;
        double rOuter = Math.min(w, h) / 2.0;
        double rInner = rOuter * innerRatio;
        double step = Math.PI / n;
        double rot = -Math.PI / 2.0;

        for (int i = 0; i < 2 * n; i++) {
            double r = (i % 2 == 0) ? rOuter : rInner;
            double angle = rot + i * step;
            double px = cx + r * Math.cos(angle);
            double py = cy + r * Math.sin(angle);
            if (i == 0) p.moveTo(px, py);
            else p.lineTo(px, py);
        }
        p.closePath();
        return p;
    }

    private static Path2D.Double createArrow2D(double x, double y, double w, double h, double shaftWidth, double headLength, double headWidth) {
        Path2D.Double p = new Path2D.Double();
        double cy = y + h / 2.0;
        double startX = x;
        double endX = x + w;

        double shaftTop = cy - shaftWidth / 2.0;
        double shaftBottom = cy + shaftWidth / 2.0;
        double headBaseX = Math.max(x, endX - headLength);
        double headTop = cy - headWidth / 2.0;
        double headBottom = cy + headWidth / 2.0;

        p.moveTo(startX, shaftTop);
        p.lineTo(headBaseX, shaftTop);
        p.lineTo(headBaseX, headTop);
        p.lineTo(endX, cy);
        p.lineTo(headBaseX, headBottom);
        p.lineTo(headBaseX, shaftBottom);
        p.lineTo(startX, shaftBottom);
        p.closePath();
        return p;
    }

    public static Path2D.Double parseSvgPathToAwt(String d) {
        return parseSvgPathToAwt(d, 0.0, 0.0, 1.0);
    }

    public static Path2D.Double parseSvgPathToAwt(String d, double offsetX, double offsetY, double scale) {
        Path2D.Double p = new Path2D.Double();
        if (d == null || d.isBlank()) return p;
        try {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("([a-zA-Z])|([-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?)").matcher(d);
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
                            curX = Double.parseDouble(tokens.get(i++)) * scale;
                            curY = Double.parseDouble(tokens.get(i++)) * scale;
                            p.moveTo(offsetX + curX, offsetY + curY);
                        }
                    }
                    case 'm' -> {
                        if (i + 1 < tokens.size()) {
                            curX += Double.parseDouble(tokens.get(i++)) * scale;
                            curY += Double.parseDouble(tokens.get(i++)) * scale;
                            p.moveTo(offsetX + curX, offsetY + curY);
                        }
                    }
                    case 'L' -> {
                        if (i + 1 < tokens.size()) {
                            curX = Double.parseDouble(tokens.get(i++)) * scale;
                            curY = Double.parseDouble(tokens.get(i++)) * scale;
                            p.lineTo(offsetX + curX, offsetY + curY);
                        }
                    }
                    case 'l' -> {
                        if (i + 1 < tokens.size()) {
                            curX += Double.parseDouble(tokens.get(i++)) * scale;
                            curY += Double.parseDouble(tokens.get(i++)) * scale;
                            p.lineTo(offsetX + curX, offsetY + curY);
                        }
                    }
                    case 'H' -> {
                        if (i < tokens.size()) {
                            curX = Double.parseDouble(tokens.get(i++)) * scale;
                            p.lineTo(offsetX + curX, offsetY + curY);
                        }
                    }
                    case 'h' -> {
                        if (i < tokens.size()) {
                            curX += Double.parseDouble(tokens.get(i++)) * scale;
                            p.lineTo(offsetX + curX, offsetY + curY);
                        }
                    }
                    case 'V' -> {
                        if (i < tokens.size()) {
                            curY = Double.parseDouble(tokens.get(i++)) * scale;
                            p.lineTo(offsetX + curX, offsetY + curY);
                        }
                    }
                    case 'v' -> {
                        if (i < tokens.size()) {
                            curY += Double.parseDouble(tokens.get(i++)) * scale;
                            p.lineTo(offsetX + curX, offsetY + curY);
                        }
                    }
                    case 'C' -> {
                        if (i + 5 < tokens.size()) {
                            double x1 = Double.parseDouble(tokens.get(i++)) * scale;
                            double y1 = Double.parseDouble(tokens.get(i++)) * scale;
                            double x2 = Double.parseDouble(tokens.get(i++)) * scale;
                            double y2 = Double.parseDouble(tokens.get(i++)) * scale;
                            curX = Double.parseDouble(tokens.get(i++)) * scale;
                            curY = Double.parseDouble(tokens.get(i++)) * scale;
                            p.curveTo(offsetX + x1, offsetY + y1, offsetX + x2, offsetY + y2, offsetX + curX, offsetY + curY);
                        }
                    }
                    case 'Q' -> {
                        if (i + 3 < tokens.size()) {
                            double x1 = Double.parseDouble(tokens.get(i++)) * scale;
                            double y1 = Double.parseDouble(tokens.get(i++)) * scale;
                            curX = Double.parseDouble(tokens.get(i++)) * scale;
                            curY = Double.parseDouble(tokens.get(i++)) * scale;
                            p.quadTo(offsetX + x1, offsetY + y1, offsetX + curX, offsetY + curY);
                        }
                    }
                    case 'Z', 'z' -> {
                        p.closePath();
                    }
                    default -> i++;
                }
            }
        } catch (Exception ignored) {
            AppLog.debug(ignored); }
        return p;
    }

    private static void renderTableToGraphics(Graphics2D g2, TemplateElement el, Bill bill, Settings settings,
                                              double x, double y, double w, double h) {
        List<TableColumn> cols = el.getColumns();
        if (cols == null || cols.isEmpty()) cols = PresetTemplates.defaultItemColumns();

        boolean isMono = settings != null && settings.isMonochromePrint();
        Color headerBg = isMono ? Color.WHITE : parseColor(el.getHeaderBg(), new Color(239, 233, 219));
        Color headerColor = isMono ? Color.BLACK : parseColor(el.getHeaderColor(), Color.BLACK);
        Color borderColor = isMono ? Color.BLACK : parseColor(el.getTableBorderColor(), new Color(200, 200, 200));
        Color rowBgColor = isMono ? Color.WHITE : parseColor(el.getRowBg(), Color.WHITE);
        Color rowTextColor = isMono ? Color.BLACK : parseColor(el.getRowColor(), new Color(26, 26, 26));
        Color zebraBgColor = isMono ? Color.WHITE : parseColor(el.getZebraColor(), new Color(248, 248, 248));

        // Border skin: grid = all cell lines, rows = horizontal only,
        // outline = outer frame only, none = no borders at all
        String bStyle = el.getBorderStyle(); // grid, rows, outline, none
        boolean drawOuter = !"none".equals(bStyle);
        boolean innerLines = "grid".equals(bStyle) || "rows".equals(bStyle);
        boolean gridLines = "grid".equals(bStyle);
        float strokePx = (float) Math.max(0.5, (el.getTableBorderWidth() > 0 ? el.getTableBorderWidth() : 0.26) * PX_PER_MM);
        BasicStroke borderStroke = new BasicStroke(strokePx);

        double rowHeightPx = (el.getRowHeight() > 0 ? el.getRowHeight() : 7.0) * PX_PER_MM;
        double headerHeightPx = Math.max(22 * (DPI / 96.0), rowHeightPx);

        // Font scale — keeps legacy templates identical (7.5pt → scale 1) while
        // making the Font Size property actually affect the printed table
        double fScale = el.tableFontScale();

        // Header Background
        g2.setColor(headerBg);
        g2.fill(new Rectangle2D.Double(x, y, w, headerHeightPx));

        // Header Text
        Font headerFont = new Font("SansSerif", Font.BOLD, (int) Math.max(6, 9 * fScale * (DPI / 72.0)));
        Font dataFont = new Font("SansSerif", Font.PLAIN, (int) Math.max(6, 8.5 * fScale * (DPI / 72.0)));

        g2.setColor(borderColor);
        g2.setStroke(borderStroke);

        double colX = x;
        for (int ci = 0; ci < cols.size(); ci++) {
            TableColumn c = cols.get(ci);
            double cW = (c.getWidth() / 100.0) * w;
            g2.setFont(headerFont);
            g2.setColor(headerColor);
            drawCellText(g2, c.getLabel(), colX + 4 * (DPI / 96.0), y, cW - 8 * (DPI / 96.0), headerHeightPx, c.getAlign());

            g2.setColor(borderColor);
            if (gridLines && ci < cols.size() - 1) {
                g2.draw(new Line2D.Double(colX + cW, y, colX + cW, y + headerHeightPx));
            }
            colX += cW;
        }
        if (innerLines) {
            g2.setColor(borderColor);
            g2.draw(new Line2D.Double(x, y + headerHeightPx, x + w, y + headerHeightPx));
        }

        List<BillItem> items = bill != null && bill.getItems() != null ? bill.getItems() : new ArrayList<>();
        if (items.isEmpty()) {
            BillItem sample = new BillItem();
            sample.setDesc("Sample Item");
            sample.setQty(1);
            sample.setRate(100);
            sample.setGst(18);
            items = List.of(sample);
        }

        double curY = y + headerHeightPx;
        String currency = settings != null ? settings.getCurrency() : "₹";

        // minRows: Indian GST layouts need the item grid to fill a fixed band —
        // render at least N data rows (borders only when empty) so the column
        // dividers run the full height instead of stopping after 1-2 items.
        int minRows = el.getMinRows();
        int rowsToDraw = Math.max(items.size(), minRows);

        for (int i = 0; i < rowsToDraw; i++) {
            BillItem item = i < items.size() ? items.get(i) : null;
            boolean isEven = (i % 2 == 0);

            // Row background, then zebra stripe overlay when enabled
            g2.setColor(rowBgColor);
            g2.fill(new Rectangle2D.Double(x, curY, w, rowHeightPx));
            if (item != null && el.isShowZebra() && !isEven) {
                g2.setColor(zebraBgColor);
                g2.fill(new Rectangle2D.Double(x, curY, w, rowHeightPx));
            }

            if (innerLines) {
                g2.setColor(borderColor);
                g2.draw(new Line2D.Double(x, curY + rowHeightPx, x + w, curY + rowHeightPx));
            }

            if (item == null) { // filler row: grid only, no text
                double fillerColX = x;
                for (int ci = 0; ci < cols.size() - 1; ci++) {
                    fillerColX += (cols.get(ci).getWidth() / 100.0) * w;
                    if (gridLines) {
                        g2.setColor(borderColor);
                        g2.draw(new Line2D.Double(fillerColX, curY, fillerColX, curY + rowHeightPx));
                    }
                }
                curY += rowHeightPx;
                continue;
            }

            double rowColX = x;
            for (int ci = 0; ci < cols.size(); ci++) {
                TableColumn c = cols.get(ci);
                double cW = (c.getWidth() / 100.0) * w;
                String val = getTableColumnValue(c.getKey(), item, i + 1, currency);
                g2.setFont(dataFont);
                g2.setColor(rowTextColor);
                drawCellText(g2, val, rowColX + 4 * (DPI / 96.0), curY, cW - 8 * (DPI / 96.0), rowHeightPx, c.getAlign());

                g2.setColor(borderColor);
                if (gridLines && ci < cols.size() - 1) {
                    g2.draw(new Line2D.Double(rowColX + cW, curY, rowColX + cW, curY + rowHeightPx));
                }
                rowColX += cW;
            }
            curY += rowHeightPx;
        }

        // Void filler: when the element declares more height than the drawn
        // rows consume, extend the column dividers + bottom border through the
        // remainder so the grid is continuous down to the summary bar.
        double bottomY = curY;
        if (minRows > 0 && y + h > curY + 0.5) {
            g2.setColor(rowBgColor);
            g2.fill(new Rectangle2D.Double(x, curY, w, y + h - curY));
            if (gridLines) {
                g2.setColor(borderColor);
                double colLineX = x;
                for (int ci = 0; ci < cols.size() - 1; ci++) {
                    colLineX += (cols.get(ci).getWidth() / 100.0) * w;
                    g2.draw(new Line2D.Double(colLineX, curY, colLineX, y + h));
                }
            }
            if (innerLines) {
                g2.setColor(borderColor);
                g2.draw(new Line2D.Double(x, y + h, x + w, y + h));
            }
            bottomY = y + h;
        }

        // Outer border — drawn per enabled side
        if (drawOuter) {
            g2.setColor(borderColor);
            g2.setStroke(borderStroke);
            if (el.isBorderTop())    g2.draw(new Line2D.Double(x, y, x + w, y));
            if (el.isBorderBottom()) g2.draw(new Line2D.Double(x, y, x + w, bottomY));
            if (el.isBorderLeft())   g2.draw(new Line2D.Double(x, y, x, bottomY));
            if (el.isBorderRight())  g2.draw(new Line2D.Double(x + w, y, x + w, bottomY));
        }
    }

    private static String getTableColumnValue(String key, BillItem item, int index, String cur) {
        if (key == null) return "";
        double qty = item.getQty();
        double rate = item.getRate();
        double disc = item.getDiscPct();
        double gst = item.getGst();
        double taxable = (qty * rate) * (1.0 - disc / 100.0);
        double total = taxable * (1.0 + gst / 100.0);

        return switch (key.toLowerCase().trim()) {
            case "sr", "index", "#", "s_no", "sno" -> String.valueOf(index);
            case "desc", "name", "description", "item_name" -> item.getDesc() != null ? item.getDesc() : "";
            case "hsn", "sac", "hsn_sac" -> item.getHsn() != null ? item.getHsn() : "";
            case "qty", "quantity" -> String.format("%.2f", qty);
            case "unit" -> item.getUnit() != null ? item.getUnit() : "PCS";
            case "rate", "price", "unit_price" -> String.format("%.2f", rate);
            case "disc", "discount", "disc_pct" -> disc > 0 ? String.format("%.1f%%", disc) : "0%";
            case "taxable", "taxable_value" -> String.format("%.2f", taxable);
            case "gst", "gst_rate", "tax" -> String.format("%.0f%%", gst);
            case "amount", "total", "total_amount" -> String.format("%.2f", total);
            default -> item.getCustomField(key);
        };
    }

    private static void drawCellText(Graphics2D g2, String text, double x, double y, double w, double h, String align) {
        PdfTextDraw.drawCellText(g2, text, x, y, w, h, align);
    }

    private static void drawWrappedText(Graphics2D g2, String text, double x, double y, double w, double h,
                                        String align, String vAlign, double lineHeightMult) {
        PdfTextDraw.drawWrappedText(g2, text, x, y, w, h, align, vAlign, lineHeightMult);
    }

    private static void drawWrappedText(Graphics2D g2, String text, double x, double y, double w, double h,
                                        String align, String vAlign, double lineHeightMult, double lineSpacingPt, double wordSpacingPt) {
        PdfTextDraw.drawWrappedText(g2, text, x, y, w, h, align, vAlign, lineHeightMult, lineSpacingPt, wordSpacingPt);
    }

    private static void renderStatusStamp(Graphics2D g2, BillStatus status, int imgW, int imgH) {
        PdfTextDraw.renderStatusStamp(g2, status, imgW, imgH);
    }

    private static void renderReceiptToGraphics(Graphics2D g2, Bill bill, BillPayment payment, String receiptNo,
                                                Settings settings, int imgW, int imgH) {
        BusinessProfile biz = settings.getBusiness();
        String cur = settings.getCurrency();
        String buyer = bill != null && bill.getVariables() != null ? bill.getVariables().getOrDefault("buyer_name", "Valued Customer") : "Valued Customer";
        String buyerAddr = bill != null && bill.getVariables() != null ? bill.getVariables().getOrDefault("buyer_address", "") : "";

        double grandTotal = bill != null && bill.getTotals() != null ? bill.getTotals().getGrandTotal() : payment.getAmount();
        double paidAmt = payment.getAmount();
        double due = Math.max(0, grandTotal - paidAmt);

        double mm = PX_PER_MM;

        // Gold Top Banner
        g2.setPaint(new GradientPaint(0, 0, new Color(185, 138, 43), imgW, 0, new Color(231, 194, 104)));
        g2.fill(new Rectangle2D.Double(0, 0, imgW, 2.5 * mm));

        // Header Business Name & Details
        double curY = 10 * mm;
        g2.setColor(new Color(26, 26, 26));
        g2.setFont(new Font("SansSerif", Font.BOLD, (int) (13 * (DPI / 72.0))));
        g2.drawString(biz.getName() != null && !biz.getName().isBlank() ? biz.getName().toUpperCase() : "MY BUSINESS", (int) (11 * mm), (int) curY);

        curY += 5 * mm;
        g2.setColor(new Color(68, 68, 68));
        g2.setFont(new Font("SansSerif", Font.PLAIN, (int) (8 * (DPI / 72.0))));
        if (biz.getAddress() != null && !biz.getAddress().isBlank()) {
            g2.drawString(biz.getAddress(), (int) (11 * mm), (int) curY);
            curY += 4 * mm;
        }
        String contact = (biz.getPhone() != null && !biz.getPhone().isBlank() ? "Ph: " + biz.getPhone() + "  " : "") +
                (biz.getEmail() != null ? biz.getEmail() : "");
        if (!contact.isBlank()) {
            g2.drawString(contact, (int) (11 * mm), (int) curY);
            curY += 4 * mm;
        }
        if (biz.getGstin() != null && !biz.getGstin().isBlank()) {
            g2.setFont(new Font("SansSerif", Font.BOLD, (int) (8 * (DPI / 72.0))));
            g2.drawString("GSTIN: " + biz.getGstin(), (int) (11 * mm), (int) curY);
            curY += 4 * mm;
        }

        // Business Logo on right if available
        if (biz.getLogo() != null && !biz.getLogo().isBlank()) {
            BufferedImage logoImg = decodeBase64Image(biz.getLogo());
            if (logoImg != null) {
                double logoW = 18 * mm;
                double logoH = 18 * mm;
                g2.drawImage(logoImg, (int) (imgW - 11 * mm - logoW), (int) (8 * mm), (int) logoW, (int) logoH, null);
            }
        }

        // Title Band
        curY = Math.max(curY + 2 * mm, 32 * mm);
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(new Line2D.Double(11 * mm, curY, imgW - 11 * mm, curY));

        curY += 5 * mm;
        g2.setColor(new Color(138, 106, 36));
        g2.setFont(new Font("SansSerif", Font.BOLD, (int) (12 * (DPI / 72.0))));
        String titleStr = "PAYMENT RECEIPT";
        int tW = g2.getFontMetrics().stringWidth(titleStr);
        g2.drawString(titleStr, (int) ((imgW - tW) / 2.0), (int) curY);

        curY += 2 * mm;
        g2.setColor(Color.BLACK);
        g2.draw(new Line2D.Double(11 * mm, curY, imgW - 11 * mm, curY));

        // Meta (Receipt No, Date, Bill No)
        curY += 6 * mm;
        g2.setColor(Color.DARK_GRAY);
        g2.setFont(new Font("SansSerif", Font.PLAIN, (int) (8.5 * (DPI / 72.0))));
        g2.drawString("Receipt No: ", (int) (11 * mm), (int) curY);
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("SansSerif", Font.BOLD, (int) (8.5 * (DPI / 72.0))));
        g2.drawString(receiptNo, (int) (32 * mm), (int) curY);

        String billNoStr = bill != null ? bill.getBillNo() : "—";
        int bW = g2.getFontMetrics().stringWidth(billNoStr);
        g2.drawString(billNoStr, (int) (imgW - 11 * mm - bW), (int) curY);
        g2.setColor(Color.DARK_GRAY);
        g2.setFont(new Font("SansSerif", Font.PLAIN, (int) (8.5 * (DPI / 72.0))));
        int lbW = g2.getFontMetrics().stringWidth("Against Invoice: ");
        g2.drawString("Against Invoice: ", (int) (imgW - 11 * mm - bW - lbW), (int) curY);

        curY += 5 * mm;
        g2.drawString("Date: ", (int) (11 * mm), (int) curY);
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("SansSerif", Font.BOLD, (int) (8.5 * (DPI / 72.0))));
        g2.drawString(payment.getDate() != null ? payment.getDate() : BillingService.todayISO(), (int) (32 * mm), (int) curY);

        // Received with thanks from
        curY += 6 * mm;
        double cardW = imgW - 22 * mm;
        double cardH = 20 * mm;
        g2.setColor(new Color(250, 247, 239));
        g2.fill(new RoundRectangle2D.Double(11 * mm, curY, cardW, cardH, 3 * mm, 3 * mm));
        g2.setColor(new Color(201, 201, 201));
        g2.setStroke(new BasicStroke(0.8f));
        g2.draw(new RoundRectangle2D.Double(11 * mm, curY, cardW, cardH, 3 * mm, 3 * mm));

        g2.setColor(new Color(119, 119, 119));
        g2.setFont(new Font("SansSerif", Font.BOLD, (int) (7 * (DPI / 72.0))));
        g2.drawString("RECEIVED WITH THANKS FROM", (int) (14 * mm), (int) (curY + 5 * mm));

        g2.setColor(Color.BLACK);
        g2.setFont(new Font("SansSerif", Font.BOLD, (int) (11 * (DPI / 72.0))));
        g2.drawString(buyer, (int) (14 * mm), (int) (curY + 11 * mm));

        if (!buyerAddr.isBlank()) {
            g2.setColor(new Color(68, 68, 68));
            g2.setFont(new Font("SansSerif", Font.PLAIN, (int) (7.5 * (DPI / 72.0))));
            g2.drawString(buyerAddr, (int) (14 * mm), (int) (curY + 16 * mm));
        }

        // Amount Received & Balance Due Boxes
        curY += cardH + 5 * mm;
        double amtBoxW = (cardW - 4 * mm) * 0.65;
        double dueBoxW = (cardW - 4 * mm) * 0.35;
        double boxH = 18 * mm;

        // Amount Box
        g2.setColor(Color.WHITE);
        g2.fill(new RoundRectangle2D.Double(11 * mm, curY, amtBoxW, boxH, 3 * mm, 3 * mm));
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(1.2f));
        g2.draw(new RoundRectangle2D.Double(11 * mm, curY, amtBoxW, boxH, 3 * mm, 3 * mm));

        g2.setColor(new Color(85, 85, 85));
        g2.setFont(new Font("SansSerif", Font.BOLD, (int) (7 * (DPI / 72.0))));
        g2.drawString("AMOUNT RECEIVED", (int) (14 * mm), (int) (curY + 5 * mm));

        g2.setColor(Color.BLACK);
        g2.setFont(new Font("SansSerif", Font.BOLD, (int) (14 * (DPI / 72.0))));
        g2.drawString(cur + String.format("%.2f", paidAmt), (int) (14 * mm), (int) (curY + 13 * mm));

        // Due Box
        double dueX = 11 * mm + amtBoxW + 4 * mm;
        g2.setColor(new Color(250, 250, 250));
        g2.fill(new RoundRectangle2D.Double(dueX, curY, dueBoxW, boxH, 3 * mm, 3 * mm));
        g2.setColor(new Color(201, 201, 201));
        g2.setStroke(new BasicStroke(0.8f));
        g2.draw(new RoundRectangle2D.Double(dueX, curY, dueBoxW, boxH, 3 * mm, 3 * mm));

        g2.setColor(new Color(85, 85, 85));
        g2.setFont(new Font("SansSerif", Font.BOLD, (int) (6.5 * (DPI / 72.0))));
        g2.drawString("BALANCE DUE", (int) (dueX + 3 * mm), (int) (curY + 5 * mm));

        g2.setColor(due > 0 ? new Color(180, 83, 9) : new Color(21, 128, 61));
        g2.setFont(new Font("SansSerif", Font.BOLD, (int) (10.5 * (DPI / 72.0))));
        g2.drawString(cur + String.format("%.2f", due), (int) (dueX + 3 * mm), (int) (curY + 13 * mm));

        // Amount in Words
        curY += boxH + 6 * mm;
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("SansSerif", Font.BOLD, (int) (7.5 * (DPI / 72.0))));
        g2.drawString("In Words: ", (int) (11 * mm), (int) curY);
        g2.setFont(new Font("SansSerif", Font.PLAIN, (int) (7.5 * (DPI / 72.0))));
        g2.drawString(BillingService.amountInWords(paidAmt), (int) (26 * mm), (int) curY);

        // Payment Details Box
        curY += 6 * mm;
        double pBoxH = 22 * mm;
        g2.setColor(new Color(248, 248, 248));
        g2.fill(new RoundRectangle2D.Double(11 * mm, curY, cardW, pBoxH, 2 * mm, 2 * mm));
        g2.setColor(new Color(220, 220, 220));
        g2.setStroke(new BasicStroke(0.6f));
        g2.draw(new RoundRectangle2D.Double(11 * mm, curY, cardW, pBoxH, 2 * mm, 2 * mm));

        g2.setColor(new Color(85, 85, 85));
        g2.setFont(new Font("SansSerif", Font.PLAIN, (int) (7.5 * (DPI / 72.0))));
        g2.drawString("Payment Method: ", (int) (14 * mm), (int) (curY + 6 * mm));
        g2.setColor(Color.BLACK);
        g2.setFont(new Font("SansSerif", Font.BOLD, (int) (7.5 * (DPI / 72.0))));
        g2.drawString(payment.getMethod() != null ? payment.getMethod().getLabel() : "Cash", (int) (40 * mm), (int) (curY + 6 * mm));

        if (payment.getReference() != null && !payment.getReference().isBlank()) {
            g2.setColor(new Color(85, 85, 85));
            g2.setFont(new Font("SansSerif", Font.PLAIN, (int) (7.5 * (DPI / 72.0))));
            g2.drawString("Txn / Reference: ", (int) (14 * mm), (int) (curY + 12 * mm));
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("SansSerif", Font.BOLD, (int) (7.5 * (DPI / 72.0))));
            g2.drawString(payment.getReference(), (int) (40 * mm), (int) (curY + 12 * mm));
        }

        if (payment.getNote() != null && !payment.getNote().isBlank()) {
            g2.setColor(new Color(85, 85, 85));
            g2.setFont(new Font("SansSerif", Font.PLAIN, (int) (7.5 * (DPI / 72.0))));
            g2.drawString("Notes: ", (int) (14 * mm), (int) (curY + 18 * mm));
            g2.setColor(Color.BLACK);
            g2.setFont(new Font("SansSerif", Font.BOLD, (int) (7.5 * (DPI / 72.0))));
            g2.drawString(payment.getNote(), (int) (40 * mm), (int) (curY + 18 * mm));
        }

        // UPI QR Code on bottom right if UPI configured
        if (biz.getUpi() != null && !biz.getUpi().isBlank()) {
            String upiPayload = BarcodeService.buildUpiPayload(biz.getUpi(), biz.getName(), due > 0 ? due : paidAmt, bill != null ? bill.getBillNo() : "");
            BufferedImage qr = BarcodeService.generateQrBufferedImage(upiPayload, (int) (26 * mm));
            if (qr != null) {
                g2.drawImage(qr, (int) (imgW - 11 * mm - 26 * mm), (int) (imgH - 36 * mm), (int) (26 * mm), (int) (26 * mm), null);
                g2.setColor(new Color(100, 100, 100));
                g2.setFont(new Font("SansSerif", Font.PLAIN, (int) (6 * (DPI / 72.0))));
                g2.drawString("Scan to Pay via UPI", (int) (imgW - 11 * mm - 24 * mm), (int) (imgH - 8 * mm));
            }
        }

        // Signature Line on bottom left
        double sigY = imgH - 16 * mm;
        g2.setColor(Color.BLACK);
        g2.setStroke(new BasicStroke(0.8f));
        g2.draw(new Line2D.Double(11 * mm, sigY, 55 * mm, sigY));
        g2.setFont(new Font("SansSerif", Font.PLAIN, (int) (7 * (DPI / 72.0))));
        g2.drawString("Authorized Signatory", (int) (11 * mm), (int) (sigY + 4 * mm));
    }

    private static BufferedImage decodeBase64Image(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String b64 = raw;
            if (b64.contains(",")) {
                b64 = b64.substring(b64.indexOf(",") + 1);
            }
            byte[] bytes = Base64.getDecoder().decode(b64.trim());
            return ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    public static Color parseColor(String hex, Color fallback) {
        if (hex == null || hex.isBlank()) return fallback;
        try {
            if (hex.startsWith("#")) {
                if (hex.length() == 4) { // #RGB
                    char r = hex.charAt(1);
                    char g = hex.charAt(2);
                    char b = hex.charAt(3);
                    hex = "#" + r + r + g + g + b + b;
                }
                return Color.decode(hex);
            } else if (hex.startsWith("rgb")) {
                String clean = hex.replaceAll("[^0-9,]", "");
                String[] parts = clean.split(",");
                if (parts.length >= 3) {
                    int r = Integer.parseInt(parts[0].trim());
                    int g = Integer.parseInt(parts[1].trim());
                    int b = Integer.parseInt(parts[2].trim());
                    return new Color(r, g, b);
                }
            }
            return fallback;
        } catch (Exception e) {
            return fallback;
        }
    }
}
