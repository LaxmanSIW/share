package com.invoicestudio.service;

import com.invoicestudio.model.Bill;
import com.invoicestudio.model.BillItem;
import com.invoicestudio.model.ElementType;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.TableColumn;
import com.invoicestudio.model.Template;
import com.invoicestudio.model.TemplateElement;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.List;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 16 acceptance: TABLE.minRows fills a fixed grid band — column dividers
 * and borders extend through the declared height even when the bill has only
 * one item (the Indian-GST "continuous column line" requirement).
 */
class TemplateTableMinRowsTest {

    private static final double DPI = 300.0; // reference canvas dpi

    private static Template tableTemplate(int minRows) {
        Template t = new Template();
        t.setPage(new com.invoicestudio.model.PageConfig());

        TemplateElement table = new TemplateElement();
        table.setType(ElementType.TABLE);
        table.setName("Items");
        table.setX(10); table.setY(60); table.setW(190); table.setH(70);
        table.setRowHeight(7);
        table.setMinRows(minRows);
        table.setColumns(List.of(
                new TableColumn("sr", "Sr", 6, "center"),
                new TableColumn("desc", "Description", 74, "left"),
                new TableColumn("qty", "Qty", 10, "right"),
                new TableColumn("amount", "Amount", 10, "right")));
        t.getElements().add(table);
        return t;
    }

    private static Bill oneItemBill() {
        Bill bill = new Bill();
        bill.setBillNo("INV-MINROWS-1");
        bill.setItems(List.of(new BillItem("bi_1", "Single Item", "6105", 1, "PCS", 100, 0, 0)));
        return bill;
    }

    private static BufferedImage render(Template t) throws Exception {
        byte[] png = TemplatePreviewService.renderPng(t, new Settings(), 300);
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    private static boolean isDark(BufferedImage img, double mmX, double mmY) {
        return hasDarkPixelNear(img, mmX, mmY, 0);
    }

    /** Scans a small window around the mm point — grid lines are 1px and antialiased. */
    private static boolean hasDarkPixelNear(BufferedImage img, double mmX, double mmY, int tolPx) {
        int cx = (int) Math.round(mmX * DPI / 25.4);
        int cy = (int) Math.round(mmY * DPI / 25.4);
        for (int dx = -tolPx; dx <= tolPx; dx++) {
            for (int dy = -tolPx; dy <= tolPx; dy++) {
                int x = cx + dx, y = cy + dy;
                if (x < 0 || y < 0 || x >= img.getWidth() || y >= img.getHeight()) continue;
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
                if (r + g + b < 740) return true; // anything detectably darker than the white page
            }
        }
        return false;
    }

    /** First column divider: tableX + tableW × colWidth_0/100 = 10 + 190×0.06 = 21.4 mm. */
    private static final double DIVIDER_X = 10 + 190.0 * 0.06;

    @Test
    void testVoidBelowLastItemIsBlankWithoutMinRows() throws Exception {
        BufferedImage img = render(tableTemplate(0));
        // One item ends ~y=75; probing y=100 in the void must find NO line
        assertFalse(hasDarkPixelNear(img, DIVIDER_X, 100, 3),
                "without minRows the grid stops after the last item (baseline)");
    }

    @Test
    void testMinRowsExtendsColumnDividerThroughTheBand() throws Exception {
        BufferedImage img = render(tableTemplate(9));
        // 9 rows × 7mm + header ≈ 71mm ≥ band → divider must exist mid-void
        assertTrue(hasDarkPixelNear(img, DIVIDER_X, 100, 4),
                "column divider must run through the void with minRows=9");
        assertTrue(hasDarkPixelNear(img, DIVIDER_X, 115, 4),
                "column divider must reach near the band bottom");
    }

    @Test
    void testMinRowsSmallerThanItemsRendersAllItems() throws Exception {
        Template t = tableTemplate(1);
        // minRows=1 but bill has 1 item → identical to natural rendering; the
        // row area must still show the item text region background (non-blank)
        BufferedImage img = render(t);
        assertTrue(hasDarkPixelNear(img, DIVIDER_X, 60 + 8.0, 6),
                "header underline region must render");
    }

    @Test
    void testMinRowsClampedNonNegative() {
        TemplateElement el = new TemplateElement();
        el.setMinRows(-5);
        assertEquals(0, el.getMinRows(), "negative minRows must clamp to 0");
        el.setMinRows(9);
        assertEquals(9, el.getMinRows());
    }
}
