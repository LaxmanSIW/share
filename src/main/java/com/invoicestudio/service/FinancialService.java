package com.invoicestudio.service;

import com.invoicestudio.model.Bill;
import com.invoicestudio.model.BillItem;
import com.invoicestudio.model.BillPayment;
import com.invoicestudio.model.BillStatus;
import com.invoicestudio.model.Expense;
import com.invoicestudio.model.ItemRecord;
import com.invoicestudio.model.PurchaseBill;
import com.invoicestudio.model.Supplier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Financial statements engine — Trading/P&L, Balance Sheet, GST summary
 * (GSTR-style outward/inward/ITC/net tax) and a unified Daybook.
 *
 * Pure computation over the existing data caches; no DB access, so it is
 * directly unit-testable. Date filtering uses ISO yyyy-MM-dd string compare,
 * matching the rest of the codebase.
 */
public class FinancialService {

    // ------------------------------------------------------------------
    // Result records
    // ------------------------------------------------------------------

    public record DaybookEntry(String date, String type, String particulars, double inflow, double outflow) {}

    /** One row of the Tally-style Stock Summary (opening/in/out/closing + value). */
    public record StockSummaryRow(String itemId, String name, String unit,
                                  double openingQty, double inQty, double outQty, double closingQty,
                                  double costRate, double closingValue) {}

    /** One row of item-wise profitability (sales vs cost → gross profit). */
    public record ItemProfitRow(String itemId, String name,
                                double qtySold, double salesValue,
                                double avgCost, double cogs, double grossProfit, double gpPercent) {}

    public record Financials(
            // Trading / P&L
            double salesRevenue,
            double openingStockValue,
            double purchasesValue,
            double directExpenses,
            double closingStockValue,
            double grossProfit,
            double indirectExpenses,
            double netProfit,
            // Balance sheet
            double sundryDebtors,
            double sundryCreditors,
            double gstPayable,
            double cashInHand,
            double inventoryValue,
            double totalLiabilities,
            double totalAssets,
            // GST
            double outputGst,
            double inputCredit,
            double netTaxPayable,
            List<DaybookEntry> daybook) {}

    // ------------------------------------------------------------------
    // Computation
    // ------------------------------------------------------------------

    /** Sales revenue = sum of non-cancelled bill grand totals. */
    public double salesRevenue(List<Bill> bills, String fromDate, String toDate) {
        double sum = 0;
        for (Bill b : bills) {
            if (b.getStatus() == BillStatus.CANCELLED) continue;
            if (inRange(b.getDate(), fromDate, toDate)) sum += b.getTotals().getGrandTotal();
        }
        return PurchaseService.round2(sum);
    }

    /** Output GST on sales (CGST+SGST+IGST), non-cancelled bills. */
    public double outputGst(List<Bill> bills, String fromDate, String toDate) {
        double sum = 0;
        for (Bill b : bills) {
            if (b.getStatus() == BillStatus.CANCELLED) continue;
            if (!inRange(b.getDate(), fromDate, toDate)) continue;
            sum += b.getTotals().getCgst() + b.getTotals().getSgst() + b.getTotals().getIgst();
        }
        return PurchaseService.round2(sum);
    }

    /** Input Tax Credit from purchase bills. */
    public double inputCredit(List<PurchaseBill> purchases, String fromDate, String toDate) {
        double sum = 0;
        for (PurchaseBill p : purchases) {
            if (!inRange(p.getDate(), fromDate, toDate)) continue;
            if (p.getTotals() == null) continue;
            sum += p.getTotals().getCgst() + p.getTotals().getSgst() + p.getTotals().getIgst();
        }
        return PurchaseService.round2(sum);
    }

    /** Sundry Debtors: unpaid sales bills (receivable from buyers). */
    public double sundryDebtors(List<Bill> bills) {
        double sum = 0;
        for (Bill b : bills) {
            if (b.getStatus() == BillStatus.CANCELLED) continue;
            double paid = b.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
            if (paid == 0 && b.getStatus() == BillStatus.PAID) paid = b.getTotals().getGrandTotal();
            sum += Math.max(0, b.getTotals().getGrandTotal() - paid);
        }
        return PurchaseService.round2(sum);
    }

