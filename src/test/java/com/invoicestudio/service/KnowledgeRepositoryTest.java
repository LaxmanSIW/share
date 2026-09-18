package com.invoicestudio.service;

import com.invoicestudio.model.KnowledgeArticle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeRepositoryTest {

    private Path tempFile;
    private KnowledgeRepository repo;

    @BeforeEach
    void setUp() throws IOException {
        tempFile = Files.createTempFile("knowledge-hub-test-", ".json");
        Files.deleteIfExists(tempFile);
        repo = KnowledgeRepository.createCustom(tempFile);
    }

    @AfterEach
    void tearDown() throws IOException {
        Files.deleteIfExists(tempFile);
    }

    @Test
    void seedsDefaultArticlesUnderTscRoot() {
        List<KnowledgeArticle> articles = repo.getAllArticles();
        assertFalse(articles.isEmpty(), "Should seed default articles");
        assertTrue(articles.size() >= 12, "Should seed at least 12 TSC articles");

        // Verify all default articles start under TSC
        for (KnowledgeArticle a : articles) {
            assertTrue(a.path().startsWith("TSC"), "Default article path must start with 'TSC': " + a.path());
            assertNotNull(a.markdown(), "Article markdown must not be null");
            assertFalse(a.markdown().isBlank(), "Article markdown must not be blank");
        }

        // Verify persistence to disk
        assertTrue(Files.exists(tempFile), "Should create JSON file on disk");
        assertTrue(tempFile.toFile().length() > 500, "JSON file should contain data");
    }

    @Test
    void buildsMultiLevelCategoryTree() {
        KnowledgeRepository.CategoryNode root = repo.buildCategoryTree();
        assertNotNull(root);
        assertFalse(root.subCategories().isEmpty());

        // Find the "TSC" root category
        KnowledgeRepository.CategoryNode tscNode = root.subCategories().stream()
                .filter(c -> "TSC".equalsIgnoreCase(c.name()))
                .findFirst()
                .orElse(null);

        assertNotNull(tscNode, "Category tree must contain 'TSC' as top-level category");
        assertTrue(tscNode.subCategories().size() >= 4, "TSC should have subcategories like TA210, TSPL, etc.");
        assertTrue(tscNode.totalArticles() >= 12, "TSC should contain all seeded articles");
    }

    @Test
    void savesAndRetrievesNewCustomArticle() {
        KnowledgeArticle custom = new KnowledgeArticle(
                "art_custom_gst",
                "Invoicing / Taxes / GST",
                "GST Input Tax Credit Guide",
                "How to claim ITC in InvoiceStudio",
                "### Claiming ITC\nAlways enter a valid 15-digit GSTIN on purchases.",
                System.currentTimeMillis()
        );

        repo.saveArticle(custom);

        Optional<KnowledgeArticle> retrieved = repo.getArticleById("art_custom_gst");
        assertTrue(retrieved.isPresent());
        assertEquals("GST Input Tax Credit Guide", retrieved.get().title());
        assertEquals("Invoicing / Taxes / GST", retrieved.get().path());

        // Reload from disk to verify persistence
        KnowledgeRepository reloadedRepo = KnowledgeRepository.createCustom(tempFile);
        Optional<KnowledgeArticle> fromDisk = reloadedRepo.getArticleById("art_custom_gst");
        assertTrue(fromDisk.isPresent());
        assertEquals("GST Input Tax Credit Guide", fromDisk.get().title());
    }

    @Test
    void updatesExistingArticle() {
        Optional<KnowledgeArticle> firstOpt = repo.getAllArticles().stream().findFirst();
        assertTrue(firstOpt.isPresent());
        KnowledgeArticle first = firstOpt.get();

        KnowledgeArticle updated = first.withUpdates(
                first.path(),
                "Updated " + first.title(),
                first.subtitle(),
                "# New Markdown Content\n\nUpdated paragraph text."
        );

        repo.saveArticle(updated);

        Optional<KnowledgeArticle> fetched = repo.getArticleById(first.id());
        assertTrue(fetched.isPresent());
        assertEquals("Updated " + first.title(), fetched.get().title());
        assertTrue(fetched.get().markdown().contains("New Markdown Content"));
    }

    @Test
    void deletesArticleSuccessfully() {
        KnowledgeArticle toDelete = new KnowledgeArticle(
                "art_to_delete",
                "Drafts",
                "Temporary Note",
                "",
                "This will be deleted.",
                System.currentTimeMillis()
        );
        repo.saveArticle(toDelete);
        assertTrue(repo.getArticleById("art_to_delete").isPresent());

        boolean deleted = repo.deleteArticle("art_to_delete");
        assertTrue(deleted);
        assertFalse(repo.getArticleById("art_to_delete").isPresent());
    }

    @Test
    void categoryNodeMatchesQuery() {
        KnowledgeRepository.CategoryNode root = repo.buildCategoryTree();
        assertTrue(root.matches("bitmap"));
        assertTrue(root.matches("TA210"));
        assertTrue(root.matches("sensor"));
        assertFalse(root.matches("non_existent_xyz_random_string"));
    }
}
