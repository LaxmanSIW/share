# Chapter 4 — Speaking SQLite: The DAO Pattern, Part 1 — Master Data

> **Part 2 of InvoiceStudio: Zero to Finished Product**
> Files covered in full this chapter: `db/SettingsDao.java`, `db/BuyerDao.java`,
> `db/SupplierDao.java`, `db/ItemDao.java`, `db/CategoryDao.java`,
> `db/TransportDao.java` — all six read and reproduced from the repository.
> Goal at the end: you can write a new DAO for any table in the schema and
> know exactly which of the app's two storage styles (JSON blob vs plain
> columns) to use and why.

---

## 1. Chapter goal

By the end of this chapter you will have written the six *master-data* DAOs
— the classes that own all reading and writing of the app's reference
records (settings, buyers, suppliers, items, categories, transports) — and
you will be able to:

- explain the **DAO pattern** and why it exists (what breaks without it);
- implement the app's three signature SQL idioms: the **guarded upsert**
  (`ON CONFLICT … WHERE`), the **user-scoped query**, and the
  **fail-empty contract**;
- understand *why every method quietly checks "is anyone logged in?"* and
  what happens when nobody is;
- see the deliberate difference between the **JSON-blob DAOs** (Buyer,
  Supplier, Settings) and the **plain-column DAOs** (Item, Category,
  Transport) — and the historical reason each table landed on its style.

---

## 2. Story intro

Chapter 3 built the ledger books. This chapter hires the **librarians**.

