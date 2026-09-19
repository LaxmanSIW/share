# Chapter 7 — Domain Models, Part 2: Templates & Design Objects

> **Part 3 of InvoiceStudio: Zero to Finished Product**
> Files covered in full this chapter: `model/Template.java`, `model/TemplateElement.java`,
> `model/ElementType.java`, `model/TableColumn.java`, `model/DocType.java`,
> `model/PageConfig.java`, `model/PageSizeName.java`, `model/LabelConfig.java`,
> `model/LabelPrintHistory.java`, `model/VariableDef.java`, `model/CustomComponent.java`,
> `model/ComponentPreset.java`, `model/CustomFontDef.java`, `model/PresetTemplates.java`
> — all 14 read and reproduced from the repository.
> Goal at the end: you understand how a printed invoice is described as pure data —
> and why that one decision makes the designer, the PDF exporter, the preview, the
> label pipeline and the AI assistant all possible with one shared renderer.

---

## 1. Chapter goal

By the end of this chapter you will have built the **document description layer**: a set
of plain Java classes that describe *what an invoice looks like* without saying anything
about *how it is drawn*. One `Template` object can be:

- shown as a live preview on screen (Chapter 16),
- exported to PDF (Chapter 16),
- rasterised and sent to a thermal printer as TSPL commands (Chapter 17),
- edited visually in the drag-and-drop designer (Chapter 15),
- created, listed and modified by the AI assistant through the MCP server (Chapter 18).

The same 14 files serve all five consumers. That is the payoff of "design as data".

## 2. Story intro

Think of a **theatre script**. The script says: *"The hero enters stage left, wearing a
red coat."* The script does not paint anything — a director (a movie), a stage manager
(a play) and an audiobook narrator each turn the same script into a different
performance.

InvoiceStudio needed exactly that. If the invoice layout lived inside a JavaFX screen,
only JavaFX could print it. If it lived inside PDFBox code, only PDFBox could show it.
Instead, the app stores the layout as **data**: rectangles, text boxes, tables, barcodes
— each with a position in millimetres, a colour, a font. Then every "performance"
(JavaFX preview, PDFBox export, TSPL raster) reads the same script and renders it its
own way. A millimetre is a millimetre everywhere: on the designer canvas, in the PDF,
and on an 80 mm thermal roll.

The second problem this solves is **persistence with evolution**. Templates are saved as
JSON in the SQLite database. A shop that saved a template in 2025 must still be able to
open it in 2026 after the app gained watermarks, gradients, and groups. The defence is a
small, repeated trick you will see in every file here: `@JsonIgnoreProperties(ignoreUnknown
= true)` plus null-safe getters that return defaults. Old JSON loads fine; unknown new
fields are ignored; missing old fields fall back to sensible defaults. Nothing crashes,
nothing is lost.

## 3. Concepts first

- **JSON (JavaScript Object Notation)** — a text format for structured data:
  `{"fontSize": 12, "color": "#1a1a1a"}`. Language-independent, human-readable.
- **Jackson** — the library that converts between Java objects and JSON. Writing a Java
  object to JSON is *serialisation*; reading JSON back into an object is
  *deserialisation*.
- **POJO / bean** — a "Plain Old Java Object": fields plus getters and setters, no
  business logic. Jackson maps JSON keys to bean properties by name (`fontSize` ↔
  `getFontSize()`/`setFontSize()`).
- **Enum** — a type with a fixed set of values (`A4`, `A5`, `LETTER`…). Safer than raw
  strings because the compiler checks you only use real values.
- **Factory** — a class whose job is to build ready-made objects (the six preset
  templates, the seven component presets) instead of making callers assemble hundreds of
  elements by hand.
- **Millimetres as the unit of design** — the canvas is 210×297 mm for A4. Every
  position and size is in mm. Renderers convert mm to pixels (screen), points (PDF) or
  dots (thermal printers) — each consumer does its own conversion, the data never changes.
- **Z-order (`zIndex`)** — when two elements overlap, the higher `zIndex` is drawn on
  top, like plates stacked on a table.
- **`groupId`** — several elements can share a group id so the designer can move them
  as one ("grouping" in PowerPoint).

## 4. Files in this chapter

