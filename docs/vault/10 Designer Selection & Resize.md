# 10 — Designer Selection & Resize (and four usability bugs)

Decision record for the 2026-09-23 bug report from real shop usage: broken
duplicate, stale gallery after rename/save, selection-box resize behaving
differently per object, and the text double-click editor changing size and
colors. It also records (documentation-only, no code change) the DPI bug seen
on a second laptop.

## 1. The bug report (verbatim intent)

1. **Copy/duplicate** of a template did not create a template with the same
   values: a barcode template became a normal bill template, and all print
   settings were lost.
2. **Rename did not work**; edits sometimes only saved on a second attempt;
   after renaming + saving, the Templates Gallery kept showing the old name /
   old list until restart.
3. **Selection box (resize handles)**: width sometimes "increased so much" and
   shrinking it back changed size differently per object type — height/width
   behave differently for some objects but not others. Wanted researched,
   standard handle behaviour plus a **Bind W/H** (aspect-ratio lock) option in
   the properties panel.
4. **Text double-click**: the inline editor changed the highlight color to
   something ugly and changed the element's size while editing.
5. **Second laptop**: on a different screen the window content got clipped
   (top, bottom, left cut off). Explicit instruction: *document only, no code
   change now*.

Standing instruction attached to the report: always check what a change can
break elsewhere; research before coding anything uncertain.

## 2. Root causes (all confirmed in code before editing)

| # | Symptom | Root cause | Evidence |
|---|---|---|---|
| 1 | Duplicate loses mode/settings | `TemplatesView` dup button built the copy from `PresetTemplates.buildClassic()` and copied only id/name/page/elements — mode, labelConfig, printOffsetX/Y never carried over | old `dupBtn.setOnAction` block |
| 2a | Rename "does not work" | `nameField.textProperty()` listener writes into the in-memory `Template` only; nothing persists until Save — and the save did not refresh the gallery (2b), so the rename appeared lost | `createToolbar()` nameField wiring |
| 2b | Gallery stays stale | `StudioApp.saveTemplates()` uses a **cached** view whose refresher only re-runs when the DataManager **data epoch** moves. Template writes went through `TemplateDao` **directly** and never bumped the epoch (no `invalidateTemplates()` existed). Worse, the designer's `saveTemplate()` called `app.reloadAllData()`, whose switch has **no `case "designer"`** — a deliberate no-op while the designer is the active view | `StudioApp.cached()` + `DataManager` bump sites |
| 3 | Resize feels inconsistent | Two facts combined: (a) `updateLiveElementVisual` resizes the *wrapper pane* but IMAGE/BARCODE/QR visuals are `ImageView`s with `preserveRatio=true` inside a `StackPane` — the frame changes while the pixels letterbox instead of scaling, which reads as "the width grew but the object didn't" (TABLE/RECT, by contrast, visibly follow the frame); (b) no proportional mode: every handle was free-resize for every type, so accidental non-uniform scaling of content objects was one careless drag away | `DesignObjectRenderer.renderImage/renderBarcode/renderQrCode`, all 8 handle drag handlers |
| 4 | Text editor resizes + recolors | Editor size used floors of `60/zoom × 30/zoom` px (dwarfed small boxes at high zoom → "it changed size"); the TextArea kept the system light-blue selection highlight (→ "ugly color") | old `startInlineTextEdit` |
| 5 | Laptop clipping | **Not fixed** — see §7. |

## 3. Research: how mainstream canvas tools define resize behaviour

