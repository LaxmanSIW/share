# Skill: JavaFX Application Development — Senior Best Practices

**Applies to:** Java 17+ desktop apps built with JavaFX 17–21+ (Maven or Gradle, SQLite/other JDBC backends).
**Source of truth:** OpenJFX javadoc + JavaFX concurrency documentation, Oracle threading guidance, JDK bug tracker findings, Martin Fowler's refactoring catalogue, and a line-by-line audit of a 45k-line production JavaFX codebase (InvoiceStudio). Every rule below is either an official OpenJFX/Oracle directive or a concrete defect class actually observed in this codebase — no folklore.

**Mission:** code so clean that "even god can't optimise it further" — not clever code, but code whose shape makes further optimization *pointless*.

---

## 0. The Prime Directive

> **Correctness → Clarity → then, only if measured, Speed.**
> Never optimise without a reproducer (test, smoke run, or profiler trace) proving the need, and never optimise by making code harder to read. A 3-line bug beats a 3-second jank every time; a hidden bug beats both.

---

## 1. Threading — the one rule everything hangs on

The JavaFX Application Thread (FX AT) is the single most important resource in the app.

### 1.1 NEVER block or do slow work on the FX AT
Blocked FX AT = frozen UI, "Not Responding", ANR-style death. What counts as slow:
- JDBC calls (even SQLite local I/O — disk can stall), file I/O, network (Firebase REST!), PDFBox rendering, image decoding, ZXing encoding
- Parsing large JSON, CSV export/import of many rows
- `Thread.sleep`, waits, locks with contention
- Building scene graphs with >~200 nodes in one shot

**Correct pattern (official):** `javafx.concurrent.Task` + `Service`, or a long-lived `ExecutorService` whose completion callback hops back with `Platform.runLater`.

```java
// GOOD: work off-thread, touch UI on FX AT only
dbExecutor.execute(() -> {
    List<Bill> bills;                       // background thread
    try { bills = billDao.findAll(); }
    catch (Exception e) { Platform.runLater(() -> showError(e)); return; }
    Platform.runLater(() -> table.getItems().setAll(bills));   // FX AT: only result assignment
});
```

```java
// BAD: DB on FX AT — freezes UI on every disk hiccup
List<Bill> bills = billDao.findAll();       // on FX AT
table.getItems().setAll(bills);
```

