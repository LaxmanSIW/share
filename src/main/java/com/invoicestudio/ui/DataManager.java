package com.invoicestudio.ui;

import com.invoicestudio.db.*;
import com.invoicestudio.model.Bill;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;
import com.invoicestudio.model.PresetTemplates;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Central application-wide data hub (singleton).
 *
 * PERFORMANCE ARCHITECTURE:
 * - DAOs are created ONCE and shared by every view (previously every view
 *   constructor created its own DAO instances).
 * - Bills are cached in memory and invalidated explicitly on any write.
 *   This removes the single worst bottleneck: BuyersView used to run a full
 *   DB scan for EVERY table cell render.
 * - Settings are cached; views call {@link #getSettings()} instead of hitting
 *   SQLite on every layout pass.
 */
public final class DataManager {

    private static DataManager instance;

    private final DatabaseManager db;
    private final BillDao billDao;
    private final TemplateDao templateDao;
    private final SettingsDao settingsDao;
    private final BuyerDao buyerDao;
    private final ItemDao itemDao;
    private final VariableDao variableDao;

    /** Cache invalidated on any bill write. Guarded by the monitor of this list. */
    private List<Bill> billsCache;
    private Settings settingsCache;

    private final CopyOnWriteArrayList<Consumer<List<Bill>>> billsListeners = new CopyOnWriteArrayList<>();

    private DataManager(DatabaseManager db) {
        this.db = db;
        this.billDao = new BillDao(db);
        this.templateDao = new TemplateDao(db);
        this.settingsDao = new SettingsDao(db);
        this.buyerDao = new BuyerDao(db);
        this.itemDao = new ItemDao(db);
        this.variableDao = new VariableDao(db);
    }

    public static synchronized DataManager init(DatabaseManager db) {
        if (instance == null) {
            instance = new DataManager(db);
        }
        return instance;
    }

    public static DataManager get() {
        if (instance == null) {
            throw new IllegalStateException("DataManager not initialised. Call init() first.");
        }
        return instance;
    }

    // ---------- DAO access (shared instances) ----------

    public DatabaseManager getDb() { return db; }
    public BillDao bills() { return billDao; }
    public TemplateDao templates() { return templateDao; }
    public SettingsDao settingsDao() { return settingsDao; }
    public BuyerDao buyers() { return buyerDao; }
    public ItemDao items() { return itemDao; }
    public VariableDao variables() { return variableDao; }

    // ---------- Cached reads ----------

    /**
     * Returns ALL bills from the in-memory cache.
     * Loads once, then reuses until {@link #invalidateBills()} is called.
     * Safe to call from any thread.
     */
    public List<Bill> getAllBills() {
        synchronized (this) {
            if (billsCache == null) {
                billsCache = billDao.getAllBills();
            }
            return billsCache;
        }
    }

    /** Cached settings (re-read from DB only when invalidated). */
    public Settings getSettings() {
        synchronized (this) {
            if (settingsCache == null) {
                settingsCache = settingsDao.getSettings();
                if (settingsCache == null) {
                    settingsCache = new Settings();
                    settingsDao.saveSettings(settingsCache);
                }
            }
            return settingsCache;
        }
    }

    // ---------- Cache invalidation ----------

    /** Call after ANY bill create/update/delete/payment/status change. */
    public void invalidateBills() {
        synchronized (this) {
            billsCache = null;
        }
        notifyBillsChanged();
    }

    /** Call after settings were persisted. */
    public void invalidateSettings() {
        synchronized (this) {
            settingsCache = null;
        }
    }

    /**
     * Persist a bill, refresh the cache and notify listeners in one call.
     * Returns the saved bill for chaining.
     */
    public Bill saveBill(Bill bill) {
        billDao.saveBill(bill);
        invalidateBills();
        return bill;
    }

    public void deleteBill(String id) {
        billDao.deleteBill(id);
        invalidateBills();
    }

    public void saveSettings(Settings s) {
        settingsDao.saveSettings(s);
        invalidateSettings();
    }

    // ---------- Change notifications ----------

    /** Subscribe to bill-data changes (cache refresh events). Listener runs on calling thread. */
    public void addBillsListener(Consumer<List<Bill>> listener) {
        billsListeners.add(listener);
    }

    private void notifyBillsChanged() {
        List<Bill> snapshot = getAllBills();
        for (Consumer<List<Bill>> l : billsListeners) {
            try {
                l.accept(snapshot);
            } catch (Exception ignored) {}
        }
    }

    // ---------- First-run seeding (fast: only when DB is empty) ----------

    public void seedIfEmpty() {
        List<Template> existing = templateDao.getAllTemplates();
        if (existing.isEmpty()) {
            for (Template t : PresetTemplates.getAllPresets()) {
                templateDao.saveTemplate(t);
            }
        }
    }
}
