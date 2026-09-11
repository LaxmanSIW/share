import { seedDatabase } from "../db/seed";
import { db } from "../db/engine";

// Only seed demo data on a genuinely empty database (first run ever).
// Without this guard, seedDatabase() runs on EVERY app start, and it
// begins by wiping every table (clearAllData()) before inserting the
// fixed demo dataset — meaning every restart would erase all real
// buyers/transactions/users you'd entered and replace them with sample
// data. This check makes it a one-time thing.
const companyCount = (
  db.prepare(`SELECT COUNT(*) as c FROM companies`).get() as { c: number }
).c;

if (companyCount === 0) {
  seedDatabase();
}

// ─── Seed system records AFTER demo seed (idempotent) ─────
// These must come after seed because seed wipes all tables.
db.exec(`
PRAGMA foreign_keys = OFF;

-- Dummy buyer for system records (id=0)
INSERT OR IGNORE INTO buyers (id, companyName, contactPerson, phone, gstNumber, creditLimit, openingBalance, createdAt, updatedAt)
VALUES (0, '__SYSTEM__', NULL, NULL, NULL, 0, 0, '2000-01-01 00:00:00', '2000-01-01 00:00:00');

-- Dummy transaction for system records (id=0, deleted=1 so it never affects reporting)
INSERT OR IGNORE INTO transactions (id, buyerId, bookType, transactionType, transactionDate, amount, includeInReporting, deleted, createdAt, updatedAt)
VALUES (0, 0, 'CS', 'sale', '2000-01-01', 0, 0, 1, '2000-01-01 00:00:00', '2000-01-01 00:00:00');

-- Dummy bill for system records (id=0)
INSERT OR IGNORE INTO bills (id, transactionId, billNumber, billDate, subtotal, discountAmount, cgstAmount, sgstAmount, igstAmount, totalTax, roundOff, createdAt, updatedAt)
VALUES (0, 0, 'SYS-DUMMY', '2000-01-01', 0, 0, 0, 0, 0, 0, 0, '2000-01-01 00:00:00', '2000-01-01 00:00:00');

PRAGMA foreign_keys = ON;
`);

// Ensure default "Trousers" item exists (if seed didn't create it)
db.exec(`
INSERT OR IGNORE INTO items (name, hsnCode, listPrice, unit, taxPercent, createdAt, updatedAt)
VALUES ('Trousers', '62034200', 0, 'Pcs.', 0, datetime('now'), datetime('now'))
`);

// Re-export all JSON DB operations
export {
  findAll,
  findById,
  findOne,
  findMany,
  insert,
  update,
  remove,
  count,
  resetNextId,
} from "../db/engine";

export type {
  User,
  Buyer,
  Transaction,
  AuditLog,
  Item,
  Bill,
  Company,
  Transport,
  InsertUser,
  InsertBuyer,
  InsertTransaction,
  InsertAuditLog,
  InsertItem,
  InsertBill,
  InsertCompany,
  InsertTransport,
  BillItem,
} from "../db/types";
