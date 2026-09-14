# InvoiceStudio MCP Server

The InvoiceStudio MCP (Model Context Protocol) server exposes **everything
the desktop app can do** to AI assistants (Claude, Cursor, VS Code, and any
JSON-RPC/MCP client) — safely, fully audited, and always scoped to the
currently signed-in user's books.

## How authentication works (two separate logins)

There are **two independent credentials** and they are NOT interchangeable:

| Layer | What it is | Where it lives | What it protects |
|---|---|---|---|
| **App login (Firebase)** | The real account signed in inside InvoiceStudio (email + password / Google). | The app itself. | Your business data. Every MCP operation is scoped to this user's records — another user's books are invisible. |
| **MCP token** | A random 32-character string the app generates. | Settings → MCP Server (click Copy). | The localhost port. Proves your AI client may talk to the app while it runs. |

Why the token instead of Firebase: the MCP server exists only while
InvoiceStudio is running, on your machine, inside your already-authenticated
session. The AI client never sees (and never needs) your Firebase password —
it presents the MCP token as `Authorization: Bearer <token>` and the app
executes everything as the logged-in Firebase user.

**Putting the token into Cursor / VS Code:** open Settings → MCP Server,
click **Copy Cursor / VS Code Config**, paste into `.cursor/mcp.json` (or VS
Code MCP settings). For Claude Desktop use **Copy Claude Desktop Config**.
Re-copy after Regenerate. If the app is closed the endpoint is down and the
token is useless — that is by design.

The `whoami` tool answers "whose books am I on?" at any time (account email,
display name, session expiry) and never returns secrets.

## Enabling

1. Open **Settings → MCP Server**.
2. Choose a **port** (default 7800), toggle **Auto-start** if you want the
   server up whenever InvoiceStudio runs, and copy the **auth token**.
3. Click **Start Server** (green status pill = running, with the exact
   endpoint URL).
4. Point your MCP client at `http://127.0.0.1:<port>/mcp` with header
   `Authorization: Bearer <token>`.

The server binds to **127.0.0.1 only** (never the network) and stops
automatically when the app closes.

## Protocol

Streamable-HTTP JSON-RPC 2.0 at `POST /mcp`:

- `initialize` → server info + capabilities
- `notifications/initialized` → `{}`
- `tools/list` → all tools with JSON-Schema input schemas
- `tools/call` `{ "name": "...", "arguments": {...} }` →
  `{ "content": [ { "type": "text", "text": "<json or text>" } ] }`

Errors are JSON-RPC `-32601` (unknown method/tool) and `-32001`
(auth failure).

## Safety model (read vs write vs destructive)

- **Read tools run immediately.**
- **Create tools** (new bills, purchases, expenses, masters) also run
  immediately — they add records but never overwrite.
- **Update / delete tools NEVER execute directly.** The first call returns
  `requiresConfirmation: true` with an `operationId` and a plain-language
  summary of exactly what will change, and a confirmation banner appears in
  Settings → MCP Server. The user clicks **Approve** (or the AI calls
  `confirm_operation` with the `operationId` after the human says yes).
  Unapproved operations are discarded on reject or server stop.
- Every call is written to the audit trail (Settings tab + `mcp-audit.log`).

## Tools

Discovery / knowledge:

| Tool | Purpose |
|---|---|
| `get_app_guide` | Full application manual (start here) |
| `get_mcp_docs` | This document + safety model |
| `get_settings` | Business profile, billing prefs (read-only) |

Masters:

| Tool | Purpose |
|---|---|
| `list_buyers` / `create_buyer` / `update_buyer` / `delete_buyer` | Customer directory (update/delete need confirmation) |
| `list_suppliers` / `create_supplier` / `update_supplier` / `delete_supplier` | Sellers/creditors directory |
| `list_items` / `create_item` / `update_item` / `delete_item` | Catalog with rates, GST, cost, reorder level |
| `list_categories` / `list_transports` / `list_templates` | Supporting masters (read-only) |
| `get_template` | Full template anatomy: page size + every positioned element with bindings — the reference for building templates programmatically |
| `create_template` / `update_template` / `duplicate_template` / `delete_template` | Print-template lifecycle (update/delete need confirmation). Best practice: `duplicate_template` a preset, then edit elements |
| `list_variables` / `create_variable` / `delete_variable` | Template variable bindings (fixed + custom) |
| `create_transport` | Logistics partner |
| `create_backup` | JSON data backup export |
| `whoami` | Whose books the server is operating on + auth model explanation (no secrets) |

Sales:

| Tool | Purpose |
|---|---|
| `list_bills` `{query?, status?, limit?}` | Search invoices |
| `get_bill` `{id}` | Full invoice incl. totals & payments |
| `create_bill` | New tax invoice: buyer name or id, lines [{itemId or desc, qty, rate, gst, discPct}], optional paid mode |
| `update_bill_status` `{id, status}` | Mark paid / unpaid / cancelled (confirmation) |
| `delete_bill` `{id}` | Delete invoice + stock reversal (confirmation) |

Purchases & payments:

| Tool | Purpose |
|---|---|
| `list_purchases` `{query?, limit?}` | Purchase register |
| `create_purchase` | Supplier bill: supplier, supplier's bill no, lines, freight, paid mode |
| `pay_purchase` `{id, amount, mode, reference?}` | Record supplier payment (full or partial) |
| `delete_purchase` `{id}` | Delete + stock reversal (confirmation) |

Expenses & money:

| Tool | Purpose |
|---|---|
| `list_expenses` `{limit?}` / `record_expense` | Direct/indirect expense vouchers |
| `delete_expense` `{id}` | Confirmation required |
| `list_transactions` `{limit?}` | CC book ledger entries |

Reports & insights (all read-only):

| Tool | Purpose |
|---|---|
| `stock_report` | Stock summary + low-stock list |
| `profitability_report` | Item-wise sales vs cost → gross profit |
| `financial_summary` `{from?, to?}` | Trading/P&L/Balance-Sheet/GST/Daybook numbers |
| `daybook` | Chronological journal |

System:

| Tool | Purpose |
|---|---|
| `server_status` | Running state, version, pending-operation count |
| `audit_log` `{limit?}` | Recent MCP activity |
| `confirm_operation` `{operationId, approve}` | Approve/reject a queued destructive op (call only after the human confirms) |

## Typical AI session

1. `initialize` → `get_app_guide` → `server_status`
2. Read: `list_items`, `list_buyers`, `stock_report`
3. Act: `create_bill` / `create_purchase` / `record_expense`
4. Verify: `financial_summary`, `daybook`
5. Anything destructive: present the summary to the user, get a yes, then
   `confirm_operation`.
