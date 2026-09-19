# Chapter 8 — The Data Engine: DataManager & Caching

> **Part 4 of InvoiceStudio: Zero to Finished Product**
> File covered in full this chapter: `ui/DataManager.java` (≈650 lines, read and
> reproduced section by section from the repository). Supporting cast from earlier
> chapters: all 17 DAOs (Chapters 4–5) and the models (Chapters 6–7).
> Goal at the end: you understand every read and write in the app flows through one
> hub — why it caches, how invalidation works, how the stock ledger and the financial
> ledger stay synchronised with billing, and how navigation knows when data went stale.

---

## 1. Chapter goal

By the end of this chapter you will have built the **singleton data hub** that:

- creates every DAO exactly once and hands the same instance to all consumers,
- caches the hot collections (bills, settings, transports, categories, transactions,
  purchases, expense accounts) in memory,
- invalidates those caches on every write and bumps a **data epoch** that the
  navigation layer uses for stale-while-revalidate refresh (Chapter 9 consumes this),
- keeps three ledgers consistent as side-effects of one call: bill → stock ledger rows,
  bill → transaction rows, purchase → stock ledger rows,
- seeds first-run data per user.

## 2. Story intro

Imagine a **librarian** in a small office. Ten employees constantly ask her for the
same dozen files. A bad librarian walks to the archive room for every request. A good
librarian keeps the hot files on her desk and — this is the crucial part — *puts a file
back in the archive the moment someone edits it*, so nobody ever receives yesterday's
version of an edited file.

`DataManager` is that librarian. Its caches are the desk; `invalidateXxx()` is putting
the edited file back; `dataEpoch` is a page counter on the desk that the office
intercom announces on every edit, so any employee who last looked at page 4 knows their
notes are stale when they hear "page 5".

The second story is **accounting discipline**. When a shop saves an invoice, three
things must happen: the invoice is stored, the stock moves out, and the money ledger
gets a matching entry. Forget one and the books silently lie. `DataManager.saveBill()`
exists so that *no caller anywhere* can save a bill without the side-effects — the
hub, not the views, owns the transaction rules.

## 3. Concepts first

- **Singleton** — a class of which exactly one instance exists, with a global access
  point. Here: `DataManager.init(db)` once at boot; `DataManager.get()` everywhere
  after. If two views created their own hubs, their caches would disagree.
- **Cache** — a copy of slow-to-fetch data kept in fast memory. Correctness rule: every
  write must either update or drop the cache ("invalidate"), or the UI shows ghosts.
- **Write-through-ish pattern** — this codebase *invalidates* rather than updates: a
  write nulls the cache; the next read re-queries SQLite and repopulates. Simpler and
  always consistent; costs one extra read per write, which is nothing at local-SQLite
  speed.
- **`volatile`** — a Java keyword meaning "writes to this field are immediately visible
  to all threads". `dataEpoch` is `volatile long` because background threads bump it
  while the FX thread reads it.
- **`synchronized (this)`** — only one thread may execute this block at a time. All
  cache reads/writes are synchronised so a background warm and a UI read cannot
  interleave.
- **`CopyOnWriteArrayList`** — a list that snapshots itself on every modification;
  iterators never see `ConcurrentModificationException`. Used for the listener list,
  which is read constantly and changed almost never.
- **Stale-while-revalidate** — serve the cached (possibly old) content immediately, and
  refresh in the background. The user never waits on data they can mostly see.
- **Ledger recompute** — instead of `current_stock = current_stock - qty` (arithmetic
  drifts with bugs), the app stores every movement as a row and recomputes balances
  from the rows: balance = Σin − Σout. Slower per write, but impossible to drift.

## 4. Files in this chapter

| File | Type | Role |
|---|---|---|
| `ui/DataManager.java` | Singleton hub | DAO registry, caches, epochs, ledger side-effects, seeding |
| *(delegates to)* all 17 DAOs | `db/*` | Chapters 4–5 |
| *(consumed by)* `ui/StudioApp.java` | Shell | warm-up, epoch checks (Chapter 9) |
| *(consumed by)* every view + MCP | — | Chapters 11–18 |

