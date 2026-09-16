# Codebase Optimization & Split Report — 2026-09-16

**Scope:** whole repo (`invoice-studio-desktop`, ~46k lines JavaFX 21 / Java 17 / SQLite)
**Skill applied:** `.freebuff/skills/javafx-best-practices/SKILL.md`
**Verification contract:** baseline **230/230 tests green** → re-run `mvn test` after every seam → final **230/230 green, BUILD SUCCESS**. No test weakened or deleted; one harness (`RulerVerify`) extended to exercise the new flush path.

---

## 1. God-class splits (before → after)

| File | Before | After | New collaborators |
|---|---|---|---|
| `TemplateDesigner.java` | 7,735 | 7,488 | `VectorGeometryUtil` (382 — pure SVG-path/vertex/bezier math, unit-tested), `DesignerState` (107 — undo/redo/clipboard session state) |
| `McpToolRegistry.java` | 1,940 | 1,774 | `McpArgs` (76 — arg parsing/validation), `McpProjections` (233 — JSON projections) |
| `SettingsView.java` | 1,443 | 1,401 | `SettingsFieldSupport` (74 — buyer-field rows/expand/collapse) |
| `PdfExportService.java` | 1,404 | 1,307 | `PdfTextDraw` (141 — text/wrap/format drawing) |
| `CreateBillView.java` | 1,384 | 1,302 | `LineItemsLayout` (124 — stateless column sizing) |
| `ReportsView.java` | 1,317 | **131** | `ReportsBuilders` (1,264 — all nine report tabs) |
| `StudioApp.java` | 1,011 | **626** | `SidebarController` (268), `AppShortcuts` (99), `UserProfilePill` (85) |

**New shared infrastructure (the skill's mandates, materialized):**

| Class | Purpose |
|---|---|
| `service/AppExecutors` | Single managed executor pool; `AppExecutors.shutdownAll()` wired into `Application.stop()` — no more orphan ad-hoc threads |
| `service/AppFormatters` | Cached `DecimalFormat`/`DateTimeFormatter` instances — replaces per-call `new DecimalFormat(...)` (expensive + garbage churn) |
| `service/AppLog` | One logging surface replacing scattered `printStackTrace` across the DAO layer |
| `ui/views/VectorGeometryUtil` | Stateless geometry — now directly unit-testable without the designer |

## 2. Gesture-coalescing optimization (the ruler/canvas case)

**Problem (observed):** every Ctrl+scroll notch fired `rebuildGridForZoom()` → full ruler rebuild: clear pane + recreate hundreds of `Line`/`Label` nodes *mid-gesture*. Dozens of full scene-graph allocations per second of scrolling — the classic jank source.

**Fix (industry-standard coalesce-then-refine):**
- Zoom transform stays **live** (cheap — scale transform only);
- the expensive static ruler re-render is **coalesced**: trailing-edge `PauseTransition` (~120 ms) fires **once** when the gesture settles, and re-fires reset the timer;
- full-render paths still sync synchronously (correctness preserved);
- `RulerVerify` harness now flushes the pending repaint before asserting, so it tests the final settled state and exercises the new flush path.

**Also grounded from research** (foojay.io high-performance-rendering, JavaFX concurrency docs): canvas is the slow path for per-pixel work (grid already canvas-based from a prior pass); scroll handlers should coalesce redraws per frame or on settle; pre-render static layers offscreen.

## 3. Deliberate stops (where "even god can't optimize further" means *stop*)

- **`TemplateDesigner` at 7,488:** remaining sections share ~108 private methods and are pinned by 8 reflective verify harnesses (`RulerVerify`, `ZoomScrollVerify`, …). Two more seams were identified but judged higher-risk than reward — **cohesion beats file size** (skill §5.2).
- **`ReportsBuilders` at 1,264:** nine sibling tab builders under section banners. Splitting siblings-apart is fragmentation, not extraction.

## 4. Verification timeline

```
baseline                     230/230  BUILD SUCCESS
+ quick-win infra             230/230  (compile + test)
+ TemplateDesigner split      230/230
+ McpToolRegistry split       230/230
+ SettingsView / Pdf / Bill   230/230
+ ruler coalescing            230/230  (+ RulerVerify flush)
+ ReportsView split           230/230
+ StudioApp split (final)     230/230  BUILD SUCCESS
```

## 6. Whole-codebase compliance audit (post-split pass)

An honest audit found the split pass had covered architecture but left mechanical rule violations. All closed, verified green after each batch:

| Rule | Before | After |
|---|---|---|
| Per-call `DateTimeFormatter.ofPattern` | 16 sites (Dashboard2View ×10, DashboardView ×6) | 0 — cached `static final` constants |
| `DatePicker` fallback parses (UiTheme) | 5 patterns re-parsed per keystroke | pre-built `FALLBACK_DATE_FORMATS` array |
| Per-instance `new DecimalFormat("#,##,##0.00")` | 3 views | `AppFormatters.inrFormat()` shared cache |
| `printStackTrace` | 2 (ExpenseDao) | 0 — `AppLog.error` |
| Truly silent `catch (Exception …) {}` | 146 across `db`/`service`/`ui`/`mcp` | 0 — 151 `AppLog.debug(ex)` hooks, no-op unless `-Dapplog.debug=true` |

**Executor inventory verdict:** the remaining `Executors.new*` sites are compliant — each is a named, purpose-built pool per subsystem (`google-oauth`, `invoicestudio-tspl-spool`, MCP HTTP pool), the pattern skill §1.4 endorses.

## 7. Where to go next

1. **TemplateDesigner designer-mode extraction** — extract toolbar/property-panel builders into a `DesignerChrome` collaborator once a headless smoke harness exists (risk currently carried by reflective harnesses only).
2. **`viewCache` eviction** — StudioApp caches every visited view forever; an LRU (size ~8) caps memory on long sessions.
3. **Headless smoke harness in CI** — skill §9: launch under Xvfb, navigate every view; the single highest-value hardening step for this app.
4. **`catch (Exception ignored)` audit** — ~40 silent swallows remain; route through `AppLog` with debug level.
