# Chapter 14 — Reports & Dashboards: Reading the Business at a Glance

> **Part 5 of InvoiceStudio: Zero to Finished Product**
> Files covered in full this chapter: `ui/views/DashboardView.java`,
> `ui/views/Dashboard2View.java`, `ui/views/ReportsView.java`,
> `ui/views/ReportsBuilders.java`, `ui/views/StockAnalysisView.java`,
> `service/ExpenseAnalytics.java`, `ui/ExpenseReportDialog.java`,
> `test/.../service/ExpenseAccountTest.java` — all eight read and reproduced
> from the repository.
> Goal at the end: **every number the business owner needs is one click away —
> and every formula behind those numbers is unit-tested.**

---

## 1. Chapter goal

By the end of this chapter you will have built, exactly as the repository has it:

1. **Dashboard 1** (`DashboardView`) — the standard overview: month navigation,
   recurring-invoice banner, four animated KPI cards, a hand-drawn 6-month
   revenue bar chart, a live GST summary, top-5 buyers, and recent invoices.
2. **Dashboard 2** (`Dashboard2View`) — the "Financial & Logistics" dashboard:
   all-time and current-period KPIs with month-over-month trends, two JavaFX
   charts, a parcel-count analysis card with Day/Week/Month/Year switching,
   three Top-5 leaderboards, and a recent-activity table that toggles between
   transactions and bills **without rebuilding the page**.
3. **Reports view** (`ReportsView` + `ReportsBuilders`) — eight report tabs:
   Outstanding & Aging, Buyer Statement & Ledger, Trouser Movement, Sales
   Trends, Item Sales & Movement, Category Breakdown, GST/Tax Summary, and
   Transport Performance.
4. **Stock & Profitability** (`StockAnalysisView`) — a Tally-style stock
   summary, item-wise gross profit, and a low-stock watchlist.
5. **Expense analytics** (`ExpenseAnalytics` + `ExpenseReportDialog`) — the
   account/category expense reports, with **all math in a pure, unit-tested
   class** and the dialog doing nothing but formatting.

And you will understand the two architectural ideas that make this chapter
work: the **shell/builder split** (a tiny view class delegates to a builder
class) and **pure-logic extraction** (math lives where tests can reach it).

---

## 2. Story intro

Imagine the owner of a trouser business — call him Kumar — standing in his
office at 9 p.m. Books are written by hand in ledgers. To answer "how much
does Ram Traders still owe me?" he flips pages. To answer "did this month beat
last month?" he does arithmetic on a calculator. To prepare GST numbers for
his accountant, he re-adds an entire month of invoices, twice, because the two
sums didn't match the first time.

A ledger is a *record*. What Kumar needs is a *report*: the same records,
pre-arranged to answer a question. Reports are pre-computed answers. A
dashboard is a page of the most important answers, visible without asking.

That is what this chapter builds. And it teaches one discipline above all
others: **the formula and the picture must live apart.** The formula
(`ExpenseAnalytics`) is plain Java with no UI — it can be tested in a
millisecond by a machine. The picture (`ExpenseReportDialog`) is JavaFX — it
can only be verified by a human looking at a screen. When math and pixels are
separated, machines verify the math and humans verify only the looks. When
they are tangled together, neither is verified well.

> **Analogy:** a restaurant kitchen has a *prep station* (where ingredients
> become sauces, measured and consistent) and a *plating station* (where food
> is made beautiful for the table). `ExpenseAnalytics` is the prep station;
> `ExpenseReportDialog` is the plating station. This chapter builds both, for
> every report in the app.

---

## 3. Concepts first

**KPI card.** "Key Performance Indicator." A small card showing one number
that matters (Total Outstanding Due), a title, and a one-line explanation.
Visually it is just a `VBox` with a colored top border — the "accent" color
codes the mood (red = money owed, green = money received).

**Aggregation — one pass, many answers.** To report "sales per month, per
category, per account, plus totals," a beginner writes four loops over the
data. The professional pattern is **one loop** that drops each record into
several *buckets* simultaneously (a `Map<String, double[]>` keyed by month,
another by category…). One pass over the data = O(n) total, no matter how many
reports read the buckets. `ExpenseAnalytics.build()` is the cleanest example
in the codebase — study it once and you can read every report here.

**Trend percentage.** `((current − previous) / |previous|) × 100`. Two edge
cases matter: when *previous* is 0 and current is positive, the trend is
defined as +100% (anything from nothing is infinite growth, capped for
display); when both are 0, it is 0%. `calcTrend()` implements exactly this.

**JavaFX charts.** The app uses four chart types, all from `javafx.scene.chart`:

| Chart | Used for | Key calls |
|---|---|---|
| `LineChart` | Sales vs payments over 12 months | `setAnimated(false)`, series of `XYChart.Data` |
| `BarChart` | Pieces sold per month; parcel counts | `setCategoryGap`, `setBarGap` |
| `StackedBarChart` | CC vs CS volumes in one bar | two series stack in one column |
| `PieChart` | Revenue share by category | `PieChart.Data(label, value)` |

`setAnimated(false)` appears on every chart. Why? JavaFX's chart animation
re-interpolates data on every change; with data that refreshes often it looks
busy and costs frame time. Instant rendering reads as *calmer*, not slower.