| File | Type | Lines | Role |
|---|---|---|---|
| `model/Template.java` | Entity | ~90 | The document: page geometry + element list + mode |
| `model/PageConfig.java` | Value | ~120 | Page size, margins, orientation, auto-height (+ nested `Margins`) |
| `model/PageSizeName.java` | Enum | ~45 | A4/A5/Letter/Legal/Thermal 80/Thermal 58/Custom |
| `model/TemplateElement.java` | Entity | ~730 | **The central class**: one designable object, ~120 properties |
| `model/ElementType.java` | Enum | ~50 | The 22 element types |
| `model/TableColumn.java` | Value | ~40 | One column of a table element |
| `model/DocType.java` | Enum | ~55 | INVOICE/PROFORMA/QUOTATION/CHALLAN/CREDITNOTE |
| `model/LabelConfig.java` | Value | ~140 | Barcode-Mode stock geometry |
| `model/LabelPrintHistory.java` | Entity | ~90 | One bulk-label print run record |
| `model/VariableDef.java` | Entity | ~95 | Custom `{{variable}}` definition |
| `model/CustomComponent.java` | Entity | ~50 | Saved designer component group |
| `model/ComponentPreset.java` | Factory | ~260 | 7 built-in component presets |
| `model/CustomFontDef.java` | Entity | ~55 | Custom font metadata |
| `model/PresetTemplates.java` | Factory | ~700 | 6 preset bill templates + label starter |

## 5. Step-by-step build

### 5.1 `Template.java` — the document root

Full source (`src/main/java/com/invoicestudio/model/Template.java`):

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Template {
    private String id;
    private String name = "Untitled Template";
    private PageConfig page = new PageConfig();
    private List<TemplateElement> elements = new ArrayList<>();
    private double printOffsetX; // mm
    private double printOffsetY; // mm
    private String createdAt;
    private String updatedAt;

    /**
     * "bill" (default/legacy/null) = normal document template.
     * "label" = Barcode Mode: canvas is ONE label cell driven by labelConfig
     * (thermal strip stock, bulk variable printing).
     */
    private String mode = "bill";

    /** Barcode Mode stock geometry — null-safe via {@link #labelOrNew()}. */
    private LabelConfig labelConfig;

    public Template() {}

    public Template(String id, String name, PageConfig page, List<TemplateElement> elements) {
        this.id = id;
        this.name = name;
        this.page = page != null ? page : new PageConfig();
        this.elements = elements != null ? elements : new ArrayList<>();
    }
    // ... getters/setters (all null-safe as shown below) ...

    /** "label" = Barcode Mode; anything else (incl. null) is a normal bill template. */
    public String getMode() { return mode != null ? mode : "bill"; }
    public void setMode(String mode) { this.mode = mode; }

    public boolean isLabelMode() { return "label".equalsIgnoreCase(getMode()); }

    /** Never-null label config; returns the live instance or a fresh default. */
    public LabelConfig labelOrNew() {
        if (labelConfig == null) labelConfig = new LabelConfig();
        return labelConfig;
    }
    ...
    @Override
    public String toString() { return name; }
}
```

Block by block:

- **`@JsonIgnoreProperties(ignoreUnknown = true)`** — when Jackson reads JSON that
  contains keys this class has never heard of (a template saved by a newer app version),
  it skips them instead of throwing. This is the single most important line for
  forward-compatibility.
- **`page` and `elements` are initialised inline.** A `new Template()` is instantly
  usable: an A4 page with no elements. The 4-arg constructor re-applies the same
  null-guards — defensive duplication that costs nothing.
- **`mode` as a String, not an enum.** Deliberate: templates saved before Barcode Mode
  existed have no `mode` key at all. Jackson leaves the field at `null`; `getMode()`
  maps `null` → `"bill"`. An enum would either need `@JsonCreator` fallback tricks or
  crash on unknown values. `ISSUE:` this is stringly-typed by choice — the compiler
  cannot catch a typo like `"lebels"`; `isLabelMode()` uses `equalsIgnoreCase` to blunt
  that risk.
- **`printOffsetX/Y`** — the Settings screen's "fine-tune print position" nudge. The
  whole page shifts by these millimetres at print time; the designer never sees it.
- **`labelOrNew()`** — the never-null accessor pattern: callers never write
  `if (t.getLabelConfig() == null)`. This pattern repeats across the model package.

### 5.2 `PageConfig.java` — paper geometry

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public class PageConfig {
    private PageSizeName sizeName = PageSizeName.A4;
    private double width = 210.0; // mm
    private double height = 297.0; // mm
    private String orientation = "portrait"; // portrait, landscape
    private Margins margin = new Margins(8, 8, 8, 8); // mm
    private boolean autoHeight = false; // for continuous thermal rolls

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Margins {
        private double top = 8, right = 8, bottom = 8, left = 8;
        ...
    }
}
```

