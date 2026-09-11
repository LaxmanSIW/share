import type { FetchCreateContextFnOptions } from "@trpc/server/adapters/fetch";
import type { User } from "./db/types";
import {
  verifyLocalAuthToken,
  getAuthCookie,
  getUserByUsername,
  createLocalAuthToken,
  serializeAuthCookie,
  REFRESH_THRESHOLD_SECONDS,
} from "./local-auth";

export type TrpcContext = {
  req: Request;
  resHeaders: Headers;
  user?: User;
};

export async function createContext(
  opts: FetchCreateContextFnOptions,
): Promise<TrpcContext> {
  const ctx: TrpcContext = { req: opts.req, resHeaders: opts.resHeaders };
  try {
   
    const token = getAuthCookie(opts.req.headers);
    if (token) {
      const payload = await verifyLocalAuthToken(token);
      if (payload) {
        // Load the real user row (id, name, email, role, etc.) instead of
        // fabricating one — keeps `ctx.user.role` in sync with the DB and
        // matches the User type ("admin" | "staff" | null), not "user".
        const dbUser = getUserByUsername(payload.username);
        if (dbUser) {
          ctx.user = dbUser;

          // ── Sliding session (optimized) ──────────────────────
          // The token that just arrived was still valid (verifyLocalAuthToken
          // didn't throw/return null), so this is an active user, not an
          // expired one. Rather than re-signing a new JWT on every single
          // request, only do it once the current token has less than
          // REFRESH_THRESHOLD_SECONDS left. This still keeps active users
          // logged in indefinitely (the clock resets well before it would
          // expire), but skips unnecessary signing work on most requests.
          // 30 minutes of total inactivity still lets the old token
          // actually expire (verifyLocalAuthToken above will then fail
          // and ctx.user simply won't be set on the next request).
          const nowSeconds = Math.floor(Date.now() / 1000);
          const remainingSeconds = (payload.exp ?? 0) - nowSeconds;

          if (remainingSeconds < REFRESH_THRESHOLD_SECONDS) {
            const freshToken = await createLocalAuthToken({
              username: payload.username,
              role: payload.role,
            });
            ctx.resHeaders.append("set-cookie", serializeAuthCookie(freshToken));
          }
        }
      }
    }
  } catch {
    // Authentication is optional here
  }
  return ctx;
}
