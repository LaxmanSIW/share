import { z } from "zod";
import { createRouter, publicQuery } from "../middleware";
import { db } from "../db/engine";
import { findAll, findById, insert, update, remove } from "../queries/connection";
import type { Buyer, Transaction } from "../queries/connection";

export const buyerRouter = createRouter({
  list: publicQuery
    .input(
      z.object({
        page: z.number().default(1),
        limit: z.number().default(25),
        search: z.string().optional(),
        sortBy: z.string().optional(),
        sortOrder: z.enum(["asc", "desc"]).default("asc"),
      }).optional()
    )
    .query(async ({ input }) => {
      let items = findAll<Buyer>("buyers").filter(b => b.id > 0); // exclude system buyer
      const allTxs = findAll<Transaction>("transactions").filter(t => !t.deleted);

      // Apply search
      if (input?.search) {
        const searchLower = input.search.toLowerCase();
        items = items.filter(b => b.companyName.toLowerCase().includes(searchLower));
      }

      // Sort
      items.sort((a, b) => {
        if (input?.sortOrder === "desc") {
          return b.companyName.localeCompare(a.companyName);
        }
        return a.companyName.localeCompare(b.companyName);
      });

      const total = items.length;

      // Calculate parcels via bills joined through transactions
      // Build a map of buyerId -> parcel count from bills (real bills only, id > 0)
      const billParcelMap = new Map<number, number>();
      const billRows = db.prepare(`
        SELECT t.buyerId, COALESCE(t.parcel, 1) as parcel
        FROM transactions t
        JOIN bills b ON b.transactionId = t.id
        WHERE b.id > 0 AND t.deleted = 0
      `).all() as any[];
      for (const r of billRows) {
        billParcelMap.set(r.buyerId, (billParcelMap.get(r.buyerId) || 0) + (r.parcel || 1));
      }

      // Calculate outstanding for each buyer
      const itemsWithStats = items.map(buyer => {
        const buyerTxs = allTxs.filter(t => t.buyerId === buyer.id);
        const totalsales = buyerTxs
          .filter(t => t.transactionType === "sale")
          .reduce((sum, t) => sum + Number(t.amount), 0);
        const totalPaid = buyerTxs
          .filter(t => t.transactionType === "payment")
          .reduce((sum, t) => sum + Number(t.amount), 0);

        const totalParcels = billParcelMap.get(buyer.id) || 0;

        // Resolve defaultTransportName from transports
        let defaultTransportName: string | null = null;
        if (buyer.defaultTransportId) {
          const tr = db.prepare(`SELECT name FROM transports WHERE id = ?`).get(buyer.defaultTransportId) as any;
          defaultTransportName = tr?.name || null;
        }

        return {
          ...buyer,
          defaultTransportName,
          totalsales,
          totalPaid,
          outstanding: totalsales - totalPaid,
          totalParcels,
        };
      });

      return {
        items: itemsWithStats,
        total,
      };
    }),

  create: publicQuery
    .input(
      z.object({
        companyName: z.string().min(1),
        contactPerson: z.string().optional(),
        phone: z.string().optional(),
        gstNumber: z.string().optional(),
        creditLimit: z.number().min(0).default(0),
        address: z.string().optional(),
        city: z.string().optional(),
        state: z.string().optional(),
        stateCode: z.string().optional(),
        defaultTransportId: z.number().nullable().optional(),
        defaultTransportName: z.string().nullable().optional(),
      })
    )
    .mutation(async ({ input }) => {
      const result = insert<Buyer>("buyers", {
        companyName: input.companyName,
        contactPerson: input.contactPerson || null,
        phone: input.phone || null,
        gstNumber: input.gstNumber || null,
        creditLimit: input.creditLimit,
        address: input.address || null,
        city: input.city || null,
        state: input.state || null,
        stateCode: input.stateCode || null,
        defaultTransportId: input.defaultTransportId || null,
      } as any);

      return { id: result.id, message: "Buyer created successfully" };
    }),

  update: publicQuery
    .input(
      z.object({
        id: z.number(),
        companyName: z.string().optional(),
        contactPerson: z.string().optional(),
        phone: z.string().optional(),
        gstNumber: z.string().optional(),
        creditLimit: z.number().optional(),
        address: z.string().optional(),
        city: z.string().optional(),
        state: z.string().optional(),
        stateCode: z.string().optional(),
        defaultTransportId: z.number().nullable().optional(),
        defaultTransportName: z.string().nullable().optional(),
      })
    )
    .mutation(async ({ input }) => {
      const { id, defaultTransportName, ...updateData } = input;

      const updateValues: Record<string, any> = {};
      if (updateData.companyName !== undefined) updateValues.companyName = updateData.companyName;
      if (updateData.contactPerson !== undefined) updateValues.contactPerson = updateData.contactPerson || null;
      if (updateData.phone !== undefined) updateValues.phone = updateData.phone || null;
      if (updateData.gstNumber !== undefined) updateValues.gstNumber = updateData.gstNumber || null;
      if (updateData.creditLimit !== undefined) updateValues.creditLimit = updateData.creditLimit;
      if (updateData.address !== undefined) updateValues.address = updateData.address || null;
      if (updateData.city !== undefined) updateValues.city = updateData.city || null;
      if (updateData.state !== undefined) updateValues.state = updateData.state || null;
      if (updateData.stateCode !== undefined) updateValues.stateCode = updateData.stateCode || null;
      if (updateData.defaultTransportId !== undefined) updateValues.defaultTransportId = updateData.defaultTransportId;

      const result = update<Buyer>("buyers", id, updateValues as any);
      if (!result) throw new Error("Buyer not found");

      return { id, message: "Buyer updated successfully" };
    }),

  delete: publicQuery
    .input(z.object({ id: z.number(), reason: z.string().optional() }))
    .mutation(async ({ input }) => {
      const buyer = findById<Buyer>("buyers", input.id);
      if (!buyer) throw new Error("Buyer not found");

      const allTxs = findAll<Transaction>("transactions");
      const txCount = allTxs.filter(t => t.buyerId === input.id && !t.deleted).length;

      if (txCount > 0) {
        throw new Error("Cannot delete buyer with active transactions. Please archive associated transactions/bills first.");
      }

      const reason = input.reason || "Buyer deleted by user";
      const success = remove<Buyer>("buyers", input.id);
      if (!success) throw new Error("Buyer not found");

      insert<any>("auditLogs", {
        tableName: "buyers",
        recordId: input.id,
        action: "DELETE",
        oldValues: buyer,
        newValues: null,
        userId: "system",
        reason,
        createdAt: new Date().toISOString(),
      });

      return { message: "Buyer deleted and logged to audit trail" };
    }),

  detail: publicQuery
    .input(z.object({ id: z.number() }))
    .query(async ({ input }) => {
      const buyer = findById<Buyer>("buyers", input.id);
      if (!buyer) {
        throw new Error("Buyer not found");
      }

      const allTxs = findAll<Transaction>("transactions").filter(t => !t.deleted && t.buyerId === input.id);

      const totalsales = allTxs
        .filter(t => t.transactionType === "sale")
        .reduce((sum, t) => sum + Number(t.amount), 0);
      const totalPaid = allTxs
        .filter(t => t.transactionType === "payment")
        .reduce((sum, t) => sum + Number(t.amount), 0);

      let defaultTransportName: string | null = null;
      if (buyer.defaultTransportId) {
        const tr = db.prepare(`SELECT name FROM transports WHERE id = ?`).get(buyer.defaultTransportId) as any;
        defaultTransportName = tr?.name || null;
      }

      return {
        ...buyer,
        defaultTransportName,
        totalsales,
        totalPaid,
        outstanding: totalsales - totalPaid,
        transactionCount: allTxs.length,
      };
    }),

  statement: publicQuery
    .input(
      z.object({
        id: z.number(),
        startDate: z.string().optional(),
        endDate: z.string().optional(),
      })
    )
    .query(async ({ input }) => {
      const buyer = findById<Buyer>("buyers", input.id);
      if (!buyer) {
        throw new Error("Buyer not found");
      }

      let txItems = findAll<Transaction>("transactions").filter(
        t => !t.deleted && t.buyerId === input.id
      );

      if (input.startDate) txItems = txItems.filter(t => t.transactionDate >= input.startDate!);
      if (input.endDate) txItems = txItems.filter(t => t.transactionDate <= input.endDate!);

      txItems.sort((a, b) => a.transactionDate.localeCompare(b.transactionDate));

      let balance = 0;
      const items = txItems.map(tx => {
        const amount = Number(tx.amount);
        const debit = tx.transactionType === "sale" ? amount : 0;
        const credit = tx.transactionType === "payment" ? amount : 0;
        balance = balance + debit - credit;

        return {
          id: tx.id,
          date: tx.transactionDate,
          description: `${tx.transactionType}${tx.checkNumber ? ` - Chq: ${tx.checkNumber}` : ""}`,
          bookType: tx.bookType,
          type: tx.transactionType,
          debit,
          credit,
          balance,
          quantity: tx.totalQuantity,
        };
      });

      return {
        buyer,
        openingBalance: 0,
        items,
        closingBalance: balance,
      };
    }),

  riskAnalysis: publicQuery
    .input(z.object({ search: z.string().optional() }).optional())
    .query(async ({ input }) => {
      let allBuyers = findAll<Buyer>("buyers").filter(b => b.id > 0);

      if (input?.search) {
        const searchLower = input.search.toLowerCase();
        allBuyers = allBuyers.filter(b => b.companyName.toLowerCase().includes(searchLower));
      }

      const allTxs = findAll<Transaction>("transactions").filter(t => !t.deleted);
      const result = [];

      for (const buyer of allBuyers) {
        const buyerTxs = allTxs.filter(t => t.buyerId === buyer.id);
        const totalsales = buyerTxs
          .filter(t => t.transactionType === "sale")
          .reduce((sum, t) => sum + Number(t.amount), 0);
        const totalPaid = buyerTxs
          .filter(t => t.transactionType === "payment")
          .reduce((sum, t) => sum + Number(t.amount), 0);
        const outstanding = totalsales - totalPaid;

        const creditLimit = Number(buyer.creditLimit) || 0;
        const utilization = creditLimit > 0 ? outstanding / creditLimit : 0;

        let riskScore = 5;
        let riskLevel = "Medium";

        if (utilization > 0.8) {
          riskScore = 2;
          riskLevel = "High";
        } else if (utilization > 0.4) {
          riskScore = 5;
          riskLevel = "Medium";
        } else {
          riskScore = 8;
          riskLevel = "Low";
        }

        result.push({
          buyer,
          totalsales,
          totalPaid,
          outstanding,
          riskScore,
          riskLevel,
          utilization,
        });
      }

      return result;
    }),

  bulkCreate: publicQuery
    .input(
      z.object({
        buyers: z.array(
          z.object({
            companyName: z.string().min(1),
            contactPerson: z.string().optional(),
            phone: z.string().optional(),
            gstNumber: z.string().optional(),
            creditLimit: z.number().min(0).default(0),
            address: z.string().optional(),
            city: z.string().optional(),
            state: z.string().optional(),
            stateCode: z.string().optional(),
          })
        ),
      })
    )
    .mutation(async ({ input }) => {
      let imported = 0;
      let failed = 0;
      const errors: Array<{ row: number; message: string }> = [];

      for (let i = 0; i < input.buyers.length; i++) {
        const buyer = input.buyers[i];
        try {
          insert<Buyer>("buyers", {
            companyName: buyer.companyName.trim(),
            contactPerson: buyer.contactPerson || null,
            phone: buyer.phone || null,
            gstNumber: buyer.gstNumber || null,
            creditLimit: buyer.creditLimit || 0,
            address: buyer.address || null,
            city: buyer.city || null,
            state: buyer.state || null,
            stateCode: buyer.stateCode || null,
          } as any);
          imported++;
        } catch (err: any) {
          failed++;
          errors.push({ row: i + 1, message: err.message || "Unknown error" });
        }
      }

      return { imported, failed, errors };
    }),
});