**Shell vs builder split.** `ReportsView` is 133 lines: it owns the header,
the `TabPane`, and tab *selection*. `ReportsBuilders` is 1,266 lines: it
builds each tab's content. One class per *role*, not one class per *size*.
The shell exposes one cross-tab hook — `selectBuyerStatement(buyerId)` — so
the Outstanding report's "Statement ↗" button can jump to tab 2 with the right
buyer pre-selected.

**Pure-logic extraction.** A class with no JavaFX imports can be constructed
and asserted against in a plain JUnit test with no screen at all.
`ExpenseAnalytics` and `FinancialService` (Chapter 13) follow this rule; the
dialogs that use them are thin formatting layers.

**`FilteredList`.** A JavaFX collection wrapper that *views* another list
through a predicate. Change the predicate and the bound `TableView` updates
without rebuilding items. The Outstanding report's search box is 8 lines of
code because of it.

**Section-scoped swap vs page rebuild.** When the user flips Dashboard 2's
"Recent Transactions / Recent Bills" toggle, the code replaces **only the
table node inside that one card** (`card.getChildren().set(1, body)`). Scroll
position, KPIs, charts — untouched. Compare with the naive approach (rebuild
the whole dashboard), which resets scroll and re-renders every chart. The
harness-visible counters `recentSwapCount` / `parcelSwapCount` exist so tests
can *prove* a swap happened instead of a rebuild.

**Animated counters.** Dashboard 1's KPI numbers count up from 0 to their
value over 550 ms using a `Timeline` with 18 keyframes and an ease-out curve
(`1 − (1 − frac)³`). Pure polish — but it is written carefully: one shared
`Timeline`, stopped and rebuilt on each refresh, so rapid re-refreshes never
stack animations.

**Cached formatters.** `DateTimeFormatter.ofPattern(...)` re-parses its
pattern string on every call. The views hold `static final` formatters
(`F_YM`, `F_MON_YY`…) and reuse them. Same for the Indian-grouping money
format from `AppFormatters.inrFormat()`.

---

## 4. Files in this chapter

| # | File | Lines | Role |
|---|---|---|---|
| 1 | `ui/views/DashboardView.java` | ~700 | Dashboard 1 — standard overview |
| 2 | `ui/views/Dashboard2View.java` | 1,166 | Dashboard 2 — financial & logistics |
| 3 | `ui/views/ReportsView.java` | 133 | Reports shell: header + 8 tabs |
| 4 | `ui/views/ReportsBuilders.java` | 1,266 | The eight report tab builders |
| 5 | `ui/views/StockAnalysisView.java` | 330 | Stock summary, profitability, low stock |
| 6 | `service/ExpenseAnalytics.java` | ~160 | Pure expense math (unit-tested) |
| 7 | `ui/ExpenseReportDialog.java` | 267 | Expense report window (formatting only) |
| 8 | `test/.../ExpenseAccountTest.java` | 212 | Tests for accounts + analytics |

Depends on: `DataManager` (Ch 8), models (Ch 6–7), `BillingService` /
`RecurringEngine` (Ch 12), `FinancialService` / `CsvService` /
`ExpenseAccountService` (Ch 13), `UiTheme` / `Toast` / `AppFormatters` /
`AppLog` / `AppExecutors` (Ch 2, 9).

Used by: `StudioApp` (`showDashboard`, `showDashboard2`, `showReports`,
`showStockAnalysis`), the sidebar (Ch 9), the chatbot's MCP tools (Ch 18 read
the same aggregates via `ExpenseAnalytics`).

---

## 5. Step-by-step build

We build in dependency order: pure math first, then the dialog that shows it,
then the reports shell and its builders, then the two dashboards, then the
stock view, and finally the tests that lock the math down.

### Step 1 — `service/ExpenseAnalytics.java` (the prep station)

> **ISSUE (found while reading, preserved faithfully):** the class docstring
> says "no JavaFX, so the math is unit-testable" — true. But note its CSV
> helper calls `CsvService.csvRow(...)`, coupling it to one more service. That
> is acceptable (CsvService is also pure), but be aware the "pure" class has
> two dependencies, both pure.

```java
package com.invoicestudio.service;

import com.invoicestudio.model.Expense;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure-Java expense aggregation for the account/category reports — no JavaFX,
 * so the math is unit-tested in isolation (skill rule 5.4).
 *
 * One O(n) pass over the voucher list builds every dimension (month, category,
 * account); the dialogs only format what this returns.
 */
public final class ExpenseAnalytics {

    private ExpenseAnalytics() {}

    /** One aggregate row of a dimension (month / category / account). */
    public record Bucket(String key, int vouchers, double total) {}

    /** Full report for a filter (account or category, optional — null = all). */
    public record Report(
            String dimension,      // "Account" | "Category" | "All"
            String filterName,     // account/category name or "" for all
            String from, String to,
            double total, int voucherCount,
            List<Bucket> byMonth,      // chronological
            List<Bucket> byCategory,   // total desc
            List<Bucket> byAccount,    // total desc
            List<Expense> vouchers) {  // date desc

        public double average() { return voucherCount > 0 ? total / voucherCount : 0; }
    }
```

Line by line:

