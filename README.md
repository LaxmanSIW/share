# InvoiceStudio 3.0.0

**Standalone JavaFX Billing & Invoice Management Desktop Application**

InvoiceStudio  is a cross-platform desktop application for creating and managing invoices.
It ships with a full billing workflow — buyers, items, bills, GST handling, an invoice
**Template Designer**, PDF export with QR/UPI codes, sales reports and a dashboard —
wrapped in a custom dark **“Obsidian & Gold”** theme with a VS Code-style sidebar.

| | |
|---|---|
| **Language / Runtime** | Java 21 |
| **UI framework** | JavaFX 21.0.4 (OpenJFX) |
| **Database** | SQLite (embedded, via `sqlite-jdbc 3.45.1.0`) |
| **PDF generation** | Apache PDFBox 3.0.2 |
| **Barcodes / QR** | ZXing 3.5.3 (Code 128 + UPI QR) |
| **JSON** | Jackson 2.17.0 |
| **Build tool** | Apache Maven (compiler target 21) |
| **Testing** | JUnit 5.10.2 (27 unit tests) + a 47-step UI smoke harness |
| **Main class** | `com.invoicestudio.Launcher` |
| **Final artifact** | `target/invoice-studio-desktop-3.0.0.jar` (self-contained “fat” jar, ≈ 33 MB) |

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Get the source](#2-get-the-source)
3. [Project layout](#3-project-layout)
4. [Compile & build](#4-compile--build)
5. [Run the application](#5-run-the-application)
6. [Package the runnable JAR](#6-package-the-runnable-jar)
7. [Create a Windows installer (setup file)](#7-create-a-windows-installer-setup-file)
8. [Bundles for Linux & macOS](#8-bundles-for-linux--macos)
9. [Smoke / UI tests (optional, for developers)](#9-smoke--ui-tests-optional-for-developers)
10. [Troubleshooting](#10-troubleshooting)
11. [Release checklist (version bump)](#11-release-checklist-version-bump)

---

## 1. Prerequisites

You need exactly **two tools** to build and run InvoiceStudio from source.
Install them before doing anything else.

### 1.1 JDK 21 (a full JDK — not just a JRE)

- Download: <https://adoptium.net/temurin/releases/?version=21>
  (Eclipse Temurin is recommended; Oracle JDK 21 or any OpenJDK 21 build also works).
- The **JDK** is required because the build needs `javac` (compiler) and the
  packaging step needs `jpackage` (installer generator) — both ship inside the JDK.
- During installation on Windows you can let the installer set `JAVA_HOME`
  and add Java to `PATH` (both checkboxes), or set them manually afterwards.

Verify:

```bash
java -version
# openjdk version "21.x.x" ...
javac -version      # must also print 21
jpackage --version  # prints something like 21.0.x — needed only for installer builds
```

> ⚠️ JavaFX 21 requires **Java 21 or newer**. On Java 17 or older the build
> fails with `invalid source release: 21`.

### 1.2 Maven 3.8+ (3.9.x recommended)

- Download: <https://maven.apache.org/download.cgi>
  (get the **binary** zip/tar.gz, extract it, and add its `bin/` folder to `PATH`).
- IDE bundles also work (IntelliJ IDEA ships Maven; see §2.2).

Verify:

```bash
mvn -version
# Apache Maven 3.9.x — and the "Java version" line must show 21
```

### 1.3 Git (only to clone the repository)

```bash
git --version
```

> **Note for Linux users:** if JavaFX windows come up blank/crash on your distro,
> install GTK libraries: `sudo apt install libgtk-3-0 libgl1` (most desktop distros
> already have them).

---

## 2. Get the source

### 2.1 Clone with Git

```bash
git clone https://github.com/LaxmanSIW/share.git
cd share

# The redesigned version lives on the ZAI-GLM branch:
git checkout ZAI-GLM
```

### 2.2 Open in an IDE (optional but comfortable)

| IDE | How |
|---|---|
| **IntelliJ IDEA** (Community is enough) | `File → Open…` → select the project folder (the `pom.xml`). IDEA auto-imports Maven. Set `Project SDK` to 21 in `File → Project Structure`. Run the app via the Maven panel: `Plugins → javafx → javafx:run`, or run `com.invoicestudio.Launcher` directly. |
| **VS Code** | Install the *Extension Pack for Java* + open the folder. Tasks: `mvn compile`, run via `Launch Java` on `Launcher.java` or the Maven side bar. |
| **Eclipse** | `File → Import → Maven → Existing Maven Projects`. Ensure an installed JRE of Java 21. |

---

## 3. Project layout

```
share/
├── pom.xml                          # Maven build definition (all versions & plugins)
├── src/
│   ├── main/
│   │   ├── java/com/invoicestudio/
│   │   │   ├── Launcher.java        # main-class bootstrap (launches JavaFX properly)
│   │   │   ├── AppDirs.java         # OS-correct user-data folder resolution
│   │   │   ├── db/                  # SQLite DAOs, DatabaseManager, schema, seeding
│   │   │   ├── model/               # Bill, Buyer, Item, Template, … domain objects
│   │   │   ├── service/             # PDF export, reports, numbering…
│   │   │   └── ui/                  # JavaFX views (Dashboard, History, Template Designer…)
│   │   └── resources/
│   │       ├── css/globalfile.css   # the whole dark theme
│   │       ├── icons/               # app icons / logos
│   │       └── seed/*.json          # first-run demo data (bills, buyers, items, templates)
│   └── test/java/                   # 27 JUnit unit tests + UI smoke harness
├── packaging/
│   ├── InvoiceStudio.ico            # Windows application icon
│   ├── build-windows-installer.ps1  # one-command Windows MSI / app-image builder
│   └── InvoiceStudio.iss            # Inno Setup script → classic setup.exe
└── .github/workflows/
    └── windows-installer.yml        # CI: builds the MSI on GitHub's servers
```

**Why is there a `Launcher` class?** JavaFX refuses to start an `Application`
subclass that sits on the *classpath* (i.e. inside a normal jar). `Launcher` is a
plain bootstrap that calls `Application.launch(StudioApp.class, args)`, which makes
both `java -jar …` and `jpackage` work without a `module-info.java`.

---

## 4. Compile & build

Run all commands from the project root (the folder that contains `pom.xml`).

### 4.1 Compile only (fast check)

```bash
mvn clean compile
```

Compiles every `.java` file into `target/classes/`. Use this for a quick
“does everything still build?” check.

### 4.2 Run the unit tests

```bash
mvn test
```

Runs the 27 JUnit tests (database, services, data-directory logic).
No display or database is required — tests use isolated temp folders.

### 4.3 Full package (compile + test + fat JAR)

```bash
mvn clean package
```

This is the **main build command**. It:

1. compiles the sources,
2. runs the unit tests,
3. builds a **self-contained “fat” jar** with the Maven Shade plugin —
   JavaFX, SQLite, PDFBox, ZXing and Jackson are all packed inside —
   and sets `com.invoicestudio.Launcher` as the jar's main class.

Result:

```
target/invoice-studio-desktop-2.0.1.jar          ← runnable fat jar (≈ 33 MB)
target/original-invoice-studio-desktop-2.0.1.jar ← classes only, keep for reference
```

Skip the tests when you just want speed:

```bash
mvn clean package -DskipTests
```

> **Platform note:** the fat jar contains the **JavaFX native libraries of the OS
> it was built on** (that's how the OpenJFX Maven artifacts resolve). A jar built
> on Windows runs on Windows, a jar built on Linux runs on Linux. If you need a
> jar for another OS, run `mvn package` on that OS (or use the CI workflow, §7.3).
> The SQLite driver already bundles every platform's native library, so it is
> always fine.

---

## 5. Run the application

### 5.1 Option A — via Maven (during development)

```bash
mvn javafx:run
```

Uses the OpenJFX Maven plugin (`mainClass = com.invoicestudio.Launcher`).
This compiles and launches directly from sources — no jar needed.

### 5.2 Option B — run the packaged JAR (any machine with Java 21)

```bash
java -jar target/invoice-studio-desktop-2.0.1.jar
```

Nothing else is required — every dependency is inside the jar.
This is exactly how the installed/bundled app launches internally.

### 5.3 First launch & demo data

On the very first start, if the database is empty, the app **seeds demo content**
from `src/main/resources/seed/*.json` (buyers *Acme / Beta / Gamma*, sample bills
`INV-0009`–`INV-0013`, items, templates and variables), so you can explore every
screen immediately. Delete the data folder (below) and relaunch to start from a
clean slate.

### 5.4 Where your data lives (important)

The database is **never** stored next to the app or in `Program Files` —
`AppDirs` resolves a per-user, writable folder:

| OS | Data folder (`invoicestudio.db`) |
|---|---|
| **Windows** | `%APPDATA%\InvoiceStudio\` (usually `C:\Users\<you>\AppData\Roaming\InvoiceStudio`) |
| **macOS** | `~/Library/Application Support/InvoiceStudio/` |
| **Linux** | `$XDG_DATA_HOME/InvoiceStudio` or `~/.local/share/InvoiceStudio/` |

- **Backup** = copy that folder. **Reset** = delete it.
- Upgrading or reinstalling the app never touches this folder — your bills survive.
- Coming from an older version that kept `invoicestudio.db` in the working folder?
  The app **migrates** that legacy database (including `-wal`/`-shm` side files)
  into the proper data folder on first run.
- **Portable / custom location** (e.g. keep data on a USB stick or `D:\`):

  ```bash
  java -Dinvoicestudio.data.dir=D:\InvoiceData -jar invoice-studio-desktop-2.0.1.jar
  ```

---

## 6. Package the runnable JAR

Section 4.3 already produced the jar; this section explains **what it is** and
how to hand it to other people.

### 6.1 What's inside the fat jar

| Contained? | Content |
|---|---|
| ✅ | All application classes & resources (CSS theme, icons, seed data) |
| ✅ | JavaFX 21.0.4 **including native graphics libraries for the build OS** |
| ✅ | SQLite JDBC driver + native SQLite binaries for Windows/macOS/Linux |
| ✅ | PDFBox, ZXing, Jackson |
| ✅ | `MANIFEST.MF` with `Main-Class: com.invoicestudio.Launcher` |

Because of that manifest you launch it with a double-click option too
(on Windows: right-click → *Open with → Java™*, if a JRE 21 is installed and
associated), but the reliable way everywhere is:

```bash
java -jar invoice-studio-desktop-2.0.1.jar
```

### 6.2 Sharing the jar

Copy `target/invoice-studio-desktop-2.0.1.jar` to any computer with **Java 21
for the same OS** and run the command above. The recipient needs nothing else —
but they *do* need Java installed, and there is no Start-menu/desktop icon.
**That gap is exactly what the installer in §7 removes**: it bundles its own
Java runtime, so end users install *nothing*, and it creates proper shortcuts.

---

## 7. Create a Windows installer (setup file)

This is the “make it feel like real software” step: one `InvoiceStudio-2.0.1.msi`
(or a classic `setup.exe`) that the user double-clicks → *Next → Next → Finish*,
and InvoiceStudio appears in the Start menu **and on the desktop**, with its own
icon, an entry in *Apps & Features* for clean uninstall — and **no Java install
required on the target PC**, because a private Java 21 runtime (≈ 40 MB) is
embedded in the package.

The tool that does this is **`jpackage`**, which ships inside every JDK 21+.
It wraps the fat jar, a bundled JRE and shortcuts into a native installer.

### 7.1 What you get

| | MSI (default) | setup.exe (Inno Setup route) |
|---|---|---|
| File produced | `packaging/dist/InvoiceStudio-2.0.1.msi` | `packaging/dist/InvoiceStudio-2.0.1-setup.exe` |
| Bundled Java 21 runtime | ✅ | ✅ |
| Desktop icon shortcut | ✅ (`--win-shortcut`) | ✅ (installer task, ticked by default) |
| Start-menu entry | ✅ (`--win-menu`) | ✅ (+ program group + Uninstall entry) |
| Install-directory chooser | ✅ (`--win-dir-chooser`) | ✅ |
| Launch-after-install | — | ✅ |
| Add/Remove-Programs uninstaller | ✅ | ✅ |
| Needs WiX Toolset on the build PC | ✅ | ❌ (needs Inno Setup instead) |

> Both run **only on Windows**. `jpackage` cannot cross-compile: the JavaFX
> natives and the bundled JRE always match the OS the packaging runs on.
> No Windows PC handy? Use **Option A (GitHub Actions)** — GitHub builds it for you.

### 7.2 Option A — build the MSI with GitHub Actions (no Windows needed)

The repository already contains a ready CI workflow:
`.github/workflows/windows-installer.yml`.

1. Push your latest code to GitHub (`git push origin ZAI-GLM`).
2. On github.com open the repo → **Actions** tab.
3. Select **“Windows Installer”** in the left list → **Run workflow** ▼ →
   choose branch `ZAI-GLM` → **Run workflow**.
4. Wait ≈ 3–6 minutes. Click the finished run and download the artifact
   **`InvoiceStudio-2.0.1-windows-installer`** — inside is the MSI.
5. *(Optional, for releases)*: pushing a version **tag** builds automatically and
   attaches the MSI to a GitHub Release that anyone can download:

   ```bash
   git tag v2.0.1
   git push origin v2.0.1
   ```

The workflow uses Temurin 21 on `windows-latest`, installs WiX via Chocolatey if
missing, runs `mvn package -DskipTests`, then calls `jpackage --type msi` with
shortcut flags — i.e. exactly what §7.3 does, but on GitHub's hardware.

### 7.3 Option B — build locally on Windows (one PowerShell command)

**One-time prerequisites** (on the Windows build machine):

1. **JDK 21** — <https://adoptium.net/temurin/releases/?version=21>
2. **Maven** — <https://maven.apache.org/download.cgi>
3. **WiX Toolset 3.x** — <https://wixtoolset.org/releases/>
   (jpackage needs its `candle.exe`/`light.exe` to emit an MSI; after installing,
   reopen PowerShell so `C:\Program Files (x86)\WiX Toolset v3.14\bin` is on `PATH`)

Then, in PowerShell inside the project folder:

```powershell
# MSI installer with desktop icon, start menu and dir chooser:
.\packaging\build-windows-installer.ps1
```

The script:

1. checks `java`, `jpackage`, `mvn` (and `candle.exe`) are available,
2. runs `mvn package -DskipTests` (a Windows build automatically pulls the
   **Windows** JavaFX natives into the fat jar),
3. stages the jar into `packaging/input/`,
4. calls `jpackage --type msi --win-shortcut --win-menu --win-dir-chooser …`.

Result: **`packaging\dist\InvoiceStudio-2.0.1.msi`** — send it to anyone.
Double-click → UAC → *Next → Next → Install* → desktop icon appears. Done.

### 7.4 Option C — classic `setup.exe` with Inno Setup

Prefer the old-school wizard-style `setup.exe`? Combine jpackage's *app-image*
folder with the included Inno Setup script:

1. Build the plain application folder (no WiX needed):

   ```powershell
   .\packaging\build-windows-installer.ps1 -AppImage
   # → packaging\app-image\InvoiceStudio\  (self-contained app incl. bundled JRE)
   ```

2. Install **Inno Setup 6** — <https://jrsoftware.org/isdl.php> — then compile:

   ```powershell
   ISCC.exe packaging\InvoiceStudio.iss
   # → packaging\dist\InvoiceStudio-2.0.1-setup.exe
   ```

The script adds: directory chooser, **“Create a desktop icon”** task,
Start-menu group, uninstaller in Apps & Features, and a
*Launch InvoiceStudio* checkbox on the finish page.

### 7.5 The jpackage command, explained (manual / customizable)

Everything the scripts do boils down to this single command (PowerShell on Windows):

```powershell
jpackage `
  --type msi `
  --name InvoiceStudio `
  --description "Billing & Invoice Design Studio" `
  --app-version 2.0.1 `
  --vendor InvoiceStudio `
  --icon packaging/InvoiceStudio.ico `
  --input packaging/input `
  --main-jar invoice-studio-desktop-2.0.1.jar `
  --main-class com.invoicestudio.Launcher `
  --win-shortcut --win-menu --win-dir-chooser `
  --dest packaging/dist
```

| Flag | Meaning |
|---|---|
| `--type msi` | Installer format (`msi`, `exe`*, `app-image`, `dmg`, `pkg`, `deb`, `rpm`). *`exe` type still needs WiX and produces an MSI-like package — the Inno route (§7.4) gives a *real* exe. |
| `--input` | Folder whose contents get copied into the app image (only the fat jar is staged). |
| `--main-jar` / `--main-class` | Entry point inside that folder. |
| `--icon` | `packaging/InvoiceStudio.ico` — used for the EXE, shortcuts and uninstaller. |
| `--win-shortcut` | Create a **desktop** shortcut. |
| `--win-menu` | Create a **Start-menu** entry. |
| `--win-dir-chooser` | Let the user pick the install directory. |
| `--app-image` (type) | No installer — just a portable folder (input for Inno Setup). |
| `--dest` | Output directory. |
| `--java-options "-Dinvoicestudio.data.dir=…"` | Optional: pin a fixed data folder (portable installs). |

Useful extras: `--win-per-user-install` (no admin rights), `--win-upgrade-uuid …`
(stable upgrade identity across versions), `--java-options "-Xmx1024m"`.

### 7.6 Installing, upgrading, uninstalling & SmartScreen

- **SmartScreen warning** — the MSI/setup.exe is **unsigned** (a code-signing
  certificate costs money). On first run Windows shows
  *“Windows protected your PC”* → click **More info → Run anyway**.
  Sign it later with `--sign` / `signtool` and your certificate to remove this.
- **Install** — double-click, allow UAC, finish. Installs to
  `C:\Program Files\InvoiceStudio` (by default), writes shortcuts, registers the uninstaller.
- **Upgrade** — install the newer MSI over the old one (or uninstall first).
  User data in `%APPDATA%\InvoiceStudio` is **never touched** by installs/uninstalls.
- **Uninstall** — *Settings → Apps → Installed apps → InvoiceStudio → Uninstall*
  (or Start-menu group → Uninstall). The database folder stays, so reinstalling
  brings your data back.
- **Where end-user data lives** — `%APPDATA%\InvoiceStudio\invoicestudio.db`.
  Backup = copy that folder.

---

## 8. Bundles for Linux & macOS

`jpackage` works the same way everywhere — run it **on** the target OS.

**Linux** (deb-based; no extra tools needed for deb/rpm):

```bash
mvn -B -ntp package -DskipTests
mkdir -p packaging/input && cp target/invoice-studio-desktop-2.0.1.jar packaging/input/
jpackage --type deb \
  --name invoicestudio --app-version 2.0.1 --vendor InvoiceStudio \
  --description "Billing & Invoice Design Studio" \
  --icon packaging/InvoiceStudio.ico \
  --input packaging/input \
  --main-jar invoice-studio-desktop-2.0.1.jar \
  --main-class com.invoicestudio.Launcher \
  --linux-shortcut --dest packaging/dist
sudo dpkg -i packaging/dist/invoicestudio_2.0.1_amd64.deb   # install
```

(Use `--type rpm` for Fedora/RHEL. `--linux-menu-group "Office"` adds a menu category.)

**macOS** (produces a signed-or-unsigned `.dmg`; needs macOS):

```bash
mvn -B -ntp package -DskipTests
mkdir -p packaging/input && cp target/invoice-studio-desktop-2.0.1.jar packaging/input/
jpackage --type dmg \
  --name InvoiceStudio --app-version 2.0.1 --vendor InvoiceStudio \
  --icon packaging/InvoiceStudio.icns \
  --input packaging/input \
  --main-jar invoice-studio-desktop-2.0.1.jar \
  --main-class com.invoicestudio.Launcher \
  --dest packaging/dist
```

(macOS wants an `.icns` icon; convert the `.png`/`.ico` with `iconutil` or an
online converter. `--type pkg` gives an installer package instead.)

---

## 9. Smoke / UI tests (optional, for developers)

Besides the 27 JUnit unit tests (`mvn test`), the repo contains a full UI smoke
harness that really launches the app, drives **all 47 navigation steps** (every
view, dialogs, edit/duplicate/convert flows, dashboard month navigation…) and
screenshots each step.

- Harness: `src/test/java/NavSmokeRunner.java` + `SmokeLauncher.java`
- Runner script: `scripts/nav_smoke_test.sh` (requires **Linux + Xvfb**):

```bash
./scripts/nav_smoke_test.sh     # builds the jar, runs 47 steps under a virtual display
```

It runs the **packaged jar** (not `target/classes`), so after editing sources
always `mvn package` first, then rerun the script.

---

## 10. Troubleshooting

| Symptom | Cause & fix |
|---|---|
| `invalid source release: 21` during `mvn compile` | Maven is running on an older JDK. Check `mvn -version` → “Java version”. Install JDK 21 and set `JAVA_HOME`. |
| `java -jar …` says `UnsupportedClassVersionError` | The machine's `java` is older than 21. Install Temurin 21 or use the installer (§7), which bundles Java. |
| `Error: JavaFX runtime components are missing` when running a class directly | You ran `StudioApp` instead of the `Launcher` main class. Use `java -jar …`, `mvn javafx:run`, or run `com.invoicestudio.Launcher`. |
| Jar starts on Windows but crashes on Linux with `UnsatisfiedLinkError` / blank window | The jar was built on a different OS — JavaFX natives don't match. Rebuild with `mvn package` on the target OS (or via CI, §7.2). |
| `jpackage: WiX Toolset not found` / `candle.exe not on PATH` | MSI needs WiX 3.x. Install it and reopen PowerShell, or build with `-AppImage` + Inno Setup (§7.4). |
| SmartScreen blocks the installer | Normal for unsigned builds: *More info → Run anyway* (§7.6). |
| App can't create the database / starts with errors in Program Files | You're running an old build from the working directory. Current builds store data in `%APPDATA%\InvoiceStudio` — reinstall from a fresh MSI. |
| Bills “disappeared” after reinstall | The data folder is intact by design — check `%APPDATA%\InvoiceStudio`. Delete it only to factory-reset. |
| `mvn javafx:run` shows no window over SSH/headless | JavaFX needs a display. Use a desktop session, or Xvfb for tests (§9). |

---

## 11. Release checklist (version bump)

When the version changes (e.g. `4.0.0` → `4.0.1`), update it in **one** place:

1. `pom.xml` → `<version>` — all other scripts read from here automatically:
   - `packaging/build-windows-installer.ps1` parses `pom.xml` for `$AppVersion` and jar name
   - `.github/workflows/windows-installer.yml` parses `pom.xml` for version and jar name
   - `packaging/InvoiceStudio.iss` accepts `/DAppVersion=...` from the build script (falls back to a hardcoded default only if invoked manually without `/D`)
2. Tag the release: `git tag v4.0.1 && git push origin v4.0.1` → CI builds the MSI
   and attaches it to a GitHub Release automatically (§7.2, step 5).

---

*Built with Java 21 + JavaFX 21 · Obsidian & Gold theme · SQLite · PDFBox · ZXing*
