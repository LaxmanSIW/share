package com.invoicestudio.service;

import com.invoicestudio.model.VariableDef;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups template variables by their category so dropdowns and picker dialogs
 * can show section headers (like BarTender's field browser) instead of one
 * long flat list.
 *
 * Pure logic — no JavaFX — so it is fully unit-testable.
 *
 * Row model: a flat list of {@link Row}s where each group is emitted as a
 * header row followed by its member rows. UI layers render header rows as
 * non-selectable section titles.
 */
public final class VariableGrouper {

    /** Canonical group order — matches how users think when composing invoices. */
    public static final List<String> GROUP_ORDER = List.of(
            "INVOICE", "PAGING", "BUYER", "BUYER CUSTOM", "BUSINESS",
            "BANK", "TOTALS", "LOGISTICS");

    /** Fallback group for user-defined variables (type = text/number/date etc.). */
    public static final String CUSTOM_GROUP = "MY VARIABLES";

    /** One display row: either a group header (var == null) or a variable. */
    public record Row(String header, VariableDef var) {
        public static Row header(String text) { return new Row(text, null); }
        public static Row item(VariableDef v) { return new Row(null, v); }
        public boolean isHeader() { return header != null; }
    }

    private VariableGrouper() {}

    /** Human-friendly section title for a raw category token. */
    public static String prettyGroup(String type) {
        if (type == null || type.isBlank()) return "Other";
        return switch (type) {
            case "INVOICE"      -> "Invoice Details";
            case "PAGING"       -> "Page Numbers";
            case "BUYER"        -> "Buyer / Customer";
            case "BUYER CUSTOM" -> "Buyer Custom Fields";
            case "BUSINESS"     -> "My Business";
            case "BANK"         -> "Bank & Payment";
            case "TOTALS"       -> "Totals & Tax";
            case "LOGISTICS"    -> "Transport & Logistics";
            case "MY VARIABLES" -> "My Custom Variables";
            default -> capitalize(type.trim());
        };
    }

    /** Group a variable list into header + item rows, canonical order first. */
    public static List<Row> group(List<VariableDef> vars) {
        Map<String, List<VariableDef>> buckets = new LinkedHashMap<>();
        if (vars != null) {
            for (VariableDef v : vars) {
                if (v == null || v.getKey() == null || v.getKey().isBlank()) continue;
                buckets.computeIfAbsent(bucketOf(v), k -> new ArrayList<>()).add(v);
            }
        }

        List<Row> rows = new ArrayList<>();
        for (String g : GROUP_ORDER) {
            append(rows, buckets.remove(g));
        }
        // Remaining buckets (custom user variables, unknown types) in encounter order.
        for (Map.Entry<String, List<VariableDef>> en : buckets.entrySet()) {
            append(rows, en.getValue());
        }
        return rows;
    }

    /** Re-group with a live search filter — empty groups disappear entirely. */
    public static List<Row> groupFiltered(List<VariableDef> vars, String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isEmpty()) return group(vars);
        List<VariableDef> matched = new ArrayList<>();
        if (vars != null) {
            for (VariableDef v : vars) {
                if (v == null || v.getKey() == null) continue;
                if (contains(v.getLabel(), q) || contains(v.getKey(), q) || contains(v.getType(), q)) {
                    matched.add(v);
                }
            }
        }
        return group(matched);
    }

    /** First selectable (non-header) row, or null when the list has none. */
    public static VariableDef firstItem(List<Row> rows) {
        if (rows == null) return null;
        for (Row r : rows) {
            if (!r.isHeader()) return r.var();
        }
        return null;
    }

    /** Which bucket a variable belongs to. */
    public static String bucketOf(VariableDef v) {
        String t = v.getType();
        if (t != null && GROUP_ORDER.contains(t)) return t;
        return CUSTOM_GROUP;
    }

    private static void append(List<Row> rows, List<VariableDef> members) {
        if (members == null || members.isEmpty()) return;
        String bucket = bucketOf(members.get(0));
        rows.add(Row.header(prettyGroup(bucket) + "  (" + members.size() + ")"));
        for (VariableDef v : members) rows.add(Row.item(v));
    }

    private static boolean contains(String s, String qLower) {
        return s != null && s.toLowerCase().contains(qLower);
    }

    private static String capitalize(String s) {
        if (s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