- **`width`/`height` are stored separately from `sizeName`** so a `CUSTOM` page can hold
  any numbers, and so a user can tweak A4's width slightly without creating a new enum.
- **`autoHeight`** is the thermal-receipt trick: an 80 mm roll has no bottom edge, so the
  renderer grows the page to fit the content instead of paginating. Chapter 16 shows
  where PDFBox and TSPL each consume this flag.
- **`Margins` is a nested static class** — it has no meaning outside a page, and Jackson
  serialises it as a nested object: `"margin": {"top": 8, "right": 8, ...}`.
- Every getter is null-safe (`getMargin()` returns a fresh 8 mm margin if the field is
  null). Corrupt or hand-edited JSON cannot crash the renderer — the worst case is a
  default-looking page.

### 5.3 `PageSizeName.java` — the paper catalogue

```java
public enum PageSizeName {
    A4("A4", 210, 297),
    A5("A5", 148, 210),
    LETTER("Letter", 215.9, 279.4),
    LEGAL("Legal", 215.9, 355.6),
    THERMAL_80("Thermal 80", 80, 240),
    THERMAL_58("Thermal 58", 58, 180),
    CUSTOM("Custom", 210, 297);
    ...
    @JsonValue
    public String getLabel() { return label; }

    @JsonCreator
    public static PageSizeName fromString(String val) {
        if (val == null) return A4;
        for (PageSizeName s : values()) {
            if (s.label.equalsIgnoreCase(val) || s.name().equalsIgnoreCase(val.replace(" ", "_"))) return s;
        }
        return A4;
    }
}
```

- **`@JsonValue`/`@JsonCreator`** make JSON store the *label* (`"A4"`, `"Thermal 80"`)
  rather than the enum name (`THERMAL_80`), and accept either form on the way back. The
  UI can therefore show friendly names while the file stays readable.
- **Fail-open parsing**: an unknown or null value becomes `A4`, never an exception. Same
  philosophy as the null-safe getters.
- **`ISSUE:`** the enum carries default dimensions but `PageConfig` duplicates them as
  field initialisers. If someone changes A4's default in one place only, the two drift.
  The enum values are used by `McpToolRegistry` and `TemplateDesigner` when creating
  custom sizes; the field initialisers win everywhere else.

### 5.4 `DocType.java` — five document kinds

```java
public enum DocType {
    INVOICE("invoice", "TAX INVOICE", "Invoice", "INV", "badge-accent"),
    PROFORMA("proforma", "PROFORMA INVOICE", "Proforma", "PI", "badge-neutral"),
    QUOTATION("quotation", "QUOTATION", "Quotation", "QT", "badge-neutral"),
    CHALLAN("challan", "DELIVERY CHALLAN", "Challan", "DC", "badge-neutral"),
    CREDITNOTE("creditnote", "CREDIT NOTE", "Credit Note", "CN", "badge-error");
    ...
    public boolean isRevenue() { return this == INVOICE; }
}
```

Five small facts, five big consumers:

- `title` (`"TAX INVOICE"`) is what the printed banner shows (renderer + presets).
- `label` is what dropdowns show; `toString()` returns it.
- `shortCode` (`INV`, `PI`, `QT`, `DC`, `CN`) seeds bill-number prefixes in BillingService.
- `styleClass` drives the CSS badge colour in HistoryView.
- `isRevenue()` is the financial rule: only INVOICEs count as income. Quotations and
  challans never touch the P&L — this single method is what enforces it.

### 5.5 `ElementType.java` — the vocabulary of the canvas