- `private ExpenseAnalytics() {}` — a *utility class* pattern: the only
  constructor is private, so nobody can instantiate it; everything is
  `static`. The empty body is intentional — it *replaces* the default
  constructor, making the class un-instantiable.
- `record Bucket(String key, int vouchers, double total)` — a Java *record*:
  an immutable data carrier whose components are final fields with accessors
  named exactly like the components (`b.key()`, not `b.getKey()`). Records
  auto-generate the constructor, accessors, `equals`, `hashCode`, and
  `toString`. Perfect for aggregate rows that are computed once and never
  edited.
- `record Report(...)` — the whole answer to one report request. Note the
  comments: `byMonth` is chronological (because it is built from a `TreeMap`
  whose natural key order is `yyyy-MM` string order — which *is* date order),
  while `byCategory`/`byAccount` are total-descending.
- `average()` — a *derived* value, computed on demand rather than stored.
  Guard: `voucherCount > 0` — otherwise division by zero would produce `NaN`
  (Not-a-Number), which would then print as "₹NaN" in the UI.

```java
    /** Build a report over {@code vouchers} filtered to [from, to] (ISO dates, inclusive). */
    public static Report build(List<Expense> vouchers, String dimension, String filterName,
                               String accountName, String category, String from, String to) {
        List<Expense> scoped = new ArrayList<>();
        Map<String, int[]> monthCounts = new TreeMap<>();   // yyyy-MM → [count]
        Map<String, Double> monthTotals = new TreeMap<>();
        Map<String, Integer> catCounts = new java.util.HashMap<>();
        Map<String, Double> catTotals = new java.util.HashMap<>();
        Map<String, Integer> accCounts = new java.util.HashMap<>();
        Map<String, Double> accTotals = new java.util.HashMap<>();

        double total = 0;
        int count = 0;
```

The bucket setup. `TreeMap` for months because we want them *sorted by key*
(ISO dates sort correctly as strings); `HashMap` for categories/accounts
because their order will be decided later by total, not by name. The `int[]`
in `monthCounts` is a mutable counter boxed in the map — `computeIfAbsent`
creates `{0}` on first sight of a month, then `[0]++` bumps it.

```java
        for (Expense e : vouchers) {
            String d = e.getDate() == null ? "" : e.getDate();
            if (!from.isBlank() && d.compareTo(from) < 0) continue;
            if (!to.isBlank() && d.compareTo(to) > 0) continue;
            if (accountName != null && !accountName.isBlank()
                    && !accountName.equalsIgnoreCase(e.getPayee() == null ? "" : e.getPayee().trim())) continue;
            if (category != null && !category.isBlank()
                    && !category.equalsIgnoreCase(e.getCategory())) continue;
```

The filter. Four independent conditions, each with `continue` (skip this
voucher, keep the loop). Details worth noticing:

- ISO dates (`yyyy-MM-dd`) compare correctly **as strings** — `"2026-08-31" <
  "2026-09-01"` lexicographically. That is why no date parsing is needed.
- Blank `from`/`to` mean "no bound" (`!from.isBlank()` guard).
- The account filter trims and case-folds the payee — so "sharma properties"
  matches "Sharma Properties ". This mirrors the registry's own
  case-insensitivity (Chapter 13).
- Null-safe `e.getPayee() == null ? "" : ...` — a null payee must not throw.

```java
            scoped.add(e);
            total += e.getAmount();
            count++;

            String month = d.length() >= 7 ? d.substring(0, 7) : "unknown";
            monthCounts.computeIfAbsent(month, k -> new int[1])[0]++;
            monthTotals.merge(month, e.getAmount(), Double::sum);
            catCounts.merge(e.getCategory(), 1, Integer::sum);
            catTotals.merge(e.getCategory(), e.getAmount(), Double::sum);
            String acc = e.getPayee() == null || e.getPayee().isBlank() ? "(unassigned)" : e.getPayee().trim();
            accCounts.merge(acc, 1, Integer::sum);
            accTotals.merge(acc, e.getAmount(), Double::sum);
        }
```

The one-pass aggregation. Three things to learn here:

1. `computeIfAbsent(month, k -> new int[1])[0]++` — get the counter or create
   it, then increment. The classic mutable-box-in-map idiom.
2. `map.merge(key, value, Double::sum)` — "put value, or combine with the
   existing one using this function." `Double::sum` is a *method reference*
   to `Double.sum(a, b)`; `Integer::sum` likewise. This one line replaces the
   four-line if/else a beginner writes.
3. Vouchers with no payee land in a literal `"(unassigned)"` account rather
   than vanishing — silent data loss is the enemy of trustworthy reports.

```java
        List<Bucket> months = new ArrayList<>();
        for (String m : monthTotals.keySet()) {
            months.add(new Bucket(m, monthCounts.get(m)[0], monthTotals.get(m)));
        }

        List<Bucket> cats = toBuckets(catCounts, catTotals);
        List<Bucket> accs = toBuckets(accCounts, accTotals);

        scoped.sort((a, b) -> b.getDate().compareTo(a.getDate()));

        return new Report(dimension, filterName == null ? "" : filterName, from, to,
                total, count, months, cats, accs, scoped);
    }
```

Assembling the answer. Months come out of the `TreeMap` already in
chronological order. `toBuckets` (below) sorts by total descending. The
voucher list is sorted date-descending for the table (newest first — what a
user scanning a register wants). `filterName == null ? "" : filterName` —
records normalize nulls into empty strings so the dialog never null-checks.