## 5. Step-by-step build

### 5.1 The singleton frame and shared DAOs

```java
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
    ...
    public static synchronized DataManager init(DatabaseManager db) {
        if (instance == null) instance = new DataManager(db);
        return instance;
    }

    public static DataManager get() {
        if (instance == null)
            throw new IllegalStateException("DataManager not initialised. Call init() first.");
        return instance;
    }
```

- `final` class: not meant to be extended; the hub's rules must not be bypassed.
- The constructor builds **all 16 DAOs once**. The class doc records why: views used to
  `new` their own DAOs, multiplying prepared-statement setup and (worse) making caches
  impossible — each view would have had its own private copy of the truth.
- `init()` is `synchronized` and idempotent (`if instance == null`): whoever calls
  first wins, later callers get the same hub.
- `get()` throws instead of silently initialising — a *fail-fast* contract that turns
  an ordering bug into a loud stack trace at boot, not a mystery NPE at midnight.
- The constructor's last line wires a session listener:

```java
com.invoicestudio.service.AuthSessionManager.addSessionChangeListener(session -> onUserSwitched());
```

When a different user signs in (Chapter 10), `onUserSwitched()` drops every cache —
user A's invoices must never be visible to user B. Data is partitioned per user at the
DAO level (Chapter 4); the hub adds the cache-level guarantee.

### 5.2 The epoch counter — the heartbeat of freshness

```java
private volatile long dataEpoch = 0L;

public long dataEpoch() { return dataEpoch; }
private void bumpEpoch() { dataEpoch++; }
```

Tiny, and load-bearing. Every invalidation calls `bumpEpoch()`. Chapter 9's
`ViewEpochTracker` stores "the epoch each view last rendered" and compares on
navigation: moved counter → that view's data changed since it was last shown → refresh
it in the background. One counter, no per-table bookkeeping, no locks beyond `volatile`.

Note what does *not* bump the epoch: pure reads. A cache fill (`billsCache == null` →
query → store) is not a write; the epoch only moves when the *underlying data* could
have changed.

### 5.3 Cache warming

```java
public void warmCachesAsync(java.util.concurrent.ExecutorService executor, Runnable onDone) {
    executor.execute(() -> {
        try {
            getSettings(); getAllBills(); getAllTransactions(); getAllBuyers();
            getAllPurchases(); getAllExpenses(); getAllExpenseAccounts();
            getAllCategories(); getAllTransports();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error("Cache warm failed", e);
        }
        if (onDone != null) onDone.run();
    });
}

public void warmCachesNow() { /* the same nine reads, synchronous */ }
```

- `warmCachesAsync` runs at login on a background executor: by the time the user
  clicks a nav item, the DB reads are already done and the view paints from memory.
  `onDone` deliberately runs on the worker thread — the doc comment warns callers to
  wrap UI work in `Platform.runLater` (Chapter 9 does).
- `warmCachesNow` is the synchronous twin, used by navigation re-warms which already
  run on a background thread.
- Every warm call goes through the *cached getters*, so warming is just "touch each
  collection once" — no separate fill logic exists. One path, tested everywhere.
- Failures are logged, never propagated: a warm failure degrades to a slow first
  navigation, not a broken boot.

### 5.4 Cached reads — one pattern, seven collections

```java
public List<Bill> getAllBills() {
    synchronized (this) {
        if (billsCache == null) {
            billsCache = billDao.getAllBills();
        }
        return billsCache;
    }
}

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
```

The read pattern is always *check → fill → return inside one monitor*. Two subtleties:

- **The returned list is the live cache.** A view holding `getAllBills()` sees the same
  object later reads return — great for consistency, but callers must treat it as
  read-only (views copy before sorting — see Chapter 11). `ISSUE:` nothing enforces
  this; an unmodifiable wrapper would.
- **`getSettings()` self-heals**: if the settings row doesn't exist (fresh user), it
  creates and persists a default in the same breath. Callers can never see null.