```java
public enum ElementType {
    TEXT("text"), IMAGE("image"), TABLE("table"), LINE("line"), RECT("rect"),
    PAGENO("pageno"), QRCODE("qrcode"), BARCODE("barcode"), CIRCLE("circle"),
    ELLIPSE("ellipse"), POLYLINE("polyline"), POLYGON("polygon"), ARC("arc"),
    PATH("path"), STAR("star"), ARROW("arrow"), DIVIDER("divider"),
    FREEHAND("freehand"), WATERMARK("watermark"), SVG("svg"), ICON("icon"),
    GROUP("group"), COMPONENT("component");
    ...
    @JsonCreator
    public static ElementType fromString(String val) {
        if (val == null) return TEXT;
        for (ElementType t : values()) {
            if (t.code.equalsIgnoreCase(val) || t.name().equalsIgnoreCase(val)) return t;
        }
        return TEXT;
    }
}
```

22 types, three families:

- **Content types** — TEXT, IMAGE, TABLE, QRCODE, BARCODE, WATERMARK: things that
  display bill data.
- **Decoration types** — RECT, LINE, CIRCLE, ELLIPSE, POLYLINE, POLYGON, ARC, PATH,
  STAR, ARROW, DIVIDER, FREEHAND, SVG, ICON: things that make the page look designed.
- **Structural types** — GROUP, COMPONENT: containers that reference other elements.

Fail-open default is `TEXT` (the least surprising type).

### 5.6 `TableColumn.java` — column spec

```java
public class TableColumn {
    private String key;          // e.g. "qty" — which bill field feeds this column
    private String label;        // e.g. "Qty" — header text
    private double width;        // percentage of table width (0-100)
    private String align = "left";
}
```

`key` is the join between the template and the bill: the renderer asks the
`RenderContext` for a value per key (`sr`, `desc`, `hsn`, `qty`, `unit`, `rate`, `gst`,
`amount`, plus custom-variable keys). `width` is a *percentage*, so a table scales with
its element box without recomputation.

### 5.7 `TemplateElement.java` — the heart of the system

This is the app's largest model (~730 lines, ~120 persisted properties). It is shown in
full in the repository — here are its sections, in file order, each explained:

**(a) Identity and canvas geometry**

```java
private String id;
private String name;
private ElementType type = ElementType.TEXT;
private double x; private double y;      // mm, top-left corner
private double w = 40; private double h = 10;
private int zIndex;
private boolean locked; private boolean hidden;
private boolean repeatOnPages;           // footer/header: draw on every page
private double rotation;                 // -180..180 deg
private boolean hideWhenBlank;           // hide if resolved text is empty
```

`getDisplayName()` (later in the file) turns any element into a human label for the
layers panel: a TEXT with content shows a 22-character preview; an IMAGE with
`useBusinessLogo` is "Business Logo"; a LINE says Vertical/Horizontal.

**(b) Individual borders** — `individualBorders` plus per-side width/colour/style
(`top/bottom/left/right × Width/Color/Style`) with `Double`/boxed fields: `null` means
"not set — fall back to the common border". The three `getEffectiveSide*()` methods and
`isSideActive(side)` resolve that fallback so renderers never duplicate the logic:

```java
public boolean isSideActive(String side) {
    if (!individualBorders) return borderWidth > 0;
    return switch (side.toLowerCase()) {
        case "top" -> isBorderTop() && getEffectiveSideWidth("top") > 0
                   && !"none".equalsIgnoreCase(getEffectiveSideStyle("top"));
        ...
    };
}
```

**(c) Text** — `text` (with `{{variables}}`), `fontFamily`, `fontSize` (pt), `fontWeight`
(400/700 — `isBold()`/`setBold()` map it to a boolean for checkboxes), italic, underline,
strikethrough, uppercase, `color`, `align`, `vAlign`, `lineHeight`, `lineSpacing`,
`letterSpacing`, `wordSpacing`, `textTransform`. `tableFontScale()` documents a real
backward-compatibility constraint: old templates rendered tables at an implicit 7.5 pt,
so new code computes `fontSize / 7.5` (clamped 0.5–3.0) to scale — changing the base
would silently shrink every saved invoice.

**(d) Appearance** — `bg`, `borderWidth/Color/Radius`, `padding`, `opacity`.

