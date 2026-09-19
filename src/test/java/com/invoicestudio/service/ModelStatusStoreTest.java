package com.invoicestudio.service;

import com.invoicestudio.AppDirs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Model status memory: success marks green, quota/balance walls mark red,
 * a later success flips red back to green, unknown models have no entry,
 * and stale red entries age out (daily quota buckets reset).
 */
class ModelStatusStoreTest {

    private static final String FILE = "model-status.json";
    private static Path overrideDir;

    @BeforeAll
    static void redirectDataDir() throws Exception {
        overrideDir = Files.createTempDirectory("model-status-test");
        System.setProperty("invoicestudio.data.dir", overrideDir.toString());
    }

    @AfterAll
    static void clearOverride() {
        System.clearProperty("invoicestudio.data.dir");
    }

    @AfterEach
    void cleanFile() throws Exception {
        Files.deleteIfExists(AppDirs.dataDir().resolve(FILE));
    }

    @Test
    void unknownModelHasNoEntry() {
        assertNull(ModelStatusStore.get("gemini", "gemini-never-used"));
    }

    @Test
    void blockedErrorMarksRedWithReason() {
        ModelStatusStore.markBlocked("gemini", "gemini-3.8-flash",
                "Daily free-tier limit reached for this model");
        ModelStatusStore.Entry e = ModelStatusStore.get("gemini", "gemini-3.8-flash");
        assertNotNull(e);
        assertEquals(ModelStatusStore.State.BLOCKED, e.state());
        assertTrue(e.reason().contains("Daily free-tier limit"));
    }

    @Test
    void successfulUseMarksGreen() {
        ModelStatusStore.markOk("gemini", "gemini-flash-lite-latest");
        ModelStatusStore.Entry e = ModelStatusStore.get("gemini", "gemini-flash-lite-latest");
        assertNotNull(e);
        assertEquals(ModelStatusStore.State.OK, e.state());
    }

    @Test
    void successFlipsRedBackToGreen() {
        ModelStatusStore.markBlocked("glm", "glm-4.6", "Insufficient balance");
        assertEquals(ModelStatusStore.State.BLOCKED,
                ModelStatusStore.get("glm", "glm-4.6").state());
        // User tops up balance / quota resets → the model works again.
        ModelStatusStore.markOk("glm", "glm-4.6");
        assertEquals(ModelStatusStore.State.OK,
                ModelStatusStore.get("glm", "glm-4.6").state());
    }

    @Test
    void statusesAreScopedPerProviderAndModel() {
        ModelStatusStore.markBlocked("glm", "glm-4.6", "Insufficient balance");
        assertNull(ModelStatusStore.get("gemini", "glm-4.6"), "same model id, other provider");
        assertNull(ModelStatusStore.get("glm", "glm-4.5-flash"), "other model, same provider");
    }

    @Test
    void blankInputsAreIgnored() {
        ModelStatusStore.markOk(null, "m");
        ModelStatusStore.markOk("gemini", "");
        ModelStatusStore.markBlocked("gemini", " ", "x");
        assertNull(ModelStatusStore.get(null, "m"));
        assertNull(ModelStatusStore.get("gemini", ""));
    }

    @Test
    void corruptFileIsToleratedAsEmpty() throws Exception {
        Files.write(AppDirs.dataDir().resolve(FILE), "]]]not json".getBytes());
        assertNull(ModelStatusStore.get("gemini", "anything"));
        // Store keeps working afterwards.
        ModelStatusStore.markOk("gemini", "recovers");
        assertEquals(ModelStatusStore.State.OK, ModelStatusStore.get("gemini", "recovers").state());
    }

    @Test
    void statePersistsToDiskAcrossReaders() {
        ModelStatusStore.markBlocked("glm", "glm-4.6", "Insufficient balance");
        assertTrue(Files.exists(AppDirs.dataDir().resolve(FILE)),
                "status must survive an app restart (dot still red next launch)");
    }
}