Sources consulted: Figma Help ("Scale layers while maintaining proportions",
"Resize assets — lock aspect ratio"), Canva Help, Microsoft Office docs
("press and hold Shift while dragging a sizing handle to maintain
proportions"). The consistent convention set:

1. **Corner handles scale proportionally for content objects** (images,
   barcode/QR codes, icons); **side handles always stretch one axis**.
2. **Shift inverts the default**: proportional⇄free (Office/Canva phrasing),
   so a power user is never stuck.
3. **Text boxes resize freely**; the font size does not change while dragging.
4. An explicit **aspect-ratio lock** in the properties panel (Figma "Lock
   aspect ratio") binds all resize gestures while enabled.
5. Content-scaling objects show the *content* growing/shrinking during the
   drag, not a frame sliding over letterboxed static pixels.

## 4. Decisions

- **D1 — Duplicate = model-level deep copy.** New `Template.copyOf(source)`
  does a Jackson round-trip (every persisted field survives: mode,
  labelConfig, print offsets, timestamps) and returns the copy with `id`
  cleared so the caller assigns a fresh id and the DAO upsert can never
  overwrite the source row. Placed on the model so it is unit-testable and
  reusable (MCP duplicate path can adopt it later).
- **D2 — Template writes bump the epoch.** New `DataManager.invalidateTemplates()`
  (epoch bump only — templates have no DataManager cache). Called after every
  template write outside the DAO: designer save, duplicate, delete, import,
  both "+ New" buttons, backup restore. This makes the cached gallery refresh
  exactly like every other view; no change to `StudioApp`'s caching design.
- **D3 — Rename stays live-edit, save stays explicit.** The wiring was already
  correct (field → template; Save persists). Bug 2 was 2b. No code change to
  rename; the stale-list fix (D2) is what makes it "work".
- **D4 — Resize semantics follow the researched standard** (§3), implemented
  as a pure static function `TemplateDesigner.constrainSize(...)` so the
  truth table is unit-testable: corner on content = proportional, Shift
  inverts, sides always stretch, lock always binds. The ratio is taken from
  the **drag-start** size, so toggling Shift mid-drag cannot compound drift.
- **D5 — Content objects scale their pixels during the drag.**
  `updateLiveScaledVisual` scales the rendered visual node relative to the
  size it was rendered at (tracked per element in an `IdentityHashMap`);
  `updateElementVisualInPlace` records the rendered size after every
  re-render; the mouse-released handler clears the preview and re-renders
  final geometry. Cheap per frame (no re-render, no re-rasterize of barcodes).
- **D6 — Aspect lock is session-scoped**, not persisted per element: it is a
  gesture modifier, not a property of the artwork, and a persisted flag could
  surprise older templates.
- **D7 — Text editor becomes exactly the element's footprint** (min 4 px
  clamp, hard `maxSize` cap) and carries brand styling: gold highlight
  (`#D9A13B55`) with element-colored highlight text and gold caret/accent.
  Inline styles here are the established pattern for this zoom-aware editor
  (see §6 note on theme compliance).

## 5. Implementation map

| File | Change | Blast radius |
|---|---|---|
| `model/Template.java` | + `copyOf()` + `COPY_MAPPER` | additive; no existing call site affected |
| `ui/DataManager.java` | + `invalidateTemplates()` | additive |
| `ui/views/TemplatesView.java` | dup button uses `copyOf`; invalidation after dup/delete/import/2×new | same file, same handlers |
| `ui/views/TemplateDesigner.java` | invalidation in `saveTemplate()`; editor size/highlight in `startInlineTextEdit()`; + `aspectLock`, `isScaleable`, `isCornerProportional`, `constrainSize`, `applyConstrain`, `updateLiveScaledVisual`, `clearScalePreview`, `recordRenderedSize`; all 8 handle drags + W/H spinners + release handlers rewired | the only behaviour change is during resize drags of content types and with Shift/lock held; untouched: move/rotate, snapping, groups, layers, undo/redo (`saveState` calls unchanged), print/PDF pipelines |
| `ui/views/SettingsView.java` | backup restore also calls `invalidateTemplates()` | one line |
| `test/model/TemplateCopyOfTest.java` | 4 tests: fidelity, deep-not-shared, id cleared, null-safety | new |
| `test/ui/views/DesignerResizeSemanticsTest.java` | 7 tests pinning the constrain truth table | new |

## 6. Deliberately not done / theme compliance

- No new CSS: the inline text-editor styling extends the editor's existing
  inline block (it is the only zoom-correct place; global CSS cannot know the
  element's font/color). The gold `#D9A13B` is the app's existing accent used
  by the selection border, so the highlight is *in theme*, not a new color.
- Text font size intentionally does NOT grow with the box (convention §3.3).
- Undo/redo snapshots, group moves, snapping and the rotation handle are
  byte-identical behaviour; the 5 mm/3 mm handle minimums are preserved.
- MCP `duplicate_template` and `AppShortcuts` label-seeding still use their
  own paths — they are single-user headless flows, unaffected by the gallery
  epoch; adopting `copyOf` there is a cheap follow-up.

## 7. Laptop DPI bug — documentation only (as instructed)

**Symptom.** On the second laptop, the app window is clipped: top, bottom and
left edges are cut off; fine on the primary monitor.

**Most likely cause.** JavaFX DPI handling on the laptop's display scaling
(typically 125 %/150 % on Windows) combined with the app's fixed
`-fx-font-size` root and hard-coded pixel paddings/regions: the scene's pixel
size is computed for one scale but the window/stage is sized on another, so
content extends past the visible stage. High-DPI machines with a *secondary
monitor at a different scale factor* (JavaFX ≤ early 21 had known
per-monitor DPI bugs on Windows) make it worse: the window was opened on
monitor A and dragged to monitor B.

**Diagnostic steps when we take this up (no code change now).**
1. Reproduce with both monitors at 100 % scaling → then mixed-DPI is the trigger.
2. Check `WindowStateManager` saved bounds: a window saved on the big monitor
   can restore at coordinates off-screen on the laptop (`WindowResizeHelper`
   exists but does not clamp to the *current* screen's visible bounds).
3. Compare JavaFX runtime version vs the known per-monitor-DPI fixes
   (JDKFX 21+ / 23+); test `-Dglass.win.uiScale=1.0` and
   `-Dprism.allowhidpi=false` as **diagnostic flags only**.

**Candidate fixes (for a future decision, not implemented).**
- Clamp restored window position/size to `Screen.getScreensForRectangle`
  / `Screen.getPrimary().getVisualBounds()` in `WindowStateManager`.
- Replace hard-coded px paddings with em-based values in `globalfile.css`
  (the stylesheet already uses px everywhere — same audit that
  `menu-button-sm` started).
- Set the stage `minWidth/minHeight` to the smallest supported layout and let
  content scroll (several views already assume ≥ 1280 px).

## 8. Verification & rollback

- `mvn test` full suite — **BUILD SUCCESS, 403 tests, 57 classes, 0 failures**; new suites
  `TemplateCopyOfTest` (4) and `DesignerResizeSemanticsTest` (7) green.
- Manual re-check list: duplicate a barcode template → stays barcode with
  offsets; rename + save → Back shows the new name immediately; drag image/
  barcode corner → content scales, Shift frees it; text double-click →
  editor matches the box exactly, gold selection.
- Rollback: revert the single commit; all changes are additive methods plus
  handler rewrites in the two documented files. No schema, no persisted
  format change.
