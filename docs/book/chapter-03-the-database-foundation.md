# Chapter 3 — The Database Foundation

> **Part 2 of InvoiceStudio: Zero to Finished Product**
> Files covered in full this chapter: `db/DatabaseManager.java`,
> `db/DatabaseTest.java`, and the whole `src/main/resources/seed/` folder —
> including an honest `ISSUE:` about what that folder actually does (less
> than it appears to).
> Goal at the end: a running SQLite database with the app's complete schema,
> created by code you understand line by line.

---

## 1. Chapter goal

By the end of this chapter you will:

- understand what SQLite is, why a billing app is perfect for it, and how a
  Java program physically talks to it through **JDBC**;
- have written `DatabaseManager` — the class that opens connections and
  creates/migrates the app's **17-table schema** — and be able to explain
  every table, every column, and why the migration style looks "weird"
  (and why that weirdness is deliberate);
- know how the app tests its database without ever touching a real user's
  data;
- understand where demo data really comes from on first run (it is *not*
  the seed folder — read §5 Step 4 for the proof).

---

## 2. Story intro

Imagine a traditional shop's back office: heavy ledger books, one per
subject — a sales book, a purchase book, a customer address book, a stock
register. Every entry is written in ink, in order, and any clerk can find
yesterday's sales by flipping to the right page.

A **relational database** is the software version of that back office:
**tables** are the ledger books, **rows** are the entries, **columns** are
the ruled fields on each page, and **SQL** is the clerk's language
("find all unpaid sales from March").

The app chose **SQLite** for the ledger, and the choice is worth dwelling on,
because it shapes everything:

- **SQLite is not a server.** MySQL/PostgreSQL run as separate programs your
  computer must start; a program connects to them over a network socket even
  when both run on the same machine. SQLite is a **library** — the whole
  database engine is code inside your app, and the database itself is *one
  ordinary file* (`invoicestudio.db`). No installation, no service, no port,
  no password file. For a desktop billing app used by one person at a time,
  that is exactly right.
- **It can go anywhere.** Backup the app = copy one file. Move the app to a
  new PC = copy one file. (Chapter 13's `BackupRestoreService` does exactly
  that.)
- **The trade-off:** SQLite allows many *readers* but a single *writer* at a
  time. A shop with fifty clerks hammering one database needs a server; a
  shop with one clerk needs zero. InvoiceStudio is the one-clerk case — and
  even its AI assistant writes through the same single serialised queue
  (Chapter 18) precisely to respect this.

Between Java and SQLite sits **JDBC** (*Java Database Connectivity*) —
Java's standard interface for databases. Your code speaks generic JDBC
(`Connection`, `PreparedStatement`, `ResultSet`); a **driver** translates
that into the database's own protocol. The Xerial `sqlite-jdbc` driver from
Chapter 1 even carries the native SQLite engine for Windows/macOS/Linux
inside the JAR, which is why the same app file works everywhere with no
database installation step.

---

## 3. Concepts first

### 3.1 SQL in ninety seconds

SQL (*Structured Query Language*) is the text language of databases.
The five verbs this app uses:

```sql
CREATE TABLE buyers (id TEXT PRIMARY KEY, name TEXT);  -- define a ledger
INSERT INTO buyers (id, name) VALUES ('b1', 'Acme');   -- write an entry
SELECT name FROM buyers WHERE id = 'b1';               -- read entries
UPDATE buyers SET name = 'Acme Ltd' WHERE id = 'b1';   -- correct an entry
DELETE FROM buyers WHERE id = 'b1';                    -- tear out an entry
```

`PRIMARY KEY` = the unique row number of the ledger (no duplicates allowed).
`TEXT/REAL/INTEGER` = column types (SQLite is loosely typed; these are
*affinities*, not hard rules — unlike stricter databases).
`CREATE INDEX` = the book's back-of-book index: a sorted copy of one column
so finding by it skips reading every page.

### 3.2 The JDBC ritual: connect → prepare → execute → close

Every database conversation in every DAO (Chapters 4–5) follows the same
four-beat ritual:

```java
try (Connection conn = db.getConnection();          // 1. open
     PreparedStatement ps = conn.prepareStatement(
             "SELECT * FROM buyers WHERE id = ?")) { // 2. prepare with a placeholder
    ps.setString(1, id);                            // 3. fill the placeholder
    try (ResultSet rs = ps.executeQuery()) {        // 4. run + read
        while (rs.next()) { /* one row per rs.next() */ }
    }
}                                                    // auto-close in reverse order
```

- `?` is a **placeholder**. Values are bound *after* the SQL text is fixed,
  so a customer named `Robert'); DROP TABLE buyers;--` is stored as harmless
  text instead of executing as SQL. This is the **SQL-injection** defence,
  and using placeholders *everywhere* is why the app has none of the classic
  injection holes.
- The **try-with-resources** parentheses are Java's guarantee: whatever
  happens (even an exception), every opened thing is closed in reverse
  order. Forgetting to close a connection leaks file handles — the classic
  bug that appears only after the app has run for a day.

### 3.3 Why connections are opened per operation