Two collections are *deliberately uncached*:

```java
public List<com.invoicestudio.model.Expense> getAllExpenses() {
    return expenseDao.getAllExpenses();          // uncached; low volume
}
public List<ItemRecord> getAllItems()  { return itemDao.getAllItems(); }
public List<Buyer> getAllBuyers()      { return buyerDao.getAllBuyers(); }
public List<Supplier> getAllSuppliers(){ return supplierDao.getAllSuppliers(); }
```

The comments and call-patterns explain the economics: expenses are low-volume;
buyers/items are read by views that re-render cells constantly, but they were the very
bottleneck the cache project removed — those views now hold their own sorted copies and
re-read only on invalidation (Chapter 11). Delegating without caching keeps the hub
simple where measurement said it didn't matter.

### 5.5 Invalidation — the write side of the bargain

```java
public void invalidateBills() {
    synchronized (this) { billsCache = null; }
    bumpEpoch();
    notifyBillsChanged();
}

public void invalidateSettings() { synchronized (this) { settingsCache = null; } bumpEpoch(); }
public void invalidateTransports() { ... }   // + bumpEpoch()
public void invalidateCategories() { ... }   // + bumpEpoch()
public void invalidateTransactions(){ ... }  // + bumpEpoch()
public void invalidatePurchases()  { ... }   // + bumpEpoch()
public void invalidateExpenseAccounts() { ... } // + bumpEpoch()
```

Three steps, always: **null the cache → bump the epoch → notify listeners** (bills
only). The monitor guards only the field swap; the DAO query later runs outside the
lock, so a long re-read never blocks other threads from at least seeing `null` and
queuing their own fill.

`notifyBillsChanged()` snapshots the fresh list and hands it to every listener:

```java
private void notifyBillsChanged() {
    List<Bill> snapshot = getAllBills();
    for (Consumer<List<Bill>> l : billsListeners) {
        try { l.accept(snapshot); }
        catch (Exception ignored) { AppLog.debug(ignored); }
    }
}
```

Listener exceptions are swallowed-and-logged: one broken dashboard must not stop the
seven other listeners from receiving data. This is the same isolation principle the
chatbot log manager uses (Chapter 19).

### 5.6 `saveBill()` — the transactional heart

```java
public Bill saveBill(Bill bill) {
    billDao.saveBill(bill);
    invalidateBills();
    // Sales decrement stock (Tally: every sales voucher moves inventory out)
    stockLedgerDao.deleteByVoucher(bill.getId());
    if (bill.getStatus() != BillStatus.CANCELLED) {
        recordSaleStockOut(bill);
    } else {
        // cancelled: no stock movement, but touched items' balances are recomputed
        java.util.Set<String> touched = ...collect item ids...;
        for (String itemId : touched) stockLedgerDao.recomputeItem(itemId);
    }
    syncBillTransaction(bill);
    return bill;
}
```

Read it as a checklist the whole app depends on:

1. **Persist** via `BillDao` (upsert + payments + items in one transaction — Ch. 5).
2. **Invalidate** the bills cache (bump + notify inside).
3. **Stock ledger rewrite**: `deleteByVoucher` then re-append — the "recompute from
   rows" philosophy. Saving the same bill twice never double-moves stock.
4. **CANCELLED bills move no stock** but still get balance recomputes for their items
   (a cancel after edits must reflect reality).
5. **Financial ledger sync** (`syncBillTransaction`) — see below.

Every view, the MCP server and the recurring-invoice engine call *this* method; none of
them call `billDao` directly for saves. That single choke point is why the ledgers
never disagree.

`deleteBill(id)` mirrors it: delete → invalidate → remove stock rows → sweep
transactions tied to the bill (by `billId` or the `"INV-" + billNo` check-number
convention) and delete each with a human reason (`"Deleted with invoice"`).

### 5.7 `syncBillTransaction()` — bills ↔ money ledger

The ~90-line method keeps the transactions table in lockstep with a bill. Its rules,
in order:

