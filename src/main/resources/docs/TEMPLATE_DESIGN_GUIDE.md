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
- Typography of cells follows the element's font properties; header row is
  bold automatically.

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
- `objectFit`: `contain` (default, no distortion), `cover` (fills, crops),
  `fill` (stretches).
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
`barcodeColor`, `barcodeShowText`.

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
- Table starts high enough for ~15 rows on A4, or page is autoHeight.
- Totals sit below the table's grown extent, never under it.
- Every `{{binding}}` resolves (warnings list unknown keys).
- QR ≥ 20×20 mm for reliable phone scanning; barcode ≥ 30 mm wide.
- Colors pass the mono-print test: preview still readable in grayscale.
- `hideWhenBlank` on optional fields (GSTIN for B2C, transport fields).

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
- `dpi` 72–300 (default 150). Use 300 only for fine print checks; previews
  travel as base64, so 150 keeps payloads lean.
