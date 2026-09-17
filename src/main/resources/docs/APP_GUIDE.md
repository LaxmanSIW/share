# InvoiceStudio — Complete Application Guide

*This guide is served to AI assistants through the InvoiceStudio MCP server
(`get_app_guide` tool) and lives in the app resources as
`/docs/APP_GUIDE.md`. It is written the way a 10-year veteran bookkeeper
would explain the software to a new operator.*

## What InvoiceStudio is

InvoiceStudio is a desktop GST billing, inventory and double-entry
accounting application for Indian businesses (Tally-style workflows):

- **Sales cycle** — tax invoices to Buyers (Sundry Debtors), receipts,
  credit notes, buyer ledgers and CC book transactions.
- **Purchase cycle** — purchase bills from Sellers/Suppliers (Sundry
  Creditors), supplier payments, debit-note groundwork, input tax credit.
- **Inventory** — an immutable stock ledger: every purchase adds stock IN,
  every sale records stock OUT; current stock is always
  `opening + Σin − Σout` and can never be hand-edited.
- **Expenses** — direct (trading-account) and indirect (P&L) expense heads.
- **Financials** — Trading Account, P&L, Balance Sheet, GST summary
  (Output − ITC = Net Tax) and a unified Daybook.
- **Printing** — a full drag-and-drop Template Designer for invoice print
  layouts (A4/A5/thermal 58/80), with variables, custom fonts and
  print calibration.

## Data model at a glance

| Concept | Where it lives | Notes |
|---|---|---|
| Buyer (customer) | `buyers` | GSTIN, state code, credit limit, custom fields |
| Seller / Supplier | `suppliers` | GSTIN, PAN, bank details, opening balance (Cr positive = payable), credit period |
| Item (catalog) | `items` | HSN, unit, selling `rate`, GST %, `purchase_rate` (cost), `opening_stock`, `reorder_level` |
| Sales invoice | `bills` | buyer, line items, discount %, totals, status UNPAID/PAID/CANCELLED, payments list |
| Purchase bill | `purchase_bills` | supplier, supplier's own bill no, freight, paid flag, payments list |
| Stock movement | `stock_ledger` | immutable rows: PURCHASE / SALE / DEBIT_NOTE / CREDIT_NOTE / ADJUSTMENT |
| Expense | `expenses` | date, category (direct/indirect), amount, payment mode |
| Transaction | `transactions` | CC-book ledger entries auto-synced from bills & payments |
| Print template | `templates` | page config + positioned elements (text, field, table, shape) |

## Sidebar map

- **WORKSPACE** — Dashboard (KPIs, charts, quick actions)
- **SALES** — Invoices (create/edit/convert/duplicate/repeat), Transactions
  (CC book ledger), Reports & Ledger (buyer statements, registers)
- **PURCHASE & EXPENSES** — Purchases (purchase register + supplier
  payments), Expenses (direct/indirect voucher entry)
- **INSIGHTS** — Financials (Trading/P&L/Balance Sheet/GST/Daybook),
  Stock & Profit (stock summary, item profitability, low stock)
- **DIRECTORY & CATALOG** (the "Catalog" popup button) — Buyers, Sellers,
  Items, Categories, Templates, Transports, Variables
- **SYSTEM** — Settings (profile, bank, billing, fields, fonts, print,
  backup, shortcuts, MCP server)

## How to create a tax invoice (sales)

1. `+ New Bill` (or `Ctrl+N`, or MCP tool `create_bill`).
2. Pick the buyer (or walk-in), the print template and the date.
3. Add item lines: choose a catalog item (rate + GST auto-fill) or type a
   free-text line; set qty, rate, per-line discount %.
4. Totals compute automatically: line taxable → CGST+SGST (intra-state) or
   IGST (inter-state, decided by buyer GSTIN state code vs company state) →
   round-off → grand total.
5. Save as UNPAID (credit sale) or mark PAID with a payment mode
   (Cash/UPI/Bank/Cheque/Card). Saving also writes the stock OUT rows and
   the CC-book transaction automatically.

## How to record a purchase

1. Sidebar → Purchases → `+ New Purchase` (or `Ctrl+P`, MCP
   `create_purchase`).
2. Pick the supplier — the app decides CGST+SGST vs IGST from the
   supplier GSTIN state code.
3. Enter the **supplier's own bill number and date** (needed for GST
   matching/ITC), then the item lines with the **purchase (cost) rate**.
4. Add freight if charged. Save as credit (payable) or paid (Cash/Bank/
   Cheque/UPI). Saving writes stock IN rows for every catalog item line.

## How supplier payments work

- Purchases view → row action **Pay** → amount, mode, reference, date.
- Partial payments are supported; the bill flips to Paid only when the
  remaining balance reaches zero.
- The seller's **Ledger** (in Sellers view) shows every bill as a credit
  and every payment as a debit with a running Cr/Dr balance — the
  Sundry Creditors statement.

## How expenses are booked

