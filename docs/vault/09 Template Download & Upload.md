---
tags: [decision, templates, import-export, feature]
aliases: [Template Import Export, DEC-001, Download Upload Templates]
---

# 09 — Template Download & Upload (Decision Record)

**Status:** implemented · **Date:** 2026-09-22 · **Area:** [[04 UI Layer]] · [[03 Service Layer]] · Templates Gallery

> [!info] What this note is
> A decision record for the "download / upload templates" feature: the request, the
> survey of what already existed, the implementation steps in order, **why an
> existing file was touched**, and **what effect that change has**. Read this
> before changing `TemplatesView` or adding another template exchange path.

---

## 1. Request

On the **Templates Gallery** screen:

1. **Download (export)** a template — one template, or **several at once chosen from a multi-select dropdown**.
2. **Upload (import)** a template file — get a layout a colleague / another machine exported.
3. Support both bill/receipt templates **and** barcode/label templates.
4. The UI must use the existing "Obsidian & Gold" theme — no ad-hoc styling.
5. Do not break anything that already works.

---

## 2. Survey — what already existed (and why it was not enough)

| Existing thing | Where | Verdict |
|---|---|---|
| `TemplateDao` (get / save / delete a template as JSON) | `db/TemplateDao.java` | **Reused as-is** — the whole feature round-trips through it, so no schema change is needed |
| `BackupRestoreService` | `service/BackupRestoreService.java` | **Not reused for the feature** — it always dumps the *entire* account (settings, bills, buyers, items, variables, templates) to one file. Far too heavy for "send me that receipt layout", and importing it replaces data. Its *format* is still accepted by our importer (see §5) |
| CSV import/export per screen (`CsvService`, `BuyersView`, `ItemsView`…) | views + `service/CsvService.java` | Studied for the FileChooser / Toast / refresh conventions. Not reusable: templates are nested structures, CSV cannot express page config + 20 element types |
| `PresetTemplates` (6 built-in starters) | `model/PresetTemplates.java` | Untouched — presets are code, not user templates; they stay in-app only |
| `MenuButton` + `CustomMenuItem` + `CheckBox`, `.menu-button`, `.context-menu`, `.check-box` CSS | `ui/views/HistoryView.java`, `resources/css/globalfile.css` | **Reused** — one `MenuButton` + `CustomMenuItem` + `CheckBox` combination gives the multi-select dropdown the app's existing menu/checkbox chrome. Only after review did three small rules get added to make the menu sit flush in a `button-sm` row (§8) |
| `FileChooser` conventions (extension filter, overwrite prompt, Toast, `refresh()`) | `ui/views/HistoryView.java`, `SuppliersView.java`, `SettingsView.java`, `ChatbotPanel.java` | **Followed** — no new dialog framework; the only addition is a shared default folder (`AppDirs.downloadsDir()`, §8) |

**Gap:** there was no per-template (or per-selection) exchange format and no per-selection export path anywhere in the app.

---

## 3. Decision

* Add a **service**, not logic in the view: `service/TemplatePackageService.java`.
* Add an **Export dropdown** (`MenuButton` + one themed `CheckBox` per saved template, `hideOnClick = false` so the menu stays open) and an **Import** secondary button to the Templates Gallery top bar.
* File format = **plain JSON**, written with the app's existing Jackson `ObjectMapper` (already a project dependency, already used by `TemplateDao`/`BackupRestoreService`).
* **Import never overwrites**: an incoming id that already exists gets a fresh id, an incoming name that already exists gets an " (imported)" suffix. Nothing already on the machine is ever destroyed by an upload.

### File format (`.json`)

```json
{
  "kind": "invoicestudio.templates",
  "version": "1.0",
  "app": "InvoiceStudio",
  "exportedAt": "2026-09-22T10:14:03.221Z",
  "templates": [ { "id": "tpl_ab12cd34ef", "name": "My Receipt", "page": {...}, "elements": [...], "mode": "bill" } ]
}
```

The importer is deliberately lenient: it accepts our package, a **bare JSON array** of templates, or **any object that carries a `"templates"` array** (i.e. a full `BackupRestoreService` backup file) — so a user can pull just the layouts out of an old backup.

---

## 4. Implementation steps (in the order they were done)

1. **Read the existing gallery** (`ui/views/TemplatesView.java`) — top bar, card builders, `refresh()`; and `TemplateDao`/`Template` to confirm templates are stored as JSON blobs per user.
2. **Write `service/TemplatePackageService.java`** — `exportTemplates(...)`, `importTemplates(...)`, `readTemplates(...)`, `suggestedFileName(...)`, plus an `ImportResult` with a ready-made `summary()` for the toast.
   * Reuses `TemplateDao` for every read/write; no new tables, no schema migration, no `StudioApp` change (the view already builds its own DAOs).
