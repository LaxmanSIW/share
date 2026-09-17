package com.invoicestudio.service;

import com.invoicestudio.model.Expense;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure-Java expense aggregation for the account/category reports — no JavaFX,
 * so the math is unit-testable in isolation (skill rule 5.4).
 *
 * One O(n) pass over the voucher list builds every dimension (month, category,
 * account); the dialogs only format what this returns.
 */
public final class ExpenseAnalytics {

    private ExpenseAnalytics() {}

    /** One aggregate row of a dimension (month / category / account). */
    public record Bucket(String key, int vouchers, double total) {}

    /** Full report for a filter (account or category, optional — null = all). */
    public record Report(
            String dimension,      // "Account" | "Category" | "All"
            String filterName,     // account/category name or "" for all
            String from, String to,
            double total, int voucherCount,
            List<Bucket> byMonth,      // chronological
            List<Bucket> byCategory,   // total desc
            List<Bucket> byAccount,    // total desc
            List<Expense> vouchers) {  // date desc

        public double average() { return voucherCount > 0 ? total / voucherCount : 0; }
    }

    /** Build a report over {@code vouchers} filtered to [from, to] (ISO dates, inclusive). */
    public static Report build(List<Expense> vouchers, String dimension, String filterName,
                               String accountName, String category, String from, String to) {
        List<Expense> scoped = new ArrayList<>();
        Map<String, int[]> monthCounts = new TreeMap<>();   // yyyy-MM → [count]
        Map<String, Double> monthTotals = new TreeMap<>();
        Map<String, Integer> catCounts = new java.util.HashMap<>();
        Map<String, Double> catTotals = new java.util.HashMap<>();
        Map<String, Integer> accCounts = new java.util.HashMap<>();
        Map<String, Double> accTotals = new java.util.HashMap<>();

        double total = 0;
        int count = 0;

        for (Expense e : vouchers) {
            String d = e.getDate() == null ? "" : e.getDate();
            if (!from.isBlank() && d.compareTo(from) < 0) continue;
            if (!to.isBlank() && d.compareTo(to) > 0) continue;
            if (accountName != null && !accountName.isBlank()
                    && !accountName.equalsIgnoreCase(e.getPayee() == null ? "" : e.getPayee().trim())) continue;
            if (category != null && !category.isBlank()
                    && !category.equalsIgnoreCase(e.getCategory())) continue;

            scoped.add(e);
            total += e.getAmount();
            count++;

            String month = d.length() >= 7 ? d.substring(0, 7) : "unknown";
            monthCounts.computeIfAbsent(month, k -> new int[1])[0]++;
            monthTotals.merge(month, e.getAmount(), Double::sum);
            catCounts.merge(e.getCategory(), 1, Integer::sum);
            catTotals.merge(e.getCategory(), e.getAmount(), Double::sum);
            String acc = e.getPayee() == null || e.getPayee().isBlank() ? "(unassigned)" : e.getPayee().trim();
            accCounts.merge(acc, 1, Integer::sum);
            accTotals.merge(acc, e.getAmount(), Double::sum);
        }

        List<Bucket> months = new ArrayList<>();
        for (String m : monthTotals.keySet()) {
            months.add(new Bucket(m, monthCounts.get(m)[0], monthTotals.get(m)));
        }

        List<Bucket> cats = toBuckets(catCounts, catTotals);
        List<Bucket> accs = toBuckets(accCounts, accTotals);

        scoped.sort((a, b) -> b.getDate().compareTo(a.getDate()));

        return new Report(dimension, filterName == null ? "" : filterName, from, to,
                total, count, months, cats, accs, scoped);
    }

    /** Report for one account (case-insensitive). */
    public static Report forAccount(List<Expense> vouchers, String accountName, String from, String to) {
        return build(vouchers, "Account", accountName, accountName, null, from, to);
    }

    /** Report for one category head (case-insensitive). */
    public static Report forCategory(List<Expense> vouchers, String category, String from, String to) {
        return build(vouchers, "Category", category, null, category, from, to);
    }

    /** Whole-register overview. */
    public static Report overall(List<Expense> vouchers, String from, String to) {
        return build(vouchers, "All", "", null, null, from, to);
    }

    private static List<Bucket> toBuckets(Map<String, Integer> counts, Map<String, Double> totals) {
        List<Bucket> out = new ArrayList<>();
        for (Map.Entry<String, Double> en : totals.entrySet()) {
            out.add(new Bucket(en.getKey(), counts.getOrDefault(en.getKey(), 0), en.getValue()));
        }
        out.sort((a, b) -> Double.compare(b.total(), a.total()));
        return out;
    }

    /** CSV export of the report's voucher table (uses CsvService quoting). */
    public static String csv(Report r, String currency) {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvService.csvRow(List.of("Date", "Category", "Paid To", "Description", "Mode", "Reference",
                "Amount (" + currency + ")")));
        for (Expense e : r.vouchers()) {
            sb.append(CsvService.csvRow(List.of(
                    e.getDate(), e.getCategory(), e.getPayee(), e.getDescription(),
                    e.getPaymentMode(), e.getReference(), String.format("%.2f", e.getAmount()))));
        }
        sb.append(CsvService.csvRow(List.of("", "", "", "", "", "TOTAL", String.format("%.2f", r.total()))));
        return sb.toString();
    }

    /** "2026-09" → "Sep 26" for chart axis labels. */
    public static String monthLabel(String yyyyMm) {
        if (yyyyMm == null || yyyyMm.length() < 7) return yyyyMm;
        try {
            int m = Integer.parseInt(yyyyMm.substring(5, 7));
            String[] names = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            return names[m - 1] + " " + yyyyMm.substring(2, 4);
        } catch (Exception e) {
            return yyyyMm;
        }
    }
}
