package com.invoicestudio.service;

import com.invoicestudio.model.Bill;
import com.invoicestudio.model.BillItem;
import com.invoicestudio.model.BillStatus;
import com.invoicestudio.model.BillTotals;
import com.invoicestudio.model.ItemRecord;
import com.invoicestudio.model.PurchaseBill;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-math verification of the purchase & financial engines.
 * No DB, no JavaFX toolkit required.
 */
class PurchaseAndFinancialsTest {

    private static BillItem item(String desc, double qty, double rate, double gst) {
        return new BillItem("it_test", desc, "6203", qty, "PCS", rate, gst, 0);
    }

    // ---------------- PurchaseService ----------------

    @Test
    void purchaseTotalsIntraStateSplitsCgstSgst() {
        BillTotals t = PurchaseService.computePurchaseTotals(
                List.of(item("A", 10, 100, 18)), 0, false);
        assertEquals(1000.0, t.getSubtotal(), 0.01);
        assertEquals(1000.0, t.getTaxable(), 0.01);
        assertEquals(90.0, t.getCgst(), 0.01);
        assertEquals(90.0, t.getSgst(), 0.01);
        assertEquals(0.0, t.getIgst(), 0.01);
        assertEquals(1180.0, t.getGrandTotal(), 0.01);
    }

    @Test
    void purchaseTotalsInterStateUsesIgst() {
        BillTotals t = PurchaseService.computePurchaseTotals(
                List.of(item("A", 10, 100, 18)), 0, true);
        assertEquals(0.0, t.getCgst(), 0.01);
        assertEquals(0.0, t.getSgst(), 0.01);
        assertEquals(180.0, t.getIgst(), 0.01);
        assertEquals(1180.0, t.getGrandTotal(), 0.01);
    }

    @Test
    void purchaseRoundOffMatchesSalesEngine() {
        // 3 × 33.33 @ 5% → 99.99 taxable, 5.00 tax, grand 104.99 → round to 105
        BillTotals t = PurchaseService.computePurchaseTotals(
                List.of(item("A", 3, 33.33, 5)), 0, false);
        assertEquals(104.99, t.getTaxable() + t.getCgst() + t.getSgst(), 0.01);
        assertEquals(Math.round(t.getTaxable() + t.getCgst() + t.getSgst()), t.getGrandTotal(), 0.01);
    }

    @Test
    void interStateDetectionByGstinPrefix() {
        assertTrue(PurchaseService.isInterStateSupply("27AAPFU0939F1ZV", "29"));
        assertFalse(PurchaseService.isInterStateSupply("27AAPFU0939F1ZV", "27"));
        assertFalse(PurchaseService.isInterStateSupply("", "27"));
        assertFalse(PurchaseService.isInterStateSupply("27AAPFU0939F1ZV", ""));
    }

    @Test
    void purchaseBillNoFormat() {
        assertEquals("PUR-0042", PurchaseService.nextPurchaseBillNo(42, 4));
        assertEquals("PUR-7", PurchaseService.nextPurchaseBillNo(7, 1));
    }

    // ---------------- PurchaseBill payable & payments ----------------

    @Test
    void payableIncludesFreight() {
        PurchaseBill p = new PurchaseBill();
        BillTotals t = new BillTotals();
        t.setGrandTotal(1180.0);
        p.setTotals(t);
        p.setFreight(20.0);
        assertEquals(1200.0, p.getAmountPayable(), 0.01);
    }

    @Test
    void paidAmountFallsBackToFullWhenFlagged() {
        PurchaseBill p = new PurchaseBill();
        BillTotals t = new BillTotals();
        t.setGrandTotal(500.0);
        p.setTotals(t);
        assertFalse(p.isPaid());
        assertEquals(0.0, p.getPaidAmount(), 0.01);
        p.setPaid(true);
        assertEquals(500.0, p.getPaidAmount(), 0.01);
    }

    // ---------------- FinancialService ----------------

    @Test
    void inRangeFilteringIsInclusive() {
        assertTrue(FinancialService.inRange("2026-04-01", "2026-04-01", "2027-03-31"));
        assertTrue(FinancialService.inRange("2026-09-13", "2026-04-01", "2026-09-13"));
        assertFalse(FinancialService.inRange("2026-03-31", "2026-04-01", ""));
        assertTrue(FinancialService.inRange("2026-05-05", "", ""));
    }

