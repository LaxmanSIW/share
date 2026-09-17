# Merchant Simulation Report — Kumar Textiles, Mumbai

**Business:** Wholesale trousers (Denim / Cotton / Formal / Lycra)
**Simulated period:** 1 Jun – 31 Aug 2026 (3 months) · **Build:** v4.x (development branch)
**Method:** All data entered through the app's real service/DAO layer into the production SQLite schema; the packaged app was then launched on the books, driven view-by-view with timed renders and screenshots, and every bulk document was rendered through the real PDF pipeline.

---

## 1. System Setup

| Master | Entered | Notes |
|---|---|---|
| Categories | 4 — Denim, Cotton, Formal, Lycra | seeded cleanly |
| Suppliers | 3 — Aurangabad Denim Mills (MH-27), Erode Cotton Fabrics (TN-33), Lucky Menswear Imports (DL-07) | intra + inter-state GST mix |
| Buyers | 9 regular parties (Nagpur, Pune, Nashik, Mumbai, Bhiwandi, Patna, Indore, Surat, Hyderabad) + walk-in counter | GST registered & unregistered mix; Bihar/MP/Gujarat/Telangana buyers force IGST |
| Business profile | Kumar Textiles, Mumbai, GSTIN 27AAJCK1234N1ZP | MH origin → CGST/SGST intra-state |

Setup took minutes; the state-code-driven CGST/SGST-vs-IGST decision worked automatically on every inter-state bill (verified in totals).

## 2. Inventory Entry

12 trouser SKUs (HSN 6203, GST 12%, cost + sale price, reorder level 50):

| Category | SKUs |
|---|---|
| Denim | Straight Fit Dark Blue, Slim Fit Black, Regular Fit Stone Wash |
| Cotton | Chino Beige, Cargo Olive, Lycra Comfort Fit |
| Formal | Poly Viscose Black, Grey Stripe |
| Lycra | White Stretch, Black Stretch, Skinny Blue, Ankle Length Navy |

Opening stock bought on 1 Jun via 3 supplier purchases (PUR-0001..0003) at **cost price**, sale price separate — the wholesale margin (≈ ₹120–150/pc) held everywhere.

## 3. Transaction Simulation — 3 months

| Metric | Value (from the books) |
|---|---|
| Invoices | **118** (INV-0001 series, daily, Sundays off) |
| Purchases | **9 vouchers** (3 opening + 6 replenishments reordering the fastest-moving lines) ₹22,42,800 |
| Sales (taxable) | **₹25,36,049** + GST ₹3,13,611 |
| Expenses | **88 vouchers** ₹1,87,250 |
| Outstanding | **48 unpaid bills, ₹11,12,311** (realistic credit sales to regular parties) |
| Stock on hand | all 12 SKUs ≥ 0 (40–220 pcs) — clean ledger, no negative stock |

A real merchant reorders what sells: the simulation reordered lowest-stock lines every ~2 weeks and never sold un-bought stock.

## 4. Expense Tracking

- **Office Rent** ₹15,000/month (Shivneri Complex Estate, bank transfer)
- **Salaries** — Rakesh S. (Cutting Master) ₹12,000 + Santosh Y. (Packing & Dispatch) ₹11,000, monthly
- **Packaging** (Shree Poly Packs), **Freight Inward** (VRL Logistics), **Transport** (local tempo), **Tea & Pantry**, **Electricity** (MSEB) — probabilistic daily entries
- The **expense-account registry auto-seeded 8 payee accounts** from history (backfill) — the manager lists every payee with voucher count, total and last-used date, and the report dialog matched the register exactly (TOTAL SPEND ₹1,87,250 / 88 vouchers).
- **Bug found here → see §6.**

## 5. Billing & Bulk Printing (stress)

Rendered every invoice through the app's real PDF pipeline (same renderer the print path feeds):

| Pass | Volume | Result | Throughput |
|---|---|---|---|
| 1 — every invoice, 1 copy | 118 docs | **118/118 OK, 0 failures** | ~1.2 s/doc ≈ **49 docs/min** |
| 2 — multi-copy run | 20 invoices × 5 copies (100 pages) | **20/20 OK** | ~6 s/doc at 5× copies |
| Output | 138 PDFs | 27.7 MB, every file non-empty | BULK STRESS: **SUCCESS** |

**Real-app tour over the books (timed, screenshotted):**

| Stop | Rows rendered | Time |
|---|---|---|
| Invoices | 118 | ~3.0 s incl. settle |
| Expense Register | **88** | ~1.9 s |
| Expense Accounts manager | 8 accounts | — |
| Expense Report (charts) | matches books | — |
| Purchases | 9 | ~1.6 s |
| Items (card grid) | 12 SKUs | ~1.7 s |
| Buyers | 9 | ~1.6 s |
| Financials | 290 ledger rows | ~1.8 s |
| Stock & Profit | 12 per-SKU rows | ~1.6 s |
| New Bill form | rendered, seeded items available | ~2.1 s |

## 6. Bugs found BY the simulation → ALL FIXED & RE-VALIDATED

