package com.invoicestudio.ui;

import com.invoicestudio.service.AppLog;
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
import com.invoicestudio.model.ItemCategory;
import com.invoicestudio.model.ItemRecord;
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
    private final AuthDao authDao;
    private final SupplierDao supplierDao;
    private final PurchaseBillDao purchaseBillDao;
    private final StockLedgerDao stockLedgerDao;
    private final ExpenseDao expenseDao;
    private final com.invoicestudio.db.ExpenseAccountDao expenseAccountDao;

    /** Cache invalidated on any bill write. Guarded by the monitor of this list. */
    private List<Bill> billsCache;
    private List<com.invoicestudio.model.Transport> transportsCache;
    private List<com.invoicestudio.model.ItemCategory> categoriesCache;
    private List<com.invoicestudio.model.Transaction> transactionsCache;
    private List<com.invoicestudio.model.PurchaseBill> purchasesCache;
    private List<com.invoicestudio.model.ExpenseAccount> expenseAccountsCache;
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
        this.authDao = new AuthDao(db);
        this.supplierDao = new SupplierDao(db);
        this.purchaseBillDao = new PurchaseBillDao(db);
        this.stockLedgerDao = new StockLedgerDao(db);
        this.expenseDao = new ExpenseDao(db);
        this.expenseAccountDao = new com.invoicestudio.db.ExpenseAccountDao(db);

        com.invoicestudio.service.AuthSessionManager.addSessionChangeListener(session -> onUserSwitched());
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

    /**
     * Monotonic counter bumped on EVERY cache invalidation (any data write) —
     * views compare the epoch they were built against to detect staleness
     * without re-reading anything (stale-while-revalidate navigation).
     */
    private volatile long dataEpoch = 0L;

    /** Current data generation; changes on every write/invalidation. */
    public long dataEpoch() { return dataEpoch; }

    private void bumpEpoch() { dataEpoch++; }

    /**
     * Pre-loads every cached collection on a background executor so the first
     * navigation to each view paints instantly. {@code onDone} runs on the
     * worker thread — wrap in {@code Platform.runLater} if it touches UI.
     */
    public void warmCachesAsync(java.util.concurrent.ExecutorService executor, Runnable onDone) {
        executor.execute(() -> {
            try {
                getSettings();
                getAllBills();
                getAllTransactions();
                getAllBuyers();
                getAllPurchases();
                getAllExpenses();
                getAllExpenseAccounts();
                getAllCategories();
                getAllTransports();
            } catch (Exception e) {
                com.invoicestudio.service.AppLog.error("Cache warm failed", e);
            }
            if (onDone != null) onDone.run();
        });
    }

    /**
     * Synchronously touches every cached collection — call from a BACKGROUND
     * thread (e.g. inside {@link #warmCachesAsync} or a nav re-warm) so the
     * next FX-thread read finds warm caches. Safe to call from any thread.
     */
    public void warmCachesNow() {
        getSettings();
        getAllBills();
        getAllTransactions();
        getAllBuyers();
        getAllPurchases();
        getAllExpenses();
        getAllExpenseAccounts();
        getAllCategories();
        getAllTransports();
    }

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
    public AuthDao auth() { return authDao; }
    public SupplierDao suppliers() { return supplierDao; }
    public PurchaseBillDao purchases() { return purchaseBillDao; }
    public StockLedgerDao stockLedger() { return stockLedgerDao; }
    public ExpenseDao expenses() { return expenseDao; }
    public com.invoicestudio.db.ExpenseAccountDao expenseAccounts() { return expenseAccountDao; }

    public void onUserSwitched() {
        synchronized (this) {
            billsCache = null;
            transportsCache = null;
            categoriesCache = null;
            transactionsCache = null;
            purchasesCache = null;
            expenseAccountsCache = null;
            settingsCache = null;
        }
        bumpEpoch();
        notifyBillsChanged();
    }

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
        bumpEpoch();
        notifyBillsChanged();
    }

    /** Call after settings were persisted. */
    public void invalidateSettings() {
        synchronized (this) {
            settingsCache = null;
        }
        bumpEpoch();
    }

    /**
     * Call after any template was created/updated/deleted outside this class
     * (designer save, duplicate, import, delete, presets). Templates have no
     * DataManager-side cache, but the epoch bump is what makes the cached
     * Templates Gallery re-read the table on its next show — without it the
     * gallery kept showing the old names/cards until the app restarted
     * (reported: renamed template still listed under the old name).
     */
    public void invalidateTemplates() {
        bumpEpoch();
    }

    /**
     * Persist a bill, refresh the cache and notify listeners in one call.
     * Automatically synchronizes with the financial ledger (transactions table).
     * Returns the saved bill for chaining.
     */
    public Bill saveBill(Bill bill) {
        billDao.saveBill(bill);
        invalidateBills();
        // Sales decrement stock (Tally: every sales voucher moves inventory out)
        stockLedgerDao.deleteByVoucher(bill.getId());
        if (bill.getStatus() != com.invoicestudio.model.BillStatus.CANCELLED) {
            recordSaleStockOut(bill);
        } else {
            java.util.Set<String> touched = new java.util.HashSet<>();
            if (bill.getItems() != null) {
                for (com.invoicestudio.model.BillItem it : bill.getItems()) {
                    com.invoicestudio.model.ItemRecord catalogItem = resolveItem(it.getId(), it.getDesc());
                    if (catalogItem != null) touched.add(catalogItem.getId());
                }
            }
            for (String itemId : touched) stockLedgerDao.recomputeItem(itemId);
        }
        syncBillTransaction(bill);
        return bill;
    }

    public void deleteBill(String id) {
        Bill removed = billDao.getBillById(id);
        billDao.deleteBill(id);
        invalidateBills();
        if (removed != null) removeSaleStockOut(removed);
        try {
            List<Transaction> txs = getAllTransactions();
            for (Transaction t : txs) {
                if (id != null && (id.equals(t.getBillId()) || (t.getId() != null && t.getId().contains(id)))) {
                    deleteTransaction(t.getId(), "Deleted with invoice");
                }
            }
        } catch (Exception ignored) {
            AppLog.debug(ignored); }
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
                t.setCheckNumber("");
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
                existing.setCheckNumber("");
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
            com.invoicestudio.service.AppLog.error(e);
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
        bumpEpoch();
    }

    public com.invoicestudio.model.Transport getTransportById(String id) {
        if (id == null || id.isBlank()) return null;
        synchronized (this) {
            if (transportsCache != null) {
                for (com.invoicestudio.model.Transport t : transportsCache) {
                    if (id.equals(t.getId())) return t;
                }
                return null;
            }
        }
        return transportDao.getTransportById(id);
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
        bumpEpoch();
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
        bumpEpoch();
    }

    public void saveTransaction(com.invoicestudio.model.Transaction t) {
        transactionDao.saveTransaction(t);
        invalidateTransactions();
    }

    public void deleteTransaction(String id, String reason) {
        transactionDao.deleteTransaction(id, reason);
        invalidateTransactions();
    }

    // ---------- Purchases Caching & Wrappers (stock-synced) ----------

    public List<com.invoicestudio.model.PurchaseBill> getAllPurchases() {
        synchronized (this) {
            if (purchasesCache == null) {
                purchasesCache = purchaseBillDao.getAllPurchaseBills();
            }
            return purchasesCache;
        }
    }

    public void invalidatePurchases() {
        synchronized (this) {
            purchasesCache = null;
        }
        bumpEpoch();
    }

    /** Live stock balance per item id (opening + ledger movements). */
    public java.util.Map<String, Double> getStockBalances() {
        return stockLedgerDao.allBalances();
    }

    // ---------- Expenses (uncached; low volume) ----------

    public List<com.invoicestudio.model.Expense> getAllExpenses() {
        return expenseDao.getAllExpenses();
    }

    public void saveExpense(com.invoicestudio.model.Expense e) {
        expenseDao.saveExpense(e);
    }

    public void deleteExpense(String id) {
        expenseDao.deleteExpense(id);
    }

    // ---------- Expense accounts (payee registry; cached) ----------

    public List<com.invoicestudio.model.ExpenseAccount> getAllExpenseAccounts() {
        synchronized (this) {
            if (expenseAccountsCache == null) {
                expenseAccountsCache = expenseAccountDao.getAllAccounts();
            }
            return expenseAccountsCache;
        }
    }

    public void invalidateExpenseAccounts() {
        synchronized (this) {
            expenseAccountsCache = null;
        }
        bumpEpoch();
    }

    /**
     * One O(n) pass over cached expenses — usage stats per account name
     * (case-insensitive): voucher count, total amount, last-used date.
     */
    public java.util.Map<String, AccountUsage> expenseAccountUsage() {
        java.util.Map<String, AccountUsage> usage = new java.util.HashMap<>();
        for (com.invoicestudio.model.Expense e : getAllExpenses()) {
            String payee = e.getPayee();
            if (payee == null || payee.isBlank()) continue;
            AccountUsage u = usage.computeIfAbsent(payee.trim().toLowerCase(),
                    k -> new AccountUsage());
            u.vouchers++;
            u.total += e.getAmount();
            String d = e.getDate() != null ? e.getDate() : "";
            if (d.compareTo(u.lastDate) > 0) u.lastDate = d; // yyyy-MM-dd sorts lexicographically
        }
        return usage;
    }

    /** Usage rollup for one account name (mutable for the single-pass loop). */
    public static class AccountUsage {
        public int vouchers;
        public double total;
        public String lastDate = "";
    }

    /**
     * Persist a purchase bill, refresh caches and re-write stock ledger rows
     * so item current_stock stays ledger-accurate (never manually edited).
     */
    /** Catalog item lookup by id, then by name (stock-tracked sale lines). */
    private com.invoicestudio.model.ItemRecord resolveItem(String id, String name) {
        if (id != null && !id.isBlank()) {
            com.invoicestudio.model.ItemRecord it = itemDao.getItemById(id);
            if (it != null) return it;
        }
        if (name != null && !name.isBlank()) {
            for (com.invoicestudio.model.ItemRecord it : itemDao.getAllItems()) {
                if (name.equalsIgnoreCase(it.getName())) return it;
            }
        }
        return null;
    }

    /** Stock OUT rows for a saved sales bill (id- or name-linked lines only). */
    private void recordSaleStockOut(com.invoicestudio.model.Bill bill) {
        if (bill == null || bill.getStatus() == com.invoicestudio.model.BillStatus.CANCELLED || bill.getItems() == null) return;
        for (com.invoicestudio.model.BillItem it : bill.getItems()) {
            com.invoicestudio.model.ItemRecord catalogItem = resolveItem(it.getId(), it.getDesc());
            if (catalogItem != null && it.getQty() > 0) {
                stockLedgerDao.append(catalogItem.getId(), bill.getDate(), StockLedgerDao.V_SALE,
                        bill.getId(), bill.getBillNo(), 0, it.getQty(), it.getRate());
            }
        }
        java.util.Set<String> touched = new java.util.HashSet<>();
        for (com.invoicestudio.model.BillItem it : bill.getItems()) {
            com.invoicestudio.model.ItemRecord catalogItem = resolveItem(it.getId(), it.getDesc());
            if (catalogItem != null) touched.add(catalogItem.getId());
        }
        for (String itemId : touched) stockLedgerDao.recomputeItem(itemId);
    }

    /** Removes sale stock rows of a deleted sales bill and recomputes balances. */
    private void removeSaleStockOut(com.invoicestudio.model.Bill bill) {
        if (bill == null) return;
        stockLedgerDao.deleteByVoucher(bill.getId());
        java.util.Set<String> touched = new java.util.HashSet<>();
        if (bill.getItems() != null) {
            for (com.invoicestudio.model.BillItem it : bill.getItems()) {
                com.invoicestudio.model.ItemRecord catalogItem = resolveItem(it.getId(), it.getDesc());
                if (catalogItem != null) touched.add(catalogItem.getId());
            }
        }
        for (String itemId : touched) stockLedgerDao.recomputeItem(itemId);
    }

    public com.invoicestudio.model.PurchaseBill savePurchase(com.invoicestudio.model.PurchaseBill bill) {
        purchaseBillDao.savePurchaseBill(bill);
        invalidatePurchases();

        // Stock IN rows for every item line (replaces any previous rows for this voucher)
        stockLedgerDao.deleteByVoucher(bill.getId());
        java.util.Set<String> touched = new java.util.HashSet<>();
        if (bill.getItems() != null) {
            for (com.invoicestudio.model.BillItem it : bill.getItems()) {
                com.invoicestudio.model.ItemRecord catalogItem = resolveItem(it.getId(), it.getDesc());
                if (catalogItem != null && it.getQty() > 0) {
                    stockLedgerDao.append(catalogItem.getId(), bill.getDate(), StockLedgerDao.V_PURCHASE,
                            bill.getId(), bill.getBillNo(), it.getQty(), 0, it.getRate());
                    touched.add(catalogItem.getId());
                }
            }
        }
        // Recompute affected item balances
        for (String itemId : touched) {
            stockLedgerDao.recomputeItem(itemId);
        }
        return bill;
    }

    public void deletePurchase(String id) {
        com.invoicestudio.model.PurchaseBill bill = purchaseBillDao.getPurchaseBillById(id);
        purchaseBillDao.deletePurchaseBill(id);
        invalidatePurchases();
        if (bill != null) {
            stockLedgerDao.deleteByVoucher(bill.getId());
            java.util.Set<String> touched = new java.util.HashSet<>();
            if (bill.getItems() != null) {
                for (com.invoicestudio.model.BillItem it : bill.getItems()) {
                    com.invoicestudio.model.ItemRecord catalogItem = resolveItem(it.getId(), it.getDesc());
                    if (catalogItem != null) touched.add(catalogItem.getId());
                }
            }
            for (String itemId : touched) {
                stockLedgerDao.recomputeItem(itemId);
            }
        }
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

    /** All suppliers of the current user (delegates to the shared SupplierDao). */
    public List<com.invoicestudio.model.Supplier> getAllSuppliers() {
        return supplierDao.getAllSuppliers();
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
            } catch (Exception ignored) {
            AppLog.debug(ignored); }
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
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    public void seedIfEmpty() {
        String uid = com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
        if (uid.isEmpty()) {
            return;
        }
        // 1. Settings: seed if empty for this user
        if (settingsDao.hasNoSettingsForUser(uid)) {
            settingsDao.saveSettings(new Settings());
        }
        // 2. Templates: seed presets if empty for this user
        List<Template> existing = templateDao.getAllTemplates();
        if (existing.isEmpty()) {
            for (Template t : PresetTemplates.getAllPresets()) {
                templateDao.saveTemplate(t);
            }
        }
        // 3. Category: seed default 'cat_trouser' ("Trouser") if empty for this user
        List<ItemCategory> existingCats = categoryDao.getAllCategories();
        if (existingCats.isEmpty()) {
            categoryDao.saveCategory(new ItemCategory("cat_trouser", "Trouser"));
        }
        // 4. Item: seed default item 'item_pent' ("PENT") if empty for this user
        List<ItemRecord> existingItems = itemDao.getAllItems();
        if (existingItems.isEmpty()) {
            ItemRecord defItem = new ItemRecord("item_pent", "PENT", "6203", "PCS", 550.0, 5.0);
            defItem.setCategoryId("cat_trouser");
            defItem.setCategoryName("Trouser");
            itemDao.saveItem(defItem);
        }
        syncBillsToTransactions();
    }
}
