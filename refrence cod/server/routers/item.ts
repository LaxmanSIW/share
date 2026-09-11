import { z } from "zod";
import { createRouter, publicQuery } from "../middleware";
import { db } from "../db/engine";
import { findById, insert, update, remove } from "../queries/connection";
import type { Item } from "../queries/connection";

export const itemRouter = createRouter({
  list: publicQuery
    .input(
      z.object({
        search: z.string().optional(),
        sortBy: z.string().optional(),
        sortOrder: z.enum(["asc", "desc"]).default("asc"),
      }).optional()
    )
    .query(async ({ input }) => {
      let items = (db.prepare(`SELECT i.*, ic.name as categoryName FROM items i LEFT JOIN itemCategories ic ON ic.id = i.categoryId`).all() as any[]);

      if (input?.search) {
        const searchLower = input.search.toLowerCase();
        items = items.filter(
          (item) =>
            item.name.toLowerCase().includes(searchLower) ||
            (item.hsnCode && item.hsnCode.toLowerCase().includes(searchLower))
        );
      }

      if (input?.sortBy) {
        const key = input.sortBy as any;
        items.sort((a, b) => {
          const valA = String(a[key] || "");
          const valB = String(b[key] || "");
          if (input.sortOrder === "desc") {
            return valB.localeCompare(valA, undefined, { numeric: true });
          }
          return valA.localeCompare(valB, undefined, { numeric: true });
        });
      } else {
        items.sort((a, b) => a.name.localeCompare(b.name));
      }

      return {
        items,
        total: items.length,
      };
    }),

  create: publicQuery
    .input(
      z.object({
        name: z.string().min(1),
        hsnCode: z.string().min(1),
        listPrice: z.number().min(0),
        unit: z.string().min(1),
        taxPercent: z.number().min(0).max(100),
        categoryId: z.number().nullable().optional(),
      })
    )
    .mutation(async ({ input }) => {
      const result = insert<Item>("items", {
        name: input.name,
        hsnCode: input.hsnCode,
        listPrice: input.listPrice,
        unit: input.unit,
        taxPercent: input.taxPercent,
        categoryId: input.categoryId || null,
      } as any);

      return { id: result.id, item: result, message: "Item created successfully" };
    }),

  update: publicQuery
    .input(
      z.object({
        id: z.number(),
        name: z.string().optional(),
        hsnCode: z.string().optional(),
        listPrice: z.number().optional(),
        unit: z.string().optional(),
        taxPercent: z.number().optional(),
        categoryId: z.number().nullable().optional(),
      })
    )
    .mutation(async ({ input }) => {
      const { id, ...updateData } = input;
      const updateValues: Record<string, any> = {};

      if (updateData.name !== undefined) updateValues.name = updateData.name;
      if (updateData.hsnCode !== undefined) updateValues.hsnCode = updateData.hsnCode;
      if (updateData.listPrice !== undefined) updateValues.listPrice = updateData.listPrice;
      if (updateData.unit !== undefined) updateValues.unit = updateData.unit;
      if (updateData.taxPercent !== undefined) updateValues.taxPercent = updateData.taxPercent;
      if (updateData.categoryId !== undefined) updateValues.categoryId = updateData.categoryId;

      const result = update<Item>("items", id, updateValues as any);
      if (!result) throw new Error("Item not found");

      return { id, item: result, message: "Item updated successfully" };
    }),

  delete: publicQuery
    .input(z.object({ id: z.number(), reason: z.string().optional() }))
    .mutation(async ({ input }) => {
      const item = findById<Item>("items", input.id);
      if (!item) throw new Error("Item not found");

      if (item.name.trim().toLowerCase() === "trousers") {
        throw new Error("Default system item 'Trousers' cannot be deleted.");
      }

      const reason = input.reason || "Item deleted by user";
      const success = remove<Item>("items", input.id);
      if (!success) throw new Error("Item not found");

      insert<any>("auditLogs", {
        tableName: "items",
        recordId: input.id,
        action: "DELETE",
        oldValues: item,
        newValues: null,
        userId: "system",
        reason,
        createdAt: new Date().toISOString(),
      });

      return { message: "Item deleted and logged to audit trail" };
    }),

  detail: publicQuery
    .input(z.object({ id: z.number() }))
    .query(async ({ input }) => {
      const item = db.prepare(`SELECT i.*, ic.name as categoryName FROM items i LEFT JOIN itemCategories ic ON ic.id = i.categoryId WHERE i.id = ?`).get(input.id);
      if (!item) throw new Error("Item not found");
      return item;
    }),
});
