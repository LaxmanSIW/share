import { z } from "zod";
import { createRouter, adminQuery } from "../middleware";
import { db } from "../db/engine";
import { findAll, findById, insert } from "../queries/connection";
import type { Bill, Company, Buyer } from "../queries/connection";

// Helper to auto-generate bill number
function generateNextBillNumber(): string {
  const bills = db
    .prepare(`SELECT billNumber FROM bills WHERE id > 0 ORDER BY id DESC LIMIT 1`)
    .get() as any;
  const companies = findAll<Company>("companies");
  const company = companies[0];
  const startingStr = company?.startingBillNumber || "0001";

  const matchStart = startingStr.match(/^(\d+)/);
  const startNum = matchStart ? parseInt(matchStart[1], 10) : 1;
  const padLen = startingStr.length;

  if (!bills) {
    return String(startNum).padStart(padLen, "0");
  }

  const matchDigits = bills.billNumber.match(/^(\d+)/);
  if (matchDigits) {
    const lastSeq = parseInt(matchDigits[1], 10);
    const nextSeq = Math.max(lastSeq + 1, startNum);
    return String(nextSeq).padStart(padLen, "0");
  }

  return String(startNum).padStart(padLen, "0");
}

// Enrich a bill row with buyer info and computed totalAmount (frontend expects these)
function enrichBill(bill: any): any {
  const buyer = bill.buyerId
    ? (db.prepare(`SELECT companyName, gstNumber, address, phone FROM buyers WHERE id = ?`).get(bill.buyerId) as any)
    : null;
  const transport = bill.transportId
    ? (db.prepare(`SELECT name, vehicleNumber, phone FROM transports WHERE id = ?`).get(bill.transportId) as any)
    : null;
  const txn = db.prepare(`SELECT parcel FROM transactions WHERE id = ?`).get(bill.transactionId) as any;
  const billItems = db.prepare(`SELECT * FROM billItems WHERE billId = ?`).all(bill.id) as any[];

  // Attach item details to each billItem
  for (const bi of billItems) {
    const item = db.prepare(`SELECT name, hsnCode, unit FROM items WHERE id = ?`).get(bi.itemId) as any;
    bi.name = item?.name || "Unknown";
    bi.hsnCode = item?.hsnCode || "";
    bi.unit = item?.unit || "Pcs.";
  }

  const totalAmount = (bill.subtotal || 0) + (bill.totalTax || 0) + (bill.roundOff || 0);

  return {
    ...bill,
    buyerName: buyer?.companyName || "Unknown",
    buyerGst: buyer?.gstNumber || null,
    buyerAddress: buyer?.address || null,
    buyerPhone: buyer?.phone || null,
    buyerEmail: null,
    transportName: transport?.name || "N/A",
    totalAmount,
    parcel: txn?.parcel || 1,
    items: billItems,
    reverseCharge: bill.reverseCharge === 1 || bill.reverseCharge === true ? "Yes" : "No",
  };
}

