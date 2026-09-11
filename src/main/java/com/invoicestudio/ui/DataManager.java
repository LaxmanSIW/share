package com.invoicestudio.ui;

import com.invoicestudio.db.*;
import com.invoicestudio.model.Bill;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;
import com.invoicestudio.model.PresetTemplates;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.time.LocalDate;
import com.invoicestudio.model.BillItem;
import com.invoicestudio.model.Buyer;
import com.invoicestudio.model.Transaction;

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
    private final TransportDao transportDao;
    private final CategoryDao categoryDao;
    private final TransactionDao transactionDao;

    /** Cache invalidated on any bill write. Guarded by the monitor of this list. */
    private List<Bill> billsCache;
    private List<com.invoicestudio.model.Transport> transportsCache;
    private List<com.invoicestudio.model.ItemCategory> categoriesCache;
    private List<com.invoicestudio.model.Transaction> transactionsCache;
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
        this.transportDao = new TransportDao(db);
        this.categoryDao = new CategoryDao(db);
        this.transactionDao = new TransactionDao(db);
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
    public TransportDao transports() { return transportDao; }
    public CategoryDao categories() { return categoryDao; }
    public TransactionDao transactions() { return transactionDao; }

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
     * Automatically synchronizes with the financial ledger (transactions table).
     * Returns the saved bill for chaining.
     */
    public Bill saveBill(Bill bill) {
        billDao.saveBill(bill);
        invalidateBills();
        syncBillTransaction(bill);
        return bill;
    }

    public void deleteBill(String id) {
        billDao.deleteBill(id);
        invalidateBills();
        try {
            List<Transaction> txs = getAllTransactions();
            for (Transaction t : txs) {
                if (id != null && (id.equals(t.getBillId()) || (t.getId() != null && t.getId().contains(id)))) {
                    deleteTransaction(t.getId(), "Deleted with invoice");
                }
            }
        } catch (Exception ignored) {}
    }

    private void syncBillTransaction(Bill bill) {
        if (bill == null) return;
        try {
            // Find if a transaction for this bill exists
            Transaction existing = getAllTransactions().stream()
                    .filter(t -> (bill.getId() != null && bill.getId().equals(t.getBillId())) ||
                                 (bill.getBillNo() != null && !bill.getBillNo().isBlank() && ("INV-" + bill.getBillNo()).equalsIgnoreCase(t.getCheckNumber())))
                    .findFirst().orElse(null);

            // Find buyer id if available
            String buyerId = "";
            String buyerName = bill.getBuyerName();
            if (buyerName != null && !buyerName.isBlank()) {
                Buyer b = getAllBuyers().stream()
                        .filter(byr -> buyerName.equalsIgnoreCase(byr.getName()) || buyerName.equalsIgnoreCase(byr.getDisplayName()))
                        .findFirst().orElse(null);
                if (b != null) buyerId = b.getId();
            }

            int qty = 0;
            if (bill.getItems() != null) {
                qty = (int) Math.round(bill.getItems().stream().mapToDouble(BillItem::getQuantity).sum());
            }

            double amount = bill.getTotals() != null ? bill.getTotals().getGrandTotal() : 0.0;
            int parcels = bill.getParcel() > 0 ? bill.getParcel() : 1;

            if (existing == null) {
                Transaction t = new Transaction();
                t.setId("tx_" + bill.getId());
                t.setBillId(bill.getId());
                t.setBillNo(bill.getBillNo());
                t.setCheckNumber("INV-" + bill.getBillNo());
                t.setBuyerId(buyerId);
                t.setBuyerName(buyerName != null && !buyerName.isBlank() ? buyerName : "Walk-in Customer");
                t.setBookType("CC");
                t.setTransactionType("sale");
                t.setTransactionDate(bill.getDate() != null && !bill.getDate().isBlank() ? bill.getDate() : LocalDate.now().toString());
                t.setDueDate(bill.getDate() != null && !bill.getDate().isBlank() ? bill.getDate() : LocalDate.now().toString());
                t.setAmount(amount);
                t.setTotalQuantity(qty);
                t.setParcel(parcels);
                t.setIncludeInReporting(true);
                t.setDeleted(false);
                saveTransaction(t);
                bill.setTransactionId(t.getId());
            } else {
                existing.setBillId(bill.getId());
                existing.setBillNo(bill.getBillNo());
                existing.setCheckNumber("INV-" + bill.getBillNo());
                if (!buyerId.isBlank()) existing.setBuyerId(buyerId);
                if (buyerName != null && !buyerName.isBlank()) existing.setBuyerName(buyerName);
                if (bill.getDate() != null && !bill.getDate().isBlank()) existing.setTransactionDate(bill.getDate());
                existing.setAmount(amount);
                existing.setTotalQuantity(qty);
                existing.setParcel(parcels);
                existing.setDeleted(false);
                saveTransaction(existing);
                bill.setTransactionId(existing.getId());
            }

            // Sync bill payments into transactions so ledger balance reflects payments accurately
            if (bill.getPayments() != null && !bill.getPayments().isEmpty()) {
                for (int i = 0; i < bill.getPayments().size(); i++) {
                    com.invoicestudio.model.BillPayment bp = bill.getPayments().get(i);
                    String pmtTxId = "tx_pmt_" + bill.getId() + "_" + i;
                    Transaction pmtTx = getAllTransactions().stream()
                            .filter(t -> pmtTxId.equals(t.getId()))
                            .findFirst().orElse(null);
                    if (pmtTx == null) {
                        pmtTx = new Transaction();
                        pmtTx.setId(pmtTxId);
                        pmtTx.setBillId(bill.getId());
                        pmtTx.setBillNo(bill.getBillNo());
                        pmtTx.setBuyerId(buyerId);
                        pmtTx.setBuyerName(buyerName != null && !buyerName.isBlank() ? buyerName : "Walk-in Customer");
                        pmtTx.setBookType("CC");
                        pmtTx.setTransactionType("payment");
                        pmtTx.setTransactionDate(bp.getDate() != null && !bp.getDate().isBlank() ? bp.getDate() : bill.getDate());
                        pmtTx.setAmount(bp.getAmount());
                        pmtTx.setCheckNumber("PMT-" + bill.getBillNo() + (bp.getReference() != null && !bp.getReference().isBlank() ? " (" + bp.getReference() + ")" : ""));
                        pmtTx.setIncludeInReporting(true);
                        saveTransaction(pmtTx);
                    } else {
                        pmtTx.setAmount(bp.getAmount());
                        if (bp.getDate() != null && !bp.getDate().isBlank()) pmtTx.setTransactionDate(bp.getDate());
                        saveTransaction(pmtTx);
                    }
                }
            } else if (bill.getStatus() == com.invoicestudio.model.BillStatus.PAID) {
                // Fully paid without explicit payment records
                String pmtTxId = "tx_pmt_" + bill.getId() + "_full";
                Transaction pmtTx = getAllTransactions().stream()
                        .filter(t -> pmtTxId.equals(t.getId()))
                        .findFirst().orElse(null);
                String payDate = bill.getPaidAt() != null && !bill.getPaidAt().isBlank() ? bill.getPaidAt() : bill.getDate();
                if (pmtTx == null) {
                    pmtTx = new Transaction();
                    pmtTx.setId(pmtTxId);
                    pmtTx.setBillId(bill.getId());
                    pmtTx.setBillNo(bill.getBillNo());
                    pmtTx.setBuyerId(buyerId);
                    pmtTx.setBuyerName(buyerName != null && !buyerName.isBlank() ? buyerName : "Walk-in Customer");
                    pmtTx.setBookType("CC");
                    pmtTx.setTransactionType("payment");
                    pmtTx.setTransactionDate(payDate);
                    pmtTx.setAmount(amount);
                    pmtTx.setCheckNumber("PAID-" + bill.getBillNo());
                    pmtTx.setIncludeInReporting(true);
                    saveTransaction(pmtTx);
                } else {
                    pmtTx.setAmount(amount);
                    pmtTx.setTransactionDate(payDate);
                    saveTransaction(pmtTx);
                }
            } else if (bill.getStatus() == com.invoicestudio.model.BillStatus.UNPAID) {
                // If status reverted to unpaid and full payment existed, delete full payment transaction
                String pmtTxId = "tx_pmt_" + bill.getId() + "_full";
                Transaction pmtTx = getAllTransactions().stream()
                        .filter(t -> pmtTxId.equals(t.getId()))
                        .findFirst().orElse(null);
                if (pmtTx != null) {
                    deleteTransaction(pmtTx.getId(), "Status reverted to unpaid");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveSettings(Settings s) {
        settingsDao.saveSettings(s);
        invalidateSettings();
    }

    // ---------- Transports Caching & Wrappers ----------

    public List<com.invoicestudio.model.Transport> getAllTransports() {
        synchronized (this) {
            if (transportsCache == null) {
                transportsCache = transportDao.getAllTransports();
            }
            return transportsCache;
        }
    }

    public void invalidateTransports() {
        synchronized (this) {
            transportsCache = null;
        }
    }

    public void saveTransport(com.invoicestudio.model.Transport t) {
        transportDao.saveTransport(t);
        invalidateTransports();
    }

    public void deleteTransport(String id) {
        transportDao.deleteTransport(id);
        invalidateTransports();
    }

    // ---------- Categories Caching & Wrappers ----------

    public List<com.invoicestudio.model.ItemCategory> getAllCategories() {
        synchronized (this) {
            if (categoriesCache == null) {
                categoriesCache = categoryDao.getAllCategories();
            }
            return categoriesCache;
        }
    }

    public void invalidateCategories() {
        synchronized (this) {
            categoriesCache = null;
        }
    }

    public void saveCategory(com.invoicestudio.model.ItemCategory c) {
        categoryDao.saveCategory(c);
        invalidateCategories();
    }

    public void deleteCategory(String id) {
        categoryDao.deleteCategory(id);
        invalidateCategories();
    }

    // ---------- Transactions Caching & Wrappers ----------

    public List<com.invoicestudio.model.Transaction> getAllTransactions() {
        synchronized (this) {
            if (transactionsCache == null) {
                transactionsCache = transactionDao.getAllTransactions();
            }
            return transactionsCache;
        }
    }

    public void invalidateTransactions() {
        synchronized (this) {
            transactionsCache = null;
        }
    }

    public void saveTransaction(com.invoicestudio.model.Transaction t) {
        transactionDao.saveTransaction(t);
        invalidateTransactions();
    }

    public void deleteTransaction(String id, String reason) {
        transactionDao.deleteTransaction(id, reason);
        invalidateTransactions();
    }

    public void deleteTransaction(String id) {
        deleteTransaction(id, "Deleted by user");
    }

    // ---------- Convenience wrappers for Items & Buyers ----------

    public List<com.invoicestudio.model.ItemRecord> getAllItems() {
        return itemDao.getAllItems();
    }

    public List<com.invoicestudio.model.Buyer> getAllBuyers() {
        return buyerDao.getAllBuyers();
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

    // ---------- First-run seeding & Synchronization ----------

    public void syncBillsToTransactions() {
        try {
            List<Bill> allBills = getAllBills();
            List<Transaction> allTxs = getAllTransactions();
            Set<String> existingBillIds = allTxs.stream()
                    .map(Transaction::getBillId)
                    .filter(s -> s != null && !s.isBlank())
                    .collect(Collectors.toSet());
            Set<String> existingCheckNos = allTxs.stream()
                    .map(Transaction::getCheckNumber)
                    .filter(s -> s != null && !s.isBlank())
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());

            for (Bill b : allBills) {
                if (!existingBillIds.contains(b.getId()) &&
                    (b.getBillNo() == null || !existingCheckNos.contains(("INV-" + b.getBillNo()).toUpperCase()))) {
                    syncBillTransaction(b);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void seedIfEmpty() {
        List<Template> existing = templateDao.getAllTemplates();
        if (existing.isEmpty()) {
            for (Template t : PresetTemplates.getAllPresets()) {
                templateDao.saveTemplate(t);
            }
        }
        syncBillsToTransactions();
    }
}
