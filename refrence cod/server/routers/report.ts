import { z } from "zod";
import { createRouter, publicQuery } from "../middleware";
import { db } from "../db/engine";
import { findAll, findById } from "../queries/connection";
import type { Buyer, Transaction } from "../queries/connection";

function getTrousersItemInfo() {
  const row = db.prepare(`
    SELECT i.id, i.name, i.hsnCode, i.categoryId, COALESCE(ic.name, 'Trousers') as categoryName
    FROM items i
    LEFT JOIN itemCategories ic ON ic.id = i.categoryId
    WHERE i.name = 'Trousers'
    LIMIT 1
  `).get() as any;
  return row || { id: 1, name: "Trousers", hsnCode: "62034200", categoryId: 1, categoryName: "Trousers" };
}

export const reportRouter = createRouter({
  // === 1. Buyer Outstanding & Aging Report ===
  outstanding: publicQuery
    .input(
      z.object({
        bookType: z.enum(["CC", "CS", "ALL"]).default("ALL"),
        riskLevel: z.enum(["High", "Medium", "Low", "ALL"]).default("ALL"),
        minAmount: z.number().optional(),
      }).optional()
    )
    .query(async ({ input }) => {
      const allBuyers = findAll<Buyer>("buyers").filter(b => b.id > 0);
      const allTxs = findAll<Transaction>("transactions").filter(t => !t.deleted);

      const bookFilter = input?.bookType && input?.bookType !== "ALL"
        ? (t: Transaction) => t.bookType === input.bookType
        : () => true;

      const buyerParcelMap = new Map<number, number>();
      const billRows = db.prepare(`
        SELECT t.buyerId, COALESCE(t.parcel, 1) as parcel
        FROM transactions t
        JOIN bills b ON b.transactionId = t.id
        WHERE b.id > 0 AND t.deleted = 0
      `).all() as any[];
      for (const r of billRows) {
        buyerParcelMap.set(r.buyerId, (buyerParcelMap.get(r.buyerId) || 0) + (r.parcel || 1));
      }

      const results = [];
      for (const buyer of allBuyers) {
        const buyerTxs = allTxs.filter(t => t.buyerId === buyer.id).filter(bookFilter);
        const totalsales = buyerTxs
          .filter(t => t.transactionType === "sale")
          .reduce((sum, t) => sum + Number(t.amount), 0);
        const totalPaid = buyerTxs
          .filter(t => t.transactionType === "payment")
          .reduce((sum, t) => sum + Number(t.amount), 0);
        const outstanding = totalsales - totalPaid;
        const totalParcels = buyerParcelMap.get(buyer.id) || 0;

        if (outstanding > 0 && (!input?.minAmount || outstanding >= input.minAmount)) {
          const saleTxs = buyerTxs
            .filter(t => t.transactionType === "sale")
            .sort((a, b) => new Date(b.transactionDate).getTime() - new Date(a.transactionDate).getTime());

          const latestSale = saleTxs[0];
          let daysOverdue = 0;
          if (latestSale) {
            const dueDate = latestSale.dueDate ? new Date(latestSale.dueDate) : new Date(latestSale.transactionDate);
            const now = new Date();
            const diffTime = now.getTime() - dueDate.getTime();
            daysOverdue = Math.max(0, Math.floor(diffTime / (1000 * 60 * 60 * 24)));
          }

          let riskScore = "Low";
          if (daysOverdue > 90 || outstanding > (buyer.creditLimit || 50000) * 1.5) {
            riskScore = "High";
          } else if (daysOverdue > 45 || outstanding > (buyer.creditLimit || 50000)) {
            riskScore = "Medium";
          }

          if (input?.riskLevel === "ALL" || input?.riskLevel === riskScore) {
            results.push({
              buyerId: buyer.id,
              companyName: buyer.companyName,
              contactPerson: buyer.contactPerson,
              phone: buyer.phone,
              gstNumber: buyer.gstNumber,
              totalsales,
              totalPaid,
              outstanding,
              totalParcels,
              creditLimit: buyer.creditLimit || 0,
              daysOverdue,
              riskScore,
            });
          }
        }
      }

      return results.sort((a, b) => b.outstanding - a.outstanding);
    }),

  // === 2. Buyer Statement Ledger ===
  buyerStatement: publicQuery
    .input(
      z.object({
        buyerId: z.number(),
        startDate: z.string().optional(),
        endDate: z.string().optional(),
      })
    )
    .query(async ({ input }) => {
      const buyer = findById<Buyer>("buyers", input.buyerId);
      if (!buyer) throw new Error("Buyer not found");

      let sql = `
        SELECT 
          t.id, t.transactionDate as date, t.transactionType, t.bookType, t.amount,
          b.billNumber
        FROM transactions t
        LEFT JOIN bills b ON b.transactionId = t.id
        WHERE t.buyerId = ? AND t.deleted = 0 AND t.includeInReporting = 1
      `;
      const params: any[] = [input.buyerId];

      if (input.startDate) { sql += ` AND t.transactionDate >= ?`; params.push(input.startDate); }
      if (input.endDate) { sql += ` AND t.transactionDate <= ?`; params.push(input.endDate); }

      sql += ` ORDER BY t.transactionDate ASC, t.id ASC`;
      const rows = db.prepare(sql).all(...params) as any[];

      let runningBalance = Number(buyer.openingBalance) || 0;
      const items = rows.map((r) => {
        const isSale = r.transactionType === "sale";
        const amt = Number(r.amount) || 0;
        const debit = isSale ? amt : 0;
        const credit = !isSale ? amt : 0;
        runningBalance += debit - credit;

        const description = isSale
          ? r.billNumber ? `Invoice #${r.billNumber}` : `Cash Sale (${r.bookType})`
          : `Payment Received (${r.bookType})`;

        return {
          id: r.id,
          date: r.date,
          description,
          bookType: r.bookType,
          debit,
          credit,
          balance: Math.round(runningBalance * 100) / 100,
        };
      });

      return {
        buyer: {
          id: buyer.id,
          companyName: buyer.companyName,
          contactPerson: buyer.contactPerson,
          phone: buyer.phone,
          gstNumber: buyer.gstNumber,
          openingBalance: Number(buyer.openingBalance) || 0,
        },
        openingBalance: Number(buyer.openingBalance) || 0,
        items,
        closingBalance: Math.round(runningBalance * 100) / 100,
      };
    }),

  // === 3. Trouser Movement Report ===
  trouserMovement: publicQuery
    .input(
      z.object({
        startDate: z.string().optional(),
        endDate: z.string().optional(),
        bookType: z.enum(["CC", "CS", "ALL"]).default("ALL"),
        groupBy: z.enum(["Day", "Week", "Month", "Buyer", "Date"]).default("Day"),
      }).optional()
    )
    .query(async ({ input }) => {
      const bookFilter = input?.bookType && input.bookType !== "ALL"
        ? `AND t.bookType = '${input.bookType}'`
        : "";

      let sql = `
        SELECT 
          t.transactionDate as date,
          b.companyName as buyer,
          t.bookType,
          COALESCE(t.totalQuantity, 0) as quantity
        FROM transactions t
        JOIN buyers b ON b.id = t.buyerId
        WHERE t.deleted = 0 AND t.transactionType = 'sale' AND t.includeInReporting = 1 ${bookFilter}
      `;
      const params: any[] = [];
      if (input?.startDate) { sql += ` AND t.transactionDate >= ?`; params.push(input.startDate); }
      if (input?.endDate) { sql += ` AND t.transactionDate <= ?`; params.push(input.endDate); }

      sql += ` ORDER BY t.transactionDate ASC, t.id ASC`;

      const rows = db.prepare(sql).all(...params) as any[];
      const groupBy = input?.groupBy || "Day";

      const groupMap = new Map<string, { ccQuantity: number; csQuantity: number; total: number }>();
      for (const r of rows) {
        let key = r.date;
        if (groupBy === "Buyer") {
          key = r.buyer || "Unknown Buyer";
        } else if (groupBy === "Month") {
          const d = new Date(r.date);
          key = isNaN(d.getTime()) ? r.date : `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
        } else if (groupBy === "Week") {
          const d = new Date(r.date);
          if (!isNaN(d.getTime())) {
            const firstDayOfYear = new Date(d.getFullYear(), 0, 1);
            const pastDaysOfYear = (d.getTime() - firstDayOfYear.getTime()) / 86400000;
            const weekNum = Math.ceil((pastDaysOfYear + firstDayOfYear.getDay() + 1) / 7);
            key = `${d.getFullYear()}-W${String(weekNum).padStart(2, "0")}`;
          }
        }

        const existing = groupMap.get(key) || { ccQuantity: 0, csQuantity: 0, total: 0 };
        const qty = Number(r.quantity) || 0;

        if (r.bookType === "CC") {
          existing.ccQuantity += qty;
        } else {
          existing.csQuantity += qty;
        }
        existing.total += qty;
        groupMap.set(key, existing);
      }

      let cumulative = 0;
      let totalCc = 0;
      let totalCs = 0;
      let peakCount = 0;
      let peakDay = "N/A";

      const items = Array.from(groupMap.entries()).map(([key, value]) => {
        cumulative += value.total;
        totalCc += value.ccQuantity;
        totalCs += value.csQuantity;

        if (value.total > peakCount) {
          peakCount = value.total;
          peakDay = key;
        }

        return {
          [groupBy === "Buyer" ? "buyer" : "date"]: key,
          ccQuantity: value.ccQuantity,
          csQuantity: value.csQuantity,
          total: value.total,
          cumulative,
        };
      });

      const grandTotal = totalCc + totalCs;
      const averagePerDay = items.length > 0 ? Math.round(grandTotal / items.length) : 0;

      return {
        items,
        summary: {
          totalCcQuantity: totalCc,
          totalCsQuantity: totalCs,
          grandTotal,
          totalPieces: grandTotal,
          averagePerDay,
          peakDay,
          peakCount,
        },
      };
    }),

  // === 4. Sales Period Analysis ===
  salesPeriod: publicQuery
    .input(
      z.object({
        period: z.enum(["Daily", "Weekly", "Monthly", "Yearly"]).default("Monthly"),
        paymentType: z.enum(["CC", "CS", "ALL"]).optional().default("ALL"),
        startDate: z.string().optional(),
        endDate: z.string().optional(),
        buyerId: z.number().optional(),
      })
    )
    .query(async ({ input }) => {
      let sql = `
        SELECT 
          t.transactionDate as date,
          t.transactionType,
          t.bookType,
          t.amount,
          COALESCE(t.totalQuantity, 0) as quantity
        FROM transactions t
        WHERE t.deleted = 0 AND t.includeInReporting = 1
      `;
      const params: any[] = [];

      if (input.paymentType && input.paymentType !== "ALL") {
        sql += ` AND t.bookType = ?`;
        params.push(input.paymentType);
      }
      if (input.buyerId) {
        sql += ` AND t.buyerId = ?`;
        params.push(input.buyerId);
      }
      if (input.startDate) { sql += ` AND t.transactionDate >= ?`; params.push(input.startDate); }
      if (input.endDate) { sql += ` AND t.transactionDate <= ?`; params.push(input.endDate); }

      const rows = db.prepare(sql).all(...params) as any[];

      const periodMap = new Map<string, {
        ccAmount: number; csAmount: number; totalAmount: number; paymentsAmount: number;
        ccQuantity: number; csQuantity: number; totalQuantity: number;
      }>();

      for (const r of rows) {
        const d = new Date(r.date);
        let periodKey = r.date;
        if (isNaN(d.getTime())) continue;

        if (input.period === "Monthly") {
          periodKey = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
        } else if (input.period === "Yearly") {
          periodKey = `${d.getFullYear()}`;
        } else if (input.period === "Weekly") {
          const firstDayOfYear = new Date(d.getFullYear(), 0, 1);
          const pastDaysOfYear = (d.getTime() - firstDayOfYear.getTime()) / 86400000;
          const weekNum = Math.ceil((pastDaysOfYear + firstDayOfYear.getDay() + 1) / 7);
          periodKey = `${d.getFullYear()}-W${String(weekNum).padStart(2, "0")}`;
        }

        const existing = periodMap.get(periodKey) || {
          ccAmount: 0, csAmount: 0, totalAmount: 0, paymentsAmount: 0,
          ccQuantity: 0, csQuantity: 0, totalQuantity: 0,
        };

        const amt = Number(r.amount) || 0;
        const qty = Number(r.quantity) || 0;

        if (r.transactionType === "sale") {
          if (r.bookType === "CC") {
            existing.ccAmount += amt;
            existing.ccQuantity += qty;
          } else {
            existing.csAmount += amt;
            existing.csQuantity += qty;
          }
          existing.totalAmount += amt;
          existing.totalQuantity += qty;
        } else if (r.transactionType === "payment") {
          existing.paymentsAmount += amt;
        }

        periodMap.set(periodKey, existing);
      }

      let grandTotalAmount = 0;
      let grandTotalQuantity = 0;
      let totalCcAmount = 0;
      let totalCsAmount = 0;
      let totalCcQuantity = 0;
      let totalCsQuantity = 0;
      let grandTotalPayments = 0;

      const items = Array.from(periodMap.entries()).map(([key, v]) => {
        const ccsales = Math.round(v.ccAmount * 100) / 100;
        const cssales = Math.round(v.csAmount * 100) / 100;
        const totalsales = Math.round(v.totalAmount * 100) / 100;
        const totalpayments = Math.round(v.paymentsAmount * 100) / 100;
        const netAmount = Math.round((totalsales - totalpayments) * 100) / 100;

        grandTotalAmount += v.totalAmount;
        grandTotalQuantity += v.totalQuantity;
        totalCcAmount += v.ccAmount;
        totalCsAmount += v.csAmount;
        totalCcQuantity += v.ccQuantity;
        totalCsQuantity += v.csQuantity;
        grandTotalPayments += v.paymentsAmount;

        return {
          period: key,
          ccsales,
          cssales,
          totalsales,
          totalpayments,
          netAmount,
          ccAmount: ccsales,
          csAmount: cssales,
          totalAmount: totalsales,
          ccQuantity: v.ccQuantity,
          csQuantity: v.csQuantity,
          totalQuantity: v.totalQuantity,
        };
      }).sort((a, b) => b.period.localeCompare(a.period));

      const overallSales = Math.round(grandTotalAmount * 100) / 100;
      const overallPayments = Math.round(grandTotalPayments * 100) / 100;
      const overallNet = Math.round((overallSales - overallPayments) * 100) / 100;

      return {
        items,
        summary: {
          totalsales: overallSales,
          totalpayments: overallPayments,
          netAmount: overallNet,
          periodCount: items.length,
          totalCcAmount: Math.round(totalCcAmount * 100) / 100,
          totalCsAmount: Math.round(totalCsAmount * 100) / 100,
          totalCcQuantity,
          totalCsQuantity,
          grandTotalAmount: overallSales,
          grandTotalQuantity,
        },
      };
    }),

  // === 5. Item Performance Report ===
  itemPerformance: publicQuery
    .input(
      z.object({
        startDate: z.string().optional(),
        endDate: z.string().optional(),
      }).optional()
    )
    .query(async ({ input }) => {
      let sql = `
        SELECT bi.itemId, i.name, i.hsnCode, ic.id as categoryId, COALESCE(ic.name, 'Uncategorized') as categoryName, bi.qty, bi.listPrice, bi.discountPercent, bi.taxPercent, bi.amount, b.billDate
        FROM billItems bi
        JOIN items i ON i.id = bi.itemId
        LEFT JOIN itemCategories ic ON ic.id = i.categoryId
        JOIN bills b ON b.id = bi.billId
        JOIN transactions t ON t.id = b.transactionId
        WHERE b.id > 0 AND t.deleted = 0 AND t.includeInReporting = 1
      `;
      const params: any[] = [];

      if (input?.startDate) { sql += ` AND b.billDate >= ?`; params.push(input.startDate); }
      if (input?.endDate) { sql += ` AND b.billDate <= ?`; params.push(input.endDate); }

      const rows = db.prepare(sql).all(...params) as any[];

      let csTxSql = `SELECT t.id, t.totalQuantity, t.amount, t.transactionDate
        FROM transactions t
        WHERE t.deleted = 0 AND t.transactionType = 'sale' AND t.bookType = 'CS' AND t.includeInReporting = 1
        AND NOT EXISTS (SELECT 1 FROM bills b WHERE b.transactionId = t.id AND b.id > 0)
      `;
      const csParams: any[] = [];
      if (input?.startDate) { csTxSql += ` AND t.transactionDate >= ?`; csParams.push(input.startDate); }
      if (input?.endDate) { csTxSql += ` AND t.transactionDate <= ?`; csParams.push(input.endDate); }

      const csTxRows = db.prepare(csTxSql).all(...csParams) as any[];
      const trousersInfo = getTrousersItemInfo();

      const perfMap = new Map<number, {
        itemId: number; name: string; hsnCode: string; categoryId: number | null; categoryName: string;
        totalQty: number; totalsales: number; totalDiscount: number;
        totalTax: number; totalRevenue: number;
      }>();

      for (const r of rows) {
        let entry = perfMap.get(r.itemId);
        if (!entry) {
          entry = { itemId: r.itemId, name: r.name || "Unknown", hsnCode: r.hsnCode || "N/A", categoryId: r.categoryId || null, categoryName: r.categoryName || "Uncategorized", totalQty: 0, totalsales: 0, totalDiscount: 0, totalTax: 0, totalRevenue: 0 };
          perfMap.set(r.itemId, entry);
        }

        const qty = r.qty || 0;
        const listPrice = Number(r.listPrice) || 0;
        const gross = listPrice * qty;
        const discPercent = Number(r.discountPercent) || 0;
        const discAmt = gross * (discPercent / 100);
        const taxable = gross - discAmt;
        const taxPercent = Number(r.taxPercent) || 0;
        const taxAmt = taxable * (taxPercent / 100);
        const finalAmt = Number(r.amount) || (taxable + taxAmt);

        entry.totalQty += qty;
        entry.totalsales += gross;
        entry.totalDiscount += discAmt;
        entry.totalTax += taxAmt;
        entry.totalRevenue += finalAmt;
      }

      let csQty = 0;
      let csRevenue = 0;
      for (const tx of csTxRows) {
        csQty += tx.totalQuantity || 0;
        csRevenue += Number(tx.amount) || 0;
      }

      if (csQty > 0 || csRevenue > 0) {
        let entry = perfMap.get(trousersInfo.id);
        if (!entry) {
          entry = {
            itemId: trousersInfo.id,
            name: trousersInfo.name,
            hsnCode: trousersInfo.hsnCode || "62034200",
            categoryId: trousersInfo.categoryId || null,
            categoryName: trousersInfo.categoryName || "Trousers",
            totalQty: 0, totalsales: 0, totalDiscount: 0, totalTax: 0, totalRevenue: 0,
          };
          perfMap.set(trousersInfo.id, entry);
        }
        entry.totalQty += csQty;
        entry.totalsales += csRevenue;
        entry.totalRevenue += csRevenue;
      }

      const results = Array.from(perfMap.values()).map(r => ({
        ...r,
        totalsales: Math.round(r.totalsales * 100) / 100,
        totalDiscount: Math.round(r.totalDiscount * 100) / 100,
        totalTax: Math.round(r.totalTax * 100) / 100,
        totalRevenue: Math.round(r.totalRevenue * 100) / 100,
      }));

      return results.sort((a, b) => b.totalQty - a.totalQty);
    }),

  // === 6. GST Tax Summary Report ===
  gstTaxReport: publicQuery
    .input(
      z.object({
        startDate: z.string().optional(),
        endDate: z.string().optional(),
      }).optional()
    )
    .query(async ({ input }) => {
      let sql = `SELECT * FROM bills WHERE id > 0`;
      const params: any[] = [];

      if (input?.startDate) { sql += ` AND billDate >= ?`; params.push(input.startDate); }
      if (input?.endDate) { sql += ` AND billDate <= ?`; params.push(input.endDate); }

      const bills = db.prepare(sql).all(...params) as any[];

      const grouped = new Map<string, {
        month: string; billCount: number; taxableAmount: number;
        cgst: number; sgst: number; igst: number;
        totalTax: number; totalAmount: number;
      }>();

      for (const bill of bills) {
        const dateObj = new Date(bill.billDate);
        if (isNaN(dateObj.getTime())) continue;
        const monthKey = `${dateObj.getFullYear()}-${String(dateObj.getMonth() + 1).padStart(2, "0")}`;

        const existing = grouped.get(monthKey) || {
          month: monthKey, billCount: 0, taxableAmount: 0,
          cgst: 0, sgst: 0, igst: 0, totalTax: 0, totalAmount: 0,
        };

        const subtotal = Number(bill.subtotal) || 0;
        const discount = Number(bill.discountAmount) || 0;
        const taxable = subtotal - discount;
        const cgst = Number(bill.cgstAmount) || 0;
        const sgst = Number(bill.sgstAmount) || 0;
        const igst = Number(bill.igstAmount) || 0;
        const tax = Number(bill.totalTax) || 0;
        const total = taxable + tax;

        existing.billCount += 1;
        existing.taxableAmount += taxable;
        existing.cgst += cgst;
        existing.sgst += sgst;
        existing.igst += igst;
        existing.totalTax += tax;
        existing.totalAmount += total;

        grouped.set(monthKey, existing);
      }

      const results = Array.from(grouped.values()).map(r => {
        const [year, month] = r.month.split("-");
        const d = new Date(parseInt(year), parseInt(month) - 1);
        const monthName = d.toLocaleDateString("en-IN", { month: "long", year: "numeric" });

        return {
          monthKey: r.month,
          period: monthName,
          billCount: r.billCount,
          taxableAmount: Math.round(r.taxableAmount * 100) / 100,
          cgst: Math.round(r.cgst * 100) / 100,
          sgst: Math.round(r.sgst * 100) / 100,
          igst: Math.round(r.igst * 100) / 100,
          totalTax: Math.round(r.totalTax * 100) / 100,
          totalAmount: Math.round(r.totalAmount * 100) / 100,
        };
      });

      return results.sort((a, b) => b.monthKey.localeCompare(a.monthKey));
    }),

  // === 7. Transport Performance Report ===
  transportPerformance: publicQuery
    .input(
      z.object({
        startDate: z.string().optional(),
        endDate: z.string().optional(),
      }).optional()
    )
    .query(async ({ input }) => {
      let sql = `SELECT * FROM bills WHERE id > 0`;
      const params: any[] = [];

      if (input?.startDate) { sql += ` AND billDate >= ?`; params.push(input.startDate); }
      if (input?.endDate) { sql += ` AND billDate <= ?`; params.push(input.endDate); }

      const bills = db.prepare(sql).all(...params) as any[];
      const allTransports = db.prepare(`SELECT id, name, vehicleNumber, phone FROM transports`).all() as any[];

      const results = [];
      for (const t of allTransports) {
        const tBills = bills.filter(b => b.transportId === t.id);
        const totalShipments = tBills.length;
        const totalGoodsValue = tBills.reduce((sum, b) => sum + ((b.subtotal || 0) + (b.totalTax || 0) + (b.roundOff || 0)), 0);
        const totalTaxable = tBills.reduce((sum, b) => sum + ((b.subtotal || 0) - (b.discountAmount || 0)), 0);
        const txnIds = tBills.map(b => b.transactionId);
        const totalParcels = txnIds.length > 0
          ? (db.prepare(`SELECT COALESCE(SUM(COALESCE(parcel, 1)), 0) as p FROM transactions WHERE id IN (${txnIds.map(() => '?').join(',')}) AND deleted = 0`).get(...txnIds) as any)?.p || 0
          : 0;

        results.push({
          transportId: t.id,
          name: t.name,
          vehicleNumber: t.vehicleNumber,
          contactPhone: t.phone,
          totalShipments,
          totalGoodsValue,
          totalTaxable,
          totalParcels,
        });
      }

      const unassignedBills = bills.filter(b => !b.transportId);
      if (unassignedBills.length > 0) {
        const totalGoodsValue = unassignedBills.reduce((sum, b) => sum + ((b.subtotal || 0) + (b.totalTax || 0) + (b.roundOff || 0)), 0);
        const totalTaxable = unassignedBills.reduce((sum, b) => sum + ((b.subtotal || 0) - (b.discountAmount || 0)), 0);
        const txnIds = unassignedBills.map(b => b.transactionId);
        const totalParcels = txnIds.length > 0
          ? (db.prepare(`SELECT COALESCE(SUM(COALESCE(parcel, 1)), 0) as p FROM transactions WHERE id IN (${txnIds.map(() => '?').join(',')}) AND deleted = 0`).get(...txnIds) as any)?.p || 0
          : 0;
        results.push({
          transportId: 0,
          name: "Direct / Buyer Pick-up",
          vehicleNumber: "N/A",
          contactPhone: "N/A",
          totalShipments: unassignedBills.length,
          totalGoodsValue,
          totalTaxable,
          totalParcels,
        });
      }

      return results.sort((a, b) => b.totalShipments - a.totalShipments);
    }),

  // === 8. Item Movement Report ===
  itemMovement: publicQuery
    .input(
      z.object({
        startDate: z.string().optional(),
        endDate: z.string().optional(),
      }).optional()
    )
    .query(async ({ input }) => {
      let billSql = `
        SELECT bi.itemId, i.name, i.hsnCode, ic.id as categoryId, COALESCE(ic.name, 'Uncategorized') as categoryName, bi.qty, bi.listPrice, bi.discountPercent, bi.taxPercent, bi.amount, b.billDate
        FROM billItems bi
        JOIN items i ON i.id = bi.itemId
        LEFT JOIN itemCategories ic ON ic.id = i.categoryId
        JOIN bills b ON b.id = bi.billId
        JOIN transactions t ON t.id = b.transactionId
        WHERE b.id > 0 AND t.deleted = 0 AND t.includeInReporting = 1
      `;
      const params: any[] = [];

      if (input?.startDate) { billSql += ` AND b.billDate >= ?`; params.push(input.startDate); }
      if (input?.endDate) { billSql += ` AND b.billDate <= ?`; params.push(input.endDate); }

      const billItemRows = db.prepare(billSql).all(...params) as any[];

      let csTxSql = `SELECT t.id, t.totalQuantity, t.amount, t.transactionDate
        FROM transactions t
        WHERE t.deleted = 0 AND t.transactionType = 'sale' AND t.bookType = 'CS' AND t.includeInReporting = 1
        AND NOT EXISTS (SELECT 1 FROM bills b WHERE b.transactionId = t.id AND b.id > 0)
      `;
      const csParams: any[] = [];
      if (input?.startDate) { csTxSql += ` AND t.transactionDate >= ?`; csParams.push(input.startDate); }
      if (input?.endDate) { csTxSql += ` AND t.transactionDate <= ?`; csParams.push(input.endDate); }

      const csTxRows = db.prepare(csTxSql).all(...csParams) as any[];
      const trousersInfo = getTrousersItemInfo();

      const movementMap = new Map<string, {
        name: string; categoryName: string; hsnCode: string; totalQty: number;
        totalsales: number; totalDiscount: number; totalTax: number; totalRevenue: number;
      }>();

      for (const r of billItemRows) {
        const itemName = r.name || `Item ${r.itemId}`;
        let entry = movementMap.get(itemName);
        if (!entry) {
          entry = { name: itemName, categoryName: r.categoryName || "Uncategorized", hsnCode: r.hsnCode || "N/A", totalQty: 0, totalsales: 0, totalDiscount: 0, totalTax: 0, totalRevenue: 0 };
          movementMap.set(itemName, entry);
        }

        const qty = r.qty || 0;
        const listPrice = Number(r.listPrice) || 0;
        const gross = listPrice * qty;
        const discPercent = Number(r.discountPercent) || 0;
        const discAmt = gross * (discPercent / 100);
        const taxable = gross - discAmt;
        const taxPercent = Number(r.taxPercent) || 0;
        const taxAmt = taxable * (taxPercent / 100);
        const finalAmt = Number(r.amount) || (taxable + taxAmt);

        entry.totalQty += qty;
        entry.totalsales += gross;
        entry.totalDiscount += discAmt;
        entry.totalTax += taxAmt;
        entry.totalRevenue += finalAmt;
      }

      let csQty = 0;
      let csRevenue = 0;
      for (const tx of csTxRows) {
        csQty += tx.totalQuantity || 0;
        csRevenue += Number(tx.amount) || 0;
      }

      if (csQty > 0 || csRevenue > 0) {
        let entry = movementMap.get(trousersInfo.name);
        if (!entry) {
          entry = {
            name: trousersInfo.name,
            categoryName: trousersInfo.categoryName || "Trousers",
            hsnCode: trousersInfo.hsnCode || "62034200",
            totalQty: 0, totalsales: 0, totalDiscount: 0, totalTax: 0, totalRevenue: 0,
          };
          movementMap.set(trousersInfo.name, entry);
        }
        entry.totalQty += csQty;
        entry.totalsales += csRevenue;
        entry.totalRevenue += csRevenue;
      }

      const results = Array.from(movementMap.values()).map(r => ({
        ...r,
        totalsales: Math.round(r.totalsales * 100) / 100,
        totalDiscount: Math.round(r.totalDiscount * 100) / 100,
        totalTax: Math.round(r.totalTax * 100) / 100,
        totalRevenue: Math.round(r.totalRevenue * 100) / 100,
      }));

      return results.sort((a, b) => b.totalQty - a.totalQty);
    }),

  // === 9. Category Statistics Report ===
  categoryStatistics: publicQuery
    .input(
      z.object({
        startDate: z.string().optional(),
        endDate: z.string().optional(),
      }).optional()
    )
    .query(async ({ input }) => {
      let sql = `
        SELECT 
          COALESCE(ic.id, 0) as categoryId,
          COALESCE(ic.name, 'Uncategorized') as categoryName,
          SUM(bi.qty) as totalQty,
          SUM(bi.amount) as totalRevenue,
          COUNT(DISTINCT bi.itemId) as itemCount
        FROM billItems bi
        JOIN items i ON i.id = bi.itemId
        LEFT JOIN itemCategories ic ON ic.id = i.categoryId
        JOIN bills b ON b.id = bi.billId
        JOIN transactions t ON t.id = b.transactionId
        WHERE b.id > 0 AND t.deleted = 0 AND t.includeInReporting = 1
      `;
      const params: any[] = [];

      if (input?.startDate) { sql += ` AND b.billDate >= ?`; params.push(input.startDate); }
      if (input?.endDate) { sql += ` AND b.billDate <= ?`; params.push(input.endDate); }

      sql += ` GROUP BY ic.id, ic.name`;

      const rows = db.prepare(sql).all(...params) as any[];

      let csTxSql = `SELECT t.id, t.totalQuantity, t.amount, t.transactionDate
        FROM transactions t
        WHERE t.deleted = 0 AND t.transactionType = 'sale' AND t.bookType = 'CS' AND t.includeInReporting = 1
        AND NOT EXISTS (SELECT 1 FROM bills b WHERE b.transactionId = t.id AND b.id > 0)
      `;
      const csParams: any[] = [];
      if (input?.startDate) { csTxSql += ` AND t.transactionDate >= ?`; csParams.push(input.startDate); }
      if (input?.endDate) { csTxSql += ` AND t.transactionDate <= ?`; csParams.push(input.endDate); }

      const csTxRows = db.prepare(csTxSql).all(...csParams) as any[];
      const trousersInfo = getTrousersItemInfo();

      const catMap = new Map<number, {
        categoryId: number; categoryName: string; totalQty: number; totalRevenue: number; itemCount: number;
      }>();

      for (const r of rows) {
        catMap.set(r.categoryId, {
          categoryId: r.categoryId,
          categoryName: r.categoryName,
          totalQty: r.totalQty || 0,
          totalRevenue: Number(r.totalRevenue) || 0,
          itemCount: r.itemCount || 0,
        });
      }

      let csQty = 0;
      let csRevenue = 0;
      for (const tx of csTxRows) {
        csQty += tx.totalQuantity || 0;
        csRevenue += Number(tx.amount) || 0;
      }

      if (csQty > 0 || csRevenue > 0) {
        const catId = trousersInfo.categoryId || 0;
        let entry = catMap.get(catId);
        if (!entry) {
          entry = {
            categoryId: catId,
            categoryName: trousersInfo.categoryName || "Trousers",
            totalQty: 0,
            totalRevenue: 0,
            itemCount: 1,
          };
          catMap.set(catId, entry);
        }
        entry.totalQty += csQty;
        entry.totalRevenue += csRevenue;
      }

      const categoryList = Array.from(catMap.values());
      const totalRevenueAll = categoryList.reduce((s, r) => s + r.totalRevenue, 0);
      const totalPiecesAll = categoryList.reduce((s, r) => s + r.totalQty, 0);

      const items = categoryList.map(r => {
        const percent = totalRevenueAll > 0 ? Math.round((r.totalRevenue / totalRevenueAll) * 10000) / 100 : 0;
        return {
          categoryId: r.categoryId,
          categoryName: r.categoryName,
          totalQty: r.totalQty,
          totalRevenue: Math.round(r.totalRevenue * 100) / 100,
          itemCount: r.itemCount,
          revenuePercent: percent,
          percentageContribution: percent,
        };
      }).sort((a, b) => b.totalRevenue - a.totalRevenue);

      return {
        items,
        totalRevenue: Math.round(totalRevenueAll * 100) / 100,
        totalPieces: totalPiecesAll,
        totalCategories: items.length,
      };
    }),
});