3. **Wire the gallery** — in `TemplatesView`:
   * new field `packageService`, built from `app.getDb()` in the constructor (same pattern as the existing `templateDao`);
   * top bar now reads `Import` · `Export` · `Print Calibration Sheet` · `+ New Label Template` · `+ New Template`;
   * `buildExportMenu(...)` builds the multi-select dropdown from the already-loaded template list, so it is rebuilt on every `refresh()` and can never show a stale list;
   * `downloadTemplates(...)` / `handleImportTemplates(...)` own the `FileChooser`, then `Toast` + `refresh()`.
4. **Make the dropdown testable** — `buildExportMenu(List<Template>, Consumer<List<Template>>)` is a package-private **static** seam; the instance method passes `this::downloadTemplates`. This keeps the file-system code in one place while letting a UI test drive the multi-select behaviour without booting the whole app.
5. **Tests** — `TemplatePackageServiceTest` (10, logic/round-trip/collisions) and `TemplatesExportMenuTest` (6, real JavaFX thread: theme classes, stay-open dropdown, counts, select-all/clear, who-gets-downloaded).
6. **Verify** — `mvn test`: 53 test classes, 0 failures.

---

## 5. Why an existing file was changed, and what effect it has

Only **`ui/views/TemplatesView.java`** was modified to build the feature (plus two additive follow-up edits described in §8). Nothing else in the app's behaviour was changed.

| Change | Why | Effect / blast radius |
|---|---|---|
| +2 imports (`TemplatePackageService`, `javafx.stage.FileChooser`, `java.io.File`, 3 `java.util` types) | new calls | none — additive |
| +`packageService` field, initialised in the constructor | the view already creates its own DAOs, so this matches the existing ownership model and avoids touching `StudioApp` | none — one extra object per view instance |
| Top bar gained `Import` + `Export` | the feature must be reachable from the gallery | the `HBox` has 2 more children; the title block already flexes (`sp` spacer) — on a narrow window the buttons compress before anything clips. No layout constant, no other view touched |
| New methods `buildExportMenu`, `downloadTemplates`, `handleImportTemplates` | keep file-system + dialog code out of `refresh()` | purely additive; `refresh()` only gained two lines |
| `buildExportMenu(List, Consumer)` made **static package-private** (was private) | test seam for the multi-select contract | visible to `com.invoicestudio.ui.views` only; no public API change |

**Not changed on purpose:** `TemplateDao`, the `templates` table/schema, `BackupRestoreService` (its export/restore behaviour is untouched), `StudioApp`, the sidebar/routing, `PresetTemplates`, and the existing per-card `Create Bill / Designer / ⎘ / 🗑` actions. The gallery's layout, section headers and counts are identical to before.

**Other effects to be aware of**

* **Data safety** — an upload can only *add* rows: fresh id on collision, " (imported)" name suffix, and every write goes through `TemplateDao`, which is already scoped to the logged-in user. Two different users on one machine can never see or overwrite each other's templates.
* **Printing/PDF** — none. An imported template is the same `Template` object the designer produces, so preview/print/PDF paths behave identically. Label-mode templates keep `mode = "label"` and `labelConfig` (covered by a test).
* **MCP / AI surface** — unchanged; the MCP tools keep using the DAOs directly.
* **Failure modes** — a corrupt/foreign JSON file surfaces as a red `Toast` ("Upload Failed: …") instead of an exception; a single malformed entry inside an otherwise valid file is skipped (`AppLog.debug`) rather than aborting the import.
* **Rollback** — delete the two new files, remove the two new methods + the field from `TemplatesView` (git revert of one file). No data migration to undo.

---

## 6. Theme compliance (no random UI)

The dropdown is a stock `MenuButton` with the app's own classes (`button-sm`, `button-secondary`) — the same classes the neighbouring "Print Calibration Sheet" button uses. Menu rows reuse `CustomMenuItem`, so the dropdown inherits the existing `.context-menu` / `.menu-item` rules; the tick boxes inside are ordinary `CheckBox`es, i.e. `.check-box` / `.check-box:selected .box` from `globalfile.css`. There is **no inline `setStyle` anywhere** — the golden rule in `UiTheme` holds; the only styling added is the scoped class block in §8.

---

## 7. Test coverage

| Test file | Pins down |
|---|---|
| `service/TemplatePackageServiceTest` | empty selection refused; single + multi export writes a recognisable package; page config/elements/label mode survive the round trip; suggested file name sanitises `/ : ?`; import never overwrites (unique ids, " (imported)" naming, twice → " (imported 2)"); a full backup file and a bare array are accepted; unrelated JSON and missing files are rejected with a readable message |
| `ui/views/TemplatesExportMenuTest` | the empty state; theme classes on the dropdown; checkbox-per-template; dropdown stays open (`hideOnClick == false`); "Download Selected (n)" follows the ticks and is disabled at 0; only ticked templates are handed over; Select all / Clear; "Download All (N)" passes everything in order |

