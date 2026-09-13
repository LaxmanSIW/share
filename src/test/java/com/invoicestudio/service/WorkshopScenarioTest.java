package com.invoicestudio.service;

import com.invoicestudio.db.DatabaseManager;
import com.invoicestudio.db.ExpenseDao;
import com.invoicestudio.db.PurchaseBillDao;
import com.invoicestudio.db.SupplierDao;
import com.invoicestudio.model.*;
import com.invoicestudio.ui.DataManager;
import org.junit.jupiter.api.*;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Sharma Auto Workshop & Spares, Nashik (MH-27)" — a full business-year
 * simulation authored the way a CA + workshop owner would actually run it,
 * validating every subsystem with real-life data and accountant arithmetic.
 *
 * SCENARIO (Sep 2026):
 *  - Two suppliers: OEM dealer (intra-state MH, GST-registered, 30-day credit)
 *    and a tool importer (inter-state DL, IGST).
 *  - Two buyers: a fleet customer (B2B, GST-registered) and a walk-in (B2C).
 *  - Stock: brake pads & engine oil purchased, then partly sold; opening stock of spark plugs.
 *  - Expenses: rent (indirect), freight inward (direct).
 *  - Payments: partial payment to OEM dealer; fleet customer pays part of invoice.
 *
 * Every assertion mirrors what the owner's CA expects on paper.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WorkshopScenarioTest {

    private static final String TEST_DB = "test_workshop_scenario.db";
    private static DatabaseManager db;
    private static DataManager dm;

    private static Supplier oemDealer;      // Maharashtra (27) — intra-state
    private static Supplier toolImporter;   // Delhi (07) — inter-state
    private static Buyer fleetCustomer;     // B2B
    private static ItemRecord brakePads;
    private static ItemRecord engineOil;
    private static ItemRecord sparkPlugs;

    // Grand totals captured when bills are saved (authoritative for later assertions)
    private static double inv1GrandTotal;
    private static double inv2GrandTotal;

    private static double walkInGrandTotal() { return inv2GrandTotal; }

    @BeforeAll
    static void setUp() {
        new File(TEST_DB).delete();
        db = DatabaseManager.initCustom("jdbc:sqlite:" + TEST_DB);
        AuthSessionManager.setActiveSession(new com.invoicestudio.model.UserSession(
                "uid_workshop", "sharma@workshop.in", "Sharma Auto Workshop",
                "tok", "ref", System.currentTimeMillis() + 3600_000L, true));
        dm = DataManager.init(db);

        seedMasters();
    }

    @AfterAll
    static void tearDown() {
        AuthSessionManager.clear();
        new File(TEST_DB).delete();
    }

    // ------------------------------------------------------------------
    // Step 1 — masters as the owner would enter them
    // ------------------------------------------------------------------

    private static void seedMasters() {
        SupplierDao sDao = dm.suppliers();

        oemDealer = new Supplier("sup_oem", "Sharma OEM Dealer");
        oemDealer.setGst("27ABCDE1234F1Z5");
        oemDealer.setState("Maharashtra");
        oemDealer.setStateCode("27");
        oemDealer.setPhone("9822011223");
        oemDealer.setOpeningBalance(0);
        oemDealer.setCreditPeriodDays(30);
        sDao.saveSupplier(oemDealer);

        toolImporter = new Supplier("sup_tools", "Delhi Tool Imports");
        toolImporter.setGst("07AAACC5678D1Z2");
        toolImporter.setState("Delhi");
        toolImporter.setStateCode("07");
        toolImporter.setOpeningBalance(1500); // owed ₹1,500 from before (Cr)
        toolImporter.setCreditPeriodDays(45);
        sDao.saveSupplier(toolImporter);

        // buyers
        fleetCustomer = new Buyer("byr_fleet", "Nashik Travels Fleet", "Jail Road, Nashik",
                "27AAAFN9999A1Z1", "9422011223", "Maharashtra", "27");
        dm.buyers().saveBuyer(fleetCustomer);

        // items with cost + selling + opening stock
        brakePads = new ItemRecord("it_pads", "Brake Pads Front", "8708", "SET", 950.0, 18.0);
        brakePads.setPurchaseRate(620.0);
        brakePads.setOpeningStock(4);
        dm.items().saveItem(brakePads);

        engineOil = new ItemRecord("it_oil", "Engine Oil 10W40 (1L)", "2710", "LTR", 480.0, 18.0);
        engineOil.setPurchaseRate(310.0);
        dm.items().saveItem(engineOil);

        sparkPlugs = new ItemRecord("it_plug", "Spark Plug Iridium", "8511", "PCS", 320.0, 28.0);
        sparkPlugs.setPurchaseRate(190.0);
        sparkPlugs.setOpeningStock(12);
        dm.items().saveItem(sparkPlugs);
    }

    private BillItem saleLine(ItemRecord it, double qty) {
        // Sale lines carry the catalog id so stock OUT is tracked
        return new BillItem(it.getId(), it.getName(), it.getHsn(), qty, it.getUnit(), it.getRate(), it.getGst(), 0);
    }

    private BillItem purchaseLine(ItemRecord it, double qty, double cost) {
        return new BillItem(it.getId(), it.getName(), it.getHsn(), qty, it.getUnit(), cost, it.getGst(), 0);
    }

    // ------------------------------------------------------------------
    // Step 2 — purchase cycle (inward)
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void purchaseBillsRecordStockInPayablesAndItc() {
        // PUR-0001: 20 sets brake pads @ 620 + 40 L oil @ 310, intra-state (MH), on credit
        PurchaseBill p1 = new PurchaseBill();
        p1.setId("pur_001");
        p1.setBillNo("PUR-0001");
        p1.setSupplierBillNo("OEM/1182");
        p1.setDate("2026-09-01");
        p1.setSupplierId(oemDealer.getId());
        p1.setSupplierName(oemDealer.getName());
        p1.setSupplierGstin(oemDealer.getGst());
        p1.setItems(List.of(purchaseLine(brakePads, 20, 620), purchaseLine(engineOil, 40, 310)));
        p1.setFreight(0);
        p1.setTotals(PurchaseService.computePurchaseTotals(p1.getItems(), 0, false));
        dm.savePurchase(p1);

        // Taxable = 20*620 + 40*310 = 12400 + 12400 = 24800; GST 18% = 4464 → CGST 2232 SGST 2232
        BillTotals t1 = p1.getTotals();
        assertEquals(24800.0, t1.getTaxable(), 0.01, "purchase taxable value");
        assertEquals(2232.0, t1.getCgst(), 0.01, "CGST on intra-state purchase");
        assertEquals(2232.0, t1.getSgst(), 0.01, "SGST on intra-state purchase");
        // GST is charged ON TOP of taxable value (standard GST-inclusive=false pricing):
        // grand total = 24800 + 4464 = 29264
        assertEquals(29264.0, t1.getGrandTotal(), 0.01, "grand total = taxable + GST");
        assertFalse(p1.isPaid(), "credit purchase is unpaid");

        // PUR-0002: 5 torque wrenches equivalent value 30000 taxable from Delhi importer, IGST 18%
        PurchaseBill p2 = new PurchaseBill();
        p2.setId("pur_002");
        p2.setBillNo("PUR-0002");
        p2.setSupplierBillNo("DTI/4411");
        p2.setDate("2026-09-03");
        p2.setSupplierId(toolImporter.getId());
        p2.setSupplierName(toolImporter.getName());
        p2.setSupplierGstin(toolImporter.getGst());
        p2.setItems(List.of(new BillItem("", "Torque Wrench Set (non-catalog)", "", 5, "PCS", 6000.0, 18.0, 0)));
        p2.setFreight(500); // freight inward (direct expense in Tally)
        p2.setTotals(PurchaseService.computePurchaseTotals(p2.getItems(), 0, true));
        dm.savePurchase(p2);

        assertEquals(30000.0, p2.getTotals().getTaxable(), 0.01);
        assertEquals(5400.0, p2.getTotals().getIgst(), 0.01, "IGST on inter-state purchase");
        assertEquals(35900.0, p2.getAmountPayable(), 0.01, "payable includes freight");

        // Stock after purchases: pads 4+20=24, oil 0+40=40, plugs 12 (no movement)
        Map<String, Double> stock = dm.getStockBalances();
        assertEquals(24.0, stock.get("it_pads"), 0.001, "brake pads stock after purchase");
        assertEquals(40.0, stock.get("it_oil"), 0.001, "engine oil stock after purchase");
        assertEquals(12.0, stock.get("it_plug"), 0.001, "spark plugs unchanged");
    }

    @Test
    @Order(2)
    void supplierLedgerShowsTrueDubleEntryBalances() {
        List<PurchaseBill> purchases = dm.getAllPurchases();

        // OEM dealer: one credit bill 29264 → payable 29264
        double oemBal = balanceOf(oemDealer, purchases);
        assertEquals(29264.0, oemBal, 0.01, "OEM payable = credit purchase");

        // Tool importer: opening 1500 + credit purchase 35900 = 37400
        double toolsBal = balanceOf(toolImporter, purchases);
        assertEquals(37400.0, toolsBal, 0.01, "importer payable = opening + credit purchase");
    }

    private double balanceOf(Supplier s, List<PurchaseBill> purchases) {
        double bal = s.getOpeningBalance();
        for (PurchaseBill p : purchases) {
            if (s.getId().equals(p.getSupplierId()) && !p.isPaid()) {
                bal += p.getAmountPayable() - p.getPaidAmount();
            }
        }
        return bal;
    }

    // ------------------------------------------------------------------
    // Step 3 — sales cycle (outward) with stock OUT
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    void salesBillsDecrementStockAndComputeGst() {
        // INV-0001 (Sept 5): fleet customer B2B — 6 pads, 15 oil, intra-state
        Bill inv1 = new Bill();
        inv1.setId("bill_fleet1");
        inv1.setBillNo("INV-0001");
        inv1.setDate("2026-09-05");
        inv1.setDocType(DocType.INVOICE);
        inv1.setStatus(BillStatus.UNPAID);
        inv1.setBuyerName(fleetCustomer.getName());
        inv1.setItems(List.of(saleLine(brakePads, 6), saleLine(engineOil, 15)));
        inv1.setTotals(BillingService.computeTotals(inv1.getItems(), 0, false));
        dm.saveBill(inv1);

        // Taxable = 6*950 + 15*480 = 5700 + 7200 = 12900; GST 18% = 2322 → grand 15222
        assertEquals(12900.0, inv1.getTotals().getTaxable(), 0.01);
        assertEquals(15222.0, inv1.getTotals().getGrandTotal(), 0.01);
        inv1GrandTotal = inv1.getTotals().getGrandTotal();

        // INV-0002 (Sept 7): walk-in B2C cash — 2 plugs, 5 oil; PAID at counter
        Bill inv2 = new Bill();
        inv2.setId("bill_walkin1");
        inv2.setBillNo("INV-0002");
        inv2.setDate("2026-09-07");
        inv2.setDocType(DocType.INVOICE);
        inv2.setStatus(BillStatus.PAID);
        inv2.setBuyerName("Walk-in Customer");
        inv2.setItems(List.of(saleLine(sparkPlugs, 2), saleLine(engineOil, 5)));
        inv2.setTotals(BillingService.computeTotals(inv2.getItems(), 0, false));
        inv2.getPayments().add(new BillPayment("pmt_1", "2026-09-07", inv2.getTotals().getGrandTotal(),
                PaymentMethod.CASH, "", "Counter sale"));
        dm.saveBill(inv2);

        // CA note: plugs carry 28% GST → 2*320 = 640 + 179.20 = 819.20;
        // oil 5*480 = 2400 + 432 = 2832 → before-round 3651.20 → grand 3651
        assertEquals(3040.0, inv2.getTotals().getTaxable(), 0.01);
        assertEquals(611.20, inv2.getTotals().getCgst() + inv2.getTotals().getSgst(), 0.02);
        assertEquals(3651.0, inv2.getTotals().getGrandTotal(), 0.01);
        inv2GrandTotal = inv2.getTotals().getGrandTotal();
        Map<String, Double> stock = dm.getStockBalances();
        assertEquals(18.0, stock.get("it_pads"), 0.001, "pads after sale");
        assertEquals(20.0, stock.get("it_oil"), 0.001, "oil after sales");
        assertEquals(10.0, stock.get("it_plug"), 0.001, "plugs after sale");
    }

    // ------------------------------------------------------------------
    // Step 4 — money: partial payments both directions
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    void partialPaymentsAdjustBothSides() {
        // Owner pays OEM dealer ₹10,000 of the 29,264 owed
        List<PurchaseBill> purchases = dm.getAllPurchases();
        PurchaseBill p1 = purchases.stream().filter(p -> p.getId().equals("pur_001")).findFirst().orElseThrow();
        p1.getPayments().add(new BillPayment("pmt_oem_1", "2026-09-08", 10000,
                PaymentMethod.CASH, "NEFT-8811", "Bank / NEFT"));
        dm.savePurchase(p1);
        assertFalse(p1.isPaid(), "partial payment should not mark paid");
        assertEquals(10000.0, p1.getPaidAmount(), 0.01);

        // OEM payable now 29264 - 10000 = 19264
        assertEquals(19264.0, balanceOf(oemDealer, dm.getAllPurchases()), 0.01, "payable after part-payment");

        // Fleet customer pays ₹10,000 of 15,222 invoice (recorded on bill)
        List<Bill> bills = dm.getAllBills();
        Bill inv1 = bills.stream().filter(b -> b.getId().equals("bill_fleet1")).findFirst().orElseThrow();
        inv1.getPayments().add(new BillPayment("pmt_fleet_1", "2026-09-10", 10000,
                PaymentMethod.CASH, "UTR-2201", "Bank / NEFT"));
        dm.saveBill(inv1);

        // Debtors from this bill = 15222 - 10000 = 5222
        FinancialService svc = new FinancialService();
        double debtors = svc.sundryDebtors(dm.getAllBills());
        // walk-in bill is PAID (no receivable), fleet partially paid → 5222
        assertEquals(5222.0, debtors, 0.01, "sundry debtors after part receipt");
    }

    // ------------------------------------------------------------------
    // Step 5 — expenses (direct & indirect)
    // ------------------------------------------------------------------

    @Test
    @Order(5)
    void expensesClassifyIntoDirectAndIndirect() {
        Expense rent = new Expense("exp_rent", "2026-09-01", "Office Rent", "September shop rent", 15000, "Bank / NEFT");
        Expense freight = new Expense("exp_fr", "2026-09-04", "Freight Inward", "Local transport of spares", 800, "Cash");

        dm.saveExpense(rent);
        dm.saveExpense(freight);

        assertTrue(Expense.isDirect("Freight Inward"), "freight is a direct head");
        assertFalse(Expense.isDirect("Office Rent"), "rent is an indirect head");

        FinancialService svc = new FinancialService();
        assertEquals(800.0, svc.directExpenses(dm.getAllExpenses(), "", ""), 0.01);
        assertEquals(15000.0, svc.indirectExpenses(dm.getAllExpenses(), "", ""), 0.01);
    }

    // ------------------------------------------------------------------
    // Step 6 — financial statements (what the CA checks)
    // ------------------------------------------------------------------

    @Test
    @Order(6)
    void financialStatementsMatchAccountantArithmetic() {
        FinancialService svc = new FinancialService();
        var f = svc.compute(dm.getAllBills(), dm.getAllPurchases(), dm.getAllExpenses(),
                List.of(oemDealer, toolImporter), dm.getAllItems(), dm.getStockBalances(), "", "");

        // Sales = fleet 15222 + walk-in grand (captured at save; plugs are 28% GST → 3651).
        // The engine is authoritative; assert it equals the sum of the two saved grand totals.
        double expectedSales = inv1GrandTotal + walkInGrandTotal();
        assertEquals(expectedSales, f.salesRevenue(), 0.02, "sales = sum of bill grand totals");

        // Output GST = fleet 2322 + walk-in 611.20 (plugs 28% = 179.20, oil 18% = 432) = 2933.20
        assertEquals(2933.20, f.outputGst(), 0.02, "output GST on sales");
        // ITC = 2232+2232 (p1) + 5400 (p2) = 9864
        assertEquals(9864.0, f.inputCredit(), 0.02, "input tax credit");
        // Net GST = 2933.20 − 9864 = −6930.80 → ITC carry-forward (not payable)
        assertEquals(2933.20 - 9864.0, f.netTaxPayable(), 0.02, "net GST (credit carry-forward)");

        // Trading: opening stock = 4*620 + 12*190 = 2480 + 2280 = 4760
        assertEquals(4760.0, f.openingStockValue(), 0.02, "opening stock at cost");
        // Purchases taxable in period = 24800 + 30000 = 54800 (freight NOT in taxable)
        assertEquals(54800.0, f.purchasesValue(), 0.02, "purchases taxable value");
        // Closing stock: pads 18*620 + oil 20*310 + plugs 10*190 = 11160 + 6200 + 1900 = 19260
        assertEquals(19260.0, f.inventoryValue(), 0.02, "closing stock at cost");
        // GP = (Sales 18873 + Closing 19260) − (Opening 4760 + Purchases 54800 + Direct 800) = −22227.00
        // (Correct accounting: heavy stocking-up month → book loss on trading account)
        assertEquals(-22227.00, f.grossProfit(), 0.02, "gross profit per trading account");
        // NP = GP − Indirect 15000 = −37227.00
        assertEquals(-37227.00, f.netProfit(), 0.02, "net profit after indirect expenses");

        // Debtors 5222 (fleet), Creditors = OEM 19264 + tools 37400 = 56664
        assertEquals(5222.0, f.sundryDebtors(), 0.02, "debtors");
        assertEquals(56664.0, f.sundryCreditors(), 0.02, "creditors incl. opening balance");

        // Cash: receipts (fleet part 10000 + walk-in 3651) − purchase payment 10000 − expenses 15800 = −12149
        assertEquals(13651.0 - 25800.0, f.cashInHand(), 0.02, "net cash position");
    }

    @Test
    @Order(7)
    void stockSummaryAndProfitabilityReports() {
        FinancialService svc = new FinancialService();
        List<FinancialService.StockSummaryRow> summary =
                svc.stockSummary(dm.getAllItems(), dm.getStockBalances(), dm.getAllPurchases(), dm.getAllBills(), "", "");

        FinancialService.StockSummaryRow pads = summary.stream()
                .filter(r -> r.itemId().equals("it_pads")).findFirst().orElseThrow();
        assertEquals(4.0, pads.openingQty(), 0.001, "pads opening");
        assertEquals(20.0, pads.inQty(), 0.001, "pads inwards");
        assertEquals(6.0, pads.outQty(), 0.001, "pads outwards");
        assertEquals(18.0, pads.closingQty(), 0.001, "pads closing");
        assertEquals(620.0, pads.costRate(), 0.01, "pads cost from purchase rate");
        assertEquals(11160.0, pads.closingValue(), 0.01, "pads closing value");

        List<FinancialService.ItemProfitRow> profit =
                svc.itemProfitability(dm.getAllItems(), dm.getAllPurchases(), dm.getAllBills(), "", "");

        // Pads: sold 6 @ 950 = 5700; avg cost 620 → COGS 3720 → GP 1980 (34.7%)
        FinancialService.ItemProfitRow padsP = profit.stream()
                .filter(r -> r.itemId().equals("it_pads")).findFirst().orElseThrow();
        assertEquals(5700.0, padsP.salesValue(), 0.01);
        assertEquals(3720.0, padsP.cogs(), 0.01);
        assertEquals(1980.0, padsP.grossProfit(), 0.01);
        assertEquals(34.74, padsP.gpPercent(), 0.05);

        // Oil: sold 20 @ 480 = 9600; cost 310 → COGS 6200 → GP 3400
        FinancialService.ItemProfitRow oilP = profit.stream()
                .filter(r -> r.itemId().equals("it_oil")).findFirst().orElseThrow();
        assertEquals(3400.0, oilP.grossProfit(), 0.01);

        // Plugs: sold 2 @ 320 = 640; cost 190 → GP 260
        FinancialService.ItemProfitRow plugP = profit.stream()
                .filter(r -> r.itemId().equals("it_plug")).findFirst().orElseThrow();
        assertEquals(260.0, plugP.grossProfit(), 0.01);
    }

    // ------------------------------------------------------------------
    // Step 7 — deleting a purchase reverses stock (immutability guarantee)
    // ------------------------------------------------------------------

    @Test
    @Order(8)
    void deletingPurchaseReversesStockOnly() {
        // Extra purchase of 2 pads then delete it → stock must return to 18
        PurchaseBill p3 = new PurchaseBill();
        p3.setId("pur_003");
        p3.setBillNo("PUR-0003");
        p3.setDate("2026-09-09");
        p3.setSupplierId(oemDealer.getId());
        p3.setSupplierName(oemDealer.getName());
        p3.setItems(List.of(purchaseLine(brakePads, 2, 600)));
        p3.setTotals(PurchaseService.computePurchaseTotals(p3.getItems(), 0, false));
        dm.savePurchase(p3);
        assertEquals(20.0, dm.getStockBalances().get("it_pads"), 0.001, "stock after extra purchase");

        dm.deletePurchase("pur_003");
        assertEquals(18.0, dm.getStockBalances().get("it_pads"), 0.001, "stock restored after delete");
    }

    // ------------------------------------------------------------------
    // Step 8 — daybook chronology & completeness
    // ------------------------------------------------------------------

    @Test
    @Order(9)
    void daybookCapturesEveryVoucherType() {
        FinancialService svc = new FinancialService();
        var rows = svc.buildDaybook(dm.getAllBills(), dm.getAllPurchases(), dm.getAllExpenses());

        assertTrue(rows.stream().anyMatch(r -> r.type().equals("Sale")), "daybook has sales");
        assertTrue(rows.stream().anyMatch(r -> r.type().equals("Receipt")), "daybook has receipts");
        assertTrue(rows.stream().anyMatch(r -> r.type().equals("Purchase")), "daybook has purchases");
        assertTrue(rows.stream().anyMatch(r -> r.type().equals("Payment")), "daybook has payments");
        assertTrue(rows.stream().anyMatch(r -> r.type().equals("Expense")), "daybook has expenses");

        // Chronological order
        for (int i = 1; i < rows.size(); i++) {
            String prev = rows.get(i - 1).date();
            String cur = rows.get(i).date();
            assertTrue(prev.compareTo(cur) <= 0, "daybook must be date-sorted: " + prev + " > " + cur);
        }
    }

    // ------------------------------------------------------------------
    // Step 9 — user partitioning: another workshop owner sees nothing
    // ------------------------------------------------------------------

    @Test
    @Order(10)
    void anotherUserSeesNoWorkshopData() {
        AuthSessionManager.setActiveSession(new com.invoicestudio.model.UserSession(
                "uid_rival", "rival@garage.in", "Rival Garage",
                "tok", "ref", System.currentTimeMillis() + 3600_000L, true));
        try {
            assertEquals(0, dm.suppliers().getAllSuppliers().size(), "no supplier leak");
            assertEquals(0, dm.getAllPurchases().size(), "no purchase leak");
            assertEquals(0, dm.getAllBills().size(), "no bill leak");
            assertEquals(0, dm.getAllExpenses().size(), "no expense leak");
        } finally {
            AuthSessionManager.setActiveSession(new com.invoicestudio.model.UserSession(
                    "uid_workshop", "sharma@workshop.in", "Sharma Auto Workshop",
                    "tok", "ref", System.currentTimeMillis() + 3600_000L, true));
        }
    }
}
