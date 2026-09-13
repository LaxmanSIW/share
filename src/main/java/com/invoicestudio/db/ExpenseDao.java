package com.invoicestudio.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.Expense;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence for expense vouchers, mirroring SupplierDao conventions:
 * user-scoped, parameterized SQL, JSON payload for full fidelity.
 */
public class ExpenseDao {
    private final DatabaseManager db;
    private final ObjectMapper mapper = new ObjectMapper();

    public ExpenseDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }

    public List<Expense> getAllExpenses() {
        List<Expense> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) return list;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT json_data FROM expenses WHERE user_id = ? ORDER BY date DESC, created_at DESC")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String json = rs.getString("json_data");
                    if (json != null) list.add(mapper.readValue(json, Expense.class));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public Expense getExpenseById(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM expenses WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapper.readValue(rs.getString("json_data"), Expense.class);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void saveExpense(Expense e) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || e == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO expenses (id, user_id, date, category, amount, payment_mode, json_data, created_at, updated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, date = excluded.date, " +
                 "category = excluded.category, amount = excluded.amount, payment_mode = excluded.payment_mode, " +
                 "json_data = excluded.json_data, updated_at = excluded.updated_at WHERE expenses.user_id = excluded.user_id")) {
            String now = Instant.now().toString();
            if (e.getCreatedAt() == null) e.setCreatedAt(now);
            e.setUpdatedAt(now);

            ps.setString(1, e.getId());
            ps.setString(2, uid);
            ps.setString(3, e.getDate());
            ps.setString(4, e.getCategory());
            ps.setDouble(5, e.getAmount());
            ps.setString(6, e.getPaymentMode());
            ps.setString(7, mapper.writeValueAsString(e));
            ps.setString(8, e.getCreatedAt());
            ps.setString(9, e.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void deleteExpense(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM expenses WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            ps.executeUpdate();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // --- Aliases for uniform API ---
    public List<Expense> findAll() { return getAllExpenses(); }
    public Expense findById(String id) { return getExpenseById(id); }
    public void save(Expense e) { saveExpense(e); }
    public void delete(String id) { deleteExpense(id); }
}