```java
    /** Report for one account (case-insensitive). */
    public static Report forAccount(List<Expense> vouchers, String accountName, String from, String to) {
        return build(vouchers, "Account", accountName, accountName, null, from, to);
    }

    /** Report for one category head (case-insensitive). */
    public static Report forCategory(List<Expense> vouchers, String category, String from, String to) {
        return build(vouchers, "Category", category, null, category, from, to);
    }

    /** Whole-register overview. */
    public static Report overall(List<Expense> vouchers, String from, String to) {
        return build(vouchers, "All", "", null, null, from, to);
    }
```

Three convenience wrappers over `build` — each passes its dimension both as
the *label* and as the *filter* (`forAccount` passes `accountName` in both the
`filterName` and `accountName` slots). Small surface, zero duplication.

```java
    private static List<Bucket> toBuckets(Map<String, Integer> counts, Map<String, Double> totals) {
        List<Bucket> out = new ArrayList<>();
        for (Map.Entry<String, Double> en : totals.entrySet()) {
            out.add(new Bucket(en.getKey(), counts.getOrDefault(en.getKey(), 0), en.getValue()));
        }
        out.sort((a, b) -> Double.compare(b.total(), a.total()));
        return out;
    }
```

Merge counts+totals into `Bucket`s sorted biggest-first. `getOrDefault(..., 0)`
defends against a key present in totals but somehow missing from counts
(cannot happen with this loop structure, but the defense is free).

```java
    /** CSV export of the report's voucher table (uses CsvService quoting). */
    public static String csv(Report r, String currency) {
        StringBuilder sb = new StringBuilder();
        sb.append(CsvService.csvRow(List.of("Date", "Category", "Paid To", "Description", "Mode", "Reference",
                "Amount (" + currency + ")")));
        for (Expense e : r.vouchers()) {
            sb.append(CsvService.csvRow(List.of(
                    e.getDate(), e.getCategory(), e.getPayee(), e.getDescription(),
                    e.getPaymentMode(), e.getReference(), String.format("%.2f", e.getAmount()))));
        }
        sb.append(CsvService.csvRow(List.of("", "", "", "", "", "TOTAL", String.format("%.2f", r.total()))));
        return sb.toString();
    }

    /** "2026-09" → "Sep 26" for chart axis labels. */
    public static String monthLabel(String yyyyMm) {
        if (yyyyMm == null || yyyyMm.length() < 7) return yyyyMm;
        try {
            int m = Integer.parseInt(yyyyMm.substring(5, 7));
            String[] names = {"Jan", "Feb", "Mar", "Apr", "May", "Jun",
                    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
            return names[m - 1] + " " + yyyyMm.substring(2, 4);
        } catch (Exception e) {
            return yyyyMm;
        }
    }
}
```

- `csv(...)` — reuses `CsvService.csvRow` (Chapter 13) so quoting rules live
  in exactly one place. The TOTAL row pads six empty cells so the number
  lands in the Amount column.
- `monthLabel("2026-09")` → `"Sep 26"`: `substring(5, 7)` is the month
  digits, `substring(2, 4)` the year digits. `names[m - 1]` because January
  is index 0 but month `01` parses as 1. The try/catch returns the raw key on
  any surprise — a chart axis label must never crash the dialog.

### Step 2 — `ui/ExpenseReportDialog.java` (the plating station)

The dialog's contract with itself: *compute nothing, format everything.* All
aggregation comes from `ExpenseAnalytics`; the dialog only picks a period,
calls one of the three wrappers, and paints.

```java
package com.invoicestudio.ui;

import com.invoicestudio.model.Expense;
import com.invoicestudio.service.AppExecutors;
import com.invoicestudio.service.ExpenseAnalytics;
import com.invoicestudio.service.ExpenseAnalytics.Bucket;
import com.invoicestudio.service.ExpenseAnalytics.Report;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.geometry.Side;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URL;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.List;

/**
 * Account / Category expense report dialog — industry-standard layout:
 * period presets + KPI cards + monthly trend bar chart + category-split pie
 * + voucher table with CSV export. Both report dimensions share this shell;
 * the math lives in {@link ExpenseAnalytics} (pure, unit-tested).
 *
 * Charts reuse the app's themed .chart CSS (same as Reports view).
 */
public class ExpenseReportDialog extends Stage {
```

It **extends `Stage`** — a separate OS window, not a pane inside the main
window. `initOwner(app.getPrimaryStage())` (below) keeps it glued to the main
window so it minimizes/restore together and always renders on top of its owner.

```java
    private final com.invoicestudio.ui.StudioApp app;
    private final String dimension;   // "Account" or "Category"
    private final String filterName;  // account/category name, or null = overview

    private final ComboBox<String> periodBox = new ComboBox<>();
    private final DatePicker fromPick = UiTheme.datePicker("From");
    private final DatePicker toPick = UiTheme.datePicker("To");
    private final Label kpiTotal = UiTheme.kpiValue("₹0.00");
    private final Label kpiCount = UiTheme.kpiValue("0");
    private final Label kpiAvg = UiTheme.kpiValue("₹0.00");
    private final Label kpiTop = UiTheme.kpiValue("—");
    private final TableView<Expense> table = new TableView<>();
    private final BarChart<String, Number> trendChart;
    private final PieChart splitChart;
    private final DecimalFormat money = new DecimalFormat("#,##0.00");

    private Report current;
```

