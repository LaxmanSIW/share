import { z } from "zod";
import { createRouter, adminQuery } from "../middleware";
import { findAll } from "../queries/connection";
import type { AuditLog } from "../queries/connection";

export const auditRouter = createRouter({
  list: adminQuery
    .input(
      z.object({
        page: z.number().default(1),
        limit: z.number().default(25),
        tableName: z.string().optional(),
        action: z.enum(["UPDATE", "DELETE", "ALL"]).default("ALL"),
      }).optional()
    )
    .query(async ({ input }) => {
      const page = input?.page || 1;
      const limit = input?.limit || 25;
      const offset = (page - 1) * limit;

      let items = findAll<AuditLog>("auditLogs");

      // Apply filters
      if (input?.tableName) {
        items = items.filter(a => a.tableName === input.tableName);
      }
      if (input?.action && input.action !== "ALL") {
        items = items.filter(a => a.action === input.action);
      }

      // Sort by createdAt desc
      items.sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());

      const total = items.length;
      const paginatedItems = items.slice(offset, offset + limit);

      return {
        items: paginatedItems,
        total,
        page,
        totalPages: Math.ceil(total / limit),
      };
    }),
});
