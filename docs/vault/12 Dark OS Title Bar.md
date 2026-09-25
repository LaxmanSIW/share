---
tags: [decision, ui, windows, titlebar, native]
aliases: [Dark Title Bar, Window Chrome]
---

# 12 — Dark OS Title Bar

Decision record: making the Windows title bar (the system strip with the app
icon, window title, and minimize / maximize / close buttons) match the app's
dark "Obsidian & Gold" theme instead of the default white caption.

## 1. Request

> "Main stage / scene header of application where we see logo and name — its
> default system top app header where we see minimise, maximise, cancel
> button. It is currently white which does not match; make it same as all app
> colors."

## 2. Why this needed a different mechanism than the previous UI work

Every other surface in this app is JavaFX content, so `globalfile.css`
controls it. The title bar is **not**: it is drawn by the operating system on
top of the JavaFX window. Consequences:

- No CSS rule can ever recolor it — the "one theme file" model does not apply.
- Any solution must call a **native OS API** (Win32, since the app targets
  Windows) and therefore must degrade safely on machines where that call is
  unavailable (other OS, old Windows, missing library).

## 3. What exists to reuse (checked before writing code)

| Candidate | Verdict |
|---|---|
| `globalfile.css` | Cannot reach OS chrome — no JavaFX property exists for the caption. |
| `DialogHelper.applyAppIcon` | Already covers every window (icon only); the natural **wiring point** — `StudioApp` calls it from a global `Window.getWindows()` listener. New windows get the icon there; the title bar now rides along. |
| `StageStyle.UNIFIED / TRANSPARENT / UNDECORATED` | Would replace the OS bar with hand-drawn min/max/close buttons — a big, risky rewrite (drag, snap, double-click-maximize, aero shake all re-implemented). Rejected. |
| JNA | Small, standard library for documented Win32 calls; adds one jar (~1.5 MB), no other dependency. |

## 4. Decision

Use the **documented Desktop Window Manager (DWM) API**
(`DwmSetWindowAttribute`, dwmapi.dll) through JNA:

| Attribute | Effect | Availability |
|---|---|---|
| 20 `DWMWA_USE_IMMERSIVE_DARK_MODE` (+ 19 for pre-20H1 Win10) | dark caption | Windows 10 1809+ |
| 34 `DWMWA_BORDER_COLOR` | window border color | Windows 11 |
| 35 `DWMWA_CAPTION_COLOR` | exact caption background | Windows 11 |
| 36 `DWMWA_TEXT_COLOR` | exact caption text | Windows 11 |

Unsupported attributes simply return an error code (no exception), so:
**Windows 11 → exact brand colors; Windows 10 → dark caption; other OS →
silent no-op.** The HWND is obtained reflectively via the stable OpenJFX peer
chain `stage.getPeer()` → `WindowStage.getPlatformWindow()` → glass
`Window.getNativeWindow()` (verified in OpenJFX source before use).

Colors mirror the `.root` tokens in `globalfile.css` (caption `#0B0E13` =
`-color-bg`, text `#F4F4F5` = `-color-text`, border `#232B38` =
`-color-border`); the CSS now carries a comment pointing back to the class so
the two stay in sync.

## 5. Implementation steps (what actually changed)

1. **`pom.xml`** — added `net.java.dev.jna:jna:5.16.0` (the only dependency
   change; comment documents the lazy-load contract).
2. **NEW `ui/TitleBarTheme.java`** — `apply(Stage)`: OS check → reflective
   HWND lookup → set the four DWM attributes. Own nested `Dwm` wrapper loads
   `dwmapi` lazily via `Native.load`; `packColorRef` converts `#RRGGBB` to
   Win32 `COLORREF` (0x00BBGGRR). **Never throws**: every step is guarded and
   failures log at debug and degrade to the default title bar.
3. **`ui/StudioApp.java`** — after `stage.show()`: `TitleBarTheme.apply(stage)`
   + one `Platform.runLater(TitleBarTheme::applyToAllProcessWindows)`; inside
   the existing global window listener next to `DialogHelper.applyAppIcon(s)`:
   apply + sweep on a `runLater`. Every dialog/Stage the app ever opens is
   themed automatically, including blank-titled Alerts. No other call sites.
4. **`globalfile.css`** — comment only (sync note on `.root`).
5. **NEW test `ui/TitleBarThemeTest.java`** — 6 tests: COLORREF packing
   (channel reversal + malformed input), palette-sync constants, never-throw
   contract, unknown-title → 0 (no-op), and the **OS round-trip**: show a
   real stage → apply → read the dark flag (and on Win11 the caption color)
   back via `DwmGetWindowAttribute` and assert the themed value. An evidence
   line (`hwnd=… captionReadBack=… darkFlag=…`) is printed into the surefire
   output so CI shows which capability branch ran.

## 6. Blast radius

