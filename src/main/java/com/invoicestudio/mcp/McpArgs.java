package com.invoicestudio.mcp;

import com.invoicestudio.model.PaymentMethod;

import java.util.Locale;
import java.util.Map;

/**
 * Argument-parsing helpers for MCP tool handlers (skill rule 4.3: parse at
 * the edge, trust inside). Extracted verbatim from {@link McpToolRegistry}.
 */
final class McpArgs {

    private McpArgs() {}

    static String str(Map<String, Object> m, String key) {
        if (m == null) return "";
        Object v = m.get(key);
        return v != null ? String.valueOf(v) : "";
    }

    static String strOr(Map<String, Object> m, String key, String def) {
        String v = str(m, key);
        return v.isBlank() ? def : v;
    }

    static double dbl(Map<String, Object> m, String key, double def) {
        if (m == null) return def;
        Object v = m.get(key);
        if (v instanceof Number n) return n.doubleValue();
        try {
            return v != null ? Double.parseDouble(String.valueOf(v)) : def;
        } catch (NumberFormatException e) {
            return def;
        }
    }

    static int intVal(Map<String, Object> m, String key, int def) {
        return (int) dbl(m, key, def);
    }

    static boolean boolVal(Map<String, Object> m, String key, boolean def) {
        if (m == null) return def;
        Object v = m.get(key);
        if (v instanceof Boolean b) return b;
        return v != null && Boolean.parseBoolean(String.valueOf(v));
    }

    static boolean matches(String query, String... fields) {
        if (query == null || query.isBlank()) return true;
        String q = query.toLowerCase(Locale.ROOT);
        for (String f : fields) {
            if (f != null && f.toLowerCase(Locale.ROOT).contains(q)) return true;
        }
        return false;
    }

    static PaymentMethod parseMethod(String mode) {
        if (mode == null) return PaymentMethod.CASH;
        return switch (mode.trim().toLowerCase(Locale.ROOT)) {
            case "upi" -> PaymentMethod.UPI;
            case "bank", "bank transfer", "neft", "rtgs", "imps" -> PaymentMethod.BANK_TRANSFER;
            case "cheque", "check" -> PaymentMethod.CHEQUE;
            case "card" -> PaymentMethod.CARD;
            default -> PaymentMethod.CASH;
        };
    }

    static Map<String, Object> mapOf(Object... kv) {
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
