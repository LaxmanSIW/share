import { z } from "zod";
import { createRouter, publicQuery, authedQuery } from "./middleware";
import {
  validateCredentials,
  createLocalAuthToken,
  serializeAuthCookie,
  serializeClearCookie,
  updateCredentials,
  isDefaultCredentials,
  ensureAdminExists,
  getUserByUsername,
} from "./local-auth";

export const authRouter = createRouter({
  me: authedQuery.query((opts) => opts.ctx.user),

  checkDefault: publicQuery.query(() => {
   
    ensureAdminExists();
    return { isDefault: isDefaultCredentials() };
  }),

  login: publicQuery
    .input(
      z.object({
        username: z.string().min(1),
        password: z.string().min(1),
      })
    )
    .mutation(async ({ input, ctx }) => {
      if (!validateCredentials(input.username, input.password)) {
        throw new Error("Invalid username or password");
      }

      const user = getUserByUsername(input.username);
      if (!user) {
        throw new Error("Invalid username or password");
      }

      const token = await createLocalAuthToken({
        username: user.username,
        role: user.role ?? "admin",
      });

      ctx.resHeaders.append("set-cookie", serializeAuthCookie(token));

      return {
        success: true,
        user: {
          id: user.id,
          name: user.name ?? user.username,
          role: user.role,
          email: user.email ?? `${user.username}@local`,
        },
      };
    }),

  changePassword: authedQuery
    .input(
      z.object({
        currentPassword: z.string().min(1),
        newPassword: z.string().min(4).optional(),
        newUsername: z.string().min(1).optional(),
      })
    )
    .mutation(async ({ input, ctx }) => {
      // Use the real username, not the display name.
      const username = ctx.user!.username;
      const updated = updateCredentials(username, input.currentPassword, input.newPassword, input.newUsername);

      const user = getUserByUsername(updated.username!);
      const token = await createLocalAuthToken({
        username: updated.username!,
        role: user?.role ?? "admin",
      });

      ctx.resHeaders.append("set-cookie", serializeAuthCookie(token));

      return {
        success: true,
        username: updated.username,
      };
    }),

  logout: authedQuery.mutation(async ({ ctx }) => {
    ctx.resHeaders.append("set-cookie", serializeClearCookie());
    return { success: true };
  }),
});
