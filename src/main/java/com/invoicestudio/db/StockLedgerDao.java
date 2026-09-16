package com.invoicestudio.db;

import com.invoicestudio.model.ItemRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable Stock Ledger (Tally-style "never edit stock directly").
 *
 * Every inward/outward movement is an append-only row; item current stock is
 * always the ledger balance, recomputed as
 * {@code opening_stock + Σ qty_in − Σ qty_out}.
 *
 * Voucher types: PURCHASE (in), SALE (out), DEBIT_NOTE (out — return to
 * supplier), CREDIT_NOTE (in — return from buyer), ADJUSTMENT (±).
 */
public class StockLedgerDao {
    /** Movement direction types — qty_in for these, qty_out otherwise. */
    public static final String V_PURCHASE = "PURCHASE";
    public static final String V_SALE = "SALE";
    public static final String V_DEBIT_NOTE = "DEBIT_NOTE";
    public static final String V_CREDIT_NOTE = "CREDIT_NOTE";
    public static final String V_ADJUSTMENT = "ADJUSTMENT";

    private final DatabaseManager db;

    public StockLedgerDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }

    /**
     * Appends a ledger row. For ADJUSTMENT, pass a positive delta as qtyIn or
     * a negative delta as qtyOut.
     */
    public void append(String itemId, String date, String voucherType,
                       String voucherId, String voucherNo,
                       double qtyIn, double qtyOut, double unitPrice) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || itemId == null || itemId.isBlank()) return;
        double in = Math.max(0, qtyIn);
        double out = Math.max(0, qtyOut);
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO stock_ledger (item_id, transaction_date, voucher_type, voucher_id, voucher_no, qty_in, qty_out, unit_price, user_id, created_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, itemId);
            ps.setString(2, date != null ? date : Instant.now().toString());
            ps.setString(3, voucherType);
            ps.setString(4, voucherId != null ? voucherId : "");
            ps.setString(5, voucherNo != null ? voucherNo : "");
            ps.setDouble(6, in);
            ps.setDouble(7, out);
            ps.setDouble(8, unitPrice);
            ps.setString(9, uid);
            ps.setString(10, Instant.now().toString());
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    /** Removes every ledger row for a voucher (edit/delete of the voucher). */
    public void deleteByVoucher(String voucherId) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || voucherId == null || voucherId.isBlank()) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM stock_ledger WHERE voucher_id = ? AND user_id = ?")) {
            ps.setString(1, voucherId);
            ps.setString(2, uid);
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    /** Recomputes and persists current_stock for one item from the ledger. */
    public void recomputeItem(String itemId) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || itemId == null || itemId.isBlank()) return;
        try (Connection conn = db.getConnection()) {
            double opening = 0;
            try (PreparedStatement ps = conn.prepareStatement("SELECT opening_stock FROM items WHERE id = ? AND user_id = ?")) {
                ps.setString(1, itemId);
                ps.setString(2, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) opening = rs.getDouble("opening_stock");
                }
            } catch (SQLException ignored) {
                // column may not exist on very old DBs; treat as 0
            }

            double in = 0;
            double out = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(qty_in),0) AS total_in, COALESCE(SUM(qty_out),0) AS total_out " +
                    "FROM stock_ledger WHERE item_id = ? AND user_id = ?")) {
                ps.setString(1, itemId);
                ps.setString(2, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        in = rs.getDouble("total_in");
                        out = rs.getDouble("total_out");
                    }
                }
            }

            double balance = opening + in - out;
            try (PreparedStatement ps = conn.prepareStatement("UPDATE items SET current_stock = ? WHERE id = ? AND user_id = ?")) {
                ps.setDouble(1, balance);
                ps.setString(2, itemId);
                ps.setString(3, uid);
                ps.executeUpdate();
            } catch (SQLException ignored) {
                // items.current_stock missing on legacy DBs
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    /** Per-item ledger balances (item_id → closing qty) for the current user. */
    public Map<String, Double> allBalances() {
        Map<String, Double> map = new HashMap<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) return map;
        String sql =
            "SELECT i.id AS item_id, " +
            "  COALESCE(i.opening_stock, 0) + COALESCE(l.total_in, 0) - COALESCE(l.total_out, 0) AS balance " +
            "FROM items i " +
            "LEFT JOIN (SELECT item_id, SUM(qty_in) AS total_in, SUM(qty_out) AS total_out " +
            "           FROM stock_ledger WHERE user_id = ? GROUP BY item_id) l " +
            "  ON l.item_id = i.id " +
            "WHERE i.user_id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uid);
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("item_id"), rs.getDouble("balance"));
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return map;
    }

    /** Full movement history for one item (newest last), for the stock summary report. */
    public List<Map<String, Object>> movementsForItem(String itemId) {
        List<Map<String, Object>> rows = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || itemId == null || itemId.isBlank()) return rows;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT transaction_date, voucher_type, voucher_no, qty_in, qty_out, unit_price " +
                 "FROM stock_ledger WHERE item_id = ? AND user_id = ? ORDER BY transaction_date ASC, created_at ASC")) {
            ps.setString(1, itemId);
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("date", rs.getString("transaction_date"));
                    row.put("type", rs.getString("voucher_type"));
                    row.put("voucherNo", rs.getString("voucher_no"));
                    row.put("qtyIn", rs.getDouble("qty_in"));
                    row.put("qtyOut", rs.getDouble("qty_out"));
                    row.put("unitPrice", rs.getDouble("unit_price"));
                    rows.add(row);
                }
            }
        } catch (Exception e) {
        }
        return rows;
    }
}
