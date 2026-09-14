package com.invoicestudio.service;

import com.invoicestudio.model.Bill;
import com.invoicestudio.model.BillItem;
import com.invoicestudio.model.BillPayment;
import com.invoicestudio.model.BillStatus;
import com.invoicestudio.model.BillTotals;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

/**
 * Headless (Java2D) renderer that turns a print template into a PNG preview —
 * the exact engine used for real PDF export, so what the AI sees is what
 * the printer gets.
 *
 * <p>Preview runs against a realistic sample bill (buyer, line items, totals,
 * a payment) so bound elements — the item table, totals block, UPI QR,
 * barcodes — render with lifelike data instead of zeros. This is the same
 * convention the Template Designer uses ({@code RenderContext} fills sample
 * buyer fields when no bill is supplied); here we go one step further and
 * populate the whole bill.</p>
 */
public final class TemplatePreviewService {

    private TemplatePreviewService() {}

    /** Default preview resolution. 150 dpi keeps an A4 preview around 1240x1754 px. */
    public static final double DEFAULT_DPI = 150.0;
    private static final double MIN_DPI = 72.0;
    private static final double MAX_DPI = 300.0;

    /**
     * Reference DPI the shared Java2D renderer ({@link PdfExportService}) draws
     * at — every mm coordinate is multiplied by {@code PX_PER_MM = 300/25.4}.
     * Previews at any other DPI must scale the graphics context to match, or
     * elements land on a smaller canvas and the page gets cropped (~60% lost
     * at 150 dpi).
     */
    public static final double BASE_DPI = 300.0;

    /**
     * Renders the template to a PNG byte array.
     *
     * @param template the template to render (never null)
     * @param settings live settings (business profile, currency, print offsets); null-safe
     * @param dpi      requested resolution; clamped to [72, 300]
     * @return PNG bytes (never null, never empty)
     */
    public static byte[] renderPng(Template template, Settings settings, double dpi) throws Exception {
        if (template == null) throw new IllegalArgumentException("template is required");
        double effDpi = Math.max(MIN_DPI, Math.min(MAX_DPI, dpi > 0 ? dpi : DEFAULT_DPI));
        if (settings == null) settings = new Settings();

        Bill sample = sampleBill();

        double widthMm = template.getPage() != null ? template.getPage().getWidth() : 210.0;
        double heightMm = template.getPage() != null ? template.getPage().getHeight() : 297.0;
        if (template.getPage() != null && template.getPage().isAutoHeight()) {
            int itemCount = sample.getItems() != null ? sample.getItems().size() : 1;
            heightMm = PdfExportService.calculateEffectiveHeight(template, itemCount);
        }

        // Canvas at the REQUESTED dpi...
        int imgW = (int) Math.max(100, Math.round(widthMm * effDpi / 25.4));
        int imgH = (int) Math.max(100, Math.round(heightMm * effDpi / 25.4));

        BufferedImage bi = new BufferedImage(imgW, imgH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = bi.createGraphics();
        applyHints(g2);
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, imgW, imgH);

        // ...but the renderer draws in 300-dpi coordinates — scale the context
        // so the full page fits the canvas at every dpi (150 dpi previews are
        // now complete, not cropped).
        g2.scale(effDpi / BASE_DPI, effDpi / BASE_DPI);

        int baseW = (int) Math.max(100, Math.round(widthMm * BASE_DPI / 25.4));
        int baseH = (int) Math.max(100, Math.round(heightMm * BASE_DPI / 25.4));

        RenderContext ctx = new RenderContext(sample, settings, 0, 1, 1);
        PdfExportService.renderTemplateToGraphics(g2, template, sample, settings, ctx, baseW, baseH);
        g2.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024);
        ImageIO.write(bi, "png", out);
        return out.toByteArray();
    }

    /** Page size actually used at render time (mm) — differs from the stored page for auto-height rolls. */
    public static double[] effectivePageSize(Template template, Settings settings) {
        double widthMm = template.getPage() != null ? template.getPage().getWidth() : 210.0;
        double heightMm = template.getPage() != null ? template.getPage().getHeight() : 297.0;
        if (template.getPage() != null && template.getPage().isAutoHeight()) {
            int itemCount = SAMPLE_ITEM_COUNT;
            heightMm = PdfExportService.calculateEffectiveHeight(template, itemCount);
        }
        return new double[]{widthMm, heightMm};
    }

    private static final int SAMPLE_ITEM_COUNT = 3;

    /** A believable demo invoice: 3 lines, GST split, one UPI payment, PAID status. */
    private static Bill sampleBill() {
        Bill bill = new Bill();
        bill.setBillNo("INV-2026-014");
        bill.setDate(LocalDate.now().toString());
        bill.setBuyerName("Acme Enterprises Ltd");
        bill.setStatus(BillStatus.PAID);

        BillItem shirt = new BillItem("bi_1", "Cotton Shirt — Blue", "6105", 3, "PCS", 799, 5, 0);
        BillItem jeans = new BillItem("bi_2", "Denim Jeans — Slim", "6203", 2, "PCS", 1299, 12, 5);
        BillItem cap = new BillItem("bi_3", "Branded Cap", "6505", 5, "PCS", 249, 12, 0);
        bill.setItems(List.of(shirt, jeans, cap));

        double subtotal = 3 * 799 + 2 * 1299 + 5 * 249;
        double taxable = 3 * 799 + (2 * 1299) * 0.95 + 5 * 249; // jeans carry 5% line discount
        double cgst = taxable * 0.085 / 2; // simplified demo split
        BillTotals t = new BillTotals();
        t.setSubtotal(subtotal);
        t.setDiscount((2 * 1299) * 0.05);
        t.setTaxable(taxable);
        t.setCgst(cgst);
        t.setSgst(cgst);
        t.setIgst(0);
        t.setRoundOff(0);
        t.setGrandTotal(Math.round((taxable + 2 * cgst) * 100.0) / 100.0);
        bill.setTotals(t);

        bill.setPayments(List.of(new BillPayment(
                "pay_1", LocalDate.now().toString(), t.getGrandTotal(),
                com.invoicestudio.model.PaymentMethod.UPI, "UPI-83219044", "")));
        return bill;
    }

    private static void applyHints(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    }
}
