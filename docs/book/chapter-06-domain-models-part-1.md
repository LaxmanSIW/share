# Chapter 6 — The Language of the Business: Domain Models, Part 1

> **Part 3 of InvoiceStudio: Zero to Finished Product**
> Files covered in full this chapter (19): `model/Bill.java`, `BillItem.java`,
> `BillPayment.java`, `BillStatus.java`, `BillTotals.java`,
> `BusinessProfile.java`, `Settings.java`, `Buyer.java`, `BuyerFieldDef.java`,
> `Supplier.java`, `ItemRecord.java`, `ItemCategory.java`, `Transport.java`,
> `Expense.java`, `ExpenseAccount.java`, `PurchaseBill.java`,
> `Transaction.java`, `PaymentMethod.java`, `RepeatCadence.java` — every one
> read from the repository and reproduced here.
> Goal at the end: you can read any model class in the app like a sentence,
> and design a new one that survives old JSON files, nulls, and renames.

---

## 1. Chapter goal

Chapters 3–5 taught the app to *store* records. This chapter defines *what a
record is*. By the end you will be able to:

- explain what a **domain model** is and why these classes contain almost no
  logic — yet are the most load-bearing files in the app;
- read the **JavaBean convention** (getters/setters) and know exactly why
  Jackson demands it;
- use the three defensive idioms every model here repeats:
  `@JsonIgnoreProperties(ignoreUnknown = true)`, null-safe getters with
  defaults, and enum `@JsonValue`/`@JsonCreator` round-tripping;
- understand the **GST vocabulary** the models encode (GSTIN, state codes,
  CGST/SGST vs IGST, ITC) — the domain knowledge the whole app rests on;