- `dimension`/`filterName` are *fixed for the life of the window* — the dialog
  is created for one account or one category and never changes target.
- `current` holds the last painted `Report` so the CSV exporter can reuse it
  without recomputing.
- All controls are `final` fields created at declaration — a deliberate style:
  by the end of the constructor every control exists, and no method can be
  called before its controls are ready.

```java
    public ExpenseReportDialog(com.invoicestudio.ui.StudioApp app, String dimension, String filterName) {
        this.app = app;
        this.dimension = dimension;
        this.filterName = filterName;

        boolean isAccount = "Account".equals(dimension);
        boolean isCategory = "Category".equals(dimension);
        String name = filterName == null ? "" : filterName;
        setTitle(isAccount ? "Account Report — " + name
                : isCategory ? "Category Report — " + name
                : "Expense Report — All Accounts & Categories");
        initModality(Modality.NONE);
        initOwner(app.getPrimaryStage());
```

- `Modality.NONE` — the window does **not** block the main window. The user
  can keep reading the register while the report floats beside it. (A modal
  report would force closing it before any other click — wrong for a
  reference window.)
- Yoda-style constant-first comparison (`"Account".equals(dimension)`) cannot
  throw if `dimension` were null.

```java
        VBox root = new VBox(14);
        root.setPadding(new Insets(18));
        root.getStyleClass().add("dialog-root");

        // --- Header + period controls ---
        HBox header = new HBox(12);
        header.setAlignment(Pos.CENTER_LEFT);
        VBox titleBox = new VBox(2);
        Label title = new Label(isAccount ? "Account Report" : "Category Report");
        title.getStyleClass().add("heading-l");
        Label sub = new Label((filterName != null && !filterName.isBlank()) ? filterName : "All expense vouchers");
        sub.getStyleClass().add("view-subtitle");
        titleBox.getChildren().addAll(title, sub);
        Region hsp = new Region();
        HBox.setHgrow(hsp, Priority.ALWAYS);

        periodBox.getItems().addAll("This Month", "Last 30 Days", "This Quarter", "This Year", "All Time", "Custom");
        periodBox.setValue("This Year");
        periodBox.setOnAction(e -> onPeriodChanged());
        fromPick.valueProperty().addListener((o, ov, nv) -> { if ("Custom".equals(periodBox.getValue())) rebuild(); });
        toPick.valueProperty().addListener((o, ov, nv) -> { if ("Custom".equals(periodBox.getValue())) rebuild(); });
```

Period presets. The two `DatePicker`s only fire a rebuild when the mode is
"Custom" — in preset modes the pickers are *outputs* of `onPeriodChanged`, not
inputs, so reacting to their changes would cause double rebuilds.

```java
        Button csvBtn = UiTheme.smallBtn("Export CSV");
        csvBtn.setOnAction(e -> exportCsv());

        Button closeBtn = UiTheme.smallBtn("Close");
        closeBtn.setOnAction(e -> close());

        header.getChildren().addAll(titleBox, hsp, new Label("Period:"), periodBox, fromPick, toPick, csvBtn, closeBtn);
        fromPick.setDisable(!"Custom".equals(periodBox.getValue()));
        toPick.setDisable(!"Custom".equals(periodBox.getValue()));
```

The pickers start disabled because the start mode is "This Year" — a visual
promise that they activate only in Custom mode.

```java
        // --- KPI row ---
        HBox kpis = new HBox(12);
        kpis.getChildren().addAll(
                UiTheme.kpiCard("TOTAL SPEND", kpiTotal, "Selected period", "accent-red"),
                UiTheme.kpiCard("VOUCHERS", kpiCount, "Entries in period", "accent-emerald"),
                UiTheme.kpiCard("AVERAGE", kpiAvg, "Per voucher", "accent-blue"),
                UiTheme.kpiCard("TOP " + (isAccount ? "CATEGORY" : "ACCOUNT"), kpiTop, "By total spend", "accent-gold"));
```

The fourth KPI's *title changes with dimension*: for an account report the
interesting "top" is which category dominates; for a category report it is
which account pays most. One label, one string ternary.

```java
        // --- Charts row ---
        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Spend (₹)");
        trendChart = new BarChart<>(xAxis, yAxis);
        trendChart.setTitle("Monthly Trend");
        trendChart.setLegendVisible(false);
        trendChart.setPrefHeight(240);

        splitChart = new PieChart();
        splitChart.setTitle(isAccount ? "Category Split" : "Account Split");
        splitChart.setPrefHeight(240);
        splitChart.setLegendSide(Side.LEFT);

        HBox.setHgrow(trendChart, Priority.ALWAYS);
        HBox.setHgrow(splitChart, Priority.ALWAYS);
        HBox charts = new HBox(14, trendChart, splitChart);
        charts.setPrefHeight(250);
```

Two charts share one row, each growing equally. `setLegendSide(Side.LEFT)` on
the pie: with many slices, a left column of legends is more readable than
labels crammed around the pie.

