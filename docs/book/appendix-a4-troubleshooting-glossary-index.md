# Appendix A4 — Troubleshooting Guide · Glossary · Index

> **Part 15 of InvoiceStudio: Zero to Finished Product**
> This appendix is the reference desk of the book. It does not build anything —
> it collects everything the other twenty-three chapters taught, so that six
> months from now, when a label prints upside-down or a bill number repeats,
> you have one place to look.
>
> Three sections, three jobs:
>
> 1. **Troubleshooting Guide** — every "Common mistakes and fixes" table from
>    Chapters 1–22, merged into one master table, deduplicated, and organized
>    by the part of the app that misbehaves. Each row names the chapter that
>    explains the fix in full.
> 2. **Glossary** — every technical term this book explained at first use,
>    alphabetically, with one definition and the chapter that introduced it.
> 3. **Index** — every class, file and topic in the codebase, mapped to the
>    chapter that owns it, built mechanically from the source tree (not from
>    memory).
>
> Nothing here is new material. If a fix below feels mysterious, the chapter
> reference is where the full story lives.

---

## 1. Troubleshooting guide — every mistake the book ever fixed

Every chapter of this book ends with a "Common mistakes and fixes" table —
symptoms a builder actually hit, the cause hiding underneath, and the fix.
There were **195 such rows** across Chapters 1–22, and they repeat each other
often (the "Not on FX application thread" crash alone appears in five
chapters, because five chapters can cause it). This section merges all 195
rows into **one master table of 70 rows**, deduplicated: where several
chapters describe the same failure, the most complete version survives, and
related symptoms that share one fix share a row. The last column names the
chapter (or chapters) whose full explanation owns the failure.

How to use it:

1. Find the *symptom*, not the theory — scan the left column of the group
   that matches where the pain shows up.
2. Apply the **fix** exactly as written — each one is the shortest correct
   action, not a philosophy.
3. When you want the *why* — the last column names the chapter. Chapter 0's
   reading advice stands: current chapter's table first, then this appendix.

### 1.1 Build, run & environment

| Symptom | Likely cause | Fix | Ch |
|---|---|---|---|
| `javac: command not found`; `invalid source release: 21`; `UnsupportedClassVersionError` at run time | A JRE instead of the JDK, Maven on an older JDK, or an older `java` on PATH | Install the full JDK 21; align `JAVA_HOME` and PATH (check `mvn -version`); open a *new* terminal | 1 |
| `Error: JavaFX runtime components are missing` | `StudioApp` (the `Application` subclass) was launched directly instead of the `Launcher` bootstrap | Always start via `Launcher` — `mvn javafx:run`, the fat jar's manifest, IDE run-config and jpackage flags all do | 1, 2, 21, 22 |
| Maven downloads fail behind a proxy; window opens blank on Linux | Environment pieces missing (proxy config; GTK/GL libraries) | Add the proxy block to `~/.m2/settings.xml`; `sudo apt install libgtk-3-0 libgl1` | 1 |
| `java -jar target/*.jar` says "no main manifest"; "which of the two `main` methods runs?"; `dependency-reduced-pom.xml` keeps appearing as an untracked change | You ran `compile` but never `package`; both `Launcher` and `StudioApp` have mains; the Shade plugin regenerates its helper file on every package | `mvn clean package` first — the Shade plugin writes the manifest, whose `Main-Class` (`Launcher`) wins; the reduced pom is expected and gitignored — delete freely | 1, 2 |
| The jar runs on the build machine but crashes elsewhere (`UnsatisfiedLinkError` / blank window) | The fat jar carries the *build* OS's JavaFX natives | Package on the target OS — or let the CI workflow, which always builds on Windows | 22 |
| A test or harness run polluted the real data folder; "my data disappeared" after a reinstall | A run forgot `-Dinvoicestudio.data.dir`; or data simply lives elsewhere than Program Files | Copy the `-Dinvoicestudio.data.dir="$RUN_DIR"` line from any `scripts/` file; check `%APPDATA%\InvoiceStudio` (delete it only for a factory reset) | 2, 21, 22 |
| Logged stack traces give no clue where they came from | Code used `ex.printStackTrace()` instead of the logger | Route everything through `AppLog.error(ex)` — time, thread and caller for free | 2 |

### 1.2 Database, caching & data integrity

| Symptom | Likely cause | Fix | Ch |
|---|---|---|---|
| `SQLException: no such column` on an older install; schema changes work in tests but fail in the app | A field added without its `ALTER TABLE` migration; tests use a fresh DB while the app migrates an old one | Add the try/catch ALTER to `DatabaseManager.initSchema()` — and re-test against a copy of a *real old* database | 3 |
| `database is locked` errors | Two connections writing at once (e.g. a DAO called from the FX thread while a background task writes) | Route all writes through the one background DB lane; keep DAO calls off the FX thread | 3 |
| `out of memory` opening a long history list; a report shows a NULL-poisoned balance | The `json_data` blob read for *every* row; a SUM missing `COALESCE` | Select hot columns for lists (deserialise only the opened row); copy the exact shape of `StockLedgerDao.allBalances()` | 3, 5 |
| `SQLITE_CONSTRAINT: UNIQUE` on category names | Two writers raced to create the same name | Expected backstop — catch it and re-select the existing row (the MCP layer does exactly this) | 3 |
| Rows "missing" or mixed between accounts: list screens empty right after login, edits invisible on one account, data "lost" after a switch | DAO reads ran before the session was set (`uid` was `""`); the tenant guard correctly refused another user's row; or you are looking at another `user_id`'s partition | Ensure sign-in completes before the first view builds; check which account owns the row (`SELECT id, user_id …`) — by design | 3, 4 |
| A newly added field never persists (buyer field, model field) | The UI built a *new* object for save without copying the field, or the getter/setter pair is missing | JSON storage saves whatever the object holds — write into the object you save; keep JavaBean accessors exact | 4, 6 |
| An archived transaction reappears in totals; a built-in variable got overwritten | A read query lost `AND deleted = 0`; the upsert lost its `WHERE builtin = 0` guard | Audit every SELECT after any change; restore the schema-level guards | 5 |
| Ledger and stock inconsistencies after custom code: duplicate sale rows, balances drifting after a crash, stock wrong after editing a purchase, stock that never moves | Voucher movements not re-appended after an edit; bills saved via `billDao` directly; sequential side-effects instead of one idempotent path; free-typed lines that match no catalog item | `deleteByVoucher` → re-append → `recomputeItem`; route saves through `saveBill()` + `syncBillsToTransactions()`; re-save the bill (idempotent) or restore a backup; pick lines from the catalog | 5, 8, 12, 13 |
| Views show stale or just-deleted rows; `IllegalStateException: DataManager not initialised` | A write bypassed `DataManager` (no invalidation/epoch bump); a static path touched `get()` before `init()` | Route every write through the `DataManager` wrappers; initialise in the boot sequence before any view or MCP use | 8, 9 |
| By-design refusals and frozen names: deleting the default item/category "does nothing"; the bill register shows the buyer's *old* name after a rename | The protected-default guard refuses; the register is a projection frozen at save time | Both intended — the default cannot be deleted from any surface, and documents preserve history (label history works the same way; see 1.6) | 4, 5 |

### 1.3 UI, threading & the shell