Expenses view → `+ New Expense` (or `Ctrl+E`, MCP `record_expense`).
Choose a **direct** head (freight inward, wages, power & fuel, factory
rent — they hit the Trading Account) or an **indirect** head (office rent,
salaries, electricity, marketing, bank charges, software... — they hit the
P&L). Mode + reference are recorded for the cash book.

## How the financial statements are derived

- **Trading Account** — Gross Profit = (Sales + Closing Stock) − (Opening
  Stock + Purchases + Direct Expenses).
- **P&L** — Net Profit = Gross Profit − Indirect Expenses.
- **Balance Sheet** — Liabilities: creditors, net GST payable, retained
  profit. Assets: debtors, cash, inventory at cost, excess ITC.
- **GST summary** — Output GST (sales) − Input Tax Credit (purchases) =
  Net tax payable; negative means credit carry-forward.
- **Daybook** — chronological journal of sales, receipts, purchases,
  payments and expenses.

## How stock works (immutable ledger)

- Stock never changes except through vouchers: a purchase adds IN rows, a
  sale adds OUT rows, deleting a voucher removes exactly its rows.
- `items.current_stock` is recomputed from the ledger
  (`opening + Σin − Σout`) — hand edits are impossible by design.
- Stock & Profit view shows per-item Opening → In → Out → Closing with
  value at cost, item-wise profitability (sales value vs cost → GP%),
  and low-stock alerts against `reorder_level`.

## How to create / edit print templates

1. Catalog → Templates → open a template (or duplicate a preset).
2. The Template Designer canvas shows the page (A4/A5/thermal 58/80) with
   a grid; drag elements to position them.
3. Element types (enum `ElementType`): **TEXT** (static), **IMAGE**,
   **TABLE** (the item grid with header/column config), **LINE**, **RECT**,
   **PAGENO**, **QRCODE**, **BARCODE**, plus vector shapes (CIRCLE,
   ELLIPSE, POLYLINE, POLYGON, ARC, PATH, STAR, ARROW, DIVIDER, FREEHAND,
   WATERMARK, SVG, ICON, GROUP, COMPONENT).
4. Every element is positioned in millimetres (x, y, w, h) on the page and
   can be rotated, locked, hidden, marked `repeatOnPages` (prints on every
   page of multi-page bills) or `hideWhenBlank`.
5. A TEXT/FIELD element's **`binding`** connects it to data: fixed app
   variables (business name, GSTIN, totals, bank details...) or custom
   variables defined in Variables view (`list_variables` over MCP).
6. Ctrl+S saves. Templates are chosen per-bill at invoice time.

**Programmatic route (AI):** `get_template_design_guide` for the full design
vocabulary → `duplicate_template` a preset → `get_template`
to read its anatomy → `update_template` with an edited element list. See
MCP_SERVER.md for the confirmation rules on updates/deletes.

## How authentication works

InvoiceStudio uses **Firebase Authentication**: you sign in with your email
(Google or email+password), and the app keeps a session (id token + refresh
token) alive while it runs. Two consequences:

- **Every record is partitioned by that user id.** Two businesses on two
  accounts each see only their own buyers, bills, stock and books — even on
  the same computer.
- **The MCP server inherits your session.** It runs only while the app is
  running and can only act as the currently signed-in user. AI clients
  authenticate to the server with the separate MCP bearer token (Settings →
  MCP Server), never with your Firebase password. See MCP_SERVER.md for
  the full explanation and IDE setup.

## Multi-user behaviour

Every record is partitioned by logged-in user id. Switching users or
running the MCP server always operates strictly on the **currently signed
in user's** books — another user's data is invisible and unreachable.

## Keyboard shortcuts

F1 (shortcut help anywhere), Ctrl+N (new bill), Ctrl+P (new purchase),
Ctrl+E (new expense), Ctrl+B (buyers), Ctrl+D (dashboard). Inside the
Template Designer: Ctrl+Z/Y undo/redo, Ctrl+C/V/D copy/paste/duplicate,
Ctrl+G grid toggle, arrows nudge, Ctrl+0/+/− zoom, Space pan. Every shortcut
is user-rebindable in Settings → Shortcuts (with collision + reserved-combo
validation), and AI assistants can read or rebind them over MCP
(`list_shortcuts`, `rebind_shortcut`, `reset_shortcut`).

## Bulk invoice & label output

History → **Export PDFs** renders every invoice matching the current filters
in one background pass (also exposed to AI as `export_bills_pdf`). The Bulk
Label Print window remembers each template's last queue — rows, copies and
printer — so the next session starts where you left off (readable over MCP
via `get_label_print_state` / `print_labels`).

## Settings

Profile (business identity, GSTIN, logo), Bank, Billing (currency, invoice
prefix & numbering, inter-state mode), Fields (custom buyer fields), Fonts
(Google fonts), Print (calibration offsets, status stamp), Backup (schedule
& restore), Shortcuts, **MCP Server** (AI access — see MCP_SERVER.md).