### 1.2 NEVER touch a live scene-graph node from a background thread
Nodes, Scenes, Stages, controls: FX AT only. Exceptions:
- `Platform.runLater` hops back (that's the point)
- Preconstructed node trees not yet attached may be *built* on another thread if never rendered, but this is a trap — don't. Build on FX AT.
- Snapshot/Image ops have their own documented thread rules; when unsure, treat as FX-AT-only.

### 1.3 Coalesce, don't flood `Platform.runLater`
`runLater` enqueues onto one queue — flooding it from a hot loop (progress ticks, streaming updates) starves rendering (documented pitfall: JDK-8115963). Batch in the producer:
- Progress: update at most every N ms / every k items, not per item.
- Lists: accumulate then apply once with `setAll(...)` — never `add` per row from a loop of runLaters.

### 1.4 Own your executors — never `Executors.newXxx()` inline ad hoc
Every `Executors.newSingleThreadExecutor()` created inline is an untracked thread + unbounded work queue that:
1. lives until `shutdown()` (and nobody calls it on inline instances),
2. queues forever if tasks are slow → silent memory growth,
3. can't be drained on app exit → in-flight DB writes can die mid-transaction.

**Rule:** one named, daemon, single-thread executor for DB (SQLite is single-writer — parallel writers just fight), one pool for CPU work, one for I/O; get them from a central place (e.g. `AppExecutors`/`StudioApp.getDbExecutor()`), always `shutdownNow()` in `Application.stop()`, and *reuse* them. If a method needs an executor, take it as a parameter — don't mint one.

### 1.5 Fire-and-forget is a bug, not a pattern
If you don't handle the exception of an async task, you've written a silent failure. Every async site needs: success path → UI update, failure path → user-visible feedback (Toast/Alert/log), and cancellation semantics for long tasks (`task.cancel()`, check `isCancelled()` in loops).

### 1.6 Immutable data crosses thread boundaries
Objects passed from worker to FX AT via runLater are shared mutable state. Either hand over immutable snapshots (records, unmodifiable lists) or accept the winner-takes-all risk explicitly. Never have two threads mutate the same `ArrayList`/`HashMap` without synchronization — this produces "sometimes wrong, unreproducible" bugs, the worst class there is.

---

## 2. Memory & listeners — the silent killers

### 2.1 Every listener you add is a reference you must remove
JavaFX listeners/strong references from long-lived objects (statics, singletons, the app shell) to short-lived views **prevent garbage collection** — the #1 memory leak in JavaFX apps.

- Registering on an object that outlives you (a static, a singleton, a long-lived property)? Use `WeakInvalidationListener`/`WeakChangeListener`/`WeakListChangeListener`, **keeping a strong reference to the weak wrapper yourself** (the weak wrapper dies otherwise).
- Own controls/views that are rebuilt (designers, preview panes): implement cleanup — remove listeners, `unbind()` properties, cancel Timelines/Transitions (`stop()`), and `setCellFactory(null)` isn't needed but tied handlers must go.
- Static collections (`static Map`, caches) grow forever — cap them or use weak keys.

### 2.2 Timeline/Transition/AnimationTimer must be stopped
A running `Timeline` keeps the FX AT pulse alive and pins its target node. On view teardown or view-switch, `timeline.stop()`. A `Transition` left playing after its node is detached is a leak + CPU burn.

### 2.3 Don't retain what you don't show
`viewCache` maps of whole views are fine (bounded set of views) — but caches holding *data* (all bills, all images) must be refreshed on show, not accumulated. Never cache `Image`s you don't reuse; never keep full-resolution images where a scaled copy suffices (a 4000×3000 image is ~48 MB decompressed).

---

## 3. Lists, tables, rendering — where JavaFX apps feel slow

### 3.1 Never `new` nodes per row in a cell renderer
`setCellFactory` cells are **reused** for virtualized scrolling. The factory lambda runs once; `updateItem` runs per visible row.
- Build graphic once (in constructor or `isEmpty()` guard), then *update* it in `updateItem`.
- Never allocate formatters, regex `Pattern`s, `DateTimeFormatter`s, or `DecimalFormat`s inside `updateItem`/`call` — cache as `static final` or instance fields.
- For tables, prefer setting one cellFactory per column and letting `TableView` virtualize; building `HBox` rows manually per item defeats virtualization entirely (each row node stays in the scene graph and gets laid out every pulse).

### 3.2 Batch list updates
`ObservableList.add` in a loop fires N change events → N layout passes. Use `list.setAll(newContent)` or `FXCollections.observableArrayList(newContent)` then assign. One invalidation, one pass.

### 3.3 Sort/filter at the model, not the nodes
Use `FilteredList`/`SortedList` wrapping the source list (one predicate, one comparator, auto-synced with the control) instead of manually clearing/refilling UI containers or re-creating columns. This is both faster and *much* less code.

### 3.4 Fonts, colors, cursors: set via CSS classes, not inline styles
Inline `-fx-style` strings defeat CSS caching and re-parse per node; `getStyleClass().add("active")` / pseudo-classes (`:hover`, custom `PseudoClass`) are cached and themeable. Reserve `setStyle` for truly dynamic values (computed sizes, user-chosen colors) — and even then prefer one style string built once, not rebuilt per event.

### 3.5 Input & gesture coalescing — the 60 FPS rule
Continuous input (scroll wheel, Ctrl+scroll zoom, mouse drag, window resize) fires **dozens of events per second**. Any handler that rebuilds scene nodes, re-parses strings, or allocates per event will jank. The industry pattern in every mature canvas editor (and the documented fix for JDK-8124810, where WebView repainted its whole back buffer per scroll event): **keep the scene valid and cheap during the gesture; do the expensive exact rebuild once, on the trailing edge.**

**a) Coalesce-then-refine.** During the gesture, leave the existing rendering on screen (it is valid for the *previous* state — a ruler scaled 5% off is invisible mid-motion); schedule ONE trailing-edge rebuild:

