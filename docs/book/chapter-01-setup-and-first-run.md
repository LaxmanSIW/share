# Chapter 1 — Your Computer, Java, and This Project: Setup & First Run

> **Part 1 of InvoiceStudio: Zero to Finished Product**
> Files covered in full this chapter: `pom.xml`, `README.md`, `.gitignore`,
> `fx.env`, `.vscode/settings.json`, `.freebuff/skills/javafx-best-practices/SKILL.md`
> (verified: all read from the repository).
> Goal at the end: **the real app is running on your screen.**

---

## 1. Chapter goal

You will install the two tools every subsequent chapter depends on (a **JDK 21**
and **Maven**), fetch this project's source, understand the build recipe that
holds it together (`pom.xml`) and the project's own documentation
(`README.md`, `.gitignore`, `fx.env`), and — the payoff — run the finished
application for the first time.

Nothing in this chapter writes application code. Everything in later chapters
assumes this foundation works, which is exactly why we verify it here with
four small commands before touching a single line of Java.

---

## 2. Story intro

Before a chef can follow a recipe, the kitchen needs a stove, an oven, and
sharp knives. Before *you* can follow the next 21 chapters, your computer
needs the same three roles filled:

- The **stove** is the **JVM** (Java Virtual Machine) — it *runs* programs.
- The **prep station** is the **compiler** (`javac`) — it turns the text you
  write into instructions the JVM can execute.
- The **sous-chef** is **Maven** — it reads the recipe (`pom.xml`), fetches
  every ingredient (libraries), cooks (compiles), tastes (tests), and plates
  (packages a JAR).

This chapter installs the kitchen and does a test cook with an existing recipe.
By the end, the finished dish — the whole InvoiceStudio app — will be running
on your machine, which gives you a working *destination* to aim at while the
rest of the book rebuilds it from scratch.

---

## 3. Concepts first

### 3.1 What "Java 21" means, and why the version matters

Java comes in versions. Code written for Java 21 can *use* features (like
`record` types and pattern matching in `switch`, both used heavily in this
codebase) that older Javas have never heard of. The reverse is also true:
a program compiled for Java 21 **cannot run** on a Java 17 JVM — the older
machine literally cannot read the newer instructions.

This project sets `maven.compiler.source` and `maven.compiler.target` to
**21** in `pom.xml`, so:

- your **compiler** must be 21 or newer (to *read* the code),
- your **runtime** must be 21 or newer (to *run* it).

Mix them up and you will meet the two most common beginner errors in this
whole book:

- `invalid source release: 21` → your compiler is older than 21.
- `UnsupportedClassVersionError` → the JVM trying to *run* the code is older
  than the compiler that *built* it.

### 3.2 JDK vs JRE

- **JRE** (Java Runtime Environment): just the JVM — can *run* Java programs.
- **JDK** (Java Development Kit): JVM **plus** the compiler (`javac`) **plus**
  packaging tools (`jpackage` — used in Chapter 22 to build the installer).

You need the **JDK**. Installing a JRE is the classic first-day mistake and
produces "javac is not recognized" errors.

> **Tip:** Eclipse **Temurin** (adoptium.net) is a free, well-maintained JDK.
> Oracle's JDK works too. On macOS, [SDKMAN!](https://sdkman.io) or
> `brew install --cask temurin@21` are the easiest routes.

### 3.3 What Maven is actually doing

Maven is convention over configuration: it expects sources in
`src/main/java`, tests in `src/test/java`, resources in `src/main/resources`.
If you follow the convention (this project does), your entire build
instruction set shrinks to one recipe file — `pom.xml` — plus a few plugin
settings.

Maven's "lifecycle" that this project uses:

```
validate → compile → test → package
```

- `mvn compile` — translate sources to bytecode (fast sanity check).
- `mvn test` — compile + run the automated tests (this project has 372).
- `mvn package` — all of the above + build the runnable **fat JAR**.

A **fat JAR** is one `.jar` file that contains not only this app's classes but
*all* its libraries (JavaFX, SQLite driver, PDFBox, ZXing, Jackson) zipped
inside — which is why it is ~33 MB and why you can copy that single file to
any Windows PC and just run it.