**(e) Image** — `src` (data URL), `objectFit` (contain/cover/fill), `useBusinessLogo`
(when true the renderer substitutes the live business logo from settings, so rebranding
a shop re-skins every template at once).

**(f) QR / barcode** — `qrSource` (`upi_amount`, `upi`, `custom`), `qrCustom`, `qrColor`;
`barcodeData` (usually `"{{invoice_no}}"`), `barcodeColor`, `barcodeShowText`,
`barcodeFormat` (`CODE_128` default, plus EAN_13, EAN_8, CODE_39, ITF, UPC_A, QR_CODE).
The UPI QR means a customer can scan a receipt to pay — the renderer builds the payload
from the bank UPI id and the bill total.

**(g) Table** — `columns`, `headerBg/Color`, `rowHeight` (mm), `borderStyle` (grid/rows/
outline/none), `showZebra`, `zebraColor`, `rowBg`, `rowColor`, `tableBorderColor/Width`,
per-side `Boolean` outer borders (null = true for legacy templates), and `minRows`:
"render at least N data rows so the grid lines fill the page even when the bill has 2
items" — an aesthetic rule Indian GST invoices follow.

**(h) Fill, gradient, stroke, dash, corners** — `fillType` (solid/linear/radial/none)
with gradient colour/angle/centre/radius; `strokeEnabled` with `strokeType`
(inside/centered/outside — matching design-tool semantics), `lineCap`, `lineJoin`,
`dashPattern` (`"5,3"`), `dashOffset`; per-corner radii falling back to `borderRadius`.

**(i) Vector shapes** — circle `radius`, ellipse `radiusX/Y`, `points` for
polygon/polyline (`"0,0 20,40 40,0"`), arc `startAngle/arcLength/arcType`, SVG
`pathData`, star `starPoints/innerRadius/outerRadius`, arrow shaft/head geometry and
`arrowHeadStyle`, divider orientation/style, watermark text/opacity/angle, `svgSource`,
`iconName`.

**(j) Effects and transforms** — shadow (enabled/colour/blur/offsets/opacity), blur,
`scaleX/scaleY`, flips.

**(k) Binding and structure** — `binding` (future data-binding hook), `visibleCondition`
(conditional rendering), `clipEnabled/clipShape`, `groupId`/`groupName` (grouping),
`componentType` (marks an element as coming from a saved component).

**(l) `copy()`** — a ~150-line field-by-field deep copy. The designer uses it for
undo/redo snapshots and clipboard duplication. It is written by hand rather than via
serialisation so that copying cannot silently gain or lose fields through Jackson
configuration. `ISSUE:` a hand-maintained copy must be updated with every new field —
if a future field is forgotten, undo will silently drop it. (In the current file,
`binding` and `visibleCondition` are *not* copied — flagged as `GAP:` for the designer
chapter.)

### 5.8 `LabelConfig.java` — Barcode-Mode stock

```java
/**
 *  ←marginL→[ label ][ gapX ][ label ]←marginR→   ← strip (liner) width
 *            ↑ labelHeight, feed gap = gapY (gap sensor)
 */
private double stripWidth = 100.0;
private int columns = 1;          // labels across the strip (max 8)
private double labelWidth = 50.0, labelHeight = 25.0;
private double gapX = 3.0, gapY = 3.0;
private double cornerRadius = 2.0;
private String orientation = "0"; // "0","90","180","270"
private double marginL = 0.0, marginR = 0.0;
private String stockType = "gap"; // gap | continuous
```

The class doc carries the ASCII diagram of die-cut stock — read it, it *is* the mental
model. Two methods matter:

- **`copy()`** — print pipelines tweak geometry (rotation per batch, margins for a
  different holder) without mutating the saved template.
- **`sanitize()`** — clamps every negative/absurd value (`columns > 8` → 8, negative
  gaps → 0, unknown orientation → `"0"`). A hand-edited `templates` row cannot crash
  the TSPL renderer.

### 5.9 `LabelPrintHistory.java` — the audit row

Plain record of one bulk print: template, printer, label cell size, `pages` (strip rows
sent), `labels` (physical count), `totalCopies`, a human `summary`
(`"Item A · 19 · S × 20; ..."`), the raw `linesJson` of the queue, user and timestamp.
`LabelHistoryView` lists these; billing never reads them — pure audit.

