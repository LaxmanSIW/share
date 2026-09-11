import { authRouter } from "./auth-router";
import { createRouter, publicQuery } from "./middleware";
import { dashboardRouter } from "./routers/dashboard";
import { transactionRouter } from "./routers/transaction";
import { buyerRouter } from "./routers/buyer";
import { reportRouter } from "./routers/report";
import { auditRouter } from "./routers/audit";
import { settingsRouter } from "./routers/settings";
import { itemRouter } from "./routers/item";
import { billRouter } from "./routers/bill";
import { transportRouter } from "./routers/transport";
import { categoryRouter } from "./routers/categories";

export const appRouter = createRouter({
  ping: publicQuery.query(() => ({ ok: true, ts: Date.now() })),
  auth: authRouter,
  dashboard: dashboardRouter,
  transaction: transactionRouter,
  buyer: buyerRouter,
  report: reportRouter,
  audit: auditRouter,
  settings: settingsRouter,
  item: itemRouter,
  bill: billRouter,
  transport: transportRouter,
  category: categoryRouter,
});

export type AppRouter = typeof appRouter;