```java
        // --- Voucher table ---
        buildTable();
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(UiTheme.emptyState("🧾", "No vouchers in this period",
                "Adjust the period or record expenses in the register."));
        VBox.setVgrow(table, Priority.ALWAYS);

        root.getChildren().addAll(header, kpis, charts, table);
        Scene scene = new Scene(root, 1020, 720);
        URL css = getClass().getResource("/css/globalfile.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());
        setScene(scene);
        scene.setOnKeyPressed(ev -> { if (ev.getCode() == KeyCode.ESCAPE) close(); });

        onPeriodChanged(); // initial build
    }
```

- The dialog loads `/css/globalfile.css` itself — a `Stage` has its own
  `Scene`, and scenes do not inherit stylesheets from other windows. The null
  check keeps the dialog alive even if the resource path ever drifts (it
  would just look unstyled rather than crash).
- **ESC closes** — a keyboard user's expectation for any dialog.
- `onPeriodChanged()` at the end performs the first paint. Construct →
  immediate content, no extra call needed by callers.

```java
    private void buildTable() {
        TableColumn<Expense, String> cDate = new TableColumn<>("Date");
        cDate.setPrefWidth(95);
        cDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDate()));

        TableColumn<Expense, String> cCat = new TableColumn<>("Category");
        cCat.setPrefWidth(160);
        cCat.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getCategory()));

        TableColumn<Expense, String> cPayee = new TableColumn<>("Paid To");
        cPayee.setPrefWidth(160);
        cPayee.setCellValueFactory(d -> new SimpleStringProperty(
                d.getValue().getPayee().isBlank() ? "—" : d.getValue().getPayee()));

        TableColumn<Expense, String> cDesc = new TableColumn<>("Description");
        cDesc.setPrefWidth(220);
        cDesc.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDescription()));

        TableColumn<Expense, String> cAmt = new TableColumn<>("Amount");
        cAmt.setPrefWidth(110);
        cAmt.setCellValueFactory(d -> new SimpleStringProperty("₹" + money.format(d.getValue().getAmount())));
        cAmt.setStyle("-fx-alignment: CENTER-RIGHT;");

        table.getColumns().addAll(cDate, cCat, cPayee, cDesc, cAmt);
    }
```

Five columns. Empty payees render as `—` so blank cells are clearly "no data,"
not "lost data." Money is right-aligned (all amounts line up on the decimal
point — typographic convention worth copying everywhere).

> **ISSUE (faithfully preserved):** `rebuild()`'s comment says "Compute off
> the FX thread; paint when ready (big registers stay fluid)" — but the
> computation actually runs **on the FX thread**, and `AppExecutors.runOnFx`
> merely schedules the paint onto the same thread it is already on. With real
> data sizes (thousands of vouchers) the aggregation is fast enough that this
> is harmless today, but the comment over-promises. If a register ever grows
> to hundreds of thousands of vouchers, wrap the compute in
> `AppExecutors.runAsync(...)` and keep the paint-on-FX split it already has.

```java
    private void onPeriodChanged() {
        String p = periodBox.getValue();
        boolean custom = "Custom".equals(p);
        fromPick.setDisable(!custom);
        toPick.setDisable(!custom);
        LocalDate today = LocalDate.now();
        switch (p == null ? "" : p) {
            case "This Month" -> { fromPick.setValue(today.withDayOfMonth(1)); toPick.setValue(today); }
            case "Last 30 Days" -> { fromPick.setValue(today.minusDays(29)); toPick.setValue(today); }
            case "This Quarter" -> {
                int qm = ((today.getMonthValue() - 1) / 3) * 3 + 1;
                fromPick.setValue(LocalDate.of(today.getYear(), qm, 1));
                toPick.setValue(today);
            }
            case "This Year" -> { fromPick.setValue(today.withDayOfYear(1)); toPick.setValue(today); }
            case "All Time" -> { fromPick.setValue(null); toPick.setValue(null); }
            case "Custom" -> { if (fromPick.getValue() == null) fromPick.setValue(today.withDayOfYear(1));
                               if (toPick.getValue() == null) toPick.setValue(today); }
        }
        rebuild();
    }
```

The quarter math deserves a slow read: `((monthValue − 1) / 3) * 3 + 1` maps
Jan–Mar → 1, Apr–Jun → 4, Jul–Sep → 7, Oct–Dec → 10. Java's integer division
truncates, so September (9) → `(8/3)*3+1` = `2*3+1` = 7. All Time sets both
pickers to `null` — which `rebuild` translates into empty strings = no bounds.

```java
    private void rebuild() {
        // Compute off the FX thread; paint when ready (big registers stay fluid).
        String from = fromPick.getValue() != null ? fromPick.getValue().toString() : "";
        String to = toPick.getValue() != null ? toPick.getValue().toString() : "";
        final Report r;
        try {
            List<Expense> all = app.getData().getAllExpenses();
            if ("Account".equals(dimension)) r = ExpenseAnalytics.forAccount(all, filterName, from, to);
            else if ("Category".equals(dimension)) r = ExpenseAnalytics.forCategory(all, filterName, from, to);
            else r = ExpenseAnalytics.overall(all, from, to);
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
            return;
        }
        AppExecutors.runOnFx(() -> paint(r));
    }
```

