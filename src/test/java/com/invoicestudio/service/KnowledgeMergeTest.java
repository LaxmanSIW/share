package com.invoicestudio.service;

import com.invoicestudio.model.KnowledgeArticle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks the Knowledge Hub merge-on-load contract:
 * <ul>
 *   <li>a stale local library still WINS for the ids it already has
 *       (user edits are never clobbered), and</li>
 *   <li>articles shipped NEW with an app update are merged in — this is the
 *       fix for "chapter not showing" reports (older installs kept their
 *       original 24-article list forever and never received the
 *       "07. Tool-Call Limit &amp; Quota Failover" chapter).</li>
 * </ul>
 */
class KnowledgeMergeTest {

    @TempDir
    Path tempDir;

    @Test
    void staleLocalLibraryMergesShippedChaptersAndKeepsLocalEdits() throws Exception {
        long now = System.currentTimeMillis();
        KnowledgeArticle localCustom = new KnowledgeArticle(
                "art_local_custom", "Local / Notes", "My Local Note",
                "user created", "Local content", now, "User");
        KnowledgeArticle locallyEdited = new KnowledgeArticle(
                "art_tsc_overview", "TSC / TA210 — Printer", "Local Edit Title",
                "user edited", "Edited body", now, "User");
        String localJson = "[" +
                com.fasterxml.jackson.databind.ObjectMapper.class.getName() + "]";
        // Write the local library the "old" way — as an app update from before
        // chapters 12/13 shipped would have saved it.
        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper()
                .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT);
        Path library = tempDir.resolve("knowledge-hub.json");
        m.writeValue(library.toFile(), List.of(localCustom, locallyEdited));
        assertNotNull(localJson); // (keeps the JSON import honest)

        KnowledgeRepository repo = KnowledgeRepository.createCustom(library);

        // 1. Local-only article survives
        Optional<KnowledgeArticle> mine = repo.getArticleById("art_local_custom");
        assertTrue(mine.isPresent(), "local-only articles must survive the merge");

        // 2. Local edits are NOT clobbered by the shipped copy
        assertEquals("Local Edit Title", repo.getArticleById("art_tsc_overview").orElseThrow().title());

        // 3. Shipped NEW chapters are merged in — including the previously
        //    missing "chapter 7" (Tool-Call Limit & Quota Failover)
        assertTrue(repo.getArticleById("art_ai_12_tool_limit_failover").isPresent(),
                "the previously-missing tool-limit chapter must merge into stale libraries");
        assertTrue(repo.getArticleById("art_ai_13_speed_optimization").isPresent(),
                "newly shipped optimization chapter must merge into stale libraries");

        // 4. Persisted: the merged library is saved back to local storage
        KnowledgeArticle[] persisted = m.readValue(library.toFile(), KnowledgeArticle[].class);
        assertEquals(repo.getAllArticles().size(), persisted.length,
                "merged library must be persisted");
    }

    @Test
    void bundledResourceContainsToolLimitChapter() throws Exception {
        // The shipped resource itself must no longer lag the seed (the root
        // cause of the missing chapter): 13 TSC + 17 AI = 30 articles.
        try (var in = KnowledgeRepository.class.getResourceAsStream("/knowledge/knowledge-hub.json")) {
            assertNotNull(in, "bundled knowledge-hub.json must exist");
            com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
            List<KnowledgeArticle> bundled = m.readValue(
                    in, m.getTypeFactory().constructCollectionType(List.class, KnowledgeArticle.class));
            assertTrue(bundled.stream().anyMatch(a -> "art_ai_12_tool_limit_failover".equals(a.id())),
                    "bundled resource must contain the tool-limit chapter");
            assertTrue(bundled.stream().anyMatch(a -> "art_ai_13_speed_optimization".equals(a.id())),
                    "bundled resource must contain the speed-optimization chapter");
            assertTrue(bundled.stream().anyMatch(a -> "art_ai_14_bug_playbook".equals(a.id())),
                    "bundled resource must contain the bug-playbook chapter");
            assertTrue(bundled.stream().anyMatch(a -> "art_ai_15_round3_tokens_glm_pipeline".equals(a.id())),
                    "bundled resource must contain the round-3 chapter");
            assertTrue(bundled.stream().anyMatch(a -> "art_ai_16_round4_instant_greetings_new_fullsurface".equals(a.id())),
                    "bundled resource must contain the round-4 change-log chapter");
            assertTrue(bundled.stream().anyMatch(a -> "art_ai_17_round5_logs_vault_stock_dropdown".equals(a.id())),
                    "bundled resource must contain the round-5 change-log chapter");
            assertEquals(30, bundled.size(), "seed and bundled resource must stay in sync");
        }
        assertTrue(Files.exists(tempDir), "tempdir sanity");
    }
}