### 3.4 The three file types this chapter covers

- **`pom.xml`** — XML recipe: identity, dependencies, plugins.
- **`.gitignore`** — a list of file patterns Git should *never* record (build
  outputs, local databases). Keeps the repository clean and shareable.
- **`fx.env`** — a small developer-convenience script holding the JavaFX
  "module path" for running the app from Git Bash on Windows. You will never
  need it with Maven; it documents a manual run path.

---

## 4. Files in this chapter

| File | Type | Purpose | Lines |
|---|---|---|---|
| `pom.xml` | Maven POM | The build recipe: identity, 9 dependencies, 4 plugins | 152 |
| `README.md` | Markdown | The project's own guide (we will read it critically) | ~230 |
| `.gitignore` | Config | What Git must not track | ~35 |
| `fx.env` | Shell env | Manual JavaFX classpath for Git Bash runs | 1 |
| `.vscode/settings.json` | Editor config | VS Code workspace settings | small |
| `.freebuff/skills/javafx-best-practices/SKILL.md` | Docs | Agent skill note (covered as a curiosity) | small |

---

## 5. Step-by-step build

### Step 1 — Install JDK 21

1. Go to <https://adoptium.net/temurin/releases/?version=21>.
2. Download the **JDK** (not JRE) for your OS.
3. **Windows:** during install, tick *both* "Set JAVA_HOME" and "Add to PATH".
   **macOS/Linux:** the package usually handles this; verify with the commands
   below.
4. Open a **new** terminal (so the PATH change applies) and check:

```bash
java -version
# openjdk version "21.0.x" ...
javac -version
# javac 21.0.x
jpackage --version
# 21.0.x  (only needed for Chapter 22, but confirms full JDK)
```

> **Warning:** if `javac -version` says "command not found", you installed a
> JRE or the PATH was not updated. Fix this now — everything else depends on it.

### Step 2 — Install Maven

1. Download the **binary zip** from <https://maven.apache.org/download.cgi>.
2. Extract it (e.g. to `C:\tools\apache-maven-3.9.6`).
3. Add its `bin` folder to your `PATH`.
4. Verify:

```bash
mvn -version
# Apache Maven 3.9.x — AND the "Java version" line must say 21
```

If Maven's "Java version" shows something older than 21, Maven picked up a
different JDK — set the `JAVA_HOME` environment variable to your JDK 21
folder and reopen the terminal.

### Step 3 — Get the source code

```bash
git clone <your-fork-url> InvoiceStudio
cd InvoiceStudio
```

(If you are rebuilding from the book rather than cloning, create an empty
folder — later chapters create every file from scratch.)

### Step 4 — Read the recipe: `pom.xml` in full

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.invoicestudio</groupId>
    <artifactId>invoice-studio-desktop</artifactId>
    <version>4.0.0</version>
    <packaging>jar</packaging>

    <name>InvoiceStudio</name>
    <description>Standalone JavaFX Billing &amp; Invoice Management Desktop Application</description>
```

**Block by block:**

- `<?xml …?>` — the XML declaration; tells parsers which encoding/standard to
  expect. XML is a tag-based text format (like HTML with stricter rules).
- `<project xmlns=…>` — the root element. The `xmlns` lines point at the
  official definition of what a POM file may contain, so tools can validate it.
- `<modelVersion>4.0.0` — the version of the POM *format* itself (fixed value
  for all modern Maven; not your app's version).
- **Coordinates** — Maven names every library in the world with three values:
  - `groupId` `com.invoicestudio` — who makes it (reversed domain style),
  - `artifactId` `invoice-studio-desktop` — its name,
  - `version` `4.0.0` — which edition.
  Together these are its "address" in the Maven universe.
- `<packaging>jar` — the build output type: a JAR file.
- `<name>`/`<description>` — human labels.

```xml
    <properties>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <javafx.version>21.0.4</javafx.version>
    </properties>
