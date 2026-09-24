# InvoiceStudio — "Institutional Editorial" UI Redesign (Static Prototype)

A complete, click-through redesign of the InvoiceStudio desktop app (JavaFX) built as
**static HTML + CSS + vanilla JS**. Nothing here runs a backend — every button, menu,
dialog, tab and toggle is wired in the UI layer so stakeholders can feel the new
experience exactly as the app would behave.

**Design source of truth: `../bbroc.md`** (BlackRock-style design specification).
Read that file for the rationale behind every token used here.

> Note: the folder is named `redesign` (correct spelling of the requested "redsign").

---

## Run it

No build step. Open any page in a browser:

```
redesign/
├── index.html            ← start here (Sign in)
├── loading.html          → boot/loading shell
├── dashboard.html        → Standard Overview
├── …every other page links from the sidebar
```

Or serve it (nicer URLs, no file:// quirks):

```bash
cd redesign && python3 -m http.server 8080
# → http://localhost:8080
```

---

## What is redesigned (beyond colour)

| Area | Old app (Obsidian & Gold, dark) | This redesign |
|---|---|---|
| Identity | Dark navy-black, gold accent, rounded cards | **White / Spring-Wood editorial canvas**, black text, **Vermilion** signal, **Supernova** highlight — per `bbroc.md` |
| Shape | 10px radii, card shadows | **Radius 0** surfaces, 2px controls, 4px dialogs, **flat** (elevation for overlays only) |
| Navigation | Sidebar + "Catalog" popup hiding 8 pages | **Grouped, collapsible sidebar** (Workspace / Sales / Purchase / Insights / Design & Print / Directory / System) + **collapse-to-icon-rail** |
| Global search | None | **Command palette (Ctrl+K)** — every page & action, keyboard-first |
| Top bar | App title only | **Breadcrumb, global search pill, refresh pill, F1 help** |
| Dashboards | Single KPI row | **Bento grid**: 4 KPI cards with sparklines + revenue trend + GST donut + top-5 buyers + recent docs; Dashboard 2 for logistics |
| Create Bill | Form + preview | **Split view** with live A4 paper preview, working line-item math, doc-type tabs, buyer autocomplete, catalog picker, print dialog |
| Tables | Plain | Sticky headers with **2px black rule**, uppercase micro labels, filter chips, pager, density affordances |
| Buttons | Gold gradient | Spec's system: **black primary**, **vermilion accent (black label)**, outline secondary, underlined text CTAs |
| Forms | Mixed | Labels-above-fields, 2-col grids, inline validation hints, units, smart selects |
| Status | Coloured pills | Restrained **tinted badges** (Paid = Supernova highlight, Unpaid = warn, Cancelled = neutral strike) |
| Charts | JavaFX charts | Editorial **SVG charts**: vermilion signal line/bars, hairline grid, mono ticks |
| Chatbot | Panel | Same pipeline UX (ROUTER → TOOL-CALL → MCP-EXEC chips, per-turn logs dialog, model status) on **every page** via FAB |
| Accessibility | Basic | Skip-link, **2px black focus ring**, 44px touch targets, WCAG-AA contrast pairs from the spec (black-on-vermilion, #C93400 for accent text) |
| Motion | 150ms fade | 150–200ms `cubic-bezier(.2,0,0,1)`, menus fade + 8px slide, `prefers-reduced-motion` respected |

## Every screen from the real app is here

- **Auth**: sign in (+forgot password, Google button, remember-me), sign up (+strength meter)
- **Shell**: loading/boot screen, sidebar with rail toggle, catalog as expandable group, user pill menu, sign-out dialog, F1 shortcuts overlay, command palette, toasts, refresh pill
- **Workspace**: Dashboard, Dashboard 2 (Financial & Logistics)
- **Sales**: Create Bill (4 doc types), Invoices/History (view/print/PDF/pay/duplicate/convert/cancel/delete), Transactions & Ledger (CC/CS books), Reports (8 report builders, each opens a populated dialog)
- **Purchase**: Purchase Register (pay supplier, delete reverses stock), Record Purchase Bill (GST mode toggle, freight, payable calc)
- **Expenses**: vouchers, accounts manager (rename/archive/duplicate/in-use guard), category report
- **Insights**: Financials (Trading A/c, P&L, Balance Sheet, GSTR-1/3B, Daybook), Stock & Profit (movement, profitability, reorder → purchase)
- **Design & Print**: Templates gallery (presets + my templates), **Template Designer** (toolbar, tool rail, canvas with rulers, properties/layers tabs, page dialog, label-stock dialog, strip preview, bulk print, colour chooser, zoom/fit footer), Label History
- **Directory**: Buyers (CSV import/export, statement ledger, custom fields), Suppliers (banking, credit period), Items (stock, reorder, sales report), Categories, Transports, Variables (grouped placeholder registry)
- **Settings**: all 11 tabs — Profile (logo, Firebase), Bank, Billing (prefix/padding explainer), Fields, Fonts (Google + local), Print (calibration + TSPL threshold), Knowledge Hub (articles + editor), Backup (JSON export/restore), Shortcuts (rebind table), Chatbot (provider, **API key vault**, behaviour), MCP Server (token, client configs, pending confirmations, activity log)

## File map

```
redesign/
├── css/
│   ├── theme.css         tokens (from bbroc.md §12), base, shell, auth
│   ├── components.css    buttons, tables, modals, popovers, toasts, palette, chat
│   └── pages.css         dashboard bento, split billing, designer, settings, statements
├── js/
│   ├── icons.js          monoline 24px SVG icon set (bbroc.md §6)
│   ├── shell.js          sidebar/topbar builder, palette, modals, popovers, toasts, chat, F1
│   └── charts.js         dependency-free SVG charts (line/bars/donut/spark)
├── index.html            21 app pages + 2 auth pages + loading (see list above)
└── README.md
```

## Interaction cheatsheet

| Try | Where |
|---|---|
| `Ctrl+K` | anywhere — command palette |
| `F1` | anywhere — keyboard shortcut overlay |
| Sidebar group headers | collapse/expand (persisted) |
| Rail toggle (☰) | collapse sidebar to icon rail |
| Row `⋯` menus | invoices → duplicate/convert/cancel/delete modals |
| "Pay" / "Pay Supplier" | payment dialogs with success toasts |
| Designer toolbar | Text/Table/Shapes/Media/Components/Code popovers |
| Page / Label Stock / Strip Preview / Bulk Print | designer dialogs (bulk rows + print-all) |
| Chat FAB | assistant panel with pipeline chips + logs dialog |
| User pill | profile menu → sign-out confirm → back to sign-in |

## Deliberate deviations & notes

- **Fonts**: the spec's `Fort` family is proprietary; we use the spec's own fallback stack
  ("Helvetica Neue", Helvetica, Arial, "Segoe UI", system-ui) with weight-800 headings.
- **Status colours**: the BlackRock palette has no semantic green; Paid/ok badges use a
  restrained `#1E7B4F` on pale tint (finance convention) — still ~90% neutral surfaces.
- **Supernova discipline**: at most one `#FFCE00` element per view (default flags, the
  "Paid" badge, one KPI accent) — enforced in the designs above.
- **Data is static**: numbers mirror the app's sample data (Acme Industries, INV-00xx…)
  so stakeholders can compare screens side-by-side with the running app.
- The prototype is intentionally framework-free so any page can be handed to a designer
  or printed as a spec sheet without tooling.