- **Find the existing sale transaction** by `billId`, or by the `"INV-" + billNo`
  check-number convention (a legacy link format kept so old data keeps matching).
- **Resolve the buyer** by name from the cached buyers (id first, display-name
  fallback) so ledger rows can be grouped per customer.
- **Quantity** = Σ item quantities (rounded); **amount** = `totals.grandTotal`;
  **parcels** = `bill.parcel > 0 ? parcel : 1`.
- **No existing row** → create `tx_<billId>`, book type `"CC"`, type `"sale"`, date
  from the bill (or today), and write back `bill.setTransactionId(t.getId())`.
- **Existing row** → update amount/qty/date/buyer and un-delete it (`setDeleted(false)`)
  — editing a bill repairs its ledger row.
- **Payments**: each `BillPayment` gets its own `tx_pmt_<billId>_<i>` transaction
  (`type = "payment"`, reference in the check number as `"PMT-INV-12 (UPI)"`) so
  partial payments appear as separate money-in rows. Re-saving a bill updates them.
- **PAID without payment rows** → synthesise one full payment row dated `paidAt`.
- **Reverted to UNPAID** → that synthesised full-payment row is *deleted* with reason
  `"Status reverted to unpaid"`.
- The whole body is wrapped in `try/catch → AppLog.error`: a ledger sync failure is
  logged, never thrown at the user after their bill already saved. `ISSUE:` that
  resilience has a cost — if sync failed, the books are subtly wrong until
  `syncBillsToTransactions()` (5.9) repairs them at next boot.

### 5.8 Purchases, stock, and the ledger helpers

```java
public PurchaseBill savePurchase(PurchaseBill bill) {
    purchaseBillDao.savePurchaseBill(bill);
    invalidatePurchases();
    stockLedgerDao.deleteByVoucher(bill.getId());
    // ... append V_PURCHASE rows per item line, recompute each item ...
    return bill;
}
```

Same shape as `saveBill`, mirrored for goods coming **in**. The shared helpers:

```java
private ItemRecord resolveItem(String id, String name) {
    // by id first; then a linear scan of items matching name (case-insensitive)
}

private void recordSaleStockOut(Bill bill) {
    // per line with a resolvable catalog item and qty > 0:
    //   stockLedgerDao.append(itemId, date, V_SALE, billId, billNo, /*in*/0, /*out*/qty, rate)
    // then recompute balances for the touched item set
}

private void removeSaleStockOut(Bill bill) {
    // deleteByVoucher + recompute (used by deleteBill and CANCELLED saves)
}
```

- `resolveItem`'s name fallback is what lets free-typed bill lines still move stock if
  they happen to match a catalog item name. `ISSUE:` two items named "Shirt" (different
  categories) resolve to the first match — an acceptable ambiguity for a boutique, a
  trap for a warehouse.
- `recomputeItem` (Ch. 5) rewrites the item's `current_stock` from the ledger rows;
  the `touched` `HashSet` guarantees each item recomputes once per save even if it
  appears on 10 lines.
- `getStockBalances()` exposes `stockLedgerDao.allBalances()` for dashboards and the
  MCP `stock_report` tool — one map, computed from rows, never from a counter.

### 5.9 Seeding and repair — `seedIfEmpty()` and `syncBillsToTransactions()`

```java
public void seedIfEmpty() {
    String uid = AuthSessionManager.getCurrentUserId();
    if (uid.isEmpty()) return;                       // never seed before login
    if (settingsDao.hasNoSettingsForUser(uid)) settingsDao.saveSettings(new Settings());
    if (templateDao.getAllTemplates().isEmpty())
        for (Template t : PresetTemplates.getAllPresets()) templateDao.saveTemplate(t);
    if (categoryDao.getAllCategories().isEmpty())
        categoryDao.saveCategory(new ItemCategory("cat_trouser", "Trouser"));
    if (itemDao.getAllItems().isEmpty()) {
        ItemRecord defItem = new ItemRecord("item_pent", "PENT", "6203", "PCS", 550.0, 5.0);
        defItem.setCategoryId("cat_trouser"); defItem.setCategoryName("Trouser");
        itemDao.saveItem(defItem);
    }
    syncBillsToTransactions();
}
```

