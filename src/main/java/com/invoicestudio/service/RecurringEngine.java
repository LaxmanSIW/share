package com.invoicestudio.service;

import com.invoicestudio.db.BillDao;
import com.invoicestudio.db.DatabaseManager;
import com.invoicestudio.db.SettingsDao;
import com.invoicestudio.model.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class RecurringEngine {
    private final BillDao billDao;
    private final SettingsDao settingsDao;
    private final DatabaseManager db;

    public RecurringEngine(DatabaseManager db) {
        this.db = db;
        this.billDao = new BillDao(db);
        this.settingsDao = new SettingsDao(db);
    }

    public static class SweepResult {
        public boolean ran;
        public String reason;
        public List<Bill> created = new ArrayList<>();
        public List<String> sourceNos = new ArrayList<>();
        public List<String> skipped = new ArrayList<>();
        public List<String> ended = new ArrayList<>();
    }

    public SweepResult runSweep(boolean force) {
        SweepResult res = new SweepResult();
        if (!AuthSessionManager.isLoggedIn() || AuthSessionManager.getCurrentUserId().isEmpty()) {
            res.ran = false;
            res.reason = "User not authenticated";
            return res;
        }
        Settings settings = settingsDao.getSettings();
        if (!settings.isAutoRecurring() && !force) {
            res.ran = false;
            res.reason = "Auto-recurring is disabled";
            return res;
        }

        String today = BillingService.todayISO();
        String lastSweep = getMeta("last_sweep_date");
        if (!force && today.equals(lastSweep)) {
            res.ran = false;
            res.reason = "Already ran today";
            return res;
        }

        List<Bill> all = billDao.getAllBills();
        for (Bill b : all) {
            if (b.getRepeat() == null || b.getRepeat() == RepeatCadence.NONE) continue;
            if (b.getDocType() != DocType.INVOICE) continue;

            String due = BillingService.nextRepeatDate(b);
            // Check end date
            if (b.getRepeatEndDate() != null && !b.getRepeatEndDate().isBlank() && due.compareTo(b.getRepeatEndDate()) > 0) {
                b.setRepeat(RepeatCadence.NONE);
                b.setRepeatEndDate(null);
                b.setRepeatSkipNext(false);
                b.setUpdatedAt(Instant.now().toString());
                billDao.saveBill(b);
                res.ended.add(b.getBillNo());
                continue;
            }

            if (due.compareTo(today) > 0) {
                // not due yet
                continue;
            }

            // One-shot skip check
            if (b.isRepeatSkipNext()) {
                b.setDate(due);
                b.setRepeatSkipNext(false);
                b.setUpdatedAt(Instant.now().toString());
                billDao.saveBill(b);
                res.skipped.add(b.getBillNo());
                continue;
            }

            // Roll bill
            Bill next = BillingService.repeatBill(b, settings);
            next.setBillNo(BillingService.nextBillNo(settings));
            settings.setBillNoNext(settings.getBillNoNext() + 1);
            settingsDao.saveSettings(settings);

            billDao.saveBill(next);
            res.created.add(next);
            res.sourceNos.add(b.getBillNo());

            // Clear repeat on previous bill so only the latest is active in chain
            b.setRepeat(RepeatCadence.NONE);
            b.setUpdatedAt(Instant.now().toString());
            billDao.saveBill(b);
        }

        setMeta("last_sweep_date", today);
        res.ran = true;
        return res;
    }

    private String getMeta(String key) {
        String uid = AuthSessionManager.getCurrentUserId();
        if (uid.isEmpty()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT val FROM meta WHERE key = ? AND user_id = ?")) {
            ps.setString(1, key);
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getString("val");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void setMeta(String key, String val) {
        String uid = AuthSessionManager.getCurrentUserId();
        if (uid.isEmpty()) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO meta (key, user_id, val) VALUES (?, ?, ?) ON CONFLICT(key) DO UPDATE SET user_id = excluded.user_id, val = excluded.val")) {
            ps.setString(1, key);
            ps.setString(2, uid);
            ps.setString(3, val);
            ps.executeUpdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