    /** Sundry Creditors: supplier opening balances + unpaid purchase bills. */
    public double sundryCreditors(List<Supplier> suppliers, List<PurchaseBill> purchases) {
        double sum = 0;
        for (Supplier s : suppliers) {
            double bal = s.getOpeningBalance();
            for (PurchaseBill p : purchases) {
                if (s.getId() != null && s.getId().equals(p.getSupplierId()) && !p.isPaid()) {
                    bal += p.getAmountPayable() - p.getPaidAmount();
                }
            }
            sum += Math.max(0, bal);
        }
        return PurchaseService.round2(sum);
    }

    /** Inventory value from live ledger balances × item purchase rate. */
    public double inventoryValue(List<ItemRecord> items, Map<String, Double> stockBalances) {
        double sum = 0;
        for (ItemRecord it : items) {
            double qty = stockBalances.getOrDefault(it.getId(), 0.0);
            double cost = it.getPurchaseRate() > 0 ? it.getPurchaseRate() : it.getRate();
            if (qty > 0) sum += qty * cost;
        }
        return PurchaseService.round2(sum);
    }

    public double directExpenses(List<Expense> expenses, String fromDate, String toDate) {
        double sum = 0;
        for (Expense e : expenses) {
            if (Expense.isDirect(e.getCategory()) && inRange(e.getDate(), fromDate, toDate)) {
                sum += e.getAmount();
            }
        }
        return PurchaseService.round2(sum);
    }

    public double indirectExpenses(List<Expense> expenses, String fromDate, String toDate) {
        double sum = 0;
        for (Expense e : expenses) {
            if (!Expense.isDirect(e.getCategory()) && inRange(e.getDate(), fromDate, toDate)) {
                sum += e.getAmount();
            }
        }
        return PurchaseService.round2(sum);
    }

    /**
     * Full statement bundle.
     * Opening stock uses item opening_stock × cost; closing stock uses live
     * ledger balance × cost (Tally's closing-stock derivation).
     */
    public Financials compute(List<Bill> bills,
                              List<PurchaseBill> purchases,
                              List<Expense> expenses,
                              List<Supplier> suppliers,
                              List<ItemRecord> items,
                              Map<String, Double> stockBalances,
                              String fromDate, String toDate) {

        double sales = salesRevenue(bills, fromDate, toDate);
        double outputTax = outputGst(bills, fromDate, toDate);
        double itc = inputCredit(purchases, fromDate, toDate);

        double purchasesValue = 0;
        for (PurchaseBill p : purchases) {
            if (inRange(p.getDate(), fromDate, toDate)) {
                purchasesValue += p.getTotals() != null ? p.getTotals().getTaxable() : 0;
            }
        }
        purchasesValue = PurchaseService.round2(purchasesValue);

        double direct = directExpenses(expenses, fromDate, toDate);
        double indirect = indirectExpenses(expenses, fromDate, toDate);

        double openingStock = 0;
        for (ItemRecord it : items) {
            double cost = it.getPurchaseRate() > 0 ? it.getPurchaseRate() : it.getRate();
            openingStock += it.getOpeningStock() * cost;
        }
        openingStock = PurchaseService.round2(openingStock);

        double closingStock = inventoryValue(items, stockBalances);

        // Trading account: GP = (Sales + Closing Stock) − (Opening Stock + Purchases + Direct Exp)
        double grossProfit = (sales + closingStock) - (openingStock + purchasesValue + direct);
        // P&L: NP = GP − Indirect Expenses
        double netProfit = grossProfit - indirect;

        double debtors = sundryDebtors(bills);
        double creditors = sundryCreditors(suppliers, purchases);
        double gstPayable = Math.max(0, outputTax - itc);
        double cash = cashInHand(bills, purchases, expenses);

        double inventory = closingStock;
        double totalAssets = debtors + cash + inventory + Math.max(0, itc - outputTax);
        double totalLiabilities = creditors + gstPayable + Math.max(0, netProfit);

        List<DaybookEntry> daybook = buildDaybook(bills, purchases, expenses);

        return new Financials(sales, openingStock, purchasesValue, direct, closingStock,
                PurchaseService.round2(grossProfit), indirect, PurchaseService.round2(netProfit),
                debtors, creditors, gstPayable, cash, inventory,
                PurchaseService.round2(totalLiabilities), PurchaseService.round2(totalAssets),
                outputTax, itc, PurchaseService.round2(outputTax - itc), daybook);
    }

