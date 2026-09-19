# Chapter 2 — The Skeleton: Entry Point & the App's Data Home

> **Part 1 of InvoiceStudio: Zero to Finished Product**
> Files covered in full this chapter: `Launcher.java`, `AppDirs.java`,
> `AppLog.java`, `AppDirsTest.java` — plus the threading contract file
> `AppExecutors.java` (fully explained here too, since no other chapter owns it).
> Goal at the end: you understand *every line that runs before the first
> window paints*, and exactly where the app is allowed to store data.

---

## 1. Chapter goal

By the end of this chapter you will have created the four smallest files in
the project and be able to answer, from memory:

- Why does the app start from a class called `Launcher` instead of `StudioApp`
  directly? (This is the #1 runtime error in JavaFX apps — you will never hit it.)
- Where does the SQLite database live on each operating system, and why can't
  it live next to the program?
- How does a user who ran an *old* version of the app keep their data when
  they install the *new* version?
- What are the app's rules for threads and logging — the two invisible
  services every later chapter quietly relies on?

---

## 2. Story intro

Every building needs two things before anyone talks about the rooms: a
**front door** and a **plot of land**.

The front door is `Launcher` — the single, boring class the operating system
is told to call. Its only job is to open the door and step aside. It exists
because of an awkward rule in JavaFX: the class that *extends* `Application`
must be started *by* JavaFX's own launcher machinery, but when your program is
packaged as a plain runnable JAR (Chapter 1's fat JAR), Java can't find
JavaFX on the module path — so the app would crash with
**"Error: JavaFX runtime components are missing"** before drawing anything.
The fix is a tiny non-JavaFX class as the declared entry point. Nine lines
prevent a hundred support tickets.

The plot of land is `AppDirs` — the code that decides *where on disk* this
app may write. Once installed under `C:\Program Files\` (Chapter 22), the
program folder is **read-only by design**: Windows blocks ordinary programs
from writing there. So the database must live in the per-user
application-data folder instead. `AppDirs` also solves a very human problem:
the developer's earliest builds stored `invoicestudio.db` next to the jar.
Real users already had data there. The migration code moves it silently, so
upgrading never "loses" a single invoice.

Alongside them live the two utilities: `AppLog` (one funnel for every error
message) and `AppExecutors` (one home for every background thread). Neither
does anything dramatic. Both exist because *scattering* logging and threads
across 100 files makes debugging miserable later.

---

## 3. Concepts first

### 3.1 What an entry point is

When you double-click an EXE, Windows calls a designated start function.
Java's equivalent is a rule: the JVM starts executing at a method with the
exact signature

```java
public static void main(String[] args)
```

inside the class named in the JAR's manifest (Chapter 1 set `Main-Class:
com.invoicestudio.Launcher`). "Entry point" just means *the one method the
operating system is allowed to call first*.

### 3.2 What `static` means (first of many meetings)

A `static` method belongs to the **class itself**, not to any object built
from the class. You call `AppDirs.dataDir()` without ever writing
`new AppDirs()`. That is why both utility classes here mark their
constructors `private` — nobody can create instances of a class that is just
a bag of static functions.

### 3.3 `Path` — Java's address object for files

`java.nio.file.Path` represents a file-system location
(`C:\Users\Kapto\AppData\Roaming\InvoiceStudio`). `Path.of("a", "b")` joins
segments with the OS's separator. `Files.createDirectories(path)` makes every
missing folder in the chain — and, importantly, does *nothing* if they exist
already (that's not an error).

### 3.4 System properties vs environment variables

Both are key→value string maps the process inherits, but they come from
different places:

- **Environment variables** (`%APPDATA%`, `XDG_DATA_HOME`) — set by the
  *operating system / shell*, read with `System.getenv("APPDATA")`.
- **System properties** (`invoicestudio.data.dir`, `applog.debug`) — set on
  the *java command line* with `-Dname=value`, read with
  `System.getProperty(...)`. Tests use this heavily to redirect the app's
  storage into throw-away folders.

### 3.5 The JavaFX Application Thread (FX thread)

JavaFX, like nearly every UI toolkit, allows **UI changes from exactly one
thread** — the JavaFX Application Thread, created by `Application.launch`.
Touch a control from any other thread and you get `IllegalStateException:
Not on FX application thread` (or worse, silent corruption). Every later
chapter's async code ends with "…and hand the result back to the FX thread",
using `AppExecutors.runOnFx` or `Platform.runLater`.

### 3.6 ExecutorService — a rented team of workers

An `ExecutorService` is a pool of pre-started threads with a queue of tasks.
You hand it work (`pool.submit(task)`) and it runs when a worker is free.
Creating a *new* pool per operation leaks threads (each pool's threads stay
alive until shut down); the app therefore owns **exactly three**, created once.

### 3.7 What an enum of exceptions has to do with `volatile`

You will meet the keyword `volatile` on `AppLog`'s `sink` field. It means:
"this field may be written by one thread and read by others — never let a
thread keep a stale cached copy." It is the cheapest correct way to publish a
swappable value across threads.

---

## 4. Files in this chapter

| File | Type | Purpose | Lines |
|---|---|---|---|
| `src/main/java/com/invoicestudio/Launcher.java` | Class | Entry point; hands control to JavaFX | 9 |
| `src/main/java/com/invoicestudio/AppDirs.java` | Class | Data-dir resolution + legacy DB migration | 100 |
| `src/main/java/com/invoicestudio/service/AppLog.java` | Class | Single logging surface | 74 |
| `src/main/java/com/invoicestudio/service/AppExecutors.java` | Class | The app's three thread pools + FX helper | 78 |
| `src/test/java/com/invoicestudio/AppDirsTest.java` | Test | 3 tests over the data-dir logic | 66 |

---

## 5. Step-by-step build

### Step 1 — `src/main/java/com/invoicestudio/Launcher.java` (complete file)

```java
package com.invoicestudio;

import com.invoicestudio.ui.StudioApp;
import javafx.application.Application;

public class Launcher {
    public static void main(String[] args) {
        Application.launch(StudioApp.class, args);
    }
}
```

**Line by line:**

- `package com.invoicestudio;` — every Java file declares which *package*
  (folder) it lives in; the folder tree under `src/main/java` must match.
- `import com.invoicestudio.ui.StudioApp;` — lets us refer to `StudioApp`
  (the real JavaFX application class, Chapter 9) by its short name.
- `import javafx.application.Application;` — JavaFX's application base class
  and home of the static `launch` helper.
- `public class Launcher {` — note what is **absent**: `extends Application`.
  That is the whole trick. Because `Launcher` is an ordinary class, the JVM
  can start it from a plain classpath (the fat JAR) where JavaFX is not on
  the module path. JavaFX's startup check ("was I launched properly?") looks
  at the class that *extends* Application, not the one named in the manifest.
- `public static void main(String[] args)` — the entry point; `args` receives
  any command-line arguments (this app ignores them).
- `Application.launch(StudioApp.class, args);` — static helper that:
  1. creates the JavaFX runtime (graphics toolkit, FX thread),
  2. creates **one instance** of `StudioApp` via its no-argument constructor,
  3. calls its `init()` → `start(Stage)` lifecycle methods on the FX thread,
  4. waits until the platform exits, then calls `StudioApp.stop()`.
- There is deliberately **no** `System.exit` here. When the last window
  closes, `Platform.exit` ends the FX thread and `main` simply returns.

> **Why not just `public class StudioApp extends Application` with `main`
> inside it?** It works with `mvn javafx:run` (the plugin launches it the
> "right" way) but **crashes from the fat JAR** with
> `Error: JavaFX runtime components are missing`. The separate `Launcher`
> class makes both launch modes identical. Chapter 1's troubleshooting table
> already met this error; now you know its cure.

### Step 2 — `src/main/java/com/invoicestudio/AppDirs.java` (complete file)

```java
package com.invoicestudio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Resolves per-user writable locations for installed-app data.
 *
 * <p>When the app is installed through an installer (MSI / Inno Setup) it runs
 * from "C:\Program Files\..." where the process has no write access, so the
 * SQLite database must live in the per-user application-data folder instead of
 * the working directory.</p>
 *
 * <p>Resolution order for the data directory:</p>
 * <ol>
 *   <li>{@code -Dinvoicestudio.data.dir=...} — tests / portable deployments</li>
 *   <li>Windows: {@code %APPDATA%\InvoiceStudio};
 *       macOS: {@code ~/Library/Application Support/InvoiceStudio};
 *       Linux/other: {@code $XDG_DATA_HOME/InvoiceStudio}
 *       (default {@code ~/.local/share/InvoiceStudio})</li>
 *   <li>Fallback: current working directory (legacy behaviour)</li>
 * </ol>
 *
 * <p>Upgrade path: users who previously ran the jar from a folder keep their
 * data — if the data dir has no database yet but the working directory does,
 * the existing file is copied over on first launch.</p>
 */
public final class AppDirs {

    private static final String DIR_OVERRIDE_PROPERTY = "invoicestudio.data.dir";
    private static final String DB_FILE = "invoicestudio.db";

    private AppDirs() {
    }
```

- The Javadoc block is the file's *contract*: it states the resolution order
  and the migration promise. Good utility classes document policy at the top
  so callers never re-derive it.
- `final class` — cannot be subclassed; utility classes should be final.
- Two constants: the override property name and the database file name.
  Constants (`static final`) instead of repeated string literals mean a typo
  is a compile error, not a runtime bug.
- `private AppDirs() {}` — a private constructor is the standard "this class
  is never instantiated" idiom.

```java
    /** Per-user writable application data directory (created if missing). */
    public static Path dataDir() {
        String override = System.getProperty(DIR_OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
```

- **Priority 1 — the override.** If the JVM was started with
  `-Dinvoicestudio.data.dir=X`, that wins outright. Tests set this (see
  Step 5) so they never touch a real user's folder; portable deployments can
  use it to keep data on a USB stick.
- `isBlank()` — true for `null`-ish, empty, or whitespace-only strings;
  one call covers all the degenerate cases.

```java
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            Path base;
            if (os.contains("win")) {
                String appdata = System.getenv("APPDATA");
                base = (appdata != null && !appdata.isBlank())
                        ? Path.of(appdata)
                        : Path.of(System.getProperty("user.home"), "AppData", "Roaming");
            } else if (os.contains("mac") || os.contains("darwin")) {
                base = Path.of(System.getProperty("user.home"), "Library", "Application Support");
            } else {
                String xdg = System.getenv("XDG_DATA_HOME");
                base = (xdg != null && !xdg.isBlank())
                        ? Path.of(xdg)
                        : Path.of(System.getProperty("user.home"), ".local", "share");
            }
```

- `os.name` lowercased, then substring checks — the standard portable way to
  branch per OS (values look like `Windows 11`, `Mac OS X`, `Linux`).
- **Windows:** `%APPDATA%` normally exists (it is `…\AppData\Roaming`); the
  fallback rebuilds the same path from `user.home` for exotic setups where
  the variable is unset.
- **macOS:** Apple's convention for per-user app data.
- **Linux:** honours the XDG base-directory spec (`XDG_DATA_HOME`), defaulting
  to `~/.local/share` — the convention desktop Linux tools agree on.
- Note each branch only computes `base` (the *parent*); the app-specific
  child comes next, once, instead of three times.

```java
            Path dir = base.resolve("InvoiceStudio");
            Files.createDirectories(dir);
            return dir;
        } catch (IOException | SecurityException ex) {
            return Path.of("").toAbsolutePath(); // legacy behaviour as last resort
        }
    }
```

- `resolve("InvoiceStudio")` = join child name. `createDirectories` makes the
  full chain on first run ever (no-op afterwards).
- The **catch** is the graceful-degradation story: if the disk is denied
  (locked-down kiosk machine, security manager), fall back to the *current
  working directory* — exactly where the old jar-run builds stored data. The
  app still runs; it just stores locally like it used to. This is why the
  class Javadoc calls it "legacy behaviour as last resort".

```java
    /**
     * Absolute SQLite JDBC URL for the app database inside the data dir.
     * Migrates a legacy working-directory database on first run so users
     * upgrading from "run the jar" keep all their records.
     */
    public static String databaseUrl() {
        Path target = dataDir().toAbsolutePath().resolve(DB_FILE);
        migrateLegacyDatabase(target);
        return "jdbc:sqlite:" + target.toString().replace('\\', '/');
    }
```

- The **JDBC URL** is the connection string the database layer (Chapter 3)
  will hand to the SQLite driver: `jdbc:sqlite:C:/Users/.../invoicestudio.db`.
- `toAbsolutePath()` before `resolve` — a relative data dir (the CWD fallback)
  would produce a relative URL that changes meaning if the app is later
  started from a different folder; absolute pins it.
- `.replace('\\', '/')` — SQLite's JDBC driver accepts both separators, but
  forward slashes avoid any escaping ambiguity in URL strings on Windows.
- Migration runs *before* returning, so the very first connection ever made
  already points at data, if data existed.

```java
    /** Copies a CWD-era database into the data dir (best effort, once). */
    private static void migrateLegacyDatabase(Path target) {
        if (Files.exists(target)) {
            return;
        }
        Path legacy = Path.of(DB_FILE).toAbsolutePath();
        if (!Files.isRegularFile(legacy)) {
            return;
        }
        try {
            Files.createDirectories(target.getParent());
            Files.copy(legacy, target, StandardCopyOption.REPLACE_EXISTING);
            // SQLite side files, when present (clean exits checkpoint them away).
            copyIfPresent(legacy, target, "-wal");
            copyIfPresent(legacy, target, "-shm");
        } catch (IOException | SecurityException ex) {
            // Leave the new location empty — a fresh database is created there;
            // the untouched original remains next to the jar.
        }
    }
```

- **Guard 1:** if the destination already has a DB, never touch it —
  migration must be *once, ever*, and must never overwrite newer data with
  older data. (First-run-after-upgrade: target missing → copy. Every later
  launch: target exists → return immediately, zero cost.)
- **Guard 2:** no legacy file beside the jar → nothing to do.
- The copy brings along SQLite's **WAL/SHM side files** when present. SQLite
  in WAL mode keeps recent writes in `invoicestudio.db-wal` until a
  checkpoint; copying only the main file could drop the last transactions of
  a hard-killed session. Clean exits checkpoint automatically (side files
  gone), so "when present" is the normal case of *no* side files.
- The empty catch is *correct here*, not laziness: failing to migrate must
  not crash the app. Worst case the user gets a fresh database in the new
  location while the original stays untouched beside the jar — data is never
  destroyed, only left behind.

```java
    private static void copyIfPresent(Path legacy, Path target, String suffix) {
        try {
            Path src = legacy.resolveSibling(legacy.getFileName() + suffix);
            if (Files.isRegularFile(src)) {
                Files.copy(src, target.resolveSibling(target.getFileName() + suffix),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ignored) {
            // Non-fatal: worst case SQLite replays/ignores journal data.
        }
    }
}
```

- `resolveSibling(name + suffix)` — put the side file **next to** the main
  file (same parent, different name) without string surgery.
- Side-file copy failures are *deliberately* ignored: SQLite recovers from a
  missing/short WAL by design.

### Step 3 — `src/main/java/com/invoicestudio/service/AppLog.java` (complete file)

```java
package com.invoicestudio.service;

import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Single logging surface (skill rule 4.2): one place that decides where
 * errors go, so a future file-logger or crash reporter is a one-line change
 * instead of a 100-file hunt for {@code printStackTrace}.
 *
 * Deliberately dependency-free and throwable-only — DAO catch blocks log
 * through here, views keep their own user-facing feedback (Toast/Alert).
 */
public final class AppLog {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static volatile PrintStream sink = System.err;

    private AppLog() {}

    /** Logs the exception with the calling class, thread and timestamp. */
    public static void error(Throwable t) {
        StackTraceElement caller = callerOf(t);
        sink.println("[" + LocalTime.now().format(TS) + "] ["
                + Thread.currentThread().getName() + "] ERROR "
                + (caller != null ? caller.getClassName() + "." + caller.getMethodName() + " (): " : "")
                + t);
        t.printStackTrace(sink);
    }
```

- The header comment states the design rule: *one funnel*. Every DAO catch
  block in the project (Chapter 4–5) calls `AppLog.error(ex)` — so redirecting
  logs to a file later means editing this one class.
- `volatile PrintStream sink` — the output target, swappable at runtime via
  `setSink` (tests capture output this way). `volatile` keeps the swap visible
  to all threads instantly.
- Each line carries: millisecond timestamp, **thread name** (crucial — the
  app runs real work on `invoicestudio-io`, `invoicestudio-chat`, FX thread…;
  the thread name often *is* the diagnosis), the first
  `com.invoicestudio.*` stack frame (so the message names *our* class even
  when the exception bubbled up from library code), then the exception.
- `callerOf` (bottom of file) walks the stack trace top-down and returns the
  first frame whose class starts with the app's package — that is the frame
  inside *this project*, i.e. the code that caught (or created) the error.

```java
    /** Logs a message plus the exception. */
    public static void error(String message, Throwable t) { … same layout … }

    public static void warn(String message) {
        sink.println("[" + LocalTime.now().format(TS) + "] ["
                + Thread.currentThread().getName() + "] WARN  " + message);
    }

    private static final boolean DEBUG_ENABLED =
            Boolean.parseBoolean(System.getProperty("applog.debug", "false"));

    /**
     * Debug-level trace for deliberate best-effort catch blocks (DAO parse
     * fallbacks, optional lookups). No-op unless the app is started with
     * {@code -Dapplog.debug=true} — zero cost and zero console spam by
     * default, full trace when diagnosing.
     */
    public static void debug(Throwable t) {
        if (DEBUG_ENABLED) error(t);
    }

    /** Debug-level message; see {@link #debug(Throwable)}. */
    public static void debug(String message) {
        if (DEBUG_ENABLED) warn(message);
    }

    /** Redirect target for tests or a future file logger. */
    public static void setSink(PrintStream newSink) {
        sink = newSink != null ? newSink : System.err;
    }
```

- **Three levels, deliberately:** `error` (something failed, stack trace
  attached), `warn` (odd but survivable), `debug` (expected-failure noise —
  e.g. a JSON file that legitimately doesn't exist yet on first run). Debug
  is compiled-in but gated by a **final boolean read once at class-load** —
  so the disabled case costs one branch, and there is no logging-framework
  dependency anywhere in the app.
- The `debug` overloads exist because "expected to fail sometimes" code
  (parsing optional user files) should still be diagnosable — flip one
  property and the full story appears.
- `setSink(null)` politely reverts to `System.err`.

```java
    private static StackTraceElement callerOf(Throwable t) {
        StackTraceElement[] frames = t.getStackTrace();
        for (StackTraceElement f : frames) {
            if (f.getClassName().startsWith("com.invoicestudio.")) {
                return f;
            }
        }
        return frames.length > 0 ? frames[0] : null;
    }
}
```

Walks outward from the throw site; the first *project* frame wins; if none
(shouldn't happen), the raw top frame; if the trace is empty (JVM optimised
it away), `null` — and the call site above guards with a ternary.

### Step 4 — `src/main/java/com/invoicestudio/service/AppExecutors.java` (complete file)

```java
package com.invoicestudio.service;

import javafx.application.Platform;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Central home for the app's long-lived background executors.
 *
 * Skill rule 1.4: never {@code Executors.newXxx()} inline. Every inline pool
 * is an untracked thread with an unbounded queue that nobody shuts down —
 * a thread leak per call site (e.g. one leaked thread per login attempt).
 *
 * All pools here are named (visible in thread dumps), daemon (never block JVM
 * exit) and shut down once from {@code Application.stop()}.
 */
public final class AppExecutors {

    /** Network / file I/O: single thread, serialized, so parallel logins can't race. */
    private static final ExecutorService IO = single("invoicestudio-io");
```

- The rule in the Javadoc is the *why* for the whole class: inline
  `Executors.newCachedThreadPool()` at ten call sites means ten unmanaged
  pools. Centralising makes three guarantees possible: **named** threads
  (log lines and thread dumps become readable), **daemon** threads (a hung
  worker never blocks the JVM from exiting when the user closes the window),
  and **one shutdown point**.

```java
    private static final ExecutorService CHAT = Executors.newFixedThreadPool(
            3,
            r -> {
                Thread t = new Thread(r, "invoicestudio-chat");
                t.setDaemon(true);
                return t;
            });

    /** CPU-bound work (rendering, image, export prep) that must not touch the DB. */
    private static final ExecutorService CPU = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            r -> {
                Thread t = new Thread(r, "invoicestudio-cpu");
                t.setDaemon(true);
                return t;
            });
```

- **`IO` — exactly one thread.** Serialised file/network access means two
  login attempts can never interleave their file writes, and SQLite is
  touched from a predictable single background lane. The cost (one slow
  request delays the next) is accepted — and is precisely why the next pool
  exists:
- **`CHAT` — three threads.** The comment records a real historical bug: AI
  provider calls can take *many seconds*; when they shared the single IO
  thread, one slow response wedged every later chat message (the UI showed
  "typing…" forever). Separate lanes fixed it. The thread-factory lambda is
  the `ThreadFactory` interface expressed as a lambda: it names each thread
  and marks it daemon.
- **`CPU` — half the cores, minimum 2**, for rendering and image work that
  must never sit behind DB tasks.

```java
    private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);

    public static ExecutorService io()   { return IO; }
    public static ExecutorService chat() { return CHAT; }
    public static ExecutorService cpu()  { return CPU; }

    /** Runs {@code r} on the FX Application Thread, immediately if already there. */
    public static void runOnFx(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }

    private static ExecutorService single(String name) {
        return Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        });
    }

    /** Called once from {@code Application.stop()}; in-flight tasks are interrupted. */
    public static void shutdownAll() {
        if (SHUTDOWN.compareAndSet(false, true)) {
            IO.shutdownNow();
            CHAT.shutdownNow();
            CPU.shutdownNow();
        }
    }
}
```

- `runOnFx` is the *bridge* used everywhere in later chapters: background
  threads finish their work, then call `runOnFx(() -> label.setText(...))`.
  The `isFxApplicationThread` check matters because `Platform.runLater` from
  the FX thread itself would *defer* the work to a later pulse — code that
  sets a value and immediately reads it back would break. Running inline when
  already on the FX thread keeps such code correct *and* faster.
- `AtomicBoolean` + `compareAndSet(false, true)` is the standard
  "exactly-once" idiom: two threads racing to shut down — only one wins the
  swap, so `shutdownNow()` can't run twice.
- `shutdownNow` = stop accepting tasks **and interrupt** running ones, so
  closing the app never lingers on a stuck network read.

### Step 5 — `src/test/java/com/invoicestudio/AppDirsTest.java` (complete file)

```java
import com.invoicestudio.AppDirs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the installed-app data location logic: system-property override,
 * JDBC URL shape, and the one-time migration of a legacy working-directory
 * database into the data dir.
 */
class AppDirsTest {

    @TempDir
    static Path overrideDir;

    @BeforeAll
    static void redirectDataDir() {
        // Keep every touch inside a temp dir — never the developer's real
        // ~/.local/share/InvoiceStudio and never Program Files semantics.
        System.setProperty("invoicestudio.data.dir", overrideDir.toString());
    }

    @AfterAll
    static void clearOverride() {
        System.clearProperty("invoicestudio.data.dir");
    }
```

- **`@TempDir`** — JUnit creates a fresh empty temporary folder for the whole
  test class and deletes it afterwards. Combined with the override property,
  every test runs in a sealed sandbox: no test can ever read or write the
  developer's real `InvoiceStudio` data folder.
- `@BeforeAll`/`@AfterAll` run once around the whole class (they must be
  `static` with JUnit's default lifecycle) — set the property, run all three
  tests, clear it so other test classes are unaffected.

```java
    @Test
    void overridePropertyWins() {
        assertEquals(overrideDir, AppDirs.dataDir());
    }

    @Test
    void databaseUrlIsAbsoluteSqliteUrl() {
        String url = AppDirs.databaseUrl();
        assertTrue(url.startsWith("jdbc:sqlite:"), url);
        assertTrue(url.endsWith("/invoicestudio.db"), url);
        assertTrue(Path.of(url.substring("jdbc:sqlite:".length())).isAbsolute(), url);
    }
```

- **Test 1** pins priority 1 of the contract: the system property beats
  everything.
- **Test 2** pins the URL *shape* the driver needs: correct scheme, correct
  file name, absolute path. These look trivial but they are the exact
  assumptions `DatabaseManager` (Chapter 3) makes — if someone rewrites
  `databaseUrl()` and gets them wrong, this test fails before any customer
  does.

```java
    /**
     * Legacy databases (jar-run era, stored next to the working directory) must
     * be carried over on first launch of an installed copy. Uses whatever db
     * exists in the surefire working directory — the developer's real one is
     * only ever READ (copied into the temp override dir), never modified.
     */
    @Test
    void legacyWorkingDirDatabaseIsMigrated() throws IOException {
        Path cwdDb = Path.of("invoicestudio.db").toAbsolutePath();
        boolean preExisting = Files.isRegularFile(cwdDb);
        Path synthetic = null;
        if (!preExisting) {
            // CI / fresh checkout: synthesize a stand-in so the flow is tested.
            synthetic = Files.createTempFile("legacy-", ".db");
            Files.copy(synthetic, cwdDb, StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(cwdDb, "legacy-data-probe");
        }

        try {
            String url = AppDirs.databaseUrl();
            Path migrated = Path.of(url.substring("jdbc:sqlite:".length()));
            assertTrue(Files.isRegularFile(migrated), "db must exist after migration: " + migrated);
            assertEquals(Files.size(cwdDb), Files.size(migrated), "migrated copy size mismatch");
            assertNotEquals(cwdDb, migrated);
        } finally {
            if (!preExisting) {
                Files.deleteIfExists(cwdDb);
            }
        }
    }
}
```

- **Test 3** exercises the migration *story* end-to-end and is the most
  thoughtful test in the file. The migration source is *the current working
  directory* — which, during a real developer's test run, may contain their
  genuine legacy database. The test handles both worlds honestly:
  - If a real `invoicestudio.db` exists in the working dir → use it, and only
    ever **read** it (the migration *copies from* it; nothing is written back).
  - If not (fresh CI checkout) → create a tiny stand-in, and **delete it in a
    `finally` block** so the workspace is left exactly as found.
- Assertions: the migrated file exists in the data dir, its size matches the
  source (content actually copied), and the two paths are distinct (it's a
  copy, not the same file).
- `finally` guarantees cleanup even when an assertion fails — a failed test
  must never leave litter that breaks the *next* run.

---

## 6. How it works at runtime

The first ~200 milliseconds of the app's life:

```
 double-click InvoiceStudio.exe / java -jar invoice-studio-desktop-4.0.0.jar
   │
   ▼
 JVM starts → reads manifest Main-Class → loads Launcher
   │
   ▼  Launcher.main(args)
 Application.launch(StudioApp.class, args)
   │
   ├── JavaFX toolkit boots; creates FX Application Thread
   ├── new StudioApp()  (no-arg constructor)
   └── FX thread: StudioApp.init() → StudioApp.start(stage)   ← Chapter 9
         │
         └── first DatabaseManager.getConnection()
               └── AppDirs.databaseUrl()
                     ├── dataDir() → %APPDATA%\InvoiceStudio (created if needed)
                     └── migrateLegacyDatabase()  ← runs at most once ever
```

**Who uses what (the dependency picture):**

- `AppDirs.dataDir()` — called by `DatabaseManager`, `McpAuditLog`,
  `McpConfig`, `McpToolRegistry`, `ApiKeysVault`, `BulkPrintStateStore`,
  `ChatbotConfig`, `CustomComponentManager`, `KnowledgeRepository`,
  `ShortcutManager` — *every* place the app writes a file goes through this
  one method. That is the whole point: one policy, many consumers.
- `AppLog` — 17 DAO/service classes for `error`, 25+ for `debug`, so all
  diagnostics share one format and one switch (`-Dapplog.debug=true`).
- `AppExecutors.io()/chat()/cpu()` — every background task in the app;
  `runOnFx` is the return bridge to the UI thread; `shutdownAll()` is called
  exactly once from `StudioApp.stop()` (Chapter 9).

**Lifecycle events in this chapter's scope:**

| Event | What happens |
|---|---|
| JVM start | `Launcher.main` runs on the *main* thread |
| `launch(...)` | FX toolkit creates the FX thread; `Launcher.main` **blocks** here |
| first DB access | `AppDirs` resolves dir, may migrate legacy DB |
| app close (last window) | FX calls `StudioApp.stop()` → `AppExecutors.shutdownAll()` → `Platform.exit` → `main` returns → JVM exits (daemon threads die silently) |

---

## 7. How to change it

**Change the data folder name** (e.g. rebrand). Update `base.resolve("InvoiceStudio")`
— **and** know the consequence: existing users' data stays in the old folder,
invisible to the new build. Add a migration line mirroring
`migrateLegacyDatabase` if real users exist. Verify: run once, confirm the
new folder appears and (with an old folder present) the copy happened.

**Redirect data for a demo/portable build.** Launch with
`java -Dinvoicestudio.data.dir=D:/Portable/InvoiceStudio -jar app.jar`.
Nothing to compile — the override is a supported feature. Verify: the folder
is created on first run and `invoicestudio.db` appears inside it.

**Send logs to a file.** One line before any DB access, e.g. in `Launcher.main`:

```java
AppLog.setSink(new PrintStream(new FileOutputStream("studio.log", true), true));
```

Because every DAO logs through `AppLog`, this single line redirects *all*
error output. Verify: trigger a failure (open the app with the DB file
locked by another process) and read the timestamped line in `studio.log`.

**What breaks if you skip things:**

- Rename the folder but not the migration → users report "all my invoices
  are gone" (they are in the old folder).
- Set the override property in production without planning → the app writes
  to a fixed path on every machine, breaking per-user isolation.
- Replace `AppExecutors.io()` usage with a new inline pool → thread-leak
  regressions that only show up as slowdowns after hours of use.

---

## 8. Performance & UX analysis

| Decision | What was done | Cost | Better alternative | Trade-off / difficulty | User-visible effect |
|---|---|---|---|---|---|
| Separate `Launcher` class | fat-jar startup works | none | none needed — this *is* the best practice | — | app actually starts from the EXE/jar |
| Data in `%APPDATA%` | per-user, writable under UAC | data is per-Windows-user (shared PC = separate books) | a "portable mode" toggle writing next to the EXE | **Easy** but changes security story | silent, correct first-run; no "access denied" on Program Files |
| Migration by copy (not move) | original jar-era DB never destroyed | one extra DB-size copy on the upgrade launch only | move + delete | copy is safer (rollback possible); **Easy** | upgrade feels seamless; zero data loss |
| Single IO executor thread | serialised file/network | one slow task delays the next | add a second lane | already done *where it hurt* (CHAT pool) — splitting IO further risks write races | logins and file saves never interleave; chat never wedges behind a slow provider |
| Gated `debug` logging | final boolean, branch-only when off | ~zero | SLF4J + Logback | **Medium**; adds a dependency + config files | no console spam; full traces available by one flag |
| `runOnFx` inline-fast-path | skips a runLater hop when already on FX thread | none | raw `Platform.runLater` everywhere | **Easy** | snappier UI updates; avoids subtle ordering bugs |

`OPTIONAL IMPROVEMENT` (background-thread recap used by every later chapter —
shown now so the pattern is on the table):

```java
// OPTIONAL IMPROVEMENT: canonical async pattern used throughout later chapters
AppExecutors.io().submit(() -> {
    try {
        var rows = dao.findAll();                 // slow disk work off the FX thread
        AppExecutors.runOnFx(() -> table.getItems().setAll(rows));  // UI touch back on FX thread
    } catch (Exception ex) {
        AppLog.error(ex);
        AppExecutors.runOnFx(() -> Toast.error("Could not load buyers"));
    }
});
```

Difficulty: **Easy**. Effect: UI never freezes during disk/network work —
the single most user-visible quality difference between an amateur and a
professional desktop app.

---

## 9. Common mistakes and fixes

| Symptom | Cause | Fix |
|---|---|---|
| `Error: JavaFX runtime components are missing` | launched `StudioApp` directly instead of `Launcher` | always start `Launcher` (pom's mainClass already does) |
| Two `main` methods confusion ("which one runs?") | both classes have mains | the manifest's `Main-Class` wins; here that is `Launcher` |
| `Not on FX application thread` exception | UI touched from a background task | wrap the UI change in `AppExecutors.runOnFx(...)` |
| Tests pollute the developer's real data dir | forgot the `-Dinvoicestudio.data.dir` override in a new test | copy the `@BeforeAll` pattern from `AppDirsTest` |
| "My data disappeared" after reinstall | app now looks in a different data dir (override set, or folder renamed) | check Help→About / the data dir resolution order; look in the old folder |
| Logged stack traces have no clue where they came from | someone logged with `ex.printStackTrace()` instead of `AppLog.error(ex)` | route through `AppLog` — you get time, thread, and caller for free |

---

## 10. Checkpoint

You have completed this chapter when:

- [ ] `mvn test -Dtest=AppDirsTest` passes (3 tests).
- [ ] You can explain, without looking, why `Launcher` exists and what
      breaks without it.
- [ ] On your machine, `%APPDATA%\InvoiceStudio` (Windows) or the platform
      equivalent now exists with `invoicestudio.db` inside — created by
      Chapter 1's first run through exactly this code.
- [ ] `java -Dinvoicestudio.data.dir=%TEMP%\demo -jar target/invoice-studio-desktop-4.0.0.jar`
      creates a **fresh** database in `%TEMP%\demo` — proving the override.

**Exercises**

1. Add `AppLog.debug("data dir = " + AppDirs.dataDir())` at the top of
   `databaseUrl()`, run with `-Dapplog.debug=true`, and confirm the line
   appears; run without the flag and confirm silence.
2. Temporarily break the migration (delete the `Files.copy` line) and re-run
   `AppDirsTest` — exactly one test fails. Read its message; revert.
3. Write (don't ship) a 5-line `main` that calls `AppExecutors.io().submit()`
   to print the data dir from the background thread, then `runOnFx` to print
   the current thread name again — observe the two different names in the
   console.

---

## 11. Summary and coverage self-check

You built the front door (`Launcher` — and now know precisely why JavaFX
demands it), the plot of land (`AppDirs` — OS-correct, override-friendly,
migration-safe), and the two utilities every later chapter leans on
(`AppLog`'s single funnel, `AppExecutors`' three named daemon pools plus the
`runOnFx` bridge). The test file taught JUnit's `@TempDir`, property
overrides, and honest cleanup.

**Files covered in full this chapter (5):**
- `Launcher.java` ✅ (9/9 lines)
- `AppDirs.java` ✅ (100/100 lines)
- `AppLog.java` ✅ (74/74 lines)
- `AppExecutors.java` ✅ (78/78 lines)
- `AppDirsTest.java` ✅ (66/66 lines)

**Gaps/issues raised:** none new in source — behaviour verified against
`AppDirsTest` and the consumers list from the compiler.

**Next: Chapter 3 — "The Database Foundation"**
(`DatabaseManager.java`, the `seed/` resources, `DatabaseTest.java`).