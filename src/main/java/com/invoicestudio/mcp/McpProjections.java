package com.invoicestudio.mcp;

import com.invoicestudio.model.Bill;
import com.invoicestudio.model.BillItem;
import com.invoicestudio.model.BillPayment;
import com.invoicestudio.model.BillStatus;
import com.invoicestudio.model.BillTotals;
import com.invoicestudio.model.Expense;
import com.invoicestudio.model.ItemRecord;
import com.invoicestudio.model.PurchaseBill;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Transaction;
import com.invoicestudio.service.FinancialService;
import com.invoicestudio.service.PurchaseService;
import com.invoicestudio.ui.DataManager;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.invoicestudio.mcp.McpArgs.mapOf;

/**
 * Read-model projections and report builders for the MCP tool surface
 * (skill rule 5.2 role 5: pure model→JSON renderers). Extracted verbatim
 * from {@link McpToolRegistry}; behavior unchanged.
 */
final class McpProjections {

    private static final FinancialService FIN = new FinancialService();

    private McpProjections() {}

    // ─── List projections ──────────────────────────────────────────────────

    static List<Map<String, Object>> buyersMap(DataManager dm, String query, int limit) {
        return dm.getAllBuyers().stream()
                .filter(b -> McpArgs.matches(query, b.getName(), b.getPhone(), b.getGst(), b.getCity()))
                .limit(limit)
                .map(b -> mapOf("id", b.getId(), "name", b.getName(), "phone", b.getPhone(),
                        "gst", b.getGst(), "state", b.getState(), "stateCode", b.getEffectiveStateCode(),
                        "city", b.getCity(), "creditLimit", b.getCreditLimit()))
                .collect(java.util.stream.Collectors.toList());
    }

    static List<Map<String, Object>> suppliersMap(DataManager dm, String query, int limit) {
        List<PurchaseBill> purchases = dm.getAllPurchases();
        return dm.getAllSuppliers().stream()
                .filter(s -> McpArgs.matches(query, s.getName(), s.getPhone(), s.getGst(), s.getCity()))
                .limit(limit)
                .map(s -> {
                    double bal = s.getOpeningBalance();
                    for (PurchaseBill p : purchases) {
                        if (s.getId() != null && s.getId().equals(p.getSupplierId()) && !p.isPaid()) {
                            bal += p.getAmountPayable() - p.getPaidAmount();
                        }
                    }
                    return mapOf("id", s.getId(), "name", s.getName(), "phone", s.getPhone(),
                            "gst", s.getGst(), "state", s.getState(), "stateCode", s.getStateCode(),
                            "city", s.getCity(), "creditPeriodDays", s.getCreditPeriodDays(),
                            "payableBalance", PurchaseService.round2(bal));
                })
                .collect(java.util.stream.Collectors.toList());
    }

    static List<Map<String, Object>> itemsMap(DataManager dm, String query, int limit) {
        Map<String, Double> stock = dm.getStockBalances();
        return dm.getAllItems().stream()
                .filter(it -> McpArgs.matches(query, it.getName(), it.getHsn(), it.getCategoryName()))
                .limit(limit)
                .map(it -> mapOf("id", it.getId(), "name", it.getName(), "hsn", it.getHsn(),
                        "unit", it.getUnit(), "rate", it.getRate(), "gst", it.getGst(),
                        "purchaseRate", it.getPurchaseRate(), "reorderLevel", it.getReorderLevel(),
                        "stock", stock.getOrDefault(it.getId(), 0.0), "category", it.getCategoryName()))
                .collect(java.util.stream.Collectors.toList());
    }

    static List<Map<String, Object>> templatesMap(DataManager dm) {
        return dm.templates().getAllTemplates().stream()
                .map(t -> mapOf("id", t.getId(), "name", t.getName(),
                        "pageSize", String.valueOf(t.getPage().getSizeName()),
                        "elements", t.getElements() == null ? 0 : t.getElements().size()))
                .collect(java.util.stream.Collectors.toList());
    }