| `ui/views/TemplatesToolbarThemeTest` | **the real `globalfile.css`** applied to an `Import` Button + the `Export` menu in one row: the menu's caption inherits the theme's light text fill (not Modena's near-black), and the two controls share padding, font and height (§8) |
| `AppDirsTest` (2 added) | `downloadsDir()` honours the `invoicestudio.downloads.dir` override and, with no override, always returns an **existing, absolute** folder — a `FileChooser` throws on a bogus initial directory |

Run: `mvn test` (the classes alone: `mvn -Dtest=TemplatePackageServiceTest,TemplatesExportMenuTest,TemplatesToolbarThemeTest test`).

---

## 8. Follow-up polish (post-review): caption colour, button height, default folder

Three issues were reported after the first pass. All three are fixed *inside* the theme or in two additive helpers — no feature logic changed, and nothing that already worked was touched.

### 8.1 "Export" rendered with near-black text

A `Button` paints its caption itself; a `MenuButton` renders it through an inner `Label` (style class `label`). Modena's own `.label` rule sets a dark `-fx-text-fill` **directly on that node**, so it beat the light `-fx-text-fill` the theme had put on the menu. The app already solves exactly this for buttons with `.button .label { -fx-text-fill: inherit; }` — the compact menu now gets the same treatment, so the caption resolves to the menu's own colour (`#CBD5E1`, byte-identical to the "Import" caption).

### 8.2 "Export" was taller than "Import"

Measured on the real JavaFX thread with the shipped stylesheet (`TemplatesToolbarThemeTest`): padding (`4 11 4 11`) and font (11px) already matched, yet `Import` laid out at **26px** and `Export` at **34px**. Two Modena defaults were responsible:

| Modena default | Effect | Correction |
|---|---|---|
| `.menu-button > .label { -fx-padding: 0.333333em 0.666667em; }` | +7.4px height (and +14.6px width) *inside* the control's own padding — the real culprit | `-fx-padding: 0` |
| `.menu-button > .arrow-button { -fx-padding: 0.5em …; }` | +5.5px of vertical em-padding around the arrow glyph | `-fx-padding: 0 0 0 4` (glyph plus a 4px gap) |

After the fix both controls lay out at the same height (asserted to within half a pixel). "Maybe reduce padding" is therefore handled where it belongs — in CSS, not with a hard-coded height.

### 8.3 Default folder = Downloads, both directions

Both dialogs (download = Save, upload = Open) now start in the user's Downloads folder through one new helper, `AppDirs.downloadsDir()`:

* `-Dinvoicestudio.downloads.dir=…` override first (tests, portable installs);
* `<user.home>/Downloads` — the Windows and macOS default, and what mainstream Linux desktops use too;
* the home folder, then the working directory, as graceful fallbacks.

`TemplatesView` applies it only when the folder really exists — `FileChooser.setInitialDirectory` rejects a non-existent path — so an account with no Downloads folder keeps the chooser's own default instead of throwing.

### 8.4 Diff of the polish pass

| File | Change | Blast radius |
|---|---|---|
| `resources/css/globalfile.css` | +one block **at the end of the file** (the documented convention — the cascade wins there), three rules on the **new** class `.menu-button-sm` | none for existing widgets: the class is new and only the export menu carries it. The shared `.menu-button` look used by the "…" row menus is untouched, so those keep their current size |
| `ui/views/TemplatesView.java` | +1 style class on the export menu; +`defaultToDownloadsFolder(FileChooser)`, called once in each dialog | additive; filters, titles and targets of both dialogs are unchanged |
| `AppDirs.java` | +`downloadsDir()` and its override property | additive; only `TemplatesView` reads it, so `dataDir()` / `databaseUrl()` behaviour is unchanged |

**Rollback of the polish**: delete the `.menu-button-sm` block and the class name from `buildExportMenu`, drop `defaultToDownloadsFolder` (and the two calls) and `AppDirs.downloadsDir()`. The feature itself keeps working; the menu merely looks Modena-tall again.

---

## 9. Follow-ups (not done here)

* Drag-and-drop a `.json` onto the gallery as an alternative to the Import dialog.
* Per-card `⇩` shortcut to download a single template without opening the dropdown (deliberately skipped — a 5th button would wrap the 280 px card).
* "Share to clipboard / share code" that pastes a template as text for chat-based sharing.
* Options for the conflict policy (rename vs. update-in-place vs. always new copy).

Related: [[04 UI Layer]] · [[03 Service Layer]] · [[00 Index]]