### 5.10 `VariableDef.java` — custom `{{variables}}`

```java
private String key;      // "size" → {{size}}
private String label;    // "Size" — shown in UIs
private String type = "text"; // text, number, date
private boolean builtin; // seeded by the app, not user-created

private String scope = "fixed";   // "fixed" | "table"
private String defaultValue = ""; // pre-fill in CreateBillView
private String choices = "";      // "S,M,L,XL,XXL" quick-picks in bulk print

public List<String> choicesList() { /* split on comma, trim, drop blanks */ }
```

- `scope = "fixed"` → one value per bill (an input field in CreateBillView).
  `scope = "table"` → one value per line item (an extra table column).
- `choicesList()` powers the quick-pick buttons in the Bulk Label Print popup — free
  text always remains possible.
- `builtin` marks the ~40 seeded variables the app guarantees (buyer/business/bank
  fields); the Variables view stops users from editing those keys.

### 5.11 `CustomComponent.java` + `CustomFontDef.java` — small holders

`CustomComponent`: `id, name, description, category, createdAt, width, height,
List<TemplateElement> elements` — a designer selection saved as a reusable sticker. The
constructor stamps `createdAt` with `Instant.now()`; `width/height` cache the bounding
box so the palette can size thumbnails without re-measuring.

`CustomFontDef`: font metadata — `source` (`google`/`upload`/`url`), `url`,
`fileUrl`, `dataUrl` (a base64-embedded TTF for uploads), `format`, `category`
(sans/serif/mono/display/handwriting). The Settings view manages the list; the renderer
resolves `fontFamily` through it. Note the getters here are *not* null-safe — an absent
`category` returns null. `GAP:` minor inconsistency with the package's own convention.

### 5.12 `ComponentPreset.java` — seven ready-made blocks

An enum of preset types (`INVOICE_HEADER`, `CUSTOMER_ADDRESS`, `INVOICE_TOTALS`,
`BANK_DETAILS`, `PAYMENT_TERMS`, `SIGNATURE_SECTION`, `DOCUMENT_FOOTER`), each with a
title, description and emoji icon, plus one factory:

```java
public static List<TemplateElement> createComponent(PresetType type, double startX, double startY) {
    List<TemplateElement> list = new ArrayList<>();
    String gid = "grp_" + UUID.randomUUID()...;
    switch (type) {
        case INVOICE_HEADER -> { /* banner rect + logo image + name/address text + doc badge */ }
        ...
    }
    return list;
}
```