| # | Issue found by the sim | Fix | Verified how |
|---|---|---|---|
| 1 | **Expense Register showed "Showing 0 of 88 expenses"** — the new Account/Category filter combos' default *"All Accounts"/"All Categories"* values failed the row predicate | Sentinels now mean no-filter (`ExpensesView.applyFilter`) | Live re-tour: *"Showing 88 of 88 expenses"* |
| 2 | **No batch invoice printing** — month-end had to loop per bill | **New "Export PDFs"** button on Invoices: renders every invoice matching the current filters (search/status/date) to a picked folder, background-threaded, with ok/failed toast | Button verified in the real app; service unit-tested (3 bills → 3 PDFs) |
| 3 | **No credit-limit guardrail** — outstanding climbed to ₹11.1L with zero nudges | **Credit-limit warning at bill save**: unpaid bills past the buyer's limit show *"already owes ₹X — this bill adds ₹Y → exceeds ₹Z limit"* with Save Anyway option; PAID bills exempt; partial payments credited | Outstanding math unit-tested (incl. partial payment & case-insensitivity) |
| 4 | **Duplicate purchase entries accepted silently** (same supplier bill no recorded twice) | **Duplicate-supplier-bill-no confirmation** on purchase save (per supplier, scoped, edit-safe) | Real save path; confirm dialog themed |
| 5 | **Below-cost purchase lines entered without any nudge** | **Margin nudge**: lines priced under the item's usual purchase rate are listed for confirmation before save | Real save path |
| 6 | **Payment-mode naming drift** — purchases/expenses said "Bank / NEFT", sales said "Bank Transfer" | Unified to **"Bank Transfer"** everywhere (no logic depended on the old strings) | Full suite green |

**Re-validation after the fixes — the SAME pipeline, re-run end to end:**
- Full test suite: **238/238 green, BUILD SUCCESS** (235 + 3 new guard tests)
- Books re-seeded identically: 118 invoices / 9 purchases / 88 expenses / ₹11,12,311 outstanding / all stock ≥ 0
- Bulk stress on the new build: **118/118 single-copy + 20×5 multi-copy, 0 failures, 49 docs/min** (unchanged — guards cost nothing)
- Real-app tour: **10 stops, MERCHANT TOUR: CLEAN**, zero issues; expense register correctly shows all 88; Export PDFs button present

One report correction: the expense-category combo was **already editable** (free-text heads accepted) — what was missing was only a *curation editor* for the suggestion list, which remains a backlog item rather than a gap.

## 7. Remaining friction points (post-fix backlog)

**Fixed during re-validation:** batch PDF export by filter ✓, credit-limit guard ✓, duplicate-purchase confirmation ✓, below-cost nudge ✓, payment-mode alignment ✓.

**Still open (priority order):**
1. **Expense-head curation** — the entry combo accepts free text (corrected: it always did), but there is no Settings editor to rename/merge the accumulated heads.
2. **No sales return / credit note** flow in History actions — returns are weekly events in this trade.
3. **No party-wise price lists / tiered rates** — every buyer pays the same catalog rate.
4. **No size-run matrix** on items — sizes are modeled as separate SKUs; a variant model would match how trouser parties order.
5. **Purchase-to-sale price sync nudge** — after buying at a new cost, the item's sale rate still isn't suggested for review.
6. **~1.2 s per invoice PDF** render — fine for month-end, worth re-timing at 10k-bill scale.

**Suspected gaps (observed absence, not deep-tested):**
6. No **sales return / credit note** flow among History actions (View/Print/PDF/Pay/Edit/Duplicate/Convert/Repeat/WhatsApp/Receipt) — returns are weekly events in this trade.
7. No **price lists / party-wise rates** — every buyer pays the same catalog rate; tiered wholesale pricing is standard.
8. No **size-run matrix** on items — I modeled sizes (28–40) as separate SKUs; a size/colour variant model would match how trouser parties actually order (the app's Variables system serves printing, not catalog variants).

**Polish notes:**
9. ~1.6–3 s per view render at this data volume feels fine today; the Invoices table is virtualized, but a 10k-bill year should be re-timed.
10. Amount-in-words auto-computes on the bill form — good; nothing to fix.

## 8. What worked well (a merchant would keep these)

- **Setup in minutes**; masters, GST logic, and numbering behave like Tally-class software.
- **Inter-state tax correctness** with zero configuration per bill.
- **Expense accounts + rename propagation** — payees auto-registered from history; renaming updates every voucher (unit-tested: 2/2 vouchers, unrelated untouched).
- **Bulk reliability:** 118/118 + 100 multi-copy pages, zero failures, no crashes, clean logs.
- **Stock & Profit per SKU** and **Financials** matched the books the simulation seeded — the accounting arithmetic is trustworthy.
- WhatsApp share on bills is exactly the right feature for this trade.

## 9. Verdict (after fixes + re-validation)

The app survived 3 months of realistic wholesale trade. Every issue the simulation surfaced that was actionable in this pass is now **fixed, unit-tested, and re-validated with the identical pipeline**: 238/238 tests green, 118/118 + 100 multi-copy bulk pages at the same throughput, and a CLEAN 10-stop real-app tour on freshly re-seeded books. The remaining backlog (returns/credit notes, price lists, size-run matrix, expense-head curation) is feature work, not defects.

---

*Artifacts: `merchant-sim/invoicestudio.db` (the books), `merchant-sim/pdf-out/` (138 stress PDFs), `merchant-sim/tour-shots/` (view screenshots), harnesses `MerchantSimSeed`, `MerchantBulkStress`, `MerchantTour` (all rerunnable).*