    /** Simplified cash position: payments received − payments made − expenses. */
    public double cashInHand(List<Bill> bills, List<PurchaseBill> purchases, List<Expense> expenses) {
        double in = 0;
        for (Bill b : bills) {
            if (b.getStatus() == BillStatus.CANCELLED) continue;
            double paid = b.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
            if (paid == 0 && b.getStatus() == BillStatus.PAID) paid = b.getTotals().getGrandTotal();
            in += paid;
        }
        double out = 0;
        for (PurchaseBill p : purchases) {
            out += p.getPaidAmount();
        }
        for (Expense e : expenses) {
            out += e.getAmount();
        }
        return PurchaseService.round2(in - out);
    }

    /** Unified chronological journal: sales, purchases, payments, expenses. */
    public List<DaybookEntry> buildDaybook(List<Bill> bills,
                                           List<PurchaseBill> purchases,
                                           List<Expense> expenses) {
        List<DaybookEntry> rows = new ArrayList<>();
        for (Bill b : bills) {
            if (b.getStatus() == BillStatus.CANCELLED) continue;
            double paid = b.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
            if (paid == 0 && b.getStatus() == BillStatus.PAID) paid = b.getTotals().getGrandTotal();
            rows.add(new DaybookEntry(b.getDate(), "Sale",
                    (b.getBillNo() != null ? b.getBillNo() + " — " : "") + b.getBuyerName(),
                    b.getTotals().getGrandTotal(), 0));
            if (paid > 0) {
                rows.add(new DaybookEntry(b.getDate(), "Receipt",
                        (b.getBillNo() != null ? b.getBillNo() + " — " : "") + b.getBuyerName(),
                        paid, 0));
            }
        }
        for (PurchaseBill p : purchases) {
            rows.add(new DaybookEntry(p.getDate(), "Purchase",
                    (p.getBillNo() != null ? p.getBillNo() + " — " : "") + p.getSupplierName(),
                    0, p.getAmountPayable()));
            double paidAmt = p.getPaidAmount();
            if (paidAmt > 0) {
                for (com.invoicestudio.model.BillPayment bp : p.getPayments()) {
                    rows.add(new DaybookEntry(bp.getDate(), "Payment",
                            p.getBillNo() + " — " + p.getSupplierName()
                                    + (bp.getReference() != null && !bp.getReference().isBlank() ? " ref " + bp.getReference() : ""),
                            0, bp.getAmount()));
                }
                if (p.isPaid() && p.getPayments().isEmpty()) {
                    rows.add(new DaybookEntry(p.getDate(), "Payment",
                            p.getBillNo() + " — " + p.getSupplierName() + " (paid with bill)", 0, paidAmt));
                }
            }
        }
        for (Expense e : expenses) {
            rows.add(new DaybookEntry(e.getDate(), "Expense",
                    e.getCategory() + (e.getDescription().isBlank() ? "" : " — " + e.getDescription()),
                    0, e.getAmount()));
        }
        rows.sort((a, b2) -> {
            String da = a.date() != null ? a.date() : "";
            String db = b2.date() != null ? b2.date() : "";
            int c = da.compareTo(db);
            return c != 0 ? c : a.type().compareTo(b2.type());
        });
        return rows;
    }

    // ------------------------------------------------------------------
    // Stock & profitability reports (Tally Stock Summary / Item P&L)
    // ------------------------------------------------------------------

