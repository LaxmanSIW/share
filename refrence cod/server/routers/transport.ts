import { z } from "zod";
import { createRouter, publicQuery } from "../middleware";
import { findAll, findById, insert, update, remove } from "../queries/connection";
import type { Transport } from "../queries/connection";

export const transportRouter = createRouter({
  list: publicQuery
    .input(
      z.object({
        search: z.string().optional(),
        sortBy: z.string().optional(),
        sortOrder: z.enum(["asc", "desc"]).default("asc"),
      }).optional()
    )
    .query(async ({ input }) => {
      let transports = findAll<Transport>("transports");

      if (input?.search) {
        const searchLower = input.search.toLowerCase();
        transports = transports.filter(
          (t) =>
            t.name.toLowerCase().includes(searchLower) ||
            (t.vehicleNumber && t.vehicleNumber.toLowerCase().includes(searchLower))
        );
      }

      if (input?.sortBy) {
        const key = input.sortBy as keyof Transport;
        transports.sort((a, b) => {
          const valA = String(a[key] || "");
          const valB = String(b[key] || "");
          if (input.sortOrder === "desc") {
            return valB.localeCompare(valA, undefined, { numeric: true });
          }
          return valA.localeCompare(valB, undefined, { numeric: true });
        });
      } else {
        transports.sort((a, b) => a.name.localeCompare(b.name));
      }

      return {
        transports,
        total: transports.length,
      };
    }),

  create: publicQuery
    .input(
      z.object({
        name: z.string().min(1),
        phone: z.string().nullable().optional(),
        vehicleNumber: z.string().nullable().optional(),
      })
    )
    .mutation(async ({ input }) => {
      const now = new Date().toISOString();
      const result = insert<Transport>("transports", {
        name: input.name,
        phone: input.phone || null,
        vehicleNumber: input.vehicleNumber || null,
        createdAt: now,
        updatedAt: now,
      });

      return { id: result.id, transport: result, message: "Transport created successfully" };
    }),

  update: publicQuery
    .input(
      z.object({
        id: z.number(),
        name: z.string().optional(),
        phone: z.string().nullable().optional(),
        vehicleNumber: z.string().nullable().optional(),
      })
    )
    .mutation(async ({ input }) => {
      const { id, ...updateData } = input;
      const updateValues: Partial<Transport> = {};

      if (updateData.name !== undefined) updateValues.name = updateData.name;
      if (updateData.phone !== undefined) updateValues.phone = updateData.phone || null;
      if (updateData.vehicleNumber !== undefined) updateValues.vehicleNumber = updateData.vehicleNumber || null;
      updateValues.updatedAt = new Date().toISOString();

      const result = update<Transport>("transports", id, updateValues);
      if (!result) throw new Error("Transport not found");

      return { id, transport: result, message: "Transport updated successfully" };
    }),

  delete: publicQuery
    .input(z.object({ id: z.number(), reason: z.string().optional() }))
    .mutation(async ({ input }) => {
      const transport = findById<Transport>("transports", input.id);
      if (!transport) throw new Error("Transport agency not found");

      const reason = input.reason || "Transport agency deleted by user";
      const success = remove<Transport>("transports", input.id);
      if (!success) throw new Error("Transport agency not found");

      insert<any>("auditLogs", {
        tableName: "transports",
        recordId: input.id,
        action: "DELETE",
        oldValues: transport,
        newValues: null,
        userId: "system",
        reason,
        createdAt: new Date().toISOString(),
      });

      return { message: "Transport agency deleted and logged to audit trail" };
    }),

  detail: publicQuery
    .input(z.object({ id: z.number() }))
    .query(async ({ input }) => {
      const transport = findById<Transport>("transports", input.id);
      if (!transport) throw new Error("Transport not found");
      return transport;
    }),
});
