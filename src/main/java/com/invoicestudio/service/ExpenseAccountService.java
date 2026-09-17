package com.invoicestudio.service;

import com.invoicestudio.model.Expense;
import com.invoicestudio.model.ExpenseAccount;
import com.invoicestudio.ui.DataManager;

import java.util.List;
import java.util.UUID;

/**
 * Expense-account (payee registry) operations: backfill from history,
 * create-on-the-fly and rename-with-propagation.
 *
 * All DB work is serialized on the shared IO executor (skill rule 1.4 — no
 * inline pools); UI callbacks hop back via {@link AppExecutors#runOnFx}.
 * Aggregates run over the DataManager caches — zero extra DB reads.
 */
public final class ExpenseAccountService {

    private ExpenseAccountService() {}

    /**
     * Seed the registry from vouchers that already carry a payee name —
     * runs once per user (guarded by a meta flag, same convention as
     * RecurringEngine). Existing installs get a useful registry with zero
     * user action; fresh installs no-op instantly.
     */
    public static void backfillFromHistoryAsync(Runnable onDone) {
        AppExecutors.io().execute(() -> {
            try {
                DataManager dm = DataManager.get();
                if (!"1".equals(getMeta("expense_accounts_backfilled"))) {
                    backfillNow(dm);
                    setMeta("expense_accounts_backfilled", "1");
                }
            } catch (Exception e) {
                AppLog.error(e);
            }
            if (onDone != null) AppExecutors.runOnFx(onDone);
        });
    }

    /** Synchronous backfill over the current user's vouchers (also used by tests). */
    public static int backfillNow(DataManager dm) {
        List<Expense> expenses = dm.getAllExpenses();
        java.util.Map<String, ExpenseAccount> known = new java.util.HashMap<>();
        for (ExpenseAccount a : dm.getAllExpenseAccounts()) {
            known.put(a.getName().toLowerCase(), a);
        }
        int created = 0;
        for (Expense e : expenses) {
            String payee = e.getPayee();
            if (payee == null || payee.isBlank()) continue;
            String key = payee.trim().toLowerCase();
            if (known.containsKey(key)) continue;
            ExpenseAccount acc = new ExpenseAccount(newId(), payee.trim());
            dm.expenseAccounts().saveAccount(acc);
            known.put(key, acc);
            created++;
        }
        if (created > 0) dm.invalidateExpenseAccounts();
        return created;
    }

    /**
     * Resolve a typed payee name to an account, creating one when unknown.
     * Returns null for blank names (payee is optional).
     */
    public static ExpenseAccount findOrCreate(DataManager dm, String name) {
        if (name == null || name.isBlank()) return null;
        String trimmed = name.trim();
        ExpenseAccount existing = dm.expenseAccounts().findByName(trimmed);
        if (existing != null) return existing;
        ExpenseAccount acc = new ExpenseAccount(newId(), trimmed);
        dm.expenseAccounts().saveAccount(acc);
        dm.invalidateExpenseAccounts();
        return acc;
    }

    /**
     * Rename an account and propagate the new name to every voucher that
     * carries the old one. Synchronous — call from a worker thread (IO
     * executor or MCP transport); the UI wrapper below handles threading.
     *
     * @return number of vouchers updated
     */
    public static int renameWithPropagation(DataManager dm, ExpenseAccount account, String newName) {
        String oldName = account.getName();
        String trimmed = newName == null ? "" : newName.trim();
        account.setName(trimmed);
        dm.expenseAccounts().saveAccount(account);
        dm.invalidateExpenseAccounts();

        int updated = 0;
        if (!oldName.equalsIgnoreCase(trimmed)) {
            List<Expense> expenses = dm.getAllExpenses();
            for (Expense e : expenses) {
                if (oldName.equalsIgnoreCase(e.getPayee() == null ? "" : e.getPayee().trim())) {
                    e.setPayee(trimmed);
                    dm.saveExpense(e);
                    updated++;
                }
            }
        }
        return updated;
    }

    /**
     * Rename an account and propagate the new name to every voucher that
     * carries the old one (single background transaction-style pass).
     *
     * @return number of vouchers updated (via callback, FX thread)
     */
    public static void renameWithPropagationAsync(DataManager dm, ExpenseAccount account,
                                                  String newName, java.util.function.IntConsumer onDone) {
        AppExecutors.io().execute(() -> {
            int updated = 0;
            try {
                updated = renameWithPropagation(dm, account, newName);
            } catch (Exception ex) {
                AppLog.error(ex);
            }
            int count = updated;
            if (onDone != null) AppExecutors.runOnFx(() -> onDone.accept(count));
        });
    }

    /** Stable id prefix consistent with the app's exp_/sup_ style. */
    public static String newId() {
        return "expacc_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    // --- user-scoped meta flag storage (mirrors RecurringEngine) ---

    private static String getMeta(String key) {
        String uid = com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
        if (uid.isEmpty()) return null;
        try (java.sql.Connection conn = DataManager.get().getDb().getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "SELECT val FROM meta WHERE key = ? AND user_id = ?")) {
            ps.setString(1, key);
            ps.setString(2, uid);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("val");
            }
        } catch (Exception e) {
            AppLog.error(e);
        }
        return null;
    }

    private static void setMeta(String key, String val) {
        String uid = com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
        if (uid.isEmpty()) return;
        try (java.sql.Connection conn = DataManager.get().getDb().getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO meta (key, user_id, val) VALUES (?, ?, ?) ON CONFLICT(key) DO UPDATE SET user_id = excluded.user_id, val = excluded.val")) {
            ps.setString(1, key);
            ps.setString(2, uid);
            ps.setString(3, val);
            ps.executeUpdate();
        } catch (Exception e) {
            AppLog.error(e);
        }
    }
}