export const billRouter = createRouter({
  getNextBillNumber: adminQuery.query(() => {
    return { nextBillNumber: generateNextBillNumber() };
  }),

  list: adminQuery
    .input(
      z
        .object({
          search: z.string().optional(),
          sortBy: z.string().optional(),
          sortOrder: z.enum(["asc", "desc"]).default("desc"),
        })
        .optional(),
    )
    .query(async ({ input }) => {
      // Use the billDetails view which already joins bill+transaction+buyer+transport
      let rows = db.prepare(`SELECT * FROM billDetails ORDER BY billId DESC`).all() as any[];

      if (input?.search) {
        const searchLower = input.search.toLowerCase();
        rows = rows.filter(
          (r) =>
            r.billNumber.toLowerCase().includes(searchLower) ||
            (r.buyerName && r.buyerName.toLowerCase().includes(searchLower)) ||
            (r.buyerGst && r.buyerGst.toLowerCase().includes(searchLower)),
        );
      }

      // Enrich each bill with items, totalAmount, parcel
      const bills = rows.map((row) => {
        const billItems = db.prepare(`SELECT * FROM billItems WHERE billId = ?`).all(row.billId) as any[];
        for (const bi of billItems) {
          const item = db.prepare(`SELECT name, hsnCode, unit FROM items WHERE id = ?`).get(bi.itemId) as any;
          bi.name = item?.name || "Unknown";
          bi.hsnCode = item?.hsnCode || "";
          bi.unit = item?.unit || "Pcs.";
        }
        const txn = db.prepare(`SELECT parcel FROM transactions WHERE id = ?`).get(row.transactionId) as any;
        const totalAmount = (row.subtotal || 0) + (row.totalTax || 0) + (row.roundOff || 0);
        return {
          id: row.billId,
          billNumber: row.billNumber,
          billDate: row.billDate,
          dueDate: row.dueDate,
          placeOfSupply: row.placeOfSupply,
          reverseCharge: row.reverseCharge ? "Yes" : "No",
          transportId: row.transportId,
          transportName: row.transportName,
          subtotal: row.subtotal,
          discountAmount: row.discountAmount,
          cgstAmount: row.cgstAmount,
          sgstAmount: row.sgstAmount,
          igstAmount: row.igstAmount,
          totalTax: row.totalTax,
          roundOff: row.roundOff,
          buyerId: row.buyerId,
          buyerName: row.buyerName,
          buyerGst: row.buyerGst,
          buyerAddress: row.buyerAddress,
          buyerPhone: row.buyerPhone,
          totalAmount,
          parcel: txn?.parcel || 1,
          items: billItems,
          createdAt: row.createdAt || row.billDate,
          updatedAt: row.updatedAt || row.billDate,
        };
      });

      if (input?.sortBy) {
        const key = input.sortBy as string;
        bills.sort((a, b) => {
          const valA = (a as any)[key];
          const valB = (b as any)[key];
          if (key === "totalAmount" || key === "subtotal" || key === "id") {
            const numA = parseFloat(String(valA || 0));
            const numB = parseFloat(String(valB || 0));
            return input.sortOrder === "desc" ? numB - numA : numA - numB;
          }
          const strA = String(valA || "");
          const strB = String(valB || "");
          if (input.sortOrder === "desc") {
            return strB.localeCompare(strA, undefined, { numeric: true });
          }
          return strA.localeCompare(strB, undefined, { numeric: true });
        });
      }

      return { bills, total: bills.length };
    }),

  create: adminQuery
    .input(
      z.object({
        buyerId: z.number(),
        billDate: z.string(),
        dueDate: z.string().nullable(),
        placeOfSupply: z.string(),
        reverseCharge: z.enum(["Yes", "No"]).default("No"),
        items: z.array(
          z.object({
            itemId: z.number(),
            qty: z.number().min(1),
            discountPercent: z.number().min(0).max(100).default(0),
            listPrice: z.number().optional(),
          }),
        ),
        roundOff: z.number().default(0),
        transportId: z.number().nullable().optional(),
        parcel: z.number().min(0).default(1),
      }),
    )
    .mutation(async ({ input }) => {
      const buyer = findById<Buyer>("buyers", input.buyerId);
      if (!buyer) throw new Error("Buyer not found");

      // Resolve Transport
      let transportId = input.transportId !== undefined ? input.transportId : null;
      if (transportId === null && buyer.defaultTransportId) {
        transportId = buyer.defaultTransportId;
      }

      // Retrieve company for tax determination
      const companies = findAll<Company>("companies");
      const company = companies[0] || { state: "Uttar Pradesh" };

      const companyState = company?.state || "Uttar Pradesh";
      const isInterState = companyState.toLowerCase() !== input.placeOfSupply.toLowerCase();

      let subtotal = 0;
      let totalTax = 0;
      let totalDiscount = 0;
      let cgstTotal = 0;
      let sgstTotal = 0;
      let igstTotal = 0;
      const lineItems: any[] = [];

      for (const entry of input.items) {
        const catalogItem = findById<any>("items", entry.itemId);
        if (!catalogItem) throw new Error(`Item ID ${entry.itemId} not found`);

        const price = entry.listPrice !== undefined ? entry.listPrice : Number(catalogItem.listPrice);
        const qty = entry.qty;
        const discPercent = entry.discountPercent;
        const taxPercent = Number(catalogItem.taxPercent);

        const grossAmount = price * qty;
        const discountAmount = grossAmount * (discPercent / 100);
        const taxableAmount = grossAmount - discountAmount;
        const taxAmount = taxableAmount * (taxPercent / 100);
        const finalAmount = taxableAmount + taxAmount;

        subtotal += grossAmount;
        totalDiscount += discountAmount;
        totalTax += taxAmount;

        if (isInterState) {
          igstTotal += taxAmount;
        } else {
          cgstTotal += taxAmount / 2;
          sgstTotal += taxAmount / 2;
        }

        lineItems.push({
          itemId: entry.itemId,
          qty,
          listPrice: price,
          discountPercent: discPercent,
          taxPercent,
          amount: finalAmount,
        });
      }

      const calculatedSubtotal = subtotal - totalDiscount;
      const calculatedTotalBeforeRound = calculatedSubtotal + totalTax;

      let finalRoundOff = input.roundOff;
      if (finalRoundOff === 0) {
        const roundedTotal = Math.round(calculatedTotalBeforeRound);
        finalRoundOff = roundedTotal - calculatedTotalBeforeRound;
      }
      const totalAmount = calculatedTotalBeforeRound + finalRoundOff;
      const totalQty = lineItems.reduce((sum, item) => sum + item.qty, 0);

      const billNumber = generateNextBillNumber();

      // Use a transaction to create transaction + bill + billItems atomically
      const createBillTx = db.transaction(() => {
        const txnInfo = db
          .prepare(
            `INSERT INTO transactions (buyerId, bookType, transactionType, transactionDate, dueDate, amount, totalQuantity, checkNumber, includeInReporting, parcel, deleted)
             VALUES (?, 'CC', 'sale', ?, ?, ?, ?, ?, 1, ?, 0)`,
          )
          .run(
            input.buyerId,
            input.billDate,
            input.dueDate,
            totalAmount,
            totalQty,
            `INV-${billNumber}`,
            input.parcel,
          );
        const txnId = txnInfo.lastInsertRowid as number;

        const billInfo = db
          .prepare(
            `INSERT INTO bills (transactionId, billNumber, billDate, dueDate, placeOfSupply, reverseCharge, transportId, subtotal, discountAmount, cgstAmount, sgstAmount, igstAmount, totalTax, roundOff)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
          )
          .run(
            txnId,
            billNumber,
            input.billDate,
            input.dueDate,
            input.placeOfSupply,
            input.reverseCharge === "Yes" ? 1 : 0,
            transportId,
            calculatedSubtotal,
            totalDiscount,
            cgstTotal,
            sgstTotal,
            igstTotal,
            totalTax,
            finalRoundOff,
          );
        const billId = billInfo.lastInsertRowid as number;

        const billItemStmt = db.prepare(
          `INSERT INTO billItems (billId, itemId, qty, listPrice, discountPercent, taxPercent, amount) VALUES (?, ?, ?, ?, ?, ?, ?)`,
        );
        for (const line of lineItems) {
          billItemStmt.run(billId, line.itemId, line.qty, line.listPrice, line.discountPercent, line.taxPercent, line.amount);
        }

        return { txnId, billId };
      });

      const { billId } = createBillTx();
      const result = findById<Bill>("bills", billId);

      return { id: billId, bill: enrichBill(result), message: "Bill created successfully" };
    }),

  update: adminQuery
    .input(
      z.object({
        id: z.number(),
        buyerId: z.number().optional(),
        billDate: z.string().optional(),
        dueDate: z.string().nullable().optional(),
        placeOfSupply: z.string().optional(),
        reverseCharge: z.enum(["Yes", "No"]).optional(),
        items: z
          .array(
            z.object({
              itemId: z.number(),
              qty: z.number().min(1),
              discountPercent: z.number().min(0).max(100).default(0),
              listPrice: z.number().optional(),
            }),
          )
          .optional(),
        roundOff: z.number().optional(),
        transportId: z.number().nullable().optional(),
        parcel: z.number().min(0).optional(),
      }),
    )
    .mutation(async ({ input }) => {
      const existingBill = findById<Bill>("bills", input.id);
      if (!existingBill) throw new Error("Bill not found");

      // Get associated transaction
      const txn = db.prepare(`SELECT * FROM transactions WHERE id = ?`).get(existingBill.transactionId) as any;
      if (!txn) throw new Error("Associated transaction not found");

      let buyerId = input.buyerId !== undefined ? input.buyerId : txn.buyerId;
      let billDate = input.billDate !== undefined ? input.billDate : existingBill.billDate;
      let dueDate = input.dueDate !== undefined ? input.dueDate : existingBill.dueDate;
      let placeOfSupply = input.placeOfSupply !== undefined ? input.placeOfSupply : existingBill.placeOfSupply;
      let reverseChargeVal =
        input.reverseCharge !== undefined ? input.reverseCharge : existingBill.reverseCharge;

      let transportId = input.transportId !== undefined ? input.transportId : existingBill.transportId;
      if (input.transportId === undefined && existingBill.transportId === null) {
        const buyer = findById<Buyer>("buyers", buyerId);
        if (buyer?.defaultTransportId) transportId = buyer.defaultTransportId;
      }

      // Recalculate if items changed
      let subtotal = 0, totalTax = 0, totalDiscount = 0;
      let cgstTotal = 0, sgstTotal = 0, igstTotal = 0;
      let totalAmount: number;
      let lineItems: any[] | null = null;
      let totalQty = 0;

      if (input.items !== undefined) {
        const companies = findAll<Company>("companies");
        const company = companies[0] || { state: "Uttar Pradesh" };
        const companyState = company?.state || "Uttar Pradesh";
        const isInterState = companyState.toLowerCase() !== (placeOfSupply || "").toLowerCase();

        lineItems = [];
        for (const entry of input.items) {
          const catalogItem = findById<any>("items", entry.itemId);
          if (!catalogItem) throw new Error(`Item ID ${entry.itemId} not found`);

          const price = entry.listPrice !== undefined ? entry.listPrice : Number(catalogItem.listPrice);
          const qty = entry.qty;
          const discPercent = entry.discountPercent;
          const taxPercent = Number(catalogItem.taxPercent);

          const grossAmount = price * qty;
          const discountAmount = grossAmount * (discPercent / 100);
          const taxableAmount = grossAmount - discountAmount;
          const taxAmount = taxableAmount * (taxPercent / 100);
          const finalAmount = taxableAmount + taxAmount;

          subtotal += grossAmount;
          totalDiscount += discountAmount;
          totalTax += taxAmount;
          totalQty += qty;

          if (isInterState) { igstTotal += taxAmount; }
          else { cgstTotal += taxAmount / 2; sgstTotal += taxAmount / 2; }

          lineItems.push({ itemId: entry.itemId, qty, listPrice: price, discountPercent: discPercent, taxPercent, amount: finalAmount });
        }
      } else {
        subtotal = Number(existingBill.subtotal);
        totalDiscount = Number(existingBill.discountAmount);
        totalTax = Number(existingBill.totalTax);
        cgstTotal = Number(existingBill.cgstAmount);
        sgstTotal = Number(existingBill.sgstAmount);
        igstTotal = Number(existingBill.igstAmount);
        // Get totalQty from existing billItems
        const existingItems = db.prepare(`SELECT qty FROM billItems WHERE billId = ?`).all(existingBill.id) as any[];
        totalQty = existingItems.reduce((s, i) => s + (i.qty || 0), 0);
      }

      const calculatedSubtotal = subtotal - totalDiscount;
      const calculatedTotalBeforeRound = calculatedSubtotal + totalTax;

      let finalRoundOff: number;
      if (input.roundOff !== undefined) {
        finalRoundOff = input.roundOff;
      } else {
        const roundedTotal = Math.round(calculatedTotalBeforeRound);
        finalRoundOff = roundedTotal - calculatedTotalBeforeRound;
      }
      totalAmount = calculatedTotalBeforeRound + finalRoundOff;

      const updateBillTx = db.transaction(() => {
        // Update bill
        db.prepare(
          `UPDATE bills SET billDate=?, dueDate=?, placeOfSupply=?, reverseCharge=?, transportId=?, subtotal=?, discountAmount=?, cgstAmount=?, sgstAmount=?, igstAmount=?, totalTax=?, roundOff=?, updatedAt=datetime('now')
           WHERE id=?`
        ).run(billDate, dueDate, placeOfSupply, reverseChargeVal === "Yes" ? 1 : 0, transportId, subtotal, totalDiscount, cgstTotal, sgstTotal, igstTotal, totalTax, finalRoundOff, input.id);

        // Update associated transaction
        db.prepare(
          `UPDATE transactions SET buyerId=?, transactionDate=?, dueDate=?, amount=?, totalQuantity=?, updatedAt=datetime('now')
           WHERE id=?`
        ).run(buyerId, billDate, dueDate, totalAmount, totalQty, existingBill.transactionId);

        // Update parcel on transaction if provided
        if (input.parcel !== undefined) {
          db.prepare(`UPDATE transactions SET parcel=? WHERE id=?`).run(input.parcel, existingBill.transactionId);
        }

        // Replace billItems if items changed
        if (lineItems !== null) {
          db.prepare(`DELETE FROM billItems WHERE billId=?`).run(input.id);
          const stmt = db.prepare(`INSERT INTO billItems (billId, itemId, qty, listPrice, discountPercent, taxPercent, amount) VALUES (?, ?, ?, ?, ?, ?, ?)`);
          for (const line of lineItems) {
            stmt.run(input.id, line.itemId, line.qty, line.listPrice, line.discountPercent, line.taxPercent, line.amount);
          }
        }
      });

      updateBillTx();
      const result = findById<Bill>("bills", input.id);
      return { id: input.id, bill: enrichBill(result), message: "Bill updated successfully" };
    }),

  delete: adminQuery
    .input(z.object({ id: z.number(), reason: z.string().optional() }))
    .mutation(async ({ input }) => {
      const bill = findById<Bill>("bills", input.id);
      if (!bill) throw new Error("Bill not found");

      const reason = input.reason || "Invoice deleted by user";

      const deleteTx = db.transaction(() => {
        // Soft delete the transaction associated with this bill
        db.prepare(
          `UPDATE transactions SET deleted = 1, deletedReason = ?, deletedAt = datetime('now'), updatedAt = datetime('now') WHERE id = ?`
        ).run(reason, bill.transactionId);

        // Delete bill and line items
        db.prepare(`DELETE FROM billItems WHERE billId=?`).run(input.id);
        db.prepare(`DELETE FROM bills WHERE id=?`).run(input.id);

        // Audit Log
        insert<any>("auditLogs", {
          tableName: "bills",
          recordId: input.id,
          action: "DELETE",
          oldValues: bill,
          newValues: null,
          reason,
          userId: "system",
          createdAt: new Date().toISOString(),
        });
      });

      deleteTx();
      return { message: "Invoice deleted and archived successfully" };
    }),

  detail: adminQuery
    .input(z.object({ id: z.number() }))
    .query(async ({ input }) => {
      const bill = findById<Bill>("bills", input.id);
      if (!bill) throw new Error("Bill not found");
      return enrichBill(bill);
    }),
});