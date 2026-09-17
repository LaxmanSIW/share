package com.invoicestudio.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.ExpenseAccount;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence for the expense-account registry (payee directory), mirroring
 * SupplierDao conventions: user-scoped, parameterized SQL, JSON payload.
 *
 * The {@code expense_accounts} table is a light index — full fidelity lives in
 * json_data, exactly like every other directory table in this app.
 */
public class ExpenseAccountDao {
    private final DatabaseManager db;
    private final ObjectMapper mapper = new ObjectMapper();

    public ExpenseAccountDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }

    /** All accounts of the current user — active first, then archived, each A→Z. */
    public List<ExpenseAccount> getAllAccounts() {
        List<ExpenseAccount> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) return list;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT json_data FROM expense_accounts WHERE user_id = ? " +
                 "ORDER BY archived ASC, name COLLATE NOCASE ASC")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String json = rs.getString("json_data");
                    if (json != null) list.add(mapper.readValue(json, ExpenseAccount.class));
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return list;
    }

    /** Active (non-archived) accounts — what the pickers show. */
    public List<ExpenseAccount> getActiveAccounts() {
        List<ExpenseAccount> out = new ArrayList<>();
        for (ExpenseAccount a : getAllAccounts()) {
            if (!a.isArchived()) out.add(a);
        }
        return out;
    }

    public ExpenseAccount getAccountById(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT json_data FROM expense_accounts WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapper.readValue(rs.getString("json_data"), ExpenseAccount.class);
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return null;
    }

    /** Case-insensitive name lookup (duplicate guard when creating/renaming). */
    public ExpenseAccount findByName(String name) {
        if (name == null || name.isBlank()) return null;
        for (ExpenseAccount a : getAllAccounts()) {
            if (a.getName().equalsIgnoreCase(name.trim())) return a;
        }
        return null;
    }

    /** Insert-or-update (upsert) scoped to the current user. */
    public void saveAccount(ExpenseAccount account) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || account == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO expense_accounts (id, user_id, name, archived, json_data, created_at, updated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, name = excluded.name, " +
                 "archived = excluded.archived, json_data = excluded.json_data, updated_at = excluded.updated_at " +
                 "WHERE expense_accounts.user_id = excluded.user_id")) {
            String now = Instant.now().toString();
            if (account.getCreatedAt() == null) account.setCreatedAt(now);
            account.setUpdatedAt(now);

            ps.setString(1, account.getId());
            ps.setString(2, uid);
            ps.setString(3, account.getName());
            ps.setInt(4, account.isArchived() ? 1 : 0);
            ps.setString(5, mapper.writeValueAsString(account));
            ps.setString(6, account.getCreatedAt());
            ps.setString(7, account.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    public void deleteAccount(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "DELETE FROM expense_accounts WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    // --- Aliases for uniform API ---
    public List<ExpenseAccount> findAll() { return getAllAccounts(); }
    public ExpenseAccount findById(String id) { return getAccountById(id); }
    public void save(ExpenseAccount account) { saveAccount(account); }
    public void delete(String id) { deleteAccount(id); }
}