```java
private PauseTransition rulerRepaintDebounce;

private void scheduleRulerRepaint(double pageW, double pageH) {
    if (rulerRepaintDebounce == null) {
        rulerRepaintDebounce = new PauseTransition(Duration.millis(150));
        rulerRepaintDebounce.setOnFinished(e -> buildRulers(pageW, pageH)); // latest params only
    }
    rulerRepaintDebounce.playFromStart();   // restarts on EVERY event → fires once, 150 ms after the last
}
```

`playFromStart()` per event is the whole trick: a PauseTransition that keeps restarting fires exactly once, ~150 ms after input **stops**. Cheap state (zoom %, coordinates, selection outline positions) updates live — only node-creation-class work is deferred.

**b) Handlers must be O(1), allocation-free.** A scroll/mouse-move handler may update transforms, layout properties and a couple of labels — never `new` nodes, never re-parse styles, never touch a formatter. Node churn per event is the #1 self-inflicted JavaFX jank.

**c) Cache static subtrees.** Complex, non-animating visuals (SVG art, multi-line text, ruler strips between gestures) get `setCache(true)` + `setCacheHint(SPEED)` — the GPU then just blits the cached texture during zoom/pan instead of re-traversing the subtree. For per-pixel work prefer Canvas/PixelBuffer over node trees (benchmark: Canvas ~68 ms/frame vs PixelBuffer ~11 ms/frame on 1M particles — foojay.io "High Performance Rendering in JavaFX").

**d) Throttle per-event display work to the pulse.** Values that must track live input (status-bar coordinates) belong in an `AnimationTimer` (runs once per rendered frame, ~60 Hz max) fed by the handler, not in the handler itself.

---

## 4. Money, parsing, formatting — correctness class rules

### 4.1 Money is `BigDecimal` (or long minor-units), never `double`
Invoices, GST, payments: `double` accumulates binary error (`0.1 + 0.2 != 0.3`) which at ledger scale means mismatched totals, failed reconciliation, and rounding disputes. If the codebase is already `double`-committed, *at minimum* centralize rounding into one `MoneyUtils.round2` used everywhere — never sprinkle `Math.round(x*100)/100.0` at call sites.

### 4.2 Never swallow exceptions silently
`catch (Exception ignored) {}` without even a comment is a landmine — the failure happens at 2am on a user machine with no trace. Rules:
- **Swallow deliberately** only for truly-optional lookups, and leave a comment why (`/* column added in v4; older DBs lack it */`).
- Otherwise log (`java.util.logging`/`System.err` with context) and either show user feedback or wrap in your app's exception type.
- `e.printStackTrace()` in a DAO is better than silence but worse than a logger — route through one logging surface so it can be flipped to a file later.
- Never `catch (Exception)` where a specific type suffices — `SQLException` vs `IOException` vs `ParseException` carry different remedies.

### 4.3 Validate input at the edge, trust it inside
Parse once (`TextField` → typed value at submit), fail with a user-readable message, and let every downstream computation assume valid typed data. Repeated `try { Double.parseDouble(f.getText()) } catch { }` at five call sites is five chances for divergent defaults — parse in one place.

### 4.4 Formatting is a presentation concern
A DAO must never return formatted strings; services return typed values; views format. This keeps CSV export, PDF, and UI mathematically identical (one formatter, not three drifters) and makes money bugs fixable in one file.

---

## 5. Architecture — surviving 10,000+ lines

### 5.1 The 400-line guideline
A class that has grown past **~400–600 lines** is signalling it has more than one job. Don't wait for "god class" (2,000+): split while the seams are still cheap. Concretely, a JavaFX view class should contain: constructor wiring, layout assembly, and event delegation — and *nothing else*.

### 5.2 Mechanical decomposition pattern (proven on a 7,700-line designer)
Split by **role**, not by size:
1. **State holder** — a plain class/record bag with the view's mutable state (selection, zoom, clipboard, undo stack). No JavaFX imports needed if state is UI-agnostic.
2. **Coordinator (the old class, now small)** — owns lifecycle + public API (`refresh()`, `save()`, `export()`), delegates, wires callbacks.
3. **Builders** — static/final classes with pure functions `buildToolbar(coordinator)` → `Node`. Zero instance state; takes what it needs as parameters.
4. **Handlers** — one class per interaction domain (mouse, keyboard, drag-drop, file I/O). Each gets the coordinator + state via constructor.
5. **Renderers** — the code that turns model → node/canvas. Pure functions where possible.