Four seeds, each guarded by *emptiness* so it runs exactly once per user, plus the
ledger repair sweep. This is where Chapter 7's `PresetTemplates` gets consumed — and
note what is *not* here: the `resources/seed/*.json` files are never read by this code
(the `ISSUE:` already flagged in Chapter 3; the JSON files are vestigial demo data).

`syncBillsToTransactions()` builds two sets of existing links (`billId`s and
upper-cased `"INV-"+billNo` check numbers) and calls `syncBillTransaction` only for
bills missing from both — an idempotent boot-time repair pass.

### 5.10 The wrappers — why views never see a DAO constructor

The hub exposes short accessors (`bills()`, `buyers()`, `items()`, `templates()`,
`stockLedger()`, …) plus write-through wrappers for every cached collection:

```java
public void saveTransport(Transport t) { transportDao.saveTransport(t); invalidateTransports(); }
public void deleteCategory(String id)  { categoryDao.deleteCategory(id); invalidateCategories(); }
public void saveTransaction(Transaction t) { transactionDao.saveTransaction(t); invalidateTransactions(); }
```

Each write wrapper is *persist + invalidate* — the two-line contract that keeps caches
honest. `expenseAccountUsage()` is a nice bonus: one O(n) pass over expenses building
per-payee usage stats (voucher count, total, last date — ISO dates compare correctly as
strings), exposed to the Expense Accounts dialog and MCP.

`onUserSwitched()` closes the loop from 5.1: null every cache, bump the epoch, notify
bills listeners. Cross-user cache leaks are impossible.

## 6. How it works at runtime

```
Boot (Ch. 2/9):    StudioApp → DatabaseManager → DataManager.init(db)
Login:             seedIfEmpty() → warmCachesAsync(executor, …)
View opens:        cached() → DataManager.getAllBills() → memory hit
User saves bill:   CreateBillView → DataManager.saveBill(bill)
                     ├─ BillDao.saveBill        (SQLite transaction)
                     ├─ invalidateBills()       (cache=null, epoch++, listeners)
                     ├─ StockLedger rewrite     (deleteByVoucher → V_SALE rows → recompute)
                     └─ syncBillTransaction()   (sale tx + payment txs)
Other views hear:  epoch bumped → next navigation sees "stale" → background re-warm
Logout/switch:     onUserSwitched() → all caches null → next login re-seeds nothing
                   (rows exist) but re-reads only what that user's views touch
```

## 7. How to change it

- **Add a cached collection** (e.g. suppliers become hot): add field + accessor pair,
  copy the 5.4 pattern verbatim, add `invalidateSuppliers()` following 5.5, convert the
  three save/delete call sites to the wrapper, and add the read to both warm methods.
  Forgetting the warm entry just means a slower first open — forgetting the
  invalidation means stale data, so grep for the DAO's write methods after any change.
- **Add a side-effect to bill saves** (e.g. loyalty points): add it to `saveBill()`
  *after* `invalidateBills()`, wrapped in its own try/catch, and mirror the reverse
  effect in `deleteBill()`. Every rule must have an undo — that symmetry is what keeps
  the ledgers balanced.
- **Verify**: save a bill → check History shows it, Stock Analysis moved the item down,
  and Transactions gained the sale row; edit its total → the transaction row updates;
  delete it → the rows disappear. Then run `mvn test -Dtest=WorkshopScenarioTest`.

## 8. Performance & UX analysis

- **What was done:** one shared DAO set + invalidate-on-write caches + a warm pass.
  *Cost:* memory for 7 collections (thousands of small objects — a few MB worst case);
  one full re-read after each write. *Alternative:* per-write cache patching (insert the
  new bill into the list instead of nulling). *Why not:* patch code has to replicate
  every DAO write rule and is where stale-ghost bugs breed; a local SQLite re-read of
  even 5,000 bills is ~10 ms on a background thread. *Trade-off:* Easy vs Medium.
