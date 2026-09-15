# InvoiceStudio — Print Template Design Reference

This is the **complete design vocabulary** of InvoiceStudio print templates.
If you are an AI assistant asked to design or edit a bill template, read this
once and you know everything the Template Designer can do — every element
type, every property, every variable binding, and the workflow that produces
professional results.

> **The golden loop (never design blind):**
> 1. `duplicate_template` a preset (or `create_template` from scratch) —
> 2. `get_template` to read its anatomy — every element with exact mm
>    coordinates, fonts, colors and bindings,
> 3. `update_template` with your edited element list,
> 4. `render_template_preview` to SEE the rendered bill exactly as it prints,
> 5. read the `warnings` in the response (out-of-bounds elements, unknown
>    bindings), fix, re-render, repeat.
> 6. Only when the preview looks right, declare the template done.

---

## 1. Page setup

A template is a page plus a list of positioned elements. All positions and
sizes are in **millimetres (mm)**, origin = top-left corner of the page.

| pageSize | Width × Height (mm) | Typical use |
|---|---|---|
| `A4` | 210 × 297 | Full GST invoice, letterhead style |
| `A5` | 148 × 210 | Compact counter/delivery invoice |
| `LETTER` | 215.9 × 279.4 | US Letter |
| `LEGAL` | 215.9 × 355.6 | US Legal |
| `THERMAL_80` | 80 × auto | POS roll receipt (80 mm) |
| `THERMAL_58` | 58 × auto | Mini portable POS receipt |
| `CUSTOM` | user width × height | Anything else |

Additional page properties:

- `orientation`: `portrait` (default) or `landscape`.
- `margin`: `{top, right, bottom, left}` in mm (default 8 mm each) — a hint
  for safe-area placement; elements are positioned absolutely, so **keep
  every element inside the margins** for printable results.
- `autoHeight: true` (thermal rolls): the printed height grows with the
  item-table row count; other elements below the table shift down
  automatically. Never place anything in the bottom 12 mm — that is the
  tear-off zone.
- Template-level `printOffsetX/Y` (mm): physical printer alignment nudge,
  normally 0.

**Element JSON contract** (used by `create_template`, `update_template` and
`render_template_preview` drafts):

```json
{ "type": "TEXT", "name": "Invoice Title", "x": 65, "y": 14, "w": 80, "h": 10,
  "text": "TAX INVOICE", "fontSize": 16, "fontWeight": 700, "align": "center" }
```

`name` is required in spirit (it is how warnings and the designer refer to
elements); everything except position/size/type has sensible defaults.
Unknown `type` values degrade to `TEXT` **and are reported in the response
warnings** — the response never silently drops a property.

## 2. Element types (all 23)

| Type | Purpose | Key properties |
|---|---|---|
| `TEXT` | Static text or `{{variable}}` bound text | text, binding, typography block |
| `TABLE` | The item grid | columns, headerBg, rowHeight, borderStyle, zebra |
| `IMAGE` | Raster picture or business logo | src (base64 data URI), useBusinessLogo, objectFit |
| `LINE` | Straight rule | direction h/v, borderWidth, borderColor, dashPattern |
| `RECT` | Box / panel / letterhead band | bg, borderWidth, borderRadius, fillType |
| `PAGENO` | `Page X of Y` | text style, align |
| `QRCODE` | UPI payment QR or custom payload | qrSource upi_amount/upi/custom, qrColor |
| `BARCODE` | Barcode (default `{{invoice_no}}`) | barcodeData, barcodeColor, barcodeShowText |
| `CIRCLE` | Filled/outlined circle | radius, bg, borderWidth |
| `ELLIPSE` | Oval | radiusX, radiusY |
| `POLYLINE` | Open polyline from points | points "x,y x,y ...", strokeEnabled |
| `POLYGON` | Closed polygon from points | points, bg |
| `ARC` | Arc segment | startAngle, arcLength, arcType open/chord/round |
| `PATH` | SVG path commands (M L C Q A Z) | pathData |
| `STAR` | Star shape | starPoints, innerRadius, outerRadius |
| `ARROW` | Arrow | arrowShaftWidth, arrowHeadLength/Width, arrowHeadStyle |
| `DIVIDER` | Themed separator line | dividerOrientation h/v, dividerStyle solid/dashed/dotted/double |
| `FREEHAND` | Hand-drawn stroke points | points |
| `WATERMARK` | Big diagonal background text | watermarkText, watermarkOpacity, watermarkAngle |
| `SVG` | Inline SVG markup | svgSource |
| `ICON` | Built-in icon glyph | iconName (e.g. "star") |
| `GROUP` | Logical container of children | groupId on children |
| `COMPONENT` | Reusable composite | componentType |

