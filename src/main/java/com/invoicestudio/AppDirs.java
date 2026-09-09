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

    /** Per-user writable application data directory (created if missing). */
    public static Path dataDir() {
        String override = System.getProperty(DIR_OVERRIDE_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
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
            Path dir = base.resolve("InvoiceStudio");
            Files.createDirectories(dir);
            return dir;
        } catch (IOException | SecurityException ex) {
            return Path.of("").toAbsolutePath(); // legacy behaviour as last resort
        }
    }

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
