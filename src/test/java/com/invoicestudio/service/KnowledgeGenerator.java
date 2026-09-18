package com.invoicestudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.invoicestudio.model.KnowledgeArticle;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Generator that compiles the TSC Reference Library and the AI Chatbot
 * Encyclopedia (delegating to the canonical {@link KnowledgeSeed}) into the
 * global codebase resource: {@code src/main/resources/knowledge/knowledge-hub.json}.
 *
 * <p>Run {@code mvn test -Dtest=KnowledgeGenerator} after editing the seed so
 * the bundled JSON resource always matches the code.</p>
 */
public class KnowledgeGenerator {

    @Test
    public void generateGlobalKnowledgeBase() throws IOException {
        List<KnowledgeArticle> articles = buildAllArticles();
        Path outDir = Paths.get("src", "main", "resources", "knowledge");
        Files.createDirectories(outDir);
        Path outFile = outDir.resolve("knowledge-hub.json");

        ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(outFile.toFile(), articles);

        System.out.println("Successfully generated global knowledge database with " + articles.size() + " articles at: " + outFile.toAbsolutePath());
        assertTrue(Files.exists(outFile));
        assertTrue(outFile.toFile().length() > 20000);
    }

    /**
     * Delegates to the canonical seed ({@link KnowledgeSeed#buildAllArticles()}).
     *
     * <p>History: this class used to carry its own FULL COPY of every article,
     * and that copy drifted out of sync — it kept shipping 11 AI chapters while
     * the seed gained the 12th ("Tool-Call Limit & Quota Failover"), so the
     * bundled {@code knowledge-hub.json} never contained that chapter and
     * installs never showed it. Delegating to the single source of truth makes
     * that class of bug impossible: regenerate the JSON and it always matches
     * what the code seeds.</p>
     */
    public static List<KnowledgeArticle> buildAllArticles() {
        return KnowledgeSeed.buildAllArticles();
    }
}
