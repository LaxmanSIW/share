// categories.ts
import { z } from "zod";
import { createRouter, publicQuery } from "../middleware";
import { db } from "../db/engine";
import { insert, update, remove } from "../queries/connection";

export const categoryRouter = createRouter({
  list: publicQuery
    .input(
      z.object({
        search: z.string().optional(),
        sortBy: z.string().optional(),
        sortOrder: z.enum(["asc", "desc"]).default("asc"),
      }).optional()
    )
    .query(async ({ input }) => {
      let categories = db.prepare(`SELECT * FROM itemCategories`).all() as any[];

      if (input?.search) {
        const searchLower = input.search.toLowerCase();
        categories = categories.filter(
          (cat) => cat.name.toLowerCase().includes(searchLower)
        );
      }

      if (input?.sortBy) {
        const key = input.sortBy as any;
        categories.sort((a, b) => {
          const valA = String(a[key] || "");
          const valB = String(b[key] || "");
          if (input.sortOrder === "desc") {
            return valB.localeCompare(valA, undefined, { numeric: true });
          }
          return valA.localeCompare(valB, undefined, { numeric: true });
        });
      } else {
        categories.sort((a, b) => a.name.localeCompare(b.name));
      }

      return {
        categories,
        total: categories.length,
      };
    }),

  create: publicQuery
    .input(
      z.object({
        name: z.string().min(1),
      })
    )
    .mutation(async ({ input }) => {
      const result = insert("itemCategories", {
        name: input.name,
      } as any);

      return { id: result.id, category: result, message: "Category created successfully" };
    }),

  update: publicQuery
    .input(
      z.object({
        id: z.number(),
        name: z.string().min(1),
      })
    )
    .mutation(async ({ input }) => {
      const result = update("itemCategories", input.id, { name: input.name } as any);
      if (!result) throw new Error("Category not found");

      return { id: input.id, category: result, message: "Category updated successfully" };
    }),

  delete: publicQuery
    .input(z.object({ id: z.number(), reason: z.string().optional() }))
    .mutation(async ({ input }) => {
      const category = db.prepare(`SELECT * FROM itemCategories WHERE id = ?`).get(input.id) as any;
      if (!category) throw new Error("Category not found");

      const reason = input.reason || "Category deleted by user";
      const success = remove("itemCategories", input.id);
      if (!success) throw new Error("Category not found");

      insert<any>("auditLogs", {
        tableName: "itemCategories",
        recordId: input.id,
        action: "DELETE",
        oldValues: category,
        newValues: null,
        userId: "system",
        reason,
        createdAt: new Date().toISOString(),
      });

      return { message: "Category deleted and logged to audit trail" };
    }),
});