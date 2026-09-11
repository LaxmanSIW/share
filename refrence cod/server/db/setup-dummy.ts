import { db } from "./index";

export function setupDummyData() {
  const dummyBuyer = db.prepare(`INSERT OR IGNORE INTO buyers (id, companyName) VALUES (0, 'Dummy Buyer')`).run();
  
  const dummyTxn = db.prepare(`
    INSERT OR IGNORE INTO transactions (id, buyerId, bookType, transactionType, transactionDate, amount, totalQuantity, includeInReporting, deleted)
    VALUES (0, 0, 'CS', 'sale', '2000-01-01', 0, 0, 0, 1)
  `).run();

  const dummyBill = db.prepare(`
    INSERT OR IGNORE INTO bills (id, transactionId, billNumber, billDate, subtotal, discountAmount, cgstAmount, sgstAmount, igstAmount, totalTax, roundOff)
    VALUES (0, 0, 'DUMMY-0', '2000-01-01', 0, 0, 0, 0, 0, 0, 0)
  `).run();

  const dummyCat = db.prepare(`INSERT OR IGNORE INTO itemCategories (id, name) VALUES (0, 'Dummy Category')`).run();

  const dummyItem = db.prepare(`
    INSERT OR IGNORE INTO items (id, categoryId, name, hsnCode, listPrice, unit, taxPercent)
    VALUES (0, 0, 'Trousers', '0000', 0, 'Pcs.', 0)
  `).run();
  
  console.log("Dummy data setup complete");
}