    /**
     * Stock Summary: per item opening → inwards (purchases) → outwards (sales)
     * → closing quantity with closing value at cost (actual purchase rate or catalog cost).
     *
     * Correctly respects date filtering by accumulating transactions before fromDate
     * into period opening stock, so Closing = Opening + In - Out strictly balances.
     */
    public List<StockSummaryRow> stockSummary(List<ItemRecord> items, Map<String, Double> stockBalances,
                                              List<PurchaseBill> purchases, List<Bill> bills,
                                              String fromDate, String toDate) {
        String from = fromDate != null ? fromDate.trim() : "";
        String to = toDate != null ? toDate.trim() : "";

        Map<String, double[]> priorIn = new HashMap<>();  // itemId -> [qty, value] before fromDate
        Map<String, double[]> priorOut = new HashMap<>(); // itemId -> [qty, value] before fromDate
        Map<String, double[]> in = new HashMap<>();        // itemId -> [qty, value] in [fromDate, toDate]
        Map<String, double[]> out = new HashMap<>();       // itemId -> [qty, value] in [fromDate, toDate]
        Map<String, double[]> allIn = new HashMap<>();     // itemId -> [qty, value] across all purchases

        for (PurchaseBill p : purchases) {
            if (p.getItems() == null) continue;
            String d = p.getDate() != null ? p.getDate().trim() : "";
            boolean isPrior = !from.isBlank() && d.compareTo(from) < 0;
            boolean isInRange = inRange(d, from, to);

            for (BillItem it : p.getItems()) {
                ItemRecord catalog = resolveCatalog(items, it.getId(), it.getDesc());
                if (catalog == null || it.getQty() <= 0) continue;
                String cid = catalog.getId();

                double[] total = allIn.computeIfAbsent(cid, k -> new double[2]);
                total[0] += it.getQty();
                total[1] += it.getAmount();

                if (isPrior) {
                    double[] v = priorIn.computeIfAbsent(cid, k -> new double[2]);
                    v[0] += it.getQty();
                    v[1] += it.getAmount();
                } else if (isInRange) {
                    double[] v = in.computeIfAbsent(cid, k -> new double[2]);
                    v[0] += it.getQty();
                    v[1] += it.getAmount();
                }
            }
        }

        for (Bill b : bills) {
            if (b.getStatus() == BillStatus.CANCELLED || b.getItems() == null) continue;
            String d = b.getDate() != null ? b.getDate().trim() : "";
            boolean isPrior = !from.isBlank() && d.compareTo(from) < 0;
            boolean isInRange = inRange(d, from, to);

            for (BillItem it : b.getItems()) {
                ItemRecord catalog = resolveCatalog(items, it.getId(), it.getDesc());
                if (catalog == null || it.getQty() <= 0) continue;
                String cid = catalog.getId();

                if (isPrior) {
                    double[] v = priorOut.computeIfAbsent(cid, k -> new double[2]);
                    v[0] += it.getQty();
                    v[1] += it.getAmount();
                } else if (isInRange) {
                    double[] v = out.computeIfAbsent(cid, k -> new double[2]);
                    v[0] += it.getQty();
                    v[1] += it.getAmount();
                }
            }
        }

        List<StockSummaryRow> rows = new ArrayList<>();
        for (ItemRecord it : items) {
            double pInQty = priorIn.getOrDefault(it.getId(), new double[2])[0];
            double pOutQty = priorOut.getOrDefault(it.getId(), new double[2])[0];
            double opening = it.getOpeningStock() + pInQty - pOutQty;

            double[] inData = in.getOrDefault(it.getId(), new double[2]);
            double inQty = inData[0];

            double[] outData = out.getOrDefault(it.getId(), new double[2]);
            double outQty = outData[0];

            double closing = opening + inQty - outQty;

            // Unit cost priority:
            // 1. Catalog purchaseRate if set > 0
            // 2. Average purchase cost from in-range purchases
            // 3. Average purchase cost from historical purchases
            // 4. 0 if no purchase cost is known (never inflate to selling rate)
            double cost = it.getPurchaseRate();
            if (cost <= 0 && inData[0] > 0) {
                cost = inData[1] / inData[0];
            }
            if (cost <= 0) {
                double[] hist = allIn.get(it.getId());
                if (hist != null && hist[0] > 0) {
                    cost = hist[1] / hist[0];
                }
            }
            cost = PurchaseService.round2(Math.max(0, cost));

            double closingValue = PurchaseService.round2(Math.max(0, closing) * cost);
            rows.add(new StockSummaryRow(it.getId(), it.getName(), it.getUnit(),
                    opening, inQty, outQty, closing, cost, closingValue));
        }
        rows.sort((a, b2) -> a.name().compareToIgnoreCase(b2.name()));
        return rows;
    }