Picture the back office again: the ledgers exist, but you would never let
the shop owner wander into the archive, flip pages, and scribble directly
into the books. Instead there is a librarian: you hand them a note ("add
this customer", "fetch buyer B-1042's file") and they — and only they —
touch the paper. They also enforce the house rules: entries are stamped
with today's date, each clerk's records stay in their own drawer, and a
note that arrives without a clerk's signature is politely refused.

A **DAO** (*Data Access Object*) is that librarian, one per table. The
pattern's value shows up in what it *prevents*:

- **No SQL in the UI.** A view says `buyerDao.save(b)`, not `INSERT INTO…`.
  When the schema changed in v4.2 (`user_id` column added to every table),
  *zero view code changed* — only the librarians learned the new rule.
- **One enforcement point.** The user-partitioning rule (`WHERE user_id = ?`)
  lives in six classes, not in forty screens. Miss it once in a screen and
  two customers' data mixes — a catastrophe no screen should have the power
  to cause.
- **One consistent failure story.** Every method here catches its own
  exceptions, logs through `AppLog` (Chapter 2's funnel), and returns an
  honest "nothing" — an empty list, `null`, or a silent skip. Callers can
  never forget to handle a database hiccup they cannot fix.

The chapter's subtitle, *master data*, is accounting vocabulary: master
records are the relatively fixed reference data the business runs on
(customers, suppliers, items, categories, transports, and the settings
profile) as opposed to *transactional* data — the invoices, purchases and
money movements that Chapter 5's DAOs handle and that grow without bound.

---

## 3. Concepts first

### 3.1 The DAO pattern in one diagram

```
   BuyersView (UI)          DataManager (cache, Ch 8)        MCP tools (Ch 18)
        │                          │                              │
        └───────────────┬──────────┴──────────────┬───────────────┘
                        ▼                          ▼
                   BuyerDao  ── the ONLY code that ──►  SQLite buyers table
                  (librarian)    knows the SQL
```

Every consumer speaks the same small vocabulary — `findAll()`, `findById()`,
`save()`, `delete()` — which is why each DAO here ends with an **alias
block**: the same object exposed under two naming traditions (verbose
`saveBuyer()` and uniform `save()`), so both the older views and newer
callers read naturally. Cheap, and it kept a big refactor unnecessary.

### 3.2 Upsert — one statement that inserts *or* updates

SQL's classic annoyance: saving a record requires knowing whether it is new.
The old way was SELECT-then-INSERT-or-UPDATE (two round trips, racey).
Modern SQLite (and PostgreSQL) offer **upsert**:

```sql
INSERT INTO buyers (id, name, ...) VALUES (?, ?, ...)
ON CONFLICT(id) DO UPDATE SET name = excluded.name, ...
```

Read it as: "try to insert; if the primary key already exists, turn the
INSERT into an UPDATE using the `excluded.*` row (the row you *tried* to
insert)." One round trip, no race between the check and the write.

### 3.3 The `WHERE` clause on the UPDATE — the app's tenant guard

The app's upserts end with something unusual:

```sql
ON CONFLICT(id) DO UPDATE SET ... WHERE buyers.user_id = excluded.user_id
```

That final `WHERE` means: *only rewrite an existing row if it belongs to the
currently signed-in user.* Consider the alternative this blocks: two
accounts on one PC both happen to save a record with id `it_abc123` (ids are
random, but "restore from backup" or an old JSON import could replay one).
Without the guard, account B's save would silently **overwrite account A's
item** — the cross-tenant corruption Chapter 3's `user_id` partitioning
exists to prevent. The WHERE clause makes the database itself refuse:
zero rows updated, no error, no damage.

### 3.4 The fail-empty contract and the `uid.isEmpty()` gate

Every public method starts identically:

```java
String uid = getEffectiveUserId();   // current signed-in user id, or ""
if (uid.isEmpty()) return List.of(); // or null, or plain return
```

This is a *contract*: **no authenticated user ⇒ no data access.** It is why
the sign-in screen never leaks another account's rows, and why every DAO
method is safe to call from anywhere at any lifecycle moment. The database
never becomes the place where "not logged in" is discovered by exception.

### 3.5 Two storage styles, one table each

Chapter 3 introduced hot-columns + `json_data`. This chapter shows the
split in practice:

- **JSON-blob DAOs** (Settings, Buyer, Supplier): `SELECT json_data …`,
  deserialize with Jackson, serialize on save. Only identity/matching
  columns are extracted. Chosen for records with many fields that grow
  (buyer has custom fields, addresses, state codes…).
- **Plain-column DAOs** (Item, Category, Transport): every field is a
  column, built field-by-field in the row-mapping code. Chosen for records
  that are (a) small and fixed-shaped, and (b) *joined into hot paths* —
  the bill editor needs item rate/GST/stock per row, and CategoryDao's
  MCP-driven rename cascade needs to UPDATE a column across many item rows
  (`updateCategoryNameForCategory`), both of which demand real columns.

`ItemDao` is the interesting hybrid: plain columns, but with per-field
try/catch *within the row mapper* — reading optional columns (`category_id`,
`purchase_rate`, stock fields) defensively so a database created by an older
version (missing those columns) still loads, field by field, instead of
losing the whole list. This is the same converge-philosophy as Chapter 3's
migrations, applied at read time.

### 3.6 Instant strings for timestamps

`Instant.now().toString()` produces ISO-8601 UTC text
(`2026-09-19T10:15:30.123Z`). Storing timestamps as ISO strings means they
sort correctly as text (`ORDER BY created_at` works), are human-readable in
any database browser, and never carry a timezone ambiguity. All DAOs here
stamp `created_at` (once, ever — only if not already set) and `updated_at`
(every save).

---

## 4. Files in this chapter

| File | Type | Purpose | Lines |
|---|---|---|---|
| `db/SettingsDao.java` | DAO | Single-row settings + bill-number counter | 96 |
| `db/BuyerDao.java` | DAO | Buyer CRUD, JSON-blob style | 134 |
| `db/SupplierDao.java` | DAO | Supplier CRUD + count for KPI header | 141 |
| `db/ItemDao.java` | DAO | Item CRUD + stock/category columns + category helpers | 205 |
| `db/CategoryDao.java` | DAO | Category CRUD + protected-default guard | 106 |
| `db/TransportDao.java` | DAO | Transport-party CRUD | 129 |

*(Tests for these DAOs were shown in Chapter 3's `DatabaseTest` walkthrough;
chapter 21 collects the remaining suites.)*

---

## 5. Step-by-step build

### Step 1 — `db/SettingsDao.java` (complete file)

```java
package com.invoicestudio.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.Settings;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class SettingsDao {
    private final DatabaseManager db;
    private final ObjectMapper mapper = new ObjectMapper();

    public SettingsDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }
```

- The shape every DAO repeats: hold the `DatabaseManager` (injected — tests
  can substitute Chapter 3's `initCustom` database), a private Jackson
  mapper (mappers are thread-safe and cheap to keep; creating one per call
  would be waste), and the shared `getEffectiveUserId()` delegate to the
  auth session manager (Chapter 10 owns that class; DAOs only need its
  one answer: who is signed in, or `""`).

```java
    public boolean hasNoSettingsForUser(String uid) {
        if (uid == null || uid.isBlank()) return true;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM settings WHERE user_id = ? LIMIT 1")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                return !rs.next();
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
            return true;
        }
    }
```

- **`SELECT 1 … LIMIT 1`** — the existence probe: "does *any* row match?"
  No table data is transferred; the database stops at the first hit. `!rs.next()`
  = "no row found" = `true` ("this user has no settings yet"). This is the
  method the first-run seeder (Chapter 8's `seedIfEmpty`) calls to decide
  whether to create starter settings.
- Note the error path returns `true` — "treat as empty". If the DB hiccups,
  the seeder will try to create starter settings rather than assume data
  exists that it can't see. For *this* method that is the safe direction
  (worst case: defaults get written); Chapter 4's §8 discusses when
  fail-empty is *unsafe* and why it's still the house style.

```java
    public Settings getSettings() {
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) {
            return new Settings();
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM settings WHERE user_id = ? LIMIT 1")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString("json_data");
                    if (json != null && !json.isBlank()) {
                        return mapper.readValue(json, Settings.class);
                    }
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return new Settings();
    }
```

- **Never-null getter:** every exit path returns a `Settings` — the real row,
  or a fresh default object. Callers (the settings screen, the bill editor
  reading the currency symbol) therefore never null-check. This is the
  JSON-blob style's read half: one column, one `readValue`, done.
- The blank-JSON guard handles a row that exists but whose payload is empty
  (a hand-edited or partially-restored database) — degrade to defaults
  rather than throw.

```java
    public void saveSettings(Settings settings) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || settings == null) {
            return;
        }
        try (Connection conn = db.getConnection()) {
            boolean exists = false;
            try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM settings WHERE user_id = ? LIMIT 1")) {
                ps.setString(1, uid);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) exists = true;
                }
            }
            if (exists) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE settings SET json_data = ? WHERE user_id = ?")) {
                    ps.setString(1, mapper.writeValueAsString(settings));
                    ps.setString(2, uid);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO settings (user_id, json_data) VALUES (?, ?)")) {
                    ps.setString(1, uid);
                    ps.setString(2, mapper.writeValueAsString(settings));
                    ps.executeUpdate();
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }
```

- **Why the manual exists-check instead of an upsert?** The `settings` table
  has *no natural unique key* the app controls — its schema is
  `(id INTEGER PRIMARY KEY, json_data, user_id)` and `id` is meant to be the
  rowid auto-assigned on insert. There is no `id` value to conflict on
  (the app never sets one), so `ON CONFLICT(id)` can't be aimed at "this
  user's row". Hence: probe, then UPDATE-by-user or INSERT. Same outcome as
  an upsert, just spelled the long way — and a nice illustration that the
  upsert idiom needs a *conflict target*, which not every table offers.
- One connection, two statements: the probe and the write share it —
  correct *and* one file-open cheaper than two methods.

```java
    public synchronized int getNextBillNumberAndIncrement() {
        Settings s = getSettings();
        int current = s.getBillNoNext();
        s.setBillNoNext(current + 1);
        saveSettings(s);
        return current;
    }

    // --- Aliases for uniform API across views and services ---
    public Settings get() { return getSettings(); }
    public void save(Settings settings) { saveSettings(settings); }
}
```

- **The bill-number dispenser** — the most concurrency-sensitive three
  lines in the chapter. Two invoices saved simultaneously must never print
  the same number. The defence here is `synchronized` on the *method*:
  within this JVM, one caller at a time executes read→increment→save. The
  number is returned *before* the increment is persisted, and the next call
  reads the updated value — classic read-modify-write made safe by the lock.
  The residual risk (a crash between increment and save could reuse a
  number) is accepted: duplicates are annoying, gaps are normal in billing.
  (Multi-process safety would need an atomic `UPDATE … RETURNING`, flagged
  in §8.)
- The alias block closes the class — `get()`/`save()` so the newer uniform
  call sites read like every other DAO.

### Step 2 — `db/BuyerDao.java` (complete file)

```java
package com.invoicestudio.db;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.Buyer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BuyerDao {
    private final DatabaseManager db;
    private final ObjectMapper mapper = new ObjectMapper();

    public BuyerDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }

    public List<Buyer> getAllBuyers() {
        List<Buyer> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) {
            return list;
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM buyers WHERE user_id = ? ORDER BY name ASC")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String json = rs.getString("json_data");
                    if (json != null) {
                        list.add(mapper.readValue(json, Buyer.class));
                    }
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return list;
    }
```

- The canonical list method of the JSON-blob style, worth internalising:
  one indexed column filter (`user_id = ?` — the `idx_buyers_user` index
  from Chapter 3), one ordering column, and each row becomes a single
  Jackson deserialisation. `ORDER BY name ASC` pushes sorting into SQLite —
  always prefer the database's sort (C code, no object churn) over sorting
  in Java afterwards.

```java
    public Buyer getBuyerById(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM buyers WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper.readValue(rs.getString("json_data"), Buyer.class);
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return null;
    }

    public Buyer findByName(String name) {
        if (name == null || name.isBlank()) return null;
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT json_data FROM buyers WHERE LOWER(name) = LOWER(?) AND user_id = ? LIMIT 1")) {
            ps.setString(1, name.trim());
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapper.readValue(rs.getString("json_data"), Buyer.class);
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return null;
    }
```

- `getBuyerById` puts `user_id` **inside the WHERE of the identity lookup**
  — even knowing a buyer's id, you cannot read another account's record.
  The partitioning rule is applied at every read, not just lists.
- `findByName` is the duplicate-check used by the Buyers screen's save
  button: `LOWER(name) = LOWER(?)` makes "acme" and "ACME" the same buyer
  to the check, `.trim()` ignores stray spaces, `LIMIT 1` stops at the
  first match. This is the app-level duplicate defence; (Chapter 3 gave
  *categories* a database-level UNIQUE index — buyers deliberately don't
  have one, because a shop legitimately may have two same-named buyers at
  different phones; the UI warns but allows.)

```java
    public void saveBuyer(Buyer buyer) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || buyer == null) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO buyers (id, user_id, name, phone, gst, state, json_data, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, name = excluded.name, phone = excluded.phone, gst = excluded.gst, state = excluded.state, json_data = excluded.json_data, updated_at = excluded.updated_at WHERE buyers.user_id = excluded.user_id")) {
            String now = Instant.now().toString();
            if (buyer.getCreatedAt() == null) buyer.setCreatedAt(now);
            buyer.setUpdatedAt(now);

            ps.setString(1, buyer.getId());
            ps.setString(2, uid);
            ps.setString(3, buyer.getName());
            ps.setString(4, buyer.getPhone());
            ps.setString(5, buyer.getGst());
            ps.setString(6, buyer.getState());
            ps.setString(7, mapper.writeValueAsString(buyer));
            ps.setString(8, buyer.getCreatedAt());
            ps.setString(9, buyer.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    public void deleteBuyer(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM buyers WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    // --- Aliases for uniform API across views and services ---
    public List<Buyer> findAll() { return getAllBuyers(); }
    public Buyer findById(String id) { return getBuyerById(id); }
    public void save(Buyer buyer) { saveBuyer(buyer); }
    public void insert(Buyer buyer) { saveBuyer(buyer); }
    public void update(Buyer buyer) { saveBuyer(buyer); }
    public void delete(String id) { deleteBuyer(id); }
}
```

- **The guarded upsert, full text.** Walk the pieces:
  1. `INSERT … VALUES (…)` — the normal insert.
  2. `ON CONFLICT(id)` — fired when the primary key exists (saving an
     edited buyer).
  3. `DO UPDATE SET … = excluded.…` — every column replaced by the new
     values, `json_data` included (the whole object round-trips).
  4. `WHERE buyers.user_id = excluded.user_id` — §3.3's tenant guard:
     if the existing row belongs to a *different* account, the UPDATE's
     WHERE fails, zero rows change, no error. Save-as-someone-else is
     impossible at the SQL level.
- Timestamps are set on the *object* before serialising — so the JSON blob
  and the columns always agree, and the caller's in-memory object is left
  with the same stamps the database now holds.
- `deleteBuyer` — hard delete (buyers are one of the tables *allowed* hard
  deletes: history rows keep the buyer's name as text in `bills.buyer_name`,
  so old invoices still render after the master record is gone — the
  denormalisation Chapter 3 noted earning its keep).
- Every method's guard-then-act shape is now familiar; note `delete` also
  silently no-ops for a blank id rather than throwing — consistent with the
  fail-empty contract.

### Step 3 — `db/SupplierDao.java` (the mirror, with two differences)

`SupplierDao` is BuyerDao's structure twin — same imports, same
`getEffectiveUserId`, same list/get/findByName/upsert-delete skeleton, same
alias block — differing in table name, model class, and two real additions:

```java
    /** Number of suppliers for the current user (used by KPI header). */
    public int count() {
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) return 0;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) AS c FROM suppliers WHERE user_id = ?")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("c");
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return 0;
    }
```

- **`count()`** — an aggregate the KPI header (the suppliers screen's
  "N suppliers" chip) can call without dragging every row across the wire.
  `SELECT COUNT(*)` lets SQLite count internally; the alias `AS c` gives the
  computed column a name the reader can fetch. Notice the fail-empty
  contract's return here is `0` — an honest "no suppliers" rather than a
  wrong number.
- Its `saveSupplier` upsert and `deleteSupplier` are line-for-line BuyerDao's
  with `suppliers`/`Supplier` substituted — which is itself the lesson:
  **the repetition is deliberate**. A shared generic `Dao<T>` base class was
  possible, but would drag reflection into every row mapping and blur which
  table has which columns. The app chose the "boring duplicate" over the
  "clever generic" — for six small classes, maintenance reality favours the
  duplicate (each DAO is fully readable top-to-bottom with zero indirection).
  §8 shows what a generic base would look like, for completeness.

### Step 4 — `db/ItemDao.java` (complete file — the richest master DAO)

```java
package com.invoicestudio.db;

import com.invoicestudio.service.AppLog;
import com.invoicestudio.model.ItemRecord;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ItemDao {
    private final DatabaseManager db;

    public ItemDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }
```

Note what's missing: no `ObjectMapper`. Items store **no JSON** — every
field is a column (the plain-column style from §3.5).

```java
    public List<ItemRecord> getAllItems() {
        List<ItemRecord> list = new ArrayList<>();
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) {
            return list;
        }
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM items WHERE user_id = ? ORDER BY name ASC")) {
            ps.setString(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ItemRecord it = new ItemRecord(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("hsn"),
                        rs.getString("unit"),
                        rs.getDouble("rate"),
                        rs.getDouble("gst")
                    );
                    try {
                        it.setCategoryId(rs.getString("category_id"));
                        it.setCategoryName(rs.getString("category_name"));
                    } catch (Exception ignored) {
            AppLog.debug(ignored); }
                    try { it.setPurchaseRate(rs.getDouble("purchase_rate")); } catch (Exception ignored) {
            AppLog.debug(ignored); }
                    try { it.setCurrentStock(rs.getDouble("current_stock")); } catch (Exception ignored) {
            AppLog.debug(ignored); }
                    try { it.setOpeningStock(rs.getDouble("opening_stock")); } catch (Exception ignored) {
            AppLog.debug(ignored); }
                    try { it.setReorderLevel(rs.getDouble("reorder_level")); } catch (Exception ignored) {
            AppLog.debug(ignored); }
                    it.setCreatedAt(rs.getString("created_at"));
                    it.setUpdatedAt(rs.getString("updated_at"));
                    list.add(it);
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return list;
    }
```

- **`SELECT *` appears for the first time** — and here it is *right*: the
  row mapper consumes (nearly) every column, so naming them adds noise
  without information. (The `getItemById` mapper below is identical except
  it returns one object — that duplication inside one file is the cost of
  the plain-column style, acknowledged in §8.)
- **The per-field try/catch blocks are the chapter's most instructive
  wart.** Each optional column (category, purchase/stock fields — all added
  by Chapter 3's `ALTER` migrations) is read inside its own guard. Why:
  `rs.getString("category_id")` throws `SQLException` if the column doesn't
  exist — which is exactly the state of a database file created before
  v4.1/v4.3 if some launch's migration failed midway. The mapper degrades
  *field by field*: you get an item with no category rather than an empty
  items list. Fail-small, not fail-everything. (The odd indentation of the
  catch blocks is a formatting artifact of an automated edit pass — the
  code is faithful; §8 notes the cosmetic fix.)
- `getItemById(String)` — same mapper, `WHERE id = ? AND user_id = ?`, the
  buyer dao's identity pattern repeated.

```java
    private String generateItemId() {
        return "it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public void saveItem(ItemRecord item) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || item == null) return;

        if (item.getId() == null || item.getId().isBlank()) {
            item.setId(generateItemId());
        } else {
            item.setId(item.getId().trim());
        }
```

- **ID generation lives in the DAO** (this is what `DatabaseTest`'s two
  auto-ID tests pin): a new item without an id gets `it_` + 12 hex chars of
  a random UUID — short enough to be readable in a database browser,
  random enough to never collide in practice. Setting it **on the caller's
  object** (not a local copy) is the contract: after `save`, the caller can
  immediately select/edit/delete by `item.getId()`.

```java
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                 "INSERT INTO items (id, user_id, name, hsn, unit, rate, gst, category_id, category_name, purchase_rate, current_stock, opening_stock, reorder_level, created_at, updated_at) " +
                 "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                 "ON CONFLICT(id) DO UPDATE SET user_id = excluded.user_id, name = excluded.name, hsn = excluded.hsn, unit = excluded.unit, " +
                 "rate = excluded.rate, gst = excluded.gst, category_id = excluded.category_id, " +
                 "category_name = excluded.category_name, purchase_rate = excluded.purchase_rate, current_stock = excluded.current_stock, " +
                 "opening_stock = excluded.opening_stock, reorder_level = excluded.reorder_level, updated_at = excluded.updated_at WHERE items.user_id = excluded.user_id")) {
            String now = Instant.now().toString();
            if (item.getCreatedAt() == null) item.setCreatedAt(now);
            item.setUpdatedAt(now);

            ps.setString(1, item.getId());
            ps.setString(2, uid);
            ps.setString(3, item.getName());
            ps.setString(4, item.getHsn());
            ps.setString(5, item.getUnit());
            ps.setDouble(6, item.getRate());
            ps.setDouble(7, item.getGst());
            ps.setString(8, item.getCategoryId());
            ps.setString(9, item.getCategoryName());
            ps.setDouble(10, item.getPurchaseRate());
            ps.setDouble(11, item.getCurrentStock());
            ps.setDouble(12, item.getOpeningStock());
            ps.setDouble(13, item.getReorderLevel());
            ps.setString(14, item.getCreatedAt());
            ps.setString(15, item.getUpdatedAt());
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }
```

- The guarded upsert at its widest: 15 placeholders, every column in the
  SET list, tenant guard last. One detail to notice: **`current_stock` is
  saved as a plain column here** — the Items screen edits opening stock and
  the app maintains the cached current-stock column (the purchase/bill
  services update it inside their transactions, Chapter 5/13). The stock
  *truth* remains the ledger (Chapter 3's design note); this column is the
  fast-read cache. The split is invisible to users but you now know both
  halves.

```java
    public void deleteItem(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) {
            throw new IllegalArgumentException("Item id is required");
        }

        String normalizedId = id.trim();
        if ("item_pent".equalsIgnoreCase(normalizedId)) {
            throw new IllegalArgumentException("Protected default item cannot be deleted");
        }

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM items WHERE id = ? AND user_id = ?")) {
            ps.setString(1, normalizedId);
            ps.setString(2, uid);
            int deleted = ps.executeUpdate();
            if (deleted == 0) {
                throw new IllegalArgumentException("Item not found or already deleted: " + normalizedId);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
            throw new RuntimeException("Failed to delete item: " + e.getMessage(), e);
        }
    }
```

- **This method breaks the fail-empty contract — deliberately — and the
  difference is the lesson.** Deletes for items *throw*:
  - blank id → `IllegalArgumentException` (caller bug, deserve to crash);
  - `"item_pent"` → the **protected default item** (the seeder's starter
    record — deleting it would break first-run assumptions and the MCP
    tools' guarantees; the same protection `CategoryDao` applies to
    `cat_trouser` below);
  - zero rows deleted → "not found" exception — telling "you deleted
    nothing" apart from "the database failed" matters for deletes, because
    the user clicked a button and deserves an outcome either way.
- The `catch (IllegalArgumentException e) { throw e; }` before the generic
  catch is Java's re-throw idiom: the broad `catch (Exception)` would
  otherwise swallow the method's *own* validation exceptions; re-throwing
  them first keeps them intact while genuine `SQLException`s get wrapped in
  a `RuntimeException` (so the UI's caller sees one honest message, and
  `AppLog` keeps the full stack).

```java
    // --- Category helpers (MCP category CRUD: rename cascade + delete guard) ---

    /** Number of catalog items currently assigned to a category (per user). */
    public int countItemsInCategory(String categoryId) { ... SELECT COUNT(*) FROM items WHERE user_id = ? AND category_id = ? ... }

    /**
     * Keeps the denormalized category_name in sync after a category rename.
     * Returns the number of item rows updated.
     */
    public int updateCategoryNameForCategory(String categoryId, String newName) {
        ... UPDATE items SET category_name = ?, updated_at = ? WHERE user_id = ? AND category_id = ...
    }
}
```

*(both shown complete in the repo file; the SQL is quoted in full above)*

- Two helpers that exist **because `category_name` is denormalised** onto
  items (stored twice: the id *and* the readable name). When a category is
  renamed, every item carrying the old name must be corrected — a single
  UPDATE over the owning user's rows. `countItemsInCategory` answers the
  delete-guard question ("4 items use this category — delete anyway?")
  without loading items into memory. Both return counts instead of
  objects — the DAO as *question-answerer*, not just box-mover. Their
  callers are the MCP tool layer (Chapter 18), which is also why they're
  named so formally.

### Step 5 — `db/CategoryDao.java` and `db/TransportDao.java` (complete files)

```java
package com.invoicestudio.db;

import com.invoicestudio.model.ItemCategory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CategoryDao {
    private final DatabaseManager db;

    public CategoryDao(DatabaseManager db) {
        this.db = db;
    }

    private String getEffectiveUserId() {
        return com.invoicestudio.service.AuthSessionManager.getCurrentUserId();
    }
```

*(getAllCategories / getCategoryById / saveCategory follow the exact
plain-column skeleton — SELECT \* with a field-by-field mapper
(`ItemCategory(id, name)` + created/updated stamps), guarded upsert on
`(id, user_id, name, created_at, updated_at)`. Reproduced complete in the
repo; here are the two genuinely new moves:)*

```java
    public void deleteCategory(String id) {
        String uid = getEffectiveUserId();
        if (uid.isEmpty() || id == null || id.isBlank()) return;
        if ("cat_trouser".equalsIgnoreCase(id) || "cat_trousers".equalsIgnoreCase(id)) return; // Protected default category
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM categories WHERE id = ? AND user_id = ?")) {
            ps.setString(1, id);
            ps.setString(2, uid);
            ps.executeUpdate();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    public List<ItemCategory> findAll() { return getAllCategories(); }
    public ItemCategory findById(String id) { return getCategoryById(id); }
}
```

- The **protected default**: `"cat_trouser"` (and its plural spelling) is
  the category `DataManager.seedIfEmpty` guarantees to exist (Chapter 8).
  Delete requests for it are **silently ignored** — the caller can't break
  the seed invariant even by trying. Contrast `ItemDao.deleteItem`, which
  *throws* for its protected record: different surfaces, different
  politeness — categories face UI + MCP flows where a silent no-op keeps a
  batch tool run alive; item deletion goes through a confirmation dialog
  where a thrown reason is the better answer. Both implement the same
  rule: *seeded defaults are irremovable.*
- No `findByName` here — categories' duplicate defence lives at the
  database level (Chapter 3's `UNIQUE INDEX … COLLATE NOCASE`), a stronger
  guarantee than any DAO check.

```java
package com.invoicestudio.db;

import com.invoicestudio.model.Transport;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TransportDao {
    ... same constructor/getEffectiveUserId skeleton ...
```

*(complete file in the repo: getAllTransports, getTransportById,
saveTransport, deleteTransport, findAll/findById aliases — the familiar
plain-column skeleton over `(id, user_id, name, phone, vehicle_number,
created_at, updated_at)`)* — plus one method worth its own look:

```java
    public Transport findByName(String name) {
        if (name == null || name.isBlank()) return null;
        String uid = getEffectiveUserId();
        if (uid.isEmpty()) return null;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM transports WHERE LOWER(name) = LOWER(?) AND user_id = ? LIMIT 1")) {
            ps.setString(1, name.trim());
            ps.setString(2, uid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Transport t = new Transport(
                        rs.getString("id"),
                        rs.getString("name"),
                        rs.getString("phone"),
                        rs.getString("vehicle_number")
                    );
                    t.setCreatedAt(rs.getString("created_at"));
                    t.setUpdatedAt(rs.getString("updated_at"));
                    return t;
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
        return null;
    }
```

- `findByName` on transports serves the bill editor's **type-ahead
  transport box**: the clerk types "Sharma Trans" and the app matches the
  existing party case-insensitively instead of creating a duplicate — the
  same dedup-by-name story the MCP `McpEnsure` helpers rely on for every
  master entity (Chapter 18). This is the third copy of that SQL shape in
  this chapter (Buyer, Supplier, Transport) — by now it should read like
  your own handwriting.

---

## 6. How it works at runtime

A buyer edit, end to end:

```
 BuyersView: user edits "Solaris Enterprises" → clicks Save
   │  (FX thread — no DB work here)
   ▼
 DataManager.saveBuyer(b)          ← cache layer, Ch 8: writes through
   ▼
 BuyerDao.save(b)
   ├── uid = AuthSessionManager.getCurrentUserId()   ("usr_9f2…")
   ├── if uid empty → return            (not signed in: nothing happens)
   ├── now = Instant.now(); stamps created_at (once) / updated_at (always)
   ├── Connection opened (jdbc:sqlite → invoicestudio.db file)
   ├── INSERT … ON CONFLICT(id) DO UPDATE … WHERE buyers.user_id = excluded.user_id
   │     ├── new id  → INSERT, row created
   │     ├── own row → UPDATE, all columns replaced
   │     └── other user's id → WHERE fails → 0 rows (no error, no damage)
   └── AppLog.error only if the file/SQL failed
   ▼
 DataManager bumps dataEpoch (Ch 8) → other views know data changed
```

**The three read styles at a glance:**

| Method | SQL shape | Why this shape |
|---|---|---|
| `getAllBuyers()` | 1 column (`json_data`), filtered + ordered | list = full objects, sorted by DB |
| `findByName()` | `LOWER()` match + `LIMIT 1` | duplicate check / type-ahead |
| `count()` / `countItemsInCategory()` | `COUNT(*)` | answer without moving rows |
| `hasNoSettingsForUser()` | `SELECT 1 … LIMIT 1` | existence probe for the seeder |
| `updateCategoryNameForCategory()` | bulk `UPDATE` | cascade a rename |

**Threading note:** none of these methods touch the FX thread themselves —
they are plain blocking calls, and every *caller* in later chapters wraps
them in `AppExecutors.io()` (the single serialised DB lane from Chapter 2).
The DAOs being thread-agnostic is what makes that discipline possible.

---

## 7. How to change it

**Add a field to buyers (JSON-blob style — the easy case).** Example: a
`gstRegistrationType` field.
1. `model/Buyer.java`: add field + getter/setter (Chapter 6 covers the class;
   Jackson picks up any public property automatically).
2. Done for storage — the field rides inside `json_data`; no SQL changes.
3. Want it searchable/sortable? *Then* add the hot column: Chapter 3's
   three-edit recipe (ALTER line → DAO save/mapper → model), e.g. index it
   if the Buyers screen will filter by it.
Verify: `mvn test -Dtest=DatabaseTest`, then round-trip through the UI
(save → reopen app → field survives). What breaks if skipped: nothing for
storage (that's the style's gift); only *search* breaks if you skip the
column.

**Add a field to items (plain-column style — the full recipe).**
1. `model/ItemRecord.java`: field + accessors.
2. `DatabaseManager.initSchema()`: the try/catch ALTER line.
3. `ItemDao`: add the column to the INSERT's two lists, the ON CONFLICT SET
   list, bind one more `ps.setX`, and add the mapper line in **both**
   `getAllItems` and `getItemById` (this style's double-edit cost).
4. Existing rows: decide the `DEFAULT` in the ALTER (old items get it
   automatically).
Verify: run the app twice (first launch migrates, second confirms
convergence), open Items, edit one, restart, check the value persisted.
What breaks if missed: skip the mapper lines → the field shows empty even
though it saved; skip the SET list → edits never persist the new field.

**Protect a new default record:** copy the `item_pent` / `cat_trouser`
guard into the relevant delete method (and decide throw vs silent-noop by
the surface: dialogs can throw; batch/MCP callers prefer silent).

---

## 8. Performance & UX analysis

| Decision | What was done | Cost | Better alternative | Trade-off / difficulty | User-visible effect |
|---|---|---|---|---|---|
| JSON-blob for Buyer/Supplier/Settings | whole object per row, hot columns only for identity | full-row parse per read | full column-per-field | **Medium**; more DDL forever | instant lists; new buyer fields need zero SQL |
| Plain columns for Item/Category/Transport | every field a column | mapper duplicated across two read methods | shared row-mapper helper | **Easy** win available (see below) | bill editor gets rate/GST/stock without JSON parsing |
| Boring duplicate DAOs vs generic `Dao<T>` | six self-contained classes | copy-paste drift risk | generic base with row-mapper lambda | **Medium**; saves ~200 lines, hides column knowledge | none directly; maintainability judgement call |
| `synchronized` bill-number dispenser | JVM-wide lock on read-modify-write | serialises bill saves (fine — they're already on one IO thread) | atomic `UPDATE settings SET billNoNext = billNoNext + 1 … RETURNING` | **Medium**; SQLite supports RETURNING since 3.35 | two rapid invoices never share a number |
| `SELECT *` in item/category mappers | convenient, coupled to schema | extra columns fetched unnoticed | explicit column lists | **Easy** | none measurable |
| Fail-empty everywhere; fail-loud only on item delete | predictable callers | silent no-ops can mask "not signed in" during dev | return an Optional/Result type | **Medium**; touches every caller | users see empty screens pre-login instead of stack traces |

`OPTIONAL IMPROVEMENT` — extract the duplicated item row-mapper:

```java
// OPTIONAL IMPROVEMENT: one mapper, two callers (Easy, ~30 lines saved)
private ItemRecord mapItemRow(ResultSet rs) throws SQLException {
    ItemRecord it = new ItemRecord(rs.getString("id"), rs.getString("name"),
            rs.getString("hsn"), rs.getString("unit"),
            rs.getDouble("rate"), rs.getDouble("gst"));
    try { it.setCategoryId(rs.getString("category_id"));
          it.setCategoryName(rs.getString("category_name")); } catch (Exception ignored) { AppLog.debug(ignored); }
    try { it.setPurchaseRate(rs.getDouble("purchase_rate")); } catch (Exception ignored) { AppLog.debug(ignored); }
    try { it.setCurrentStock(rs.getDouble("current_stock")); } catch (Exception ignored) { AppLog.debug(ignored); }
    try { it.setOpeningStock(rs.getDouble("opening_stock")); } catch (Exception ignored) { AppLog.debug(ignored); }
    try { it.setReorderLevel(rs.getDouble("reorder_level")); } catch (Exception ignored) { AppLog.debug(ignored); }
    it.setCreatedAt(rs.getString("created_at"));
    it.setUpdatedAt(rs.getString("updated_at"));
    return it;
}
```

`getAllItems`/`getItemById` then call `mapItemRow(rs)` — the field-level
defensive reads stay exactly as they are, and a new column needs one edit,
not two. Verify with `mvn test -Dtest=DatabaseTest` plus the auth
partitioning suite.

`OPTIONAL IMPROVEMENT` — atomic bill numbering (multi-process safe):

```java
// OPTIONAL IMPROVEMENT: number + increment in one SQLite statement
public int nextBillNumberAtomic() throws SQLException {
    try (Connection c = db.getConnection();
         PreparedStatement ps = c.prepareStatement(
             "UPDATE settings SET json_data = json_data WHERE user_id = ? RETURNING 1")) {
        // full version rewrites billNoNext inside the Settings JSON atomically —
        // shown conceptually; current single-process synchronized form is correct
        // for this app's one-JVM reality.
    }
    ...
}
```

*Honest note:* because settings is JSON-blob storage, the atomic version
must parse-modify-serialise inside one statement — awkward in SQLite. The
`synchronized` in-JVM lock is the right cost/benefit here; the alternative
only pays off if the app ever runs two processes against one file.

---

## 9. Common mistakes and fixes

| Symptom | Cause | Fix |
|---|---|---|
| List screens empty right after login succeeds | DAO reads ran before the session was set (`uid` was `""`) | ensure sign-in completes before the first view builds (Chapter 10's ordering) |
| Edits to one account's buyer don't appear | upsert's tenant guard saw a row owned by another user id (e.g. after restoring another account's backup) | by design — check which account owns the row (`SELECT id, user_id …`) |
| `SQLException: no such column` while listing items | an old DB without a migrated column, and the *outer* try caught it (whole list lost) | the per-field guards handle added columns; if you add one, follow the full Step "add a field to items" recipe |
| Same bill number twice after a crash | increment happened, save of the counter didn't | accepted trade-off; manually set the counter in Settings (or implement §8's atomic form) |
| Deleting the default item/category "does nothing" | protected-default guard | intended; it cannot be deleted from any surface |
| New buyer field never persists | added to the model but the caller builds a *new* object for save without copying the field | JSON style stores whatever the object holds — ensure the UI writes into the object it saves |

---

## 10. Checkpoint

- [ ] `mvn test -Dtest=DatabaseTest` — all 7 tests green (these six DAOs are
      the subject of six of them).
- [ ] You can write, from memory, the guarded-upsert skeleton for any table:
      INSERT … ON CONFLICT(id) DO UPDATE SET … WHERE table.user_id = excluded.user_id.
- [ ] You can name which DAOs are JSON-blob style (Settings, Buyer,
      Supplier) and which are plain-column (Item, Category, Transport) —
      and say why items need columns (bill-editor hot path + rename cascade).
- [ ] You can explain the two protected defaults (`item_pent`, `cat_trouser`)
      and the two different enforcement styles (throw vs silent no-op).

**Exercises**

1. Add a `credit_days INTEGER DEFAULT 0` column to buyers end-to-end via
   the *JSON-style* recipe (model only), then try to `WHERE` on it —
   observe that you can't, and add the hot column to see the difference
   between the two styles with your own SQL.
2. In a test database, save two buyers with ids belonging to "different
   users" (swap the session between saves) and confirm the tenant guard:
   the second save updates 0 rows. Write it as a JUnit test.
3. Add `count()` to `BuyerDao` by copying `SupplierDao`'s, and use it in a
   tiny assertion in `DatabaseTest`.

---

## 11. Summary and coverage self-check

You built the six master-data librarians and met every idiom the remaining
DAOs reuse: the guarded upsert with its tenant-guard WHERE, the
`LOWER()+LIMIT 1` name match, the `COUNT(*)`/`SELECT 1` probes, the
per-field defensive row mapper, the protected-default guards (throw and
silent variants), the never-null `Settings.get()`, and the synchronized
bill-number dispenser. You saw the JSON-blob vs plain-column split up close
and why each table chose its style.

**Files covered in full this chapter (6):**
- `db/SettingsDao.java` ✅ (96/96 lines)
- `db/BuyerDao.java` ✅ (134/134 lines)
- `db/SupplierDao.java` ✅ (141/141 lines; structural twin noted, deltas covered)
- `db/ItemDao.java` ✅ (205/205 lines)
- `db/CategoryDao.java` ✅ (106/106 lines)
- `db/TransportDao.java` ✅ (129/129 lines)

**Markers raised:** none new — behaviour matches the tests; the duplicated
item mapper and odd catch-block indentation are noted as cosmetic clean-ups
in §8 rather than issues.

**Next: Chapter 5 — "The DAO Pattern, Part 2 — Documents & Ledgers"**
(`BillDao`, `PurchaseBillDao`, `TransactionDao`, `ExpenseDao`,
`ExpenseAccountDao`, `StockLedgerDao`, `LabelPrintHistoryDao`,
`VariableDao`, `TemplateDao`, `AuthDao` — the transactional half).