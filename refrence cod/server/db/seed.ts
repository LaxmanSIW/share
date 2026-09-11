/**
 * seed.ts
 * -------
 * Populates the database (created by schema.ts) with realistic sample
 * data: 1 company, 2 users, 3 transports, 5 buyers, 3 item categories,
 * 6 items, 3 CC sales (each with a bill + line items), assorted CS
 * sales/payments, CC payments, and one soft-deleted transaction to
 * demonstrate that `deleted=1` rows are excluded from the balance views.
 *
 * Usage:
 *   npm install better-sqlite3
 *   npm install --save-dev @types/better-sqlite3
 *   npx tsx seed.ts
 *
 * Re-running this script is safe — it wipes the relevant tables first
 * (in FK-safe order) and re-inserts, so you always get a clean, known
 * dataset for development/testing.
 */

import { db } from "./engine";

const DB_PATH = process.env.DB_PATH || 'app.db';

// ============================================================
// Reset (FK-safe order: children before parents)
// ============================================================
function clearAllData(): void {
  const tablesInDeleteOrder = [
    'billItems',
    'bills',
    'transactions',
    'items',
    'itemCategories',
    'buyers',
    'transports',
    'users',
    'companies',
    'auditLogs'
  ];
  for (const table of tablesInDeleteOrder) {
    db.exec(`DELETE FROM ${table};`);
  }
  // Reset AUTOINCREMENT counters so IDs start from 1 again on reseed.
  db.exec(`DELETE FROM sqlite_sequence;`);
}

