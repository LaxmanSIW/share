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
    void seedsDefaultArticlesWithTscAndAiChatbot() {
        List<KnowledgeArticle> articles = repo.getAllArticles();
        assertFalse(articles.isEmpty(), "Should seed default articles");
        assertTrue(articles.size() >= 24, "Should seed at least 24 articles (TSC + AI Chatbot): got " + articles.size());

        // Verify articles belong to either TSC or AI Chatbot and carry authors
        for (KnowledgeArticle a : articles) {
            assertTrue(a.path().startsWith("TSC") || a.path().startsWith("AI Chatbot"),
                    "Default article path must start with 'TSC' or 'AI Chatbot': " + a.path());
            assertNotNull(a.markdown(), "Article markdown must not be null");
            assertFalse(a.markdown().isBlank(), "Article markdown must not be blank");
            assertNotNull(a.author(), "Article author must not be null");
            assertFalse(a.author().isBlank(), "Article author must not be blank");
        }

        // Verify persistence to disk
        assertTrue(Files.exists(tempFile), "Should create JSON file on disk");
        assertTrue(tempFile.toFile().length() > 500, "JSON file should contain data");
    }

    @Test
    void buildsMultiLevelCategoryTreeWithBothRoots() {
        KnowledgeRepository.CategoryNode root = repo.buildCategoryTree();
        assertNotNull(root);
        assertFalse(root.subCategories().isEmpty());

        // Find "TSC" root category
        KnowledgeRepository.CategoryNode tscNode = root.subCategories().stream()
                .filter(c -> "TSC".equalsIgnoreCase(c.name()))
                .findFirst()
                .orElse(null);
        assertNotNull(tscNode, "Category tree must contain 'TSC' as top-level category");
        assertTrue(tscNode.subCategories().size() >= 4, "TSC should have subcategories like TA210, TSPL, etc.");
        assertTrue(tscNode.totalArticles() >= 12, "TSC should contain at least 12 articles");

        // Find "AI Chatbot" root category
        KnowledgeRepository.CategoryNode aiNode = root.subCategories().stream()
                .filter(c -> "AI Chatbot".equalsIgnoreCase(c.name()))
                .findFirst()
                .orElse(null);
        assertNotNull(aiNode, "Category tree must contain 'AI Chatbot' as top-level category");
        assertTrue(aiNode.subCategories().size() >= 5, "AI Chatbot should have at least 5 chapters/subcategories");
        assertTrue(aiNode.totalArticles() >= 11, "AI Chatbot should contain at least 11 articles");
    }

    @Test
    void savesAndRetrievesNewCustomArticleWithAuthor() {
        KnowledgeArticle custom = new KnowledgeArticle(
                "art_custom_gst",
                "Invoicing / Taxes / GST",
                "GST Input Tax Credit Guide",
                "How to claim ITC in InvoiceStudio",
                "### Claiming ITC\nAlways enter a valid 15-digit GSTIN on purchases.",
                System.currentTimeMillis(),
                "Kapto Accountant"
        );

        repo.saveArticle(custom);

        Optional<KnowledgeArticle> retrieved = repo.getArticleById("art_custom_gst");
        assertTrue(retrieved.isPresent());
        assertEquals("GST Input Tax Credit Guide", retrieved.get().title());
        assertEquals("Invoicing / Taxes / GST", retrieved.get().path());
        assertEquals("Kapto Accountant", retrieved.get().author());

        // Reload from disk to verify persistence
        KnowledgeRepository reloadedRepo = KnowledgeRepository.createCustom(tempFile);
        Optional<KnowledgeArticle> fromDisk = reloadedRepo.getArticleById("art_custom_gst");
        assertTrue(fromDisk.isPresent());
        assertEquals("GST Input Tax Credit Guide", fromDisk.get().title());
        assertEquals("Kapto Accountant", fromDisk.get().author());
    }

    @Test
    void updatesExistingArticlePreservingAuthor() {
        Optional<KnowledgeArticle> firstOpt = repo.getAllArticles().stream().findFirst();
        assertTrue(firstOpt.isPresent());
        KnowledgeArticle first = firstOpt.get();

        KnowledgeArticle updated = first.withUpdates(
                first.path(),
                "Updated " + first.title(),
                first.subtitle(),
                "# New Markdown Content\n\nUpdated paragraph text.",
                "Custom Editor"
        );

        repo.saveArticle(updated);

        Optional<KnowledgeArticle> fetched = repo.getArticleById(first.id());
        assertTrue(fetched.isPresent());
        assertEquals("Updated " + first.title(), fetched.get().title());
        assertEquals("Custom Editor", fetched.get().author());
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
                System.currentTimeMillis(),
                "Tester"
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
        assertTrue(root.matches("Smart Routing"));
        assertTrue(root.matches("Smalltalk"));
        assertTrue(root.matches("InvoiceStudio AI Core"));
        assertFalse(root.matches("non_existent_xyz_random_string"));
    }
}
