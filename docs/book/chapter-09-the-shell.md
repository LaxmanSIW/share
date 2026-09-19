# Chapter 9 — The Shell: Window, Theme, Sidebar, Navigation

> **Part 5 of InvoiceStudio: Zero to Finished Product**
> Files covered in full this chapter: `ui/StudioApp.java` (807 lines),
> `ui/SidebarController.java`, `ui/AppShortcuts.java`, `ui/WindowResizeHelper.java`,
> `ui/WindowStateManager.java`, `ui/UiTheme.java`, `ui/IconHelper.java`,
> `ui/Toast.java`, `ui/DialogHelper.java`, `ui/ShortcutCatalog.java`,
> `ui/ShortcutManager.java`, `ui/ViewEpochTracker.java` (+ its test),
> `ui/ShortcutsPanel.java`, `ui/ShortcutsDialog.java`, `ui/UserProfilePill.java`
> — all read from the repository. Also introduced: `resources/css/globalfile.css`
> (the 3,774-line stylesheet that styles everything).
> Goal at the end: you have the complete application frame — window behaviour, theme
> pipeline, navigation with instant cached views, background refresh, shortcuts, and
> the chatbot overlay — with every feature view slotting into it in later chapters.

---

## 1. Chapter goal

By the end of this chapter you will have built the **shell**: the single JavaFX
`Application` subclass that owns the window, wires the theme, mounts the sidebar,
switches between cached views with a fade, refreshes stale data in the background,
handles every keyboard shortcut, and hosts cross-cutting overlays (toasts, the F1 help,
logout, the AI chatbot). Every view from Chapter 11 onward is a guest in this house.

## 2. Story intro

A **hotel** is a good analogy for an application shell. Guests (views) come and go; the
building (window frame), the lobby directory (sidebar), the electricity and plumbing
(theme, executors), and the concierge (navigation controller) stay. A good hotel
remembers your room temperature (persisted window geometry), never makes you wait at
reception when your room is ready (cached views), and quietly refreshes the fruit
basket while you're at lunch (background re-warm).

The shell's defining decision is **coordination only**: the class doc says it outright
("skill rule 5.2 role 1 — coordination only"). `StudioApp` builds no business UI — it
delegates the sidebar to `SidebarController`, shortcuts to `AppShortcuts`, window state
to `WindowStateManager`, and caches data via `DataManager`. Each collaborator is small
enough to test; the shell just routes.

## 3. Concepts first

- **`Application` / `Stage` / `Scene`** — JavaFX's trio: `Application` is the app
  lifecycle; the `Stage` is the OS window; the `Scene` is the node tree inside it.
- **JavaFX Application Thread (FX thread)** — the *only* thread allowed to touch scene
  nodes. Long work off it; results delivered with `Platform.runLater(...)`.
- **BorderPane / StackPane / VBox** — layout containers: `BorderPane` = north/south/
  east/west/centre slots; `StackPane` = layers (used to overlay toasts/chatbot on
  content); `VBox` = vertical stack.
- **Stylesheets** — JavaFX CSS files attached to a `Scene`. Class selectors
  (`.gold-btn`) beat type selectors; **inline `setStyle()` beats everything** — the bug
  `UiTheme`'s "GOLDEN RULE" exists to prevent (hover states die under inline styles).
- **`Preferences` API** — Java's built-in key-value store backed by the OS registry
  (Windows) / plist (macOS); used for window geometry and shortcut bindings.
- **KeyCombination / accelerators** — `Scene.getAccelerators()` maps key combos to
  `Runnable`s; the app-level keyboard engine is built on it.
- **Stale-while-revalidate** — serve cached content instantly, revalidate in the
  background; Chapter 8's epoch counter is the freshness signal.