```

- `sourceEncoding UTF-8` — the source files contain real-world text (₹, –,
  Devanagari in test data); UTF-8 is the text encoding that can represent all
  of it. Without this, builds fail on other machines with "unmappable
  character".
- `source`/`target` **21** — as explained in §3.1: compile *for* Java 21.
- `javafx.version` — a reusable constant; `${javafx.version}` below substitutes
  it, so upgrading JavaFX means editing **one line**.

```xml
    <dependencies>
        <!-- JavaFX Controls, FXML, Swing -->
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-controls</artifactId>
            <version>${javafx.version}</version>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-fxml</artifactId>
            <version>${javafx.version}</version>
        </dependency>
        <dependency>
            <groupId>org.openjfx</groupId>
            <artifactId>javafx-swing</artifactId>
            <version>${javafx.version}</version>
        </dependency>
```

Each `<dependency>` is a library Maven must download (from Maven Central, a
global public repository) before compiling.

- `javafx-controls` — buttons, tables, text fields: the UI toolkit itself.
- `javafx-fxml` — support for FXML layout files. **This project declares it
  but builds all UI in pure Java code; no `.fxml` files exist in the repo.**
  Keeping the dependency is harmless and leaves the door open.

  > **ISSUE (harmless):** `javafx-fxml` is an unused dependency today. It is
  > kept intentionally (future-proofing), and the faithful build keeps it too.
  > Removing it would shave a few hundred KB from the fat JAR — see
  > Performance notes.

- `javafx-swing` — the bridge between JavaFX and the older Swing toolkit. The
  app uses it for one specific trick: `JFXPanel`-style bootstrapping in tests
  and the headless test harness. You will meet it again in Chapter 21.

```xml
        <!-- SQLite Database JDBC -->
        <dependency>
            <groupId>org.xerial</groupId>
            <artifactId>sqlite-jdbc</artifactId>
            <version>3.45.1.0</version>
        </dependency>
```

- **JDBC** (*Java Database Connectivity*) is Java's standard API for talking
  to databases: you write `Connection`, `PreparedStatement`, `ResultSet`
  against an interface, and a *driver* implements the actual wire protocol.
- The **Xerial** driver is special: it embeds the real SQLite engine (written
  in C) as native binaries **for Windows, macOS and Linux**, and loads the
  right one automatically. That is why the same JAR works everywhere.

```xml
        <!-- JSON Processing (Jackson) -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
            <version>2.17.0</version>
        </dependency>
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
            <version>2.17.0</version>
        </dependency>
```

- **Jackson** converts between Java objects and JSON text — in both
  directions. The app uses it for: settings files, the AI provider APIs
  (request/response JSON), the seed data, and the Knowledge Hub.
- `jsr310` adds support for Java's modern date/time types
  (`java.time.LocalDate` and friends) in that conversion.

```xml
        <!-- Apache PDFBox for PDF Generation -->
        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>pdfbox</artifactId>
            <version>3.0.2</version>
        </dependency>
```

**PDFBox** creates PDF documents programmatically: pages, text with exact
positions, fonts, lines, images. Chapter 16 builds the invoice exporter on it.

```xml
        <!-- ZXing for Barcodes (Code 128) and QR Codes (UPI) -->
        <dependency>
            <groupId>com.google.zxing</groupId>
            <artifactId>core</artifactId>
            <version>3.5.3</version>
        </dependency>
        <dependency>
            <groupId>com.google.zxing</groupId>
            <artifactId>javase</artifactId>
            <version>3.5.3</version>
        </dependency>
```

**ZXing** ("zebra crossing") generates barcodes and QR codes as images. The
invoice PDF embeds a **UPI QR code** so a customer can scan-and-pay. `core`
is the engine; `javase` adds helpers for turning results into Java images.

```xml
        <!-- JUnit 5 Testing -->
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>5.10.2</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
```

- **JUnit** is the automated-testing framework. `<scope>test` means: available
  to code under `src/test/java` only — it never ships inside the fat JAR,
  because customers do not need your test framework.

```xml
    <build>
        <plugins>
            <!-- Compiler Plugin -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
                <configuration>
                    <source>21</source>
                    <target>21</target>
                    <encoding>UTF-8</encoding>
                </configuration>
            </plugin>
