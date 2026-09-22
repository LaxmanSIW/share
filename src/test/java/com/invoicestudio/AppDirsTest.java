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

    /**
     * The file dialogs for template download/upload open here, so the override
     * must win verbatim — that is what the UI tests rely on to stay sandboxed.
     */
    @Test
    void downloadsOverridePropertyWins() {
        System.setProperty("invoicestudio.downloads.dir", overrideDir.toString());
        try {
            assertEquals(overrideDir, AppDirs.downloadsDir());
        } finally {
            System.clearProperty("invoicestudio.downloads.dir");
        }
    }

    /**
     * Without the override the helper must still hand back a folder that really
     * exists — a FileChooser throws on a bogus initial directory, which would
     * break the download/upload buttons on accounts without a ~/Downloads.
     */
    @Test
    void downloadsDirIsAlwaysAnExistingFolder() {
        Path downloads = AppDirs.downloadsDir();
        assertTrue(Files.isDirectory(downloads), "not a real folder: " + downloads);
        assertTrue(downloads.isAbsolute(), "chooser needs an absolute path: " + downloads);
    }
}