    @Test
    void salesRevenueSkipsCancelled() {
        Bill ok = new Bill();
        ok.setDate("2026-09-01");
        ok.setStatus(BillStatus.PAID);
        BillTotals t1 = new BillTotals();
        t1.setGrandTotal(1000);
        ok.setTotals(t1);

        Bill cancelled = new Bill();
        cancelled.setDate("2026-09-02");
        cancelled.setStatus(BillStatus.CANCELLED);
        BillTotals t2 = new BillTotals();
        t2.setGrandTotal(999);
        cancelled.setTotals(t2);

        FinancialService svc = new FinancialService();
        assertEquals(1000.0, svc.salesRevenue(List.of(ok, cancelled), "", ""), 0.01);
    }

    @Test
    void tradingAndPnlMath() {
        // Items: cost 60, sell 100
        ItemRecord it = new ItemRecord("it1", "PENT", "6203", "PCS", 100.0, 5.0);
        it.setPurchaseRate(60.0);
        it.setOpeningStock(10);

        Map<String, Double> stock = new HashMap<>();
        stock.put("it1", 8.0); // sold 2 units → closing stock 8

        // One sale: 2 × 100 @ 5% GST = 210 grand
        Bill sale = new Bill();
        sale.setId("b1");
        sale.setBillNo("INV-1");
        sale.setDate("2026-09-01");
        sale.setStatus(BillStatus.UNPAID);
        sale.setItems(List.of(item("PENT", 2, 100, 5)));
        sale.setTotals(BillingService.computeTotals(sale.getItems(), 0, false));
        sale.setBuyerName("Ramesh");

        // One purchase: 5 × 60 @ 5% GST — taxable 300
        PurchaseBill pur = new PurchaseBill();
        pur.setId("p1");
        pur.setBillNo("PUR-1");
        pur.setDate("2026-09-02");
        pur.setItems(List.of(item("PENT", 5, 60, 5)));
        pur.setTotals(PurchaseService.computePurchaseTotals(pur.getItems(), 0, false));

        FinancialService svc = new FinancialService();
        var f = svc.compute(List.of(sale), List.of(pur), List.of(), List.of(),
                List.of(it), stock, "", "");

        assertEquals(210.0, f.salesRevenue(), 0.01);
        assertEquals(300.0, f.purchasesValue(), 0.01);
        // Opening 10×60=600; closing 8×60=480
        assertEquals(600.0, f.openingStockValue(), 0.01);
        assertEquals(480.0, f.inventoryValue(), 0.01);
        // GP = (210 + 480) − (600 + 300 + 0) = −210 (bought more than sold — correct accounting)
        assertEquals(-210.0, f.grossProfit(), 0.01);
        assertEquals(-210.0, f.netProfit(), 0.01);
        // Output GST 10, ITC 15 → net −5 → clamp to 0? Engine reports raw −5; payable ≥ 0 in UI
        assertEquals(10.0, f.outputGst(), 0.01);
        assertEquals(15.0, f.inputCredit(), 0.01);
        // Debtors: unpaid 210
        assertEquals(210.0, f.sundryDebtors(), 0.01);
    }

    @Test
    void daybookSortedByDate() {
        Bill sale = new Bill();
        sale.setDate("2026-09-05");
        sale.setStatus(BillStatus.PAID);
        BillTotals t = new BillTotals();
        t.setGrandTotal(100);
        sale.setTotals(t);
        sale.setBuyerName("X");

        FinancialService svc = new FinancialService();
        var rows = svc.buildDaybook(List.of(sale), List.of(), List.of());
        assertFalse(rows.isEmpty());
        assertTrue(rows.get(0).date().compareTo(rows.get(rows.size() - 1).date()) <= 0);
    }

