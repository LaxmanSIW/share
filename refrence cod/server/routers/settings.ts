import { z } from "zod";
import { createRouter, authedQuery, publicQuery } from "../middleware";
import { findAll, resetNextId, update } from "../queries/connection";
import type { Buyer, Transaction, Company } from "../queries/connection";

export const settingsRouter = createRouter({
  summary: authedQuery.query(() => {
    const buyers = findAll<Buyer>("buyers");
    const transactions = findAll<Transaction>("transactions").filter((tx) => !tx.deleted);
    return {
      buyerCount: buyers.length,
      transactionCount: transactions.length,
      nextBuyerId: buyers.reduce((max, row) => Math.max(max, row.id || 0), 0) + 1,
      nextTransactionId: transactions.reduce((max, row) => Math.max(max, row.id || 0), 0) + 1,
    };
  }),

  resetBuyerIds: authedQuery.mutation(() => {
    const nextBuyerId = resetNextId("buyers");
    return { success: true, nextBuyerId };
  }),

  resetTransactionIds: authedQuery.mutation(() => {
    const nextTransactionId = resetNextId("transactions");
    return { success: true, nextTransactionId };
  }),

  getCompany: publicQuery.query(() => {
    const companies = findAll<Company>("companies");
    if (companies.length === 0) {
      return {
        id: 1,
        companyName: "Alpha Wholesale",
        address: "123 Commercial Belt, Sector 4, Noida, Uttar Pradesh",
        phone: "+91 9999999999",
        email: "company@gmail.com",
        gstNumber: "09AAAAA1234A1Z2",
        bankName: "ICICI Bank",
        accountNumber: "123456789",
        ifscCode: "ICIC11222",
        branchName: "Noida",
        authorizedSignatory: "Add Name",
        terms: [
          "1. Goods once sold will not be taken back.",
          "2. Interest @ 18% p.a. will be charged if the payment for Company Name is not made within the stipulated time.",
          "3. Subject to 'Delhi' Jurisdiction only."
        ],
        startingBillNumber: "0001",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString()
      };
    }
    const comp = companies[0];
    if (!comp.startingBillNumber) {
      comp.startingBillNumber = "0001";
    }
    return comp;
  }),

  updateCompany: publicQuery
    .input(
      z.object({
        companyName: z.string().min(1),
        address: z.string().min(1),
        state: z.string().optional(),
        stateCode: z.string().optional(),
        phone: z.string().min(1),
        email: z.string().email(),
        gstNumber: z.string().min(1),
        bankName: z.string().min(1),
        accountNumber: z.string().min(1),
        ifscCode: z.string().min(1),
        branchName: z.string().min(1),
        authorizedSignatory: z.string().min(1),
        terms: z.array(z.string()),
        startingBillNumber: z.string().nullable().optional(),
      })
    )
    .mutation(async ({ input }) => {
      const result = update<Company>("companies", 1, {
        ...input,
        startingBillNumber: input.startingBillNumber || "0001",
        updatedAt: new Date().toISOString(),
      });
      return { success: true, company: result };
    }),
});