    /**
     * Item-wise Profitability: qty sold × selling value vs qty sold × average
     * purchase cost → gross profit per item (Tally "Stock Item-wise Profit").
     * Uses actual purchase history (in-range, then all-time) or catalog purchase rate.
     */
    public List<ItemProfitRow> itemProfitability(List<ItemRecord> items,
                                                 List<PurchaseBill> purchases,
                                                 List<Bill> bills,
                                                 String fromDate, String toDate) {
        String from = fromDate != null ? fromDate.trim() : "";
        String to = toDate != null ? toDate.trim() : "";

        // Purchases in range and all-time purchases (for accurate historical cost)
        Map<String, double[]> boughtInRange = new HashMap<>(); // itemId -> [qty, value]
        Map<String, double[]> boughtAllTime = new HashMap<>(); // itemId -> [qty, value]

        for (PurchaseBill p : purchases) {
            if (p.getItems() == null) continue;
            String d = p.getDate() != null ? p.getDate().trim() : "";
            boolean inPeriod = inRange(d, from, to);

            for (BillItem it : p.getItems()) {
                ItemRecord catalog = resolveCatalog(items, it.getId(), it.getDesc());
                if (catalog == null || it.getQty() <= 0) continue;
                String cid = catalog.getId();

                double[] total = boughtAllTime.computeIfAbsent(cid, k -> new double[2]);
                total[0] += it.getQty();
                total[1] += it.getAmount();

                if (inPeriod) {
                    double[] v = boughtInRange.computeIfAbsent(cid, k -> new double[2]);
                    v[0] += it.getQty();
                    v[1] += it.getAmount();
                }
            }
        }

        Map<String, double[]> sold = new HashMap<>(); // itemId -> [qty, salesValue]
        for (Bill b : bills) {
            if (b.getStatus() == BillStatus.CANCELLED || !inRange(b.getDate(), from, to) || b.getItems() == null) continue;
            for (BillItem it : b.getItems()) {
                ItemRecord catalog = resolveCatalog(items, it.getId(), it.getDesc());
                if (catalog == null || it.getQty() <= 0) continue;
                double[] v = sold.computeIfAbsent(catalog.getId(), k -> new double[2]);
                v[0] += it.getQty();
                v[1] += it.getAmount();
            }
        }

        List<ItemProfitRow> rows = new ArrayList<>();
        for (Map.Entry<String, double[]> e : sold.entrySet()) {
            ItemRecord item = null;
            for (ItemRecord it : items) {
                if (it.getId().equals(e.getKey())) { item = it; break; }
            }
            if (item == null) continue;

            double qtySold = e.getValue()[0];
            double salesValue = e.getValue()[1];

            // Unit cost priority:
            // 1. In-range average purchase rate (if bought during this period)
            // 2. Catalog purchase rate if set > 0
            // 3. Historical all-time purchase average (if bought previously)
            // 4. 0 if no purchase cost is known (never inflate to selling rate)
            double avgCost = 0;
            double[] bpIn = boughtInRange.get(e.getKey());
            if (bpIn != null && bpIn[0] > 0) {
                avgCost = bpIn[1] / bpIn[0];
            } else if (item.getPurchaseRate() > 0) {
                avgCost = item.getPurchaseRate();
            } else {
                double[] bpAll = boughtAllTime.get(e.getKey());
                if (bpAll != null && bpAll[0] > 0) {
                    avgCost = bpAll[1] / bpAll[0];
                }
            }

            double cogs = avgCost * qtySold;
            double gp = salesValue - cogs;
            double pct = salesValue != 0 ? (gp / salesValue) * 100.0 : 0;
            rows.add(new ItemProfitRow(item.getId(), item.getName(), qtySold,
                    PurchaseService.round2(salesValue), PurchaseService.round2(avgCost),
                    PurchaseService.round2(cogs), PurchaseService.round2(gp), PurchaseService.round2(pct)));
        }
        rows.sort((a, b2) -> Double.compare(b2.grossProfit(), a.grossProfit()));
        return rows;
    }

    private ItemRecord resolveCatalog(List<ItemRecord> items, String id, String name) {
        if (id != null && !id.isBlank()) {
            for (ItemRecord it : items) {
                if (it.getId().equals(id)) return it;
            }
        }
        if (name != null && !name.isBlank()) {
            for (ItemRecord it : items) {
                if (it.getName() != null && it.getName().equalsIgnoreCase(name.trim())) return it;
            }
        }
        return null;
    }

    /** ISO-date inclusive range check (blank bounds = open). */
    public static boolean inRange(String date, String fromDate, String toDate) {
        String d = date != null ? date : "";
        if (!fromDate.isBlank() && d.compareTo(fromDate) < 0) return false;
        if (!toDate.isBlank() && d.compareTo(toDate) > 0) return false;
        return true;
    }
}