### Common properties every element accepts

**Position & layout:** `x`, `y`, `w`, `h` (mm), `zIndex` (paint order),
`rotation` (-180..180°), `locked`, `hidden`, `repeatOnPages` (print on every
page of multi-page bills — use for headers/footers), `hideWhenBlank` (skip
when the bound variable is empty — use for optional fields like GSTIN),
`opacity` (0..1), `clipEnabled` + `clipShape` (`circle`, `rounded_rect`),
`scaleX/scaleY`, `flipHorizontal/flipVertical`.

#### zIndex layering convention (use it for every overlapping design)

Elements are painted in ascending `zIndex` (ties fall back to list order).
Overlapping designs (invoice cards over party frames, ribbons over panels,
signature stamps over text) become unmaintainable when everything is `0` —
stick to this 4-tier hierarchy:

| Tier | zIndex | Paints |
|---|---|---|
| 1 | 1–10 | Background fills, letterhead bands, corner artwork, watermark |
| 2 | 11–30 | Outlined boxes, party frames, the item-table grid |
| 3 | 31–60 | Text labels, bound values, logos, QR/barcodes |
| 4 | 61–100 | Overlapping badges, ribbons, signature stamps, status overlays |

#### Coordinate convention for POLYGON / POLYLINE / PATH / FREEHAND

`points` and `pathData` are **element-local**: each coordinate is ADDED to
the element's `x, y` (`final = elementX + pointX`). Pick ONE of two modes
and never mix them:

- **Local mode (recommended):** set `x, y, w, h` as the shape's container
  box and write points relative to `(0,0)` — e.g. a right-pointing triangle
  filling a 20×10 box at position (120,40): `x: 120, y: 40, w: 20, h: 10,
  points: "0,0 20,5 0,10"`. The preview warnings will flag shapes whose
  local coordinates exceed the box.
- **Page-absolute mode:** set `x: 0, y: 0` and write full-page mm
  coordinates directly in `points` (e.g. a bottom ribbon across an A4:
  `points: "0,277 210,277 210,287 0,287"`). With `x/y = 0` the offset is a
  no-op, so absolute coordinates land exactly where you wrote them.

`PATH` `pathData` follows the same rule: `M 10 5` means "10 mm right and
5 mm down from the element's x,y". Commands: `M` move, `L` line, `C`/`Q`
curves, `A` arc, `Z` close.

