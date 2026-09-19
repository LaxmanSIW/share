package com.invoicestudio.service;

import com.invoicestudio.AppDirs;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * API-key vault: persistence roundtrip, idempotent re-save, provider lookup,
 * masking, corrupt-file tolerance, change notification. Runs against an
 * isolated temp data dir (same pattern as {@code AppDirsTest}) so it never
 * touches a real user vault.
 */
class ApiKeysVaultTest {

    private static final String FILE = "api-vault.json";
    private static Path overrideDir;

    @BeforeAll
    static void redirectDataDir() throws Exception {
        overrideDir = Files.createTempDirectory("api-vault-test");
        System.setProperty("invoicestudio.data.dir", overrideDir.toString());
    }

    @AfterAll
    static void clearOverride() {
        System.clearProperty("invoicestudio.data.dir");
    }

    @AfterEach
    void cleanVaultFile() throws Exception {
        Files.deleteIfExists(AppDirs.dataDir().resolve(FILE));
    }

    @Test
    void addAndListRoundtripPersistsToDisk() {
        ApiKeysVault.VaultEntry e = ApiKeysVault.add("Gemini free tier", "gemini", "AIzaSyTESTKEY1234567890");
        List<ApiKeysVault.VaultEntry> all = ApiKeysVault.list();
        assertEquals(1, all.size());
        assertEquals(e.id(), all.get(0).id());
        assertEquals("Gemini free tier", all.get(0).label());
        assertEquals("gemini", all.get(0).provider());
        assertEquals("AIzaSyTESTKEY1234567890", all.get(0).key());
        assertNotNull(all.get(0).createdAt());
        // Written to disk in the (overridden) data dir — a fresh reader sees it.
        assertTrue(Files.exists(AppDirs.dataDir().resolve(FILE)));
    }

    @Test
    void reSavingSameKeyUpdatesLabelInsteadOfDuplicating() {
        ApiKeysVault.add("Gemini free tier", "gemini", "AIzaSyTESTKEY1234567890");
        ApiKeysVault.VaultEntry renamed = ApiKeysVault.add("Gemini main", "gemini", "AIzaSyTESTKEY1234567890");
        List<ApiKeysVault.VaultEntry> all = ApiKeysVault.list();
        assertEquals(1, all.size(), "same provider+key must not duplicate");
        assertEquals("Gemini main", all.get(0).label(), "label refreshed on re-save");
        assertEquals(renamed.id(), all.get(0).id());
    }

    @Test
    void blankLabelFallsBackToProviderDefault() {
        ApiKeysVault.VaultEntry e = ApiKeysVault.add("   ", "glm", "glmsupersecretkey001");
        assertEquals(AiChatClient.providerLabel("glm") + " key", e.label());
    }

    @Test
    void removeDeletesOnlyTheTargetEntry() {
        ApiKeysVault.VaultEntry a = ApiKeysVault.add("A", "gemini", "key-a-1234567890");
        ApiKeysVault.VaultEntry b = ApiKeysVault.add("B", "glm", "key-b-1234567890");
        ApiKeysVault.remove(a.id());
        List<ApiKeysVault.VaultEntry> all = ApiKeysVault.list();
        assertEquals(1, all.size());
        assertEquals(b.id(), all.get(0).id());
        ApiKeysVault.remove("nonexistent-id"); // no-op, no throw
        assertEquals(1, ApiKeysVault.list().size());
    }

    @Test
    void findForProviderReturnsFirstMatchOnly() {
        ApiKeysVault.add("G1", "gemini", "gemini-key-0001");
        ApiKeysVault.add("G2", "gemini", "gemini-key-0002");
        ApiKeysVault.add("Z", "glm", "glm-key-000000001");
        assertEquals("G1", ApiKeysVault.findForProvider("gemini").get().label());
        assertEquals("Z", ApiKeysVault.findForProvider("glm").get().label());
        assertTrue(ApiKeysVault.findForProvider("openai").isEmpty());
        assertTrue(ApiKeysVault.findForProvider(null).isEmpty());
    }

    @Test
    void maskHidesEverythingButHeadAndTail() {
        assertEquals("AIzaS••••wxyz", ApiKeysVault.mask("AIzaS1234567890123456wxyz"));
        assertEquals("•••••", ApiKeysVault.mask("short"));        // short keys fully hidden
        assertEquals("•••••", ApiKeysVault.mask("123456789"));    // boundary (<=9)
        assertEquals("—", ApiKeysVault.mask(null));
        assertEquals("—", ApiKeysVault.mask("   "));
    }

    @Test
    void corruptVaultFileIsToleratedAsEmpty() throws Exception {
        Files.write(AppDirs.dataDir().resolve(FILE), "{not valid json!!".getBytes());
        assertTrue(ApiKeysVault.list().isEmpty(), "corrupt file reads as empty, never throws");
        // Vault still usable afterwards — the next save overwrites cleanly.
        ApiKeysVault.add("After crash", "gemini", "recovered-key-123456");
        assertEquals(1, ApiKeysVault.list().size());
    }

    @Test
    void changeListenersAreNotifiedOnMutation() {
        List<List<ApiKeysVault.VaultEntry>> events = new CopyOnWriteArrayList<>();
        Consumer<List<ApiKeysVault.VaultEntry>> listener = events::add;
        ApiKeysVault.addChangeListener(listener);
        try {
            ApiKeysVault.VaultEntry e = ApiKeysVault.add("Notified", "gemini", "listener-key-1234567");
            assertEquals(1, events.size());
            assertEquals(1, events.get(0).size());
            assertEquals(e.id(), events.get(0).get(0).id());
            ApiKeysVault.remove(e.id());
            assertEquals(2, events.size());
            assertTrue(events.get(1).isEmpty());
        } finally {
            ApiKeysVault.removeChangeListener(listener);
        }
    }
}
