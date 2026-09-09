package com.invoicestudio.service;

import com.invoicestudio.model.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

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

    private static double calculateEffectiveHeight(Template template, int itemCount) {
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

    private static void renderTemplateToGraphics(Graphics2D g2, Template template, Bill bill, Settings settings,
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
        AffineTransform origTx = g2.getTransform();
        if (el.getRotation() != 0) {
            g2.rotate(Math.toRadians(el.getRotation()), x + w / 2.0, y + h / 2.0);
        }

        switch (el.getType()) {
            case RECT -> {
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
            }
            case LINE -> {
                g2.setColor(parseColor(el.getBorderColor(), Color.BLACK));
                float bw = (float) Math.max(1, (el.getBorderWidth() > 0 ? el.getBorderWidth() : 0.4) * PX_PER_MM);
                g2.setStroke(new BasicStroke(bw));
                if ("v".equalsIgnoreCase(el.getDirection())) {
                    g2.draw(new Line2D.Double(x + w / 2.0, y, x + w / 2.0, y + h));
                } else {
                    g2.draw(new Line2D.Double(x, y + h / 2.0, x + w, y + h / 2.0));
                }
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
                if (el.isUppercase()) text = text.toUpperCase();

                int fontStyle = Font.PLAIN;
                if (el.getFontWeight() >= 700) fontStyle |= Font.BOLD;
                if (el.isItalic()) fontStyle |= Font.ITALIC;

                String fontName = el.getFontFamily() != null ? el.getFontFamily() : "SansSerif";
                int fontSizePx = (int) Math.max(8, Math.round(el.getFontSize() * (DPI / 72.0)));
                Font font = new Font(fontName, fontStyle, fontSizePx);
                g2.setFont(font);
                g2.setColor(parseColor(el.getColor(), Color.BLACK));

                drawWrappedText(g2, text, x, y, w, h, el.getAlign(), el.getVAlign(), el.getLineHeight());
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
                BufferedImage bar = BarcodeService.generateBarcodeBufferedImage(payload, (int) Math.round(w), (int) Math.round(h), el.isBarcodeShowText());
                if (bar != null) {
                    g2.drawImage(bar, (int) Math.round(x), (int) Math.round(y), (int) Math.round(w), (int) Math.round(h), null);
                }
            }
            case TABLE -> {
                renderTableToGraphics(g2, el, bill, settings, x, y, w, h);
            }
        }

        g2.setTransform(origTx);
    }

    private static void renderTableToGraphics(Graphics2D g2, TemplateElement el, Bill bill, Settings settings,
                                              double x, double y, double w, double h) {
        List<TableColumn> cols = el.getColumns();
        if (cols == null || cols.isEmpty()) cols = PresetTemplates.defaultItemColumns();

        Color headerBg = parseColor(el.getHeaderBg(), new Color(239, 233, 219));
        Color headerColor = parseColor(el.getHeaderColor(), Color.BLACK);
        Color borderColor = parseColor(el.getBorderColor(), new Color(200, 200, 200));

        double rowHeightPx = (el.getRowHeight() > 0 ? el.getRowHeight() : 7.0) * PX_PER_MM;
        double headerHeightPx = Math.max(22 * (DPI / 96.0), rowHeightPx);

        // Header Background
        g2.setColor(headerBg);
        g2.fill(new Rectangle2D.Double(x, y, w, headerHeightPx));

        // Header Border
        g2.setColor(borderColor);
        g2.setStroke(new BasicStroke(1.0f));
        g2.draw(new Rectangle2D.Double(x, y, w, headerHeightPx));

        // Header Text
        Font headerFont = new Font("SansSerif", Font.BOLD, (int) Math.max(9, 9 * (DPI / 72.0)));
        Font dataFont = new Font("SansSerif", Font.PLAIN, (int) Math.max(8.5, 8.5 * (DPI / 72.0)));

        double colX = x;
        for (TableColumn c : cols) {
            double cW = (c.getWidth() / 100.0) * w;
            g2.setFont(headerFont);
            g2.setColor(headerColor);
            drawCellText(g2, c.getLabel(), colX + 4 * (DPI / 96.0), y, cW - 8 * (DPI / 96.0), headerHeightPx, c.getAlign());

            g2.setColor(borderColor);
            g2.draw(new Line2D.Double(colX + cW, y, colX + cW, y + headerHeightPx));
            colX += cW;
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

        for (int i = 0; i < items.size(); i++) {
            BillItem item = items.get(i);
            boolean isEven = (i % 2 == 0);
            if (el.isShowZebra() && !isEven) {
                g2.setColor(new Color(248, 248, 248));
                g2.fill(new Rectangle2D.Double(x, curY, w, rowHeightPx));
            }

            g2.setColor(borderColor);
            g2.draw(new Line2D.Double(x, curY + rowHeightPx, x + w, curY + rowHeightPx));

            double rowColX = x;
            for (TableColumn c : cols) {
                double cW = (c.getWidth() / 100.0) * w;
                String val = getTableColumnValue(c.getKey(), item, i + 1, currency);
                g2.setFont(dataFont);
                g2.setColor(Color.BLACK);
                drawCellText(g2, val, rowColX + 4 * (DPI / 96.0), curY, cW - 8 * (DPI / 96.0), rowHeightPx, c.getAlign());

                g2.setColor(borderColor);
                g2.draw(new Line2D.Double(rowColX + cW, curY, rowColX + cW, curY + rowHeightPx));
                rowColX += cW;
            }
            curY += rowHeightPx;
        }

        // Outer border
        g2.setColor(borderColor);
        g2.draw(new Rectangle2D.Double(x, y, w, curY - y));
    }

    private static String getTableColumnValue(String key, BillItem item, int index, String cur) {
        if (key == null) return "";
        double qty = item.getQty();
        double rate = item.getRate();
        double disc = item.getDiscPct();
        double gst = item.getGst();
        double taxable = (qty * rate) * (1.0 - disc / 100.0);
        double total = taxable * (1.0 + gst / 100.0);

        return switch (key.toLowerCase()) {
            case "sr", "index", "#" -> String.valueOf(index);
            case "desc", "name", "description" -> item.getDesc() != null ? item.getDesc() : "";
            case "hsn", "sac" -> item.getHsn() != null ? item.getHsn() : "";
            case "qty", "quantity" -> String.format("%.2f", qty);
            case "unit" -> item.getUnit() != null ? item.getUnit() : "PCS";
            case "rate", "price" -> String.format("%.2f", rate);
            case "disc", "discount" -> disc > 0 ? String.format("%.1f%%", disc) : "0%";
            case "taxable" -> String.format("%.2f", taxable);
            case "gst", "gst_rate", "tax" -> String.format("%.0f%%", gst);
            case "amount", "total" -> String.format("%.2f", total);
            default -> "";
        };
    }

    private static void drawCellText(Graphics2D g2, String text, double x, double y, double w, double h, String align) {
        if (text == null || text.isBlank()) return;
        FontMetrics fm = g2.getFontMetrics();
        int strW = fm.stringWidth(text);
        int textY = (int) Math.round(y + ((h - fm.getHeight()) / 2.0) + fm.getAscent());

        int textX = (int) Math.round(x);
        if ("right".equalsIgnoreCase(align)) {
            textX = (int) Math.round(x + w - strW);
        } else if ("center".equalsIgnoreCase(align)) {
            textX = (int) Math.round(x + (w - strW) / 2.0);
        }
        g2.drawString(text, textX, textY);
    }

    private static void drawWrappedText(Graphics2D g2, String text, double x, double y, double w, double h,
                                        String align, String vAlign, double lineHeightMult) {
        if (text == null || text.isEmpty()) return;
        FontMetrics fm = g2.getFontMetrics();
        double lineSpacing = Math.max(fm.getHeight(), fm.getHeight() * (lineHeightMult > 0 ? lineHeightMult : 1.25));

        String[] rawLines = text.split("\n");
        List<String> lines = new ArrayList<>();

        for (String raw : rawLines) {
            String[] words = raw.split(" ");
            StringBuilder sb = new StringBuilder();
            for (String word : words) {
                if (sb.length() == 0) {
                    sb.append(word);
                } else if (fm.stringWidth(sb + " " + word) <= w) {
                    sb.append(" ").append(word);
                } else {
                    lines.add(sb.toString());
                    sb = new StringBuilder(word);
                }
            }
            if (sb.length() > 0) lines.add(sb.toString());
        }

        double totalTextHeight = lines.size() * lineSpacing;
        double startY = y + fm.getAscent();

        if ("middle".equalsIgnoreCase(vAlign)) {
            startY = y + Math.max(0, (h - totalTextHeight) / 2.0) + fm.getAscent();
        } else if ("bottom".equalsIgnoreCase(vAlign)) {
            startY = y + Math.max(0, h - totalTextHeight) + fm.getAscent();
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int strW = fm.stringWidth(line);
            double lineX = x;
            if ("right".equalsIgnoreCase(align)) {
                lineX = x + w - strW;
            } else if ("center".equalsIgnoreCase(align)) {
                lineX = x + (w - strW) / 2.0;
            }
            g2.drawString(line, (int) Math.round(lineX), (int) Math.round(startY + (i * lineSpacing)));
        }
    }

    private static void renderStatusStamp(Graphics2D g2, BillStatus status, int imgW, int imgH) {
        boolean isPaid = (status == BillStatus.PAID);
        String label = isPaid ? "PAID" : "CANCELLED";
        Color stampColor = isPaid ? new Color(14, 122, 79, 70) : new Color(192, 38, 38, 70);

        AffineTransform orig = g2.getTransform();
        g2.translate(imgW / 2.0, imgH * 0.44);
        g2.rotate(Math.toRadians(-22));

        Font font = new Font("Arial", Font.BOLD, (int) (imgW * 0.09));
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();

        int padX = (int) (imgW * 0.04);
        int padY = (int) (imgW * 0.015);
        int strW = fm.stringWidth(label);
        int boxW = strW + padX * 2;
        int boxH = fm.getHeight() + padY * 2;

        g2.setColor(stampColor);
        g2.setStroke(new BasicStroke((float) (imgW * 0.008)));
        g2.drawRoundRect(-boxW / 2, -boxH / 2, boxW, boxH, (int) (imgW * 0.015), (int) (imgW * 0.015));

        g2.drawString(label, -strW / 2, fm.getAscent() - boxH / 2 + padY);

        g2.setTransform(orig);
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

    private static Color parseColor(String hex, Color fallback) {
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