**Typography** (TEXT, PAGENO, table cells): `fontFamily` (e.g. "Segoe UI",
"Arial"), `fontSize` (pt), `fontWeight` (400 or 700), `italic`,
`underline`, `strikethrough`, `uppercase`, `color` (#RRGGBB), `align`
(left/center/right), `vAlign` (top/middle/bottom), `lineHeight` (default
1.25), `letterSpacing`, `wordSpacing`, `textTransform`.

**Box & border** (any boxed element): `bg` (#RRGGBB or "transparent"),
`borderWidth` (mm), `borderColor`, `borderRadius` (mm; per-corner:
`topLeftRadius` etc.), `padding` (mm), individual sides `borderTop/Bottom/
Left/Right` + per-side `borderTopWidth/Color/Style` where style is
`solid | dashed | dotted | none`.

**Fill & gradient:** `fillType` (`solid | linear | radial | none`),
`gradientStartColor`, `gradientEndColor`, `gradientAngle` (deg),
`gradientCenterX/Y`, `gradientRadius` (0..1 fractions of the box).

**Stroke & dash:** `strokeEnabled`, `strokeType` (inside/centered/outside),
`lineCap` (butt/round/square), `lineJoin` (miter/round/bevel),
`dashPattern` (e.g. `"5,3"`), `dashOffset`.

**Shadow & blur:** `shadowEnabled`, `shadowColor`, `shadowBlur`,
`shadowOffsetX/Y`, `shadowOpacity`; `blurEnabled`, `blurRadius`.

> **Printing reality check:** thermal printers and mono printers crush
> subtle colors. Prefer dark text (#1a1a1a), bold header bands, and test
> with `render_template_preview`. Light gray (#c8c8c8) rules look good on
> screen and print fine on laser, not on thermal.

## 3. Variable bindings — putting live data on the bill

A TEXT element shows either static text or bound data: set `binding` to a
variable key, or embed `{{key}}` placeholders inside `text` (mixed text and
placeholders is fine: `"Invoice {{invoice_no}}"`).

### Fixed variables (business & document)

| Key | Renders as |
|---|---|
| `invoice_no` | Bill number (e.g. INV-2026-014) |
| `invoice_date` | Bill date (ISO) |
| `doc_type` | Document title ("TAX INVOICE", ...) |
| `business_name`, `business_address`, `business_gst`, `business_phone`, `business_email`, `business_state` | Seller identity block |
| `business_logo` | Logo data (used by IMAGE elements) |
| `terms` | Terms & conditions text |
| `bank_name`, `bank_account`, `bank_ifsc`, `bank_upi` | Bank / UPI block |
| `buyer_name`, `buyer_trade_name`, `buyer_gst`, `buyer_gstin`, `buyer_address`, `buyer_phone`, `buyer_email`, `buyer_state`, `buyer_state_code`, `buyer_city`, `buyer_contact_person` | Buyer block |
| `po_no`, `vehicle_no`, `transport_name`, `transport_phone`, `transport_contact`, `e_way_bill`, `due_date` | Logistics & terms block |
| `parcel`, `parcels` | Parcel count |
| `page_no`, `page_count`, `copy_label` | Multi-page / GST copy labels |
| `notes` | Bill notes |
| `due_amount`, `paid_amount`, `payment_status` | Payment state |

### Totals variables

`subtotal`, `discount`, `taxable`, `cgst`, `sgst`, `igst`, `round_off`,
`grand_total` (currency-formatted), `amount_in_words`, `total_qty`,
`item_count`.

### Custom variables

Anything defined in the Variables view — `list_variables` over MCP returns
the live list (fixed + table scope). Create new ones with `create_variable`.
A `{{key}}` that resolves to nothing renders empty for real bills; in
previews unknown keys are reported in `warnings` so typos never sneak into
print.

## 4. The item TABLE element

The table renders the bill's line items. Configure:

- `columns`: list of `{key, label, width, align}` — `width` is a
  **percentage of table width** (make columns sum to ~100).
- `headerBg` / `headerColor`, `rowBg` / `rowColor`, `zebraColor` +
  `showZebra` for banded rows.
- `rowHeight` (mm per row, default 7), `borderStyle`
  (`grid | rows | outline | none`), `tableBorderColor`, `tableBorderWidth`,
  per-side `borderTop/Bottom/Left/Right`.
- **`minRows`** (default 0): render at least N data rows so the grid fills a
  fixed band even for 1–2 item invoices — empty rows draw borders only, no
  text. This is the key to the Indian-GST "continuous column line" look:
  set the TABLE's `h` to the full band (e.g. 70 mm) and `minRows` to
  `ceil((h − header ≈ 8 mm) / rowHeight)` (e.g. 70/7 → 9). When the drawn
  rows don't reach the declared `h`, the column dividers and bottom border
  are **extended automatically through the remainder** — no more hand-placed
  LINE elements to fake the grid.
- Typography of cells follows the element's font properties; header row is
  bold automatically.

#### Continuous column line formula (when you position things manually)

The x-position of the divider after column i (0-based) is:

```
x_i = tableX + tableW × (colWidth_0 + colWidth_1 + … + colWidth_i) / 100
```

With `minRows` you normally never need this — but it is how the reference
invoices align the summary bar's `{{parcel}}`, `{{total_qty}}` and
`{{taxable}}` under specific table columns.

### Column keys

| key | column shows |
|---|---|
| `sr` (or `index`, `#`, `s_no`) | Row number |
| `desc` (or `name`, `description`, `item_name`) | Item name |
| `hsn` (or `sac`) | HSN/SAC code |
| `qty` (or `quantity`) | Quantity |
| `unit` | Unit (PCS, KG...) |
| `rate` (or `price`, `unit_price`) | Rate |
| `disc` (or `discount`, `disc_pct`) | Line discount % |
| `taxable` (or `taxable_value`) | Taxable value |
| `gst` (or `gst_rate`, `tax`) | GST % |
| `amount` (or `total`, `total_amount`) | Line total incl. GST |
| anything else | The item's custom field with that key |

## 5. Images, QR, barcode

**IMAGE element**
- Simplest logo: `"useBusinessLogo": true` — pulls the business logo from
  Settings → Profile; you need no image data at all.
- Any picture: `src` = a base64 data URI embedded directly in the template,
  e.g. `"data:image/png;base64,iVBORw0KG..."`. JPEG also works
  (`data:image/jpeg;base64,...`).
- **Transparency is fully supported**: 32-bit ARGB PNGs keep their alpha —
  the render engine composites them with `SRC_OVER`, so transparent
  backgrounds blend with whatever is behind (panel colors, header bands).
  Use transparent PNGs for signatures, brand badges and corner geometry
  instead of flattening white boxes behind them.
- `objectFit`: `contain` (default, no distortion), `cover` (fills, crops),
  `fill` (stretches).
- Recommended printed sizes:
  - Brand logos: 60–80 mm wide, 20–30 mm tall.
  - Signatures: 30–40 mm wide, 10–15 mm tall.
  - Corner artwork / flourishes: 30–45 mm wide and tall.
- Keep embedded images small (< 300 KB base64): they live inside the
  template JSON and every tool call carries them.
- If the user asks for a *new* logo/artwork, that is image **generation** —
  outside MCP. Ask the user for the image, or design with shapes/typography
  instead (RECT bands + CIRCLE + TEXT make clean minimal logos).

**QRCODE element** — `qrSource`:
- `upi_amount` (default): scan-to-pay QR with the grand total baked in.
- `upi`: UPI intent without amount.
- `custom`: your payload in `qrCustom` (supports `{{variables}}`).
- `qrColor` for the modules; keep quiet-zone white for scannability.

**BARCODE element** — `barcodeData` (default `{{invoice_no}}`),
`barcodeColor`, `barcodeShowText`, and `barcodeFormat`:
- `barcodeFormat` (symbology): `CODE_128` (default — every template without
  the field keeps Code 128), `EAN_13`, `EAN_8`, `CODE_39`, `ITF`, `UPC_A`,
  `QR_CODE`.
- Numeric symbologies validate the payload: EAN_13 needs exactly 13 digits,
  EAN_8 → 8, UPC_A → 12, ITF → even digit count. A wrong payload silently
  falls back to Code 128 so a mis-typed size code never blocks a print run.
- For retail price tags use EAN_13; for garment tags Code 128 with
  `{{barcode}}` is the norm.

## 6. Proven design recipes

### 6.1 Classic A4 GST invoice anatomy (what the presets do)

```
y 0-30   Header band:   RECT full-width (bg dark or brand color) OR letterhead:
                        IMAGE logo top-left (useBusinessLogo), business_name
                        16-18pt bold, address/GSTIN/phone 8-9pt below
y 12-22  Doc title:     "TAX INVOICE" centered 16pt bold (or top-right)
y 30-58  Party block:   RECT outlined left = Bill To (buyer_name bold,
                        address, buyer_gst), right = invoice_no, date,
                        po_no, due_date as label:value pairs
y 58-64  Table header:  TABLE starting ~y=60, columns
                        sr(6) desc(34) hsn(10) qty(8) unit(8) rate(10)
                        disc(8) gst(8) amount(18) — adjust widths to sum 100
y 64-75  Table growth:  reserve ≥ rows × rowHeight; totals block BELOW the
                        table's maximum extent (A4 fits ~15 rows before the
                        totals zone; for auto-height pages elements shift)
y 200-230 Totals:       right column x≈130-200: subtotal, taxable, cgst,
                        sgst/igst, round_off each 4.5mm apart, then
                        grand_total in a RECT band, bold 12pt
y 200-215 Amount in words: left, italic 8pt, w≈110
y 235-260 Bank block:   bank_name, a/c, IFSC, UPI + QRCODE (22×22mm) right
y 260-280 Terms:        terms 7pt gray; signature line right;
                        PAGENO centered if multi-page
Footer:   copy_label ("Original for Recipient") centered 7pt at y≈285
```

### 6.2 Thermal 80mm receipt anatomy

```
width 80, autoHeight, margins 2-3mm, fontSize 8-10pt
y 2    business_name centered bold 12pt; address/phone 7pt centered
y 18   divider; invoice_no + date as two small TEXT lines (or one line
       "INV-2026-014  2026-09-14")
y 26   buyer_name (walk-in → hideWhenBlank)
y 32   TABLE: desc(46) qty(14) rate(18) amount(22), rowHeight 5,
       borderStyle "rows", zebra off
below  divider, subtotal/cgst/sgst/grand_total right-aligned 4.2mm apart,
       grand_total bold 11pt
below  QRCODE 24×24mm centered (upi_amount), "Scan to Pay" 7pt under it
bottom divider + "Thank you, visit again!" centered; PAGENO off
```

Rules of thumb: A4 body text 9-10 pt, labels 7-8 pt, title 14-18 pt;
labels in gray #6b7280, values in #1a1a1a; one accent color max;
align numbers right; never let two TEXT elements overlap — the preview
will show it and the print will too.

### 6.3 Design self-checklist (verify with render_template_preview)

- All elements inside page bounds minus 8 mm margins (`warnings` catches
  hard overflows).
- Table starts high enough for ~15 rows on A4, or page is autoHeight; for a
  fixed grid band set `minRows` (see §4) instead of hand-placing lines.
- Totals sit below the table's grown extent, never under it.
- Every `{{binding}}` resolves (warnings list unknown keys).
- QR ≥ 20×20 mm for reliable phone scanning; barcode ≥ 30 mm wide.
- Colors pass the mono-print test: preview still readable in grayscale.
- `hideWhenBlank` on optional fields (GSTIN for B2C, transport fields).
- Overlapping elements follow the zIndex tiers (§2).

### 6.4 Pre-baked blocks for standard Indian GST invoices

95% of trade invoices reuse these five blocks. Coordinates assume A4
portrait (210 mm wide); scale proportionally for other sizes.

**① Double-decker invoice card (top-right)** — header-value stack:
```
RECT  x=130 y=10 w=70 h=22  bg #f3ede4, borderRadius 2
TEXT  "INVOICE NO."  x=132 y=12 w=66 h=5  7pt gray #6b7280, bg #f3ede4
TEXT  {{invoice_no}} x=132 y=17 w=66 h=6  12pt bold #1a1a1a, bg #f3ede4
LINE  x=133 y=24.5 w=64 (h, 0.26mm, #d8cfc0)
TEXT  "INVOICE DATE" x=132 y=25.5 w=32 h=4  6.5pt gray
TEXT  {{invoice_date}} x=132 y=29 w=32 h=5  8pt #1a1a1a
```

**② Split party block (middle)** — Shipped-to left, Billed-to right:
```
RECT frame ×2:  x=10 / x=108, y=34, w=96/92, h=26, 0.3mm #1a1a1a border
Left  (Consignee): "Consignee (Shipped to)" 7pt bold on #f3ede4 band,
      {{buyer_name}} 9pt bold, {{buyer_address}} 7.5pt,
      GSTIN {{buyer_gstin}} 7.5pt, State: {{buyer_state}} ({{buyer_state_code}}) 7.5pt
Right (Receiver):  "Receiver (Billed to)", {{buyer_name}},
      {{buyer_address}}, GSTIN {{buyer_gstin}}
Transport strip between/below: {{transport_name}} · {{vehicle_no}} · {{po_no}}
```

**③ Table summary bar (directly under the TABLE)** — aligns with columns
using the divider formula from §4 (example for sr6/desc34/hsn10/qty8
widths, tableX=10):
```
RECT x=10 y={tableY+tableH} w=190 h=6 bg #f3ede4
TEXT "PARCEL QUANTITY : {{parcel}}"  x=12  …  7.5pt bold
TEXT {{total_qty}}  x=10+190×0.58  …  right-aligned under qty column
TEXT {{taxable}}    x=10+190×0.74  …  right-aligned under taxable column
```

**④ Bank details & terms (left, below table)**:
```
RECT "BANK DETAILS" band  x=10 y=240 w=95 h=5.5 bg #ded1c1, 7.5pt bold
TEXT bank_name / A/c {{bank_account}} / IFSC {{bank_ifsc}}  7.5pt, 4.2mm apart
TEXT "GST NO." + {{business_gst}}  7.5pt
TEXT {{terms}}  x=10 y=262 w=95 h=28  6.5pt #4b5563 (7 standard points)
```

**⑤ Tax summary & signatory (right, below table)**:
```
Rows 4.5mm apart, x=130..200 right column: Taxable {{taxable}},
CGST {{cgst}}, SGST {{sgst}}, (IGST {{igst}} hideWhenBlank), Round off {{round_off}}
RECT GRAND TOTAL band x=130 y=... w=70 h=9 bg #1a1a1a,
     "GRAND TOTAL" white 8pt left + {{grand_total}} white 10.5pt bold right
TEXT "For, {{business_name}}"   x=132 y=272  8pt
IMAGE signature  x=150 y=273 w=35 h=12 (transparent PNG, ARGB)
LINE  signature rule  x=140 y=287 w=45
TEXT "Authorised Signatory"  x=140 y=288  7pt centered
```

## 7. Tool cheat-sheet for template work

| Tool | Use |
|---|---|
| `list_templates` | See what exists (presets + user templates) |
| `get_template` | Read full anatomy: every element, every property |
| `duplicate_template` | Copy a preset as your starting point (recommended) |
| `create_template` | New template from an element list |
| `update_template` | Replace name/page/elements (confirmation-gated) |
| `render_template_preview` | Render saved template OR `draft` to PNG + warnings |
| `list_variables` / `create_variable` | Discover / add `{{bindings}}` |
| `get_template_design_guide` | This document |

`render_template_preview` parameters:

- `{ "id": "tpl_..." , "dpi": 150 }` — render a saved template.
- `{ "draft": { "name": "Draft", "pageSize": "A4", "elements": [...] },
    "dpi": 150 }` — render **unsaved** element list; nothing touches the
  database, so iterate as freely as you like.
- Response: a native image content block (the PNG, same engine as PDF
  export, sample data filled in) plus `meta`: `widthMm`, `heightMm`
  (auto-height applied), `pixelWidth/Height`, `elementCount`, `mode`
  (saved/draft), and `warnings` — out-of-bounds elements and unresolved
  bindings, each naming the offending element. Fix every warning before
  saving.
- `dpi` 72–300 (default 150). Every dpi renders the **complete page** — the
  renderer self-scales its 300-dpi drawing to the requested canvas, so a
  150-dpi preview is uncropped and faithful. Use 150 for iteration (lean
  payloads) and 300 only for fine print/edge checks.

## 8. Barcode Mode — label design & bulk print (thermal strip stock)

Barcode Mode turns the designer into a **label editor** for thermal label
printers (TSC TA210 and friends). The core idea: you design **ONE label
cell**; the print engine tiles it across the strip, substitutes variables
per label, and can output hundreds of labels in one click.

### 8.1 The mental model (die-cut strip)

```
 |←mL→| label 1 |←gapX→| label 2 |→mR|     ← strip (liner) width = printed page
 |------------ labelHeight ------------|   ← feed gap (gapY) is advanced by the
                                              printer's gap sensor, never printed
```

- **Canvas = one label cell** (`labelWidth` × `labelHeight` mm). Rulers,
  snapping, margins and zoom all behave exactly like bill templates.
- **One printed page = one strip row** carrying up to `columns` labels.
  Slots fill left → right from the print queue; the last page simply
  leaves the remaining columns blank.
- Elements are positioned in mm relative to the cell origin (0,0) — same
  as any template.

### 8.2 Template JSON additions

Top level (both optional; absent = normal bill template):

```json
{
  "id": "tpl_label_1",
  "name": "Garment Size Tag",
  "mode": "label",
  "labelConfig": {
    "stripWidth": 108,      // liner width mm (TA210 prints up to ~108 mm)
    "columns": 2,           // labels across the strip (1 = standard roll)
    "labelWidth": 50,       // design cell width mm  = designer canvas width
    "labelHeight": 25,      // design cell height mm = designer canvas height
    "gapX": 3,              // gap between columns mm
    "gapY": 3,              // feed-direction gap mm (driver handles)
    "cornerRadius": 2,      // die-cut corner radius mm (preview guides)
    "orientation": "0",     // artwork rotation at print time: 0|90|180|270
    "marginL": 0, "marginR": 0,   // strip side margins mm
    "stockType": "gap"      // gap (die-cut roll) | continuous
  },
  "elements": [ ...normal elements... ]
}
```

Rules the engine enforces:
- `mode: "label"` + `labelConfig` never break old templates: JSON without
  them loads as `mode "bill"` and `labelOrNew()` hands out safe defaults.
- With `orientation` 90/270 the **physical** cell on the strip is the
  design cell transposed (design height becomes strip width). Strip-fit
  math, page size and feed pitch all use the physical cell; the designer
  canvas stays in design orientation, so you never redesign for a rotated
  print — you just pick the angle.
- Labels are centered inside any strip slack (marginL/R + leftover width),
  so slightly-wider stock still prints symmetrically.
- Extra dynamic variables available on every label: `{{label_date}}`
  (dd-MM-yyyy) and `{{label_time}}` (HH:mm) — handy for "Packed on" marks.

### 8.3 Barcode variables (possible values / quick-picks)

Create variables with **scope `barcode`** (Catalog → Variables → Barcode
Label pill). `choices` holds the possible values, comma separated:

```json
{ "key": "size", "label": "Size", "scope": "barcode", "choices": "S,M,L,XL,XXL" }
```

- Drop them on the label as `{{size}}`, `{{item_name}}`, `{{length}}` …
  exactly like bill variables; they also work inside `barcodeData`.
- The Bulk Print popup gives each variable its own column with a
  quick-pick combo (typing a custom value is always allowed).
- Any `{{placeholder}}` typed on the label without a matching variable
  still gets a column in the popup, so a run is never blocked.

### 8.4 Bulk print workflow (keyboard-first)

Templates → open a label template → **🖨 Bulk Print**:

1. One table row = one print line: variable columns + **Copies**.
2. Type values into the combos, set copies — e.g.
   `A / 19 / S × 20`, `A / 20 / M × 10`, `B / 19 / S × 11`.
3. **Print All** expands the queue into physical labels, fills the strip
   rows and streams the pages to the printer. Each run is appended to
   Catalog → **Label Print History** (when, template, printer, size,
   columns, pages, labels, the queue summary).

Keyboard map in the popup:

| Key | Action |
|---|---|
| Enter | commit cell, move DOWN (adds a fresh row at the end) |
| Tab / Shift+Tab | next / previous cell |
| ↑ ↓ ← → | move between rows / cells |
| Insert / Alt+N | add row |
| Ctrl+Delete | remove selected row |
| Ctrl+Enter | Print All |
| F4 | close |

Use **Test Print (1)** first: it outputs one label with the first row's
values so you can verify alignment against the die-cut before committing
a 500-label run.

### 8.5 TSC TA210 printing — native TSPL (default for TSC printers)

TSC printers understand their own command language, **TSPL/TSPL2**. When the
selected printer's name contains TSC / TA2xx / TA3xx, InvoiceStudio skips
the Windows GDI driver entirely and streams a raw TSPL script to the
spooler (datatype `RAW` — the TSC driver passes it untouched to the port):

```text
SIZE 432 dot,200 dot      ← one strip row: 54 × 25 mm @ 8 dots/mm (203 dpi)
GAP 24 dot,0 dot          ← feed gap 3 mm (GAP 0,0 for continuous stock)
DIRECTION 1               ← print reads upright after tearing = strip view
CLS
BITMAP 0,0,54,200,0,<1-bit rows>
PRINT 1,1                 ← exactly ONE label — quantity is ours, not the driver's
```

Why this matters on the shop floor:

- **Exact quantities.** The old JavaFX/GDI path let the driver guess the
  stock, so one selected label could feed several blank ones. TSPL owns
  `SIZE`/`GAP`/`PRINT`, so the driver never feeds anything extra.
- **Strip view = printout (WYSIWYG).** Pages are rasterized with the same
  renderer as the preview at the printer's native dot grid (TA210 = 203
  dpi = 8 dots/mm, TA310 = 300 dpi = 12 dots/mm) — no scaling, no
  rotation, no margin surprises from the driver stock.
- **Copies are free.** Identical consecutive rows compress into one
  bitmap download and a single `PRINT n,1`.

Knobs (rarely needed): `-Dinvoicestudio.print.engine=tspl|driver` forces
or disables the native path; `-Dinvoicestudio.tspl.direction=0` flips the
print 180° on hardware that ejects face-down;
`-Dinvoicestudio.tspl.threshold=0..255` darkens/lightens the burn cut.

Driver setup notes:

- **The driver's stock no longer matters** for TSC printers — the script
  declares its own size. Keep the driver stock roughly correct anyway so
  Windows print dialogs show sane previews.
- Keep `stripWidth` within the print head: the TA210 (4-inch, 203 dpi) prints
  at most **108 mm** across per the official datasheet (the 300-dpi TA310:
  104 mm). The Bulk Print result warns when a strip exceeds the head.
  (Older builds wrongly said 2-inch / 54 mm.)
- For sideways labels (vertical dispensers) use the Label Stock dialog's
  **Rotate Design 90°** button — one-click WYSIWYG spin of the whole
  design into the print orientation (preferred over legacy `orientation`).
- Non-TSC printers (Zebra, inkjets, PDF) keep the classic JavaFX/driver
  pipeline with automatic form matching.
- Label Print History is an info-only audit: it never blocks or limits
  printing and is partitioned per user.

### 8.6 Designer zoom, grid & quick jumps

- **Zoom range is 30% – 400%** (footer slider, `Ctrl + Mouse Wheel`,
  `Ctrl + +/−`, `Ctrl + 0` resets to 100%).
- The background **grid and rulers adapt to zoom** — every +100% the cell
  refines one step, `10 → 5 → 2 → 1 mm`, so cells stay a workable size on
  screen instead of collapsing into a wall of lines:
  | Zoom | Grid cell | Ruler labels |
  |---|---|---|
  | ≤ 105% | 10 mm | every 10 mm |
  | ≤ 205% | 5 mm | every 10 mm |
  | ≤ 305% | 2 mm | every 5 mm |
  | > 305% | 1 mm | every 5 mm |
- **Selection handles always sit on the true corners** of the element,
  even when the element extends past the label edge — handles, dashed
  border and geometry spinner values always agree.
- Quick jumps: **`Ctrl+Shift+L`** opens the Label Designer (Barcode Mode)
  from anywhere — or toggles Barcode Mode on the template being designed;
  **`Ctrl+Shift+B`** opens the Bulk Label Print window directly. All
  shortcuts are listed in Settings → **Shortcuts** tab and the F1 popup.