The three-way dispatch mirrors the three analytics wrappers. A failed compute
logs and leaves the previous paint on screen — better than a blank report.

```java
    private void paint(Report r) {
        current = r;
        String cur = app.getData().getSettings().getCurrency();
        kpiTotal.setText(cur + money.format(r.total()));
        kpiCount.setText(String.valueOf(r.voucherCount()));
        kpiAvg.setText(cur + money.format(r.average()));

        boolean isAccount = "Account".equals(dimension);
        List<Bucket> topDim = isAccount ? r.byCategory() : r.byAccount();
        kpiTop.setText(topDim.isEmpty() ? "—" : topDim.get(0).key());

        table.setItems(FXCollections.<Expense>observableArrayList(r.vouchers()));

        // Charts
        trendChart.getData().clear();
        javafx.scene.chart.XYChart.Series<String, Number> series = new javafx.scene.chart.XYChart.Series<>();
        for (Bucket b : r.byMonth()) {
            series.getData().add(new javafx.scene.chart.XYChart.Data<>(
                    ExpenseAnalytics.monthLabel(b.key()), b.total()));
        }
        trendChart.getData().add(series);

        splitChart.getData().clear();
        List<Bucket> splitDim = isAccount ? r.byCategory() : r.byAccount();
        int shown = 0;
        double otherTotal = 0;
        for (Bucket b : splitDim) {
            if (shown < 7) {
                splitChart.getData().add(new PieChart.Data(b.key(), b.total()));
                shown++;
            } else {
                otherTotal += b.total();
            }
        }
        if (otherTotal > 0) splitChart.getData().add(new PieChart.Data("Other", otherTotal));
    }
```

The pie-chart "Other" pattern: beyond 7 slices, everything aggregates into one
slice. A 30-slice pie is unreadable; a 7+Other pie answers "who dominates?"
precisely. `kpiTop` picks index 0 of the *cross* dimension (sorted desc by
`ExpenseAnalytics`), so the biggest is always first.

```java
    private void exportCsv() {
        if (current == null) return;
        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Export Report to CSV");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("CSV Spreadsheet (*.csv)", "*.csv"));
        String base = (filterName != null && !filterName.isBlank() ? filterName.replaceAll("\\W+", "-") : "expenses")
                + "-" + LocalDate.now();
        fc.setInitialFileName(base + ".csv");
        java.io.File dest = fc.showSaveDialog(app.getPrimaryStage());
        if (dest == null) return;
        try (java.io.FileWriter fw = new java.io.FileWriter(dest)) {
            fw.write(ExpenseAnalytics.csv(current, app.getData().getSettings().getCurrency()));
            Toast.show(app.getRootPane(), "Export Successful",
                    current.vouchers() + " vouchers exported.", false);
        } catch (Exception ex) {
            Toast.show(app.getRootPane(), "Export Failed", ex.getMessage(), true);
        }
    }
}
```

`replaceAll("\\W+", "-")` turns any account name into a safe filename (letters,
digits, underscore only; everything else becomes `-`). The user cancelling the
save dialog returns `null` → polite abort. `Toast.show(..., false/true)` =
success/error styling (Chapter 9).

### Step 3 — `ui/views/ReportsView.java` (the shell)

One hundred thirty-three lines. Everything heavy is delegated.

```java
package com.invoicestudio.ui.views;

import com.invoicestudio.model.Buyer;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.service.AppFormatters;
import com.invoicestudio.ui.UiTheme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.text.DecimalFormat;

/**
 * Reports &amp; Analytics View — Version 4.0.0 (Redesigned)
 * Cohesive Obsidian &amp; Gold executive aesthetic.
 * Fully unified accounting engine integrating all Bills and Transactions.
 *
 * 1. Outstanding &amp; Aging Report (with KPIs, search, risk indicators, statement drilldown)
 * 2. Buyer Statement &amp; Ledger (Opening balance, Debits, Credits, Running balance, CSV &amp; Print)
 * 3. Trouser Movement (Daily, weekly, and monthly velocity across CC and CS books)
 * 4. Sales Trends (Monthly billed sales vs collection rate efficiency)
 * 5. Item Movement (Catalog item sales velocity across all invoices)
 * 6. Item Performance (Top revenue and volume items)
 * 7. Category Breakdown (Category revenue &amp; pieces share)
 * 8. GST / Tax Summary (Taxable, CGST, SGST, IGST, and GSTR-1 CSV export)
 * 9. Transport Performance (Freight and parcel analysis per transporter)
 *
 * Shell role (skill rule 5.2): owns header, tab navigation and cross-view
 * selection; all tab content is built by {@link ReportsBuilders}.
 */
public class ReportsView extends BorderPane {

    private final StudioApp app;
    private final TabPane tabPane = new TabPane();
    /** Shared cached Indian-grouped money format (skill 2.1), handed to the tab builders. */
    private final DecimalFormat currencyFmt = AppFormatters.inrFormat();
    private final ReportsBuilders builders;

    public ReportsView(StudioApp app) {
        this.app = app;
        this.builders = new ReportsBuilders(this, app, currencyFmt);
        setPadding(new Insets(18, 24, 20, 24));
        getStyleClass().add("bg-app");

        setTop(createHeader());
        setCenter(createTabPane());
    }
```