- spot the two computed-property patterns (`getEffectiveStateCode`,
  `getAmount`) and the one genuinely misleading constructor
  (`BillTotals`' dropped `dueAmount`) — flagged as an `ISSUE:`.

---

## 2. Story intro

Every office has two kinds of paper: the **forms** and the **cabinets**. The
DAOs of Chapters 4–5 are the cabinets — they know how to file and retrieve.
The models are the forms themselves: *a sale invoice has these boxes; a
customer record has those*. Change the form and every cabinet, clerk, and
counter feels it — which is why this chapter spends its energy on making the
forms *self-defending*.

Three real-world forces shaped every class you're about to read:

1. **Files outlive code.** A bill saved in January is JSON on disk in
   September, when the Java class has gained new fields. `@JsonIgnoreProperties(ignoreUnknown = true)`
   is the peace treaty: old files load into newer classes without complaint.
2. **Data is typed by humans.** The clerk typos a GSTIN, a date arrives
   blank, a JSON file from a backup lacks a field the class now expects.
   Every getter here answers with a *sane default* (`""`, `0`, the enum's
   fallback) instead of a NullPointerException three screens later.
3. **The tax law is the real spec.** India's GST system decides most field
   names: GSTIN (the 15-character registration number whose first two digits
   *are* the state code), CGST+SGST for within-state sales, IGST for
   cross-state, ITC for the credit on purchases. The models are that law
   wearing Java syntax.

One more habit to notice throughout: these classes are almost logic-free —
a handful of tiny computed helpers at most. That is deliberate. The
*thinking* lives in services (Chapters 12–14); the models are the shared
vocabulary both sides of every conversation (UI, DAOs, MCP tools, renderer)
must agree on. Simple vocabulary, rich conversation.

---

## 3. Concepts first

### 3.1 What a domain model (POJO) is

A **POJO** — *Plain Old Java Object* — is a class that holds data and offers
accessors, with no framework magic inside. The convention it follows is
**JavaBean**: private fields, public no-argument constructor, and
`getX()`/`setX(value)` pairs for each field. Two reasons the pattern is
non-negotiable here:

- **Jackson maps by convention.** When `BillDao` runs
  `mapper.readValue(json, Bill.class)`, Jackson walks the JSON keys and
  finds the matching setters (`"billNo"` → `setBillNo`). Writing
  `writeValueAsString(bill)` walks the getters. No mapping config needed —
  the naming *is* the mapping.
- **Every consumer speaks it.** The bill editor writes `bill.setTotals(...)`,
  the renderer reads `bill.getVariables()`, the MCP tools read
  `bill.getStatus()`. One vocabulary, everywhere.

### 3.2 `@JsonIgnoreProperties(ignoreUnknown = true)` — the forward-compatibility seal

When Jackson meets a JSON key with no matching property, it throws
`UnrecognizedPropertyException` — *unless* the class is annotated with
`ignoreUnknown = true`. Every persisted model in this app carries the
annotation. It means: add a field to `Settings` today, and yesterday's
`settings.json` (and every database `json_data` row) still loads — the new
field simply takes its default. Remove the annotation and the first schema
evolution breaks every existing customer. This single annotation is why the
JSON-blob storage of Chapter 3 could evolve through v4.1→v4.6 without a
single data migration of stored JSON.

### 3.3 Enums that survive their own renames — `@JsonValue` / `@JsonCreator`

An **enum** is a type with a fixed set of instances (`UNPAID`, `PAID`,
`CANCELLED`). Persisted naively, an enum stores its Java constant *name* —
rename the constant and old files stop loading. The app's enums therefore
publish a **stable code** instead:

- `@JsonValue` on a getter tells Jackson "when writing, store this value"
  (`BillStatus` stores `"unpaid"`, never `"UNPAID"`);
- `@JsonCreator` on a static factory tells Jackson "when reading, use this
  method to decide" — and the factory is *forgiving*: it matches by code or
  name, case-insensitively, and **falls back to a safe default**
  (`UNPAID`, `NONE`, `CASH`) for anything unrecognised.

A corrupted or hand-edited file can never crash the loader; it degrades to
the least-dangerous state. Notice the deliberate default: an unknown
*status* becomes UNPAID (money still chased), an unknown *cadence* becomes
NONE (no surprise repeats), an unknown *payment method* becomes CASH.

### 3.4 Computed properties — answers that must never be stored wrong

Several "fields" are actually methods: `BillItem.getAmount()`,
`Buyer.getEffectiveStateCode()`, `PurchaseBill.getAmountPayable()`. They
read like properties but *derive* their value on every call from the real
fields. The rule this chapter follows: **store facts, compute opinions.**
Quantity and rate are facts (stored); line amount is an opinion derived
from them (computed) — and can therefore never disagree with them.

### 3.5 The GST field guide (the domain in six bullets)

- **GSTIN** — 15 characters: `27` (state code) + `ABCDE1234F` (PAN) + `1`
  (entity index) + `Z` + `5` (checksum). The models exploit this structure:
  `getEffectiveStateCode()` reads the first two digits;
  `Supplier.getEffectivePan()` slices characters 3–12.
- **State code** — two digits (`27` = Maharashtra). GST tax *routing*
  depends on comparing seller's and buyer's state codes.
- **CGST + SGST** — within one state, the tax splits half-and-half between
  the Central and State governments. **IGST** — between states, one
  integrated tax. `BillTotals` carries all three; `Settings.interState`
  decides which pair the bill editor computes.
- **ITC** (*Input Tax Credit*) — GST paid on purchases, reclaimable against
  GST collected on sales; `PurchaseBillDao` projects it (Chapter 5).
- **HSN** (*Harmonised System of Nomenclature*) — the commodity code on
  every line item (`ItemRecord.hsn`, `BillItem.hsn`).
- **Direct vs indirect expenses** — accounting's split of costs that belong
  to production (freight in, wages, power) versus overhead (rent, salaries);
  `Expense.CATEGORIES` hard-codes the chart with the first four marked
  direct.

### 3.6 Why some fields are duplicated as accessors (`getGstin`/`getGst`)

`Buyer` stores one field `gst` but offers `getGstin()`/`setGstin()` too.
These are *aliases*, added because different parts of the codebase (and
different eras of it) use different vocabulary. The alias costs three lines
and prevents a rename-refactor wildfire. `BillItem` does the same with
`getQuantity`/`getQty`, `Transaction` with `getBillNumber`/`getBillNo` and
`getParcels`/`getParcel`.

---

## 4. Files in this chapter

| File | Kind | Purpose | Lines |
|---|---|---|---|
| `model/Bill.java` | Document | Sales invoice aggregate (header) | 120 |
| `model/BillItem.java` | Value | One invoice line | 93 |
| `model/BillPayment.java` | Value | One payment against a bill | 42 |
| `model/BillStatus.java` | Enum | unpaid / paid / cancelled + badge styles | 38 |
| `model/BillTotals.java` | Value | GST computation snapshot | 58 |
| `model/BusinessProfile.java` | Master | The seller's identity (who bills) | 61 |
| `model/Settings.java` | Master | App-wide preferences + numbering | 79 |
| `model/Buyer.java` | Master | Customer record | 123 |
| `model/BuyerFieldDef.java` | Meta | Configurable buyer-field definitions | 27 |
| `model/Supplier.java` | Master | Vendor record (Sundry Creditor) | 132 |
| `model/ItemRecord.java` | Master | Stock item | 84 |
| `model/ItemCategory.java` | Master | Item category | 44 |
| `model/Transport.java` | Master | Transport party | 60 |
| `model/Expense.java` | Document | Expense voucher + chart of accounts | 81 |
| `model/ExpenseAccount.java` | Master | Payee registry entry | 50 |
| `model/PurchaseBill.java` | Document | Purchase invoice (inward supply) | 113 |
| `model/Transaction.java` | Document | Cash/bank book entry | 142 |
| `model/PaymentMethod.java` | Enum | Cash/UPI/Bank/Cheque/Card/Other | 34 |
| `model/RepeatCadence.java` | Enum | none/weekly/monthly/yearly | 36 |

---

## 5. Step-by-step build

### Step 1 — `model/Bill.java` (complete file)

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Bill {
    private String id;
    private String billNo;
    private String date; // yyyy-MM-dd
    private String templateId;
    private String templateName;
    private Map<String, String> variables = new HashMap<>();
    private List<BillItem> items = new ArrayList<>();
    private double discountPct;
    private BillTotals totals = new BillTotals();
    private String amountInWords;
    private String notes = "";
    private int printCount;
    private BillStatus status = BillStatus.UNPAID;
    private String paidAt;
    private List<BillPayment> payments = new ArrayList<>();
    private DocType docType = DocType.INVOICE;
    private RepeatCadence repeat = RepeatCadence.NONE;
    private String repeatEndDate;
    private boolean repeatSkipNext;
    private int parcel = 1;
    private String transactionId = "";
    private String createdAt;
    private String updatedAt;

    public Bill() {}
```

- The field list *is* the invoice's anatomy: identity (`id`, `billNo`,
  `date`), presentation (`templateId`/`templateName` — which design printed
  it), the **`variables` map** (the merge-field bag: `buyer_name`, `po_no`,
  transport fields — everything the template system fills; Chapter 3's
  built-ins live here per bill), the body (`items`, `discountPct`,
  `totals`), money state (`status`, `payments`, `paidAt`), the recurring
  engine's controls (`repeat`, `repeatEndDate`, `repeatSkipNext`), and
  logistics (`parcel`).
- **Every collection and nested object is initialised at declaration**
  (`new HashMap<>()`, `new ArrayList<>()`, `new BillTotals()`). A brand-new
  `Bill` is immediately usable — `bill.getItems().add(...)` cannot NPE.
  This "always-valid object" discipline is what lets the editor and the
  preview renderer read any bill state without guards.
- `date` is an ISO string (`yyyy-MM-dd` per the comment), not a
  `LocalDate` — the JSON-blob storage prefers strings that sort correctly
  and survive Jackson without a type module (the module *is* loaded —
  Chapter 1's `jsr310` — but strings keep the format visible in every
  database browser).
- The unused `JsonProperty` import is a leftover from an earlier
  explicit-mapping revision — `GAP:` harmless, flagged for the optional
  clean-up list.

```java
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBillNo() { return billNo; }
    public void setBillNo(String billNo) { this.billNo = billNo; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public String getTemplateName() { return templateName; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public Map<String, String> getVariables() { return variables; }
    public void setVariables(Map<String, String> variables) { this.variables = variables != null ? variables : new HashMap<>(); }

    public List<BillItem> getItems() { return items; }
    public void setItems(List<BillItem> items) { this.items = items != null ? items : new ArrayList<>(); }

    public double getDiscountPct() { return discountPct; }
    public void setDiscountPct(double discountPct) { this.discountPct = discountPct; }

    public BillTotals getTotals() { return totals; }
    public void setTotals(BillTotals totals) { this.totals = totals != null ? totals : new BillTotals(); }
```

- The setters guard against `null` being injected: a JSON file containing
  `"items": null` would otherwise replace the always-valid empty list with
  null and detonate the first `.add()`. Every *collection/object* setter in
  the app follows this `!= null ? value : fresh-empty` shape.

```java
    public BillStatus getStatus() { return status != null ? status : BillStatus.UNPAID; }
    public void setStatus(BillStatus status) { this.status = status; }

    public String getPaidAt() { return paidAt; }
    public void setPaidAt(String paidAt) { this.paidAt = paidAt; }

    public List<BillPayment> getPayments() { return payments; }
    public void setPayments(List<BillPayment> payments) { this.payments = payments != null ? payments : new ArrayList<>(); }

    public DocType getDocType() { return docType != null ? docType : DocType.INVOICE; }
    public void setDocType(DocType docType) { this.docType = docType; }

    public RepeatCadence getRepeat() { return repeat != null ? repeat : RepeatCadence.NONE; }
    public void setRepeat(RepeatCadence repeat) { this.repeat = repeat; }
```

- The **enum getters are null-safe too**: a JSON `"status": null` (or a
  field added later than the file) reads back as the enum's least-dangerous
  member. Note the pattern: *setters trust, getters defend* — the class
  never stores a wrong value, and never lies about what was stored.

```java
    public String getBuyerName() {
        return variables != null ? variables.getOrDefault("buyer_name", "") : "";
    }
    public void setBuyerName(String buyerName) {
        if (variables == null) variables = new HashMap<>();
        variables.put("buyer_name", buyerName);
    }

    public int getParcel() { return parcel > 0 ? parcel : 1; }
    public int getParcels() { return getParcel(); }
    public void setParcel(int parcel) {
        this.parcel = parcel > 0 ? parcel : 1;
        if (variables == null) variables = new HashMap<>();
        variables.put("parcel", String.valueOf(this.parcel));
        variables.put("parcels", String.valueOf(this.parcel));
    }

    public String getTransactionId() { return transactionId != null ? transactionId : ""; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
}
```

- **`getBuyerName`/`setBuyerName` are a window onto the variables map**, not
  a separate field — buyer name lives *once* (in `variables`), so the
  register projection in `BillDao`, the renderer's `{{buyer_name}}`, and
  this convenience accessor can never disagree.
- **`setParcel` is a two-way sync**: the typed field (for code) and the two
  string variables (`parcel`, `parcels` — both spellings exist because both
  merge-fields are offered to templates, Chapter 3's list) update together.
  A template printing `{{parcels}}` and a report reading `getParcel()`
  share one truth. The `> 0` guard makes zero/negative parcels impossible —
  a bill always shipped *something*.

### Step 2 — `model/BillItem.java` (complete file)

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BillItem {
    private String id;
    private String desc = "";
    private String hsn = "";
    private double qty;
    private String unit = "PCS";
    private double rate;
    private double gst = 18.0;
    private double discPct;
    private java.util.Map<String, String> custom = new java.util.HashMap<>();

    public BillItem() {}

    public BillItem(String id, String desc, String hsn, double qty, String unit, double rate, double gst, double discPct) {
        this.id = id;
        this.desc = desc;
        this.hsn = hsn;
        this.qty = qty;
        this.unit = unit;
        this.rate = rate;
        this.gst = gst;
        this.discPct = discPct;
    }
```

- Defaults encode the trade: `unit = "PCS"`, `gst = 18.0` (the standard GST
  slab), `desc = ""`. The all-args constructor is what the bill editor's
  "add item" builds from a picked `ItemRecord` — item master flows into
  line item in one call.

```java
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDesc() { return desc; }
    public void setDesc(String desc) { this.desc = desc; }

    public String getDescription() { return desc != null ? desc : ""; }
    public void setDescription(String description) { this.desc = description; }

    public String getHsn() { return hsn; }
    public void setHsn(String hsn) { this.hsn = hsn; }

    public double getQty() { return qty; }
    public void setQty(double qty) { this.qty = qty; }

    public double getQuantity() { return qty; }
    public void setQuantity(double quantity) { this.qty = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public double getRate() { return rate; }
    public void setRate(double rate) { this.rate = rate; }

    public double getGst() { return gst; }
    public void setGst(double gst) { this.gst = gst; }

    public double getDiscPct() { return discPct; }
    public void setDiscPct(double discPct) { this.discPct = discPct; }
```

- **Two alias pairs** (`getDesc`/`getDescription`, `getQty`/`getQuantity`) —
  §3.6's tolerance pattern. Jackson tolerates them harmlessly (it maps
  `desc` by the primary pair; the alias is just another way in for code).

```java
    public java.util.Map<String, String> getCustom() {
        if (custom == null) custom = new java.util.HashMap<>();
        return custom;
    }

    public void setCustom(java.util.Map<String, String> custom) {
        this.custom = custom != null ? custom : new java.util.HashMap<>();
    }

    public String getCustomField(String key) {
        if (key == null || custom == null) return "";
        return custom.getOrDefault(key, "");
    }

    public void setCustomField(String key, String value) {
        if (key == null) return;
        if (custom == null) custom = new java.util.HashMap<>();
        if (value == null || value.isBlank()) {
            custom.remove(key);
        } else {
            custom.put(key, value);
        }
    }
```

- **The custom-field bag** — per-line extra columns driven by the user's
  table-scope variables (a "Batch No" column, say). `setCustomField` *erases*
  on blank rather than storing `""` — empty fields don't accumulate in the
  JSON, keeping stored documents lean.

```java
    public double getGross() {
        return qty * rate;
    }

    public double getAmount() {
        double gross = getGross();
        double d = Math.max(0, Math.min(100, discPct));
        return Math.round((gross - gross * (d / 100.0)) * 100.0) / 100.0;
    }
}
```

- **The two computed properties (§3.4).** `getGross()` is the pre-discount
  value. `getAmount()` clamps the discount into `[0, 100]` (a typo'd `150`
  or `-5` cannot invert or exaggerate the math), applies it, and **rounds
  to two decimals** — the paise-level rounding that keeps a printed invoice
  total matching the sum of its lines. The rounding idiom
  `Math.round(x * 100.0) / 100.0` is half-up to 2 dp; it appears here once
  because `BillingService` (Chapter 12) performs the bill-level totals with
  the same discipline — line amounts and bill totals *agree by construction*.

### Step 3 — `model/BillPayment.java`, `model/BillStatus.java`, `model/BillTotals.java`

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BillPayment {
    private String id;
    private String date; // yyyy-MM-dd
    private double amount;
    private PaymentMethod method = PaymentMethod.CASH;
    private String reference = "";
    private String note = "";

    public BillPayment() {}

    public BillPayment(String id, String date, double amount, PaymentMethod method, String reference, String note) {
        this.id = id;
        this.date = date;
        this.amount = amount;
        this.method = method;
        this.reference = reference;
        this.note = note;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public PaymentMethod getMethod() { return method != null ? method : PaymentMethod.CASH; }
    public void setMethod(PaymentMethod method) { this.method = method; }

    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
```

- One payment row against a bill: *how much, when, how, with what proof*
  (`reference` = UTR / cheque number). Partial payments are simply several
  `BillPayment`s whose sum `BillDao` projects into `due_amount` (Chapter 5).
  A bill can be paid over three months and each instalment keeps its own
  date and method — the audit trail lives inside the document.

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BillStatus {
    UNPAID("unpaid", "Unpaid", "badge-warning"),
    PAID("paid", "Paid", "badge-success"),
    CANCELLED("cancelled", "Cancelled", "badge-error");

    private final String code;
    private final String label;
    private final String styleClass;

    BillStatus(String code, String label, String styleClass) {
        this.code = code;
        this.label = label;
        this.styleClass = styleClass;
    }

    @JsonValue
    public String getCode() { return code; }

    public String getLabel() { return label; }
    public String getStyleClass() { return styleClass; }

    @JsonCreator
    public static BillStatus fromString(String val) {
        if (val == null) return UNPAID;
        for (BillStatus s : values()) {
            if (s.code.equalsIgnoreCase(val) || s.name().equalsIgnoreCase(val)) return s;
        }
        return UNPAID;
    }

    @Override
    public String toString() { return label; }
}
```

- The §3.3 pattern, in full: three faces per constant — **code** (stored,
  lowercase-stable), **label** (shown to humans), **styleClass** (the CSS
  badge class the history rows use: warning-gold, success-green, error-red —
  the theme classes from the stylesheet, Chapter 9). `fromString` accepts
  either spelling and defaults to UNPAID.
- `toString()` returning the label is what makes the enum drop neatly into
  JavaFX combo boxes without a custom cell factory.

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BillTotals {
    private double subtotal;
    private double discount;
    private double taxable;
    private double cgst;
    private double sgst;
    private double igst;
    private double roundOff;
    private double grandTotal;
    private double totalQty;
    private int itemCount;

    public BillTotals() {}

    public BillTotals(double subtotal, double cgst, double sgst, double igst, double grandTotal, double roundOff, double dueAmount) {
        this.subtotal = subtotal;
        this.cgst = cgst;
        this.sgst = sgst;
        this.igst = igst;
        this.grandTotal = grandTotal;
        this.roundOff = roundOff;
    }

    public double getSubtotal() { return subtotal; }
    public void setSubtotal(double subtotal) { this.subtotal = subtotal; }

    public double getDiscount() { return discount; }
    public void setDiscount(double discount) { this.discount = discount; }

    public double getTaxable() { return taxable; }
    public void setTaxable(double taxable) { this.taxable = taxable; }

    public double getCgst() { return cgst; }
    public void setCgst(double cgst) { this.cgst = cgst; }

    public double getSgst() { return sgst; }
    public void setSgst(double sgst) { this.sgst = sgst; }

    public double getIgst() { return igst; }
    public void setIgst(double igst) { this.igst = igst; }

    public double getRoundOff() { return roundOff; }
    public void setRoundOff(double roundOff) { this.roundOff = roundOff; }

    public double getGrandTotal() { return grandTotal; }
    public void setGrandTotal(double grandTotal) { this.grandTotal = grandTotal; }

    public double getTotalQty() { return totalQty; }
    public void setTotalQty(double totalQty) { this.totalQty = totalQty; }

    public int getItemCount() { return itemCount; }
    public void setItemCount(int itemCount) { this.itemCount = itemCount; }
}
```

- The **GST computation snapshot** the renderer prints in the invoice's
  totals box and the reports aggregate. Field order mirrors the printed
  layout: subtotal → discount → taxable → the three taxes → round-off →
  grand.
- **ISSUE (real, verified): the 7-arg constructor accepts `dueAmount` and
  silently discards it** — there is no `dueAmount` field on this class
  (Chapter 5's `BillDao` *derives* due from grand − payments instead).
  Callers — including Chapter 3's own `DatabaseTest`
  (`new BillTotals(1000, 90, 90, 0, 1180, 0, 1180)`) — naturally assume the
  last argument lands somewhere. It doesn't. It is harmless at runtime
  (due is computed, never stored, by design) but the signature *lies*. The
  faithful build keeps it; §8 offers the cleanup. Lesson for your own
  models: **a constructor parameter that is ignored is a bug wearing a
  suit** — either store it or remove it.
- `totalQty`/`itemCount` ride along so the invoice's summary rows
  ("Total quantity: 240 · 6 items") come from the same snapshot as the
  money — one object, one consistency story.

### Step 4 — `model/BusinessProfile.java` and `model/Settings.java`

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BusinessProfile {
    private String name = "SHREE TRADERS";
    private String address = "12, Industrial Estate, MG Road\nMumbai – 400001, Maharashtra";
    private String gstin = "27ABCDE1234F1Z5";
    private String phone = "+91 98200 12345";
    private String email = "accounts@shreetraders.in";
    private String state = "Maharashtra";
    private String stateCode = "27";
    private String logo = ""; // data URL or file path
    private String bankName = "HDFC Bank, MG Road Branch";
    private String accountNo = "50200012345678";
    private String ifsc = "HDFC0000123";
    private String upi = "shreetraders@hdfcbank";
    private String terms = "1. Goods once sold will not be taken back.\n2. Interest @18% p.a. will be charged on overdue payments.\n3. Subject to Mumbai jurisdiction only.";

    public BusinessProfile() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }

    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }

    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getAccountNo() { return accountNo; }
    public void setAccountNo(String accountNo) { this.accountNo = accountNo; }

    public String getIfsc() { return ifsc; }
    public void setIfsc(String ifsc) { this.ifsc = ifsc; }

    public String getUpi() { return upi; }
    public void setUpi(String upi) { this.upi = upi; }

    public String getTerms() { return terms; }
    public void setTerms(String terms) { this.terms = terms; }
}
```

- **The seller's identity card** — everything the invoice header and footer
  print: firm name/address/GSTIN/phone/email, the state pair (which drives
  tax routing against the buyer's code), the logo (a **data URL** — the
  image bytes inlined as text — or a file path), bank details for the
  payment slip, and the UPI id that Chapter 16 turns into the scannable
  payment QR.
- **The defaults are the demo identity** ("SHREE TRADERS", the Mumbai
  address) — the exact strings Chapter 8's first-run seeding writes and
  Chapter 3's unused `seed/settings.json` carries. A brand-new install
  therefore prints a *plausible* invoice before the owner types a thing —
  deliberately: the demo must look real, and the Settings screen's job is
  to replace it. (`ISSUE:` borderline — demo data as code defaults is a
  product decision, not an accident; noted for transparency.)

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Settings {
    private BusinessProfile business = new BusinessProfile();
    private String currency = "₹";
    private boolean interState; // true -> IGST, false -> CGST+SGST
    private String billNoPrefix = "INV-";
    private int billNoNext = 1;
    private int billNoDigits = 4; // 0/1: no padding, 3: 001, 4: 0001
    private boolean monochromePrint = false; // B&W Xerox Print Mode
    private double printOffsetX; // mm
    private double printOffsetY; // mm
    private boolean statusStamp = true;
    private boolean autoRecurring = false;

    /**
     * Thermal label (TSC/TSPL) brightness threshold, 0–255. Downsampled dot
     * grays ≤ threshold burn sharp black, everything above stays sharp white
     * — the only two colors a thermal head can produce. Default 150 (slightly
     * above mid-gray so hairlines and small text survive). Keep in sync with
     * {@code MonoImage.DEFAULT_THRESHOLD} (kept as a literal to avoid a
     * model→service dependency).
     */
    private int barcodeThreshold = 150;

    private List<BuyerFieldDef> buyerFields = new ArrayList<>();
    private List<CustomFontDef> customFonts = new ArrayList<>();

    public Settings() {}

    public BusinessProfile getBusiness() { return business != null ? business : new BusinessProfile(); }
    public void setBusiness(BusinessProfile business) { this.business = business; }

    public String getCurrency() { return currency != null ? currency : "₹"; }
    public void setCurrency(String currency) { this.currency = currency; }

    public boolean isInterState() { return interState; }
    public void setInterState(boolean interState) { this.interState = interState; }

    public String getBillNoPrefix() { return billNoPrefix != null ? billNoPrefix : ""; }
    public void setBillNoPrefix(String billNoPrefix) { this.billNoPrefix = billNoPrefix != null ? billNoPrefix : ""; }

    public int getBillNoNext() { return billNoNext; }
    public void setBillNoNext(int billNoNext) { this.billNoNext = billNoNext; }

    public int getBillNoDigits() { return billNoDigits > 0 ? billNoDigits : 1; }
    public void setBillNoDigits(int billNoDigits) { this.billNoDigits = Math.max(0, billNoDigits); }

    public boolean isMonochromePrint() { return monochromePrint; }
    public void setMonochromePrint(boolean monochromePrint) { this.monochromePrint = monochromePrint; }

    public double getPrintOffsetX() { return printOffsetX; }
    public void setPrintOffsetX(double printOffsetX) { this.printOffsetX = printOffsetX; }

    public double getPrintOffsetY() { return printOffsetY; }
    public void setPrintOffsetY(double printOffsetY) { this.printOffsetY = printOffsetY; }

    public boolean isStatusStamp() { return statusStamp; }
    public void setStatusStamp(boolean statusStamp) { this.statusStamp = statusStamp; }

    public boolean isAutoRecurring() { return autoRecurring; }
    public void setAutoRecurring(boolean autoRecurring) { this.autoRecurring = autoRecurring; }

    public int getBarcodeThreshold() { return barcodeThreshold; }
    public void setBarcodeThreshold(int barcodeThreshold) {
        this.barcodeThreshold = Math.max(0, Math.min(255, barcodeThreshold));
    }

    public List<BuyerFieldDef> getBuyerFields() { return buyerFields; }
    public void setBuyerFields(List<BuyerFieldDef> buyerFields) { this.buyerFields = buyerFields != null ? buyerFields : new ArrayList<>(); }

    public List<CustomFontDef> getCustomFonts() { return customFonts; }
    public void setCustomFonts(List<CustomFontDef> customFonts) { this.customFonts = customFonts != null ? customFonts : new ArrayList<>(); }
}
```

- **The app's preference sheet**, stored as one JSON row per user
  (Chapter 4's `SettingsDao`). Grouping by consumer:
  - *Billing engine:* `currency`, `interState` (the CGST+SGST vs IGST
    switch), the bill-number triple (prefix / next counter / digit padding —
    `INV-0042` vs `INV-42` is `billNoDigits`' job; the getter clamps to ≥1,
    the setter clamps to ≥0 — belt and braces around the padding math),
    `autoRecurring`.
  - *Printing:* `monochromePrint` (a B&W "xerox mode" that strips colour
    fills for cheap printers), `printOffsetX/Y` in millimetres (nudging the
    print onto pre-printed letterhead), `statusStamp` (the PAID/UNPAID
    rubber-stamp overlay), `barcodeThreshold`.
  - *Customisation:* `buyerFields` (the user-defined buyer screen columns —
    each a `BuyerFieldDef`) and `customFonts` (fonts imported into the
    designer; each a `CustomFontDef`).
- The **threshold Javadoc is a miniature design lesson**: a thermal printer
  burns only black or white, so the renderer's grayscale output must be
  thresholded; 150 sits slightly above mid-gray so hairline text survives.
  The "keep in sync" note documents why the constant is *duplicated* rather
  than imported — a `model → service` dependency is forbidden (models must
  stay dependency-free), and the author chose an honest comment over a
  dishonest import. A real trade-off, written down where the next editor
  will see it.

### Step 5 — `model/Buyer.java` (complete file)

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Buyer {
    private String id;
    private String name = "";
    private String address = "";
    private String gst = "";
    private String phone = "";
    private String state = "";
    private String stateCode = "";
    private String contactPerson = "";
    private String city = "";
    private double creditLimit = 100000.0;
    private int riskScore = 8; // 1-10 risk scale (Low, Medium, High)
    private String defaultTransportId = "";
    private double openingBalance = 0.0;
    private Map<String, String> custom = new HashMap<>();
    private String createdAt;
    private String updatedAt;

    public Buyer() {}

    public Buyer(String id, String name, String address, String gst, String phone, String state) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.gst = gst;
        this.phone = phone;
        this.state = state;
    }

    public Buyer(String id, String name, String address, String gst, String phone, String state, String stateCode) {
        this.id = id;
        this.name = name;
        this.address = address;
        this.gst = gst;
        this.phone = phone;
        this.state = state;
        this.stateCode = stateCode;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() {
        return name != null && !name.isBlank() ? name : "Unnamed Buyer";
    }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getGst() { return gst; }
    public void setGst(String gst) { this.gst = gst; }

    public String getGstin() { return gst; }
    public void setGstin(String gstin) { this.gst = gstin; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getStateCode() { return stateCode != null ? stateCode : ""; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }

    public String getEffectiveStateCode() {
        if (stateCode != null && !stateCode.isBlank()) return stateCode.trim();
        if (gst != null && gst.trim().length() >= 2) {
            String prefix = gst.trim().substring(0, 2);
            if (prefix.matches("\\d{2}")) return prefix;
        }
        return "";
    }

    public Map<String, String> getCustom() { return custom; }
    public void setCustom(Map<String, String> custom) { this.custom = custom != null ? custom : new HashMap<>(); }

    public String getTradeName() {
        return custom != null ? custom.getOrDefault("trade_name", "") : "";
    }

    public String getContactPerson() { return contactPerson != null ? contactPerson : ""; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson != null ? contactPerson : ""; }

    public String getCity() { return city != null ? city : ""; }
    public void setCity(String city) { this.city = city != null ? city : ""; }

    public double getCreditLimit() { return creditLimit; }
    public void setCreditLimit(double creditLimit) { this.creditLimit = creditLimit; }

    public int getRiskScore() { return riskScore; }
    public void setRiskScore(int riskScore) { this.riskScore = riskScore; }

    public String getRiskLevel() {
        if (riskScore <= 3) return "High";
        if (riskScore <= 7) return "Medium";
        return "Low";
    }

    public String getDefaultTransportId() { return defaultTransportId != null ? defaultTransportId : ""; }
    public void setDefaultTransportId(String defaultTransportId) { this.defaultTransportId = defaultTransportId != null ? defaultTransportId : ""; }

    public double getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(double openingBalance) { this.openingBalance = openingBalance; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() { return name; }
}
```

- **`getEffectiveStateCode()` is the tax-routing keystone.** Priority:
  explicit `stateCode` → first two digits of the GSTIN (if they're both
  digits) → `""`. The bill editor compares this against the seller's code
  (`BusinessProfile.stateCode`) to decide CGST+SGST vs IGST — the user
  never touches a tax toggle; the *data* decides. The `\\d{2}` regex guard
  means a malformed GSTIN's first two characters can't masquerade as a code.
- **The CRM fields** (`creditLimit` default ₹1,00,000, `riskScore` with its
  `getRiskLevel` bands — ≤3 High, ≤7 Medium, else Low, so the default 8
  shows "Low" — `defaultTransportId` pre-fills the bill editor's transport
  box, `openingBalance` seeds receivables for books migrated from paper,
  `custom` for user-defined fields, `tradeName` as the legal-vs-trade name
  distinction GST invoices sometimes need).
- Two constructors (with and without state code) and `toString()` returning
  the name for combo boxes — the by-now-familiar family shapes.

### Step 6 — `model/BuyerFieldDef.java` and `model/CustomFontDef.java` (complete files)

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BuyerFieldDef {
    private String key;
    private String label;
    private String type = "text"; // text, number, date

    public BuyerFieldDef() {}

    public BuyerFieldDef(String key, String label, String type) {
        this.key = key;
        this.label = label;
        this.type = type != null ? type : "text";
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
```

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomFontDef {
    private String id;
    private String name;
    private String family;
    private String source = "google"; // google, upload, url
    private String url;
    private String fileUrl;
    private String dataUrl;
    private String format;
    private String category = "sans"; // sans, serif, mono, display, handwriting
    private String createdAt;

    public CustomFontDef() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFamily() { return family; }
    public void setFamily(String family) { this.family = family; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getDataUrl() { return dataUrl; }
    public void setDataUrl(String dataUrl) { this.dataUrl = dataUrl; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
```

- `BuyerFieldDef` is the *schema-in-data* pattern: the user defines extra
  buyer columns at runtime (key/label/type), Settings stores the
  definitions, and `Buyer.custom` stores the values. The Buyers screen and
  CSV export read the definitions to build columns dynamically — no code
  change for a new field.
- `CustomFontDef` describes one imported font whichever way it arrived
  (`source`: downloaded from Google Fonts / uploaded / linked by URL), with
  the binary carried as a `dataUrl` when embedded. The designer (Ch 15)
  resolves the right one at render time. Both are tiny carriers — models
  that exist so `Settings` can hold lists of them.

### Step 7 — `model/Supplier.java` (complete file)

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.HashMap;
import java.util.Map;

/**
 * Supplier / Seller — the party from whom goods or services are purchased
 * (Sundry Creditor in Indian accounting terminology).
 *
 * Persisted as a JSON payload in the {@code suppliers} table via
 * {@link com.invoicestudio.db.SupplierDao}; scalar columns (name, phone, gst,
 * state) are duplicated for fast search indexing, matching the Buyer pattern.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Supplier {
    private String id;
    private String name = "";
    private String contactPerson = "";
    private String phone = "";
    private String email = "";
    private String gst = "";
    private String state = "";
    private String stateCode = "";
    private String city = "";
    private String address = "";
    private String pan = "";
    private String bankName = "";
    private String bankAccountNo = "";
    private String bankIfsc = "";
    /** Opening balance. Positive = payable to seller (Cr); negative = advance paid (Dr). */
    private double openingBalance = 0.0;
    /** Standard credit period this supplier allows (e.g. 30, 45, 60 days). */
    private int creditPeriodDays = 0;
    private Map<String, String> custom = new HashMap<>();
    private String createdAt;
    private String updatedAt;

    public Supplier() {}

    public Supplier(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name != null ? name : ""; }
    public void setName(String name) { this.name = name; }

    public String getDisplayName() {
        return name != null && !name.isBlank() ? name : "Unnamed Supplier";
    }

    public String getContactPerson() { return contactPerson != null ? contactPerson : ""; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getPhone() { return phone != null ? phone : ""; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email != null ? email : ""; }
    public void setEmail(String email) { this.email = email; }

    public String getGst() { return gst != null ? gst : ""; }
    public void setGst(String gst) { this.gst = gst; }

    public String getState() { return state != null ? state : ""; }
    public void setState(String state) { this.state = state; }

    public String getStateCode() { return stateCode != null ? stateCode : ""; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }

    /**
     * Effective 2-digit GST state code: explicit field wins, otherwise derived
     * from the first two digits of the GSTIN.
     */
    public String getEffectiveStateCode() {
        if (stateCode != null && !stateCode.isBlank()) return stateCode.trim();
        if (gst != null && gst.trim().length() >= 2) {
            String prefix = gst.trim().substring(0, 2);
            if (prefix.matches("\\d{2}")) return prefix;
        }
        return "";
    }

    /**
     * PAN derived from GSTIN characters 3-12 (positions 3..12 of the 15-char
     * GSTIN) when the explicit PAN field is empty.
     */
    public String getEffectivePan() {
        if (pan != null && !pan.isBlank()) return pan.trim();
        if (gst != null && gst.trim().length() >= 12) return gst.trim().substring(2, 12);
        return "";
    }

    public String getCity() { return city != null ? city : ""; }
    public void setCity(String city) { this.city = city; }

    public String getAddress() { return address != null ? address : ""; }
    public void setAddress(String address) { this.address = address; }

    public String getPan() { return pan != null ? pan : ""; }
    public void setPan(String pan) { this.pan = pan; }

    public String getBankName() { return bankName != null ? bankName : ""; }
    public void setBankName(String bankName) { this.bankName = bankName; }

    public String getBankAccountNo() { return bankAccountNo != null ? bankAccountNo : ""; }
    public void setBankAccountNo(String bankAccountNo) { this.bankAccountNo = bankAccountNo; }

    public String getBankIfsc() { return bankIfsc != null ? bankIfsc : ""; }
    public void setBankIfsc(String bankIfsc) { this.bankIfsc = bankIfsc; }

    public double getOpeningBalance() { return openingBalance; }
    public void setOpeningBalance(double openingBalance) { this.openingBalance = openingBalance; }

    public int getCreditPeriodDays() { return creditPeriodDays; }
    public void setCreditPeriodDays(int creditPeriodDays) { this.creditPeriodDays = creditPeriodDays; }

    public Map<String, String> getCustom() { return custom; }
    public void setCustom(Map<String, String> custom) { this.custom = custom != null ? custom : new HashMap<>(); }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() { return name; }
}
```

- Buyer's purchasing-side twin, with the purchasing-specific extras:
  **bank details** (paying the supplier), `creditPeriodDays` (the "pay in
  45 days" term that drives payable ageing, Chapter 14), `pan` with its
  **derived accessor** — a GSTIN's characters 3–12 *are* the PAN (§3.5's
  structure), so a supplier recorded with only a GSTIN still yields a PAN.
  `openingBalance`'s sign convention is documented on the field:
  positive = you owe them (Credit), negative = advance paid (Debit) —
  accounting's Dr/Cr polarity in one comment.

### Step 8 — `model/ItemRecord.java`, `model/ItemCategory.java`, `model/Transport.java`

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemRecord {
    private String id;
    private String name = "";
    private String hsn = "";
    private String unit = "PCS";
    private double rate;
    private double gst = 18.0;
    private String categoryId = "";
    private String categoryName = "";
    // v4.3 — Purchase foundation groundwork (backward compatible defaults)
    private double purchaseRate = 0.0;
    private double currentStock = 0.0;
    private double openingStock = 0.0;
    private double reorderLevel = 0.0; // 0 = no alerting
    private String createdAt;
    private String updatedAt;

    public ItemRecord() {}

    public ItemRecord(String id, String name, String hsn, String unit, double rate, double gst) {
        this(id, name, hsn, unit, rate, gst, "", "");
    }

    public ItemRecord(String id, String name, String hsn, String unit, double rate, double gst, String categoryId, String categoryName) {
        this.id = id;
        this.name = name;
        this.hsn = hsn;
        this.unit = unit;
        this.rate = rate;
        this.gst = gst;
        this.categoryId = categoryId != null ? categoryId : "";
        this.categoryName = categoryName != null ? categoryName : "";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHsn() { return hsn; }
    public void setHsn(String hsn) { this.hsn = hsn; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public double getRate() { return rate; }
    public void setRate(double rate) { this.rate = rate; }

    public double getGst() { return gst; }
    public void setGst(double gst) { this.gst = gst; }

    public String getCategoryId() { return categoryId != null ? categoryId : ""; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId != null ? categoryId : ""; }

    public String getCategoryName() { return categoryName != null ? categoryName : ""; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName != null ? categoryName : ""; }

    public double getPurchaseRate() { return purchaseRate; }
    public void setPurchaseRate(double purchaseRate) { this.purchaseRate = purchaseRate; }

    public double getCurrentStock() { return currentStock; }
    public void setCurrentStock(double currentStock) { this.currentStock = currentStock; }

    public double getOpeningStock() { return openingStock; }
    public void setOpeningStock(double openingStock) { this.openingStock = openingStock; }

    public double getReorderLevel() { return reorderLevel; }
    public void setReorderLevel(double reorderLevel) { this.reorderLevel = reorderLevel; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() { return name; }
}
```

- The stock item: selling identity (`rate`, `gst`, `hsn`, `unit`) plus the
  v4.3 purchasing/stock quartet — `purchaseRate` (what you pay), the stock
  trio whose *cache* role Chapter 5 established (`currentStock` is
  recomputed from the ledger; `openingStock` is the human-entered seed;
  `reorderLevel > 0` arms the low-stock alerts in Chapter 14's reports).
  The comment marks the version that introduced them — matching the
  Chapter 3 migration lines — and "backward compatible defaults" is
  `@JsonIgnoreProperties` + `= 0.0` doing its job: old JSON loads as if the
  fields were zeros.
- Constructor delegation (`this(id, name, …, "", "")`) — the 6-arg
  convenience forwards to the 8-arg canonical form; one mapping path, two
  doors.

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ItemCategory {
    private String id;
    private String name = "";
    private String createdAt;
    private String updatedAt;

    public ItemCategory() {
        this.id = "cat_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.updatedAt = this.createdAt;
    }

    public ItemCategory(String id, String name) {
        this.id = (id != null && !id.isBlank()) ? id : "cat_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        this.name = name != null ? name : "";
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name != null ? name : ""; }
    public void setName(String name) { this.name = name; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        return name;
    }
}
```

- **ID generation lives in the constructor here** (`cat_` + 10 hex chars)
  — a different placement than `ItemDao.save` (which back-fills item ids).
  Consequence: a new `ItemCategory` is *born with* an identity, which is
  why the MCP find-or-create helpers (Chapter 18) can build one, probe the
  DAO by id, and save on miss without a separate generation step. Note the
  timestamps use `LocalDateTime` (no zone) rather than `Instant` — the
  pre-existing convention of this class; the DAOs overwrite both stamps on
  save anyway (Chapter 4), so the constructor values are effective only
  until first persist.

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Transport {
    private String id;
    private String name = "";
    private String phone = "";
    private String vehicleNumber = "";
    private String createdAt;
    private String updatedAt;

    public Transport() {
        this.id = "trp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.updatedAt = this.createdAt;
    }

    public Transport(String id, String name, String phone, String vehicleNumber) {
        this.id = (id != null && !id.isBlank()) ? id : "trp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        this.name = name != null ? name : "";
        this.phone = phone != null ? phone : "";
        this.vehicleNumber = vehicleNumber != null ? vehicleNumber : "";
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name != null ? name : ""; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone != null ? phone : ""; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getVehicleNumber() { return vehicleNumber != null ? vehicleNumber : ""; }
    public void setVehicleNumber(String vehicleNumber) { this.vehicleNumber = vehicleNumber; }

    public String getVehicleNo() { return getVehicleNumber(); }
    public void setVehicleNo(String vehicleNo) { setVehicleNumber(vehicleNo); }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @Override
    public String toString() {
        if (vehicleNumber != null && !vehicleNumber.isBlank()) {
            return name + " (" + vehicleNumber + ")";
        }
        return name;
    }
}
```

- Same constructor-id pattern (`trp_`), same family shape — plus
  `toString()` upgraded to `Name (MH12AB1234)`, which is what the bill
  editor's transport combo actually displays: the clerk picks the lorry by
  plate, not just firm name. The `getVehicleNo` alias matches the
  built-in variable's name (`vehicle_no`, Chapter 3) — the merge-field
  vocabulary echoing into the model.

### Step 9 — `model/Expense.java` and `model/ExpenseAccount.java`

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Expense voucher (Tally F5/F7: Payment/Expense).
 *
 * Persisted as JSON in the {@code expenses} table via ExpenseDao.
 * Category is one of the built-in direct/indirect heads (see
 * Expense.CATEGORIES) or a user-defined label stored in the JSON payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Expense {
    /** Built-in chart-of-accounts heads: first 4 direct, rest indirect. */
    public static final String[] CATEGORIES = {
            "Freight Inward", "Wages", "Power & Fuel", "Packaging",
            "Office Rent", "Electricity", "Salaries", "Telephone & Internet",
            "Marketing", "Bank Charges", "Software Subscription", "Tea & Pantry",
            "Repairs & Maintenance", "Miscellaneous"
    };

    public static boolean isDirect(String category) {
        if (category == null) return false;
        for (int i = 0; i < 4 && i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equalsIgnoreCase(category)) return true;
        }
        return false;
    }

    private String id;
    private String date;           // yyyy-MM-dd
    private String category = "Miscellaneous";
    private String description = "";
    private double amount;
    private String paymentMode = "Cash"; // Cash / Bank / NEFT / Cheque / UPI
    private String reference = "";       // cheque no / UTR
    private String payee = "";
    private String createdAt;
    private String updatedAt;

    public Expense() {}

    public Expense(String id, String date, String category, String description, double amount, String paymentMode) {
        this.id = id;
        this.date = date;
        this.category = category;
        this.description = description;
        this.amount = amount;
        this.paymentMode = paymentMode;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDate() { return date != null ? date : ""; }
    public void setDate(String date) { this.date = date; }

    public String getCategory() { return category != null ? category : "Miscellaneous"; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description != null ? description : ""; }
    public void setDescription(String description) { this.description = description; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getPaymentMode() { return paymentMode != null ? paymentMode : "Cash"; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

    public String getReference() { return reference != null ? reference : ""; }
    public void setReference(String reference) { this.reference = reference; }

    public String getPayee() { return payee != null ? payee : ""; }
    public void setPayee(String payee) { this.payee = payee; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
```

- **The chart of accounts as a constant.** Fourteen heads, first four
  direct (freight in, wages, power & fuel, packaging — costs of making the
  goods move) and ten indirect (overheads). `isDirect()` is the P&L's
  classifier: `FinancialService` (Chapter 14) splits the expense report
  into *Cost of Sales* vs *Operating Expenses* by exactly this boundary.
  The array being `public static final` means the expense screen's combo
  and the reports share one source; user-defined payees (below) ride
  beside it without disturbing the canonical list.
- `payee` is a *name* reference into the `ExpenseAccount` registry — the
  loose coupling Chapter 5's `ExpenseAccountDao` Javadoc described:
  vouchers keep working even if the registry entry is renamed or archived,
  because they store the string they were written with.

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A named expense account (payee directory) — "Techparks Rent", "Indian Oil",
 * etc. Referenced by name from {@link Expense#getPayee()} so existing vouchers
 * keep working with zero migration; this registry only adds structure on top.
 *
 * Persisted as JSON in the {@code expense_accounts} table via ExpenseAccountDao.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExpenseAccount {

    private String id;
    private String name = "";
    private String notes = "";
    private String defaultPaymentMode = "";   // optional convenience default
    private boolean archived = false;
    private String createdAt;
    private String updatedAt;

    public ExpenseAccount() {}

    public ExpenseAccount(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name != null ? name : ""; }
    public void setName(String name) { this.name = name; }

    public String getNotes() { return notes != null ? notes : ""; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getDefaultPaymentMode() { return defaultPaymentMode != null ? defaultPaymentMode : ""; }
    public void setDefaultPaymentMode(String defaultPaymentMode) { this.defaultPaymentMode = defaultPaymentMode; }

    public boolean isArchived() { return archived; }
    public void setArchived(boolean archived) { this.archived = archived; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
```

- The registry row: a remembered payee with optional conveniences (notes, a
  default payment mode pre-filling the expense form) and the
  **archive flag** — §3's "soft" state for *directories*: not deleted (old
  vouchers still resolve), just retired from the pickers (Chapter 5's
  `archived ASC` ordering). Note there is no ID generation in the
  constructor — accounts are created through the service layer, which
  assigns ids and enforces the name-uniqueness rules.

### Step 10 — `model/PurchaseBill.java` (complete file)

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Purchase Bill (inward supply) from a Seller / Supplier.
 *
 * Mirrors the sales-side {@link Bill} but records stock IN and Input Tax
 * Credit instead of stock OUT and output GST. Reuses {@link BillItem} for
 * line items and {@link BillTotals} for the GST computation block.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PurchaseBill {
    private String id;
    private String billNo;          // internal purchase voucher no (PUR-0001)
    private String supplierBillNo;  // supplier's original invoice no (GST matching)
    private String date;            // yyyy-MM-dd (bill date)
    private String supplierId = "";
    private String supplierName = "";
    private String supplierGstin = "";
    private List<BillItem> items = new ArrayList<>();
    private double discountPct;
    private double freight;         // other charges (freight inward / packing)
    private BillTotals totals = new BillTotals();
    private boolean paid;           // false = on credit (adds to payable)
    private String paymentMode = ""; // CASH / BANK / CHEQUE / UPI
    /** Partial/full payments made against this bill (supplier payment vouchers). */
    private List<com.invoicestudio.model.BillPayment> payments = new ArrayList<>();
    private String notes = "";
    private String createdAt;
    private String updatedAt;

    public PurchaseBill() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBillNo() { return billNo != null ? billNo : ""; }
    public void setBillNo(String billNo) { this.billNo = billNo; }

    public String getSupplierBillNo() { return supplierBillNo != null ? supplierBillNo : ""; }
    public void setSupplierBillNo(String supplierBillNo) { this.supplierBillNo = supplierBillNo; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getSupplierId() { return supplierId != null ? supplierId : ""; }
    public void setSupplierId(String supplierId) { this.supplierId = supplierId; }

    public String getSupplierName() { return supplierName != null ? supplierName : ""; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public String getSupplierGstin() { return supplierGstin != null ? supplierGstin : ""; }
    public void setSupplierGstin(String supplierGstin) { this.supplierGstin = supplierGstin; }

    public List<BillItem> getItems() { return items; }
    public void setItems(List<BillItem> items) { this.items = items != null ? items : new ArrayList<>(); }

    public double getDiscountPct() { return discountPct; }
    public void setDiscountPct(double discountPct) { this.discountPct = discountPct; }

    public double getFreight() { return freight; }
    public void setFreight(double freight) { this.freight = Math.max(0, freight); }

    public BillTotals getTotals() { return totals; }
    public void setTotals(BillTotals totals) { this.totals = totals != null ? totals : new BillTotals(); }

    public boolean isPaid() { return paid; }
    public void setPaid(boolean paid) { this.paid = paid; }

    public String getPaymentMode() { return paymentMode != null ? paymentMode : ""; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

    public List<com.invoicestudio.model.BillPayment> getPayments() {
        return payments != null ? payments : (payments = new ArrayList<>());
    }
    public void setPayments(List<com.invoicestudio.model.BillPayment> payments) {
        this.payments = payments != null ? payments : new ArrayList<>();
    }

    /** Total paid so far (for bills marked Paid without explicit rows, equals payable). */
    public double getPaidAmount() {
        double sum = getPayments().stream().mapToDouble(com.invoicestudio.model.BillPayment::getAmount).sum();
        if (sum == 0 && paid) return getAmountPayable();
        return sum;
    }

    public String getNotes() { return notes != null ? notes : ""; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    /** Grand total including freight (other charges) and GST. */
    public double getAmountPayable() {
        double base = totals != null ? totals.getGrandTotal() : 0.0;
        return base + (freight > 0 ? freight : 0.0);
    }

    public Map<String, String> extraVars() {
        Map<String, String> m = new HashMap<>();
        m.put("supplier_bill_no", supplierBillNo);
        m.put("freight", String.valueOf(freight));
        return m;
    }
}
```

- **The mirror-with-differences** the class Javadoc declares. Reuses
  `BillItem` and `BillTotals` outright (one line-item grammar across both
  trade directions), and adds the purchase-only fields: the two bill
  numbers (yours `PUR-0001`, theirs — for GST input matching), supplier
  identity snapshot, and **`freight`**.
- **`getAmountPayable()` is a computed composition**: GST-inclusive total
  *plus* freight. Freight is deliberately *outside* the `BillTotals`
  snapshot — it isn't taxed as goods are, and keeping it separate lets the
  renderer print "Taxable + GST" and "Other charges" as the law's format
  expects. The two computed money answers (`getPaidAmount`,
  `getAmountPayable`) repeat Chapter 5's projection rules *inside the
  model* so every consumer — DAO, report, MCP tool — derives identically.
- `extraVars()` hands the renderer the two purchase-specific merge fields
  (`supplier_bill_no`, `freight`) — the document offering up its extras in
  the template system's own currency (a string map), no special-case
  plumbing.
- The lazy `getPayments()` (`payments != null ? payments : (payments = new ArrayList<>())`)
  *repairs* the field on read — a slightly different defensive style than
  the setters-elsewhere; same guarantee, one allocation saved.

### Step 11 — `model/Transaction.java` (complete file)

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Transaction {
    private String id;
    private String buyerId = "";
    private String buyerName = "";
    private String bookType = "CC"; // "CC" (Credit) or "CS" (Cash Sale)
    private String transactionType = "sale"; // "sale" or "payment"
    private String transactionDate = LocalDate.now().toString();
    private String dueDate = "";
    private double amount = 0.0;
    private int totalQuantity = 0; // Total pieces / units
    private String checkNumber = ""; // Check / Cheque number
    private boolean includeInReporting = true;
    private int parcel = 0; // Shipment parcel count
    private String billId = ""; // Optional linked bill id
    private String billNo = ""; // Optional linked bill number
    private String notes = ""; // Optional notes / remarks
    private boolean deleted = false;
    private String deletedReason = "";
    private String deletedAt = "";
    private String createdAt;
    private String updatedAt;

    public Transaction() {
        this.id = "tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.updatedAt = this.createdAt;
    }

    public Transaction(String id, String buyerId, String buyerName, String bookType, String transactionType,
                       String transactionDate, String dueDate, double amount, int totalQuantity,
                       String checkNumber, boolean includeInReporting, int parcel) {
        this.id = (id != null && !id.isBlank()) ? id : "tx_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        this.buyerId = buyerId != null ? buyerId : "";
        this.buyerName = buyerName != null ? buyerName : "";
        this.bookType = bookType != null ? bookType.toUpperCase() : "CC";
        this.transactionType = transactionType != null ? transactionType.toLowerCase() : "sale";
        this.transactionDate = (transactionDate != null && !transactionDate.isBlank()) ? transactionDate : LocalDate.now().toString();
        this.dueDate = dueDate != null ? dueDate : "";
        this.amount = amount;
        this.totalQuantity = totalQuantity;
        this.checkNumber = checkNumber != null ? checkNumber : "";
        this.includeInReporting = includeInReporting;
        this.parcel = parcel;
        this.createdAt = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        this.updatedAt = this.createdAt;
    }

    public Transaction(String id, String buyerId, String buyerName, String bookType, String transactionType,
                       String transactionDate, String dueDate, int totalQuantity, double amount,
                       String checkNumber, boolean includeInReporting, String billNo) {
        this(id, buyerId, buyerName, bookType, transactionType, transactionDate, dueDate, amount, totalQuantity, checkNumber, includeInReporting, 0);
        this.billNo = billNo != null ? billNo : "";
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBuyerId() { return buyerId != null ? buyerId : ""; }
    public void setBuyerId(String buyerId) { this.buyerId = buyerId; }

    public String getBuyerName() { return buyerName != null ? buyerName : ""; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    public String getBookType() { return bookType != null ? bookType : "CC"; }
    public void setBookType(String bookType) { this.bookType = bookType != null ? bookType.toUpperCase() : "CC"; }

    public String getTransactionType() { return transactionType != null ? transactionType : "sale"; }
    public void setTransactionType(String transactionType) { this.transactionType = transactionType != null ? transactionType.toLowerCase() : "sale"; }

    public String getTransactionDate() { return transactionDate != null ? transactionDate : ""; }
    public void setTransactionDate(String transactionDate) { this.transactionDate = transactionDate; }

    public String getDueDate() { return dueDate != null ? dueDate : ""; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public int getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(int totalQuantity) { this.totalQuantity = totalQuantity; }

    public String getCheckNumber() { return checkNumber != null ? checkNumber : ""; }
    public void setCheckNumber(String checkNumber) { this.checkNumber = checkNumber; }

    public boolean isIncludeInReporting() { return includeInReporting; }
    public void setIncludeInReporting(boolean includeInReporting) { this.includeInReporting = includeInReporting; }

    public int getParcel() { return parcel; }
    public void setParcel(int parcel) { this.parcel = parcel; }

    public int getParcels() { return parcel; }
    public void setParcels(int parcels) { this.parcel = parcels; }

    public String getBillId() { return billId != null ? billId : ""; }
    public void setBillId(String billId) { this.billId = billId; }

    public String getBillNo() { return billNo != null ? billNo : ""; }
    public void setBillNo(String billNo) { this.billNo = billNo; }

    public String getBillNumber() { return billNo != null ? billNo : ""; }
    public void setBillNumber(String billNumber) { this.billNo = billNumber; }

    public String getNotes() { return notes != null ? notes : ""; }
    public void setNotes(String notes) { this.notes = notes; }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }

    public String getDeletedReason() { return deletedReason != null ? deletedReason : ""; }
    public void setDeletedReason(String deletedReason) { this.deletedReason = deletedReason; }

    public String getDeletedAt() { return deletedAt != null ? deletedAt : ""; }
    public void setDeletedAt(String deletedAt) { this.deletedAt = deletedAt; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    public boolean isSale() {
        return "sale".equalsIgnoreCase(transactionType);
    }

    public boolean isPayment() {
        return "payment".equalsIgnoreCase(transactionType);
    }

    @Override
    public String toString() {
        return (isSale() ? "Sale #" : "Payment #") + id + " - " + buyerName + " (₹" + String.format("%.2f", amount) + ")";
    }
}
```

- The cash/bank book's row (Chapter 5's storage chapter, now with its face).
  Key reading:
  - **`bookType` "CC"/"CS"** — the shop's ledger-column vocabulary: Credit
    book (udhaar sales) vs Cash Sale book. Normalised `toUpperCase()` on
    *every* write path (constructor and setter) so case can never fork the
    two books into four.
  - **`transactionType` "sale"/"payment"** — money out to the book (a
    sale) vs money in from the customer (a payment). Normalised lowercase
    symmetrically. `isSale()`/`isPayment()` are the case-insensitive
    askers the reports use instead of string-comparing everywhere.
  - **The soft-delete trio** (`deleted`, `deletedReason`, `deletedAt`) —
    the model half of Chapter 5's `deleteTransaction`.
  - **Three constructors** — the no-arg (born with id + today), the
    canonical 12-arg (normalises as it stores), and the legacy 12-arg-with-
    billNo variant that *delegates* with `this(...)` then sets `billNo`.
    The delegation keeps one normalisation path even for the old caller
    signature (its parameter order — amount/quantity swapped vs canonical —
    is why delegation was essential, not convenience).
  - `toString()` — the log/audit-friendly one-liner with the rupee sign
    hardcoded (log lines are display text; the *data* uses Settings'
    currency).

### Step 12 — `model/PaymentMethod.java` and `model/RepeatCadence.java`

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentMethod {
    CASH("Cash"),
    UPI("UPI"),
    BANK_TRANSFER("Bank Transfer"),
    CHEQUE("Cheque"),
    CARD("Card"),
    OTHER("Other");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() { return label; }

    @JsonCreator
    public static PaymentMethod fromString(String val) {
        if (val == null) return CASH;
        for (PaymentMethod m : values()) {
            if (m.label.equalsIgnoreCase(val) || m.name().equalsIgnoreCase(val.replace(" ", "_"))) return m;
        }
        return CASH;
    }

    @Override
    public String toString() { return label; }
}
```

```java
package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RepeatCadence {
    NONE("none", "None"),
    WEEKLY("weekly", "Weekly"),
    MONTHLY("monthly", "Monthly"),
    YEARLY("yearly", "Yearly");

    private final String code;
    private final String label;

    RepeatCadence(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonValue
    public String getCode() { return code; }

    public String getLabel() { return label; }

    @JsonCreator
    public static RepeatCadence fromString(String val) {
        if (val == null) return NONE;
        for (RepeatCadence r : values()) {
            if (r.code.equalsIgnoreCase(val) || r.name().equalsIgnoreCase(val)) return r;
        }
        return NONE;
    }

    @Override
    public String toString() { return label; }
}
```

- **`PaymentMethod`** has no separate code — its *label* is the stored
  value (`"Bank Transfer"` with the space, human-readable in JSON). The
  `@JsonCreator` therefore matches two ways: by label, or by constant name
  after replacing underscores with spaces (`"BANK_TRANSFER"` →
  `"BANK TRANSFER"` matches too) — every era of written data loads.
  Unknown → CASH, the conservative money answer.
- **`RepeatCadence`** is the recurring engine's vocabulary (Chapter 12's
  `RecurringEngine` sweeps bills whose cadence is due): NONE never
  repeats, and unknown → NONE — the only default that can *prevent* an
  action rather than take one. The `Bill.repeat` field you met in Step 1
  is this enum at work.

---

## 6. How it works at runtime

One invoice's object graph, as it exists in memory while the clerk edits
and after it's saved:

```
 Bill (the aggregate root — everything hangs off it)
 ├── id, billNo "INV-0042", date "2026-09-19"
 ├── docType: DocType.INVOICE          status: BillStatus.UNPAID (enum, code "unpaid")
 ├── variables: { buyer_name:"Solaris Enterprises", po_no:"PO/88",
 │                buyer_state_code:"27", transport_name:"Sharma Trans", … }
 ├── items: [ BillItem{ qty 40, rate 25, gst 18 → getAmount() = 1000.00 }, … ]
 ├── totals: BillTotals{ subtotal 1000, cgst 90, sgst 90, grandTotal 1180 }
 ├── payments: [ BillPayment{ 1180, UPI, ref "UTR…", method label "UPI" } ]
 └── repeat: RepeatCadence.NONE
        │
        ▼ mapper.writeValueAsString(bill)        (BillDao.saveBill, Ch 5)
 {"id":"…","billNo":"INV-0042","status":"unpaid","payments":[{"method":"UPI",…}],…}
        │          ↑ enums serialize as their @JsonValue codes
        ▼ stored in bills.json_data + projected register columns
```

**The three derived-answer paths, all consistent by construction:**

| Question | Answered by | Source |
|---|---|---|
| What does the customer owe? | `BillDao` projection: grand − Σ payments | facts stored on Bill |
| What does the line print as? | `BillItem.getAmount()` computed at render | qty, rate, discPct |
| Which taxes apply? | editor compares `getEffectiveStateCode()`s | GSTIN structure |

**Round-trip guarantee:** save a Bill → JSON → load it into a *newer*
class with two extra fields — the fields default, the rest survives
(`ignoreUnknown`). Save a `BillStatus.CANCELLED` → file stores
`"cancelled"` → rename nothing, ever.

---

## 7. How to change it

**Add a field to a model** (e.g. `salesperson` on Bill):
1. Add `private String salesperson = "";` + getter/setter to `Bill`.
2. Done for persistence — it rides `json_data`. Old files load fine
   (missing key → default), new files carry it (readers without the
   field... none exist — all readers are this same class).
3. Only if a *screen* must edit it or a *report* must filter it do you
   continue: editor widget → variable-map entry if templates need it →
   Chapter 3's hot-column recipe if SQL must see it.
Verify: save a bill with the field, restart the app, reopen, confirm it
persisted; run `mvn test -Dtest=DatabaseTest`. What breaks if skipped:
nothing (that's the JSON-blob design); what breaks if you *rename* a
field instead of adding: old JSON silently loses the value under its old
name — prefer *adding* and deprecating.

**Add an enum constant** (e.g. `PaymentMethod.NEFT`): add the constant
with its label; `fromString`'s loop picks it up automatically. Old files
unaffected; new files store the new label. Verify with a one-line JUnit:
`assertEquals(PaymentMethod.NEFT, PaymentMethod.fromString("NEFT"))`.

**Add a computed accessor** (e.g. `getLineCount()`): add a plain method —
Jackson ignores methods without setters when *reading* and skips
get-style properties only if you annotate; a read-only `getX()` *would*
be written into JSON by default. If you don't want that, mark it
`@JsonIgnore`. (None of this chapter's computed helpers are serialised —
they all compute from stored fields — which is why the rule hasn't
bitten yet.)

---

## 8. Performance & UX analysis

| Decision | What was done | Cost | Better alternative | Trade-off / difficulty | User-visible effect |
|---|---|---|---|---|---|
| Mutable JavaBeans vs Java 21 `record`s | classic getter/setter classes | verbose (~150 lines each) | records for value types (`BillItem`, `BillTotals`) | **Medium**; records are immutable — the editor mutates lines in place, so Bill/Buyer can't switch; small value types could | none; less boilerplate to read |
| Defaults in field declarations | every model is valid at `new` | defaults can mask missing data (`gst = 18.0` on a JSON that lost its gst) | explicit required-field validation at save | **Medium**; validation lives in services today | no NPEs anywhere in the UI — the app's most invisible reliability win |
| `@JsonIgnoreProperties` everywhere | forward-compatible loading | unknown keys silently dropped | strict mode + version field | **Medium**; strictness would catch typos but break every evolution | old backups always open |
| Enum code/label split | stable storage codes + display labels | small class bulk | storing `name()` | renames of Java constants stop being storage migrations | settings/status survive refactors |
| Aliased accessors (`getGstin`/`getGst`…) | vocabulary tolerance | 3 lines each | enforce one vocabulary by refactor | **Medium**; big diff, no behaviour change | none; code reads naturally at every call site |
| `BillTotals` 7-arg constructor drops `dueAmount` | misleading signature (this chapter's `ISSUE:`) | one confused reader per year | remove the param or store the field | **Easy** — but touches `DatabaseTest`'s call | none at runtime |
| Unused `JsonProperty` import in `Bill` | leftover | none | delete | **Easy** | none |

`OPTIONAL IMPROVEMENT` — make the totals constructor honest:

```java
// OPTIONAL IMPROVEMENT: Easy — keep the signature, refuse the lie
public BillTotals(double subtotal, double cgst, double sgst, double igst,
                  double grandTotal, double roundOff, double ignoredDueAmount) {
    // due is DERIVED (grand - payments) in BillDao; the parameter is kept
    // for call-site compatibility but intentionally not stored.
    this.subtotal = subtotal; this.cgst = cgst; this.sgst = sgst;
    this.igst = igst; this.grandTotal = grandTotal; this.roundOff = roundOff;
}
```

Renaming the parameter to `ignoredDueAmount` changes no bytecode behaviour
but makes every IDE tooltip tell the truth. Difficulty: **Easy**.
User-visible: none; future-reader-visible: immediate.

`OPTIONAL IMPROVEMENT` — validate money at the boundary:

```java
// OPTIONAL IMPROVEMENT: in BillingService (Ch 12) before save — Medium
if (bill.getItems().isEmpty()) throw new IllegalArgumentException("Bill needs at least one line");
if (bill.getTotals().getGrandTotal() < 0) throw new IllegalArgumentException("Negative grand total");
```

Defaults make models *tolerant*; the service makes them *correct*. The
current app validates at the editor's Save button; a service-level guard
adds a second wall for MCP/AI flows (Chapter 18) that bypass the editor.

---

## 9. Common mistakes and fixes

| Symptom | Cause | Fix |
|---|---|---|
| `UnrecognizedPropertyException` reading an old JSON | a model missing `@JsonIgnoreProperties(ignoreUnknown = true)` | add the annotation to the class |
| New model field never appears after reload | getter/setter pair missing (Jackson maps by accessors, not fields) | add the accessors; keep the JavaBean names exact (`getSalesperson`/`setSalesperson`) |
| `status` reads as UNPAID though the file says `PAID` | typo'd or differently-cased value fell to `fromString`'s default | check the stored code against the enum's `@JsonValue` spelling |
| Tax computes IGST for a same-state sale | buyer's `stateCode` blank AND GSTIN too short → `getEffectiveStateCode()` returns `""` | enter the state code or a full GSTIN; the routing comparison then works |
| Line amount ≠ qty × rate in a report | discount applied twice (once in `getAmount()`, again by the caller) | call `getGross()` for pre-discount math; `getAmount()` is already discounted |
| `new BillTotals(…, due)` then reading due gives 0 | the constructor drops the parameter (this chapter's `ISSUE:`) | derive due as grand − Σ payments, as `BillDao` does |
| Two books appear in the transactions tabs | bookType stored with different case before the normalising setters existed | data fix: `UPDATE transactions SET book_type = UPPER(book_type)` (or let Chapter 5's upsert rewrite each row on next edit) |

---

## 10. Checkpoint

- [ ] `mvn test -Dtest=DatabaseTest` green — Bill/Buyer/Settings/Variable
      round-trips all pass through Jackson.
- [ ] You can draw `Bill`'s object graph from memory (items, totals,
      payments, variables) and say which parts are *stored* vs *computed*.
- [ ] You can recite the three defensive idioms and point at one instance
      of each in this chapter's code.
- [ ] You can explain, in one sentence each: GSTIN's first-two-digits rule,
      CGST+SGST vs IGST, and why `RepeatCadence`'s unknown-default is NONE
      while `BillStatus`'s is UNPAID.

**Exercises**

1. Add `salesperson` to `Bill` (field + accessors), write a 6-line JUnit
   that Jackson-round-trips a Bill through a string and asserts the field
   survives — your first model test.
2. Add `PaymentMethod.NEFT("NEFT")` and prove both `fromString("NEFT")`
   and `fromString("neft")` resolve; then serialise a payment with it and
   inspect the JSON key's stored spelling.
3. Find every caller of `Buyer.getEffectiveStateCode()` with the
   compiler's find-usages, and trace which one decides CGST+SGST vs IGST.
   Note the answer for Chapter 12, where that logic becomes explicit.

---

## 11. Summary and coverage self-check

You now own the app's vocabulary: the invoice aggregate (`Bill` with its
always-valid collections, variable-map bag, two-way parcel sync), the line
math (`BillItem.getAmount` with clamped discount and paise rounding), the
payment trail (`BillPayment`), the GST snapshot (`BillTotals` — including
its honest-`ISSUE:` constructor), the seller identity with demo defaults
(`BusinessProfile`), the preference sheet (`Settings` and its
threshold-sync note), the two party records with derived GSTIN intelligence
(`Buyer`, `Supplier`), the stock item and its ledger-coupled stock fields
(`ItemRecord`), the registry patterns (`ItemCategory`, `Transport`,
`ExpenseAccount`, `BuyerFieldDef`, `CustomFontDef`), the money records
(`Expense` with its chart of accounts, `Transaction` with normalised
book/type vocabulary and soft-delete trio, `PurchaseBill` with freight
outside the tax math), and the two code-stable enums (`BillStatus`,
`PaymentMethod`, `RepeatCadence` — three, counting them properly).

**Files covered in full this chapter (19):**
`Bill.java` ✅ · `BillItem.java` ✅ · `BillPayment.java` ✅ ·
`BillStatus.java` ✅ · `BillTotals.java` ✅ · `BusinessProfile.java` ✅ ·
`Settings.java` ✅ · `Buyer.java` ✅ · `BuyerFieldDef.java` ✅ ·
`Supplier.java` ✅ · `ItemRecord.java` ✅ · `ItemCategory.java` ✅ ·
`Transport.java` ✅ · `Expense.java` ✅ · `ExpenseAccount.java` ✅ ·
`PurchaseBill.java` ✅ · `Transaction.java` ✅ · `PaymentMethod.java` ✅ ·
`RepeatCadence.java` ✅

**Markers raised:** 1 `ISSUE:` (`BillTotals`' dropped `dueAmount`
parameter — signature misleads, runtime unaffected); 1 `GAP:` (unused
`JsonProperty` import in `Bill`); 1 product-decision note (demo identity
as `BusinessProfile` defaults).

**Next: Chapter 7 — "Domain Models, Part 2: Templates & Design Objects"**
(`Template`, `TemplateElement` — the 823-line heart of the designer —
`ElementType`, `TableColumn`, `DocType`, `LabelConfig`, `LabelPrintHistory`,
`PageConfig`, `PageSizeName`, `VariableDef`, `CustomComponent`,
`ComponentPreset`, `PresetTemplates`, `KnowledgeArticle`, `UserSession`,
`UnitConverter`).