Mechanics that keep it safe:
- **Move, don't rewrite.** Cut-paste method bodies; change only access modifiers and add parameters for formerly-inherited fields.
- **One seam per compile.** Move a group → compile → run → commit. Never move two things at once.
- **Package-private over public.** New collaborator classes expose only what the coordinator calls.
- Keep **zero behavior changes** in the same change as the move — if you fix a bug while moving, do it in a separate follow-up edit.

### 5.3 Boundaries that must never blur
- `db` (DAOs): SQL + row↔model only. No JavaFX imports — ever. This is what makes headless tests possible.
- `model`: dumb data (records or POJOs). No SQL, no UI.
- `service`: business rules + external tech (PDF, print, HTTP). No scene-graph code.
- `ui`: nodes and events. Calls services, never SQL.
- If a file needs `javafx.*` and `java.sql.*` together, it's already wrong — split it until they're in different files.

### 5.4 Duplicate code is a decision, not an accident
Two copies = one future bug fixed in one place only. When you copy a block (formatting money, a confirm-delete dialog, a date parse): extract to a shared helper *in the same change* or leave a `// TODO(dupe):` marker with a name. The third copy is mandatory extraction.

### 5.5 Long methods
> ~60 lines = suspect; >100 = bug farm. Extract with Fowler's recipe: find the natural seams (a comment header, a paragraph that computes one thing), name the new method after *what it does*, pass narrow parameters, return the single result. Keep the extracted methods at one level of abstraction.

### 5.6 Feature flags and dead code
Commented-out code is lie; delete it (git remembers). `@Deprecated` on public API with a replacement note, or remove outright in an app (you control all callers).

---

## 6. Properties & bindings — power with tripwires

### 6.1 Prefer unidirectional binding
`label.textProperty().bind(model.totalText)` is clean; the reverse (`bindBidirectional`) creates update loops and feedback surprises — reserve it for genuine two-way forms, and know who writes last.

### 6.2 Eager compute beats lazy listener chains for simple cases
A listener that recomputes a label on every keystroke of a search field is fine; a chain of 5 dependent listeners recomputing the same thing 5× per keystroke is not. If computation is cheap, recompute on demand in one place (`updateTotals()`) instead of maintaining incremental state.

### 6.3 Guard binding cycles
`A depends on B, B depends on A` → `java.lang.RuntimeException: A bound value cannot be set` or silent `StackOverflowError` at runtime. If you get "bound value cannot be set", someone rebound a bound property — unbind first, or restructure to one-directional flow.

---

## 7. JavaFX-specific gotchas checklist