    @Test
    void stockSummaryDateFilteringAndMathIntegrity() {
        FinancialService svc = new FinancialService();

        ItemRecord shirt = new ItemRecord("it_shirt", "Cotton Shirt", "6205", "PCS", 800.0, 5.0);
        shirt.setOpeningStock(10); // initial baseline
        shirt.setPurchaseRate(400.0);

        // Transaction prior to June: 5 purchased in May, 3 sold in May
        PurchaseBill pMay = new PurchaseBill();
        pMay.setDate("2026-05-10");
        pMay.setItems(List.of(item("Cotton Shirt", 5, 400, 5))); // matched by name

        Bill sMay = new Bill();
        sMay.setDate("2026-05-20");
        sMay.setStatus(BillStatus.PAID);
        sMay.setItems(List.of(item("Cotton Shirt", 3, 800, 5)));

        // Transactions in June: 10 purchased in June, 4 sold in June, 2 cancelled in June
        PurchaseBill pJune = new PurchaseBill();
        pJune.setDate("2026-06-05");
        pJune.setItems(List.of(item("Cotton Shirt", 10, 420, 5)));

        Bill sJune = new Bill();
        sJune.setDate("2026-06-15");
        sJune.setStatus(BillStatus.PAID);
        sJune.setItems(List.of(item("Cotton Shirt", 4, 800, 5)));

        Bill sCancelled = new Bill();
        sCancelled.setDate("2026-06-18");
        sCancelled.setStatus(BillStatus.CANCELLED);
        sCancelled.setItems(List.of(item("Cotton Shirt", 2, 800, 5)));

        List<ItemRecord> items = List.of(shirt);
        List<PurchaseBill> purchases = List.of(pMay, pJune);
        List<Bill> bills = List.of(sMay, sJune, sCancelled);

        // Report for June 2026
        List<FinancialService.StockSummaryRow> summary = svc.stockSummary(
                items, Map.of(), purchases, bills, "2026-06-01", "2026-06-30");

        assertEquals(1, summary.size());
        FinancialService.StockSummaryRow row = summary.get(0);

        // Opening as of June 1 = 10 (initial) + 5 (May in) - 3 (May out) = 12
        assertEquals(12.0, row.openingQty(), 0.001, "Period opening stock must include prior transactions");
        // In June = 10
        assertEquals(10.0, row.inQty(), 0.001, "Inwards qty in range");
        // Out June = 4 (cancelled bill must be excluded)
        assertEquals(4.0, row.outQty(), 0.001, "Outwards qty in range excluding cancelled");
        // Closing = 12 + 10 - 4 = 18
        assertEquals(18.0, row.closingQty(), 0.001, "Closing must equal Opening + In - Out");
        assertEquals(row.openingQty() + row.inQty() - row.outQty(), row.closingQty(), 0.001);

        // Cost rate should use purchaseRate or average in-range purchase rate (400 or 420)
        assertTrue(row.costRate() > 0);
        assertEquals(row.closingQty() * row.costRate(), row.closingValue(), 0.01);
    }

    @Test
    void itemProfitabilityUsesHistoricalPurchaseCost() {
        FinancialService svc = new FinancialService();

        ItemRecord widget = new ItemRecord("it_widget", "Gizmo Widget", "8471", "PCS", 100.0, 18.0);
        // Catalog purchaseRate is 0, so cost must come from purchase bills!
        widget.setPurchaseRate(0.0);

        // Purchased in April @ 45 each
        PurchaseBill pApril = new PurchaseBill();
        pApril.setDate("2026-04-10");
        pApril.setItems(List.of(item("Gizmo Widget", 20, 45, 18)));

        // Sold in June @ 100 each, 0 purchases in June
        Bill sJune = new Bill();
        sJune.setDate("2026-06-12");
        sJune.setStatus(BillStatus.PAID);
        sJune.setItems(List.of(item("Gizmo Widget", 10, 100, 18)));

        List<FinancialService.ItemProfitRow> profit = svc.itemProfitability(
                List.of(widget), List.of(pApril), List.of(sJune), "2026-06-01", "2026-06-30");

        assertEquals(1, profit.size());
        FinancialService.ItemProfitRow row = profit.get(0);
        assertEquals(10.0, row.qtySold(), 0.001);
        assertEquals(1000.0, row.salesValue(), 0.01);
        // Cost should be picked up from historical April purchase (45.0), not 0 and not 100
        assertEquals(45.0, row.avgCost(), 0.01, "Historical purchase cost must be used when no purchases in range");
        assertEquals(450.0, row.cogs(), 0.01);
        assertEquals(550.0, row.grossProfit(), 0.01);
        assertEquals(55.0, row.gpPercent(), 0.01);
    }
}