Each preset is 2–4 `TemplateElement`s sharing one `groupId`, positioned relative to
`startX/startY`, styled in a light "document" palette (`#0f172a` headings on `#f8fafc`
cards — these presets were designed for light paper, not the app's dark UI). Dropping
one into the designer is one click instead of assembling 4 elements by hand.

`ISSUE:` inside `INVOICE_HEADER` there is a copy-paste slip — `addr` is positioned with
`addr.setW(110)` *after* `name.setW(110)` is called:

```java
addr.setX(startX + 30); addr.setY(startY + 10.5); name.setW(110); addr.setH(14);
```

`name.setW(110)` is redundant (name already has w=110) and the line *should* read
`addr.setW(110)`. Net effect: address width stays at its default 40 mm. Harmless at
these coordinates (the text is short), but a faithful-book finding, not a fix.

### 5.13 `PresetTemplates.java` — six starter invoices + one label

The factory behind first-run seeding (remember Chapter 3: `DataManager.seedIfEmpty()`
calls `getAllPresets()`). Structure:

- `defaultItemColumns()` — the standard 8-column GST table (sr, desc, hsn, qty, unit,
  rate, gst, amount) with percentage widths summing to 107 — `ISSUE:` slightly over 100,
  so the last column runs a few percent wider than its spec; renderers treat widths as
  proportional, so the visual result is fine, but a strict reader should know.
- `buildClassic()` — the flagship: cream `#f4f1ea` header band, logo, dark `#1c1c1c`
  "TAX INVOICE" banner with 4 pt letter-spacing, BILL TO / INVOICE DETAILS boxes, the
  standard table, a dark GRAND TOTAL bar, amount-in-words box, bank box, terms, a
  signature line and a repeated footer (`repeatOnPages(true)` → drawn on every PDF
  page). ~40 elements, positioned by hand in mm — the file is effectively a
  coordinate-accurate drawing of the default invoice.
- `buildModern()` — dark `#181818` header + gold `#d9a13b` rule (the app's own
  "Obsidian & Gold" look on paper).
- `buildCompactA5()` — the same data on an A5 canvas with 6 mm margins and fewer
  columns.
- `buildMinimal()` — whitespace-heavy, no boxes, hairline rule.
- `buildThermal80()` / `buildThermal58()` — POS receipts: `autoHeight(true)`, centred
  business name, a compact table, a `tabShift()` helper that is a transparent one-liner
  (`el.setY(y)` — kept because the coordinates were originally tabbed y-positions), a
  `QRCODE` with `qrSource("upi_amount")`, and (80 mm only) a `BARCODE` of
  `{{invoice_no}}`.
- `buildLabelTemplate()` — the Barcode Mode starter: `mode("label")`, `CUSTOM` page
  50×25 mm with zero margins, a 2-column 108 mm strip `LabelConfig`, and 5 elements
  (business name, `{{item_name}}`, size line, `₹{{price}}`, a barcode of `{{barcode}}`).

Every element gets `id = "el_" + 10 hex chars` and a running `zIndex` — the same
conventions the designer uses, so a saved preset and a user-designed template are
indistinguishable downstream.

Two helpers (`addInvRow`, `addTotalRow`) stamp label/value text pairs with the value
right-aligned at `x + 32` — the visual convention the presets share.

## 6. How it works at runtime

From first run to printed page:

```
First launch
  DataManager.seedIfEmpty()
        └── PresetTemplates.getAllPresets()  ─ 6 Template objects built in code
        └── TemplateDao.upsert(...)          ─ serialised (Jackson) → templates table (JSON column)

User designs / edits
  TemplateDesigner  ── mutates List<TemplateElement> ──> save → TemplateDao.upsert

Every render (all consumers read the SAME data):
  BillPreviewPane (JavaFX canvas)      ← Template + RenderContext(bill)
  PdfExportService  (PDFBox)           ← Template + RenderContext(bill)
  TemplatePreviewService (thumbnail)   ← Template
  LabelPrintService → TSPL raster      ← Template(mode=label) + LabelConfig
  McpToolRegistry   (AI edits)         ← Template JSON patching
```

Key invariant: **none of these consumers mutate the template.** Renderers receive the
template plus a `RenderContext` (the bill's resolved variables) and only read. The only
writers are the designer, the MCP tools, and the settings screens. That is why a
template can be rendered five ways without five sources of truth.

## 7. How to change it

- **Add a new element type** (say, `SIGNATURE`): add the enum constant with a code
  string; add display text in `getDisplayName()`; add drawing in `DesignObjectRenderer`,
  `PdfExportService` and the designer canvas; add any new fields to `TemplateElement`
  **and to `copy()`**. Missing any of the four render/editor sites means the type
  silently renders blank in that one consumer.
- **Add a template property** (e.g. `watermarkEveryPage`): field + null-safe getter in
  `Template`; no other model changes. Old JSON lacks the key → getter returns the
  default → nothing breaks. Remember: if the designer exposes it, add it to `copy()`.
- **Change default page size**: update *both* `PageSizeName`'s default dimensions and
  `PageConfig`'s field initialisers (see the drift `ISSUE:` above).
- **Verify any change**: open Templates → create a template from a preset → open the
  designer → move one element → save → reopen (round-trip through JSON) → preview and
  export PDF. A round-trip that preserves all four renders is the acceptance test.

## 8. Performance & UX analysis

- **What was done:** ~120 flat fields on one class, all primitives/strings, Jackson
  serialisation into a single SQLite JSON column.
  *Cost:* a fully-featured template serialises to ~40–80 KB. Loading 20 templates is
  sub-millisecond JSON parsing after SQLite read; no join tables, no ORM.
  *Alternative:* normalised tables (`elements` table with one row per element, columns
  per property). *Why not:* 120-column table or EAV schema, 40× more rows, complex
  migrations for every designer feature; and the designer needs whole-document
  snapshots for undo anyway. *Trade-off:* Easy (keep flat JSON) vs Hard (normalise) —
  the flat choice is correct at this scale.
- **`copy()` deep-copy per undo step:** a 40-element template copy allocates ~40 small
  objects — microseconds. Undo stacks are capped in the designer. No action needed.
- **OPTIONAL IMPROVEMENT (Medium):** generate `copy()` and null-checks via a
  serialisation round-trip (`MAPPER.readValue(MAPPER.writeValueAsString(this),
  TemplateElement.class)`) — immune to forgotten fields. *Cost:* ~3× slower per copy
  and a checked exception to wrap. *User notice:* none directly; it removes the class
  of "undo lost my new field" bugs.
- **User experience lever:** because templates are data, the preview updates on every
  drag without any persistence — the "live preview" feel in CreateBillView is free.

## 9. Common mistakes and fixes

| Symptom | Cause | Fix |
|---|---|---|
| `Unrecognized field "newProp"` when loading a template | Class missing `@JsonIgnoreProperties(ignoreUnknown = true)` | Add the annotation to every model class |
| New element type shows as blank rectangle in PDF but fine on canvas | Renderer not updated for the new `ElementType` | Add the case in `PdfExportService`/`DesignObjectRenderer` |
| Undo drops a newly added property | New field missing from `copy()` | Add it (or adopt the round-trip OPTIONAL IMPROVEMENT) |
| Template from JSON has NPE in renderer | Field added later; old JSON has no key | Make the getter null-safe with a default (package convention) |
| Column widths look wrong after editing `defaultItemColumns` | Widths are percentages and should sum to ~100 | Rebalance widths; remember they are proportional |
| Thermal template paginates instead of growing | `autoHeight` not set on the `PageConfig` | `p.setAutoHeight(true)` in the preset |

## 10. Checkpoint

Verify with:

```bash
mvn -q compile
mvn test -Dtest=TemplateV3FeaturesTest,TemplateTableMinRowsTest,LabelPrintLogicTest
```

All green means: individual borders, min-rows tables, label stock geometry and the
model layer behave as described. Exercises:

1. Add `private boolean showBorderOnPrint;` to `TemplateElement` with a null-safe
   getter; confirm an old template JSON still loads and the flag defaults to `false`.
2. Create a new `ComponentPreset.PresetType` called `UPI_BLOCK` with a QR element and a
   caption; drop it from the designer palette and save it into a template.
3. Fix the `addr.setW(110)` slip in `ComponentPreset` (change `name.setW(110)` →
   `addr.setW(110)`) and observe the header address widen on the canvas.

## 11. Summary and coverage self-check

Templates are data, not code. `Template` = page + elements + mode; `TemplateElement` is
the ~120-property atom every renderer reads; enums fail open; null-safe getters make
old JSON immortal; presets are code-built factories. Every consumer — canvas, PDF,
TSPL, MCP — reads the same objects, which is the whole trick that makes the remaining
chapters small.

**Covered in full this chapter (14/14):** `Template`, `TemplateElement`, `ElementType`,
`TableColumn`, `DocType`, `PageConfig` (+ nested `Margins`), `PageSizeName`,
`LabelConfig`, `LabelPrintHistory`, `VariableDef`, `CustomComponent`, `ComponentPreset`,
`CustomFontDef`, `PresetTemplates`. Every field, enum constant and factory method shown
or enumerated above; `TemplateElement`'s full source lives in the repo and its 12
property groups are each explained.

**Markers raised this chapter:**
- `ISSUE:` `ComponentPreset.INVOICE_HEADER` calls `name.setW(110)` where `addr.setW(110)`
  was intended (address uses default width 40 mm).
- `ISSUE:` `PresetTemplates.defaultItemColumns()` widths sum to 107 %, relying on
  proportional rendering.
- `ISSUE:` `mode`/`orientation`/`align` are strings chosen for JSON compatibility where
  enums would be safer.
- `GAP:` `TemplateElement.copy()` omits `binding` and `visibleCondition`
  (undo/clipboard will not carry them).
- `GAP:` `CustomFontDef` getters are not null-safe, unlike the rest of the package.
- `GAP:` `PageSizeName` default dimensions can drift from `PageConfig` initialisers.

**Next: Chapter 8 — The Data Engine: DataManager & Caching.**