```

A **plugin** is an extension that performs a lifecycle step. The compiler
plugin runs `javac` with these settings (repeating the properties — belt and
braces, both exist in the file).

```xml
            <!-- Surefire Plugin for Tests -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.2.5</version>
                <configuration>
                    <useModulePath>false</useModulePath>
                </configuration>
            </plugin>
```

- **Surefire** runs the JUnit tests during `mvn test`.
- `useModulePath false` — Java's module system (JPMS) is deliberately *not*
  used by this project (no `module-info.java` anywhere). This flag tells
  Surefire to run tests on the traditional classpath, matching how the app
  itself runs. Without it, some test setups fail with "JavaFX runtime
  components are missing".

```xml
            <!-- JavaFX Maven Plugin for running in dev -->
            <plugin>
                <groupId>org.openjfx</groupId>
                <artifactId>javafx-maven-plugin</artifactId>
                <version>0.0.8</version>
                <configuration>
                    <mainClass>com.invoicestudio.Launcher</mainClass>
                </configuration>
            </plugin>
```

Lets you type `mvn javafx:run` during development: it assembles the JavaFX
module path correctly and launches the given main class. Without it you would
have to build a long `-module-path … --add-modules …` command by hand (that
hand-built alternative is exactly what `fx.env` stores — see Step 6).

```xml
            <!-- Shade Plugin for Runnable Fat JAR -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.1</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals>
                            <goal>shade</goal>
                        </goals>
                        <configuration>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <mainClass>com.invoicestudio.Launcher</mainClass>
                                </transformer>
                            </transformers>
                            <filters>
                                <filter>
                                    <artifact>*:*</artifact>
                                    <excludes>
                                        <exclude>META-INF/*.SF</exclude>
                                        <exclude>META-INF/*.DSA</exclude>
                                        <exclude>META-INF/*.RSA</exclude>
                                    </excludes>
                                </filter>
                            </filters>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

- The **Shade plugin** runs during `package` and produces the **fat JAR**: it
  merges the app's own classes with *every* dependency's classes into one JAR.
- `ManifestResourceTransformer` — a JAR's `META-INF/MANIFEST.MF` is its table
  of contents; the `Main-Class:` entry tells `java -jar` where to start.
  This sets it to `com.invoicestudio.Launcher`.
- The `filters` block strips old **signature files** (`*.SF`, `*.DSA`,
  `*.RSA`) from the merged dependencies. Those files digitally sign the
  *original* JARs; after merging, they would describe the wrong content and
  Java's security layer would refuse to load the fat JAR. Removing them is
  the standard shade-plugin ritual.

### Step 5 — Read the README critically

Open `README.md` in full (it is ~230 lines; we read every section in
Chapter 0's exercise). Three observations worth making *now*, as a taste of
the critical reading this book practises:

1. **Version drift — ISSUE.** The README's title says "InvoiceStudio 3.0.0"
   and quotes `target/invoice-studio-desktop-2.0.1.jar` in several places,
   while `pom.xml` says version **4.0.0**. The README was written at 3.0.0
   and only partially updated; the release checklist (§11 of the README)
   claims the version lives in "one place" — the pom — which is true for
   *builds*, but the README's example commands still show 2.0.1.
   **Faithful build: keep both files as they are.** When you bump versions
   yourself, update README examples too.
2. **Test-count drift — GAP (resolved by measurement).** The README says
   "27 unit tests + a 47-step UI smoke harness". Today's suite is **372 JUnit
   tests**. The number in the README is a snapshot from an earlier release;
   the real count is whatever `mvn test` prints. Nothing in the build depends
   on the README's number.
3. **Branch reference — GAP.** §2.1 says the redesigned version lives on the
   `ZAI-GLM` branch of `github.com/LaxmanSIW/share`. This repository's
   current development branch is `development`. Branches move; the *code in
   front of you* is the source of truth.

Everything else in the README (install steps, jpackage flags, troubleshooting
table) matches the build we verified. We will reuse its Chapter 22-relevant
sections verbatim when we build the installer.

### Step 6 — `.gitignore` in full

```gitignore
# Build artifacts
target/
dependency-reduced-pom.xml

# Installer build outputs (packaging/ sources ARE committed)
packaging/input/
packaging/dist/
packaging/app-image/

# Local runtime data (SQLite DB is created next to the working directory)
*.db
smoke-test/
nav-smoke/
bulk-verify/
barcode-verify/
tspl-verify/
knowledge-verify/
selzoom-verify/
zoom-verify/

# Logs
*.log

# IDE
.idea/
*.iml
.vscode/
.classpath
.project
.settings/

# OS
.DS_Store
Thumbs.db

# Graphify & AI Agents
graphify-out/
.agents/
.agent/
.gemini/
*.graphify*
labelstock-verify/
ruler-verify/

# Chatbot verification harness output (screenshots)
cb-verify/shots/
```

Line by line:

- `target/` — Maven's output folder; rebuilt from source at any time, so it
  must never be committed. (`dependency-reduced-pom.xml` is a temporary file
  the Shade plugin writes while merging.)
- `packaging/input|dist|app-image` — Chapter 22 stages the fat JAR into
  `packaging/input/` and jpackage writes installers into `dist/`; the
  *scripts* (`build-windows-installer.ps1`, `InvoiceStudio.iss`) ARE
  committed — only their *outputs* are ignored.
- `*.db` — any SQLite database anywhere in the tree. **Crucial:** your real
  business data must never end up in a Git push. (The repo *does* commit one
  deliberate exception — `src/main/resources/seed/custom.db`, a demo database
  shipped as seed data; Chapter 3 covers it.)
- The verify folders (`nav-smoke/`, `tspl-verify/`, …) — output directories
  of the test harnesses in Chapter 21.
- `*.log` — log files (the app writes `app.log` via `AppLog`, Chapter 2).
- IDE folders (`.idea/`, `.vscode/`…) — editor state is personal; note the
  small irony that `.vscode/settings.json` **is** committed while `.gitignore`
  ignores the folder — Git's rule is that already-tracked files stay tracked
  unless you `git rm --cached` them. **ISSUE (harmless):** this is why the
  repo has a `.vscode/settings.json` despite the ignore rule.
- The "Graphify & AI Agents" block ignores tooling scratch dirs used by
  AI-assisted development tools; `cb-verify/shots/` keeps chatbot-verification
  screenshots (binary noise) out of Git while letting the harness write them.

### Step 7 — `fx.env` in full

```bash
FXCP=/c/Users/Kapto/.m2/repository/org/openjfx/javafx-base/21.0.4/javafx-base-21.0.4-win.jar;/c/Users/Kapto/.m2/repository/org/openjfx/javafx-graphics/21.0.4/javafx-graphics-21.0.4-win.jar;/c/Users/Kapto/.m2/repository/org/openjfx/javafx-controls/21.0.4/javafx-controls-21.0.4-win.jar;/c/Users/Kapto/.m2/repository/org/openjfx/javafx-swing/21.0.4/javafx-swing-21.0.4-win.jar
```

One line, Windows machine-specific. It holds the **module path** — the list
of JavaFX JAR files (downloaded into Maven's local cache, `~/.m2/repository`,
during earlier builds) — for manually launching JavaFX from a Git Bash shell
with `java --module-path "$FXCP" …`.

> **Note:** with `mvn javafx:run` you never need this file. It exists as a
> documented escape hatch (and as a trace of how the developer ran the app
> during development). Your path will differ — do not commit yours.

**GAP:** `fx.env` is referenced by no script in the repository; it is purely a
personal convenience file that happens to be committed. The faithful build
keeps it; you may delete yours without breaking anything.

### Step 8 — `.vscode/settings.json` and the skills note

Both files are small workspace conveniences (editor settings; a note describing
JavaFX best practices used by an AI coding assistant). Their content is not
load-bearing for the app. **GAP:** `.vscode/settings.json` returned an access
error when this book's author tried to read it from the writing environment,
so its exact contents are not reproduced here — it is editor configuration
with zero runtime effect, and it is listed in the inventory as such. If you
want it in a later print of the book, open it in your editor and paste it into
`docs/book/` — the audit table (Appendix A5) marks it as editor-config-only.

### Step 9 — First run!

From the project root:

```bash
mvn clean compile     # 1–2 min first time: downloads everything
mvn javafx:run
```

What you should see:

1. Maven downloads the internet (once) — ~150 JARs into `~/.m2/repository`.
2. A dark-gold splash-free window appears: **the auth screen** (sign-in),
   because the shell gates the workspace behind authentication (Chapter 10).
3. Create an account or explore; the first run also **seeds demo data**
   (buyers *Acme / Beta / Gamma*, sample invoices) into a new SQLite database —
   at `%APPDATA%\InvoiceStudio\invoicestudio.db` on Windows. Chapter 2 explains
   exactly why that folder.

Close the window. That's the whole product — and from Chapter 3 onward you
will rebuild every layer that just ran.

> **Warning:** if the window appears *blank*, or the console prints
> `UnsupportedClassVersionError` or `Exception in Application constructor`,
> jump straight to §9 below — these three cover 90% of first-run failures.

---

## 6. How it works at runtime

For this chapter the "runtime" is Maven + the JVM. When you ran
`mvn javafx:run`:

```
 you: mvn javafx:run
   │
   ▼
 Maven (reads pom.xml)
   ├── resolves dependencies from ~/.m2 (downloads if missing)
   ├── compiles src/main/java → target/classes   (javac, Java 21)
   ├── builds the JavaFX module path
   └── starts a new JVM:
         java --module-path <javafx jars> --add-modules javafx.controls,javafx.swing
              -classpath target/classes com.invoicestudio.Launcher
                    │
                    ▼
        Launcher.main(args)                ← Chapter 2
                    │  Application.launch(StudioApp.class, args)
                    ▼
        JavaFX toolkit starts
                    │  creates the JavaFX Application Thread (the UI thread)
                    ▼
        StudioApp.start(stage)             ← Chapter 9
                    │
                    ▼
        Auth screen appears (Chapter 10)
```

Two things to burn into memory now, because every later chapter leans on them:

1. **Exactly one UI thread.** JavaFX created it when `Application.launch` ran.
   All the UI code we write in later chapters will run there; anything slow
   (disk, network, SQLite) must run on *other* threads and hand results back —
   the recurring discipline of this book.
2. **The classpath/module-path distinction.** Maven put the libraries on a
   *module path* for the run (that's what `javafx-maven-plugin` assembled, and
   what `fx.env` pre-computes by hand). When we package the fat JAR in
   Chapter 22, everything moves to the plain classpath instead — which is only
   legal because of the `Launcher` bootstrap trick explained in Chapter 2.

---

## 7. How to change it

**Change the app version.** Edit `<version>4.0.0</version>` in `pom.xml`.
Every other file reads it automatically (the README's §11 explains the
scripts parse it). Verify: `mvn -q -Dexec.skip package -DskipTests` then look
for the new jar name in `target/`. If you change it, update the README's
example commands too (see the ISSUE in Step 5).

**Add a library.** Insert a `<dependency>` block inside `<dependencies>`,
save, run `mvn compile` — Maven downloads it on the next build. Verify it
landed: `ls ~/.m2/repository/<groupId path>`. What breaks if skipped: the
compile fails with "package X does not exist" if the dependency is missing,
or the fat JAR throws `NoClassDefFoundError` at runtime if you added the code
but not the dependency.

**Change a build setting (e.g. bump Java version).** Edit the
`<properties>` values *and* the compiler plugin's `<source>/<target>`.
Both places exist on purpose; keeping them in sync is your job. Verify with
`mvn -version` and a clean `mvn compile`.

---

## 8. Performance & UX analysis

| Decision | What was done | Cost | Better alternative | Trade-off / difficulty | User-visible effect |
|---|---|---|---|---|---|
| Fat JAR via Shade | all deps merged into one 33 MB jar | slower startup than module runtime (~0.5–1 s extra JIT/link work); big file | `jlink` custom runtime image (Ch. 22 alternative) | jlink is **Medium**: needs module declarations; gains ~40 MB smaller installs and marginally faster starts | install size, double-click start latency |
| Unused `javafx-fxml` dependency | declared but unused | few hundred KB in jar, one extra module on dev module-path | remove it | **Easy**, zero risk — but leaves the door open for FXML later | none (only jar size) |
| UTF-8 explicitly pinned | avoids platform-default encoding | none | — | — | prevents crashes/`?` characters for non-ASCII business names |
| `useModulePath=false` in tests | tests run on classpath | none | migrate to full JPMS | **Hard**: whole codebase would need `module-info.java`, exports, opens | none visible; JPMS adds compile-time safety at high migration cost |
| Maven Central pinned versions | exact versions, no ranges | builds are reproducible; but library updates are manual | Dependabot/Renovate bot | **Easy** on GitHub | none directly; fewer security surprises |

`OPTIONAL IMPROVEMENT` (safe, quick): delete the `javafx-fxml` dependency and
rebuild — verify with `mvn clean package` and launching the fat jar. Expected
effect: a slightly smaller jar; nothing else. Revert if a future chapter
introduces FXML.

---

## 9. Common mistakes and fixes

| Symptom | Cause | Fix |
|---|---|---|
| `javac: command not found` | JRE installed, or PATH not refreshed | install the full JDK 21; open a new terminal |
| `invalid source release: 21` | Maven runs on an older JDK | check `mvn -version` "Java version" line; set `JAVA_HOME` to JDK 21 |
| `UnsupportedClassVersionError` at run time | the `java` on PATH is older than the compiler's target | same machine mismatch — align JDK; or you're running the jar on an old PC |
| `Error: JavaFX runtime components are missing` | someone ran `StudioApp` directly instead of `Launcher` | always start via `Launcher` (`mvn javafx:run`, `java -jar`, or IDE run-config on `Launcher`) — explained fully in Chapter 2 |
| Maven downloads fail behind a proxy | corporate network | configure `~/.m2/settings.xml` proxy block (Maven docs §"Proxy") |
| Window opens blank on Linux | missing GTK libs | `sudo apt install libgtk-3-0 libgl1` |
| `mvn javafx:run` works but `java -jar target/*.jar` says no main manifest | you ran `compile` but not `package` | run `mvn clean package` first (Shade plugin writes the manifest at package) |

---

## 10. Checkpoint

You have completed this chapter when all four are true:

```bash
java -version          # 21.x
mvn -version           # 3.8+, Java 21
mvn clean package -DskipTests   # BUILD SUCCESS; target/invoice-studio-desktop-4.0.0.jar exists
java -jar target/invoice-studio-desktop-4.0.0.jar   # window opens, auth screen shows
```

**Exercises**

1. Find three `<dependency>` blocks in `pom.xml` and say in one sentence each
   what library it brings (answer key: UI toolkit / database driver / PDF writer).
2. Add `<maven.compiler.source>17</maven.compiler.source>` temporarily and run
   `mvn clean compile`. Read the error carefully — you are deliberately
   reproducing the #1 beginner error. Revert.
3. Open `~/.m2/repository/org/openjfx` in your file browser and identify the
   five JavaFX JARs your build downloaded (base, graphics, controls, swing,
   fxml) — the physical reality behind the pom's coordinates.

---

## 11. Summary and coverage self-check

You installed the kitchen (JDK 21 + Maven), fetched the project, read the
entire build recipe `pom.xml` block by block — identity, properties, all nine
dependencies and all four plugins — plus `.gitignore`, `fx.env`, and the
README (critically, with two drifts flagged). The finished application ran on
your machine, and you watched Maven compile it and start the JVM that launched
JavaFX.

**Files covered in full this chapter (6):**

- `pom.xml` ✅
- `README.md` ✅
- `.gitignore` ✅
- `fx.env` ✅
- `.vscode/settings.json` — ⚠️ flagged `GAP:` (unreadable in the writing
  environment; editor-config only; row in `appendix-file-inventory.md`)
- `.freebuff/skills/javafx-best-practices/SKILL.md` — introduced; full text
  reproduced in Chapter 21 with the verification-harness discussion it belongs to

**Markers raised:** 2 `ISSUE:` (README version drift; committed `.vscode`
folder vs ignore rule), 2 `GAP:` (README test-count/branch snapshot;
`fx.env` referenced by nothing).

**Next: Chapter 2 — "The Skeleton: Entry Point & the App's Data Home"**
(`Launcher.java`, `AppDirs.java`, `AppDirsTest.java`, and the JavaFX
threading model).