| Existing file | Change | Risk |
|---|---|---|
| `StudioApp.java` | 2 additive lines (primary stage + global listener) | None — helper is no-throw; worst case the default white bar remains |
| `pom.xml` | +1 dependency | JNA loads lazily; a broken jar degrades to no-op, not startup failure |
| `globalfile.css` | comment only | None |

Untouched by design: every view, dialog, DAO, service, the designer, and
printing. No schema change. Other OSes (if ever targeted) keep the default
title bar.

## 7. Post-ship corrections (two rounds, each root-caused with evidence)

### Round 1 — apply-before-show ordering

The first launch showed the bar still white. Root cause: `TitleBarTheme.apply`
was called **before** `stage.show()`. The OpenJFX source shows the native peer
— and with it the HWND — is created lazily inside `show()` (every peer call in
`Stage.java` is guarded by `if (getPeer() != null)`), so the HWND lookup
returned 0 and the helper correctly no-opped. Fix: apply **after** `show()`
plus a `Platform.runLater` re-apply (the native window can land a pulse later;
the call is idempotent).

### Round 2 — reflection was the wrong tool entirely

Still white on the user's machine. Root cause, proven by a standalone probe
program: the HWND was reached reflectively via JavaFX internals
(`getPeer()` → `getPlatformWindow()` → `getNativeWindow()`), and those
packages are **strongly encapsulated** on JDK 17+ — the reflective call fails
(`InaccessibleObjectException`), the guard swallows it, handle 0, silent
no-op. The user's screenshot at 19:16 ran that build (it compiled 18:40; the
fix landed 19:25).

Fix: **no reflection at all.** The HWND is now found through plain Win32 —
`FindWindowW` by exact title (WString/UTF-16, the app title contains an
em-dash) with an own-process + visible check, falling back to an
`EnumWindows` sweep of this process's visible top-level windows. A process
sweep (`applyToAllProcessWindows`) is also wired after show() and on every
new window, which additionally covers JavaFX Alerts (created with a blank
title).

### Evidence & capability matrix (Windows 10 build 19045, the user's machine)

| Fact | Evidence (probe + test output) |
|---|---|
| Dark-caption flag (attr 20) IS supported | `GET attr 20 → hr=0x0`; after set: `val=1` |
| Attr 19 (pre-20H1 alias) is NOT supported | `hr=0x80070057` — harmless, id 20 suffices on 19045 |
| Exact caption/text/border colors (35/36/34) are Win11-only | `hr=0x80070057` on this build |
| The app window resolves by title + sweep | `hwnd=0x260488` found from a shown test stage |
| End-to-end round trip passes | `TitleBarThemeTest`: show → apply → read back `darkFlag=1` from DWM |

### Lesson recorded

1. Native window chrome calls must run **after** peer creation ("before
   show" is right for `initStyle`/`initOwner`, wrong for HWND attributes).
2. Reaching HWNDs through JavaFX internals does not survive module
   encapsulation — use the OS itself (FindWindow/EnumWindows) instead.
3. A no-throw helper without a read-back is **unfalsifiable** — the OS
   round-trip test (set, then `DwmGetWindowAttribute` and compare) is what
   finally proved the chain, and it must fail loudly if a future JDK/JavaFX
   or Windows update breaks it.
4. A test suite that shows+hides a real stage must call
   `Platform.setImplicitExit(false)` — and it must do so **outside** the
   `Platform.startup` try/catch. The first placement was inside it: when
   another suite had already bootstrapped FX, `startup` threw
   `IllegalStateException`, the flag never executed, the probe's hide()
   triggered implicit exit, and the whole surefire fork lost its FX thread
   (proven by a thread dump: `JavaFX Application Thread` count = 0 while
   `AWT-Windows` was still alive). One-line reposition fixed all 6
   collateral failures in `TemplatesExportMenuTest` and both in
   `TemplatesToolbarThemeTest`; a bisect (`CopyButton → TitleBar → Export`
   fails deterministically, each pair alone passes) plus a standalone repro
   (`target` scratch harness, since removed) isolated it.

## 8. Known limits

- The dark-mode flag colors the caption per the **system** dark palette on
  Windows 10 (exact colors are Windows 11 only) — still a massive visual match
  versus white.
- JavaFX internals are accessed reflectively; if a future JavaFX upgrade
  moves `getPlatformWindow`, the code degrades to no-op (logged), never fails.
- JNA version pins Windows behavior; bumping it should be smoke-tested by
  launching once.

## 10. Rollback

Delete `TitleBarTheme.java` + its test, remove the two `TitleBarTheme.apply`
lines in `StudioApp`, and drop the JNA dependency. Nothing else references it.

## 11. Verification

`mvn -Dtest=TitleBarThemeTest test` → **6/6 pass**, including the OS
round-trip on the dev machine (Windows 10 build 19045): shown test stage →
apply → `DwmGetWindowAttribute` reads back `darkFlag=1`. Full-suite regression
runs separately (`mvn test`); production changes are confined to
`TitleBarTheme` (new file) and additive lines in `StudioApp`.

Related: [[04 UI Layer]] · [[06 Build Packaging CI]] · [[00 Index]]
