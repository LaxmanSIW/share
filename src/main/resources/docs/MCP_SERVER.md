# InvoiceStudio MCP Server

The InvoiceStudio MCP (Model Context Protocol) server exposes **everything
the desktop app can do** to AI assistants (Claude, Cursor, VS Code, and any
JSON-RPC/MCP client) — safely, fully audited, and always scoped to the
currently signed-in user's books.

This document has two parts:

- **Part A — Operator Guide**: how to *use* the server well. Written the way
  a veteran operator would onboard a new hire. If you are an LLM calling
  these tools, internalize this part — it is the difference between an
  operator and a hazard.
- **Part B — Developer / Architecture Guide**: entity model, per-tool specs,
  and the checklist for extending the toolset without reintroducing old bugs.

---

# Part A — Operator Guide

## The one rule that makes you an experienced operator

> **Check before you act; every create tool does this FOR you — and TELLS you
> when it created a dependency.** This is the *check-then-create contract*.

Every tool that creates an entity which references another entity (an item
points at a category, an invoice points at a buyer, a purchase points at a
supplier, an invoice line points at a catalog item) follows the same pattern:

1. Resolve each referenced entity **by id first, then by name
   (case-insensitive)**.
2. If it exists → link it. Nothing new is created.
3. If it does not exist → **auto-create it with sane defaults**, link it, and
   list it in the response under `autoCreated` — you are never left guessing
   whether a dependency silently appeared.
4. The primary entity itself is **idempotent by its natural key** (usually
   name): creating the same buyer/item/supplier/transport/template twice
   returns the *existing* record (`existed: true`, same `id`) instead of
   duplicating it.
5. If the primary save fails, everything the call auto-created is **rolled
   back** — the system never leaves a half-done state.

### Reading every create response

| Field | Meaning | What you should do |
|---|---|---|
| `ok: true` | The operation committed. | Proceed. |
| `id` | Id of the created (or existing, see `existed`) record. | Keep it for follow-up calls. |
| `existed: true` | The entity already existed; it was **not** modified or duplicated. | Don't re-create. Use the matching `update_*` tool (confirmation-gated) to change it. |
| `autoCreated: [...]` | Dependencies this call created because they were missing. Each entry: `{type, id, name}`. | Mention these to the user — e.g. "I also created category *Packaging*." Review defaults if they need real values. |
| `matchedBy` | `"id"` / `"name"` / `"key"` — how an existing record was resolved. | Confidence signal that the right record was linked. |
| `requiresConfirmation: true` | Update/delete tools never run directly. | Show `summary` to the user; after they approve, call `confirm_operation` with the `operationId`. |

## Daily flows, the way a veteran runs them

### Flow 1 — "Add item 'Cotton Shirt' under category 'Apparel'"

```
create_item { name: "Cotton Shirt", hsn: "6105", unit: "PCS",
              rate: 799, gst: 5, purchaseRate: 450,
              openingStock: 50, reorderLevel: 10,
              categoryName: "Apparel" }
```

