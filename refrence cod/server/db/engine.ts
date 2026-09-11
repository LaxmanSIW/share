import fs from "fs";
import path from "path";
import Database from "better-sqlite3";


export interface ExecutableDb {
  exec(sql: string): unknown;
}


const DATA_DIR = path.resolve(process.cwd(), "data");
if (!fs.existsSync(DATA_DIR)) {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

export const db = new Database(path.join(DATA_DIR, "sqlite.db"));




const SCHEMA_SQL = `
-- ============================================================
-- PRAGMAs
-- ============================================================
PRAGMA foreign_keys = ON;

-- ============================================================
-- companies  (single-row: your business's own letterhead/bank info,
-- used to print on bills)
-- ============================================================
CREATE TABLE IF NOT EXISTS companies (
  id                  INTEGER PRIMARY KEY AUTOINCREMENT,
  companyName         TEXT NOT NULL,
  state               TEXT,
  stateCode           TEXT,
  address             TEXT,
  phone               TEXT,
  email               TEXT,
  gstNumber           TEXT,
  bankName            TEXT,
  accountNumber       TEXT,
  ifscCode            TEXT,
  branchName          TEXT,
  authorizedSignatory TEXT,
  terms               TEXT,
  startingBillNumber  TEXT,
  createdAt           TEXT NOT NULL DEFAULT (datetime('now')),
  updatedAt           TEXT NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================
-- users
-- ============================================================
CREATE TABLE IF NOT EXISTS users (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  unionId      TEXT,
  username     TEXT NOT NULL UNIQUE,
  password     TEXT NOT NULL,
  name         TEXT,
  email        TEXT,
  avatar       TEXT,
  role         TEXT,
  createdAt    TEXT NOT NULL DEFAULT (datetime('now')),
  updatedAt    TEXT NOT NULL DEFAULT (datetime('now')),
  lastSignInAt TEXT
);

-- ============================================================
-- transports
-- ============================================================
CREATE TABLE IF NOT EXISTS transports (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  name          TEXT NOT NULL,
  phone         TEXT,
  vehicleNumber TEXT,
  createdAt     TEXT NOT NULL DEFAULT (datetime('now')),
  updatedAt     TEXT NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================
-- buyers
-- Removed: defaultTransportName (redundant w/ defaultTransportId FK)
-- ============================================================
CREATE TABLE IF NOT EXISTS buyers (
  id                    INTEGER PRIMARY KEY AUTOINCREMENT,
  companyName           TEXT NOT NULL,
  contactPerson         TEXT,
  phone                 TEXT,
  gstNumber             TEXT,
  creditLimit           REAL NOT NULL DEFAULT 0,
  riskScore             REAL,
  address               TEXT,
  city                  TEXT,
  state                 TEXT,
  stateCode             TEXT,
  defaultTransportId    INTEGER REFERENCES transports(id) ON DELETE SET NULL,
  openingBalance        REAL NOT NULL DEFAULT 0,
  createdAt             TEXT NOT NULL DEFAULT (datetime('now')),
  updatedAt             TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_buyers_defaultTransportId ON buyers(defaultTransportId);

-- ============================================================
-- itemCategories  (new — for category-wise matrix reporting)
-- ============================================================
CREATE TABLE IF NOT EXISTS itemCategories (
  id        INTEGER PRIMARY KEY AUTOINCREMENT,
  name      TEXT NOT NULL UNIQUE,
  createdAt TEXT NOT NULL DEFAULT (datetime('now'))
);

-- ============================================================
-- items  (master price list)
-- Added: categoryId. Changed listPrice/taxPercent TEXT -> REAL.
-- ============================================================
CREATE TABLE IF NOT EXISTS items (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  categoryId  INTEGER REFERENCES itemCategories(id) ON DELETE SET NULL,
  name        TEXT NOT NULL,
  hsnCode     TEXT,
  listPrice   REAL NOT NULL DEFAULT 0,
  unit        TEXT NOT NULL DEFAULT 'Pcs.',
  taxPercent  REAL NOT NULL DEFAULT 0,
  createdAt   TEXT NOT NULL DEFAULT (datetime('now')),
  updatedAt   TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_items_categoryId ON items(categoryId);

-- ============================================================
-- transactions  (the ledger — every sale and every payment,
-- both cash-book and check-book)
-- Removed: items (JSON blob, replaced by billItems),
--          billId (redundant — see bills.transactionId),
--          billNumber (redundant — join via bills)
-- Renamed: totalQuantity -> totalQuantity
-- ============================================================
CREATE TABLE IF NOT EXISTS transactions (
  id                  INTEGER PRIMARY KEY AUTOINCREMENT,
  buyerId             INTEGER NOT NULL REFERENCES buyers(id) ON DELETE RESTRICT,
  bookType            TEXT NOT NULL CHECK (bookType IN ('CS', 'CC')),
  transactionType     TEXT NOT NULL CHECK (transactionType IN ('sale', 'payment')),
  transactionDate     TEXT NOT NULL,
  dueDate             TEXT,
  amount              REAL NOT NULL,
  totalQuantity       INTEGER,
  -- checkNumber only meaningful when bookType = ''
  checkNumber         TEXT,
  includeInReporting  INTEGER NOT NULL DEFAULT 1 CHECK (includeInReporting IN (0, 1)),
  parcel              INTEGER,
  deleted             INTEGER NOT NULL DEFAULT 0 CHECK (deleted IN (0, 1)),
  deletedReason       TEXT,
  deletedAt           TEXT,
  createdAt           TEXT NOT NULL DEFAULT (datetime('now')),
  updatedAt           TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_transactions_buyerId ON transactions(buyerId);
CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(transactionDate);
CREATE INDEX IF NOT EXISTS idx_transactions_bookType_type ON transactions(bookType, transactionType);
CREATE INDEX IF NOT EXISTS idx_transactions_deleted ON transactions(deleted);

-- ============================================================
-- bills  (extends a transaction 1:1 — only exists for
-- bookType='CC' AND transactionType='sale' transactions)
-- Removed: buyerId (derive via transactionId -> transactions.buyerId),
--          buyerName/buyerGst/buyerAddress/buyerPhone/buyerEmail (use buyers via join),
--          transportName (use transports via join)
-- Changed: reverseCharge TEXT -> INTEGER (0/1);
--          money fields TEXT -> REAL
-- ============================================================
CREATE TABLE IF NOT EXISTS bills (
  id              INTEGER PRIMARY KEY AUTOINCREMENT,
  transactionId   INTEGER NOT NULL UNIQUE REFERENCES transactions(id) ON DELETE CASCADE,
  billNumber      TEXT NOT NULL UNIQUE,
  billDate        TEXT NOT NULL,
  dueDate         TEXT,
  placeOfSupply   TEXT,
  reverseCharge   INTEGER NOT NULL DEFAULT 0 CHECK (reverseCharge IN (0, 1)),
  transportId     INTEGER REFERENCES transports(id) ON DELETE SET NULL,
  subtotal        REAL NOT NULL DEFAULT 0,
  discountAmount  REAL NOT NULL DEFAULT 0,
  cgstAmount      REAL NOT NULL DEFAULT 0,
  sgstAmount      REAL NOT NULL DEFAULT 0,
  igstAmount      REAL NOT NULL DEFAULT 0,
  totalTax        REAL NOT NULL DEFAULT 0,
  roundOff        REAL NOT NULL DEFAULT 0,
  createdAt       TEXT NOT NULL DEFAULT (datetime('now')),
  updatedAt       TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_bills_date ON bills(billDate);
CREATE INDEX IF NOT EXISTS idx_bills_transportId ON bills(transportId);

-- ============================================================
-- billItems  (new — replaces the old JSON 'items' column on bills)
-- Snapshots the transactional fields (qty/price/discount/tax/amount)
-- for invoice-history accuracy; name/hsnCode/category are joined
-- live from 'items' for reporting.
-- ============================================================
CREATE TABLE IF NOT EXISTS billItems (
  id               INTEGER PRIMARY KEY AUTOINCREMENT,
  billId           INTEGER NOT NULL REFERENCES bills(id) ON DELETE CASCADE,
  itemId           INTEGER NOT NULL REFERENCES items(id) ON DELETE RESTRICT,
  qty              INTEGER NOT NULL DEFAULT 1,
  listPrice        REAL NOT NULL,
  discountPercent  REAL NOT NULL DEFAULT 0,
  taxPercent       REAL NOT NULL DEFAULT 0,
  amount           REAL NOT NULL,
  createdAt        TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_billItems_billId ON billItems(billId);
CREATE INDEX IF NOT EXISTS idx_billItems_itemId ON billItems(itemId);

-- ============================================================
-- auditLogs  (unchanged — already a reasonable design;
-- oldValues/newValues are intentionally free-form JSON snapshots)
-- ============================================================
CREATE TABLE IF NOT EXISTS auditLogs (
  id         INTEGER PRIMARY KEY AUTOINCREMENT,
  tableName  TEXT,
  recordId   INTEGER,
  action     TEXT,
  oldValues  TEXT,
  newValues  TEXT,
  reason     TEXT,
  userId     TEXT,
  createdAt  TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_auditLogs_table_record ON auditLogs(tableName, recordId);

-- ============================================================
-- Views
-- ============================================================

-- Live-computed buyer balance. No stored column, so it can never
-- drift out of sync with the ledger.
CREATE VIEW IF NOT EXISTS buyerBalances AS
SELECT
  b.id AS buyerId,
  b.companyName,
  b.openingBalance
    + COALESCE(SUM(
        CASE
          WHEN t.transactionType = 'sale'    THEN  t.amount
          WHEN t.transactionType = 'payment' THEN -t.amount
          ELSE 0
        END
      ), 0) AS currentBalance
FROM buyers b
LEFT JOIN transactions t
  ON t.buyerId = b.id AND t.deleted = 0
GROUP BY b.id;

-- Same, split out by book (cash vs check) since you run two ledgers.
CREATE VIEW IF NOT EXISTS buyerBookBalances AS
SELECT
  t.buyerId,
  t.bookType,
  COALESCE(SUM(
    CASE
      WHEN t.transactionType = 'sale'    THEN  t.amount
      WHEN t.transactionType = 'payment' THEN -t.amount
      ELSE 0
    END
  ), 0) AS bookBalance
FROM transactions t
WHERE t.deleted = 0
GROUP BY t.buyerId, t.bookType;

-- One-stop view for printing/reporting on a bill: pulls buyer and
-- transport details live via FK, instead of storing them redundantly.
CREATE VIEW IF NOT EXISTS billDetails AS
SELECT
  bl.id               AS billId,
  bl.billNumber,
  bl.billDate,
  bl.dueDate,
  bl.placeOfSupply,
  bl.reverseCharge,
  bl.subtotal,
  bl.discountAmount,
  bl.cgstAmount,
  bl.sgstAmount,
  bl.igstAmount,
  bl.totalTax,
  bl.roundOff,
  t.id                AS transactionId,
  t.transactionDate,
  t.amount            AS transactionAmount,
  bu.id               AS buyerId,
  bu.companyName      AS buyerName,
  bu.gstNumber        AS buyerGst,
  bu.address          AS buyerAddress,
  bu.phone            AS buyerPhone,
  tr.id               AS transportId,
  tr.name             AS transportName,
  tr.vehicleNumber    AS transportVehicleNumber
FROM bills bl
JOIN transactions t ON t.id = bl.transactionId
JOIN buyers bu       ON bu.id = t.buyerId
LEFT JOIN transports tr ON tr.id = bl.transportId;

-- Line-item detail joined with item master + category, for
-- item-wise / category-wise matrix reporting.
CREATE VIEW IF NOT EXISTS billItemDetails AS
SELECT
  bi.id               AS billItemId,
  bi.billId,
  bi.qty,
  bi.listPrice,
  bi.discountPercent,
  bi.taxPercent,
  bi.amount,
  i.id                AS itemId,
  i.name              AS itemName,
  i.hsnCode,
  i.unit,
  ic.id               AS categoryId,
  ic.name             AS categoryName
FROM billItems bi
JOIN items i ON i.id = bi.itemId
LEFT JOIN itemCategories ic ON ic.id = i.categoryId;

-- ============================================================
-- Triggers: keep updatedAt current automatically
-- ============================================================
CREATE TRIGGER IF NOT EXISTS trg_companies_updatedAt
AFTER UPDATE ON companies
BEGIN
  UPDATE companies SET updatedAt = datetime('now') WHERE id = NEW.id;
END;

CREATE TRIGGER IF NOT EXISTS trg_users_updatedAt
AFTER UPDATE ON users
BEGIN
  UPDATE users SET updatedAt = datetime('now') WHERE id = NEW.id;
END;

CREATE TRIGGER IF NOT EXISTS trg_transports_updatedAt
AFTER UPDATE ON transports
BEGIN
  UPDATE transports SET updatedAt = datetime('now') WHERE id = NEW.id;
END;

CREATE TRIGGER IF NOT EXISTS trg_buyers_updatedAt
AFTER UPDATE ON buyers
BEGIN
  UPDATE buyers SET updatedAt = datetime('now') WHERE id = NEW.id;
END;

CREATE TRIGGER IF NOT EXISTS trg_items_updatedAt
AFTER UPDATE ON items
BEGIN
  UPDATE items SET updatedAt = datetime('now') WHERE id = NEW.id;
END;

CREATE TRIGGER IF NOT EXISTS trg_transactions_updatedAt
AFTER UPDATE ON transactions
BEGIN
  UPDATE transactions SET updatedAt = datetime('now') WHERE id = NEW.id;
END;

CREATE TRIGGER IF NOT EXISTS trg_bills_updatedAt
AFTER UPDATE ON bills
BEGIN
  UPDATE bills SET updatedAt = datetime('now') WHERE id = NEW.id;
END;
`;

/**
 * Creates every table, index, view, and trigger if it doesn't already
 * exist. Safe to call on every app startup.
 */

// Initialize Schema
db.exec(SCHEMA_SQL);

// Ensure default "Trousers" category & item exist and are linked
(function initDefaultTrousersCategoryAndItem() {
  try {
    let cat = db.prepare(`SELECT id FROM itemCategories WHERE name = 'Trousers' LIMIT 1`).get() as any;
    if (!cat) {
      const info = db.prepare(`INSERT INTO itemCategories (name) VALUES ('Trousers')`).run();
      cat = { id: info.lastInsertRowid };
    }

    let item = db.prepare(`SELECT id, categoryId FROM items WHERE name = 'Trousers' LIMIT 1`).get() as any;
    if (!item) {
      db.prepare(
        `INSERT INTO items (categoryId, name, hsnCode, listPrice, unit, taxPercent)
         VALUES (?, 'Trousers', '62034200', 0, 'Pcs.', 18)`
      ).run(cat.id);
    } else if (!item.categoryId) {
      db.prepare(`UPDATE items SET categoryId = ? WHERE id = ?`).run(cat.id, item.id);
    }
  } catch (err) {
    console.error("Error initializing default Trousers category/item:", err);
  }
})();

// ─── Generic CRUD Operations ─────────────────────────────
type TableName =
  | "users"
  | "buyers"
  | "transactions"
  | "auditLogs"
  | "items"
  | "itemCategories"
  | "billItems"
  | "bills"
  | "companies"
  | "transports";

function rowToObj(row: any): any {
  if (!row) return row;
  const obj = { ...row };

  // Convert boolean fields (stored as INTEGER 0/1 in SQLite)
  if ("includeInReporting" in obj)
    obj.includeInReporting = obj.includeInReporting === 1;
  if ("deleted" in obj) obj.deleted = obj.deleted === 1;
  if ("reverseCharge" in obj) obj.reverseCharge = obj.reverseCharge === 1;

  // Convert JSON text fields
  // NOTE: transactions.items no longer exists (replaced by the billItems
  // table), so that conversion was removed.
  if (obj.terms && typeof obj.terms === "string") {
    try {
      obj.terms = JSON.parse(obj.terms);
    } catch {}
  }
  if (obj.oldValues && typeof obj.oldValues === "string") {
    try {
      obj.oldValues = JSON.parse(obj.oldValues);
    } catch {}
  }
  if (obj.newValues && typeof obj.newValues === "string") {
    try {
      obj.newValues = JSON.parse(obj.newValues);
    } catch {}
  }

  return obj;
}

export function findAll<T extends { id: number }>(table: TableName): T[] {
  const rows = db.prepare(`SELECT * FROM ${table}`).all();
  return rows.map(rowToObj) as T[];
}

export function findById<T extends { id: number }>(
  table: TableName,
  id: number,
): T | undefined {
  const row = db.prepare(`SELECT * FROM ${table} WHERE id = ?`).get(id);
  return rowToObj(row) as T | undefined;
}

export function findOne<T>(
  table: TableName,
  predicate: (row: T) => boolean,
): T | undefined {
  const rows = findAll<T & { id: number }>(table);
  return rows.find(predicate) as T | undefined;
}

export function findMany<T>(
  table: TableName,
  predicate: (row: T) => boolean,
): T[] {
  const rows = findAll<T & { id: number }>(table);
  return rows.filter(predicate) as T[];
}

export function insert<T extends Record<string, any>>(
  table: TableName,
  data: Omit<T, "id">,
): T {
  const keys = Object.keys(data);
  const values = keys.map((k) => {
    const val = (data as any)[k];
    if (typeof val === "boolean") return val ? 1 : 0;
    if (typeof val === "object" && val !== null) return JSON.stringify(val);
    return val;
  });

  const stmt = db.prepare(
    `INSERT INTO ${table} (${keys.join(", ")}) VALUES (${keys.map(() => "?").join(", ")})`,
  );
  const info = stmt.run(...values);

  return { ...data, id: info.lastInsertRowid } as unknown as T;
}

export function update<T extends { id: number }>(
  table: TableName,
  id: number,
  data: Partial<T>,
): T | undefined {
  const keys = Object.keys(data);
  if (keys.length === 0) return findById(table, id);

  const values = keys.map((k) => {
    const val = (data as any)[k];
    if (typeof val === "boolean") return val ? 1 : 0;
    if (typeof val === "object" && val !== null) return JSON.stringify(val);
    return val;
  });

  const setClause = keys.map((k) => `${k} = ?`).join(", ");
  const stmt = db.prepare(`UPDATE ${table} SET ${setClause} WHERE id = ?`);
  stmt.run(...values, id);

  return findById(table, id);
}

export function remove<_T = any>(
  table: TableName,
  id: number,
): boolean {
  const info = db.prepare(`DELETE FROM ${table} WHERE id = ?`).run(id);
  return info.changes > 0;
}

export function count(
  table: TableName,
  predicate?: (row: any) => boolean,
): number {
  if (!predicate) {
    const row = db.prepare(`SELECT COUNT(*) as c FROM ${table}`).get() as {
      c: number;
    };
    return row.c;
  }
  const rows = findAll<any>(table);
  return rows.filter(predicate).length;
}

export function resetNextId(_table?: TableName): number {
  return 0; // Not needed for SQLite AUTOINCREMENT
}

/**
 * Convenience helper for the new 3-table bill write (transaction + bill +
 * line items). Wrap this in db.transaction(...) at the call site, or use
 * createBillWithItems() below which already does that for you.
 */
export interface BillLineItemInput {
  itemId: number;
  qty: number;
  listPrice: number;
  discountPercent?: number;
  taxPercent: number;
  amount: number;
}

export function createBillWithItems(
  transactionData: Record<string, any>,
  billData: Record<string, any>,
  lineItems: BillLineItemInput[],
): { transaction: any; bill: any } {
  const run = db.transaction(() => {
    const transaction = insert(
      "transactions",
      transactionData as any,
    );
    const bill = insert("bills", {
      ...billData,
      transactionId: transaction.id,
    } as any);
    for (const line of lineItems) {
      insert("billItems", { ...line, billId: bill.id } as any);
    }
    return { transaction, bill };
  });

  return run() as { transaction: any; bill: any };
}

export function initializeSchema(db: ExecutableDb): void {
  db.exec(SCHEMA_SQL);
}