// ============================================================
// Seed
// ============================================================
function seed(): void {
  // ---------- companies (single row) ----------
  db.prepare(
    `INSERT INTO companies
      (companyName, state, stateCode, address, phone, email, gstNumber,
       bankName, accountNumber, ifscCode, branchName, authorizedSignatory,
       terms, startingBillNumber)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  ).run(
    'Alpha Garments Pvt Ltd',
    'Gujarat',
    '24',
    'Plot 12, Textile Market, Ahmedabad',
    '9825000000',
    'accounts@alphagarments.example',
    '24AAAAA0000A1Z5',
    'State Bank of India',
    '000123456789',
    'SBIN0001234',
    'Ahmedabad Main Branch',
    'R. Patel',
    JSON.stringify([
      "1. Goods once sold will not be taken back.",
      "2. Interest @ 18% p.a. will be charged if the payment for Alpha Garments Pvt Ltd is not made within the stipulated time.",
      "3. Subject to 'Ahmedabad' Jurisdiction only."
    ]),
    'INV-0001'
  );

  
  // ---------- transports ----------
  const transport1 = db
    .prepare(`INSERT INTO transports (name, phone, vehicleNumber) VALUES (?, ?, ?)`)
    .run('ABC Logistics', '9898000001', 'GJ01AB1234').lastInsertRowid as number;

  const transport2 = db
    .prepare(`INSERT INTO transports (name, phone, vehicleNumber) VALUES (?, ?, ?)`)
    .run('Speedy Transport', '9898000002', 'GJ05CD5678').lastInsertRowid as number;

  const transport3 = db
    .prepare(`INSERT INTO transports (name, phone, vehicleNumber) VALUES (?, ?, ?)`)
    .run('City Cargo', '9898000003', 'MH12EF9012').lastInsertRowid as number;

  // ---------- buyers ----------
  const buyerStmt = db.prepare(
    `INSERT INTO buyers
      (companyName, contactPerson, phone, gstNumber, creditLimit, riskScore,
       address, city, state, stateCode, defaultTransportId, openingBalance)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
  );

  const buyer1 = buyerStmt.run(
    'XYZ Traders', 'Manoj Kumar', '9812300001', '24ABCDE1234F1Z5',
    50000, 2, 'Ring Road, Ahmedabad', 'Ahmedabad', 'Gujarat', '24',
    transport1, 1000
  ).lastInsertRowid as number;

  const buyer2 = buyerStmt.run(
    'Sharma Garments', 'Vikas Sharma', '9812300002', '27PQRSX5678G1Z9',
    75000, 3, 'Dadar West, Mumbai', 'Mumbai', 'Maharashtra', '27',
    transport3, 0
  ).lastInsertRowid as number;

  const buyer3 = buyerStmt.run(
    'Patel Wholesale', 'Jignesh Patel', '9812300003', '24LMNOP4321H1Z2',
    40000, 1, 'Ring Road, Surat', 'Surat', 'Gujarat', '24',
    transport1, 500
  ).lastInsertRowid as number;

  const buyer4 = buyerStmt.run(
    'Kumar Textiles', 'Anil Kumar', '9812300004', '06XYZAB8765I1Z3',
    30000, 4, 'Chandni Chowk, Delhi', 'Delhi', 'Delhi', '07',
    transport2, 0
  ).lastInsertRowid as number;

  const buyer5 = buyerStmt.run(
    'Global Apparel Co', 'Sunita Rao', '9812300005', null,
    20000, 2, 'Commercial Street, Bangalore', 'Bangalore', 'Karnataka', '29',
    null, 200
  ).lastInsertRowid as number;

  // ---------- item categories ----------
  const catShirts = db.prepare(`INSERT INTO itemCategories (name) VALUES (?)`).run('Shirts')
    .lastInsertRowid as number;
  const catTrousers = db.prepare(`INSERT INTO itemCategories (name) VALUES (?)`).run('Trousers')
    .lastInsertRowid as number;
  const catJackets = db.prepare(`INSERT INTO itemCategories (name) VALUES (?)`).run('Jackets')
    .lastInsertRowid as number;

  // ---------- items ----------
  const itemStmt = db.prepare(
    `INSERT INTO items (categoryId, name, hsnCode, listPrice, unit, taxPercent)
     VALUES (?, ?, ?, ?, ?, ?)`
  );

  const itemPoloShirt = itemStmt.run(catShirts, 'Cotton Polo Shirt', '61051010', 600.0, 'Pcs.', 12.0)
    .lastInsertRowid as number;
  const itemDenimTrousers = itemStmt.run(catTrousers, 'Denim Trousers', '39231020', 800.0, 'Pcs.', 18.0)
    .lastInsertRowid as number;
  const itemFormalShirt = itemStmt.run(catShirts, 'Formal Cotton Shirt', '62052000', 550.0, 'Pcs.', 12.0)
    .lastInsertRowid as number;
  const itemChinoTrousers = itemStmt.run(catTrousers, 'Chino Trousers', '62034200', 750.0, 'Pcs.', 18.0)
    .lastInsertRowid as number;
  const itemDenimJacket = itemStmt.run(catJackets, 'Denim Jacket', '62011000', 1500.0, 'Pcs.', 18.0)
    .lastInsertRowid as number;
  const itemCargoTrousers = itemStmt.run(catTrousers, 'Cargo Trousers', '62034300', 850.0, 'Pcs.', 18.0)
    .lastInsertRowid as number;

  // ---------- helper: create a CC sale transaction + bill + line items ----------
  const txnStmt = db.prepare(
    `INSERT INTO transactions
      (buyerId, bookType, transactionType, transactionDate, dueDate, amount, totalQuantity)
     VALUES (?, 'CC', 'sale', ?, ?, ?, ?)`
  );
  const billStmt = db.prepare(
    `INSERT INTO bills
      (transactionId, billNumber, billDate, dueDate, placeOfSupply, reverseCharge,
       transportId, subtotal, discountAmount, cgstAmount, sgstAmount, igstAmount,
       totalTax, roundOff)
     VALUES (?, ?, ?, ?, ?, 0, ?, ?, 0, ?, ?, ?, ?, 0)`
  );
  const billItemStmt = db.prepare(
    `INSERT INTO billItems (billId, itemId, qty, listPrice, discountPercent, taxPercent, amount)
     VALUES (?, ?, ?, ?, 0, ?, ?)`
  );

  type LineItem = { itemId: number; qty: number; listPrice: number; taxPercent: number };

  function roundMoney(n: number): number {
    return Math.round(n * 100) / 100;
  }

  function createBill(
    buyerId: number,
    billNumber: string,
    billDate: string,
    dueDate: string,
    placeOfSupply: string,
    transportId: number | null,
    intraState: boolean,
    lines: LineItem[]
  ): void {
    const lineTotals = lines.map((l) => {
      const base = roundMoney(l.qty * l.listPrice);
      const tax = roundMoney((base * l.taxPercent) / 100);
      return { ...l, base, tax, amount: roundMoney(base + tax) };
    });

    const subtotal = roundMoney(lineTotals.reduce((sum, l) => sum + l.base, 0));
    const totalTax = roundMoney(lineTotals.reduce((sum, l) => sum + l.tax, 0));
    const totalQuantity = lineTotals.reduce((sum, l) => sum + l.qty, 0);
    const totalAmount = roundMoney(subtotal + totalTax);

    const cgstAmount = intraState ? roundMoney(totalTax / 2) : 0;
    const sgstAmount = intraState ? roundMoney(totalTax / 2) : 0;
    const igstAmount = intraState ? 0 : totalTax;

    const txnId = txnStmt.run(buyerId, billDate, dueDate, totalAmount, totalQuantity)
      .lastInsertRowid as number;

    const billId = billStmt.run(
      txnId, billNumber, billDate, dueDate, placeOfSupply,
      transportId, subtotal, cgstAmount, sgstAmount, igstAmount, totalTax
    ).lastInsertRowid as number;

    for (const l of lineTotals) {
      billItemStmt.run(billId, l.itemId, l.qty, l.listPrice, l.taxPercent, l.amount);
    }
  }

  // Bill 1 — XYZ Traders (Gujarat -> intra-state, CGST+SGST)
  createBill(
    buyer1, 'INV-0001', '2026-07-28', '2026-08-27', 'Gujarat', transport1, true,
    [
      { itemId: itemPoloShirt, qty: 2, listPrice: 600.0, taxPercent: 12.0 },
      { itemId: itemDenimTrousers, qty: 1, listPrice: 800.0, taxPercent: 18.0 }
    ]
  );

  // Bill 2 — Sharma Garments (Maharashtra -> inter-state, IGST)
  createBill(
    buyer2, 'INV-0002', '2026-07-29', '2026-08-28', 'Maharashtra', transport3, false,
    [
      { itemId: itemDenimJacket, qty: 1, listPrice: 1500.0, taxPercent: 18.0 },
      { itemId: itemChinoTrousers, qty: 2, listPrice: 750.0, taxPercent: 18.0 }
    ]
  );

  // Bill 3 — Patel Wholesale (Gujarat -> intra-state, CGST+SGST)
  createBill(
    buyer3, 'INV-0003', '2026-07-30', '2026-08-29', 'Gujarat', transport1, true,
    [
      { itemId: itemFormalShirt, qty: 3, listPrice: 550.0, taxPercent: 12.0 },
      { itemId: itemCargoTrousers, qty: 1, listPrice: 850.0, taxPercent: 18.0 }
    ]
  );

  // ---------- plain transactions (no bill): CS sales, CS/CC payments ----------
  const plainTxnStmt = db.prepare(
    `INSERT INTO transactions
      (buyerId, bookType, transactionType, transactionDate, amount, checkNumber, totalQuantity)
     VALUES (?, ?, ?, ?, ?, ?, ?)`
  );

  plainTxnStmt.run(buyer1, 'CS', 'sale', '2026-07-31', 500.0, null, 1);
  plainTxnStmt.run(buyer1, 'CC', 'payment', '2026-08-01', 1000.0, 'CHK-1001', null);

  plainTxnStmt.run(buyer2, 'CS', 'sale', '2026-07-25', 1200.0, null, 3);
  plainTxnStmt.run(buyer2, 'CS', 'payment', '2026-07-27', 600.0, null, null);

  plainTxnStmt.run(buyer3, 'CC', 'payment', '2026-07-31', 500.0, 'CHK-1002', null);

  plainTxnStmt.run(buyer4, 'CS', 'sale', '2026-07-20', 900.0, null, 2);

  plainTxnStmt.run(buyer5, 'CS', 'sale', '2026-07-22', 300.0, null, 1);
  plainTxnStmt.run(buyer5, 'CS', 'payment', '2026-07-26', 300.0, null, null);

  // ---------- one soft-deleted transaction, to demonstrate that
  // deleted=1 rows are excluded from the balance views ----------
  const deletedTxnId = plainTxnStmt.run(buyer4, 'CS', 'sale', '2026-07-21', 100.0, null, 1)
    .lastInsertRowid as number;
  db.prepare(
    `UPDATE transactions
       SET deleted = 1, deletedReason = ?, deletedAt = datetime('now')
     WHERE id = ?`
  ).run('Entered by mistake — duplicate of correct entry', deletedTxnId);
}

// ============================================================
// Run
// ============================================================
const runSeed = db.transaction(() => {
  clearAllData();
  seed();
});


export function seedDatabase(){

runSeed();

console.log(`Seed complete -> ${DB_PATH}`);
// NOTE: db.close() was removed here on purpose. `db` (from engine.ts) is
// a single shared connection used by the whole app for its entire
// lifetime (findAll/insert/update/etc. all use it). Closing it after
// seeding broke every query made after startup. better-sqlite3 is
// synchronous and doesn't keep the process alive on its own, so if you
// run this file standalone (`npx tsx seed.ts`), the process will still
// exit cleanly on its own once the script finishes — you don't need an
// explicit close() for that.
}