    static List<Map<String, Object>> billsMap(DataManager dm, String query, String status, int limit) {
        return dm.getAllBills().stream()
                .filter(b -> status == null || status.isBlank()
                        || b.getStatus().name().equalsIgnoreCase(status.trim()))
                .filter(b -> McpArgs.matches(query, b.getId(), b.getBillNo(), b.getBuyerName()))
                .sorted(java.util.Comparator.comparing(Bill::getDate,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .limit(limit)
                .map(McpProjections::billSummary)
                .collect(java.util.stream.Collectors.toList());
    }

    static Map<String, Object> billSummary(Bill b) {
        double paid = b.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
        if (paid == 0 && b.getStatus() == BillStatus.PAID) paid = b.getTotals().getGrandTotal();
        return mapOf("id", b.getId(), "billNo", b.getBillNo(), "date", b.getDate(),
                "buyer", b.getBuyerName(), "grandTotal", b.getTotals().getGrandTotal(),
                "paid", PurchaseService.round2(paid),
                "status", b.getStatus().name());
    }

    static Map<String, Object> billFull(Bill b) {
        Map<String, Object> m = billSummary(b);
        List<Map<String, Object>> lines = new ArrayList<>();
        if (b.getItems() != null) {
            for (BillItem it : b.getItems()) {
                lines.add(mapOf("itemId", it.getId(), "desc", it.getDesc(), "hsn", it.getHsn(),
                        "qty", it.getQty(), "unit", it.getUnit(), "rate", it.getRate(),
                        "gst", it.getGst(), "discPct", it.getDiscPct(),
                        "amount", PurchaseService.round2(it.getQty() * it.getRate() * (1 - it.getDiscPct() / 100.0))));
            }
        }
        m.put("items", lines);
        m.put("totals", totalsMap(b.getTotals()));
        List<Map<String, Object>> pays = new ArrayList<>();
        for (BillPayment p : b.getPayments()) {
            pays.add(mapOf("date", p.getDate(), "amount", p.getAmount(),
                    "method", p.getMethod() != null ? p.getMethod().name() : "", "reference", p.getReference()));
        }
        m.put("payments", pays);
        return m;
    }

    static List<Map<String, Object>> purchasesMap(DataManager dm, String query, int limit) {
        return dm.getAllPurchases().stream()
                .filter(p -> McpArgs.matches(query, p.getBillNo(), p.getSupplierBillNo(), p.getSupplierName()))
                .limit(limit)
                .map(p -> mapOf("id", p.getId(), "billNo", p.getBillNo(), "supplierBillNo", p.getSupplierBillNo(),
                        "date", p.getDate(), "supplier", p.getSupplierName(),
                        "grandTotal", p.getTotals() != null ? p.getTotals().getGrandTotal() : 0.0,
                        "itc", p.getTotals() != null
                                ? PurchaseService.round2(p.getTotals().getCgst() + p.getTotals().getSgst() + p.getTotals().getIgst())
                                : 0.0,
                        "paid", p.getPaidAmount(), "fullyPaid", p.isPaid()))
                .collect(java.util.stream.Collectors.toList());
    }

    static Map<String, Object> expenseMap(Expense e) {
        return mapOf("id", e.getId(), "date", e.getDate(), "category", e.getCategory(),
                "head", Expense.isDirect(e.getCategory()) ? "DIRECT" : "INDIRECT",
                "amount", e.getAmount(), "paymentMode", e.getPaymentMode(),
                "payee", e.getPayee(), "description", e.getDescription());
    }

    static Map<String, Object> transactionMap(Transaction t) {
        return mapOf("id", t.getId(), "date", t.getTransactionDate(), "type", t.getTransactionType(),
                "billNo", t.getBillNo(), "buyer", t.getBuyerName(), "amount", t.getAmount());
    }

    static Map<String, Object> totalsMap(BillTotals t) {
        return mapOf("subtotal", t.getSubtotal(), "discount", t.getDiscount(), "taxable", t.getTaxable(),
                "cgst", t.getCgst(), "sgst", t.getSgst(), "igst", t.getIgst(),
                "roundOff", t.getRoundOff(), "grandTotal", t.getGrandTotal());
    }

    static Map<String, Object> settingsMap(Settings s) {
        var b = s.getBusiness();
        return mapOf("businessName", b.getName(), "gstin", b.getGstin(), "state", b.getState(),
                "stateCode", b.getStateCode(), "phone", b.getPhone(), "email", b.getEmail(),
                "currency", s.getCurrency(), "billNoPrefix", s.getBillNoPrefix(),
                "billNoNext", s.getBillNoNext(), "billNoDigits", s.getBillNoDigits(),
                "interStateDefault", s.isInterState());
    }


    // ─── Reports ───────────────────────────────────────────────────────────

    static List<Map<String, Object>> stockReport(DataManager dm) {
        Map<String, Double> balances = dm.getStockBalances();
        Map<String, ItemRecord> byId = new HashMap<>();
        for (ItemRecord it : dm.getAllItems()) byId.put(it.getId(), it);
        String fyStart = LocalDate.now().getMonthValue() >= 4
                ? LocalDate.now().getYear() + "-04-01"
                : (LocalDate.now().getYear() - 1) + "-04-01";
        String today = LocalDate.now().toString();
        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> low = new ArrayList<>();
        for (var row : FIN.stockSummary(dm.getAllItems(), balances,
                dm.getAllPurchases(), dm.getAllBills(), fyStart, today)) {
            rows.add(mapOf("itemId", row.itemId(), "name", row.name(), "unit", row.unit(),
                    "opening", row.openingQty(), "in", row.inQty(), "out", row.outQty(),
                    "closing", row.closingQty(), "costRate", row.costRate(), "closingValue", row.closingValue()));
            ItemRecord cat = byId.get(row.itemId());
            if (cat != null && row.closingQty() <= cat.getReorderLevel()) {
                low.add(mapOf("itemId", row.itemId(), "name", row.name(),
                        "closing", row.closingQty(), "reorderLevel", cat.getReorderLevel()));
            }
        }
        return List.of(mapOf("stock", rows, "lowStock", low));
    }

    static List<Map<String, Object>> profitabilityReport(DataManager dm) {
        String fyStart = LocalDate.now().getMonthValue() >= 4
                ? LocalDate.now().getYear() + "-04-01"
                : (LocalDate.now().getYear() - 1) + "-04-01";
        String today = LocalDate.now().toString();
        return FIN.itemProfitability(dm.getAllItems(), dm.getAllPurchases(), dm.getAllBills(), fyStart, today)
                .stream()
                .map(r -> mapOf("itemId", r.itemId(), "name", r.name(), "qtySold", r.qtySold(),
                        "salesValue", r.salesValue(), "avgCost", r.avgCost(), "cogs", r.cogs(),
                        "grossProfit", r.grossProfit(), "gpPercent", r.gpPercent()))
                .collect(java.util.stream.Collectors.toList());
    }

    static Map<String, Object> financialSummary(DataManager dm, Map<String, Object> args) {
        String from = McpArgs.strOr(args, "from", LocalDate.now().getMonthValue() >= 4
                ? LocalDate.now().getYear() + "-04-01"
                : (LocalDate.now().getYear() - 1) + "-04-01");
        String to = McpArgs.strOr(args, "to", LocalDate.now().toString());
        FinancialService.Financials f = FIN.compute(dm.getAllBills(), dm.getAllPurchases(), dm.getAllExpenses(),
                dm.getAllSuppliers(), dm.getAllItems(), dm.getStockBalances(), from, to);
        return mapOf(
                "period", mapOf("from", from, "to", to),
                "trading", mapOf("salesRevenue", f.salesRevenue(), "openingStock", f.openingStockValue(),
                        "purchases", f.purchasesValue(), "directExpenses", f.directExpenses(),
                        "closingStock", f.closingStockValue(), "grossProfit", f.grossProfit()),
                "profitAndLoss", mapOf("grossProfit", f.grossProfit(),
                        "indirectExpenses", f.indirectExpenses(), "netProfit", f.netProfit()),
                "balanceSheet", mapOf("sundryDebtors", f.sundryDebtors(), "sundryCreditors", f.sundryCreditors(),
                        "cashInHand", f.cashInHand(), "inventoryValue", f.inventoryValue(),
                        "gstPayable", f.gstPayable(), "totalAssets", f.totalAssets(),
                        "totalLiabilities", f.totalLiabilities()),
                "gst", mapOf("outputGst", f.outputGst(), "inputCredit", f.inputCredit(),
                        "netTaxPayable", f.netTaxPayable()));
    }
}