`BorderPane` gives the classic top/center layout: header strip on top, tab
content filling the rest. The one `DecimalFormat` is created once and *handed
to* the builders — every tab shares one formatter instance instead of each
building its own.

```java
    public void refresh() {
        int selIdx = tabPane.getSelectionModel().getSelectedIndex();
        buildTabs();
        tabPane.getSelectionModel().select(Math.max(0, selIdx));
    }
```

Refresh = rebuild all tabs, then restore the selection index. Note what this
*costs*: any per-tab state the user had set (a selected buyer in the statement
combo, a typed search) is lost, because the tabs are brand-new nodes. The
epoch system (Ch 9) calls `refresh()` only when that view's data actually
changed, so the annoyance is rare — but it is real, and we flag it honestly:

> **ISSUE (faithfully preserved):** `refresh()` rebuilds every tab, discarding
> per-tab UI state (selected buyer, search text, date filters). A surgical
> refresh would ask each builder to re-read data into existing tables. Today
> the epoch system makes full rebuilds infrequent, so the trade was accepted.

```java
    public void selectBuyerStatement(String buyerId) {
        tabPane.getSelectionModel().select(1); // tab 1 is Buyer Statement
        ComboBox<Buyer> combo = builders.statementBuyerCombo();
        if (combo != null && buyerId != null) {
            for (Buyer b : combo.getItems()) {
                if (buyerId.equalsIgnoreCase(b.getId())) {
                    combo.setValue(b);
                    break;
                }
            }
        }
    }
```

The cross-tab drilldown: switch to tab index 1, then find the buyer by id
(case-insensitive) and set the combo value — which fires the combo's listener
inside `ReportsBuilders` and loads the ledger automatically. *Setting a value
drives the UI; the shell never pokes the ledger directly.*

```java
    private Node createHeader() {
        HBox box = new HBox(16);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0, 0, 16, 0));

        VBox titleBox = new VBox(2);
        Label title = new Label("Reports & Financial Ledger");
        title.getStyleClass().add("heading-l");
        Label subtitle = new Label("Unified accounting analytics, buyer statements, aging analysis, and logistics performance.");
        subtitle.getStyleClass().add("kpi-subtext");
        titleBox.getChildren().addAll(title, subtitle);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button refreshBtn = UiTheme.secondaryBtn("↻ Refresh Data");
        refreshBtn.setTooltip(new Tooltip("Reload latest bills and transactions"));
        refreshBtn.setOnAction(e -> refresh());

        Button newTxBtn = UiTheme.goldBtn("+ New Transaction");
        newTxBtn.setOnAction(e -> app.showTransactions());

        box.getChildren().addAll(titleBox, sp, refreshBtn, newTxBtn);
        return box;
    }
```

A `Region` with `HGrow ALWAYS` is the standard *spring* that pushes right-side
buttons to the far edge.

```java
    private Node createTabPane() {
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add("clean-tab-pane");
        buildTabs();
        return tabPane;
    }

    private void buildTabs() {
        tabPane.getTabs().clear();
        tabPane.getTabs().add(new Tab("Outstanding Report", buildOutstandingReport()));
        tabPane.getTabs().add(new Tab("Buyer Statement & Ledger", buildBuyerStatementReport()));
        tabPane.getTabs().add(new Tab("Trouser Movement", buildTrouserMovementReport()));
        tabPane.getTabs().add(new Tab("Sales Trends", buildSalesTrendsReport()));
        tabPane.getTabs().add(new Tab("Item Sales & Movement", buildItemMovementReport()));
        tabPane.getTabs().add(new Tab("Category Breakdown", buildCategoryBreakdownReport()));
        tabPane.getTabs().add(new Tab("GST / Tax Summary", buildGstTaxReport()));
        tabPane.getTabs().add(new Tab("Transport Performance", buildTransportPerformanceReport()));
    }

    // --- Delegations to ReportsBuilders (behavior preserved) ---
    Node buildOutstandingReport() { return builders.buildOutstandingReport(); }
    Node buildBuyerStatementReport() { return builders.buildBuyerStatementReport(); }
    Node buildTrouserMovementReport() { return builders.buildTrouserMovementReport(); }
    Node buildSalesTrendsReport() { return builders.buildSalesTrendsReport(); }
    Node buildItemMovementReport() { return builders.buildItemMovementReport(); }
    Node buildCategoryBreakdownReport() { return builders.buildCategoryBreakdownReport(); }
    Node buildGstTaxReport() { return builders.buildGstTaxReport(); }
    Node buildTransportPerformanceReport() { return builders.buildTransportPerformanceReport(); }
}
```

`TabClosingPolicy.UNAVAILABLE` — fixed tabs, no close buttons (these are
navigation, not documents). The eight package-private delegation methods give
`ReportsBuilders` access back to the shell (`owner.selectBuyerStatement(...)`) 
while keeping `builders` private. Note the doc list says 9 sections; tabs 5
and 6 (Item Movement / Item Performance) share one builder method pair
(`buildItemAnalytics(false/true)`) but only the movement tab is wired in
`buildTabs()` — the performance variant exists and is used by the chatbot's
model menu (Ch 19) but is not a visible tab. **GAP flagged:** the "Item
Performance" tab title in the docstring has no tab of its own.

<!-- CH14-CONT -->