- **Debounce** — collapse a burst of events into one action after quiet time
  (`WindowStateManager`'s live geometry save).

## 4. Files in this chapter

| File | Lines | Role |
|---|---|---|
| `ui/StudioApp.java` | 807 | The `Application`: boot, auth gate, nav API, cache, refresh pill, chatbot, logout |
| `ui/SidebarController.java` | ~250 | Sidebar buttons, Catalog popup, active highlighting |
| `ui/AppShortcuts.java` | ~120 | Registers all rebindable actions; designer quick-jumps |
| `ui/ShortcutManager.java` | ~220 | Registry + validation + persistence + accelerator install |
| `ui/ShortcutCatalog.java` | ~90 | Contextual (non-rebindable) shortcut text for help UIs |
| `ui/ShortcutsPanel.java` / `ShortcutsDialog.java` | ~350 | Settings tab and F1 overlay rendering the catalog |
| `ui/WindowStateManager.java` | ~110 | Persist/restore window geometry with off-screen guards |
| `ui/WindowResizeHelper.java` | ~100 | Edge-drag resizing for undecorated windows |
| `ui/UiTheme.java` | ~280 | Factory of themed widgets; the no-inline-style golden rule |
| `ui/IconHelper.java` | ~390 | SVG-path icon factory + glyph fallbacks |
| `ui/Toast.java` | ~90 | Fade-in/stay/fade-out notifications |
| `ui/DialogHelper.java` | ~110 | Dialog theming, min-size anti-flicker, cached app icon |
| `ui/ViewEpochTracker.java` | ~50 | Per-view freshness bookkeeping (pure logic) |
| `ui/UserProfilePill.java` | ~180 | Sidebar user chip + account menu |
| `css/globalfile.css` | 3,774 | The entire visual language (introduced; sampled, not listed) |

## 5. Step-by-step build

### 5.1 `StudioApp.java` — fields and boot

```java
public class StudioApp extends Application {
    private Stage primaryStage;
    private StackPane rootPane;          // everything overlays here
    private BorderPane mainLayout;       // sidebar (left) + content (center)
    private StackPane mainContentPane;   // where views live

    private DataManager data;
    private BackupRestoreService backupService;
    private PrintingService printingService;

    private final Map<String, Node> viewCache = new HashMap<>();
    private final ViewEpochTracker viewEpochs = new ViewEpochTracker();
    private Label refreshPill;
    private boolean refreshInProgress;
    private final Map<String, Runnable> pendingRefreshers = new LinkedHashMap<>();

    private final SidebarController sidebarController = new SidebarController(this);
    private final AppShortcuts shortcuts = new AppShortcuts(this);
    private UserProfilePill userProfilePill;

    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "invoicestudio-db");
        t.setDaemon(true);
        return t;
    });
```

- **`rootPane` is a StackPane** — a deliberate layering choice: the content is layer 0,
  and the chatbot FAB, chatbot panel, toasts, refresh pill, F1 overlay and logout
  dialog are all later layers positioned absolutely over it. One container, no popup
  windows for in-app chrome.
- **`dbExecutor` is a *single*-thread executor** named `invoicestudio-db`, daemon:
  SQLite writes should serialise (single-writer DB, Ch. 3) and one worker gives that
  for free; daemon means it never blocks JVM exit (Ch. 2's rule).
- `viewCache` + `viewEpochs` implement stale-while-revalidate; `pendingRefreshers`
  handles fast A→B navigation while A's refresh is mid-flight (5.4).

### 5.2 `start(Stage)` — the boot sequence

The 130-line `start()` reads as a script:

1. **Frame**: build `rootPane` → `mainLayout` (BorderPane, class `main-layout`) →
   `mainContentPane` (centre) → `mainLayout.setLeft(sidebarController.buildSidebar())`
   → `rootPane.getChildren().add(mainLayout)` → `installChatbot()`.
2. **Scene + theme**: `new Scene(rootPane, 1440, 900)`, install shortcuts, attach
   `/css/globalfile.css` (guarded — a missing stylesheet must not crash boot).
3. **Window**: title, min 1024×640, icon from `/icons/invoice-mark.png` (wordmark
   fallback), then `new WindowStateManager().applyAndTrack(stage, 1440, 900, 1024, 640)`.
4. **Global icon safety net**:

```java
Window.getWindows().addListener((ListChangeListener<Window>) change -> {
    while (change.next()) {
        for (Window w : change.getAddedSubList()) {
            if (w instanceof Stage s) DialogHelper.applyAppIcon(s);
        }
    }
});
```

Every window the app *ever* opens inherits the logo unless it set its own — one
listener prevents an entire class of "default Java cup icon" regressions.

5. **Async hydration** (the heart):

```java
showLoading();                       // "⌛ Preparing your workspace…"
dbExecutor.execute(() -> {
    UserSession session = data.auth().getActiveSession();
    if (session != null && session.isExpired()) {
        session = FirebaseAuthService.getInstance().refreshSession(session);  // may fail
        if (failed && !session.isRememberMe()) session = null;
    }
    Platform.runLater(() -> {
        if (session != null) {
            AuthSessionManager.setActiveSession(session);
            dbExecutor.execute(() -> data.seedIfEmpty());   // Ch. 8
            showDashboardInternal();                        // instant cached paint
            checkRecurringSweepAsync();                     // due invoices → toast
            ExpenseAccountService.backfillFromHistoryAsync(null);
            data.warmCachesAsync(dbExecutor, () -> { });    // Ch. 8 warm pass
        } else {
            showAuthScreen(AuthView.AuthState.SIGN_IN);     // Chapter 10
        }
    });
});
```

Note the ordering discipline: the *auth check* (network for refresh) runs on the DB
executor; the *UI decision* hops to the FX thread; *seeding* goes back to the worker.
`showDashboardInternal()` calls `setView(..., animate=false)` so the first paint has no
fade. The comment on warm-up records a design fact: cache warming is read-only and
never bumps the epoch, and each view records its own epoch at build time — so warming
needs no bookkeeping here.

### 5.3 `stop()` and services

```java
@Override public void stop() {
    McpServer.shutdown();          // the AI tool server must die with the app
    AppExecutors.shutdownAll();    // shared pools (Ch. 19 chat, Ch. 12 preview…)
    dbExecutor.shutdownNow();
}
```

`initServices()` (called first in `start`) creates `DatabaseManager.getInstance()`,
`DataManager.init(db)`, the backup and printing services — and
`startMcpIfConfigured()`: if `mcp-server.json` says `autoStart`, the embedded MCP HTTP
server (Ch. 18) boots *before* the window shows.

### 5.4 Navigation: `setView`, `cached`, `refreshViewAsync`

```java
private void setView(String id, Node viewNode, boolean animate) {
    sidebarController.updateNavActive(id);
    Node content = viewNode;
    if (content instanceof VBox && !(content instanceof SettingsView)) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true); scroll.setFitToHeight(false);
        scroll.getStyleClass().add("scroll-pane");
        content = scroll;
    }
    mainContentPane.getChildren().setAll(content);
    if (animate) { /* 150 ms FadeTransition 0→1 */ }
}
```

- Bare `VBox` roots get auto-wrapped in a `ScrollPane` so long pages scroll
  (`SettingsView` opts out — it manages its own scrolling). `setAll` (not `add`)
  replaces the single content child.
- The 150 ms fade is the only animation on navigation — fast enough to feel instant.

```java
private Node cached(String id, Supplier<Node> factory, Runnable refresher) {
    Node view = viewCache.get(id);
    if (view == null) {
        view = factory.get();
        viewCache.put(id, view);
        viewEpochs.markRefreshed(id, data != null ? data.dataEpoch() : Long.MIN_VALUE);
    } else if (refresher != null && data != null
            && viewEpochs.needsRefresh(id, data.dataEpoch())) {
        refreshViewAsync(id, refresher);
    }
    return view;
}
```

The stale-while-revalidate core: build once and mark the epoch; on revisit, ask the
tracker (Chapter 8's counter vs *this view's* last read — `ViewEpochTracker`'s doc
records the exact bug the old single-global flag had). The stale view is returned
**immediately**; the refresh lands in the background:

```java
private void refreshViewAsync(String viewId, Runnable refresher) {
    if (refreshInProgress) { pendingRefreshers.put(viewId, refresher); return; }
    refreshInProgress = true;
    showRefreshPill();                                  // "⟳  Refreshing…"
    dbExecutor.execute(() -> {
        data.warmCachesNow();                           // background re-warm (Ch. 8)
        Platform.runLater(() -> {
            try {
                refresher.run();                        // view re-reads into live nodes
                viewEpochs.markRefreshed(viewId, data.dataEpoch());
                for (var entry : pendingRefreshers.entrySet()) {
                    entry.getValue().run();
                    viewEpochs.markRefreshed(entry.getKey(), data.dataEpoch());
                }
                pendingRefreshers.clear();
            } catch (Exception e) { AppLog.error("View refresh failed", e); }
            finally { hideRefreshPill(); refreshInProgress = false; }
        });
    });
}
```

Queue-drain guarantees a view visited *during* someone else's refresh is refreshed too,
never silently skipped. The pill is `mouseTransparent` — it never steals clicks.

### 5.5 The navigation API — 17 cached views + 6 fresh ones

The public `show*()` methods follow one shape:

```java
public void showHistory() {
    setView("history", cached("history",
            () -> new HistoryView(this),
            () -> ((HistoryView) viewCache.get("history")).refresh()));
}
```

*Build* is a constructor; *refresh* is a lambda calling that view's own
`refresh()`/`reload()` — each view owns its data re-read (Chapters 11–14 define them).
Six views are deliberately **never cached**:

- `showTemplateDesigner` — the designer is stateful per template ("always a fresh
  instance" comment);
- `showCreateBill(...)` / `editBill` / `duplicateBill` / `convertBill` / `repeatBill` —
  forms holding unsaved state must reset on entry;
- `showCreatePurchase` / `showEditPurchase` — same rule.

`duplicateBill`/`convertBill`/`repeatBill` show the clone recipe: new bill, fresh
number via `BillingService.nextBillNo(settings)`, today's date, copy doc type/notes/
buyer/variables/items/totals (`convertBill` forces `DocType.INVOICE`; `repeatBill`
delegates to `BillingService.repeatBill`, Ch. 12).

`reloadAllData()` (the F5 action) simply re-invokes the `show*()` for the current
sidebar view — thanks to the epoch system a forced reload is just "re-run navigation".

### 5.6 Chatbot overlay + logout + auth screens

The chatbot (full treatment in Chapter 19) has its shell wiring here:
`installChatbot()` builds a 52×52 round gold FAB (id `chatbot-fab`, tooltip, hover
colour swap via style replacement), `refreshChatbotIcon()` adds/removes it per the
Settings switch, and `toggleChatbot()` shows the config toast + jumps to Settings when
no API key exists, otherwise mounts `ChatbotPanel` pinned `CENTER_RIGHT` with height
bound to the shell.

`promptLogout()` overlays `LogoutDialog`; on confirm, `performLogout()` clears the
session **on the db executor**, then on the FX thread `viewCache.clear()`,
`viewEpochs.clear()`, and shows the auth screen. `onAuthenticationSuccess()` restores
`mainLayout`, re-seeds, and shows the dashboard.

### 5.7 `SidebarController.java`

- `buildSidebar()` assembles: brand block (icon + "InvoiceStudio / BILL DESIGN & PRINT",
  click → dashboard), section labels (WORKSPACE / SALES / PURCHASE & EXPENSES /
  INSIGHTS / DIRECTORY & CATALOG / SYSTEM), nav buttons via `addNavButton` (id, label,
  icon, `Runnable` → an `app::show*` method reference), a flexible `Region` filler, and
  the footer: gold **+ New Bill** CTA and the user pill.
- **The Catalog popup**: the seven directory views (Buyers, Sellers, Items, Categories,
  Templates, Transports, Variables, Label History) collapse behind ONE "Catalog"
  button to keep the sidebar short. `showCatalogPopup` builds a themed `Popup`
  (`autoHide(true)`) anchored under the button — the same real-button pattern the
  model picker uses (Ch. 19), which is why these buttons fire reliably.
- `updateNavActive(id)` is the visual state machine: remove `.active` everywhere, add
  it to the match, swap icon colour grey `#94A3B8` → gold `#F2CA6B`. Its mapping rules
  matter: `designer` lights **Templates**; `dashboard2` lights **Dashboard**; any of
  the eight catalog ids lights **Catalog**.
- `navIconFor(id)` maps every id to an `IconHelper` constant (with a `default` case so
  an unknown id can't NPE).

### 5.8 `AppShortcuts.java` + `ShortcutManager.java`

`AppShortcuts` registers all 25 actions in display groups (Workspace, Insights,
Directory, Design & Print, Data), each with id, group, label, default combo, action,
and a `worksWithoutData` flag (only F1 help works before login). Two actions carry
logic beyond navigation:

- `openLabelDesignerShortcut` — if the designer is on screen, call
  `enterBarcodeModeFromShortcut()`; else find the latest label-mode template by
  `updatedAt` comparison, creating + persisting `PresetTemplates.buildLabelTemplate()`
  if none exists, then `app.showTemplateDesigner(lbl)`.
- `openBulkPrintShortcut` — same locate-or-create, then (re)show the designer and
  trigger `openBulkPrintFromShortcut()`.

`ShortcutManager` is the engine:

```java
public record ShortcutAction(String id, String group, String label,
                             String defaultCombo, Runnable action, boolean worksWithoutData) {}
private static final Map<String, ShortcutAction> ACTIONS = new LinkedHashMap<>();
private static final Map<String, String> CUSTOM = new LinkedHashMap<>(); // overrides
private static final Set<String> RESERVED = Set.of("Alt+Tab", "Alt+F4",
        "Ctrl+Alt+Delete", "Meta", "Meta+Tab");
```

- **`validate(actionId, candidate)`** returns an enum (`OK, TAKEN, RESERVED,
  TOO_SIMPLE, INVALID`) — a combo must parse, not be reserved, carry a modifier (F-keys
  exempt — the comment explains: plain letters would fire *while typing*), and not be
  taken by another action. `validationMessage()` renders the enum for the UI.
- **`normalize(raw)`** canonicalises input: modifiers deduped and ordered Ctrl → Shift
  → Alt → Meta, key title-cased (`"ctrl+shift+l"` → `"Ctrl+Shift+L"`); `null` if two
  non-modifiers appear.
- **`bind/resetToDefault/resetAll`** mutate `CUSTOM` and `save()` to
  `AppDirs.dataDir()/shortcuts.json` (Jackson, indented); `load()` tolerates a corrupt
  file (warn + defaults).
- **`installAll(scene)`** pushes every effective combo into
  `scene.getAccelerators()` as `KeyCombination.valueOf(combo)`; bad combos are logged,
  not thrown. Called at boot and after every rebinding in `ShortcutsPanel`.

`ShortcutCatalog.groups()` holds the *contextual*, non-rebindable cheat-sheet text
(designer tool keys, bulk-print keys) — deliberately separate so rebindable and
contextual help never conflate. `ShortcutsPanel` (Settings tab) and `ShortcutsDialog`
(F1 overlay) both render from `ShortcutManager.grouped()` + `ShortcutCatalog`, so the
two surfaces cannot drift.

### 5.9 Window behaviour: `WindowStateManager` + `WindowResizeHelper`

`WindowStateManager.applyAndTrack(stage, 1440, 900, 1024, 640)`:

- **First run**: default size + maximised (the doc: "professional out-of-box
  experience").
- **Later runs**: `restore()` reads `Preferences.userNodeForPackage(...)` keys
  (`win.x/y/w/h/maximised`); non-maximised geometry is accepted only if
  `isVisibleOnAnyScreen` — at least ~100 px horizontally and 40 px vertically must
  overlap a connected screen's visual bounds, defeating the classic "unplugged the
  monitor, app opens invisible" bug. Note the `centerOnScreen()` then re-apply dance:
  centering adjusts position, so x/y are set again afterwards.
- **Save paths**: `stage.setOnHidden(...)` plus a **debounced** live saver — five
  property listeners funnel into a `Runnable` that coalesces via a `scheduled` flag and
  one `Platform.runLater`, so a drag that fires hundreds of size changes writes the
  registry once per frame-batch. An OS crash therefore loses at most the last moments.

`WindowResizeHelper.addResizeListener(stage, scene)` installs a `ResizeListener` for
MOUSE_MOVED/PRESSED/DRAGGED/EXITED. It: skips when maximised (and resets the cursor);
on *moved*, picks one of 8 resize cursors when within a 6 px border; on *pressed*,
records the drag origin (screen + stage geometry); on *dragged*, applies per-direction
arithmetic honouring `minWidth/minHeight` (default 600×400) — west/north drags move the
stage origin so the opposite edge stays pinned; on *exited* (button up), reset cursor.

### 5.10 `UiTheme.java` — the widget factory and the golden rule

The class doc states the rule that fixed an app-wide bug:

> NEVER call `node.setStyle(...)`. Inline styles have higher precedence than stylesheet
> `:hover` rules, which is why hover states died in the original app. Everything here
> attaches CSS classes from `globalfile.css`; dynamic variation is expressed with extra
> style classes, never inline styles.

The factory methods, by family:

- **Layout**: `page()` (24 px padded `view-page` VBox), `card(spacing)`, `row(spacing)`,
  `spacer()`.
- **Typography**: `pageTitle` (`heading-l`), `pageSubtitle` (`view-subtitle`, wrap),
  `sectionTitle` (`card-title`), `microLabel`, and `headerBlock(iconGlyph, title,
  subtitle)` — the stacked header every view reuses.
- **KPI**: `kpiCard(title, valueLabel, subText, accentClass)` — the *accent class goes
  on the sub line* (deltas read green/red) while the big value stays neutral;
  `kpiValue`, `subLabel`.
- **Pills**: `pill` (`badge-neutral`), `codePill` (gold `{{placeholder}}` chips),
  `statusPill(text, semantic)` for success/warning/danger/neutral.
- **Buttons**: `primaryBtn`, `goldBtn`, `secondaryBtn`, `smallBtn`, `dangerBtn`,
  `iconBtn` — class-only, so hover works everywhere.
- **Date pickers**: `configureDatePicker` installs a `StringConverter` displaying
  `dd/MM/yyyy` and *parsing five fallback formats* (`dd/MM/yyyy`, `dd-MM-yyyy`,
  `yyyy-MM-dd`, `d/M/yyyy`, `d-M-yyyy`) plus the ISO default — pre-built formatters
  because `ofPattern` recompiles per call. Users can type `5-6-2026` and it lands.
- **Extras**: `statRow` (label–spacer–value), `labeled` (form label above input),
  `emptyState(glyph, message, hint)`, and `toast(...)` passthrough.

### 5.11 `IconHelper.java`, `Toast.java`, `DialogHelper.java`

**IconHelper** — no image files for UI icons. 60+ `ICON_*` name constants;
`getIcon(name, size, colorHex)` builds a JavaFX `SVGPath` from `getSvgPath(name)`
(hand-authored Material-style paths — `chat` is a bubble with three dots, `mcp` a
stacked-server mark) and falls back to a unicode glyph from `getFallbackGlyph(name)`
with an inline `setStyle` (the one sanctioned exception — fallback glyphs are labels,
not interactive chrome). Variants: `getMenuIcon` (18×18 box), `createIconLabel`,
`createTabGraphic` (fills from a `selectedProperty` listener — grey ↔ gold),
`getToolbarIcon` (class `.toolbar-icon`, so CSS drives hover recolouring).

**Toast** — `show(rootPane, title, message, isError)` builds a `toast-box info|error`
VBox (max 360×80), bottom-right with 24 px margins, then a `SequentialTransition`:
fade-in 200 ms → pause 3.5 s → fade-out 300 ms with `setOnFinished(remove)`. The node
overloads resolve the target pane from the passed node's scene. Styling is 100 % CSS.

**DialogHelper** — `getAppIcon()` lazily loads `/icons/invoice-mark.png` (wordmark
fallback) into an `AtomicReference` cache; `applyAppIcon(stage)` adds it only if the
stage has none; `styleScene(scene)` attaches the stylesheet. `styleDialog(dialog, minW,
minH)` is the interesting one: resizable panes, expandable content (with a
`contentProperty` listener so swapped content stays expandable), the
`custom-dialog-pane` class, and an **anti-flicker** documented in-source — clamp the
*pane's* min size before `show()`, because JavaFX sizes the dialog stage from the
pane's constrained preferred size; raising the stage minimum in a later
`Platform.runLater` made dialogs visibly "jump".

### 5.12 `ViewEpochTracker.java` (+ `ViewEpochTrackerTest.java`)

The tracker is 50 lines of pure bookkeeping (full source quoted in Chapter 8's context;
its javadoc records the exact user-visible bug — "it wasn't forgetfulness; the check
was wrong"): `needsRefresh(viewId, epoch)` is true only when the *global* epoch moved
since *this view's* last `markRefreshed`; untracked views are treated as fresh because
`cached()` marks at build time; `clear()` serves logout. The test suite covers: mark →
no refresh at same epoch; refresh after bump; A's refresh never marks B fresh (the
regression); build-time marking; clear. Five tests, no JavaFX.

### 5.13 `UserProfilePill.java`

The sidebar footer chip: avatar circle with initials (or icon), display name and email,
and a click popup with account actions (profile info, logout via `app.promptLogout()`).
`refresh()` re-reads `AuthSessionManager` — called at boot and after every successful
login so the pill never shows a stale user. (Full listing was read; its menu follows
the same real-button popup pattern as the Catalog popup.)

### 5.14 `globalfile.css` — the visual language

3,774 lines of JavaFX CSS define the "Obsidian & Gold" theme: a root palette (obsidian
`#0B0E13`, panel `#12161F`, gold `#D9A13B`/`#F2CA6B`, text `#F2EBDD`, muted `#94A3B8`),
component classes used throughout this chapter (`.app-sidebar`, `.sidebar-nav-btn`,
`.sidebar-brand`, `.gold-btn`, `.kpi-card`, `.toast-box`, `.refresh-pill`,
`.custom-dialog-pane`, `.catalog-popup`…), and — critically — **every** `:hover` rule
in the app lives here, which is only possible because Java code never sets competing
inline styles (5.10's golden rule). The chapter samples it rather than listing all
3,774 lines; it is covered exhaustively as the reference for every view's class names
in Chapters 11–19.

## 6. How it works at runtime

```
java -jar → Launcher.main → Application.launch → StudioApp.start(stage)
  ├─ initServices(): DatabaseManager → DataManager.init → MCP auto-start
  ├─ build frame (sidebar, content pane, chatbot FAB)
  ├─ Scene + globalfile.css + shortcuts + icon + window state restore
  └─ showLoading()
       └─ dbExecutor: read session → (refresh token if expired)
            ├─ session? FX: setActiveSession → seed → showDashboardInternal
            │     → recurring sweep → expense backfill → warmCachesAsync
            └─ none? FX: showAuthScreen(SIGN_IN)        → Chapter 10

Every nav click → showXxx() → cached(id, build, refresh)
  ├─ first time: build node, store, mark epoch → setView (+150 ms fade)
  └─ repeat:  epoch moved? → return cached view NOW
                              └─ background: warm → FX: view.refresh() + pill
Keyboard     → scene accelerators ← ShortcutManager (rebindable, persisted)
Resize/drag  → WindowResizeHelper (undecorated edges) / OS (decorated)
Close        → WindowStateManager.save → stop(): MCP down, executors down
```

## 7. How to change it

- **Add a view to navigation**: create `XxxView` with a no-arg-refresh method; add a
  `showXxx()` following the 5.5 template (id, factory, refresher); add a sidebar
  button in `SidebarController.buildSidebar` + an icon mapping in `navIconFor`; add a
  case in `AppShortcuts.registerAll` and `StudioApp.reloadAllData`; add a CSS class if
  it needs new styling. Miss the reload case and F5 silently ignores it; miss the
  `navIconFor` case and the button falls back to the receipt glyph.
- **Recolour anything**: add/modify a class in `globalfile.css` — never `setStyle` on
  interactive nodes (the golden rule). Verify hover still works; hover death is the
  symptom of an inline style.
- **Move the window min-size floor**: update `start()` (`setMinWidth/Height`) *and*
  the `applyAndTrack(...)` min parameters *and* the Scene's initial size together.
- **Verify navigation caching**: open a view, edit data elsewhere, navigate away and
  back — the pill must flash and the new data appear without a rebuild flicker;
  `ViewEpochTrackerTest` proves the tracker logic.

## 8. Performance & UX analysis

- **View caching + fade (what was done).** *Cost:* memory for up to 17 live view graphs
  (tens of MB worst case with big tables). *Why it wins:* navigation is O(1) — no
  construction, no layout storm; the fade masks the data swap. *Alternative:* rebuild
  per click — simpler, but every click pays 50–300 ms of layout on big tables and
  scroll positions reset. *Trade-off:* Easy (cache) vs Medium (rebuild+state restore).
- **Single-thread DB executor.** *Cost:* queued jobs wait behind each other. *Why:*
  SQLite is single-writer; serialising prevents `SQLITE_BUSY` storms and makes
  ordering predictable. The FX thread never touches SQLite directly.
- **Debounced window persistence.** *Cost:* one Runnable + flag. *Why:* a resize burst
  would otherwise write the registry hundreds of times; debounce = ≤1 write per pulse.
- **OPTIONAL IMPROVEMENT (Easy):** LRU-evict the two heaviest uncached-on-demand views
  (e.g. keep at most 12 cached views, evict least-recently-used) to cap memory on
  long sessions. *User notice:* none in normal use; only super-long sessions with all
  views visited.
- **OPTIONAL IMPROVEMENT (Medium):** pre-build the *first* view during warm-up
  (`warmCachesAsync`'s `onDone`) so even the first dashboard paint skips construction.
  Already partially achieved by `showDashboardInternal()`.

## 9. Common mistakes and fixes

| Symptom | Cause | Fix |
|---|---|---|
| Hover effects dead on a widget | `setStyle()` inline overriding CSS | Move the style into `globalfile.css` classes |
| `IllegalStateException: Not on FX application thread` | View touched nodes on `dbExecutor` | Wrap in `Platform.runLater` |
| Dialog "jumps" when it opens | Min size raised after `show()` | Use `DialogHelper.styleDialog` (pre-show pane min) |
| View shows stale rows after an edit elsewhere | Write bypassed `DataManager` (no epoch bump) | Route writes through the hub (Ch. 8) |
| Window opens off-screen after unplugging a monitor | Geometry restored without bounds check | `WindowStateManager.isVisibleOnAnyScreen` already guards; don't bypass |
| Shortcut fires while typing in a text field | Bound a bare letter | `ShortcutManager.validate` rejects `TOO_SIMPLE` — rebind with a modifier |
| Sidebar highlights wrong button | New view id missing from `updateNavActive` mapping | Add the id (or its catalog-group membership) |
| App icon reverts to the Java cup on one dialog | Dialog sets/removes its own icons | Let the global `Window.getWindows()` listener apply the shared icon |

## 10. Checkpoint

```bash
mvn test -Dtest=ViewEpochTrackerTest,DatePickerThemeTest
mvn compile
```

Then run the app and verify by hand: resize/restore geometry persists across restart;
F1 toggles the overlay; Ctrl+N opens a fresh bill form; edit a buyer → navigate away
and back → the pill flashes and the row updates; the Catalog popup highlights its
active child; logout clears every cached view. Exercises:

1. Add a `showLabelDesigner()` convenience that opens the latest label template
   (mirror `openLabelDesignerShortcut` without the shortcut) and wire a sidebar entry.
2. Change the refresh pill to show the view's human name ("⟳ Refreshing Invoices…") —
   pass the label through `cached()`.
3. Write a test proving `ShortcutManager.normalize("shift+ctrl+L")` yields
   `"Ctrl+Shift+L"` and that `validate` rejects `"P"` with `TOO_SIMPLE`.

## 11. Summary and coverage self-check

The shell is a coordinator: a layered StackPane frame, a BorderPane layout, cached
navigation with per-view epochs and a queued background refresher, one DB executor,
persisted window state, a CSS-driven theme with a strict no-inline-style rule, an
SVG icon system, user-rebindable shortcuts, and overlay chrome (toast, F1, logout,
chatbot). Every feature chapter after this plugs a view into `show*()` and inherits
all of it.

**Covered in full this chapter (15/15):** `StudioApp` (all 807 lines: boot, stop,
chatbot wiring, help overlay, services, `setView`/`cached`/`refreshViewAsync`, pill,
loading, all 24 `show*`/bill-clone methods, `reloadAllData`, recurring sweep,
accessors, logout/auth, `main`), `SidebarController`, `AppShortcuts`,
`ShortcutManager` (record, registry, validation enum + messages, bind/reset,
normalize, installAll, load/save), `ShortcutCatalog`, `WindowStateManager` (apply/
restore/off-screen guard/save + debounce), `WindowResizeHelper` (8-cursor listener),
`UiTheme` (every factory family + date converter), `IconHelper` (constants, SVG
factory, variants, glyph fallback), `Toast` (transition timeline + pane resolution),
`DialogHelper` (icon cache, styleDialog anti-flicker, styleScene),
`ViewEpochTracker` (+ test), `UserProfilePill`, `ShortcutsPanel`/`ShortcutsDialog`
(as renderers of the two catalogs), `css/globalfile.css` (introduced and sampled;
the class-name contract for all later chapters).

**Markers raised this chapter:**
- `NOTE:` `globalfile.css` is sampled, not printed line-by-line (3,774 lines of pure
  styling with no logic); its naming contract is what later chapters rely on.
- `ISSUE:` `StudioApp.toggleChatbot` replaces the chatbot panel only by removing it —
  two rapid toggles can leave `chatbotPanel` non-null but detached (harmless, rebuilt
  on next open; noted for Ch. 19).
- `ISSUE:` `WindowResizeHelper` overlaps with decorated-window resizing; it matters
  only for undecorated stages (kept for the custom-chrome option).

**Next: Chapter 10 — Signing In: Authentication.**
