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

**Resolution (implemented).** The geometry half of the bug is fixed in
`WindowStateManager`:
- `fitToScreen(...)` clamps the saved rectangle to fit ENTIRELY inside the
  screen it most overlaps (size capped to visual bounds, position pinned so
  top + bottom edges stay visible). Previously `isVisibleOnAnyScreen` only
  demanded ~100 px of overlap, so a mostly-off-screen window passed the guard
  and opened clipped — taking the bottom-right chatbot button with it.
- Stage `minWidth/minHeight` are clamped to the smallest connected screen in
  `applyAndTrack(...)` (floors 320×240); `StudioApp` no longer sets a fixed
  1024×640 minimum that overflows a scaled laptop's logical desktop.
- Covered by `WindowStateManagerGeometryTest` (8 pure-geometry tests).

**Still open (separate work):** replace hard-coded px paddings with em-based
values in `globalfile.css` (the stylesheet uses px everywhere — same audit
that `menu-button-sm` started), and if a laptop still mis-renders content
*inside* a correctly-sized window (JavaFX per-monitor-DPI, JDKFX 21.0.4),
try upgrading JavaFX or run once with `-Dglass.win.uiScale=1.0` /
`-Dprism.allowhidpi=false` as diagnostics.

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

## 2026-10-07 — Text frame shrink-wrap + honest padding policy

User-reported bug: after scaling, the selection box was much larger than the
text (screenshot: small NERROW inside a big gold box). Root causes found:

- `ensureTextFrame` only ever GREW the frame, so slack from an earlier large
  scale was never removed (and its text measurements were mixed px/mm units).
- The selection border added another 1 mm/line-height pad OUTSIDE a frame that
  already contained the renderer padding — i.e. double padding.

Policy now (single source of truth, shared by overlay, canvas and PDF):

- Horizontal gap text→frame: exactly 1 mm per side.
- Vertical gap: line-height driven, `(lh - 1) * 0.5 mm` per side, with a
  0.05 mm anti-clip floor (applies for lh <= 1.0 too).
- The frame shrink-wraps BOTH ways from real JavaFX font metrics (bold-aware,
  +4% for synthesized bold faces), so the box always hugs the text and the
  gap stays constant at any font size (scale).
- Scale releases re-run: shrink-wrap → re-render visual → rebuild overlay.
- New TEXT gets a no-clip default line height: `1.2 + 1 mm / font-size(mm)`
  (clamped 1.2..2.5); the line-height spinner accepts any value (editable,
  0.1..200).
- PDF text export insets by the same pad so print matches canvas (WYSIWYG).

Verification: `TextFramePaddingTest` (7, headless JavaFX, real font metrics)
asserts no clipping, 1 mm horizontal gap constant across 6/10/18/36 pt,
line-height-driven vertical gap, and shrink-back after a stale oversized
frame. `MultiSelectSupportTest` (14) and `DesignerResizeSemanticsTest` (7)
green; full `mvn compile` clean.

## 2026-10-07 — Bind W/H now drives every handle (finite scaling)

Reported: scaling from a selection vertex always grew BOTH dimensions, even
though the Position & Size panel offers a Bind W/H (aspect) checkbox, and the
side handles never consulted it at all.

- `aspectLock` (Bind W/H) now DEFAULTS TO ON, and all eight handles route
  through `applyHandleDrag` → `applyConstrain`, so the lock really governs
  side handles too (side handles previously ignored it).
- Bound: the drag scales the FONT, so width, height and glyphs grow together
  (crisp text, no distortion).
- Unbound: only the dragged axis changes — the frame takes the drag and the
  glyphs STRETCH via scaleX/scaleY (top-left pivot), so X and Y each follow
  the drag and the selection box still hugs the text with no clipping.
- Corner + Shift still toggles constraint independently of the lock.
- Typed W/H edits follow the same rule (bound → font scales, unbound →
  stretch), so the frame never desyncs from the glyphs.
- PDF export matches: text is laid out at its unscaled size and stretched
  from the element top-left, pivoting like the canvas.

Verification: `TextHandleScalePolicyTest` (5) pins bound-vs-free drag
behaviour and the release-time hug; `TextStretchRenderTest` (4, headless
JavaFX) proves stretched text fills the frame exactly on each axis;
`DesignerResizeSemanticsTest` (7) still green.

## 2026-10-07 — Overlay badge, multi-select help, real progressive bold

- Canvas delete badge ("x") on the multi-selection box was unstyled: it inherited
  the app themes large default font AND scaled with canvas zoom, so it rendered
  huge. It now has an explicit, zoom-invariant footprint (~15 px on screen,
  same inverse-zoom rule as the resize handles) plus its own font size,
  padding and corner radius.
- Multi-select spacing panel now explains itself: a "How this works" card
  describes Set (equalise to typed mm), -/+ (nudge every gap by 1 mm),
  Distribute (even spread, endpoints fixed), that "now" is the average gap,
  that only each elements X/Y is stored, and that each action is one undo step.
  The disabled hint also spells out Ctrl+Click / marquee selection.
- Font weight above 700 was effectively ignored on families without heavier
  faces. Cause: JavaFX resolves to the nearest REAL face, so Regular/Bold-only
  families map 800/900 onto the same bold face, and the previous "synthesis"
  only added LCD smoothing (crispness, not weight). Now the renderer stacks
  sub-pixel offset glyph copies, progressively: 1 pass for a missing bold,
  1-2 for 800, 2-3 for 900 (0 when a real face exists). PDF export prints the
  same passes, so 800/900 are visibly heavier on canvas and paper.

Verification: `FontWeightSynthesisTest` (4) pins the policy + the stacked
  rendering; also green: TextFramePaddingTest (7), TextHandleScalePolicyTest
  (5), TextStretchRenderTest (4), MultiSelectSupportTest (14),
  DesignerResizeSemanticsTest (7), TemplateDesignerEnhancementsTest (3).
  JavaFX test classes no longer call Platform.exit() (it crashed the forked
  VM for later classes in the same JVM).