- If **Apparel** exists (case doesn't matter): the item links to it. `autoCreated` is empty.
- If **Apparel** doesn't exist: it is created for you and listed in
  `autoCreated` — say so in your summary to the user.
- Call it twice by mistake? The second call returns the existing item with
  `existed: true` — **never** a duplicate. To change anything (rate, hsn, unit,
  **or its category**) use `update_item` (confirmation-gated).
- Side effects: opening stock seeds the stock ledger, so `stock_report` shows
  it immediately.

### Flow 1b — "Move these 10 items from 'Misc' to 'Apparel'" (category reassignment)

```
update_item { id: "<itemId>", categoryName: "Apparel" }   // one call per item
```

- **This is the only correct way to reassign a category.** Re-calling
  `create_item` with a new `categoryName` does NOT move anything — create is
  idempotent by name and returns the untouched item (`existed: true`).
- 'Apparel' missing? It is auto-created (check-then-create, same guarantees as
  create paths) and the move is spelled out in the confirmation summary:
  `Update item it_xxx: MOVE category → Apparel`.
- Every `update_item` is confirmation-gated: queue all 10, then have the user
  approve them together in Settings → MCP Server (or `confirm_operation` each).
- Batch example: `list_categories` first to reuse an existing category name —
  avoids creating a near-duplicate like "Apparels".

### Flow 2 — "Invoice 3 units of Cotton Shirt to Ramesh Traders, paid by UPI"

```
create_bill { buyerName: "Ramesh Traders",
              items: [{ name-from-catalog: use itemId from list_items,
                        qty: 3, rate: 799 }],
              paid: true, paymentMode: "UPI" }
```

- `Ramesh Traders` unknown? A buyer directory entry is auto-created and
  reported. Blank buyer name = deliberate **Walk-in Customer** (no directory
  entry) — that is the correct way to sell to a stranger.
- Line `itemId` unknown? The catalog item is auto-created **from the line
  itself** (desc/rate/gst become its defaults) and reported — an invoice line
  can never point at a phantom product.
- Side effects you can verify: GST split (CGST/SGST vs IGST) is decided by
  buyer state code vs your business state; stock is decremented; the CC-book
  transaction ledger gets a `sale` row; invoice number auto-increments.
- If anything fails to save, auto-created deps are rolled back and you get a
  clear error — retry confidently after fixing the cause.

### Flow 3 — "Record a purchase from a brand-new supplier"

```
create_purchase { supplierName: "Global Fabrics Pvt Ltd",
                  supplierBillNo: "GF-2231",
                  items: [{ itemId: "...", qty: 100, rate: 45 }],
                  freight: 250 }
```

- Unknown supplier is **auto-created** (never a hard error) and reported.
- Stock goes **in**, ITC (input tax credit) is computed, payable balance
  shows up in `list_suppliers`. Pay later with `pay_purchase` (partial
  payments allowed) — that tool deliberately *requires* an existing purchase
  id; paying for a purchase that doesn't exist is an operator error, not a
  creation opportunity.

### Flow 4 — "Book an expense"

```
record_expense { category: "Office Rent", amount: 12000, paymentMode: "Bank" }
```

- `category` here is a **free-text accounting head** (direct heads like
  Freight Inward hit the Trading Account; indirect ones like Office Rent hit
  P&L). It is deliberately **not** a directory reference — nothing is
  auto-created, and identical vouchers are legitimate. `response.head` tells
  you DIRECT/INDIRECT.

### Flow 5 — Anything destructive (update/delete)

```
delete_item { id: "item_..." }
→ { requiresConfirmation: true, operationId: "op-...", summary: "..." }
```

1. Show the `summary` to the human user. Explain consequences (deletes
   reverse stock ledger rows).
2. Get an explicit yes.
3. `confirm_operation { operationId, approve: true }` — or tell the user to
   click Approve in Settings → MCP Server.
4. Never confirm on your own initiative. Unapproved ops are discarded when
   the server stops.

### Flow 6 — Categories as first-class directory entries

```
create_category { name: "Apparel" }                        // idempotent by name
update_category { id: "cat_...", name: "Apparels & Clothing" }  // rename, cascades
delete_category { id: "cat_..." }                           // only when empty
```

- `list_categories` returns `itemCount` per category — 0 means safe to delete.
- Renaming cascades: items carry the category name denormalized, and the
  update rewrites it everywhere (the response is confirmation-gated; the
  summary says it cascades).
- Deleting a non-empty category is REFUSED with the exact count and the
  move-first recipe (`update_item { id, categoryId/categoryName }`). The
  app's default category is protected.
- `update_item` remains the way to move an item BETWEEN categories
  (see Flow 1b).

### Flow 7 — Buyer logistics: "assign default transport to these buyers"

```
create_buyer { name: "New Buyer", transportName: "Sharma Transport" }   // at creation
update_buyer { id: "byr_...", transportName: "Sharma Transport" }        // or later
```

- `transportId`/`transportName` are accepted by BOTH tools; a missing
  transport is auto-created (check-then-create, reported in `autoCreated`).
- On `update_buyer` the assignment is spelled out in the confirmation
  summary: `Update buyer byr_x: ASSIGN default transport → Sharma Transport`.
- Same fields update everything else the buyer dialog holds: city,
  contactPerson, openingBalance, creditLimit, address, stateCode — full
  create/update parity on both buyers and suppliers.

### Flow 8 — Transport masters are first-class (update + delete)

```
update_transport { id: "trn_...", phone: "9999999999", vehicleNumber: "MH14 XY 9999" }
delete_transport { id: "trn_..." }              // refuses while buyers reference it
delete_transport { id: "trn_...", force: true } // clears buyer references + deletes
```

- `update_transport` accepts name/phone/vehicleNumber (whatever the
  Transports dialog holds); the confirmation summary lists every change.
- `delete_transport` is reference-guarded: buyers carry `defaultTransportId`,
  so a delete that would leave them dangling is REFUSED with the reference
  count and two remedies — reassign each buyer via `update_buyer`, or pass
  `force: true` to clear their default-transport assignment automatically
  (spelled out in the confirmation summary). Unreferenced transports delete
  directly.

## Golden habits (what a year of experience looks like)

1. **Read first**: `get_app_guide` → `server_status` → `whoami` at session
   start; `list_*` before any create. Ten seconds of reading prevents an
   hour of cleanup.
2. **Trust but verify auto-creation**: every `autoCreated` entry is a
   dependency that now exists with default fields. If it deserves real data
   (GSTIN, phone), say so or fix it via the update tools.
3. **Prefer natural keys**: pass `buyerName`/`categoryName` you can spell
   consistently — resolution is case-insensitive but spelling-sensitive
   ("Ramesh Traders" ≠ "Ramesh Trader").
4. **Reuse ids from responses**, don't guess ids.
5. **Reports are your receipt**: after billing, confirm in `stock_report` /
   `daybook` / `financial_summary`. The books must tell the same story you
   just wrote.

## Authentication & lifecycle (two separate logins)

| Layer | What it is | Protects |
|---|---|---|
| **App login (Firebase)** | The account signed in inside InvoiceStudio. | Your business data — every MCP operation is scoped to this user's records. |
| **MCP token** | 32-char bearer token from Settings → MCP Server. | The localhost port. |

Enable: **Settings → MCP Server** → port (default 7800) → copy token →
Start Server. Point your client at `http://127.0.0.1:<port>/mcp` with
`Authorization: Bearer <token>`. Binds to 127.0.0.1 only; dies with the app.
`whoami` tells you whose books you're on — never returns secrets.

## Protocol

Streamable-HTTP JSON-RPC 2.0 at `POST /mcp`: `initialize`,
`notifications/initialized`, `ping`, `tools/list`, `tools/call`.
Errors: `-32601` unknown method/tool, `-32001` auth, `-32002` bad arguments,
`-32003` tool failure (auto-rolled-back where applicable).

---

# Part B — Developer / Architecture Guide

## Entity relationship map

```mermaid
flowchart LR
  CAT[Category] <-- category_id/name -- ITEM[Item]
  BUYER[Buyer] <-- buyer ref -- BILL[Bill / Invoice]
  ITEM <-- line refs -- BILL
  SUP[Supplier] <-- supplier_id -- PB[PurchaseBill]
  ITEM <-- line refs -- PB
  BILL <-- auto-sync -- TX[Transaction CC-ledger]
  SL[StockLedger] <-- voucher rows -- BILL & PB
  EXP[Expense] -. free-text head, NOT a ref .-> CAT
  TPL[Template] & VAR[Variable] & TRN[Transport] --> no directory refs
```

Reference semantics after hardening:

| Reference | Resolved by | Missing → | Visible via |
|---|---|---|---|
| item → category | id, then case-insensitive name | auto-create category (`cat_` id, name = supplied name or id) | `autoCreated` |
| bill → buyer | id, then name | auto-create buyer (`byr_` id, blank contacts) | `autoCreated` |
| bill/purchase line → item | id, then name | auto-create item **from the line's own data** | `autoCreated` |
| purchase → supplier | id, then name | auto-create supplier (`sup_` id, zero balances) | `autoCreated` |
| payment → purchase | id only | **hard error** (operator error, by design) | — |
| expense → head | none (free text) | n/a — duplicates legitimate | `head` field |

## The shared layer: `McpEnsure`

Every create path funnels through `com.invoicestudio.mcp.McpEnsure`:

- **Existence lookups** (Task 1: fast by design) — served from
  `DataManager`'s in-memory caches (categories/transports) or a single
  id-lookup / case-insensitive name scan on small directory tables. No cache
  invalidation, no extra I/O beyond at most one SELECT.
- **Check-then-create** (Task 2) — `ensureCategory`, `ensureBuyer`,
  `ensureSupplier`, `ensureItemForLine` return an `Outcome
  {id, name, created, matchedBy}` the registry shapes into the response.
- **Race safety** — MCP dispatch uses a 4-thread pool; every ensure takes a
  striped lock (64 buckets) keyed by `(type, normalized key)` around the
  check-then-insert window, and a DB-level `UNIQUE INDEX
  idx_categories_user_name ON categories(user_id, name COLLATE NOCASE)`
  backstops the hottest path (a race loser re-reads the winner and returns
  `created=false, matchedBy="race"`). A schema migration dedupes legacy
  categories and repoints `items.category_id` before installing the index.
- **Partial failure** — `create_bill`/`create_purchase`/`create_item` track
  auto-creations (`McpEnsure.track()` + `Tracked` records) and call
  `McpEnsure.rollback(...)` on ANY failure up to a verified-persisted primary
  (DAOs swallow SQL errors, so the registry verifies with a read-back before
  declaring success).

## Per-tool spec (create paths)

| Tool | Dependency checks | Idempotency | Rollback | Response additions |
|---|---|---|---|---|
| `create_item` | category (id→name) | by item name | category rolled back if item save unverified | `existed`, `autoCreated` |
| `create_buyer` | default transport (id→name) | by name | — | `existed`, `autoCreated` (buyer itself + any transport) |
| `create_supplier` | — (leaf) | by name | — | `existed`, `autoCreated` |
| `create_transport` | — (leaf) | by name | — | `existed` |
| `create_template` | — (leaf) | by name | — | `existed`, `warnings` (type downgrades) |
| `duplicate_template` | source id must exist | by newName | — | `existed` |
| `create_variable` | — (leaf) | by key, **never overwrites** | — | `existed` |
| `create_bill` | buyer (id→name), each line item (id→name) | bill not deduped (invoices are intentionally append-only; numbering prevents confusion) | buyer+items rolled back on any failure | `autoCreated` |
| `create_purchase` | supplier (id→name), each line item | same as bill | supplier+items rolled back | `autoCreated` |
| `record_expense` | none by design | duplicates allowed | — | `head` |
| `pay_purchase` | purchase must exist (hard error) | n/a | — | `paid`, `remaining`, `fullySettled` |

Update/delete tools: queue a `PendingOperations` op (confirmation-gated) and
re-validate existence at execution time. Exception to "unchanged":
`update_item` now also runs a dependency check — its `categoryId`/`categoryName`
reference is resolved via the same check-then-create path (auto-create + race
safety + no dangling ids), so category reassignment is first-class there.

Transport parity: `update_transport` (name/phone/vehicleNumber) and
`delete_transport` (reference-guarded — refuses while buyers still use it as
their default; `force: true` clears those assignments then deletes, nothing
left dangling). See Flow 8.

## Template design tools — full vocabulary exposure + sight

Templates are the one place where the AI is a *designer*, so the server
exposes the complete design surface instead of a lossy subset:

- **`get_template_design_guide`** — the full design reference
  (`TEMPLATE_DESIGN_GUIDE.md` embedded in the jar via `GuideContent`): all
  23 element types, every property, variable bindings, page geometry, A4 /
  thermal layout recipes, and the golden loop. Read before designing.
- **Lossless element I/O** — `create_template` / `update_template` accept
  and `get_template` returns EVERY `TemplateElement` property (position,
  typography, borders, gradients, images, QR/barcode, table columns,
  watermark, effects, conditions). Unknown element types degrade to TEXT
  **and are reported** in the response `warnings` — nothing is silently
  dropped. `get_template` round-trips into `update_template`.
- **`render_template_preview`** — renders a saved template (`id`) or an
  UNSAVED draft (`draft: {name, pageSize, elements}`; nothing is written)
  to a PNG using the real print engine (`PdfExportService` → Java2D, sample
  bill data, auto-height honored). Response = a native MCP **image content
  block** (`{type:"image", data, mimeType}` — vision clients see the bill)
  + a text block of meta: effective page size, pixel size, `elementCount`
  and `warnings` — out-of-bounds elements and unresolved `{{bindings}}`,
  each naming the offending element. Design loop: duplicate → update →
  preview → fix warnings → repeat.
- **Page geometry integrity** — named page sizes (`A4`…`THERMAL_58/80`)
  now always carry their real dimensions; thermal sizes also get
  `autoHeight: true` and tight margins, so MCP-created thermal templates
  print correctly instead of inheriting A4 geometry.

Where: registry `renderTemplatePreview` / `elementFull` / `elementsFrom(raw,
warnings)`, `service/TemplatePreviewService` (headless PNG),
`McpImageResult` (image content block carrier in `McpServer`). Tests:
`McpTemplateDesignTest` (guide completeness, styling round-trip, saved +
draft previews, bounds/binding warnings, thermal geometry, draft never
persists).

## Error & response shapes

Success (create): `{ok: true, id, ..., existed?, matchedBy?, autoCreated?[]}`

Failure — JSON-RPC error with HTTP 200:

```json
{ "jsonrpc": "2.0", "id": 1,
  "error": { "code": -32002,
             "message": "name is required" } }
```

`-32002` = bad arguments (nothing auto-created survived: rollback already
ran), `-32003` = tool failure (message states whether dependencies were
rolled back).

## Extension checklist — adding a new tool that creates entity X referencing entity Y

1. **Register the reference** in the ER map above and in this doc's table.
2. **Add an ensure** in `McpEnsure` (`ensureY`) if one doesn't exist:
   lookup by id → by case-insensitive natural key → create with defaults
   matching the desktop UI → all inside `synchronized (lockFor("y|"+key))`.
3. **Wire it** in `McpToolRegistry`: call `ensureY` BEFORE building/inserting
   X; on `outcome.created`, add to `created` (`McpEnsure.track()`) and to
   `autoCreated`.
4. **Guarantee no half-done state**: wrap ensure+insert in try/catch →
   `McpEnsure.rollback(dm, created)` → rethrow; after insert, **read back**
   X (DAOs swallow SQL errors) and roll back if absent.
5. **Idempotency**: decide X's natural key; return the existing record
   (`existed: true`) instead of duplicating — unless the entity is
   legitimately append-only (invoices, purchases, expenses): then say so in
   the tool description.
6. **Describe the contract in the tool description** (the `tools/list` text
   IS the operator's training): state exactly what gets auto-created.
7. **Tests — both branches** in `McpEnsureHardeningTest`:
   dependency-exists (nothing created, `autoCreated` empty) and
   dependency-missing (created with defaults, listed in `autoCreated`,
   linkage verified). Add a race test if Y is a new auto-create target, and
   a unique-index migration if Y is name-keyed.
8. **Update `MCP_SERVER.md`** (this file — it ships inside the jar via
   `GuideContent` → `get_mcp_docs`) and `docs/vault/08 MCP Server.md`.
9. **Bump the audit trail expectations** if you added new `[TOOL]`/`[CONFIRMED]`
   event shapes.

## Where the code lives

| Piece | File |
|---|---|
| HTTP/JSON-RPC endpoint, bearer auth | `mcp/McpServer.java` |
| Tool catalogue + dispatch + create flows | `mcp/McpToolRegistry.java` |
| Image tool results (native MCP image blocks) | `mcp/McpImageResult.java` |
| Headless template → PNG preview (print-true) | `service/TemplatePreviewService.java` |
| Template design reference (embedded) | `resources/docs/TEMPLATE_DESIGN_GUIDE.md` via `GuideContent` |
| Existence layer + check-then-create + rollback | `mcp/McpEnsure.java` |
| Confirmation queue for update/delete | `mcp/PendingOperations.java` |
| Audit trail | `mcp/McpAuditLog.java` (+ `mcp-audit.log`) |
| Config (port, token) | `mcp/McpConfig.java` → `mcp-server.json` |
| Embedded docs (this file) | `resources/docs/MCP_SERVER.md` via `GuideContent` |
| Both-branch tests | `src/test/java/com/invoicestudio/mcp/` |