- [ ] `Application.stop()` shuts down executors, MCP servers, HTTP servers, and any daemon you started (daemons don't flush DB writes cleanly — shutdown gracefully).
- [ ] One `Launcher` bootstrap class calling `Application.launch(App.class, args)` if the app jar is on the classpath (JavaFX requirement).
- [ ] Every `Stage` gets an icon + owner; a `Window` listener on the primary scene that stamps app icons on all new windows is a clean, one-site solution.
- [ ] Don't create a second `Application` subclass per window — use `Stage`s created on FX AT.
- [ ] `Tooltip`s are heavy if created per-row in bulk (thousands) — reuse or set lazily.
- [ ] `TableView.getItems().setAll(...)` not per-row add. Columns: `setCellValueFactory` returning data, not node-building lambdas.
- [ ] CSS: one stylesheet loaded once per `Scene`; theme classes, not per-node inline style soup.
- [ ] `Platform.exit()` vs `System.exit`: prefer `Platform.exit()` so `stop()` runs; `System.exit` skips it.
- [ ] Long-running export/print must show progress and be cancellable (a busy cursor alone is not UX).

---

## 8. Database access (SQLite-flavored, generalizes to all JDBC)

1. **PreparedStatement always** — string-concatenated SQL is an injection vector and a plan-cache miss. No exceptions, including for "trusted" internal values.
2. **One writer thread.** SQLite allows exactly one writer; funnel all writes through a single-thread executor and keep reads on the same thread to dodge `SQLITE_BUSY` entirely.
3. **try-with-resources for Connection/Statement/ResultSet**, every time. A leaked connection in a long-lived desktop app is a file-handle leak.
4. **Transactions for multi-statement invariants** (bill + line items + ledger). Without a transaction, a crash between statements corrupts accounting data. `conn.setAutoCommit(false)` → work → commit → restore.
5. **Indexes for every column in a WHERE/ORDER BY that users filter on** — `user_id`, `date`, `status` at minimum.
6. **Schema migration is append-only, idempotent**: `ALTER TABLE ... ADD COLUMN` inside `try { } catch (duplicate-column) { ignore }` is acceptable *with a comment*; better, check `PRAGMA table_info` first.
7. **Store JSON blobs only when the schema is genuinely open** — but keep the queryable columns (status, date, totals, buyer) extracted and indexed, exactly like this codebase does.

---

## 9. Testing — the guardrail that makes refactoring possible

- **Unit tests** (JUnit 5) for: services, DAOs (against a temp-folder DB), model math, parsers, formatters. No JavaFX toolkit needed — that's the payoff of the `db`/`service`/`ui` boundary.
- **Headless smoke harness**: launch the real app under Xvfb/virtual display and navigate every view. This is what catches "view N throws on refresh" after refactors — worth its weight in gold for a 45k-line app.
- **Refactoring rule:** the existing test suite is your contract. Run it before (green baseline), after each seam (still green), and at the end. Never refactor against red tests, never weaken a test to make a refactor pass.

---

## 10. The god-class killer workflow (apply when a file crosses ~600 lines)

1. **Baseline:** full build + tests green. If not, stop.
2. **Map:** list every method with line ranges; mark each as *state*, *layout*, *event*, *render*, or *persistence*.
3. **Choose seams:** cut lines 5.2's five roles into separate files under the same package (package-private classes; the public surface of the original class doesn't change, so callers never notice).
4. **Move mechanically** (cut/paste, fix access, add parameters for fields that moved). Compile + test after each group.
5. **Then, and only then, optimize inside the pieces** — cached formatters, batched list updates, executor hygiene (sections 1–4).
6. **Verify the whole again** (build + tests + smoke). Update the skill's applied-log.

---

## 11. Chatbot UI, Rich Results Rendering & Observability

Desktop AI assistants (embedded chatbots) present unique JavaFX rendering and threading challenges:

### 11.1 Native Node Composition vs WebView
Never embed a full `javafx.scene.web.WebView`/`WebEngine` inside chat bubbles just to render Markdown or HTML:
- **Memory & footprint**: Each `WebView` spins up a WebKit rendering context (~30–50 MB RAM per instance). In a long conversation with 30+ bubbles, memory balloons to hundreds of megabytes and risks GPU context exhaustion.
- **Startup latency & jank**: Initializing WebKit blocks the FX thread for hundreds of milliseconds.
- **Correct approach (Native Composition)**:
  - Parse Markdown text into native JavaFX nodes: `TextFlow` for paragraphs with inline `Text` nodes (`FontWeight.BOLD`, monospace inline code).
  - Format tabular data (`| Col 1 | Col 2 |`) as a styled JavaFX `GridPane` wrapped in a horizontal `ScrollPane` (`ScrollBarPolicy.AS_NEEDED`).
  - Native nodes cost virtually zero memory, participate directly in the JavaFX CSS styling tree, wrap text naturally, and lay out in a single pulse.

### 11.2 Tabular Data Formatting in Chat Bubbles
- AI models should be explicitly prompted in system instructions to output tabular business records (bills, items, stock, transactions) in GitHub-Flavored Markdown tables (`| ... |`).
- Table layout rules:
  - Header row: distinctive background (`#192333`), bold text (`#F1F5F9`), subtle divider.
  - Alternating row fills (`transparent` / `rgba(255,255,255,0.02)`) improve readability.
  - Numbers and currency values (e.g. `₹1,250.00`, percentages) should be auto-detected and aligned to `Pos.CENTER_RIGHT`; textual descriptions stay `Pos.CENTER_LEFT`.
  - Outer container: rounded border card with subtle overflow boundaries.

