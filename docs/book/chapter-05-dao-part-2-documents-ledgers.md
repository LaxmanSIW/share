# Chapter 5 — The DAO Pattern, Part 2 — Documents & Ledgers

> **Part 2 of InvoiceStudio: Zero to Finished Product**
> Files covered in full this chapter: `db/BillDao.java`, `db/PurchaseBillDao.java`,
> `db/TransactionDao.java`, `db/ExpenseDao.java`, `db/ExpenseAccountDao.java`,
> `db/StockLedgerDao.java`, `db/LabelPrintHistoryDao.java`, `db/VariableDao.java`,
> `db/TemplateDao.java`, `db/AuthDao.java` — all ten read from the repository.
> Goal at the end: you understand how the app stores *documents* (invoices,
> purchases, expenses) and *ledgers* (stock movements, print history) — the
> data that grows forever and must never be corrupted.

---

## 1. Chapter goal

Chapter 4's master data changes slowly; this chapter's data is the *living*
record of a business — thousands of invoices, purchases, expenses, stock
movements. By the end you will be able to explain:

- **Write-time denormalisation:** how `saveBill` *derives* the list-screen
  columns (`grand_total`, `due_amount`, `buyer_name`) from the object's
  richer structure at the moment of saving, so reads never recompute;
- **Soft delete:** the `deleted / deleted_reason / deleted_at` triple from
  Chapter 3, now seen working in `TransactionDao`;
- **The stock ledger equation** — `opening + Σqty_in − Σqty_out` — and how
  the app keeps its cached `current_stock` honest;
- **SQL-level immutability:** how `VariableDao`'s upsert makes the 16
  built-in variables *unoverwritable by database rule*, not by convention;
- **A never-throws DAO:** why `LabelPrintHistoryDao.insert` refuses to let
  an audit failure block a print run.

---

## 2. Story intro

Chapter 4's librarians guard the reference shelf. This chapter staffs the
*records room* — and the records room has different rules, because the
paper here is different in kind:

- A **sales invoice** is a *document*. It has a body full of detail (line
  items, payment splits, terms, custom fields), but the office needs a
  **register** at the front desk: number, date, party, total, due — the
  five facts you scan when a customer calls. The app writes both at once:
  the full document (JSON) *and* the register line (columns), derived from
  the same object, at the same instant, so they can never disagree.
- **Stock movements are facts**, not opinions. Tally — the accounting
  software every Indian shop owner knows — taught the trade its golden
  rule: *never edit stock directly; record vouchers and let the balance be
  arithmetic*. `StockLedgerDao` is that rule as code: you can only append
  movements or remove a voucher's movements wholesale; the "current stock"
  number is always *computed*, never typed in.
- **The cash/bank book can't lose entries.** Accounting rows get
  *archived* (marked deleted, with reason and timestamp) rather than
  shredded — an auditor can always ask "what happened to entry #42?".
- **Audit trails must be cheap.** "Which labels did we print for that
  order?" is a nice-to-have answer; it must never cost a print job its
  success. Hence a DAO that cannot throw.

Ten files, one theme: **these rows are history. Write them so that history
stays true.**

---

## 3. Concepts first

### 3.1 Projection — the register line derived from the document

`BillDao.saveBill` computes `grand`, `paid`, `due`, and `buyer` from the
bill object right before saving:

```java
double grand = bill.getTotals().getGrandTotal();
double paid  = bill.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
double due   = Math.max(0, grand - paid);
String buyer = bill.getVariables().getOrDefault("buyer_name", "");
```

The bill *object* is the single source of truth; the columns are a
**projection** written at save time. The alternative — computing `due` in
SQL over a payments child-table — would need a join per row of every
history screen and a second table to keep consistent. The projection costs
one computation per *save* (rare) and makes every *read* (frequent) a
plain indexed column scan. When you hear "denormalisation", think
"pre-computed answer stored next to the question".

### 3.2 Java streams in one line: `mapToDouble(...).sum()`

`bill.getPayments().stream().mapToDouble(BillPayment::getAmount).sum()`
reads: turn the payments list into a *stream* (a conveyor belt), map each
element to its `amount` (the `BillPayment::getAmount` *method reference*
is shorthand for `p -> p.getAmount()`), and sum the result. Null-guarded
by the ternary around it because a brand-new bill has no payments yet.

### 3.3 Soft delete vs hard delete

| | Hard delete (`DELETE FROM`) | Soft delete (`UPDATE … SET deleted = 1`) |
|---|---|---|
| Row still exists? | no | yes, flagged |
| Recoverable? | no | yes (flip the flag) |
| Audit trail? | gone | reason + timestamp kept |
| Read queries | plain | must filter `deleted = 0` |
| Used for | buyers, suppliers, items, templates, expenses | **transactions** (the money book) |

Neither is "correct" — they serve different trusts. Master records can be
recreated; money entries are evidence.

### 3.4 Safe dynamic SQL — building a WHERE clause from optional filters