- **What the user notices:** first paint of every view is instant after login (warm);
  after saving, the very next navigation re-reads in the background while the cached
  view shows — the "⟳ Refreshing…" pill (Ch. 9) is the only visible trace.
- **OPTIONAL IMPROVEMENT (Easy):** return `Collections.unmodifiableList(cache)` from
  the cached getters and give views an explicit `copyForSorting()` — removes the
  accidental-mutation hazard noted in 5.4. *Cost:* one-line change per getter plus
  fixing any caller that mutates (the compiler/test suite finds them).
- **OPTIONAL IMPROVEMENT (Medium):** replace `synchronized(this)` with per-collection
  locks (`ReentrantReadWriteLock`) if profiles ever show warm-vs-read contention.
  At local-SQLite scale they never will — noted so the choice stays deliberate.

## 9. Common mistakes and fixes

| Symptom | Cause | Fix |
|---|---|---|
| View shows a just-deleted record | View kept its own copy; invalidation bypassed | Write via `DataManager` wrappers only |
| Stock doesn't move on a sale | Bill line's `desc` doesn't match any catalog item and no `id` set | Link the line to an item in CreateBillView |
| Transactions show a duplicate sale row | Older code path saved bills via `billDao` directly | Route saves through `saveBill()`; run `syncBillsToTransactions()` |
| Ledger balances drift after an app crash mid-save | Side-effects are sequential, not one SQLite transaction | Re-save the bill (idempotent `deleteByVoucher`) or restore a backup |
| `IllegalStateException: DataManager not initialised` | A static path touched `get()` before `init()` | Initialise in the boot sequence before any view/MCP use |
| Cache seems stale for user B after switch | Custom write path forgot invalidation | Add `invalidateXxx()` or use the wrapper |

## 10. Checkpoint

```bash
mvn test -Dtest=WorkshopScenarioTest,ExpenseAccountTest,AuthAndDataPartitioningTest
```

Green means: billing→ledger sync, expense account rollups, and per-user partitioning
all behave. Exercises:

1. Add a `notesCache` for a hypothetical `NoteDao`: getter, invalidation, warm entry,
   and a save wrapper — verify with a two-line temporary main that writes then reads.
2. In `saveBill`, temporarily comment out `syncBillTransaction(bill)` and run
   `WorkshopScenarioTest` — observe which assertion catches the missing ledger row.
   Restore it.
3. Add `Collections.unmodifiableList` to `getAllBills()` and run the full suite;
   find any caller that mutates the returned list (the OPTIONAL IMPROVEMENT made real).

## 11. Summary and coverage self-check

`DataManager` is the app's single point of truth-by-policy: shared DAOs, seven cached
collections with invalidate+epoch discipline, warm-up for instant navigation, and the
three-way ledger consistency (bill ↔ stock ↔ transactions) enforced at exactly one
choke point. Everything downstream — views, MCP, dashboards — is a consumer of rules
defined here.

**Covered in full this chapter (1/1):** `ui/DataManager.java` — every field, every
method group (singleton frame, epoch, warm, cached reads, invalidations, `saveBill`,
`deleteBill`, `syncBillTransaction`, purchase/stock helpers, `seedIfEmpty`,
`syncBillsToTransactions`, wrappers, `AccountUsage`, `onUserSwitched`, listener bus),
with the two-line bodies shown verbatim where they are the contract.

**Markers raised this chapter:**
- `ISSUE:` cached lists are returned live (mutable) — enforced only by convention.
- `ISSUE:` `resolveItem`'s name fallback can match the wrong duplicate-named item.
- `ISSUE:` `syncBillTransaction` failures are swallowed after the bill saved; repair
  happens only via the boot-time `syncBillsToTransactions()`.
- `GAP:` `resources/seed/*.json` remain unreferenced by seeding (carried from Ch. 3).

**Next: Chapter 9 — The Shell: Window, Theme, Sidebar, Navigation.**
