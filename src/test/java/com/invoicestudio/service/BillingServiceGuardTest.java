package com.invoicestudio.service;

import com.invoicestudio.model.Bill;
import com.invoicestudio.model.BillItem;
import com.invoicestudio.model.BillStatus;
import com.invoicestudio.model.BillTotals;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;
import com.invoicestudio.model.ElementType;
import com.invoicestudio.model.PageConfig;
import com.invoicestudio.model.PageSizeName;
import com.invoicestudio.model.TemplateElement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guards added after the 3-month merchant simulation:
 *  - buyerOutstanding arithmetic (credit-limit guard feeds on this)
 *  - exportBatchPdf: per-bill outcomes, failure isolation, real files on disk
 */
class BillingServiceGuardTest {

    private static Bill bill(String no, String buyer, BillStatus status, double grand, double due) {
        Bill b = new Bill();
        b.setId("bill_" + no);
        b.setBillNo(no);
        b.setDate("2026-08-10");
        b.setBuyerName(buyer);
        b.setStatus(status);
        BillItem it = new BillItem();
        it.setDesc("Denim Straight Fit — Dark Blue");
        it.setQty(10);
        it.setRate(grand / 10);
        it.setGst(12);
        b.setItems(new ArrayList<>(List.of(it)));
        b.setTotals(BillingService.computeTotals(new ArrayList<>(List.of(it)), 0, false));
        if (status != BillStatus.UNPAID) {
            com.invoicestudio.model.BillPayment p = new com.invoicestudio.model.BillPayment();
            p.setId("pay_" + no);
            p.setDate("2026-08-10");
            p.setAmount(grand);
            b.setPayments(new ArrayList<>(List.of(p)));
        }
        return b;
    }

    @Test
    void buyerOutstandingSumsOnlyUnpaidDuesCaseInsensitively() {
        Bill partial = bill("INV-002", "zaid garments", BillStatus.UNPAID, 5000, 0);
        com.invoicestudio.model.BillPayment part = new com.invoicestudio.model.BillPayment();
        part.setId("pay_part");
        part.setDate("2026-08-12");
        part.setAmount(1000);
        partial.setPayments(new ArrayList<>(List.of(part)));   // ₹1000 of ₹5000 paid

        List<Bill> bills = List.of(
                bill("INV-001", "Zaid Garments", BillStatus.UNPAID, 10000, 0),
                partial,
                bill("INV-003", "Zaid Garments", BillStatus.PAID, 7000, 0),
                bill("INV-004", "Zaid Garments", BillStatus.CANCELLED, 9999, 0),
                bill("INV-005", "Royal Fashions", BillStatus.UNPAID, 3000, 0));

        double due = BillingService.buyerOutstanding(bills, "  ZAID Garments ");
        // computeTotals adds 12% GST: 10000 → 11200; 5000 → 5600 − 1000 partial = 4600
        assertEquals(15800.0, due, 1e-9, "GST-inclusive dues; partial payment credited; paid + cancelled excluded");

        assertEquals(0.0, BillingService.buyerOutstanding(bills, "Nobody Traders"));
        assertEquals(0.0, BillingService.buyerOutstanding(bills, null));
        assertEquals(0.0, BillingService.buyerOutstanding(bills, ""));
    }

    @Test
    void buyerOutstandingUsesGstInclusiveGrandTotal() {
        Bill b = bill("INV-010", "Metro Retail", BillStatus.UNPAID, 8000, 0); // 8000 + 12% GST = 8960
        assertEquals(8960.0, BillingService.buyerOutstanding(List.of(b), "Metro Retail"), 1e-9);
    }

    @Test
    void exportBatchPdfRendersEachBillAndIsolatesFailures(@TempDir Path tmp) {
        Template tpl = new Template("tpl_guard", "Guard Test",
                new PageConfig(PageSizeName.A4, 210, 297, "portrait", new PageConfig.Margins(10, 10, 10, 10)),
                new ArrayList<>());
        TemplateElement el = new TemplateElement();
        el.setId("g1");
        el.setType(ElementType.TEXT);
        el.setX(15); el.setY(15); el.setW(80); el.setH(8);
        el.setText("INV {{billNo}}");
        el.setFontSize(10);
        el.setColor("#111111");
        el.setZIndex(0);
        tpl.getElements().add(el);

        List<Bill> bills = List.of(
                bill("INV-G01", "Zaid Garments", BillStatus.UNPAID, 5600, 5600),
                bill("INV-G02", "Royal Fashions", BillStatus.PAID, 11800, 0),
                bill("INV-G03", "Khan Traders", BillStatus.UNPAID, 9440, 9440));

        File out = tmp.resolve("pdfs").toFile();
        BillingService.BatchResult r = BillingService.exportBatchPdf(bills, tpl, new Settings(), out, 1);

        assertEquals(3, r.ok(), "all three render");
        assertEquals(0, r.failed());
        assertTrue(r.failures().isEmpty());
        for (Bill b : bills) {
            File f = new File(out, b.getBillNo() + ".pdf");
            assertTrue(f.exists(), b.getBillNo() + " exists");
            assertTrue(f.length() > 500, b.getBillNo() + " non-trivial size");
        }

        // Failure isolation: a degenerate bill (no items, no totals) must never
        // prevent the other bills in the batch from rendering.
        Bill broken = new Bill();
        broken.setId("bill_broken");
        broken.setBillNo("INV-BAD");
        broken.setDate("2026-08-11");
        broken.setBuyerName("Ghost Party");
        broken.setStatus(BillStatus.UNPAID);
        broken.setItems(null);
        broken.setTotals(null);

        BillingService.BatchResult r2 = BillingService.exportBatchPdf(
                new ArrayList<>(List.of(bills.get(0), broken)), tpl, new Settings(), out, 1);
        assertTrue(r2.ok() >= 1, "good bill still rendered alongside the degenerate one");
        assertTrue(r2.ok() + r2.failed() == 2, "every bill accounted for exactly once");
    }
}