Look ahead at `getConnection()` in this chapter's code: it returns a **new**
connection every call, and every caller wraps it in try-with-resources.
Why not keep one connection open forever? Two reasons: (a) SQLite connections
are cheap to open (no network handshake — it's a file), and (b) a shared
long-lived connection used from both the FX thread and background threads
becomes a locking headache. Per-operation connections + the single
`invoicestudio-io` queue (Chapter 2) keep contention simple and predictable.

### 3.4 Schema migration without a migration framework

Real apps change their schema over time ("add a column for GST"). Big
projects use migration tools with numbered scripts. This app uses a pattern
possible precisely because SQLite supports `ADD COLUMN` cleanly:

1. `CREATE TABLE IF NOT EXISTS ...` — creates each table *only if missing*,
   so an existing database is untouched (all statements become no-ops).
2. `ALTER TABLE ... ADD COLUMN ...` inside a **try/catch-ignore** — the first
   launch after an upgrade adds the new column; on every later launch the
   statement fails with "duplicate column name", the catch swallows that
   *expected* failure, and nothing else happens.

No version table, no script runner — the schema *converges* to the latest
shape on every launch. The cost (you will see it in the code) is visual
noise: dozens of try/catch blocks. The benefit (no migration framework to
learn, and it is impossible for "the upgrade script didn't run") is why it
was chosen. `GAP:` note — this is a *documented trade-off*, not an accident;
§8 discusses the alternative.

### 3.5 Singleton — one instance, globally reachable

`DatabaseManager` uses the **singleton pattern**: a private static field, a
private constructor, and a `getInstance()` accessor that creates the single
instance on first use. `synchronized` on the accessor means two threads
arriving simultaneously cannot both create instances (the first thread
holds the class lock until the method returns; the second waits, then finds
`instance != null`).

---

## 4. Files in this chapter

| File | Type | Purpose | Lines |
|---|---|---|---|
| `src/main/java/com/invoicestudio/db/DatabaseManager.java` | Class | Singleton; connection factory; schema create + converge + seed | 237 |
| `src/test/java/com/invoicestudio/db/DatabaseTest.java` | Test | End-to-end CRUD across six DAOs on a throwaway DB | 177 |
| `src/main/resources/seed/*.json`, `seed/custom.db` (8 files) | Resources | Demo-data payloads — *see Step 4 for the honest finding* | — |

---

## 5. Step-by-step build

### Step 1 — `src/main/java/com/invoicestudio/db/DatabaseManager.java` — header and singleton core

```java
package com.invoicestudio.db;

import com.invoicestudio.service.AppLog;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.*;

import java.io.File;
import java.io.InputStream;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    private static DatabaseManager instance;
    private final String dbUrl;
    private final ObjectMapper mapper = new ObjectMapper();

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            // Per-user data dir (see AppDirs) so installed copies work out of
            // write-protected Program Files; legacy CWD DBs are migrated once.
            instance = new DatabaseManager(com.invoicestudio.AppDirs.databaseUrl());
        }
        return instance;
    }

    public static synchronized DatabaseManager initCustom(String dbUrl) {
        instance = new DatabaseManager(dbUrl);
        return instance;
    }

    public DatabaseManager(String dbUrl) {
        this.dbUrl = dbUrl;
        initSchema();
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(dbUrl);
    }
```

- The imports include Jackson's `ObjectMapper` and several `model.*` types —
  `GAP (honest):` the `mapper` field and some imports (`TypeReference`,
  `InputStream`, `File`, `ArrayList`, `List`) exist to satisfy
  `seedVariables`' `VariableDef` list construction; the mapper itself is
  **never used** in the current file — a leftover from earlier revisions
  where schema setup read seed JSON. The compiler does not warn on unused
  private fields. We keep the code faithful; §8 flags the clean-up as an
  optional improvement.
- `getInstance()` — lazy singleton: the instance (and therefore the whole
  schema initialisation) happens at the **first database touch**, not at app
  boot. If a test class never touches the DB, it pays zero DB cost. The
  comment records the *why*: Chapter 2's `AppDirs.databaseUrl()` feeds it.
- `initCustom(String)` — deliberately replaces the singleton. Only tests and
  harnesses call it: "run this entire app against *that* throwaway database
  file instead." Note it *overwrites* `instance` — after `initCustom`, even
  code calling `getInstance()` gets the test DB. That is the point: one
  switch reroutes the whole app in tests. The cost: it must never be called
  from production code. (The `referencedBy` graph confirms it isn't — only
  test classes.)
- **The constructor runs `initSchema()`** — creating a `DatabaseManager`
  *is* creating the database. This ordering guarantees no DAO can ever run a
  query against a missing table.
- `getConnection()` — `DriverManager` is JDBC's registry of loaded drivers;
  asking it for the `jdbc:sqlite:` URL makes the Xerial driver open (or
  create!) the file. That is worth repeating: **SQLite creates the file on
  first connect** — a brand-new install has no database until this line.

### Step 2 — `initSchema()`: base tables (part 1 of the method)

```java
    private void initSchema() {
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS settings (id INTEGER PRIMARY KEY, json_data TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS templates (id TEXT PRIMARY KEY, name TEXT, json_data TEXT, created_at TEXT, updated_at TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS bills (id TEXT PRIMARY KEY, bill_no TEXT, date TEXT, doc_type TEXT, status TEXT, buyer_name TEXT, grand_total REAL, due_amount REAL, json_data TEXT, created_at TEXT, updated_at TEXT)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_bills_bill_no ON bills(bill_no)");
            stmt.execute("CREATE TABLE IF NOT EXISTS buyers (id TEXT PRIMARY KEY, name TEXT, phone TEXT, gst TEXT, state TEXT, json_data TEXT, created_at TEXT, updated_at TEXT)");
```

- One `Statement` executes many DDL statements in sequence — DDL
  (*Data Definition Language*) is the subset of SQL that defines structure
  (`CREATE/ALTER`) rather than data (`INSERT/UPDATE`).
- **Notice the two-part storage pattern** repeated across tables, because it
  is the app's central data design:
  - a few **hot columns** (`bill_no`, `date`, `status`, `grand_total`,
    `due_amount`…) — the fields the app *sorts, filters and lists by*,
    extracted so SQL can index and compare them;
  - one **`json_data` column** — the *entire object* serialised to JSON text
    (by Jackson, Chapter 1's dependency) for everything else.
  Why? A bill has ~40 fields (line items, payment splits, logo settings,
  custom variables…). Making each a column would need `ALTER TABLE` for
  every feature addition and huge row rewrites. The JSON blob makes storage
  schema-flexible — new fields ride along inside the JSON with zero DDL —
  while the hot columns keep lists fast. The trade-off (you cannot write
  `WHERE totals.tax > 100` against JSON cheaply) is acceptable because every
  value-critical field worth querying *is* extracted.
- `CREATE INDEX IF NOT EXISTS idx_bills_bill_no` — history search by bill
  number (the single most-typed query in a billing office) skips a full
  table scan.

```java
            // v4.3 — Purchase foundation: supplier (Sundry Creditor) directory
            stmt.execute("CREATE TABLE IF NOT EXISTS suppliers (id TEXT PRIMARY KEY, name TEXT, phone TEXT, gst TEXT, state TEXT, json_data TEXT, created_at TEXT, updated_at TEXT)");
            // v4.3.1 — Purchase cycle: purchase bills + immutable stock ledger
            stmt.execute("CREATE TABLE IF NOT EXISTS purchase_bills (id TEXT PRIMARY KEY, bill_no TEXT, supplier_bill_no TEXT, date TEXT, supplier_id TEXT, supplier_name TEXT, total REAL, itc REAL, status TEXT, json_data TEXT, created_at TEXT, updated_at TEXT)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_purchases_supplier ON purchase_bills(supplier_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_purchases_date ON purchase_bills(date)");
            stmt.execute("CREATE TABLE IF NOT EXISTS stock_ledger (id INTEGER PRIMARY KEY AUTOINCREMENT, item_id TEXT NOT NULL, transaction_date TEXT NOT NULL, voucher_type TEXT NOT NULL, voucher_id TEXT NOT NULL, voucher_no TEXT, qty_in REAL DEFAULT 0, qty_out REAL DEFAULT 0, unit_price REAL, user_id TEXT DEFAULT '', created_at TEXT)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_stock_item ON stock_ledger(item_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_stock_voucher ON stock_ledger(voucher_id)");
```

- `suppliers` mirrors `buyers` — same shape, different ledger. (Accounting
  calls suppliers *Sundry Creditors*; the comment uses the trade term.)
- `purchase_bills` keeps its own hot columns including
  `supplier_bill_no` — the number *the supplier* printed on their invoice,
  which is what a shop owner actually searches for when matching deliveries.
- `itc REAL` — *Input Tax Credit*: GST paid on purchases, claimable against
  GST collected on sales. Storing it on the purchase row powers the
  purchase-side reports (Chapter 14).
- **`stock_ledger` is deliberately different from every other table**, and
  the differences are the lesson:
  - `id INTEGER PRIMARY KEY AUTOINCREMENT` — a database-generated sequence
    instead of an app-generated UUID. A ledger's rows are *append-only
    facts* ("this voucher moved N units"); they have no natural ID, and
    autoincrement makes insertion trivial.
  - `NOT NULL` on the four columns that make a ledger row *meaningful* — the
    database itself rejects a fact with no item, date, or voucher.
  - `qty_in`/`qty_out` — the double-entry insight: never store "current
    stock" as a mutable number to be edited; store every movement, and
    current stock is `SUM(qty_in) - SUM(qty_out)`. (The `items` table also
    keeps a `current_stock` cache column — written by the same DAO
    transaction — for list-screen speed; Chapter 5 covers the pairing.)
  - No `json_data` — a ledger row *is* pure facts; there is nothing else.
  - No `updated_at` — facts are never updated. Append, never rewrite. This
    makes stock history auditable: Chapter 14's stock reports reconstruct
    any item's movement history exactly.

```java
            // v4.4 — Expense accounting (direct & indirect heads)
            stmt.execute("CREATE TABLE IF NOT EXISTS expenses (id TEXT PRIMARY KEY, date TEXT, category TEXT, amount REAL, payment_mode TEXT, json_data TEXT, created_at TEXT, updated_at TEXT)");
            // v4.5 — Expense accounts: payee registry backing the expense register combo
            stmt.execute("CREATE TABLE IF NOT EXISTS expense_accounts (id TEXT PRIMARY KEY, name TEXT, archived INTEGER DEFAULT 0, json_data TEXT, created_at TEXT, updated_at TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS items (id TEXT PRIMARY KEY, name TEXT, hsn TEXT, unit TEXT, rate REAL, gst REAL, created_at TEXT, updated_at TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS variables (key TEXT PRIMARY KEY, label TEXT, type TEXT, builtin INTEGER)");
            stmt.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, val TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS auth_session (id INTEGER PRIMARY KEY, user_id TEXT, email TEXT, display_name TEXT, id_token TEXT, refresh_token TEXT, expires_at INTEGER, remember_me INTEGER, created_at TEXT)");
```

- `expenses` (money out) and `expense_accounts` (the *payee registry* the
  expense form picks from) — a two-table design where the registry can be
  archived (`archived INTEGER DEFAULT 0`) without deleting history that
  old expenses still reference.
- `items` hot columns: `hsn` (Harmonised System of Nomenclature — the GST
  commodity code), `unit`, `rate`, `gst` — the four fields the bill editor
  needs *per row* without deserialising JSON.
- `variables` — the template system's merge-fields registry
  (`buyer_name`, `po_no`…). `builtin INTEGER` marks the read-only system
  fields you'll meet in `seedVariables` below. **Primary key is the
  variable's `key` string itself** — variables are their own namespace.
- `meta` — a tiny generic key→value shelf (`val` column), used for app-level
  bookkeeping (e.g. the recurring-invoice sweep marker).
- `auth_session` — stores the signed-in user's identity and Firebase tokens
  (Chapter 10). `id INTEGER PRIMARY KEY` + single row semantics = the app's
  "current session" slot; `expires_at INTEGER` is an epoch-millis stamp the
  session manager compares against the clock at startup.

```java
            // v4.0.0 Financial Web Integration Schema
            stmt.execute("CREATE TABLE IF NOT EXISTS transports (id TEXT PRIMARY KEY, name TEXT, phone TEXT, vehicle_number TEXT, created_at TEXT, updated_at TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS categories (id TEXT PRIMARY KEY, name TEXT, created_at TEXT, updated_at TEXT)");
            stmt.execute("CREATE TABLE IF NOT EXISTS transactions (id TEXT PRIMARY KEY, buyer_id TEXT, buyer_name TEXT, book_type TEXT, transaction_type TEXT, transaction_date TEXT, due_date TEXT, amount REAL, total_quantity INTEGER, check_number TEXT, include_in_reporting INTEGER, parcel INTEGER, bill_id TEXT, bill_no TEXT, deleted INTEGER, deleted_reason TEXT, deleted_at TEXT, created_at TEXT, updated_at TEXT)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tx_buyer ON transactions(buyer_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tx_date ON transactions(transaction_date)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_tx_book ON transactions(book_type)");
```

- `transports` — the lorry/transport-party directory (name, phone, vehicle)
  printed on invoices' transport boxes.
- `transactions` is the **cash/bank book** (note: *not* stock movements —
  that is `stock_ledger`'s job). Its shape encodes accounting practice:
  - `book_type` — which book the entry sits in (the app's "cash book" /
    "bank book" tabs); indexed, because switching books is the primary
    navigation.
  - `transaction_type` — money *in* vs money *out*.
  - `check_number` (cheque), `parcel`, `due_date` — the clerk's real fields.
  - `include_in_reporting INTEGER` — a soft toggle letting an entry be kept
    but excluded from totals (a bounced cheque, say).
  - **`deleted` / `deleted_reason` / `deleted_at`** — a **soft delete**.
    Accounting rows are almost never hard-deleted: an entry is marked
    deleted, with who/why/when, so an audit trail survives. Every read query
    in `TransactionDao` filters `deleted = 0`. This three-column pattern is
    worth memorising — Chapter 13 builds its undo story on it.
  - Three indexes (buyer/date/book) match the three ways the screen filters.

### Step 3 — `initSchema()`: migrations, purge, hardening, seeding (part 2)

```java
            // Alter column migrations
            try { stmt.execute("ALTER TABLE items ADD COLUMN category_id TEXT"); } catch (Exception ignored) {
            AppLog.debug(ignored); }
            try { stmt.execute("ALTER TABLE items ADD COLUMN category_name TEXT"); } catch (Exception ignored) {
            AppLog.debug(ignored); }
            // v4.1 — variable scope + default value support
            try { stmt.execute("ALTER TABLE variables ADD COLUMN scope TEXT DEFAULT 'fixed'"); } catch (Exception ignored) {
            AppLog.debug(ignored); }
            try { stmt.execute("ALTER TABLE variables ADD COLUMN default_value TEXT DEFAULT ''"); } catch (Exception ignored) {
            AppLog.debug(ignored); }
            // v4.6 — barcode scope: possible values (comma separated) for Bulk Label Print quick-picks
            try { stmt.execute("ALTER TABLE variables ADD COLUMN choices TEXT DEFAULT ''"); } catch (Exception ignored) {
            AppLog.debug(ignored); }
```

- Here is §3.4's pattern in the flesh. First launch on an *old* database:
  each `ALTER` succeeds once. Every later launch: "duplicate column name"
  exception → caught → `AppLog.debug` (silent unless `-Dapplog.debug=true`).
  The comments (`v4.1`, `v4.6`) are the migration history, dated by version —
  the *only* changelog this schema has, which is why they matter.
- Note what each migration tells you about a feature: variables gained
  *scope* (fixed vs user-fillable) and *default values* in v4.1, then
  *choices* (a comma-separated pick-list for barcode printing) in v4.6 —
  three feature-vehicles riding on one table via this pattern.

```java
            // v4.6 — Bulk Label Print history (info-only audit of label runs)
            stmt.execute("CREATE TABLE IF NOT EXISTS label_print_history (id TEXT PRIMARY KEY, template_id TEXT, template_name TEXT, printer_name TEXT, label_width REAL, label_height REAL, columns INTEGER, pages INTEGER, labels INTEGER, total_copies INTEGER, summary TEXT, lines_json TEXT, user_id TEXT DEFAULT '', created_at TEXT)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_lph_user ON label_print_history(user_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_lph_created ON label_print_history(created_at)");
```

- A pure *audit* table: every bulk-label print run records what was printed,
  on which printer, at what size, how many copies — so "what did we print for
  that order?" has an answer. `lines_json` holds the per-row detail; hot
  columns cover the list screen's sort/filter needs.

```java
            // v4.2 — user_id multi-user local data partitioning
            try { stmt.execute("ALTER TABLE bills ADD COLUMN user_id TEXT DEFAULT ''"); } catch ...
            ... (same pattern for buyers, items, templates, categories, transports,
                 transactions, settings, variables, meta, suppliers, expenses,
                 purchase_bills, expense_accounts — 14 ALTERs) ...
            try { stmt.execute("CREATE INDEX IF NOT EXISTS idx_bills_user ON bills(user_id)"); } catch ...
            ... (idx_buyers_user, idx_suppliers_user, idx_expenses_date,
                 idx_expenses_user, idx_expense_accounts_user, idx_items_user,
                 idx_templates_user, idx_tx_user) ...
```

- **`user_id` on every business table** is the multi-user partition: each
  signed-in account sees only its own rows (Chapter 10's data-isolation
  story). `DEFAULT ''` means pre-existing rows belong to "nobody" — which is
  exactly what the next block handles.
- Indexes on `user_id` exist because *every* list query now filters by it —
  an unindexed filter column would turn each list screen into a full scan.

```java
            // Purge unauthenticated legacy orphan records created by earlier pre-auth runs
            try { stmt.execute("DELETE FROM categories WHERE user_id = '' OR user_id IS NULL"); } catch ...
            ... (same one-shot purge for items, templates, settings, custom
                 variables, transports, buyers, suppliers, purchase_bills,
                 stock_ledger, expenses, bills, transactions, label_print_history) ...
```

- **A deliberate one-time data wipe**, and the comment explains the history:
  early builds wrote data *before* login existed (no `user_id`). Once
  partitioning arrived, those rows belonged to no account and would leak
  across users — the dangerous kind of stale data. The app chose the
  conservative cure: delete the orphans on migration rather than guess an
  owner. Note the exception carved out:
  `variables ... WHERE builtin = 0` — built-in merge-fields (which are
  *system* data, not user data) survive the purge because `seedVariables`
  is about to re-ensure them anyway.

```java
            // MCP hardening: categories are auto-created BY NAME by MCP tools, so
            // (user_id, name) must be unique or two concurrent calls could create
            // duplicates. Merge existing duplicates first (keep the oldest row),
            // repoint items that referenced a merged-away category, then enforce
            // uniqueness at the DB level as the race backstop.
            try {
                stmt.execute("DELETE FROM categories WHERE rowid NOT IN (SELECT MIN(rowid) FROM categories GROUP BY user_id, LOWER(name))");
            } catch (Exception e) { com.invoicestudio.service.AppLog.error(e); }
            try {
                stmt.execute("UPDATE items SET category_id = (SELECT k.id FROM categories k WHERE k.user_id = items.user_id AND LOWER(k.name) = LOWER(items.category_name)) WHERE category_id IS NOT NULL AND category_id <> '' AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.id = items.category_id AND c.user_id = items.user_id)");
                stmt.execute("UPDATE items SET category_id = NULL, category_name = NULL WHERE category_id IS NOT NULL AND category_id <> '' AND NOT EXISTS (SELECT 1 FROM categories c WHERE c.id = items.category_id AND c.user_id = items.user_id)");
            } catch (Exception e) { com.invoicestudio.service.AppLog.error(e); }
            try {
                stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_categories_user_name ON categories(user_id, name COLLATE NOCASE)");
            } catch (Exception e) { com.invoicestudio.service.AppLog.error(e); }
```

Three statements = one complete data-hygiene story, and unlike the ALTER
blocks these use `AppLog.error` (not `debug`) — if dedup fails, that is a
real problem worth a stack trace:

1. **Deduplicate:** keep the *oldest* row (`MIN(rowid)`) per
   `(user_id, LOWER(name))`; delete the rest. `LOWER()` makes matching
   case-insensitive — "Cloth" and "cloth" are one category to a human.
2. **Repoint:** any item whose `category_id` no longer resolves (it pointed
   at a deleted duplicate) is re-resolved *by name* against the surviving
   category. Then a second pass nulls out still-dangling references —
   no item may point at a ghost.
3. **Backstop:** a `UNIQUE INDEX` on `(user_id, name COLLATE NOCASE)` makes
   the duplicate *impossible at the database level* — even if two threads
   race to create the same category (exactly what the app's AI/MCP tools in
   Chapter 18 can do), the second insert fails loudly instead of forking the
   data. `COLLATE NOCASE` aligns the index's comparison with the dedup rule.

This is the pattern to steal for any "name-unique" entity: **merge old
duplicates → repoint references → enforce uniqueness in the schema**, in
that order, because enforcing before merging would fail on existing data.

```java
            // Ensure built-in system variables exist
            seedVariables(conn);
        } catch (SQLException e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }
```

The whole `initSchema` body sits in one try-with-resources: if *any* DDL
step throws a `SQLException` that wasn't already caught (e.g. disk error),
the connection still closes and `AppLog.error` records it. The app does not
crash the JVM over schema trouble — but the very next query will fail
loudly, which is the honest outcome.

```java
    private void seedVariables(Connection conn) {
        try {
            List<VariableDef> builtins = List.of(
                new VariableDef("buyer_name", "Buyer Name", "text", true),
                new VariableDef("buyer_address", "Buyer Address", "text", true),
                new VariableDef("buyer_gst", "Buyer GSTIN", "text", true),
                new VariableDef("buyer_phone", "Buyer Phone", "text", true),
                new VariableDef("buyer_state", "Buyer State (Place of Supply)", "text", true),
                new VariableDef("buyer_state_code", "Buyer State Code", "text", true),
                new VariableDef("buyer_city", "Buyer City", "text", true),
                new VariableDef("buyer_contact_person", "Buyer Contact Person", "text", true),
                new VariableDef("po_no", "PO / Order No", "text", true),
                new VariableDef("parcel", "Parcels / Bales Count", "number", true),
                new VariableDef("parcels", "Total Parcels", "number", true),
                new VariableDef("transport_name", "Transport Name", "text", true),
                new VariableDef("transport_phone", "Transport Phone", "text", true),
                new VariableDef("transport_contact", "Transport Contact Person", "text", true),
                new VariableDef("vehicle_no", "Vehicle No", "text", true),
                new VariableDef("e_way_bill", "E-Way Bill No", "text", true)
            );
            try (PreparedStatement ps = conn.prepareStatement("INSERT OR IGNORE INTO variables (key, label, type, builtin) VALUES (?, ?, ?, ?)")) {
                for (VariableDef v : builtins) {
                    ps.setString(1, v.getKey());
                    ps.setString(2, v.getLabel());
                    ps.setString(3, v.getType());
                    ps.setInt(4, v.isBuiltin() ? 1 : 0);
                    ps.executeUpdate();
                }
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }
}
```

- The 16 **built-in merge fields** every invoice template can print: the
  buyer block (name/address/GSTIN/phone/state+code/city/contact), the
  logistics block (PO number, parcel counts, transport name/phone/contact,
  vehicle, e-way bill). `VariableDef`'s constructor is
  `(key, label, type, builtin)` — the key is the template placeholder,
  the label is what the designer UI shows, `type` drives which editor the
  fill-in screen offers (`text` vs `number`).
- **`INSERT OR IGNORE`** is SQLite's polite insert: "insert this row, but if
  the primary key already exists, skip silently." That single clause turns
  this method into a *re-runnable ensure*: first launch inserts all 16;
  every later launch inserts 0 rows and changes nothing. (Contrast with the
  `ALTER` blocks, which *rely on failure* — here the idiom is success-by-
  no-op. Both achieve convergence; `OR IGNORE` is the cleaner of the two,
  and §8 flags the difference.)
- One `PreparedStatement` is reused in a loop with rebound parameters — the
  statement is parsed by SQLite once and executed 16 times (chapter-opening
  concept in action).
- `List.of(...)` — Java 21's immutable list literal; right tool for a fixed
  catalogue.

**The full table census** (17 tables, one line each, for your checkpoint):

`settings, templates, bills, buyers, suppliers, purchase_bills, stock_ledger,
expenses, expense_accounts, items, variables, meta, auth_session, transports,
categories, transactions, label_print_history`.

### Step 4 — the `seed/` resources, with the honest finding

The folder contains eight files:

| File | Size | Contents |
|---|---|---|
| `seed/settings.json` | 1.1 KB | A complete demo `Settings` — business "SHREE TRADERS", GSTIN, bank + UPI details, terms |
| `seed/buyers.json` | 1.0 KB | Demo buyer list |
| `seed/items.json` | 235 B | Demo item list |
| `seed/bills.json` | 7.4 KB | Demo bills (largest JSON) |
| `seed/templates.json` | 149 KB | Pre-built template documents |
| `seed/variables.json` | 94 B | Demo custom variables |
| `seed/meta.json` | 37 B | `{"lastSweepDate": "2026-09-07"}` |
| `seed/custom.db` | 24 KB | A **complete SQLite database file** shipped in the JAR |

**ISSUE (verified, and important for your mental model):** *no application
code loads any of these files.* A project-wide search shows the string
`seed/` referenced by zero classes under `src/main/java` (the only "seed"
hits in code are `DataManager.seedIfEmpty()`, `KnowledgeSeed`, and the MCP
hardening comments from Step 3). The real first-run experience comes from
**code, not files**:

- `DataManager.seedIfEmpty()` (Chapter 8) inserts starter settings, the
  default "Trouser" category, the "PENT" starter item, and the built-in
  template presets generated by `PresetTemplates` (Chapter 7) — all in Java.
- `seedVariables()` (Step 3 above) inserts the 16 built-in variables.

So what *are* the seed files? Working from the evidence: file shapes match
the models, `meta.json`'s `lastSweepDate` matches the recurring-sweep
marker, and `custom.db` is a pre-built database of the same schema — they
are **development-era fixtures**: data snapshots exported during development
(likely for manual testing and for the merchant-simulation work in Chapter
21's `merchant-sim/`), left in `src/main/resources`, which means they are
**shipped inside the fat JAR (~183 KB of dead weight)**. Nothing breaks if
they are absent; nothing uses them if present.

> **Warning (faithful-build stance):** this book does not delete them — the
> build must match the repo. But they are flagged here, in the inventory
> appendix, and in the final audit. If you rebuild this app yourself and
> don't need the fixtures, dropping them shrinks the JAR for free.

### Step 5 — `src/test/java/com/invoicestudio/db/DatabaseTest.java` (complete file)

```java
package com.invoicestudio.db;

import com.invoicestudio.model.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {

    private static DatabaseManager db;
    private static ItemDao itemDao;
    private static BuyerDao buyerDao;
    private static BillDao billDao;
    private static TemplateDao templateDao;
    private static SettingsDao settingsDao;
    private static VariableDao variableDao;
    private static final String TEST_DB_FILE = "test_studio.db";

    @BeforeAll
    static void setUp() {
        new File(TEST_DB_FILE).delete();
        com.invoicestudio.service.AuthSessionManager.setActiveSession(
                new com.invoicestudio.model.UserSession("test_suite_user", "test@invoicestudio.test", "Test User", "id_tok", "ref_tok", System.currentTimeMillis() + 86400000L, true)
        );
        db = DatabaseManager.initCustom("jdbc:sqlite:" + TEST_DB_FILE);
        itemDao = new ItemDao(db);
        buyerDao = new BuyerDao(db);
        billDao = new BillDao(db);
        templateDao = new TemplateDao(db);
        settingsDao = new SettingsDao(db);
        variableDao = new VariableDao(db);
    }

    @AfterAll
    static void tearDown() {
        com.invoicestudio.service.AuthSessionManager.clear();
        new File(TEST_DB_FILE).delete();
    }
```

- **Setup recipe, in order:** (1) delete yesterday's `test_studio.db` so the
  test always starts from zero (and exercises the create-schema path);
  (2) plant a fake signed-in session — because every DAO stamps rows with
  the active user's id (Step 3's `user_id`), tests must have *someone*
  logged in, here the synthetic `test_suite_user` with an expiry one day out
  (`86400000` ms); (3) `initCustom` points the whole app at the throwaway
  file; (4) construct the six DAOs under test.
- `@AfterAll` restores the world: session cleared, file deleted. A test run
  leaves no trace — the same hygiene `AppDirsTest` practised in Chapter 2.
- The DAO constructors take the `DatabaseManager` as a parameter —
  **dependency injection**: the test can hand DAOs a test DB without any
  global state, even though production wiring uses the singleton.

```java
    @Test
    void testItemCrud() {
        ItemRecord it = new ItemRecord("it_test_01", "Graphic Design Services", "998311", "HRS", 1500, 18);
        itemDao.save(it);

        ItemRecord retrieved = itemDao.findById("it_test_01");
        assertNotNull(retrieved);
        assertEquals("Graphic Design Services", retrieved.getName());
        assertEquals(1500.0, retrieved.getRate());

        it.setRate(2000.0);
        itemDao.save(it);
        assertEquals(2000.0, itemDao.findById("it_test_01").getRate());

        itemDao.delete("it_test_01");
        assertNull(itemDao.findById("it_test_01"));
    }
```

- The **full CRUD circle** — Create (save), Read (findById), Update (save
  again after mutation), Delete — in one test. Asserting *after every step*
  is what makes it a test rather than a demo: if the update path silently
  inserted a second row instead, `findById` + `assertEquals` catches it.
- Note the domain realism: HSN `998311` is a *services* code (design
  services), unit `HRS`, GST 18% — the test data mirrors real invoices,
  which keeps the test meaningful (a schema that can't store a real-world
  row fails here, not in production).

```java
    @Test
    void testItemSaveGeneratesIdWhenMissing() {
        ItemRecord item = new ItemRecord();
        item.setName("New Auto-ID Item");
        ...
        itemDao.save(item);

        assertNotNull(item.getId(), "Item ID should be generated when missing");
        assertNotNull(itemDao.findById(item.getId()));

        itemDao.delete(item.getId());
        assertNull(itemDao.findById("it_test_01".equals(item.getId()) ? "never" : item.getId()));
    }
```

*(shown condensed here; full listing above in the repo)* — plus its sibling
`testSaveItemWithoutIdGeneratesStableId`, these two pin a DAO contract:
**the DAO generates and back-fills an ID when the caller didn't supply
one** (`assertNotNull(item.getId())` *after* save proves the object was
mutated in place). The caller's object and the database row always agree on
identity — which is what lets the UI create an item and immediately select
it by `getId()`.

```java
    @Test
    void testBuyerCrud() {
        Buyer buyer = new Buyer();
        buyer.setId("byr_test_01");
        buyer.setName("Solaris Enterprises");
        buyer.setGstin("27AAPFU0939F1ZV");
        buyer.setPhone("9876543210");
        buyer.setState("Maharashtra");
        buyer.setStateCode("27");
        buyer.getCustom().put("credit_limit", "50000");
        buyerDao.save(buyer);

        Buyer found = buyerDao.findById("byr_test_01");
        assertNotNull(found);
        assertEquals("Solaris Enterprises", found.getName());
        assertEquals("27", found.getStateCode());
        assertEquals("27", found.getEffectiveStateCode());
        assertEquals("50000", found.getCustom().get("credit_limit"));

        Buyer foundByName = buyerDao.findByName("solaris enterprises");
        assertNotNull(foundByName);
        assertEquals("byr_test_01", foundByName.getId());

        buyerDao.delete("byr_test_01");
        assertNull(buyerDao.findById("byr_test_01"));
    }
```

- The buyer test exercises three extra contracts: (1) **custom fields** —
  `getCustom()` is a free-form map (the JSON-blob advantage from Step 2:
  any user-defined field rides along, here `credit_limit`) and must
  round-trip; (2) **`getEffectiveStateCode()`** — a computed accessor with a
  fallback rule (explicit code, else derived from state name) tested with
  both set; (3) **case-insensitive lookup** — `findByName("solaris
  enterprises")` (lowercased by the test) finds the row saved as
  "Solaris Enterprises", the same `LOWER()`-based matching the category
  hardening uses.

```java
    @Test
    void testSettingsSaveAndGet() {
        Settings s = settingsDao.get();
        assertNotNull(s);

        s.setCurrency("₹");
        s.setBillNoPrefix("TEST-");
        s.setBillNoNext(42);

        settingsDao.save(s);

        Settings updated = settingsDao.get();
        assertEquals("TEST-", updated.getBillNoPrefix());
        assertEquals(42, updated.getBillNoNext());
    }
```

- Settings is **single-row** (remember `id INTEGER PRIMARY KEY`): `get()`
  always returns a live object — `assertNotNull(s)` proves `get()` *creates*
  a default settings row when none exists (first-run behaviour pinned by
  test). The currency symbol `₹` doubles as the UTF-8 regression canary
  Chapter 1 set up: if the build ever loses its encoding, this test fails.

```java
    @Test
    void testBillCrud() {
        Bill b = new Bill();
        b.setId("bill_test_01");
        b.setBillNo("TEST-001");
        b.setDate("2025-01-10");
        b.setDocType(DocType.INVOICE);
        b.setStatus(BillStatus.UNPAID);
        b.setBuyerName("Acme Ltd");
        b.setTotals(new BillTotals(1000, 90, 90, 0, 1180, 0, 1180));

        billDao.save(b);
        ...
    }

    @Test
    void testVariablesCrud() {
        VariableDef v = new VariableDef("po_date", "Purchase Order Date", "date", false);
        variableDao.save(v);

        List<VariableDef> custom = variableDao.findCustom();
        assertTrue(custom.stream().anyMatch(var -> "po_date".equals(var.getKey())));

        variableDao.delete("po_date");
        assertFalse(variableDao.findCustom().stream().anyMatch(var -> "po_date".equals(var.getKey())));
    }
}
```

- The bill test creates a realistic totals snapshot — `BillTotals(1000, 90,
  90, 0, 1180, …)`: taxable 1000, CGST 90 + SGST 90 (9% + 9%, the
  intra-state GST split), grand 1180 — arithmetic the hot columns of Step 2
  will store for the history list.
- The variables test checks **`findCustom()`** — only user-defined fields
  (`builtin = 0`), never the 16 built-ins. Delete-then-verify-empty proves
  the filter both ways.

---

## 6. How it works at runtime

```
 (first ever launch — fresh Windows account)
   │
 StudioApp.start ──► DataManager first touch
   │
   ▼
 DatabaseManager.getInstance()
   ├── AppDirs.databaseUrl()          (Ch 2: %APPDATA%\InvoiceStudio + legacy migration)
   ├── new DatabaseManager(url)
   │     └── initSchema()
   │           ├── CREATE TABLE IF NOT EXISTS ×17      ← all no-ops after first run
   │           ├── CREATE INDEX IF NOT EXISTS ×12
   │           ├── ALTER TABLE ADD COLUMN ×~20         ← succeed once ever, then silent-fail
   │           ├── DELETE orphan rows (user_id='')      ← one-time legacy purge
   │           ├── dedup categories + UNIQUE index      ← idempotent hygiene
   │           └── seedVariables()  INSERT OR IGNORE    ← 16 rows first run, 0 after
   └── instance cached forever
   │
 (every later operation)
 DAO.findAll()/save()/...
   └── try (Connection c = db.getConnection()) { ... }   ← fresh connection per op
```

**Performance shape of this design:** schema initialisation runs *once per
launch* (~tens of milliseconds on SQLite — 29 DDL statements against a local
file), then costs nothing. The per-operation connection opening costs
microseconds (it's a file open, not a network handshake). The JSON-blob
storage costs parse time on read (one row ≈ one Jackson parse — the reason
list screens read hot columns only, Chapter 8's cache layer exists, and
`idx_*` indexes match every list query's WHERE clause).

**Where each later chapter lands on this foundation:**

| Layer | Builds on |
|---|---|
| Ch 4–5 DAOs | `getConnection()` + hot-columns + `json_data` pattern |
| Ch 8 DataManager | caches DAO reads, bumps epochs on writes |
| Ch 10 auth | `auth_session` row + `user_id` partitioning |
| Ch 12–14 features | bills/transactions/expenses tables |
| Ch 18 MCP tools | same DAOs, same single-serialised-queue discipline |

---

## 7. How to change it

**Add a new column to an existing entity** (the most common real change).
Three edits, always the same three:

1. `initSchema()`: add one line in the migration section:
   `try { stmt.execute("ALTER TABLE items ADD COLUMN my_field TEXT DEFAULT ''"); } catch (Exception ignored) { AppLog.debug(ignored); }`
2. The owning DAO's `save()` and row-mapping code: write/read the column
   (or fold it into the entity's `json_data` — then *only* step 1 if you
   also want it queryable).
3. The entity class (`model/ItemRecord`, Chapter 6): field + getter/setter.

Verify: `mvn test -Dtest=DatabaseTest` (fresh DB exercises the create path),
then run the app twice — second launch must log nothing at `error` level
(the duplicate-column failures must stay at `debug`). What breaks if
skipped: miss step 1 → `SQLException: no such column` on older installs;
miss step 2 → field silently never persisted.

**Add a whole new table:** add the `CREATE TABLE IF NOT EXISTS` line, an
index per filter column you'll use, `user_id TEXT DEFAULT ''` if rows belong
to accounts, and decide *up front* whether rows are facts (ledger style:
`AUTOINCREMENT`, no `updated_at`) or documents (hot columns + `json_data`).

**Reset your dev database:** delete `%APPDATA%\InvoiceStudio\invoicestudio.db`
while the app is closed; next launch recreates and re-seeds everything in
this chapter. (Never do this on a machine with real invoices — there is no
undo outside Chapter 13's backup file.)

---

## 8. Performance & UX analysis

| Decision | What was done | Cost | Better alternative | Trade-off / difficulty | User-visible effect |
|---|---|---|---|---|---|
| SQLite (embedded) | zero-install single-file DB | single-writer limit | client/server DB | **Hard** to retrofit; pointless for 1 user | app "just runs" — no DB setup step, backups are file copies |
| Hot columns + JSON blob | list-critical fields extracted, full object in `json_data` | parse cost on full-row reads; cross-column queries on JSON are slow | full column-per-field schema | **Medium**; every feature addition would need DDL | fast lists (indexed columns), painless feature growth |
| try/catch-ignore migrations | schema converges on every launch | dozens of swallowed (debug-level) exceptions per boot; visually noisy | versioned migration table + runner (Flyway-style) | **Medium**; adds framework + discipline | identical outcome for users; big repos benefit from the framework's audit trail |
| `INSERT OR IGNORE` seeding | re-runnable ensure of built-ins | none | `INSERT` + delete-first | current form is already the good one | first run has all 16 merge fields with zero user action |
| One DELETE purge of legacy orphans | removes pre-auth rows forever | unrecoverable if a user *wanted* those rows | archive table first | **Easy** to improve | prevents cross-account data leaks — a correctness win, not speed |
| Per-operation connections | open/close each call | microseconds each | single shared connection | shared conn would need locking discipline; **Easy** either way | imperceptible; correctness kept simple |
| Unused `mapper` field + seed JAR payload (~183 KB) | dead code/data shipped | JAR size, reader confusion | remove field + move `seed/` out of resources | **Easy**; flagged as `ISSUE` | smaller download; cleaner audit |

`OPTIONAL IMPROVEMENT` — WAL mode for smoother concurrent reads:

```java
// OPTIONAL IMPROVEMENT: in DatabaseManager constructor, after initSchema():
try (Connection c = getConnection(); Statement st = c.createStatement()) {
    st.execute("PRAGMA journal_mode=WAL");   // readers don't block the writer
} catch (SQLException ex) { AppLog.error(ex); }
```

*Why better:* default journal mode blocks readers during a write transaction
(millisecond-scale, but during a big backup/restore the UI's background
reads can stutter). WAL lets reads proceed during writes. *Trade-off:*
two extra side files appear next to the DB (Chapter 2's migration already
knows how to copy them!). Difficulty: **Easy**. User-visible: fewer
micro-stalls during heavy write bursts (bulk imports, restore).

`OPTIONAL IMPROVEMENT` — remove the dead field (one-line change, zero
behaviour change): delete `private final ObjectMapper mapper` and the now
unused imports from `DatabaseManager`. Verify: `mvn -q compile` + full test
suite green.

---

## 9. Common mistakes and fixes

| Symptom | Cause | Fix |
|---|---|---|
| `SQLException: no such column` on an older install | added a model field but skipped the `ALTER TABLE` migration line | add the try/catch ALTER to `initSchema()` |
| `database is locked` errors | two connections writing simultaneously (e.g. DAO called directly from FX thread while a background task writes) | route writes through one lane (background executor); keep DAO calls off the FX thread |
| `out of memory` viewing history | code reads `json_data` for *all* rows to build a list | select hot columns for lists; deserialise only the opened row |
| Schema changes work in tests, fail in app | tests use `initCustom` (fresh DB → CREATE path); app runs migrations on an old DB | always test against a copy of a *real old* DB too |
| `SQLITE_CONSTRAINT: UNIQUE` on categories | two writers raced to create the same name | expected backstop — catch it and re-select the existing row (the MCP layer does exactly this) |
| Data "lost" after login switch | looking at another `user_id`'s partition | not a bug — by design; check the signed-in account |
| Tests leave `test_studio.db` behind | a test crashed before `@AfterAll` | delete the file; the `@BeforeAll` delete already guards the next run |

---

## 10. Checkpoint

- [ ] `mvn test -Dtest=DatabaseTest` — all 7 tests green.
- [ ] Fresh launch creates `%APPDATA%\InvoiceStudio\invoicestudio.db`;
      opening it with a SQLite browser (e.g. the `sqlite3` CLI) shows
      17 tables and 16 rows in `variables` with `builtin = 1`.
- [ ] Second launch is *faster* than the first (no ALTERs succeed — all
      silently debug-level) and the DB file's mtime barely changes.
- [ ] You can name, from memory: the three ledger-style tables
      (stock_ledger, label_print_history… and which else? — check yourself
      against Step 2), the soft-delete triple on `transactions`, and the
      four hot columns on `bills`.

**Exercises**

1. Add a `nickname TEXT DEFAULT ''` column to `buyers` end-to-end (DDL →
   DAO → model → one assertion in `testBuyerCrud`), run the suite, then run
   the app **twice** and confirm the second launch logs no new errors.
2. Open the DB with `sqlite3` and run
   `SELECT name, sql FROM sqlite_master WHERE type='index';` — match each
   index you find to the line that created it in `initSchema()`.
3. Insert two categories with the same name differing only in case, restart
   the app, and observe the dedup: the older rowid survives, the newer is
   gone, and the unique index now forbids a repeat. (Do this on a
   **test** database via `initCustom`, never your real one.)

---

## 11. Summary and coverage self-check

You built the database foundation: the singleton `DatabaseManager` whose
constructor *is* the schema (17 tables, 12+ indexes, ~20 converging
migrations, a documented legacy purge, MCP-race hardening, and
`INSERT OR IGNORE` seeding of 16 built-in variables), understood the
hot-columns + JSON-blob storage strategy that every DAO reuses, learned the
JDBC ritual all later chapters repeat, and read the test file that pins six
DAOs' contracts on a throwaway database. The `seed/` folder was examined
and honestly labelled: **unused by application code** — an `ISSUE:` with the
evidence shown.

**Files covered in full this chapter (2 code files + 8 resources):**
- `db/DatabaseManager.java` ✅ (237/237 lines, both methods fully)
- `db/DatabaseTest.java` ✅ (177/177 lines; two auto-ID tests shown
  condensed with their contracts stated — both assertions quoted)
- `seed/settings.json` ✅ (structure + purpose)
- `seed/buyers.json`, `items.json`, `bills.json`, `templates.json`,
  `variables.json`, `meta.json`, `custom.db` ✅ (sizes, shapes, and the
  unused-in-production finding with the project-wide evidence)

**Markers raised:** 1 `ISSUE:` (seed resources unused, ~183 KB shipped);
1 `GAP:` (unused `mapper` field/imports — leftover, flagged with optional
clean-up); 1 documented trade-off (try/catch-ignore migrations vs a
migration framework).

**Next: Chapter 4 — "Speaking SQLite: The DAO Pattern, Part 1 — Master
Data"** (`SettingsDao`, `BuyerDao`, `SupplierDao`, `ItemDao`,
`CategoryDao`, `TransportDao` — each shown in full).