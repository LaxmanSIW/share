import { z } from "zod";
import { createRouter, publicQuery } from "../middleware";
import { db } from "../db/engine";
import { findAll, findById, insert, update } from "../queries/connection";
import type { Buyer, Transaction, AuditLog } from "../queries/connection";

// Get the default "Trousers" item id and dummy bill id (0)
function getDefaultItemAndBill(): { itemId: number; billId: number } {
  const item = db.prepare(`SELECT id FROM items WHERE name = 'Trousers' LIMIT 1`).get() as any;
  return { itemId: item?.id || 1, billId: 0 };
}

export const transactionRouter = createRouter({
  list: publicQuery
    .input(
      z.object({
        page: z.number().default(1),
        limit: z.number().default(25),
        bookType: z.enum(["CC", "CS", "ALL"]).optional().default("ALL"),
        search: z.string().optional(),
        startDate: z.string().optional(),
        endDate: z.string().optional(),
        transactionType: z.enum(["sale", "payment", "ALL"]).optional().default("ALL"),
        buyerId: z.number().optional(),
        noPagination: z.boolean().optional().default(false),
      }).optional()
    )
    .query(async ({ input }) => {
      // Using raw SQL to join transactions with buyers and bills
      let items = db.prepare(`
        SELECT 
          t.id, t.buyerId, b.companyName, t.bookType, t.transactionDate, t.dueDate, 
          t.amount, t.totalQuantity, t.checkNumber, t.transactionType, 
          t.includeInReporting, t.parcel, t.createdAt,
          bl.billNumber as billNumber
        FROM transactions t
        LEFT JOIN buyers b ON t.buyerId = b.id
        LEFT JOIN bills bl ON bl.transactionId = t.id
        WHERE t.deleted = 0
      `).all() as any[];

      // Apply filters
      if (input?.bookType && input.bookType !== "ALL") {
        items = items.filter(t => t.bookType === input.bookType);
      }
      if (input?.transactionType && input.transactionType !== "ALL") {
        items = items.filter(t => t.transactionType === input.transactionType);
      }
      if (input?.startDate) {
        items = items.filter(t => t.transactionDate >= input.startDate!);
      }
      if (input?.endDate) {
        items = items.filter(t => t.transactionDate <= input.endDate!);
      }
      if (input?.buyerId) {
        items = items.filter(t => t.buyerId === input.buyerId);
      }

      // Sort by id desc (latest transaction first)
      items.sort((a, b) => b.id - a.id);

      const total = items.length;

      return { items, total };
    }),

  create: publicQuery
    .input(
      z.object({
        buyerId: z.number(),
        bookType: z.enum(["CC", "CS"]),
        transactionDate: z.string(),
        dueDate: z.string().optional(),
        amount: z.number().positive(),
        totalQuantity: z.number().int().min(0).default(0),
        checkNumber: z.string().optional(),
        transactionType: z.enum(["sale", "payment"]),
        includeInReporting: z.boolean().default(true),
      })
    )
    .mutation(async ({ input }) => {
      const parseDate = (dateStr: string): string => {
        if (dateStr.includes("/") || dateStr.includes("-")) {
          const parts = dateStr.split(/[\/\-]/);
          if (parts.length === 3) {
            const day = parts[0].padStart(2, "0");
            const month = parts[1].padStart(2, "0");
            const year = parts[2].length === 2 ? `20${parts[2]}` : parts[2];
            return `${year}-${month}-${day}`;
          }
          return dateStr;
        }
        const clean = dateStr.replace(/\D/g, "");
        if (clean.length === 6 || clean.length === 8) {
          const day = clean.substring(0, 2);
          const month = clean.substring(2, 4);
          const year = clean.length === 6 ? `20${clean.substring(4, 6)}` : clean.substring(4, 8);
          return `${year}-${month}-${day}`;
        }
        return dateStr;
      };

      const txDate = parseDate(input.transactionDate);
      const dueDate = input.dueDate ? parseDate(input.dueDate) : null;

      const result = db.transaction(() => {
        const info = db
          .prepare(
            `INSERT INTO transactions (buyerId, bookType, transactionType, transactionDate, dueDate, amount, totalQuantity, checkNumber, includeInReporting, deleted)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)`
          )
          .run(
            input.buyerId,
            input.bookType,
            input.transactionType,
            txDate,
            dueDate,
            input.amount,
            input.totalQuantity || null,
            input.checkNumber || null,
            input.includeInReporting ? 1 : 0,
          );
        const txnId = info.lastInsertRowid as number;

        // For sale transactions not via bill, create a billItem entry
        // linked to the dummy bill (id=0) with the default "Trousers" item
        if (input.transactionType === "sale" && input.totalQuantity > 0) {
          const { itemId, billId } = getDefaultItemAndBill();
          db.prepare(
            `INSERT INTO billItems (billId, itemId, qty, listPrice, discountPercent, taxPercent, amount)
             VALUES (?, ?, ?, 0, 0, 0, ?)`
          ).run(billId, itemId, input.totalQuantity, input.amount);
        }

        return txnId;
      })();

      return { id: result, message: "Transaction created successfully" };
    }),

  update: publicQuery
    .input(
      z.object({
        id: z.number(),
        buyerId: z.number().optional(),
        bookType: z.enum(["CC", "CS"]).optional(),
        transactionDate: z.string().optional(),
        dueDate: z.string().optional(),
        amount: z.number().positive().optional(),
        totalQuantity: z.number().int().min(0).optional(),
        checkNumber: z.string().optional(),
        transactionType: z.enum(["sale", "payment"]).optional(),
        includeInReporting: z.boolean().optional(),
      })
    )
    .mutation(async ({ input }) => {
      const { id, ...updateData } = input;

      const oldTransaction = findById<Transaction>("transactions", id);
      if (!oldTransaction || oldTransaction.deleted) {
        throw new Error("Transaction not found");
      }

      // Check if this transaction has an associated bill
      const hasBill = db.prepare(`SELECT 1 FROM bills WHERE transactionId = ? LIMIT 1`).get(id);
      if (hasBill) {
        const attemptedKeys = Object.keys(updateData).filter(
          (k) => updateData[k as keyof typeof updateData] !== undefined && k !== "includeInReporting"
        );
        if (attemptedKeys.length > 0) {
          throw new Error("This transaction is linked to a bill. Only 'Include in Reporting' can be updated directly; other fields are locked and can only be modified through the bill itself.");
        }
      }

      const updateValues: Record<string, any> = {};
      if (updateData.buyerId !== undefined) updateValues.buyerId = updateData.buyerId;
      if (updateData.bookType !== undefined) updateValues.bookType = updateData.bookType;
      if (updateData.transactionDate !== undefined) updateValues.transactionDate = updateData.transactionDate;
      if (updateData.dueDate !== undefined) updateValues.dueDate = updateData.dueDate || null;
      if (updateData.amount !== undefined) updateValues.amount = updateData.amount;
      if (updateData.totalQuantity !== undefined) updateValues.totalQuantity = updateData.totalQuantity;
      if (updateData.checkNumber !== undefined) updateValues.checkNumber = updateData.checkNumber || null;
      if (updateData.transactionType !== undefined) updateValues.transactionType = updateData.transactionType;
      if (updateData.includeInReporting !== undefined) updateValues.includeInReporting = updateData.includeInReporting;

      const result = update<Transaction>("transactions", id, updateValues as any);
      if (!result) throw new Error("Transaction not found");

      // Update corresponding billItem if it's a sale
      if (updateData.amount !== undefined || updateData.totalQuantity !== undefined) {
        // Note: we don't easily know which billItem belongs to this transaction when using dummy bill id=0
      }

      // Create audit log
      insert<AuditLog>("auditLogs", {
        tableName: "transactions",
        recordId: id,
        action: "UPDATE",
        oldValues: oldTransaction as unknown as Record<string, unknown>,
        newValues: updateValues as unknown as Record<string, unknown>,
        userId: "system",
        reason: null,
        createdAt: new Date().toISOString(),
      });

      return { id, message: "Transaction updated successfully" };
    }),

  delete: publicQuery
    .input(
      z.object({
        id: z.number(),
        reason: z.string().min(1),
      })
    )
    .mutation(async ({ input }) => {
      const oldTransaction = findById<Transaction>("transactions", input.id);
      if (!oldTransaction || oldTransaction.deleted) {
        throw new Error("Transaction not found");
      }

      // Check if transaction was created by a bill
      const hasBill = db.prepare(`SELECT 1 FROM bills WHERE transactionId = ? AND id > 0 LIMIT 1`).get(input.id);
      if (hasBill) {
        throw new Error("This transaction was automatically created by a bill and cannot be deleted directly. It will be removed automatically if you delete or update the corresponding bill.");
      }

      // Perform soft delete
      db.prepare(
        `UPDATE transactions SET deleted = 1, deletedReason = ?, deletedAt = datetime('now'), updatedAt = datetime('now') WHERE id = ?`
      ).run(input.reason, input.id);

      // Create audit log
      insert<AuditLog>("auditLogs", {
        tableName: "transactions",
        recordId: input.id,
        action: "DELETE",
        oldValues: oldTransaction as unknown as Record<string, unknown>,
        newValues: null,
        userId: "system",
        reason: input.reason,
        createdAt: new Date().toISOString(),
      });

      return { id: input.id, message: "Transaction archived successfully" };
    }),

  bulkCreate: publicQuery
    .input(
      z.object({
        transactions: z.array(
          z.object({
            buyerName: z.string(),
            bookType: z.enum(["CC", "CS"]),
            transactionDate: z.string(),
            dueDate: z.string().optional(),
            transactionType: z.enum(["sale", "payment"]),
            quantity: z.number().int().min(0).default(0),
            amount: z.number().positive(),
            checkNumber: z.string().optional(),
            includeInReporting: z.boolean().default(true),
          })
        ),
      })
    )
    .mutation(async ({ input }) => {
      let imported = 0;
      let failed = 0;
      let newBuyers = 0;
      const errors: Array<{ row: number; message: string }> = [];

      const buyerCache = new Map<string, number>();
      const allBuyers = findAll<Buyer>("buyers").filter(b => b.id > 0); // exclude system buyer
      const { itemId, billId } = getDefaultItemAndBill();

      const runBulk = db.transaction(() => {
        for (let i = 0; i < input.transactions.length; i++) {
          const tx = input.transactions[i];
          try {
            let buyerId: number;
            const cacheKey = tx.buyerName.toLowerCase().trim();

            if (buyerCache.has(cacheKey)) {
              buyerId = buyerCache.get(cacheKey)!;
            } else {
              const existingBuyer = allBuyers.find(
                b => b.companyName.toLowerCase().trim() === cacheKey
              );

              if (existingBuyer) {
                buyerId = existingBuyer.id;
              } else {
                const info = db.prepare(
                  `INSERT INTO buyers (companyName, creditLimit, openingBalance, createdAt, updatedAt)
                   VALUES (?, 0, 0, datetime('now'), datetime('now'))`
                ).run(tx.buyerName.trim());
                buyerId = info.lastInsertRowid as number;
                allBuyers.push({ id: buyerId, companyName: tx.buyerName.trim() } as Buyer);
                newBuyers++;
              }
              buyerCache.set(cacheKey, buyerId);
            }

            // Parse date
            let txDate: string;
            const dateStr = tx.transactionDate.trim();
            if (dateStr.includes("/")) {
              const parts = dateStr.split("/");
              const day = parts[0].padStart(2, "0");
              const month = parts[1].padStart(2, "0");
              const year = parts[2].length === 2 ? `20${parts[2]}` : parts[2];
              txDate = `${year}-${month}-${day}`;
            } else {
              txDate = dateStr;
            }

            let dueDate: string | null = null;
            if (tx.dueDate?.trim()) {
              const dueStr = tx.dueDate.trim();
              if (dueStr.includes("/")) {
                const parts = dueStr.split("/");
                const day = parts[0].padStart(2, "0");
                const month = parts[1].padStart(2, "0");
                const year = parts[2].length === 2 ? `20${parts[2]}` : parts[2];
                dueDate = `${year}-${month}-${day}`;
              } else {
                dueDate = dueStr;
              }
            }

            db.prepare(
              `INSERT INTO transactions (buyerId, bookType, transactionType, transactionDate, dueDate, amount, totalQuantity, checkNumber, includeInReporting, deleted)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)`
            ).run(
              buyerId, tx.bookType, tx.transactionType, txDate, dueDate,
              tx.amount, tx.quantity || null, tx.checkNumber || null,
              tx.includeInReporting ? 1 : 0,
            );

            // For sale transactions, add billItem with Trousers
            if (tx.transactionType === "sale" && tx.quantity > 0) {
              db.prepare(
                `INSERT INTO billItems (billId, itemId, qty, listPrice, discountPercent, taxPercent, amount)
                 VALUES (?, ?, ?, 0, 0, 0, ?)`
              ).run(billId, itemId, tx.quantity, tx.amount);
            }

            imported++;
          } catch (err: any) {
            failed++;
            errors.push({ row: i + 1, message: err.message || "Unknown error" });
          }
        }
      });

      runBulk();
      return { imported, failed, newBuyers, errors };
    }),
});