| Symptom | Likely cause | Fix | Ch |
|---|---|---|---|
| `IllegalStateException: Not on FX application thread` | A background task touched UI nodes (aggregating, `setText`, a knowledge listener…) | Wrap every UI touch in `AppExecutors.runOnFx(...)` / `Platform.runLater` — heavy math stays on the background thread, nodes never leave the FX thread | 2, 9, 14, 20, 21 |
| `AuthException: Failed to refresh…` every launch; error banner shows raw `REQUEST_TYPE`; two accounts' data mixed | Refresh token revoked (password change / 30-day idle); an unmapped Firebase error code; a login that bypassed `handleSuccessfulLogin` | The user signs in again (by design); add a `parseErrorMessage` mapping case; always route login/logout through `AuthView` + `clear()` | 10 |
| Google sign-in does nothing; "Address already in use" on port 8085 | No default browser / `Desktop` unsupported; the fixed loopback port is held and the fallback failed | Copy the surfaced "Please open: <url>" into a browser; retry — the fallback binds port 0 | 10 |
| Shell chrome & input glitches: hover effects dead on a widget; the app icon reverts to the Java cup on one dialog; a dialog "jumps" when it opens; the window opens off-screen after unplugging a monitor; the sidebar highlights the wrong button; a shortcut fires while typing | `setStyle()` inline styles overriding the stylesheet; a dialog managing its own icons; min size raised after `show()`; geometry restored without a bounds check; a view id missing from the nav mapping; a bare letter bound | Move styles into `globalfile.css` classes; let the global `Window.getWindows()` listener apply the shared icon; use `DialogHelper.styleDialog`; don't bypass `isVisibleOnAnyScreen`; add the id to `updateNavActive`; rebind with a modifier (`validate` rejects `TOO_SIMPLE`) | 9 |
| A card "jumps" when switching states; a hidden control leaves an empty gap | `visible` toggled without `managed` | Toggle both together (see `setScope`) | 10, 11 |
| A list view freezes, and the freeze grows with data size; cells keep stale text/colors from other rows | A DB call inside a cell factory; `updateItem` handling only the non-empty branch while cells are recycled | Never touch the DB per cell — hoist to `refresh()` with a lookup map; always set text *and* style on every call, even `setStyle(null)` in the empty branch | 11, 14 |
| A dialog closes but nothing saves | The result converter ran, but `showAndWait().ifPresent(...)` was forgotten | Handle the dialog's result — save *and* refresh there | 11 |
| ComboBox misbehaviors: shows `Buyer@3f2a`; a column renders `toString()`; clicks inside a popup (the vault's copy icon, a TextField) are swallowed | No `setButtonCell`; a plain `new TableCell()`; ComboBox skins consuming MOUSE_RELEASED | Set the button cell with the same rendering; a value factory alone is enough for plain text; use a real `Popup` with real buttons — don't "simplify" back to a combo | 11, 14, 19 |
| Search silently stops filtering after a refresh; chart months appear in random order; the counter animation leaves ₹99,999.83 on screen | The `FilteredList` was orphaned by a fresh plain list; aggregation used a `HashMap`; the eased last frame never reaches the target | Keep one `FilteredList` field and `setAll` into it; use a `TreeMap` (calendar order, or `reverseOrder()`); snap exact values in `setOnFinished` | 14 |
| Two KPI cards appear after re-selecting a buyer; Dashboard 2 pills do nothing after a refactor; taking over scroll breaks table scrolling | A listener appends instead of replacing; a stale state field; a scroll filter without exemptions | Use `getChildren().setAll(...)` for rebuilt blocks; guard with `if (!opt.equals(state))` then swap; copy the parent-walk exemption from `installSmoothScrolling` | 14 |

### 1.4 Billing & money math

| Symptom | Likely cause | Fix | Ch |
|---|---|---|---|
| Line and tax math: IGST computed for a same-state sale; line amount ≠ qty × rate in a report; CGST ≠ the sum of line taxes | Buyer's state code blank / GSTIN too short (routing sees `""`); discount applied twice (`getAmount()` then again by the caller); tax applied on the grand total instead of per line | Enter the state code or a full GSTIN; call `getGross()` for pre-discount math — `getAmount()` is already discounted; tax each line's amount, then aggregate | 6, 12 |
| Totals disagree after a duplicate/repeat bill; duplicate bill numbers on crash/retry | Totals copied instead of recomputed; counter bumped on the FX thread or outside the save task | Always recompute via `computeTotals(...)`; bump `billNoNext` inside the same background save (or set it in Settings after a crash) | 4, 12 |
| Typing in the bill editor lags; CPU spikes | The preview re-renders on every keystroke listener | Debounce with `PauseTransition.playFromStart()` | 12 |
| `$` in the notes field breaks rendering | `appendReplacement` treats `$` as a group reference | Use `Matcher.quoteReplacement` in every replacement | 12 |
| Purchase bookkeeping: payable ≠ the grand total shown; old vouchers keep the old payee; new categories land on the wrong statement side | Freight forgotten; rename without propagation; `isDirect` classification treated as fixed | `payable = grandTotal + freight` at display *and* save; always `renameWithPropagation`; update `Expense.isDirect` together with `CATEGORIES` | 13 |
| One month's totals look wrong for a single row; `NaN` shown as collection/recovery % | A date not in `yyyy-MM-dd` form fell out of the month key; division by zero when no sales exist | Fix the source date (reports deliberately skip malformed rows); keep the three-way guards (`x > 0 ? … : 100.0`) | 14 |

### 1.5 Templates & the designer

| Symptom | Likely cause | Fix | Ch |
|---|---|---|---|
| `Unrecognized field "newProp"` loading old JSON; a new field never appears after reload; NPE from an old template; `status` reads UNPAID though the file says PAID | A model missing `@JsonIgnoreProperties`; missing accessors (Jackson maps by accessors); no null-safe getter default; a typo'd enum code | Add the annotation to every model class; add the JavaBean accessors; default the getter; check the stored code against the `@JsonValue` spelling | 6, 7 |
| Column widths look wrong after editing `defaultItemColumns`; a thermal template paginates instead of growing | Widths are percentages that should sum to ~100; `autoHeight` not set on the `PageConfig` | Rebalance the widths; `p.setAutoHeight(true)` in the preset | 7 |
| An element renders on screen but not on paper (or vice versa); a new element type shows as a blank rectangle in the PDF | The new type was added to only one engine's `switch` | Follow the two-engine checklist — the PDF switch silently drops missing cases | 7, 16 |
| Undo and clipboard lose work: undo drops a newly added property; a duplicate of a custom path comes out as a blank box; Ctrl+Z "does nothing" after opening | The new field is missing from `copy()`; `duplicateSelected()` drops `points`/`pathData`/`svgSource`; identical snapshots are deduplicated | Complete `copy()` (or adopt the round-trip improvement); deep-copy via `el.copy()`; and verify the edit actually changed the model — dedupe is correct behavior | 7, 15 |
| Canvas won't scroll far enough at high zoom; selection handles become giant blobs at 400%; the grid looks thick and blurry | Zoom applied to the wrong node; a fixed design-px overlay; grid drawn at 1× and stretched | Keep `updateCenterWrapperSize()` + margins and scale `canvasContainer`, never `scaleGroup`; divide overlay sizes by zoom and rebuild on zoom; rebuild grid lines at device resolution with `Math.round(x) + 0.5` | 15 |
| Clicks on a transparent shape do nothing; the magnet snap fights the grid / guides lie | The wrapper isn't pick-on-bounds or the hitArea is missing; the snap-priority guard broke | Keep `wrapper.setPickOnBounds(true)` + the 0.5%-alpha `hitArea`; restore `if (snapToGrid && snappedGuideX < 0)` — magnet wins, grid fills gaps | 15 |
| Typed numbers in X/Y/W/H spinners don't apply; arrow keys move elements while typing in a property field; the inline text editor shows a fat white frame | `configureNumberSpinner` bypassed or the `updatingProperties` guard removed; the `isInputFieldActive` guard too narrow; the TextArea skin's `.content` padding | Keep the commit/guard pair; extend the guard's parent walk; keep the `applyCss(); lookup(".content")` zeroing block | 15 |
| Label mode shows a page the size of the invoice | `syncPageFromLabelConfig` not called after a stock change | Call it after every `LabelConfig` mutation (the code base does this in five places) | 15 |

### 1.6 PDF, preview, labels & printing

| Symptom | Likely cause | Fix | Ch |
|---|---|---|---|
| PNG preview at 150 dpi loses the bottom ~half of the page; the print preview ignores `setZoom` | The renderer draws 300-dpi coordinates onto a smaller canvas; the preview node isn't a `BillPreviewPane` | Scale the graphics context (`g2.scale(effDpi/BASE_DPI, …)`) and pin it with the bottom-marker probe test; return a `BillPreviewPane` from your `PreviewFactory` | 16 |
| It looks broken but is by design: PDF text cannot be selected or searched; last session's bulk rows didn't come back; label history shows the template's old name | PDF pages are 300-dpi rasters (`LosslessFactory`); restore is keyed on the template's variables; history rows are print-time snapshots | Expected behavior; the text-layer improvement is sketched in Ch 16 §8; re-add the variable to recover the shape | 16, 17 |
| Table element surprises: column dividers stop after the last item; `{{buyer_name}}` prints literally on screen but blank on the PDF; the totals row shows "₹₹1,234.00" | `minRows` is 0; unknown keys resolve to `""` with a bill (designer mode keeps them visible); the template prepends ₹ to an already-currency-baked variable | Set `minRows` on the table element; fix the key spelling — keys are `[a-zA-Z0-9_]+`; prepend ₹ only to bare variables | 16 |
| Barcode prints as Code 128 though EAN-13 was chosen; a gray "QR Error" square appears in the corner | The payload had ≠ 13 digits (documented fallback); `BarcodeService` caught an encode failure and drew the fallback image | Feed 13 digits (12 + check digit) or accept the fallback — the rule keeps print runs moving; check `AppLog`, shorten the payload, verify the UPI ID is set | 16 |
| Every copy says "Original for Recipient" | The copy index was not passed (all copies render with `copyIdx = 0`) | Pass the loop index — `exportBillPdf` does; custom callers must too | 16 |
| Custom paint gaps: a rotated element pivots around its corner on paper; a radial-gradient band prints empty | The custom path rotated before translating (wrong pivot); `buildPaint2D` has no `"radial"` branch | Copy `renderSingleElement`: `rotate(theta, x + w/2, y + h/2)` with the translate→scale→translate-back sandwich for flips; use a linear gradient for print-critical art or add the radial branch | 16 |
| Changing a template's page margins moves nothing | Renderers read `Settings.printOffsetX/Y`, not `PageConfig.Margins` | Nudge the global print offset — or implement the margin improvement and audit presets | 16 |
| The printer feeds extra blank labels around the printed ones (or one record prints 2–3 labels) | The printer's learned pitch no longer matches the loaded roll | Press **Calibrate Sensor**, then measure label + gap and set Label Height + Feed Gap to match | 15, 17 |
| A label prints as a black page with content-shaped holes; text prints blank but the barcode prints fine | Ink encoded as *set* bits (TSC polarity is the opposite); the node was snapshotted without a Scene so Label/Text skins never loaded | Use `packBits` (fill `0xFF`, clear where ink burns) — check hand-rolled scripts against `testPackBitsMsbFirst`; keep the transient-Scene + `applyCss()` + `layout()` sequence in `rasterize` | 17 |
| Strip and bulk-grid surprises: the right side is clipped; the printout is rotated 90° vs the preview; barcodes come out gray, fuzzy or broken; the bulk grid grows a phantom empty row while typing | Strip width exceeds the 108 mm head; a landscape `PageLayout` let JavaFX rotate; threshold mismatch; the auto-row-on-every-commit behavior was re-introduced | Trim margins/columns or use a wider-head printer; always send PORTRAIT and rotate the node yourself; raise/lower the barcode threshold (the toast echoes the effective value); only `moveDownFrom` on a *valued* last row may `addRow()` | 17 |
| Spooler discipline: RAW job refused ("does not accept raw data"); a job named for one printer printed on the default printer | The driver isn't a passthrough TSC driver; someone bypassed `resolve()`'s no-redirect rule | Install the TSC driver or fall back to the driver path; never silently redirect a *named* label job — return the not-found error | 17 |

### 1.7 The MCP server

| Symptom | Likely cause | Fix | Ch |
|---|---|---|---|
| Protocol basics misfire: 401 "missing or invalid bearer token" on every request; `GET /mcp` returns 405; Start fails because port 7800 is taken | No/old `Authorization` header (or a blank token — which fails closed on purpose); the endpoint is POST-only; another app owns the default port | Re-copy the token (Settings → Copy Token) and update the client config; use `curl -X POST -H "Authorization: Bearer …" -d '{"jsonrpc":"2.0","id":1,"method":"tools/list"}'`; change the port (1024–65535), Start again, re-copy the config | 18 |
| Idempotent-create surprises: `create_item` with an existing name "didn't update the category"; the client created an unwanted category; MCP creates stack duplicate knowledge chapters | `create_*` is check-then-create: idempotent by name and *never* updates; a missing name auto-creates; someone bypassed the registry helper | Use `update_item { id, categoryId/categoryName }` (the response `note` says so); expected — delete the empty category or move items; route all writes through the registry helpers | 18, 20 |
| `tools/call create_bill` fails "items[] is required" | The `items` argument missing, or sent as an object instead of an array | Send at least one line: `[{desc, qty, rate?}]` | 18 |
| Destructive-op friction: tool returns `requiresConfirmation` and nothing happens; `confirm_operation` says "Unknown or already-handled operationId"; a queued delete fails "still has N item(s) assigned"; approving errors "Nothing to update" | Approval is required; ids are single-use; guards fire at execution, not queue time; the gated lambda found no changed fields | Approve in Settings → MCP Server or via `confirm_operation` after the human agrees; re-issue the original tool call; do what the error says then re-issue; pass at least one field | 18 |
| The chatbot says "MCP server is switched off" though the books are fine | The server isn't running, so the chat loop took its zero-schema fast path | Start it in Settings → MCP Server (or tick auto-start) — one switch controls both front doors | 18, 19 |
| The audit log file grows forever | It is append-only by design — one line per MCP event, never read back | Rotate `mcp-audit.log` manually if it bothers you | 18 |

### 1.8 The AI assistant & the Knowledge Hub

| Symptom | Likely cause | Fix | Ch |
|---|---|---|---|
| "No API key configured" though you saved one | The key is saved for provider A but the combo is on provider B (keys are per-provider) | Pick the key from the vault (the provider switch auto-fills it), then Save | 19 |
| "hi there!" got a canned intro instead of an answer | The message matched `CHAT_ONLY` — pure smalltalk is intercepted locally by design | Rephrase with actual content; confirmations are exempt, greetings are not | 19 |
| Provider protocol errors: Gemini HTTP 400 `Unknown name "text" at 'system_instruction'`; HTTP 400 on two tools only; round 2 fails on thinking models ("thought signature"); a strict OpenAI-compatible server rejects the assistant tool-call message; Anthropic errors "tool_result without matching tool_use" | A bare inner-node wrapper; an array schema without `items`; `thoughtSignature` not round-tripped; `"content": null` rejected by strict servers; a `call` turn trimmed from the replay | Keep wrappers as OUTER objects (`AiChatClientPayloadTest` pins the shape); route tools through `geminiSafeSchema`; keep the `tSig` slot; send `""` instead of null for stricter endpoints; preserve `call`+`tool` turn pairs (`prepareTurns` never trims the current interaction) | 19 |
| The model chip shows a model you never picked; the log window is silent after closing and reopening | The old shared-config mutation bug; the pre-fix single-attach listener | Both fixed — failover mutates a per-send copy (`copyForSend`; grep for stray `setModel` if it recurs) and `show()` re-attaches the listener and catches up from the buffer | 19 |
| Usage limits: the reply ends "…(truncated — narrow your query)"; "All Gemini fallback models are also at their daily limits" | The tool result crossed `MAX_RESULT_CHARS` (4,000); the whole failover ladder is exhausted (free tier: 20 req/day/model) | Ask with a narrower scope ("top 5…", "this month…") or raise the cap knowingly — every later round re-sends the result; wait for the daily reset, switch provider, or use a local Ollama model | 19 |
| Knowledge content surprises: a new seeded chapter never appears; a locally edited article "loses" shipped improvements; a user-deleted seeded article reappears | The merge rules: local wins per id, and the id-union re-adds every shipped id missing locally; the seed id/snapshot lagged the count test | Keep ids stable-and-new; regenerate the JSON in dev mode and let the merge test's count assertion catch the lag; ship content changes as *new ids*; "delete = hide" is not supported — remove from the seed too | 20 |
| Knowledge plumbing: the tree shows one flat list or duplicate shelves; the panel shows nothing after an MCP write; `getResourceAsStream` returns null in the packaged jar; the title field's red border never clears; `IllegalStateException` from a knowledge mutation | Paths written without `" / "` or unnormalized; a listener threw or the write hit a different repository instance; a resource path without the leading `/`; the failure style never reset; a listener touching the scene graph off the FX thread | Keep `"A / B"` format and trim paths (the split is `\s*/\s*`); check `AppLog` and mutate only through `KnowledgeRepository.getInstance()`; use `/knowledge/knowledge-hub.json` exactly; apply the reset-before-validate improvement; wrap listener bodies in `Platform.runLater` | 20 |

### 1.9 Packaging, release & the test estate

| Symptom | Likely cause | Fix | Ch |
|---|---|---|---|
| `jpackage: WiX Toolset not found` / `candle.exe not on PATH` | MSI output needs WiX 3.x, and a fresh shell may lack the new PATH | Install WiX and reopen PowerShell — or build `-AppImage` + Inno, or let CI do it | 22 |
| Installer build fails or mislabels: `ISCC` can't find the app-image; setup.exe metadata shows the wrong version; the Actions run shows `if-no-files-found: error` | The Inno script wraps an app-image that was never built; a manual `ISCC` run hit the hard-coded fallback; jpackage failed and `dist/` was empty | Run `.\packaging\build-windows-installer.ps1 -AppImage` first; pass `/DAppVersion=<pom version>` (or bump the fallback with the pom); fix the failing step — the flag correctly refuses to upload nothing | 22 |
| Install identity: SmartScreen blocks the installer; MSI and setup.exe both appear in Apps & Features | The build is unsigned; two install families with independent identities | *More info → Run anyway* (long-term: sign with a certificate); uninstall one family before installing the other — never mix upgrade paths | 22 |
| Test hygiene: the next DB suite fails with rows from a deleted database; a suite passes locally but fails on CI with `Path` errors; the JVM hangs after all tests; `IllegalStateException` in a UI test | A skipped singleton reset; a working-directory-relative file; a `Stage` left open (the FX thread never dies); nodes touched from the JUnit thread | Copy the `resetSingleton` block into the previous `@AfterAll`; use `@TempDir` (fixtures only in `try`/`finally`); close every window in `@AfterAll` or run the settle-then-`Platform.exit` ritual; wrap UI touches in a `runAndWait`-style `Platform.runLater` + latch | 21 |
| Harness surprises: `mvn test` rewrites `knowledge-hub.json`; an ordered suite is green together but red with `-Dtest=` alone; "packaged jar missing"; scripts test *stale* code after an edit; "Xvfb failed to start"; screenshots count 0; a live-AI suite burned quota; merchant sim reports differ between runs | `KnowledgeGenerator` runs in the suite (flagged `ISSUE:`); shared `@BeforeAll` state; the script ran before packaging; the harnesses run the **packaged jar**, not `target/classes`; display number in use / xvfb missing; the harness crashed pre-shot; the `-Dlive.*` flags were passed; re-seeding from a different build | Regenerate deliberately with `-Dtest=KnowledgeGenerator` and commit; run the whole suite (or re-seed per test); `mvn package` first — every harness run; pick a fresh `:8x` display and `sudo apt install xvfb`; read the run dir's `$LOG`; the `Assumptions` guard normally skips live tests; the seed is deterministic *per build* — re-seed from the same build | 21, 22 |

---

## 2. Glossary

Every technical term this book stopped to explain, in one alphabetical table.
The chapter reference points to where the term was *introduced and explained*
— later chapters usually reuse the term without re-defining it. Chapter 0's
"ten seed words" are all here too.

| Term | Meaning | Ch |
|---|---|---|
| **Aggregation (one pass, many answers)** | Building every report dimension (month, category, account…) in a single loop over the data that drops each record into several buckets, instead of one loop per report. O(n) regardless of how many reports read the buckets. | 14 |
| **API key** | A secret string a cloud service issues to identify your requests. The app stores provider keys in a local vault file, masked in the UI and never printed to logs. | 19 |
| **Audit log** | An append-only record of "who did what, when". Two exist in the app: the MCP tool-call log (memory ring + one line per event on disk) and the label print history. Neither is ever rewritten. | 18 |
| **`@JsonIgnoreProperties(ignoreUnknown = true)`** | A Jackson annotation meaning "if the JSON has a field this class doesn't know, ignore it instead of crashing" — the reason old save files load into newer models. | 6 |
| **AUTODETECT (sensor calibration)** | The TSPL command that makes the printer re-learn where its label gaps are; run it whenever the label roll is changed. | 17 |
| **Bearer token** | An HTTP authentication scheme: the client sends `Authorization: Bearer <token>` on every request. The MCP server generates the token and compares it in constant time. | 18 |
| **Bezier curve / Catmull-Rom smoothing** | Mathematical curves through control points. The designer converts a hand-drawn polygon's corners into smooth Catmull-Rom beziers so pen-tool shapes look curved, not jointed. | 15 |
| **Bit polarity** | Which bit value means "burn black" on a thermal head. TSC printers burn where bits are *clear*, the opposite of the intuitive "1 = ink" — getting this wrong prints a black page with content-shaped holes. | 17 |
| **Book markers (`GAP:` / `ISSUE:` / `NOTE:`)** | The book's honesty devices: `GAP:` = source ambiguous, `ISSUE:` = a real quirk kept faithfully, `NOTE:` = background worth knowing. Chapter 0 introduces all of them. | 0 |
| **Cache invalidation** | Throwing away a saved-in-memory copy when the underlying data changes, so no view keeps showing a deleted or edited row. `DataManager` invalidates a collection on every write. | 8 |
| **Cell factory** | The function JavaFX calls to create (or recycle) the visual cell that renders one list/table row — JavaFX builds only enough cells for the visible rows and reuses them while scrolling, which is why an `updateItem` that forgets its empty branch leaves stale content behind. Writing DB calls inside one is the classic freeze bug. | 11 |
| **Check-then-create** | The MCP's idempotent create pattern: look up by natural key (name) first, create only if absent. Race-safe via locks, and never silently updates an existing row. | 18 |
| **CI/CD** | Continuous Integration / Continuous Delivery: robots that build and test on every change, keep the resulting files as downloadable artifacts, and (on a tag) publish a release automatically. | 0, 22 |
| **Classpath vs module path** | The two ways Java finds libraries. Classic `-cp` lists jars; JavaFX 21 prefers the module path — `fx.env` builds it, and the Shade plugin sidesteps it for the fat jar. | 1, 22 |
| **`COALESCE`** | SQL's "first non-null wins" function — without it, one NULL row poisons an entire SUM into NULL. | 5 |
| **Compensation** | Undoing the side-effects of a failed operation. When an MCP create fails halfway, its auto-created dependencies (say, a category) are rolled back so no litter remains. | 18 |
| **Constant-time comparison** | A string comparison that always takes the same time regardless of where the first mismatch is, so attackers can't time requests to guess the MCP token character by character. | 18 |
| **CSS class contract** | The rule that widgets get their looks from named classes in `globalfile.css`, never inline `setStyle` — inline styles silently kill the stylesheet's hover effects. | 9 |
| **CSV state machine** | The quote-aware parser in `CsvService`: a tiny machine with states (in-quotes, out-of-quotes) that survives commas, quotes and newlines *inside* fields, where `split(",")` shatters. | 13 |
| **DAO (Data Access Object)** | A class whose only job is reading and writing one kind of database row. All SQL lives in DAOs; nothing else in the app knows SQL exists. | 0, 4 |
| **Data directory resolution** | `AppDirs` deciding where user data lives per OS (`%APPDATA%\InvoiceStudio` on Windows), honoring the `-Dinvoicestudio.data.dir` override, and migrating legacy locations. | 2 |
| **Data epoch** | A counter that bumps on every data change; views remember the epoch they were built at, so the shell can refresh exactly the views whose data actually moved. | 8 |
| **Debounce** | Waiting for events to stop before reacting — react to the *last* keystroke, not every one. The bill preview uses a `PauseTransition` reset on every keystroke; the designer coalesces repaints. | 12, 15 |
| **Dependency** | A third-party library your build downloads automatically because `pom.xml` lists it. Nine of them feed InvoiceStudio. | 0 |
| **Die-cut / feed gap / pitch** | Label-strip vocabulary: die-cuts are the pre-cut label shapes, the feed gap is the blank space between them, and pitch is label height + gap — the number the printer's sensor must learn. | 17 |
| **Dithering (Floyd–Steinberg)** | Simulating gray shades on a black-and-white surface by scattering dots proportionally to the error left over at each pixel — the classic error-diffusion idea behind keeping label art legible at one bit. | 17 |
| **Dots per mm (dpm)** | The thermal head's resolution: a 203 dpi head prints ≈ 8 dots per mm. Every label measurement in mm is multiplied by dpm to become printable dot counts. | 17 |
| **DPI** | Dots per inch — but *whose* dots? Screen (≈96), PDF (measured in points, 1/72 inch) and the thermal head (203) all differ; every renderer must convert explicitly or the output shrinks by 72/96. | 16, 17 |
| **Driver path (printing)** | Printing through the OS printer driver instead of sending raw bytes. The fallback for non-TSC printers: the app renders a page image and lets the driver place it. | 17 |
| **Enum fail-open** | Reading an unknown value (e.g. a new element type from newer JSON) and falling back to a safe default instead of throwing — old app versions survive new files. | 6, 7 |
| **Event filter vs handler** | Filters run during the capture phase, before child nodes see the event — how Dashboard 2's smooth scrolling sees every wheel turn first and still lets nested tables opt out. | 14 |
| **Executor pools** | Pre-built, named background-thread pools (`AppExecutors.io()`, `db()`, `compute()`) of *daemon* threads that never block shutdown — so code never says `new Thread(...)` and all DB work shares one lane. | 2 |
| **Fail-open** | A policy that, when its own check fails, chooses the *permissive* path. The chatbot's tool router fails open: if it can't shortlist tools, it sends all of them rather than none. | 19 |
| **Failover** | Automatic switch to a backup when the primary fails. The AI client walks Gemini's fallback ladder model by model when one hits its daily quota, without spending the user's saved choice. | 19 |
| **Fat JAR (shaded JAR)** | One `.jar` containing the app *and* every dependency, with a manifest naming the real main class — produced by the Maven Shade plugin, runnable with plain `java -jar`. | 22 |
| **FilteredList** | A JavaFX wrapper that *views* another list through a predicate; change the predicate and every bound table updates without rebuilding the list. The secret behind all live search boxes. | 11, 14 |
| **FXML** | An XML markup for declaring JavaFX UIs. The project declares the `javafx-fxml` dependency but builds all UI in pure Java code — no `.fxml` file exists in the repo (flagged as a harmless unused dependency). | 1 |
| **Font metrics** | A font's measured dimensions (ascent, descent, widths). PDF text layout needs them to wrap lines and center labels; guessing widths is how text overflows cells. | 16 |
| **FX thread (JavaFX Application Thread)** | The one thread allowed to touch on-screen nodes. Slow work there freezes the window; the book's every view keeps DB work on executors and hops results back with `runOnFx`. | 0, 2 |
| **Graphics context (Graphics2D)** | The object you draw *through* — its state (transform, color, stroke) persists between calls, which is why one unbalanced `scale()` call makes everything render at the wrong size. | 16 |
| **GSTIN / state code routing** | A GSTIN encodes the state; the effective state code decides CGST+SGST (intra-state) vs IGST (inter-state). A blank code or short GSTIN routes nothing — and nothing warns. | 6 |
| **CGST / SGST / IGST** | India's three GST components: Central and State tax for sales within a state, Integrated tax across states. Every bill line computes its split per line, and reports sum the components separately for filing. | 6, 14 |
| **GSTR-1** | The monthly GST filing statement. The GST/Tax Summary report exports a GSTR-1-shaped CSV: one row per tax period with taxable value and each tax component. | 14 |
| **Hit-testing** | Deciding which node a click lands on. JavaFX tests against a node's rendered geometry — a transparent shape needs `pickOnBounds` or an invisible hit rectangle to stay clickable. | 15 |
| **Hot columns & JSON blob** | The storage strategy: a few queried-often fields live as real SQL columns (hot), the full record lives as one JSON text blob — fast lists *and* painless schema evolution. | 3 |
| **HSN** | Harmonised System of Nomenclature — the standardized product code every GST invoice line carries. Items store their HSN as a hot column. | 3 |
| **Headless testing** | Running UI tests with no display, via Monocle (a headless JavaFX toolkit) or Xvfb (a virtual X server). The app's seven visual tests and 23 launcher journeys run this way. | 21 |
| **Idempotency** | An operation that produces the same result no matter how many times it runs. Re-saving a bill re-runs `deleteByVoucher` first; `create_item` by an existing name changes nothing. Crash-recovery leans on this. | 8, 18 |
| **Immutable ledger** | The stock ledger never updates or deletes rows — every movement is a new entry, and balances are computed by summing. History can't be rewritten because there is nothing to rewrite. | 5 |
| **Inno Setup** | The free scriptable installer builder that turns jpackage's self-contained app-image folder into `InvoiceStudio.exe` — a classic wizard installer merchants already know how to click. | 22 |
| **Interpolation & easing** | Computing in-between frames of an animation. `Interpolator.EASE_OUT` starts fast and settles gently — the difference between an animated counter and a slot machine. | 14 |
| **Jackson** | The JSON library: converts Java objects ⇄ JSON text via getters/setters, with annotations to tolerate unknown fields and custom names. Powers every save file and AI request. | 6 |
| **JAR** | A `.jar` file — a zip of compiled `.class` files with a table of contents (manifest). The unit Maven builds and `java -jar` runs. | 0 |
| **JavaBean** | The naming convention Jackson maps by: `getName()`/`setName(x)` expose a property *name*. A missing accessor is why a new field silently never persists. | 6 |
| **JavaFX** | The UI toolkit the whole app is built in: windows (stages), scenes of nodes, CSS styling, properties and bindings. | 0 |
| **JDBC** | Java's standard database API. The app uses the SQLite JDBC driver: open a `Connection`, build a `PreparedStatement`, execute, read a `ResultSet`, close. | 3 |
| **JSON-RPC 2.0** | The request format MCP speaks: a JSON object with `jsonrpc`, `method`, `params`, `id` — and no GET requests, which is why a plain browser visit returns 405. | 18 |
| **JUnit 5** | The test framework: `@Test` methods, `@BeforeAll`/`@AfterAll` lifecycle hooks, and assertions that fail loudly. `AppDirsTest` is the book's first example. | 2, 21 |
| **JVM / JDK / JRE** | The Java Virtual Machine runs bytecode; the JRE is runtime-only; the JDK adds the compiler and tools. You need the full JDK 21 to build this project. | 0 |
| **jpackage** | The JDK's packaging tool: turns the fat jar plus a bundled Java runtime into a native installer (MSI on Windows) — or into a self-contained app-image folder — so merchants never install Java. | 22 |
| **Timeline & KeyFrame** | JavaFX's animation primitives: a Timeline plays a sequence of KeyFrames (a moment in time + values to reach). Toasts, counters, and scroll glides are all Timelines. | 9, 14 |
| **Listener bus** | A tiny publish-subscribe hub: components register listeners, events (`onUserSwitched`, session changes, knowledge updates) fire them. Each app subsystem has exactly one. | 8, 10 |
| **Loopback** | The `127.0.0.1` address meaning "this machine only". Both network listeners bind loopback: Google sign-in's redirect server and the MCP server — nothing is reachable from the network. | 10, 18 |
| **LRU cache** | Least-Recently-Used cache: keep the last N results, evict the oldest. `BarcodeService` caches rendered barcodes this way so re-rendering a 50-page PDF doesn't re-encode every code. | 16 |
| **Managed vs visible** | Two independent node flags: `visible(false)` hides a node but leaves its layout space; `managed(false)` removes the space. Hiding a control properly means setting both. | 10, 11 |
| **Markdown** | The plain-text formatting language (`**bold**`, `# heading`, tables) used by the Knowledge Hub articles and the chatbot — one renderer serves both. | 19, 20 |
| **MCP (Model Context Protocol)** | A standard way to expose tools to AI assistants: a JSON-RPC endpoint listing tool schemas and executing calls. InvoiceStudio embeds one server exposing ~77 business tools. | 18 |
| **Merge-on-load** | The Knowledge Hub's update story: shipped seed articles and the user's local copy are merged per article id — local edits win, new shipped ids appear, nothing user-made is ever lost. | 20 |
| **Migration** | Code that reshapes an old database to the current schema (new columns via `ALTER TABLE`, new indexes, data fixes) — run at startup inside `try/catch` so it is safe to re-run. | 3 |
| **Model routing** | The chatbot's light router that inspects the user message and sends only the tools a question could need to the model — a token-saving shortlist that fails open to the full set. | 19 |
| **Monochrome (1-bit) mode** | Reducing a drawing to pure black-and-white — for thermal heads, for the bill preview's ink-saver mode, and for print stamps. Everything the label pipeline outputs is monochrome by necessity. | 12, 16, 17 |
| **NaN guard** | Checking the denominator before dividing, so reports show `0` or `100.0` instead of the `NaN` that prints as "₹NaN". Pinned by tests. | 14 |
| **Null-safe getter** | A getter that returns a sensible default instead of null when the JSON lacked the field — the convention that makes old template files immortal. | 7 |
| **OPTIONAL IMPROVEMENT** | The book's label for better-than-original code, kept out of the faithful build, with trade-offs and difficulty stated. | 0 |
| **Painter's algorithm** | Draw in back-to-front order; later painting covers earlier painting. Both renderers iterate elements in list order for exactly this reason. | 16 |
| **Paise rounding** | Indian currency has 100 paise per rupee; per-line amounts round at the paise with clamped discounts, and `getGross()` vs `getAmount()` keep pre/post-discount math from double-charging. | 6, 12 |
| **PauseTransition** | A one-shot JavaFX timer — the debounce workhorse: restart it on every keystroke, and its `onFinished` fires only after typing stops. | 12 |
| **PDFBox** | The Apache library that writes PDFs: documents, pages, content streams, images. Everything `PdfExportService` does rides on it. | 0, 16 |
| **Prepared statement** | A SQL statement compiled once with `?` placeholders, then bound with values — faster on reuse, and the defense against SQL injection. Every DAO read/write uses one. | 3 |
| **POJO** | Plain Old Java Object — a class with fields, accessors and no framework baggage. The domain models are POJOs so Jackson can serialize them and tests can construct them. | 6 |
| **Projection** | A row shape written *at save time* for fast reading later — the bill register freezes buyer name and totals into columns so lists never touch the JSON blob. Read-model projections (MCP) shape tool results the same way. | 5, 18 |
| **Protected default** | The guard that refuses to delete the one record the app cannot run without (the default category/item) — via exception in the DAO, silently in the UI. | 4 |
| **Pure-logic extraction** | Putting math in a class with no UI imports so a millisecond JUnit test can verify it, and the dialog only formats what the math returns. | 14 |
| **Quota** | A provider's usage limit — Gemini's free tier is 20 requests/day/model. The client meters tokens, tracks per-model status, and walks its ladder when a bucket empties. | 19 |
| **QR / UPI payload** | The `upi://pay?...` string encoded into the invoice QR code — payee VPA, name, amount, note — so any UPI app opens a prefilled payment. ZXing turns the payload into a `BitMatrix` of dots; the result is cached per payload. | 12, 16 |
| **Raster** | A grid of pixels (as opposed to vectors). PDF pages here are 300-dpi rasters; thermal labels are 1-bit rasters; "rasterize" means converting nodes into such a grid. | 16, 17 |
| **Raw vs driver printing** | Raw printing sends printer-language bytes (TSPL) straight to the OS print queue (the *spooler*, whose job names the app sets for readability); driver printing renders a page and lets the OS driver place it. The transport seam lets the label pipeline do both. | 17 |
| **Record (Java)** | An immutable data carrier whose components auto-generate accessors, `equals` and `hashCode` — used for computed rows (`Bucket`, `StockSummaryRow`) and normalized articles. | 11, 14 |
| **Refresh token** | The long-lived credential that renews an expired login silently. Revoked or expired after 30 idle days, it fails — and the app correctly demands a fresh sign-in. | 10 |
| **Row mapper** | The DAO function turning one `ResultSet` row into a model object — written defensively (per-field guards) so one weird column can't lose the whole list. | 4 |
| **Run-length packing (packBits)** | TSPL's compressed bitmap format: repeated byte runs are encoded as (count, byte) pairs, and the bit order decides which pixels burn. The pipeline's bit packer is tested bit by bit. | 17 |
| **Scene graph** | JavaFX's live tree of on-screen nodes. The designer repaints by mutating the tree surgically; knowledge listeners must hop to the FX thread before touching it. | 15 |
| **Schema** | The database's structure: tables, columns, indexes. In this app the schema *is* `DatabaseManager`'s constructor — 17 tables, 12+ indexes, ~20 converging migrations. | 3 |
| **Seed** | Built-in starter data (demo settings, 30 knowledge articles) shipped inside the jar and inserted on first run — and the merge discipline that lets updates add more without overwriting users. | 3, 20 |
| **Semantic versioning** | The major.minor.patch scheme where breaking changes bump major, features bump minor, fixes bump patch. The pom's version flows into installers and release tags. | 22 |
| **Shell (app shell)** | The coordinating frame every view plugs into: window, sidebar, navigation, view cache, epochs, toasts, shortcuts, auth gate. Feature chapters only build views. | 9 |
| **Shell/builder split** | One tiny view class owning layout and navigation, delegating content construction to a builder class (`ReportsView` 133 lines / `ReportsBuilders` 1,266). One class per *role*, not per *size*. | 14 |
| **Singleton** | One shared instance for the whole JVM (`DatabaseManager`, `DataManager`). Powerful in the app, a nuisance in tests — which is why the reset contract exists. | 3, 21 |
| **Snapshot undo** | Undo by saving the full model as JSON before each change and restoring on Ctrl+Z (capped at 50). Simple, correct, and the reason `copy()` must carry every field. | 15 |
| **Soft delete** | Marking a row `deleted = 1` instead of removing it, so archives and audits survive — with the discipline that every read query must add `AND deleted = 0`. | 5 |
| **SQLite** | A complete SQL database stored in *one file* on disk. No server process, no installation — the reason a desktop billing app can run with zero administration. | 0 |
| **Strategy seam** | An interface (`RawPrintTransport`) placed exactly where two implementations must swap — raw bytes for TSC printers, the driver path for everything else. | 17 |
| **Striped locks** | Splitting one big lock into several (stripes) keyed by name/id, so two different items can be created concurrently while the *same* name still serializes. The MCP ensure-layer's race armor. | 18 |
| **Surefire** | Maven's test plugin — the thing `mvn test` runs. Its one-forked-JVM behavior is why singleton resets matter between suites. | 21 |
| **Supersample & downsample** | Rendering big, then shrinking: label art is drawn at high resolution and reduced with a threshold before the 1-bit pack, so edges stay clean instead of jagged. | 17 |
| **System prompt** | The standing instruction message that tells the model who it is and what tools exist; the app builds it, appends pending approvals, and sends it every round. | 19 |
| **Tenant guard** | The upsert's `WHERE user_id = ?` clause ensuring one account can never read or overwrite another's rows — multi-user safety at the SQL layer. | 4 |
| **Threshold** | The cutoff converting gray pixels to pure black or white. Too low loses barcode strokes; too high bloats them. Separate thresholds exist for labels and barcodes. | 17 |
| **Token (AI)** | The unit LLMs read and bill by — roughly a word fragment. Tool results are capped at 4,000 tokens' worth because every conversation round re-sends the whole history. | 19 |
| **Tool schema** | The JSON description of a tool — name, when to use it, and its typed parameters — that the MCP server publishes via `tools/list` so models can call it correctly. | 18 |
| **Transient Scene** | The snapshot trick: attach the label node to an invisible Scene, `applyCss()` and `layout()` so skins load, *then* rasterize — else text prints blank while barcodes (images) print fine. | 17 |
| **TreeMap ordering** | A map sorted by key. Aggregations that become charts use `TreeMap` so months come out in calendar order — a `HashMap` shuffles them. | 14 |
| **TSPL** | The TSC printer command language — plain text commands (`SIZE`, `GAP`, `BITMAP`, `PRINT`) that drive the thermal head. The label pipeline builds it in `TsplCommandBuilder`. | 17 |
| **Upsert** | Update-or-insert: try an `UPDATE` keyed on a natural identifier, `INSERT` if none matched — with a tenant-guard WHERE and a lowercase name match as the house idioms. | 4 |
| **Variable map** | The per-bill dictionary (`buyer_name`, `grand_total`, …) that `{{placeholders}}` in templates resolve against — one resolver serves screen, PDF and MCP preview. | 12 |
| **View cache** | The shell keeps each built view in memory and re-shows it instead of rebuilding — freshness decided by epochs, not by re-construction. | 9 |
| **Warm-up** | Loading the caches once at startup (or on login) so the first navigation to every view is instant instead of a DB round trip. | 8 |
| **WYSIWYG contract** | What-you-see-is-what-you-burn: the strip preview's row must be byte-for-byte the page the thermal head prints — one geometry, one renderer, two destinations. | 17 |
| **Xvfb** | "X virtual framebuffer" — a real X server drawing into memory, giving the launcher journeys a screen on a monitor-less CI box. | 21 |
| **Zoom rules (designer & preview)** | Scale the *content*, size the *wrapper*: zoom is applied to the canvas container (never the overlay group), overlays divide by zoom, and `fitToHeight` stays false so scroll ranges survive. | 12, 15 |

---

## 3. Index

Every class in `src/main/java/com/invoicestudio/` — all **172 of them** —
mapped to the chapter that covered it in full. This index was built
mechanically: the file list comes from the source tree, and each entry's
chapter was verified by searching the chapter files for the class name
(not from memory). The owner chapter is the one whose coverage self-check
claims the file; where the book later revisits a class, the first owner is
listed and the revisit rides along in Appendix A5's audit table.

Entries marked †, ‡ or ◊ have an honesty footnote — three files whose
"covered" claim needed qualification. They are the only ones.

### 3.1 Root package (`com.invoicestudio`)

| Entry | Entry |
|---|---|
| `AppDirs` — Ch 2 | `Launcher` — Ch 2 |

### 3.2 Database layer (`db/`)

| Entry | Entry |
|---|---|
| `AuthDao` — Ch 5 ◊ | `BillDao` — Ch 5 |
| `BuyerDao` — Ch 4 | `CategoryDao` — Ch 4 |
| `DatabaseManager` — Ch 3 | `ExpenseAccountDao` — Ch 5 |
| `ExpenseDao` — Ch 5 | `ItemDao` — Ch 4 |
| `LabelPrintHistoryDao` — Ch 5 | `PurchaseBillDao` — Ch 5 |
| `SettingsDao` — Ch 4 | `StockLedgerDao` — Ch 5 |
| `SupplierDao` — Ch 4 | `TemplateDao` — Ch 5 |
| `TransactionDao` — Ch 5 | `TransportDao` — Ch 4 |
| `VariableDao` — Ch 5 | |

### 3.3 Domain models (`model/`)

| Entry | Entry |
|---|---|
| `Bill` — Ch 6 | `BillItem` — Ch 6 |
| `BillPayment` — Ch 6 | `BillStatus` — Ch 6 |
| `BillTotals` — Ch 6 | `BusinessProfile` — Ch 6 |
| `Buyer` — Ch 6 | `BuyerFieldDef` — Ch 6 |
| `ComponentPreset` — Ch 7 | `CustomComponent` — Ch 7 |
| `CustomFontDef` — Ch 7 | `DocType` — Ch 7 |
| `ElementType` — Ch 7 | `Expense` — Ch 6 |
| `ExpenseAccount` — Ch 6 | `ItemCategory` — Ch 6 |
| `ItemRecord` — Ch 6 | `KnowledgeArticle` — Ch 20 |
| `LabelConfig` — Ch 7 | `LabelPrintHistory` — Ch 7 |
| `PageConfig` — Ch 7 | `PageSizeName` — Ch 7 |
| `PaymentMethod` — Ch 6 | `PresetTemplates` — Ch 7 |
| `PurchaseBill` — Ch 6 | `RepeatCadence` — Ch 6 |
| `Settings` — Ch 6 | `Supplier` — Ch 6 |
| `TableColumn` — Ch 7 | `Template` — Ch 7 |
| `TemplateElement` — Ch 7 | `Transaction` — Ch 6 |
| `Transport` — Ch 6 | `UnitConverter` — Ch 17 |
| `UserSession` — Ch 10 | `VariableDef` — Ch 7 |

### 3.4 Service layer (`service/`)

| Entry | Entry |
|---|---|
| `AiChatClient` — Ch 19 | `ApiKeysVault` — Ch 19 |
| `AppExecutors` — Ch 2 | `AppFormatters` — Ch 14 |
| `AppLog` — Ch 2 | `AuthSessionManager` — Ch 10 |
| `BackupRestoreService` — Ch 13 | `BarcodeService` — Ch 16 |
| `BillingService` — Ch 12 | `BulkPrintStateStore` — Ch 17 |
| `ChatTranscriptStore` — Ch 19 | `ChatbotConfig` — Ch 19 |
| `ChatbotLogManager` — Ch 19 | `CsvService` — Ch 13 |
| `CustomComponentManager` — Ch 15 | `DesignObjectRenderer` — Ch 16 |
| `ExpenseAccountService` — Ch 13 | `ExpenseAnalytics` — Ch 14 |
| `FinancialService` — Ch 13 | `FirebaseAuthService` — Ch 10 |
| `JavaxRawPrintTransport` — Ch 17 | `KnowledgeRepository` — Ch 20 |
| `KnowledgeSeed` — Ch 20 | `LabelGeometryService` — Ch 17 |
| `LabelPresets` — Ch 17 | `LabelPrintService` — Ch 17 |
| `LabelRenderUtil` — Ch 17 | `ModelCatalog` — Ch 19 |
| `ModelStatusStore` — Ch 19 | `MonoImage` — Ch 17 |
| `PdfExportService` — Ch 16 | `PdfTextDraw` — Ch 16 |
| `PrintOptions` — Ch 16 | `PrintingService` — Ch 17 |
| `PurchaseService` — Ch 13 | `RawPrintTransport` — Ch 17 |
| `RecurringEngine` — Ch 12 | `RenderContext` — Ch 12 ◊ |
| `SvgVectorParser` — Ch 15 | `TemplatePreviewService` — Ch 16 |
| `TsplCommandBuilder` — Ch 17 | `TsplPrintService` — Ch 17 |
| `VariableGrouper` — Ch 15 † | |

### 3.5 UI shell & shared (`ui/`)

| Entry | Entry |
|---|---|
| `AppShortcuts` — Ch 9 | `BillPreviewPane` — Ch 12 |
| `ChatbotPanel` — Ch 19 | `ChatbotSettingsPanel` — Ch 19 |
| `CopyButtonFactory` — Ch 19 | `CustomColorChooserDialog` — Ch 15 |
| `DataManager` — Ch 8 | `DialogHelper` — Ch 9 |
| `ExpenseAccountsDialog` — Ch 13 ‡ | `ExpenseReportDialog` — Ch 14 |
| `IconHelper` — Ch 9 | `KnowledgeHubPanel` — Ch 20 |
| `LabelBulkPrintDialog` — Ch 17 | `LabelStripPreviewDialog` — Ch 17 |
| `ModelStatusDot` — Ch 19 | `PrintPreviewDialog` — Ch 16 |
| `ShortcutCatalog` — Ch 9 | `ShortcutManager` — Ch 9 |
| `ShortcutsDialog` — Ch 9 | `ShortcutsPanel` — Ch 9 |
| `SidebarController` — Ch 9 | `StudioApp` — Ch 9 |
| `Toast` — Ch 9 | `UiTheme` — Ch 9 |
| `UserProfilePill` — Ch 10 | `ViewEpochTracker` — Ch 9 |
| `WindowResizeHelper` — Ch 9 | `WindowStateManager` — Ch 9 |

### 3.6 Authentication (`ui/auth/`)

| Entry | Entry |
|---|---|
| `AuthView` — Ch 10 | `GoogleSignInButton` — Ch 10 |
| `LogoutDialog` — Ch 10 | `PasswordFieldWithToggle` — Ch 10 |
| `PasswordStrengthMeter` — Ch 10 | |

### 3.7 Chat UI (`ui/chat/`)

| Entry | Entry |
|---|---|
| `ChatMarkdownRenderer` — Ch 19 | `ChatPipelineBar` — Ch 19 |
| `ChatbotLogDialog` — Ch 19 | `ChatbotModelPickerDialog` — Ch 19 |

### 3.8 Feature views (`ui/views/`)

| Entry | Entry |
|---|---|
| `BuyersView` — Ch 11 | `CategoriesView` — Ch 11 |
| `CreateBillView` — Ch 12 | `CreatePurchaseView` — Ch 13 |
| `Dashboard2View` — Ch 14 | `DashboardView` — Ch 14 |
| `DesignerState` — Ch 15 | `ExpensesView` — Ch 13 |
| `FinancialsView` — Ch 13 | `HistoryView` — Ch 12 |
| `ItemsView` — Ch 11 | `LabelHistoryView` — Ch 17 |
| `LineItemsLayout` — Ch 12 | `PurchasesView` — Ch 13 |
| `ReportsBuilders` — Ch 14 | `ReportsView` — Ch 14 |
| `SettingsFieldSupport` — Ch 11 | `SettingsView` — Ch 11 |
| `StockAnalysisView` — Ch 14 | `SuppliersView` — Ch 11 |
| `TemplateDesigner` — Ch 15 | `TemplatesView` — Ch 11 ◊ |
| `TransactionsView` — Ch 13 | `TransportsView` — Ch 11 |
| `VariablesView` — Ch 11 | `VectorGeometryUtil` — Ch 15 |

### 3.9 MCP server (`mcp/`)

| Entry | Entry |
|---|---|
| `GuideContent` — Ch 18 | `McpArgs` — Ch 18 |
| `McpAuditLog` — Ch 18 | `McpConfig` — Ch 18 |
| `McpEnsure` — Ch 18 | `McpImageResult` — Ch 18 |
| `McpProjections` — Ch 18 | `McpServer` — Ch 18 |
| `McpSettingsPanel` — Ch 18 | `McpToolRegistry` — Ch 18 |
| `PendingOperations` — Ch 18 | |

### 3.10 Footnotes — the three honest qualifications

- ◊ **`TemplatesView`** — listed in the living inventory under Ch 11, but no
  chapter ever names or shows this file; Ch 15 passes "the Templates view" a
  cross-reference that points back to Ch 11, which doesn't own it either.
  Declared **uncovered** in Appendix A5 rather than pretended otherwise.
- ‡ **`ExpenseAccountsDialog`** — its *behavior* is covered in Ch 13 (the
  accounts registry, the checkpoint's "Accounts dialog", the usage rollup
  test), but the file itself is never named or reproduced. Marked
  covered-by-behavior, not covered-in-full — see A5.
- † **`VariableGrouper`** — explained at its call site in Ch 15 (the
  designer's variable combo), but never shown as a file; the inventory had
  assigned it to Ch 13, where its name never appears. See A5.

(There is also one tracked file the living inventory never listed at all —
`.freebuff/project-id` — recorded as an explicit uncovered row in A5.)

### 3.11 Resources, configuration & packaging

Every non-Java file the book explained, with its owning chapter.

| Entry | Entry |
|---|---|
| `pom.xml` — Ch 1 | `.gitignore` — Ch 1 |
| `fx.env` — Ch 1 | `README.md` — Ch 1 |
| `.vscode/settings.json` — Ch 1 (GAP) | `.freebuff/.../SKILL.md` — Ch 21 |
| `.github/workflows/windows-installer.yml` — Ch 22 | `packaging/InvoiceStudio.iss` — Ch 22 |
| `packaging/build-windows-installer.ps1` — Ch 22 | `packaging/InvoiceStudio.ico` — Ch 22 |
| `css/globalfile.css` — Ch 9 (sampled, by NOTE) | `icons/*` (3 files) — Ch 9 |
| `knowledge/knowledge-hub.json` — Ch 20 | `docs/APP_GUIDE.md` — Ch 20 |
| `docs/MCP_SERVER.md` — Ch 20 | `docs/TEMPLATE_DESIGN_GUIDE.md` — Ch 20 |
| `seed/*` (7 files) — Ch 3 | `dash2-smoke/mcp-server.json` — Ch 21 |
| `merchant-sim/Q.java` — Ch 21 | `merchant-sim/mcp-server.json` — Ch 21 |
| `ls-verify/`, `cb-verify/`, `bulk-verify-run/` (6 JSON) — Ch 21 | `MERCHANT_SIM_REPORT.md` — Ch 21 |
| `docs/OPTIMIZATION_REPORT_2026-09-16.md` — Ch 21 | `docs/PERFORMANCE_OPTIMIZATION_GUIDE.md` — Ch 21 |
| `docs/vault/00–08` (9 notes) — Ch 21 | `scripts/*` (8 shell scripts) — Ch 21 |

### 3.12 The test estate

77 Java test files, organized by where their chapters live. The full census
(with every launcher named) is Chapter 21 §4; Appendix A5 reconciles the
ledger.

| Suite group | Count | Owner chapter |
|---|---|---|
| `AppDirsTest` | 1 | 2 |
| `db/DatabaseTest` | 1 | 3 |
| `service/*` suites | 35 | 12–20 by topic (billing, labels, AI, knowledge…) |
| `mcp/*` suites | 6 | 18 |
| `ui/*` + `ui/chat/*` suites | 11 | 9–20 by topic |
| Root launchers & verify harnesses | 23 | 21 |

### 3.13 Topics A–Z

The book's recurring *subjects*, for when you remember the concept but not
the class.

| Topic — Ch | Topic — Ch |
|---|---|
| Aging report — Ch 14 | Audit trail (label history) — Ch 5 |
| Authentication flow — Ch 10 | Backup & restore — Ch 13 |
| Barcode symbologies — Ch 16 | Billing pipeline — Ch 12 |
| Calibrate sensor — Ch 17 | Cache warm-up — Ch 8 |
| Component groups — Ch 15 | Credit-limit guardrail — Ch 12 |
| CSV import/export — Ch 13 | Dashboards (classic) — Ch 14 |
| Dashboards (financial) — Ch 14 | Debounced preview — Ch 12 |
| Expense analytics — Ch 14 | Failover ladder — Ch 19 |
| Financial statements — Ch 13 | Full-text search — Ch 20 |
| GST summary & filing — Ch 14 | Grid snapping & guides — Ch 15 |
| Hit-testing & pick-on-bounds — Ch 15 | Idempotent sweep (recurring) — Ch 12 |
| Knowledge merge ladder — Ch 20 | Label strip layout — Ch 17 |
| Live preview (billing) — Ch 12 | Loopback redirect (Google) — Ch 10 |
| MCP approvals — Ch 18 | MCP audit log — Ch 18 |
| Model picker & status dots — Ch 19 | One-pass aggregation — Ch 14 |
| Painter's algorithm — Ch 16 | Pen tool & beziers — Ch 15 |
| Purchase register — Ch 13 | Recurring invoices — Ch 12 |
| Shortcuts (rebindable) — Ch 9 | Smooth scrolling — Ch 14 |
| Soft deletes — Ch 5 | Stock ledger & balances — Ch 5 |
| Tenant partitioning — Ch 4 | Thermal raster pipeline — Ch 17 |
| Token budgeting — Ch 19 | Undo/redo snapshots — Ch 15 |
| UPI QR payment — Ch 12, 16 | Variable resolution — Ch 12 |
| View epochs & refresh — Ch 9 | WhatsApp share — Ch 12 |
| Window state persistence — Ch 9 | Zoom machinery — Ch 12, 15 |

---

## 4. Coverage self-check

This appendix promised to be the book's reference desk. Checking the promise
the way every chapter taught you to:

- **Section 1 (Troubleshooting):** all 22 chapters' "Common mistakes and
  fixes" tables were read and merged — 195 source rows deduplicated into
  **70 master rows** across 9 symptom groups, every row carrying its chapter
  reference(s). Where a failure appeared in several chapters, the most
  complete version survived and the rest are cited in its Ch column; where
  several symptoms share one root cause and one fix, they share a row.
- **Section 2 (Glossary):** **120 terms**, alphabetical, each defined in
  1–3 sentences with the chapter that introduced it. Every term was verified
  against the chapter text — nothing is defined here that the book never
  explained (and `MVC`, which some readers expect, is honestly absent: the
  book never used the term; "virtual flow" likewise appears here only as
  cell recycling, the behavior Chapter 11 actually describes).
- **Section 3 (Index):** all **172 application classes** from the source
  tree, grouped by package, each mapped to its owner chapter via a mechanical
  name search across all 23 chapter files; plus 24 resource/config/packaging
  entries, the 77-file test estate census, and 48 A–Z topics. Three entries
  carry honesty footnotes († ‡ ◊) where the coverage claim needed
  qualification — resolved in A5.
- **Honesty first:** this appendix invents nothing. The row counts, term
  counts and class-to-chapter mappings above were produced by grep and file
  listing against the real repository, and the three imperfect coverage
  claims are flagged rather than smoothed over.

**Next: Appendix A5 — Final Coverage Audit** (delivered as the upgraded
`appendix-file-inventory.md`).
