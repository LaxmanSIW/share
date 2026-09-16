package com.invoicestudio.service;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * One home for money/number formatting (skill rule 4.4).
 *
 * {@link String#format} re-parses the pattern and allocates a Formatter on
 * every call — measurable inside TableView cell factories that run per visible
 * row per scroll. These cached, thread-local {@link DecimalFormat}s do the same
 * work with zero per-call parsing.
 *
 * All methods preserve the exact output of the String.format calls they
 * replace (HALF_UP rounding, locale-default symbols, no hidden grouping
 * change), so this is a pure mechanical optimization.
 */
public final class AppFormatters {

    /** "1234.50" — equivalent of String.format("%.2f", v). */
    private static final ThreadLocal<DecimalFormat> PLAIN_2 = ThreadLocal.withInitial(() -> {
        DecimalFormat df = (DecimalFormat) NumberFormat.getNumberInstance();
        df.applyPattern("0.00");
        df.setRoundingMode(RoundingMode.HALF_UP);
        return df;
    });

    /** "1,234" — equivalent of String.format("%,d", v) or "%,.0f". */
    private static final ThreadLocal<DecimalFormat> GROUPED_INT = ThreadLocal.withInitial(() -> {
        DecimalFormat df = (DecimalFormat) NumberFormat.getNumberInstance();
        df.applyPattern("#,##0");
        df.setRoundingMode(RoundingMode.HALF_UP);
        return df;
    });

    /** "1,234.50" — Indian-grouped, for dashboards that already grouped. */
    private static final ThreadLocal<DecimalFormat> GROUPED_2 = ThreadLocal.withInitial(() -> {
        DecimalFormat df = (DecimalFormat) NumberFormat.getNumberInstance();
        df.applyPattern("#,##,##0.00");
        df.setRoundingMode(RoundingMode.HALF_UP);
        return df;
    });

    private AppFormatters() {}

    /** Equivalent of {@code String.format("%.2f", v)} with zero format parsing. */
    public static String plain2(double v) {
        return PLAIN_2.get().format(v);
    }

    /** Equivalent of {@code String.format("%s%.2f", currency, v)}. */
    public static String money(double v, String currency) {
        String cur = currency != null ? currency : "";
        return cur + PLAIN_2.get().format(v);
    }

    /** Equivalent of {@code String.format("%,d", v)}. */
    public static String grouped(long v) {
        return GROUPED_INT.get().format(v);
    }

    /** Equivalent of {@code String.format("%,.0f", v)}. */
    public static String grouped(double v) {
        return GROUPED_INT.get().format(v);
    }

    /** Indian-grouped 2-decimal, equivalent of {@code new DecimalFormat("#,##,##0.00")}. */
    public static String inr(double v) {
        return GROUPED_2.get().format(v);
    }

    /**
     * Shared cached Indian-grouped format instance (per-thread) for views that
     * keep a {@code DecimalFormat} field — the field then references the cache
     * instead of constructing a new formatter per view instance.
     */
    public static DecimalFormat inrFormat() {
        return GROUPED_2.get();
    }
}