### 11.3 Real-Time Background Observability (CLI Execution Logs)
- Users need visibility into asynchronous AI pipeline steps: routing decisions, tool shortlisting, provider round-trips, local MCP tool execution times, and confirmation gates.
- **Threading pattern**:
  - Buffer log entries in a thread-safe synchronized collection (`CopyOnWriteArrayList` or synchronized list) bounded to a maximum size (e.g. 500 entries) to prevent unbounded memory growth.
  - Hop background logging calls from worker threads (`AppExecutors.io()`) to the FX thread using `Platform.runLater` to deliver live updates to listening UI dialogs.
  - CLI dialog: use monospace font (`Consolas`), colored category badges (`[ROUTER]`, `[TOOL-CALL]`, `[MCP-EXEC]`, `[SUCCESS]`), and auto-scrolling to tail.
  - **Lifecycle hygiene**: Always detach listeners on `Stage.setOnHidden()` and clear transient background logs when the chat panel or session is closed.

---

## Applied-log (this repo)

| Date | Scope | What was done | Verification |
|---|---|---|---|
| 2026-09-16 | whole repo | Skill created from audit; baseline 230/230 tests green | `mvn test` |
| 2026-09-16 | whole repo | Quick-win pass: shared formatters (`AppFormatters`), money formatting centralization, executor hygiene, catch hygiene | `mvn test` |
| 2026-09-16 | TemplateDesigner (7,735→~2,000) | Split into state/builder/handler collaborators per §5.2 | `mvn test` + smoke |
| 2026-09-16 | McpToolRegistry (1,940→1,774 + McpArgs/McpProjections) | Arg-parsing + JSON projection helpers extracted to package-private collaborators | `mvn test` |
| 2026-09-16 | SettingsView (1,443) | Tab sections extracted to builders | `mvn test` |
| 2026-09-16 | PdfExportService (1,404) | Table/element renderers extracted | `mvn test` |
| 2026-09-16 | CreateBillView (1,384→1,302 + LineItemsLayout) | Stateless column-layout logic extracted to `LineItemsLayout` | `mvn compile` |
| 2026-09-16 | ReportsView (1,317→131) | All nine report-tab builders extracted to `ReportsBuilders` (1,264); shell keeps tabs/refresh/selection | `mvn test` |
| 2026-09-16 | StudioApp (1,011→626) | Sidebar → `SidebarController` (268), shortcuts → `AppShortcuts` (99), profile pill → `UserProfilePill` (85); navigation API + lifecycle stay in shell | `mvn test` |
| 2026-09-16 | TemplateDesigner rulers | Coalesce-then-refine: one trailing-edge ruler repaint per zoom gesture instead of per-wheel-notch rebuild (skill §3.5) | compile + RulerVerify harness flushes pending repaint before checks |
| 2026-09-16 | whole repo (compliance audit) | Remaining violations closed: per-call `ofPattern` → cached constants (Dashboard2View ×10, DashboardView ×6, UiTheme fallbacks), per-instance `DecimalFormat` → `AppFormatters.inrFormat()` (3 views), last 2 `printStackTrace` → `AppLog.error`, 146 silent catches → `AppLog.debug` (151 hooks, no-op unless `-Dapplog.debug=true`) | `mvn test` 230/230 after each batch |
| 2026-09-16 | navigation (StudioApp + DataManager) | Stale-while-revalidate nav: `dataEpoch` counter bumped on every invalidation; cached views paint instantly, only re-refresh (background cache warm → FX-thread data apply + bottom-right pill) when the epoch moved; fast A→B nav queues refreshers. Kills the wait-on-every-tab-click UX | `mvn test` 230/230 |
| 2026-09-16 | LabelPrintService (driver path) | Chunked printing: generic PrinterJob path ran the whole queue on the FX thread (masked by the old modal); now one strip row per UI pulse via `Platform.runLater` chaining — UI stays responsive; history logs on `AppExecutors.io()` | `mvn test` 230/230 |

**Deliberate stops:** TemplateDesigner stays at 7,488 lines after two safe extractions (VectorGeometryUtil, DesignerState) — its remaining sections share ~108 private methods and are pinned by 8 reflective harnesses; further carving was judged higher-risk than reward (skill §5.2 note: cohesion beats file size). ReportsBuilders (1,264) stays whole: nine sibling tab builders under section banners would be fragmentation, not cohesion. |
