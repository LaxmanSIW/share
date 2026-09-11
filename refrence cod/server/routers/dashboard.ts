// dashboard.ts
import { z } from "zod";
import { createRouter, publicQuery } from "../middleware";
import { db } from "../db/engine";
import { getMonthRange } from "../lib/utils";

export const dashboardRouter = createRouter({
  // === All-Time Accounts Overview ===
  allTimeStats: publicQuery
    .input(
      z.object({
        bookType: z.enum(["CC", "CS", "ALL"]).optional().default("ALL"),
      }).optional()
    )
    .query(async ({ input }) => {
      const bookFilter = input?.bookType && input.bookType !== "ALL" ? `AND bookType = '${input.bookType}'` : "";
      
      const sql = `
        SELECT 
          SUM(CASE WHEN transactionType = 'sale' THEN amount ELSE 0 END) as totalSaleAmount,
          SUM(CASE WHEN transactionType = 'payment' THEN amount ELSE 0 END) as totalPaymentAmount,
          SUM(CASE WHEN transactionType = 'sale' AND includeInReporting = 1 THEN totalQuantity ELSE 0 END) as totalPiecesSold,
          SUM(CASE WHEN transactionType = 'sale' AND parcel IS NOT NULL AND parcel > 0 THEN parcel ELSE 0 END) as totalParcelsSent
        FROM transactions
        WHERE deleted = 0 ${bookFilter}
      `;
      const row = db.prepare(sql).get() as any;
      
      const totalSale = row?.totalSaleAmount || 0;
      const totalPay = row?.totalPaymentAmount || 0;
      
      return {
        totalSaleAmount: totalSale,
        totalPaymentAmount: totalPay,
        outstandingBalance: totalSale - totalPay,
        totalPiecesSold: row?.totalPiecesSold || 0,
        totalParcelsSent: row?.totalParcelsSent || 0,
      };
    }),

  // === Current Period & Logistics Performance ===
  currentPeriodStats: publicQuery
    .input(
      z.object({
        bookType: z.enum(["CC", "CS", "ALL"]).optional().default("ALL"),
      }).optional()
    )
    .query(async ({ input }) => {
      const now = new Date();
      const currentMonth = now.getMonth() + 1;
      const currentYear = now.getFullYear();
      
      const { startStr: currStart, endStr: currEnd } = getMonthRange(currentYear, currentMonth);
      
      const lastMonthDate = new Date(currentYear, currentMonth - 2, 1);
      const { startStr: lastStart, endStr: lastEnd } = getMonthRange(lastMonthDate.getFullYear(), lastMonthDate.getMonth() + 1);

      const bookFilter = input?.bookType && input.bookType !== "ALL" ? `AND bookType = '${input.bookType}'` : "";

      const baseSql = `
        SELECT 
          SUM(CASE WHEN transactionType = 'sale' THEN amount ELSE 0 END) as totalSales,
          SUM(CASE WHEN transactionType = 'payment' THEN amount ELSE 0 END) as totalPayments,
          SUM(CASE WHEN transactionType = 'sale' AND includeInReporting = 1 THEN totalQuantity ELSE 0 END) as totalPieces,
          SUM(CASE WHEN transactionType = 'sale' AND parcel IS NOT NULL AND parcel > 0 THEN parcel ELSE 0 END) as totalParcels
        FROM transactions
        WHERE deleted = 0 AND transactionDate >= ? AND transactionDate <= ? ${bookFilter}
      `;
      
      const currRow = db.prepare(baseSql).get(currStart, currEnd) as any;
      const lastRow = db.prepare(baseSql).get(lastStart, lastEnd) as any;

      const calcTrend = (curr: number, last: number) => {
        if (last === 0) return curr > 0 ? 100 : 0;
        return parseFloat((((curr - last) / Math.abs(last)) * 100).toFixed(1));
      };

      return {
        totalSales: currRow?.totalSales || 0,
        totalPayments: currRow?.totalPayments || 0,
        totalPieces: currRow?.totalPieces || 0,
        totalParcels: currRow?.totalParcels || 0,
        trends: {
          sales: calcTrend(currRow?.totalSales || 0, lastRow?.totalSales || 0),
          payments: calcTrend(currRow?.totalPayments || 0, lastRow?.totalPayments || 0),
          pieces: calcTrend(currRow?.totalPieces || 0, lastRow?.totalPieces || 0),
          parcels: calcTrend(currRow?.totalParcels || 0, lastRow?.totalParcels || 0),
        },
        periodLabel: `${new Date(currentYear, currentMonth - 1).toLocaleString("default", { month: "long" })} ${currentYear}`,
      };
    }),

  // === Top 5 Debtors (All Time) ===
  topDebtors: publicQuery
    .input(
      z.object({
        limit: z.number().default(5),
        bookType: z.enum(["CC", "CS", "ALL"]).optional().default("ALL"),
      }).optional()
    )
    .query(async ({ input }) => {
      const limit = input?.limit || 5;
      const bookFilter = input?.bookType && input.bookType !== "ALL" ? `AND t.bookType = '${input.bookType}'` : "";
      
      const sql = `
        SELECT b.id as buyerId, b.companyName, 
               SUM(CASE WHEN t.transactionType = 'sale' THEN t.amount ELSE 0 END) - 
               SUM(CASE WHEN t.transactionType = 'payment' THEN t.amount ELSE 0 END) as outstanding
        FROM buyers b
        JOIN transactions t ON b.id = t.buyerId
        WHERE t.deleted = 0 AND b.id > 0 ${bookFilter}
        GROUP BY b.id, b.companyName
        HAVING outstanding > 0
        ORDER BY outstanding DESC
        LIMIT ?
      `;
      return db.prepare(sql).all(limit) as any[];
    }),

  // === Top 5 Paymasters (All Time) ===
  topPaymasters: publicQuery
    .input(
      z.object({
        limit: z.number().default(5),
        bookType: z.enum(["CC", "CS", "ALL"]).optional().default("ALL"),
      }).optional()
    )
    .query(async ({ input }) => {
      const limit = input?.limit || 5;
      const bookFilter = input?.bookType && input.bookType !== "ALL" ? `AND t.bookType = '${input.bookType}'` : "";

      const sql = `
        SELECT b.id as buyerId, b.companyName, SUM(t.amount) as totalPaid
        FROM buyers b
        JOIN transactions t ON b.id = t.buyerId
        WHERE t.deleted = 0 AND t.transactionType = 'payment' AND b.id > 0 ${bookFilter}
        GROUP BY b.id, b.companyName
        ORDER BY totalPaid DESC
        LIMIT ?
      `;
      return db.prepare(sql).all(limit) as any[];
    }),

  // === Volume Leaders (All Time) ===
  topVolumeBuyers: publicQuery
    .input(
      z.object({
        limit: z.number().default(5),
        bookType: z.enum(["CC", "CS", "ALL"]).optional().default("ALL"),
      }).optional()
    )
    .query(async ({ input }) => {
      const limit = input?.limit || 5;
      const bookFilter = input?.bookType && input.bookType !== "ALL" ? `AND t.bookType = '${input.bookType}'` : "";
      
      const sql = `
        SELECT b.id as buyerId, b.companyName, SUM(t.totalQuantity) as totalQuantity
        FROM buyers b
        JOIN transactions t ON b.id = t.buyerId
        WHERE t.deleted = 0 AND t.transactionType = 'sale' AND t.includeInReporting = 1 AND b.id > 0 ${bookFilter}
        GROUP BY b.id, b.companyName
        ORDER BY totalQuantity DESC
        LIMIT ?
      `;
      return db.prepare(sql).all(limit) as any[];
    }),

  // === Monthly Trends (Sales vs Payments) ===
  monthlyTrends: publicQuery
    .input(
      z.object({
        months: z.number().default(12),
        bookType: z.enum(["CC", "CS", "ALL"]).optional().default("ALL"),
      }).optional()
    )
    .query(async ({ input }) => {
      const months = input?.months || 12;
      const bookFilter = input?.bookType && input.bookType !== "ALL" ? `AND bookType = '${input.bookType}'` : "";

      const now = new Date();
      const d = new Date(now.getFullYear(), now.getMonth() - months + 1, 1);
      const cutoffStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-01`;

      const sql = `
        SELECT strftime('%Y-%m', transactionDate) as monthStr, 
               SUM(CASE WHEN transactionType = 'sale' THEN amount ELSE 0 END) as sales,
               SUM(CASE WHEN transactionType = 'payment' THEN amount ELSE 0 END) as payments
        FROM transactions
        WHERE deleted = 0 AND transactionDate >= ? ${bookFilter}
        GROUP BY monthStr
        ORDER BY monthStr ASC
      `;
      const rows = db.prepare(sql).all(cutoffStr) as any[];

      const data = [];
      const rowMap = new Map(rows.map(r => [r.monthStr, r]));

      for (let i = months - 1; i >= 0; i--) {
        const cur = new Date(now.getFullYear(), now.getMonth() - i, 1);
        const mStr = `${cur.getFullYear()}-${String(cur.getMonth() + 1).padStart(2, "0")}`;
        const row = rowMap.get(mStr) || { sales: 0, payments: 0 };
        data.push({
          month: cur.toLocaleString("default", { month: "short" }),
          year: cur.getFullYear(),
          sales: row.sales || 0,
          payments: row.payments || 0,
        });
      }

      return data;
    }),

  // === Monthly Trouser Movement ===
  monthlyQuantity: publicQuery
    .input(
      z.object({
        months: z.number().default(12),
        bookType: z.enum(["CC", "CS", "ALL"]).optional().default("ALL"),
      }).optional()
    )
    .query(async ({ input }) => {
      const months = input?.months || 12;
      const bookFilter = input?.bookType && input.bookType !== "ALL" ? `AND bookType = '${input.bookType}'` : "";

      const now = new Date();
      const d = new Date(now.getFullYear(), now.getMonth() - months + 1, 1);
      const cutoffStr = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-01`;

      const sql = `
        SELECT strftime('%Y-%m', transactionDate) as monthStr, 
               SUM(totalQuantity) as quantity
        FROM transactions
        WHERE deleted = 0 AND transactionType = 'sale' AND includeInReporting = 1 AND transactionDate >= ? ${bookFilter}
        GROUP BY monthStr
        ORDER BY monthStr ASC
      `;
      const rows = db.prepare(sql).all(cutoffStr) as any[];

      const data = [];
      const rowMap = new Map(rows.map(r => [r.monthStr, r]));

      for (let i = months - 1; i >= 0; i--) {
        const cur = new Date(now.getFullYear(), now.getMonth() - i, 1);
        const mStr = `${cur.getFullYear()}-${String(cur.getMonth() + 1).padStart(2, "0")}`;
        const row = rowMap.get(mStr) || { quantity: 0 };
        data.push({
          month: cur.toLocaleString("default", { month: "short" }),
          year: cur.getFullYear(),
          quantity: row.quantity || 0,
        });
      }

      return data;
    }),

  // === Parcel Counts Analysis ===
  parcelStats: publicQuery
    .input(
      z.object({
        view: z.enum(["Day", "Week", "Month", "Year"]).default("Month"),
        bookType: z.enum(["CC", "CS", "ALL"]).optional().default("ALL"),
      })
    )
    .query(async ({ input }) => {
      const { view, bookType } = input;
      let bookFilter = "";
      if (bookType && bookType !== "ALL") {
        bookFilter = `AND bookType = '${bookType}'`;
      }

      const chartMap = new Map<string, number>();

      if (view === "Day") {
        const now = new Date();
        const start = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 29);
        const startStr = start.toISOString().split("T")[0];

        const sql = `SELECT transactionDate, SUM(parcel) as count FROM transactions WHERE deleted = 0 AND parcel IS NOT NULL AND parcel > 0 AND transactionDate >= ? ${bookFilter} GROUP BY transactionDate`;
        const rows = db.prepare(sql).all(startStr) as any[];
        
        for (let i = 29; i >= 0; i--) {
          const d = new Date(now.getFullYear(), now.getMonth(), now.getDate() - i);
          chartMap.set(d.toISOString().split("T")[0], 0);
        }
        for (const r of rows) if (chartMap.has(r.transactionDate)) chartMap.set(r.transactionDate, r.count);

      } else if (view === "Week") {
        const now = new Date();
        const start = new Date(now.getTime() - 11 * 7 * 24 * 60 * 60 * 1000);
        const startStr = start.toISOString().split("T")[0];

        const sql = `SELECT transactionDate, parcel FROM transactions WHERE deleted = 0 AND parcel IS NOT NULL AND parcel > 0 AND transactionDate >= ? ${bookFilter}`;
        const rows = db.prepare(sql).all(startStr) as any[];

        const weekLabels: string[] = [];
        for (let i = 11; i >= 0; i--) {
          const d = new Date(now.getTime() - i * 7 * 24 * 60 * 60 * 1000);
          const startOfWeek = new Date(d);
          const day = startOfWeek.getDay();
          startOfWeek.setDate(startOfWeek.getDate() - day + (day === 0 ? -6 : 1));
          const label = `Wk ${String(startOfWeek.getDate()).padStart(2, "0")}/${String(startOfWeek.getMonth() + 1).padStart(2, "0")}`;
          chartMap.set(label, 0);
          weekLabels.push(label);
        }

        for (const row of rows) {
          const billTime = new Date(row.transactionDate).getTime();
          for (let i = 11; i >= 0; i--) {
            const d = new Date(now.getTime() - i * 7 * 24 * 60 * 60 * 1000);
            const startOfWeek = new Date(d);
            const day = startOfWeek.getDay();
            startOfWeek.setDate(startOfWeek.getDate() - day + (day === 0 ? -6 : 1));
            startOfWeek.setHours(0, 0, 0, 0);
            const endOfWeek = new Date(startOfWeek);
            endOfWeek.setDate(startOfWeek.getDate() + 7);

            if (billTime >= startOfWeek.getTime() && billTime < endOfWeek.getTime()) {
              const label = weekLabels[11 - i];
              chartMap.set(label, chartMap.get(label)! + row.parcel);
              break;
            }
          }
        }
      } else if (view === "Month") {
        const now = new Date();
        const start = new Date(now.getFullYear(), now.getMonth() - 11, 1);
        const startStr = `${start.getFullYear()}-${String(start.getMonth() + 1).padStart(2, "0")}-01`;

        const sql = `SELECT strftime('%Y-%m', transactionDate) as mStr, SUM(parcel) as count FROM transactions WHERE deleted = 0 AND parcel IS NOT NULL AND parcel > 0 AND transactionDate >= ? ${bookFilter} GROUP BY mStr`;
        const rows = db.prepare(sql).all(startStr) as any[];

        for (let i = 11; i >= 0; i--) {
          const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
          const label = d.toLocaleString("default", { month: "short" }) + " " + String(d.getFullYear()).slice(-2);
          chartMap.set(label, 0);
        }

        for (const r of rows) {
          const [y, m] = r.mStr.split('-');
          const d = new Date(parseInt(y), parseInt(m) - 1, 1);
          const label = d.toLocaleString("default", { month: "short" }) + " " + String(d.getFullYear()).slice(-2);
          if (chartMap.has(label)) chartMap.set(label, chartMap.get(label)! + r.count);
        }
      } else if (view === "Year") {
        const now = new Date();
        const startYear = now.getFullYear() - 4;
        const startStr = `${startYear}-01-01`;

        const sql = `SELECT strftime('%Y', transactionDate) as yStr, SUM(parcel) as count FROM transactions WHERE deleted = 0 AND parcel IS NOT NULL AND parcel > 0 AND transactionDate >= ? ${bookFilter} GROUP BY yStr`;
        const rows = db.prepare(sql).all(startStr) as any[];

        for (let i = 4; i >= 0; i--) {
          chartMap.set(String(now.getFullYear() - i), 0);
        }
        for (const r of rows) {
          if (chartMap.has(r.yStr)) chartMap.set(r.yStr, chartMap.get(r.yStr)! + r.count);
        }
      }

      const chartData = Array.from(chartMap.entries()).map(([label, value]) => ({ label, value }));
      
      const totalSql = `SELECT SUM(parcel) as count FROM transactions WHERE deleted = 0 AND parcel IS NOT NULL AND parcel > 0 ${bookFilter}`;
      const totalRow = db.prepare(totalSql).get() as any;

      return {
        totalParcels: totalRow?.count || 0,
        chartData,
      };
    }),
});