When a screen offers optional filters ("show the bank book", "show only
money-out"), the SQL can't be one fixed string. The *wrong* way is string
concatenation of user values (the SQL-injection door). The right way —
used by `TransactionDao.getTransactions` — is to build the **clause
structure** dynamically but bind every **value** as a parameter, tracked in
a parallel list so each `?` gets its value in order.

### 3.5 Aggregate queries: `SUM`, `COALESCE`, `LEFT JOIN`

- `SUM(qty_in)` — SQLite adds up the column across matching rows.
- `COALESCE(x, 0)` — "if x is NULL (no rows matched), use 0"; without it,
  one missing sum can NULL-poison a whole arithmetic expression.
- `LEFT JOIN` — keep every left-table row even when the right side has no
  match (an item with *no* movements still appears, with NULL sums that
  `COALESCE` turns into 0).

### 3.6 The three validation layers, revisited

Chapter 3 gave categories a UNIQUE index; Chapter 4 gave items and
categories *code* guards. This chapter completes the picture with guards
inside **SQL** itself (`VariableDao`'s `WHERE variables.builtin = 0` on the
upsert). Three layers, strongest last: UI convention → DAO code → database
rule.

---

## 4. Files in this chapter

| File | Type | Style | Lines | Distinctive move |
|---|---|---|---|---|
| `db/BillDao.java` | DAO | JSON blob | 157 | derived register columns; print counter |
| `db/PurchaseBillDao.java` | DAO | JSON blob | 130 | BillDao mirror; ITC derivation |
| `db/TransactionDao.java` | DAO | plain columns | 219 | dynamic WHERE builder; soft delete |
| `db/ExpenseDao.java` | DAO | JSON blob | 113 | compactest document DAO |
| `db/ExpenseAccountDao.java` | DAO | JSON blob | 136 | archived-sink ordering; NOCASE |
| `db/StockLedgerDao.java` | Ledger | plain columns | 189 | append-only movements; balances join |
| `db/LabelPrintHistoryDao.java` | Ledger | plain columns | 127 | never-throws audit; `LIMIT 1000` |
| `db/VariableDao.java` | DAO | plain columns | 185 | scope queries; SQL-level builtin guard |
| `db/TemplateDao.java` | DAO | JSON blob | 110 | the minimal template of the family |
| `db/AuthDao.java` | State | plain columns | 99 | single-row session; delete-then-insert |

---

## 5. Step-by-step build

### Step 1 — `db/BillDao.java` (complete file)

```java
package com.invoicestudio.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.Bill;
import com.invoicestudio.model.BillPayment;
import com.invoicestudio.model.BillStatus;
import com.invoicestudio.model.DocType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BillDao {
    private final DatabaseManager db;
    private final ObjectMapper mapper = new ObjectMapper();

    public BillDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }
```

Same skeleton as every DAO (Chapter 4 §5 Step 1): injected `DatabaseManager`,
private Jackson mapper, user gate.

```java
    public List<Bill> getAllBills() {
        List<Bill> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) {
            return list;
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM bills WHERE user_id = ? ORDER BY date DESC, bill_no DESC")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String json = rs.getString("json_data");
                    if (json != null) {
                        list.add(mapper.readValue(json, Bill.class));
                    }
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return list;
    }
```

- **`ORDER BY date DESC, bill_no DESC`** — the history screen's order:
  newest date first, and within one date, highest bill number first. Two
  keys because a shop can raise several invoices on the same date and the
  natural reading order is *descending number*. Both sort columns are
  stored text in ISO/zero-padded-ish form; date `DESC` on ISO strings sorts
  correctly (Chapter 3's timestamp insight).

```java
    public Bill getBillById(String id) { ... WHERE id = ? AND user_id = ? ... }
    public Bill getBillByNo(String billNo) { ... WHERE bill_no = ? AND user_id = ? ... }
```

*(both complete in the file — the identity lookup and its sibling)* —
`getBillByNo` is the one **human-facing** lookup: the clerk has "INV-0042"
on a paper copy and needs the electronic twin. It uses the
`idx_bills_bill_no` index from Chapter 3. Note it takes the *first* match
if numbers ever repeat (the accepted counter-reuse risk from Chapter 4's
`getNextBillNumberAndIncrement` discussion).

```java
    public void saveBill(Bill bill) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || bill == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO bills (id, user_id, bill_no, date, doc_type, status, buyer_name, grand_total, due_amount, json_data, created_at, updated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, bill_no = excluded.bill_no, date = excluded.date, " +
                 "doc_type = excluded.doc_type, status = excluded.status, buyer_name = excluded.buyer_name, " +
                 "grand_total = excluded.grand_total, due_amount = excluded.due_amount, json_data = excluded.json_data, " +
                 "updated_at = excluded.updated_at WHERE bills.user_id = excluded.user_id")) {
            String now = Instant.now().toString();
            if (bill.getCreatedAt() == null) bill.setCreatedAt(now);
            bill.setUpdatedAt(now);

            double grand = bill.getTotals() != null ? bill.getTotals().getGrandTotal() : 0;
            double paid = bill.getPayments() != null ? bill.getPayments().stream().mapToDouble(BillPayment::getAmount).sum() : 0;
            if (paid == 0 && bill.getStatus() == BillStatus.PAID) paid = grand;
            double due = Math.max(0, grand - paid);
            if (bill.getStatus() == BillStatus.CANCELLED) due = 0;

            String buyer = bill.getVariables() != null ? bill.getVariables().getOrDefault("buyer_name", "") : "";

            ps.setString(1, bill.getId());
            ps.setString(2, uid);
            ps.setString(3, bill.getBillNo());
            ps.setString(4, bill.getDate());
            ps.setString(5, bill.getDocType().getCode());
            ps.setString(6, bill.getStatus().getCode());
            ps.setString(7, buyer);
            ps.setDouble(8, grand);
            ps.setDouble(9, due);
            ps.setString(10, mapper.writeValueAsString(bill));
            ps.setString(11, bill.getCreatedAt());
            ps.setString(12, bill.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }
```

- The **projection block** (§2's story) has two business rules baked in:
  - `if (paid == 0 && status == PAID) paid = grand;` — a bill marked PAID
    without recorded payments (paid cash, nobody logged a split) shows due
    0, not grand. The register must never contradict the status the user
    set.
  - `if (status == CANCELLED) due = 0;` — a cancelled invoice can't chase
    money. `Math.max(0, …)` is the floor: overpaid bills (rounding, advance
    adjustments) show due 0, never negative-red.
- `buyer_name` comes from the bill's `variables` map — the same merge-field
  map the template renderer uses (Chapter 16). The register's party column
  and the printed document therefore share one source: rename the buyer
  while editing, save, and history + printout agree.
- `getDocType().getCode()` / `getStatus().getCode()` — enums stored as
  short stable codes, not `name()`; codes survive enum refactors (an enum
  constant renamed in Java would otherwise orphan old rows).
- The guarded upsert: identical tenant-guard shape to every Chapter 4 save.

```java
    public void deleteBill(String id) { ... hard DELETE, user-scoped ... }

    public void incrementPrintCount(String id) {
        Bill b = getBillById(id);
        if (b != null) {
            b.setPrintCount(b.getPrintCount() + 1);
            saveBill(b);
        }
    }
```

- **`incrementPrintCount`** is the honest-counter pattern: read the full
  document, bump an in-object counter, save the whole thing. Called by the
  history screen every time an invoice is printed ("printed ×3" badge).
  The naive alternative (`UPDATE … SET print_count = print_count + 1` in
  SQL) is impossible here — the counter lives *inside the JSON blob*, so
  the read-modify-write is the only route. The known cost: two simultaneous
  prints could read the same count and both write count+1 (losing one
  increment) — accepted, because prints from one desk are serial anyway,
  and a miscount on an info badge is harmless.

```java
    // --- Aliases for uniform API across views and services ---
    public List<Bill> findAll() { return getAllBills(); }
    public Bill findById(String id) { return getBillById(id); }
    public void save(Bill bill) { saveBill(bill); }
    public void insert(Bill bill) { saveBill(bill); }
    public void update(Bill bill) { saveBill(bill); }
    public void delete(String id) { deleteBill(id); }
}
```

### Step 2 — `db/PurchaseBillDao.java` (complete file)

```java
package com.invoicestudio.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.PurchaseBill;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence for purchase bills (supplier inward supply register).
 * Mirrors {@link BillDao}: scalar columns for fast listing + full JSON payload.
 */
public class PurchaseBillDao {
    private final DatabaseManager db;
    private final ObjectMapper mapper = new ObjectMapper();

    public PurchaseBillDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }

    public List<PurchaseBill> getAllPurchaseBills() {
        List<PurchaseBill> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) {
            return list;
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "SELECT json_data FROM purchase_bills WHERE user_id = ? ORDER BY date DESC, bill_no DESC")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String json = rs.getString("json_data");
                    if (json != null) {
                        list.add(mapper.readValue(json, PurchaseBill.class));
                    }
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return list;
    }

    public PurchaseBill getPurchaseBillById(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM purchase_bills WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper.readValue(rs.getString("json_data"), PurchaseBill.class);
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return null;
    }

    public void savePurchaseBill(PurchaseBill bill) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || bill == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO purchase_bills (id, user_id, bill_no, supplier_bill_no, date, supplier_id, supplier_name, total, itc, status, json_data, created_at, updated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, bill_no = excluded.bill_no, " +
                 "supplier_bill_no = excluded.supplier_bill_no, date = excluded.date, supplier_id = excluded.supplier_id, " +
                 "supplier_name = excluded.supplier_name, total = excluded.total, itc = excluded.itc, status = excluded.status, " +
                 "json_data = excluded.json_data, updated_at = excluded.updated_at WHERE purchase_bills.user_id = excluded.user_id")) {
            String now = Instant.now().toString();
            if (bill.getCreatedAt() == null) bill.setCreatedAt(now);
            bill.setUpdatedAt(now);

            double grand = bill.getAmountPayable();
            double itc = bill.getTotals() != null
                    ? bill.getTotals().getCgst() + bill.getTotals().getSgst() + bill.getTotals().getIgst()
                    : 0;

            ps.setString(1, bill.getId());
            ps.setString(2, uid);
            ps.setString(3, bill.getBillNo());
            ps.setString(4, bill.getSupplierBillNo());
            ps.setString(5, bill.getDate());
            ps.setString(6, bill.getSupplierId());
            ps.setString(7, bill.getSupplierName());
            ps.setDouble(8, grand);
            ps.setDouble(9, itc);
            ps.setString(10, bill.isPaid() ? "PAID" : "UNPAID");
            ps.setString(11, mapper.writeValueAsString(bill));
            ps.setString(12, bill.getCreatedAt());
            ps.setString(13, bill.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    public void deletePurchaseBill(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM purchase_bills WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    // --- Aliases for uniform API across views and services ---
    public List<PurchaseBill> findAll() { return getAllPurchaseBills(); }
    public PurchaseBill findById(String id) { return getPurchaseBillById(id); }
    public void save(PurchaseBill bill) { savePurchaseBill(bill); }
    public void insert(PurchaseBill bill) { savePurchaseBill(bill); }
    public void update(PurchaseBill bill) { savePurchaseBill(bill); }
    public void delete(String id) { deletePurchaseBill(id); }
}
```

- The class Javadoc says it outright: *mirrors BillDao*. Differences worth
  your eyes:
  - **ITC derivation:** `cgst + sgst + igst` summed into the register's
    `itc` column — the claimable input-tax figure, pre-computed for the
    purchase reports (Chapter 14). Same projection philosophy, different
    arithmetic: purchases *accumulate* tax credit; sales *collect* it.
  - **`status` as plain string** (`bill.isPaid() ? "PAID" : "UNPAID"`) —
    purchases have only two states, so no enum round-trip; contrast
    BillDao's `BillStatus.getCode()`.
  - `supplier_bill_no` bound as a first-class column (the seller's printed
    number — the field actually searched during goods-in matching).
- The mirror is complete and self-contained — the same deliberate
  duplication-over-generics choice Chapter 4 documented.

### Step 3 — `db/TransactionDao.java` (complete file)

```java
package com.invoicestudio.db;

import com.invoicestudio.model.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TransactionDao {
    private final DatabaseManager db;

    public TransactionDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }

    public List<Transaction> getAllTransactions() {
        List<Transaction> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) {
            return list;
        }
        String sql = "SELECT * FROM transactions WHERE deleted = 0 AND user_id = ? ORDER BY transaction_date DESC, created_at DESC";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return list;
    }
```

- **`WHERE deleted = 0`** — the soft-delete filter, on *every* read. This
  is the cost of soft deletion, paid forever: forget it once and archived
  entries resurface in totals. (§9 lists it as the classic mistake.)
- Note this DAO **extracts its row mapper** (`mapRow`, bottom of file) —
  the exact remedy for `ItemDao`'s duplication, already practised here.

```java
    public List<Transaction> getTransactions(String bookType, String transactionType) {
        List<Transaction> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) {
            return list;
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM transactions WHERE deleted = 0 AND user_id = ?");
        List<String> params = new ArrayList<>();
        params.add(uid);
        if (bookType != null && !bookType.equalsIgnoreCase("ALL")) {
            sql.append(" AND UPPER(book_type) = ?");
            params.add(bookType.toUpperCase());
        }
        if (transactionType != null && !transactionType.equalsIgnoreCase("ALL")) {
            sql.append(" AND LOWER(transaction_type) = ?");
            params.add(transactionType.toLowerCase());
        }
        sql.append(" ORDER BY transaction_date DESC, created_at DESC");

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setString(i + 1, params.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return list;
    }
```

- **§3.4's safe dynamic SQL in production form.** The *structure* grows
  (`AND UPPER(book_type) = ?` appended only when the filter is real), but
  every value — including the uid — goes through `params`, and one loop
  binds them positionally in the exact order the `?`s appear. `"ALL"` (or
  null) means "no filter", so the tab strip's ALL tab sends no clause at
  all. `UPPER` on both sides normalises the book type regardless of what
  the UI passed; the `idx_tx_book` / `idx_tx_date` indexes from Chapter 3
  serve the two most common filter+sort shapes.

```java
    public List<Transaction> getTransactionsByBuyer(String buyerId) { ... WHERE buyer_id = ? AND deleted = 0 AND user_id = ? ORDER BY transaction_date ASC, id ASC ... }
    public Transaction getTransactionById(String id) { ... WHERE id = ? AND user_id = ? ... }
```

*(complete in file)* — the buyer-ledger read sorts **ascending**: the
statement view reads like a bank passbook, oldest first (contrast the
register screens' descending recency). Same table, two natural orders,
chosen per consumer.

```java
    public void saveTransaction(Transaction t) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || t == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO transactions (id, user_id, buyer_id, buyer_name, book_type, transaction_type, " +
                 "transaction_date, due_date, amount, total_quantity, check_number, include_in_reporting, " +
                 "parcel, bill_id, bill_no, deleted, deleted_reason, deleted_at, created_at, updated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, buyer_id = excluded.buyer_id, buyer_name = excluded.buyer_name, " +
                 "book_type = excluded.book_type, transaction_type = excluded.transaction_type, " +
                 "transaction_date = excluded.transaction_date, due_date = excluded.due_date, " +
                 "amount = excluded.amount, total_quantity = excluded.total_quantity, " +
                 "check_number = excluded.check_number, include_in_reporting = excluded.include_in_reporting, " +
                 "parcel = excluded.parcel, bill_id = excluded.bill_id, bill_no = excluded.bill_no, " +
                 "deleted = excluded.deleted, deleted_reason = excluded.deleted_reason, " +
                 "deleted_at = excluded.deleted_at, updated_at = excluded.updated_at WHERE transactions.user_id = excluded.user_id")) {
            String now = Instant.now().toString();
            if (t.getCreatedAt() == null || t.getCreatedAt().isBlank()) t.setCreatedAt(now);
            t.setUpdatedAt(now);

            ps.setString(1, t.getId());
            ps.setString(2, uid);
            ps.setString(3, t.getBuyerId());
            ps.setString(4, t.getBuyerName());
            ps.setString(5, t.getBookType());
            ps.setString(6, t.getTransactionType());
            ps.setString(7, t.getTransactionDate());
            ps.setString(8, t.getDueDate());
            ps.setDouble(9, t.getAmount());
            ps.setInt(10, t.getTotalQuantity());
            ps.setString(11, t.getCheckNumber());
            ps.setInt(12, t.isIncludeInReporting() ? 1 : 0);
            ps.setInt(13, t.getParcel());
            ps.setString(14, t.getBillId());
            ps.setString(15, t.getBillNo());
            ps.setInt(16, t.isDeleted() ? 1 : 0);
            ps.setString(17, t.getDeletedReason());
            ps.setString(18, t.getDeletedAt());
            ps.setString(19, t.getCreatedAt());
            ps.setString(20, t.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }
```

- Twenty bound parameters — the widest upsert in the app. Every column
  including the soft-delete triple round-trips through the JSON-free
  mapping; the tenant guard closes it as always. Notice the upsert even
  persists *deletion state* — an archived entry re-saved stays archived.

```java
    public void deleteTransaction(String id, String reason) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "UPDATE transactions SET deleted = 1, deleted_reason = ?, deleted_at = ?, updated_at = ? WHERE id = ? AND user_id = ?")) {
            String now = Instant.now().toString();
            ps.setString(1, reason != null ? reason : "Archived by user");
            ps.setString(2, now);
            ps.setString(3, now);
            ps.setString(4, id);
            ps.setString(5, uid);
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    public void hardDelete(String id) { ... real DELETE, user-scoped, kept for maintenance ... }

    private Transaction mapRow(ResultSet rs) throws Exception {
        Transaction t = new Transaction();
        t.setId(rs.getString("id"));
        t.setBuyerId(rs.getString("buyer_id"));
        t.setBuyerName(rs.getString("buyer_name"));
        t.setBookType(rs.getString("book_type"));
        t.setTransactionType(rs.getString("transaction_type"));
        t.setTransactionDate(rs.getString("transaction_date"));
        t.setDueDate(rs.getString("due_date"));
        t.setAmount(rs.getDouble("amount"));
        t.setTotalQuantity(rs.getInt("total_quantity"));
        t.setCheckNumber(rs.getString("check_number"));
        t.setIncludeInReporting(rs.getInt("include_in_reporting") == 1);
        t.setParcel(rs.getInt("parcel"));
        t.setBillId(rs.getString("bill_id"));
        t.setBillNo(rs.getString("bill_no"));
        t.setDeleted(rs.getInt("deleted") == 1);
        t.setDeletedReason(rs.getString("deleted_reason"));
        t.setDeletedAt(rs.getString("deleted_at"));
        t.setCreatedAt(rs.getString("created_at"));
        t.setUpdatedAt(rs.getString("updated_at"));
        return t;
    }
}
```

- **The soft delete, performed:** it's an `UPDATE` setting the triple —
  flag, reason, timestamp — with a default reason ("Archived by user") so
  the audit field is never empty. Nothing is removed; every list query
  filters it away.
- **`hardDelete` exists deliberately alongside** — for genuine maintenance
  (backup-restore reconciliation, test cleanup), not for UI flows. The
  naming makes intent uncallable-by-accident.
- `mapRow` centralises the 19-column mapping; the boolean columns read as
  `rs.getInt(...) == 1` because SQLite stores booleans as integers.

### Step 4 — `db/ExpenseDao.java` and `db/ExpenseAccountDao.java` (complete files)

```java
package com.invoicestudio.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.Expense;
import com.invoicestudio.service.AppLog;

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
            com.invoicestudio.service.AppLog.error(e);
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
            com.invoicestudio.service.AppLog.error(e);
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
            AppLog.error(ex);
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
            AppLog.error(ex);
        }
    }

    // --- Aliases for uniform API ---
    public List<Expense> findAll() { return getAllExpenses(); }
    public Expense findById(String id) { return getExpenseById(id); }
    public void save(Expense e) { saveExpense(e); }
    public void delete(String id) { deleteExpense(id); }
}
```

- The compactest document DAO in the app — hot columns (date, category,
  amount, payment_mode) chosen for exactly the expense register's sort and
  filter needs; everything else (notes, receipt references) rides in JSON.
  Expenditures are hard-deleted (they're the user's own log, not evidence).

```java
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
```

- **`ORDER BY archived ASC, name COLLATE NOCASE ASC`** — the registry's
  signature ordering: active payees alphabetically first, archived ones
  sunk to the bottom (still visible — old expenses reference them — but
  out of the way). `COLLATE NOCASE` inside the ORDER BY makes the A→Z
  case-insensitive, so "electricity board" sorts beside "Electricity BOARD".
- **Two in-Java patterns, worth comparing with their SQL cousins:**
  `getActiveAccounts` filters in Java (fine: the list is small), and
  `findByName` *loads everything and matches in Java* — a small
  inconsistency with BuyerDao's SQL `LOWER()` version, harmless at
  registry scale and flagged in §8 for uniformity.
- Archive-not-delete is the registry's philosophy: `deleteAccount` exists,
  but the Expenses UI and the MCP tools use `archived` so historical
  expense vouchers keep their payee label resolvable.

### Step 5 — `db/StockLedgerDao.java` (complete file)

```java
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
```

- The Javadoc is the *specification*: the balance equation and the five
  voucher types. Public `String` constants (not an enum) because the
  voucher type is stored as plain text and the constants are referenced
  across service layers that shouldn't need a type dependency; the names
  double as vocabulary for the MCP tools (Chapter 18) that append
  movements.

```java
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
```

- The **only write** is an INSERT — no UPDATE method exists in this class
  at all (check the file: there is none). The `Math.max(0, …)` clamps make
  the sign convention impossible to violate: quantity can't sneak into the
  wrong column. `ADJUSTMENT`'s delta trick (+in or −out) is documented on
  the method — the one case where a human "edits" stock, and even that is
  recorded as a movement with a voucher trail.
- The ledger writes are called from the billing/purchase services *inside*
  their business flows (Chapter 12/13), and from the MCP tool layer — all
  funnelled through this single append.

```java
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
```

- **"Append-only" with a scalpel.** Individual movements are never edited —
  but when a *voucher* is edited or deleted, all its movements are removed
  wholesale (and the edited voucher re-appends fresh ones). The ledger is
  immutable *per generation of each voucher*; the audit story stays intact
  because the voucher itself (bill/purchase) keeps its edit history in the
  document tables. This is the Tally model precisely.

```java
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
```

- **The equation, executed:** read `opening_stock`, sum the ledger, write
  `opening + in − out` into the cache column. One shared connection across
  the three statements — the read-modify-write stays on one file handle.
- The two inner `SQLException` guards repeat Chapter 4's per-field
  defence, here at statement level: a legacy database without
  `opening_stock`/`current_stock` still gets a ledger balance written from
  movements alone (opening 0).
- `COALESCE` earns its keep: an item with zero movements would otherwise
  sum to NULL and poison the balance.

```java
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
```

- **`allBalances()` is the chapter's SQL showpiece.** Read it inside-out:
  1. The *subquery* `l` collapses the whole ledger into per-item sums
     (`GROUP BY item_id`) — thousands of movement rows become one row per
     item, computed entirely inside SQLite.
  2. `LEFT JOIN` attaches those sums to every item — items with no
     movements keep NULL sums, which the `COALESCE`s turn into 0 so the
     balance is just their opening stock.
  3. The equation runs *in the SELECT* — the database returns finished
     balances, `item_id → closing qty`, ready for the stock screens and
     reports (Chapter 14) without any Java-side arithmetic.
  - Both `?` placeholders receive the same uid (subquery and outer filter
    each need it — one tenant scope inside the aggregation, one outside).
- `movementsForItem` returns `List<Map<String,Object>>` rather than a model
  class — the ledger row has no domain object because it is *pure report
  data*; string-keyed maps keep the report layer (which renders columns
  generically) simple. The empty catch on its read is a deliberate
  silence-but-return-empty (history reports must not error the screen);
  §8 notes that logging here would cost nothing and be slightly better.
- `Statement` and `ItemRecord` imports: `ItemRecord` is unused in the
  current file (a leftover from an earlier balance-map-of-objects design) —
  `GAP:` harmless unused import, noted for the optional clean-up.

### Step 6 — `db/LabelPrintHistoryDao.java` (complete file)

```java
package com.invoicestudio.db;

import com.invoicestudio.model.LabelPrintHistory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read/write access to {@code label_print_history} — an info-only audit of
 * every Bulk Label Print run (when, which template, which printer, how many).
 */
public class LabelPrintHistoryDao {
    private final DatabaseManager db;

    public LabelPrintHistoryDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }

    /** Appends one print run. Never throws — history must not block printing. */
    public void insert(LabelPrintHistory h) {
        if (h == null) return;
        String uid = getEffectiveUserId();
        if (uid == null || uid.isEmpty()) uid = "";
        if (h.getId() == null || h.getId().isBlank()) {
            h.setId("lph_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO label_print_history (id, template_id, template_name, printer_name, label_width, label_height, " +
                     "columns, pages, labels, total_copies, summary, lines_json, user_id, created_at) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, h.getId());
            ps.setString(2, h.getTemplateId());
            ps.setString(3, h.getTemplateName());
            ps.setString(4, h.getPrinterName());
            ps.setDouble(5, h.getLabelWidth());
            ps.setDouble(6, h.getLabelHeight());
            ps.setInt(7, h.getColumns());
            ps.setInt(8, h.getPages());
            ps.setInt(9, h.getLabels());
            ps.setInt(10, h.getTotalCopies());
            ps.setString(11, h.getSummary());
            ps.setString(12, h.getLinesJson());
            ps.setString(13, uid);
            ps.setString(14, h.getCreatedAt() != null && !h.getCreatedAt().isBlank()
                    ? h.getCreatedAt() : java.time.Instant.now().toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }
```

- **The never-throws contract, stated in the Javadoc and kept in the
  code:** the single `catch (SQLException)` logs and returns. A print run
  that succeeded must not be reported as failed because its *audit row*
  failed. Note it also tolerates a signed-out state (`uid = ""`) rather
  than refusing — history is worth keeping even then. ID generation
  (`lph_` + 12 hex chars) mirrors `ItemDao`'s pattern.

```java
    public List<LabelPrintHistory> getAll() {
        List<LabelPrintHistory> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid == null || uid.isEmpty()) return list;
        String sql = "SELECT * FROM label_print_history WHERE user_id = ? ORDER BY created_at DESC LIMIT 1000";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(fromResultSet(rs));
            }
        } catch (SQLException e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return list;
    }

    /** Deletes one run. Returns true when a row was removed. */
    public boolean delete(String id) {
        String uid = getEffectiveUserId();
        if (uid == null || uid.isEmpty() || id == null || id.isBlank()) return false;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM label_print_history WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            com.invoicestudio.service.AppLog.error(e);
            return false;
        }
    }

    /** Clears the whole history for the current user. Returns rows removed. */
    public int clearAll() {
        String uid = getEffectiveUserId();
        if (uid == null || uid.isEmpty()) return 0;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM label_print_history WHERE user_id = ?")) {
            ps.setString(1, uid);
            return ps.executeUpdate();
        } catch (SQLException e) {
            com.invoicestudio.service.AppLog.error(e);
            return 0;
        }
    }

    private LabelPrintHistory fromResultSet(ResultSet rs) throws SQLException {
        LabelPrintHistory h = new LabelPrintHistory();
        h.setId(rs.getString("id"));
        h.setTemplateId(rs.getString("template_id"));
        h.setTemplateName(rs.getString("template_name"));
        h.setPrinterName(rs.getString("printer_name"));
        h.setLabelWidth(rs.getDouble("label_width"));
        h.setLabelHeight(rs.getDouble("label_height"));
        h.setColumns(rs.getInt("columns"));
        h.setPages(rs.getInt("pages"));
        h.setLabels(rs.getInt("labels"));
        h.setTotalCopies(rs.getInt("total_copies"));
        h.setSummary(rs.getString("summary"));
        h.setLinesJson(rs.getString("lines_json"));
        h.setUserId(rs.getString("user_id"));
        h.setCreatedAt(rs.getString("created_at"));
        return h;
    }
}
```

- **`LIMIT 1000`** — the audit viewer's seatbelt: history grows with every
  print run forever; the screen shows the newest thousand (plenty for any
  human review) instead of loading years of rows. The full data remains in
  the table; only the *view* is bounded.
- **DAOs that answer:** `delete` returns boolean ("did I remove a row?"),
  `clearAll` returns the count — the UI can say "Cleared 214 print records"
  instead of a generic toast. Compare the void deletes elsewhere: answer
  when the answer is useful to a human.

### Step 7 — `db/VariableDao.java` (complete file)

```java
package com.invoicestudio.db;

import com.invoicestudio.service.AppLog;
import com.invoicestudio.model.VariableDef;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class VariableDao {
    private final DatabaseManager db;

    public VariableDao(DatabaseManager db) {
        this.db = db;
    }

    /** Reads all columns including scope, default_value and choices. */
    private VariableDef fromResultSet(ResultSet rs) throws Exception {
        VariableDef v = new VariableDef(
            rs.getString("key"),
            rs.getString("label"),
            rs.getString("type"),
            rs.getInt("builtin") == 1
        );
        String scope = rs.getString("scope");
        v.setScope(scope != null ? scope : "fixed");
        String defVal = rs.getString("default_value");
        v.setDefaultValue(defVal != null ? defVal : "");
        try {
            String choices = rs.getString("choices");
            v.setChoices(choices != null ? choices : "");
        } catch (Exception ignored) {
            AppLog.debug(ignored);
            // Older schema without the choices column — leave empty.
        }
        return v;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }
```

- The extracted mapper (like `TransactionDao.mapRow`) with the same
  per-field defence for `choices` — the v4.6 column that old databases
  may lack. Note the two null-coalescing defaults (`scope → "fixed"`,
  `default_value → ""`): rows written before those columns existed read
  back with sane values, not nulls leaking into the designer UI.

```java
    public List<VariableDef> getAllVariables() {
        List<VariableDef> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        String sql = uid.isEmpty()
                ? "SELECT * FROM variables WHERE builtin = 1 ORDER BY builtin DESC, label ASC"
                : "SELECT * FROM variables WHERE builtin = 1 OR user_id = ? ORDER BY builtin DESC, label ASC";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (!uid.isEmpty()) {
                ps.setString(1, uid);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(fromResultSet(rs));
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return list;
    }
```

- **The visibility rule, in SQL:** built-ins belong to *everyone*
  (`builtin = 1` alone suffices, no user scope); custom variables belong
  to their owner. Signed out? You still get the 16 built-ins — they are
  system vocabulary, not user data (this is why Chapter 3's purge
  deliberately spared them).
- This is the one DAO with a genuinely different **signed-out behaviour**:
  everywhere else the gate returns empty; here it returns the shared
  catalogue. The branch also sidesteps binding a parameter that isn't in
  the SQL — notice `ps.setString(1, uid)` only in the scoped branch.

```java
    public List<VariableDef> getCustomVariables() { ... WHERE builtin = 0 AND user_id = ? ORDER BY label ASC ... }

    /**
     * Returns only user-created variables with scope = 'table'.
     * Used by TemplateDesigner's column picker to offer custom table columns.
     */
    public List<VariableDef> getTableScopeVariables() { ... AND scope = 'table' ... }

    /**
     * Returns only user-created variables with scope = 'fixed'.
     * Used by CreateBillView to render per-bill custom input fields.
     */
    public List<VariableDef> getFixedScopeVariables() { ... AND (scope = 'fixed' OR scope IS NULL) ... }

    /** Returns only user-created variables with scope = 'barcode' (Bulk Label Print variables). */
    public List<VariableDef> getBarcodeScopeVariables() { ... AND scope = 'barcode' ... }
```

*(all four complete in the file)* — the **scope trilogy**: the same table
feeds three different UIs, each seeing only its slice:
- `scope = 'table'` → the template designer's *extra table column* picker
  (a custom "Batch No" column on the invoice table);
- `scope = 'fixed'` (or NULL — the pre-v4.1 default) → the bill editor's
  per-invoice *fill-in fields* (this invoice's PO number, parcel count);
- `scope = 'barcode'` → the Bulk Label Print dialog's per-barcode variables.
One registry, three features, zero schema coupling between them — the
variable *system* being the app's most quietly extensible corner.

```java
    public void saveVariable(VariableDef v) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || v == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO variables (key, user_id, label, type, builtin, scope, default_value, choices) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON CONFLICT(key) DO UPDATE SET user_id = excluded.user_id, label = excluded.label, type = excluded.type, " +
                     "scope = excluded.scope, default_value = excluded.default_value, choices = excluded.choices WHERE variables.builtin = 0")) {
            ps.setString(1, v.getKey());
            ps.setString(2, uid);
            ps.setString(3, v.getLabel());
            ps.setString(4, v.getType());
            ps.setInt(5, v.isBuiltin() ? 1 : 0);
            ps.setString(6, v.getScope());
            ps.setString(7, v.getDefaultValue());
            ps.setString(8, v.getChoices());
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    public boolean deleteVariable(String key) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || key == null || key.isBlank()) return false;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM variables WHERE key = ? AND builtin = 0 AND user_id = ?")) {
            ps.setString(1, key.trim());
            ps.setString(2, uid);
            int rows = ps.executeUpdate();
            return rows > 0;
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
            return false;
        }
    }

    // --- Aliases for uniform API across views and services ---
    public List<VariableDef> findAll() { return getAllVariables(); }
    public List<VariableDef> findCustom() { return getCustomVariables(); }
    public void save(VariableDef v) { saveVariable(v); }
    public void insert(VariableDef v) { saveVariable(v); }
    public void update(VariableDef v) { saveVariable(v); }
    public void delete(String key) { deleteVariable(key); }
}
```

- **The builtin guard lives in the SQL itself.** Read the upsert's final
  clause: `WHERE variables.builtin = 0`. If someone saves a variable whose
  key collides with a *built-in* (`buyer_name`), the INSERT conflicts on
  the key, the UPDATE's WHERE sees `builtin = 1`, and **zero rows change**.
  The 16 seed variables from Chapter 3 are unoverwritable — not by a code
  check someone could forget, but by the database rule enforced on every
  path, including future ones. (The Chapter 4 protected defaults were
  *code* guards; this is the stronger *schema* guard — the layers from
  §3.6.)
- `deleteVariable` repeats the guard (`AND builtin = 0`) and answers with a
  boolean, so the Variables screen can toast "Built-in variables can't be
  deleted" when it comes back false.
- `key` is the conflict target here — this table's natural unique key *is*
  its business key (the merge-field name), so the upsert aims cleanly,
  where `settings` (Chapter 4) could not.

### Step 8 — `db/TemplateDao.java` and `db/AuthDao.java` (complete files)

```java
package com.invoicestudio.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.Template;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TemplateDao {
    private final DatabaseManager db;
    private final ObjectMapper mapper = new ObjectMapper();

    public TemplateDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }

    public List<Template> getAllTemplates() {
        List<Template> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) {
            return list;
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM templates WHERE user_id = ? ORDER BY name ASC")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String json = rs.getString("json_data");
                    if (json != null) {
                        list.add(mapper.readValue(json, Template.class));
                    }
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return list;
    }

    public Template getTemplateById(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM templates WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper.readValue(rs.getString("json_data"), Template.class);
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return null;
    }

    public void saveTemplate(Template template) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || template == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO templates (id, user_id, name, json_data, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, name = excluded.name, json_data = excluded.json_data, updated_at = excluded.updated_at WHERE templates.user_id = excluded.user_id")) {
            String now = Instant.now().toString();
            if (template.getCreatedAt() == null) template.setCreatedAt(now);
            template.setUpdatedAt(now);

            ps.setString(1, template.getId());
            ps.setString(2, uid);
            ps.setString(3, template.getName());
            ps.setString(4, mapper.writeValueAsString(template));
            ps.setString(5, template.getCreatedAt());
            ps.setString(6, template.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    public void deleteTemplate(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM templates WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    // --- Aliases for uniform API across views and services ---
    public List<Template> findAll() { return getAllTemplates(); }
    public Template findById(String id) { return getTemplateById(id); }
    public void save(Template template) { saveTemplate(template); }
    public void insert(Template template) { saveTemplate(template); }
    public void update(Template template) { saveTemplate(template); }
    public void delete(String id) { deleteTemplate(id); }
}
```

- The **minimal complete document DAO** — worth internalising as the
  family template: name + JSON payload only (a template *is* its design
  document; there is nothing worth extracting as hot columns beyond the
  name). Its callers are everywhere — the designer (Ch 15), the bill
  editor's template picker, the renderer (Ch 16), backup/restore, and the
  MCP template tools — all through these six methods.

```java
package com.invoicestudio.db;

import com.invoicestudio.model.UserSession;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;

public class AuthDao {
    private final DatabaseManager db;

    public AuthDao(DatabaseManager db) {
        this.db = db;
    }

    public void saveSession(UserSession session) {
        if (session == null) return;
        try (Connection conn = db.getConnection()) {
            // Keep single active session record
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM auth_session")) {
                del.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO auth_session (id, user_id, email, display_name, id_token, refresh_token, expires_at, remember_me, created_at) " +
                    "VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, session.getUserId());
                ps.setString(2, session.getEmail());
                ps.setString(3, session.getDisplayName());
                ps.setString(4, session.getIdToken());
                ps.setString(5, session.getRefreshToken());
                ps.setLong(6, session.getExpiresAtMillis());
                ps.setInt(7, session.isRememberMe() ? 1 : 0);
                ps.setString(8, session.getCreatedAt() != null ? session.getCreatedAt() : Instant.now().toString());
                ps.executeUpdate();
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }
```

- **The one deliberately un-scoped DAO.** No `getEffectiveUserId`, no
  `user_id` filtering — the session row *precedes* being signed in; it's
  how the app remembers who was signed in. It stores **identity tokens**
  (Firebase's `id_token`/`refresh_token`, Chapter 10) — machine-local
  secret material in the same SQLite file as the data, which is why the
  app's threat model is "same Windows user or nothing" (the vault posture
  from the earlier chapters applies here too).
- **`DELETE`-then-`INSERT` with hardcoded `id = 1`:** the single-slot
  pattern. An upsert would also work — but the explicit delete-then-insert
  on one connection guarantees *exactly one* row no matter what (a
  corrupted database with two session rows would be cleaned on next save).
  Simple, self-healing, and two statements on one open file cost nothing.

```java
    public UserSession getActiveSession() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT user_id, email, display_name, id_token, refresh_token, expires_at, remember_me, created_at FROM auth_session WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                UserSession session = new UserSession();
                session.setUserId(rs.getString("user_id"));
                session.setEmail(rs.getString("email"));
                session.setDisplayName(rs.getString("display_name"));
                session.setIdToken(rs.getString("id_token"));
                session.setRefreshToken(rs.getString("refresh_token"));
                session.setExpiresAtMillis(rs.getLong("expires_at"));
                session.setRememberMe(rs.getInt("remember_me") == 1);
                session.setCreatedAt(rs.getString("created_at"));
                return session;
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return null;
    }
```

- **Reads the slot:** `WHERE id = 1`, map or return `null` ("nobody was
  here"). This is the first query the app runs at startup (Chapter 10's
  auto-login flow calls it before any window shows) — the DAO without a
  login gate, because it *creates* the login.

```java
    public void updateTokens(String userId, String idToken, String refreshToken, long expiresAtMillis) {
        ... UPDATE auth_session SET id_token = ?, refresh_token = ?, expires_at = ? WHERE user_id = ? ...
    }

    public void updateTokens(String idToken, String refreshToken, long expiresAtMillis) {
        ... UPDATE auth_session SET id_token = ?, refresh_token = ?, expires_at = ? WHERE id = 1 ...
    }

    public void clearSession() {
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM auth_session")) {
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }
}
```

- **Token refresh path:** Firebase tokens expire hourly; the session
  manager silently exchanges them and persists the new pair — two
  overloads because two callers exist (one knows the userId, one only
  knows "the active slot"). `clearSession` (logout) empties the slot —
  tokens deleted, the data tables untouched (they're partitioned *by*
  `user_id`, which lives in the in-memory session, not here).

---

## 6. How it works at runtime

**Invoice save → history screen, the full projection story:**

```
 CreateBillView: Save invoice INV-0043 (FX thread)
   ▼ AppExecutors.io()
 DataManager.saveBill(bill)
   ├── BillingService first: assigns bill no (Ch 4's dispenser),
   │   appends SALE movements to StockLedgerDao, updates items.current_stock
   ├── BillDao.saveBill(bill)
   │     ├── derive: grand=1180  paid=1180  due=0   buyer="Solaris Enterprises"
   │     ├── INSERT … ON CONFLICT(id) DO UPDATE … WHERE bills.user_id = excluded.user_id
   │     └── JSON blob = the full document (items, payments, terms, variables)
   ▼
 HistoryView (later): SELECT json_data ORDER BY date DESC, bill_no DESC
   └── register line comes from the *columns*; opening the row parses the JSON
```

**Stock truth vs stock cache, over one purchase:**

```
 CreatePurchaseView: receive 50 units of PENT @ 240
   ▼
 PurchaseService: PurchaseBillDao.save(purchase)      (document + register)
   │               StockLedgerDao.append(item, date, "PURCHASE", voucherId, "PB-001", in:50, out:0, 240)
   │               StockLedgerDao.recomputeItem(item)   → current_stock = opening + 50 − 0
   ▼
 ItemsView shows the cache; Stock Analysis report calls allBalances()
   └── SELECT … LEFT JOIN (…SUM…GROUP BY…) — truth recomputed from movements
```

**Logout leaves history intact:** `AuthDao.clearSession()` empties the
slot; the signed-in `user_id` leaves memory; every DAO's `uid.isEmpty()`
gate now returns empty views — the data is still in its partitions,
awaiting the next login.

---

## 7. How to change it

**Add a column to the bill register** (a new hot column, e.g.
`salesperson TEXT DEFAULT ''`):
1. Chapter 3's migration line in `initSchema()`.
2. `BillDao.saveBill`: add to the INSERT lists, the SET list, bind it —
   and *derive it* in the projection block (e.g. from
   `bill.getVariables().get("salesperson")`), because register columns
   here are projections, never independently saved.
3. Consumers: HistoryView's filter, `McpProjections` if tools should
   expose it.
Verify: save an invoice with the field, restart, check history still
lists it; run `mvn test -Dtest=DatabaseTest`. What breaks if missed: the
mapper/SET omission = column never populates; missing derivation = column
populates only when the caller remembers to set it — the projection block
is the *single* place that must know.

**Add a voucher type to the stock ledger** (e.g. `MANUFACTURE_IN`): add
the constant next to the five existing ones, decide in/out, append from
the new service flow. No schema change — `voucher_type` is free text by
design (reports group by it). Verify with `movementsForItem` + a
`recomputeItem` balance check.

**Convert a hard delete to soft** (e.g. expenses): add the three columns
(Chapter 3 migration), change `deleteExpense` to the `UPDATE … SET
deleted = 1 …` shape, add `AND deleted = 0` to **every** read in the DAO —
grep the class for `SELECT` to be sure. Miss one read and archived rows
leak into totals.

---

## 8. Performance & UX analysis

| Decision | What was done | Cost | Better alternative | Trade-off / difficulty | User-visible effect |
|---|---|---|---|---|---|
| Write-time projection (register columns) | derive totals/buyer at save | recompute on every re-save | SQL joins over child tables | joins per row per screen — worse; **Easy** current form | history screen loads instantly, totals never disagree with the document |
| Soft delete for transactions | flag + reason + time | every read carries `deleted = 0` forever | archive table | **Medium**; flag keeps rows joinable | deletions are recoverable; audit questions answerable |
| Ledger + cache column | truth in movements, `current_stock` cached | two writes per movement | cache-free (always SUM) | **Medium**; SUM over a huge ledger per list render would crawl | items screen instant; stock report exact |
| `allBalances()` aggregation in SQL | one grouped subquery | ledger scanned per call | maintain a running summary table | **Hard**; summary can drift — the scan is honest | stock report stays correct by construction |
| `LIMIT 1000` on print history | bounded audit view | older rows unlisted | pagination | **Medium**; pagination is UI work | history screen never slows down, even after years |
| `mapRow` extraction (Transactions/Variables/Labels) | one mapper per DAO | — | (ItemDao still duplicates — Ch 4 §8) | **Easy** to align | single edit point per schema change |
| `findByName` in Java (ExpenseAccountDao) | loads list, matches in loop | full list per check | SQL `LOWER()` like BuyerDao | **Easy**; cosmetic consistency | none at registry scale |
| Silent catch in `movementsForItem` | empty list on error | failure invisible | `AppLog.error` in catch | **Easy**, one line | none; diagnosability gain |

`OPTIONAL IMPROVEMENT` — the balance cache, made bulletproof:

```java
// OPTIONAL IMPROVEMENT: wrap movement-append + recompute in ONE transaction (Medium)
try (Connection conn = db.getConnection()) {
    conn.setAutoCommit(false);
    try (PreparedStatement ps = conn.prepareStatement("INSERT INTO stock_ledger …")) {
        /* bind + execute */ ;
    }
    try (PreparedStatement up = conn.prepareStatement("UPDATE items SET current_stock = current_stock + ? WHERE id = ? AND user_id = ?")) {
        up.setDouble(1, delta); /* … */ up.executeUpdate();
    }
    conn.commit();
} catch (SQLException ex) { AppLog.error(ex); /* rollback happens on close */ }
```

*Why better:* today a crash *between* the append and the recompute leaves
cache and ledger briefly disagreeing (next `recomputeItem` heals it, but a
report in that window could mislead). One transaction makes the pair
atomic. *Difficulty:* Medium — touches every caller of `append`.
*User-visible:* removes a rare "stock looks wrong for a second" class of
bug.

---

## 9. Common mistakes and fixes

| Symptom | Cause | Fix |
|---|---|---|
| Archived transaction reappears in totals | a read query missing `AND deleted = 0` | audit every SELECT in `TransactionDao` after any change |
| Stock shows wrong number after editing a purchase | voucher movements not re-appended after edit | call `deleteByVoucher` then re-append, then `recomputeItem` (the services do; custom code must too) |
| `getCurrentStock` NULL-poisoned balance in a report | missing `COALESCE` around a SUM | copy `allBalances()`'s shape exactly |
| Built-in variable got overwritten | someone removed the `WHERE variables.builtin = 0` guard from the upsert | restore it — the guard is the schema-level protection |
| Print succeeded but no history row | someone made `LabelPrintHistoryDao.insert` throw or moved it before the print | keep it last and never-throwing |
| Two auth rows in `auth_session` | hand-edited DB | harmless — next `saveSession`'s delete-then-insert self-heals |
| Bill register shows old buyer name after rename | expected: register is a projection frozen at save time | by design (documents preserve history); history rows are snapshots |

---

## 10. Checkpoint

- [ ] `mvn test -Dtest=DatabaseTest` green (BillDao's contract is
      `testBillCrud`).
- [ ] You can write the balance equation and name the five voucher types
      without looking.
- [ ] You can explain to a colleague why `bills.buyer_name` keeps the name
      from *save time* and why that is correct for invoices.
- [ ] You can point at the exact SQL clause that makes built-in variables
      unoverwritable, and the one that makes cross-account bill saves
      impossible.

**Exercises**

1. Add `salesperson` end-to-end per §7 and prove the projection rule: save
   a bill without touching `saveBill` — the column stays empty; add the
   derivation — it populates.
2. In a test DB: append 3 movements for one item (purchase 50, sale 20,
   adjustment −5 via qtyOut), run `recomputeItem`, assert
   `current_stock == opening + 25`.
3. Soft-delete a transaction, then write the *wrong* query (omit
   `deleted = 0`) and watch it reappear — feel the §9 mistake once, safely.

---

## 11. Summary and coverage self-check

You built the records room: two document DAOs with write-time projections
(Bill, PurchaseBill), the money book with safe dynamic SQL and soft
deletes (TransactionDao), two registries with archive-aware ordering
(Expense, ExpenseAccount), the immutable stock ledger with SQL-side
balance aggregation (StockLedgerDao), the never-throwing audit trail
(LabelPrintHistoryDao), the scope-partitioned variable system with
schema-level builtin protection (VariableDao), the minimal document
template (TemplateDao), and the single-slot un-scoped session store
(AuthDao).

**Files covered in full this chapter (10):**
- `db/BillDao.java` ✅ (157/157; `getBillById`/`getBillByNo` shown condensed
  with SQL quoted — both are the Chapter 4 identity pattern over bills)
- `db/PurchaseBillDao.java` ✅ (130/130, complete listing)
- `db/TransactionDao.java` ✅ (219/219; two standard list/get methods shown
  condensed with SQL quoted, mapper and all distinctive methods complete)
- `db/ExpenseDao.java` ✅ (113/113, complete listing)
- `db/ExpenseAccountDao.java` ✅ (136/136, complete listing)
- `db/StockLedgerDao.java` ✅ (189/189, complete listing)
- `db/LabelPrintHistoryDao.java` ✅ (127/127, complete listing)
- `db/VariableDao.java` ✅ (185/185; the four scope getters shown condensed
  with SQL quoted — they are one shape; mapper/save/delete complete)
- `db/TemplateDao.java` ✅ (110/110, complete listing)
- `db/AuthDao.java` ✅ (99/99; the two token-update overloads shown
  condensed with SQL quoted)

**Markers raised:** 1 `GAP:` (unused `ItemRecord` import in
`StockLedgerDao` — leftover; silent catch in `movementsForItem` noted as a
one-line diagnosability improvement).

**Next: Chapter 6 — "The Language of the Business: Domain Models, Part 1"**
(`Bill`, `BillItem`, `BillPayment`, `BillStatus`, `BillTotals`,
`BusinessProfile`, `Settings`, `Buyer`, `BuyerFieldDef`, `Supplier`,
`ItemRecord`, `ItemCategory`, `Transport`, `Expense`, `ExpenseAccount`,
`PurchaseBill`, `Transaction`, `PaymentMethod`, `RepeatCadence` — the
objects every DAO just learned to store).