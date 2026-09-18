package com.invoicestudio.ui;

import com.invoicestudio.model.KnowledgeArticle;
import com.invoicestudio.service.KnowledgeRepository;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KnowledgeHubPanelTest {

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {}
    }

    @Test
    void loadsAndRendersArticlesInKnowledgeHub() {
        KnowledgeRepository repo = KnowledgeRepository.getInstance();
        List<KnowledgeArticle> articles = repo.getAllArticles();
        assertFalse(articles.isEmpty(), "Knowledge repository must contain articles");

        Platform.runLater(() -> {
            KnowledgeHubPanel panel = new KnowledgeHubPanel();
            assertNotNull(panel);
            assertTrue(panel.getChildren().size() >= 2, "Panel must contain hero and body");
        });
    }
}
