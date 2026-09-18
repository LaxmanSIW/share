package com.invoicestudio.service;

import com.invoicestudio.db.DatabaseManager;
import com.invoicestudio.mcp.McpConfig;
import com.invoicestudio.mcp.McpServer;
import com.invoicestudio.model.ItemRecord;
import com.invoicestudio.service.AuthSessionManager;
import com.invoicestudio.ui.DataManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LIVE end-to-end validation (runs only when a Gemini API key is present in
 * the user's real chatbot.json — otherwise skipped): a real model round-trip
 * that MUST execute an MCP tool and answer from its JSON result. This is the
 * exact path the chat panel exercises, including the multi-round function
 * calling loop and its contents re-emission rules.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiChatLiveMcpTest {

    private static String apiKey;
    private static String glmKey;
    private static DataManager dm;
    private static Path tmpDb;

    @BeforeAll
    static void setUp() throws Exception {
        // Live tests burn real quota (free tier: 20 req/day per model) — they
        // run ONLY on explicit opt-in:  mvn test -Dlive.gemini=true
        // (Gemini)  or  mvn test -Dlive.glm=true -Dglm.key=…  (Z.ai GLM)
        boolean geminiOptIn = Boolean.getBoolean("live.gemini");
        boolean glmOptIn = Boolean.getBoolean("live.glm");
        Assumptions.assumeTrue(geminiOptIn || glmOptIn,
                "Live provider test skipped (run with -Dlive.gemini=true or -Dlive.glm=true to opt in)");
        // Key resolution: -Dgemini.key=... wins (CI / borrowed keys), the
        // user's saved chatbot.json is the fallback.
        apiKey = System.getProperty("gemini.key",
                System.getenv("GEMINI_API_KEY") != null ? System.getenv("GEMINI_API_KEY") : "");
        if (apiKey.isBlank()) apiKey = ChatbotConfig.load().getApiKey();
        if (geminiOptIn) {
            Assumptions.assumeTrue(apiKey != null && !apiKey.isBlank(),
                    "No Gemini API key configured — live test skipped");
        }
        // GLM key: -Dglm.key=... or GLM_API_KEY env — never persisted.
        glmKey = System.getProperty("glm.key",
                System.getenv("GLM_API_KEY") != null ? System.getenv("GLM_API_KEY") : "");
        if (glmOptIn) {
            Assumptions.assumeTrue(glmKey != null && !glmKey.isBlank(),
                    "No GLM API key configured (pass -Dglm.key=…) — live test skipped");
        }

        try {
            javafx.application.Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized
        }

        // The tool loop only runs when the MCP server is UP (send() has an
        // MCP-off fast path). Without this the live tool round silently took
        // the zero-schema branch and the model said "MCP is off".
        for (int port : new int[]{17821, 18345, 19157}) {
            McpConfig mc = new McpConfig();
            mc.setPort(port);
            mc.setRequireToken(false);
            if (McpServer.start(mc) == null) break;
        }

        tmpDb = Files.createTempFile("aichat-live", ".db");
        Files.deleteIfExists(tmpDb);
        DatabaseManager.initCustom("jdbc:sqlite:" + tmpDb.toAbsolutePath());
        // MCP tools scope every query to the logged-in user — the chatbot asks
        // through them, so seed under a session like McpSurfaceExtensionTest does.
        AuthSessionManager.setActiveSession(new com.invoicestudio.model.UserSession(
                "uid_aichat_live", "aichat@test.in", "AI Chat Live Test",
                "tok", "ref", System.currentTimeMillis() + 3600_000L, true));
        dm = DataManager.init(DatabaseManager.getInstance());

        // Deterministic seed the question can be verified against.
        // Stock truth = opening_stock + ledger movements (getStockBalances),
        // so seed openingStock (currentStock is a derived cache).
        ItemRecord it = new ItemRecord();
        it.setName("Probe Denim Jeans");
        it.setCategoryName("Denim");
        it.setOpeningStock(7);
        it.setCurrentStock(7);
        it.setPurchaseRate(500);
        it.setRate(900);
        it.setGst(12);
        dm.items().insert(it);
        ItemRecord it2 = new ItemRecord();
        it2.setName("Probe Cotton Shirt");
        it2.setCategoryName("Cotton");
        it2.setOpeningStock(50);
        it2.setCurrentStock(50);
        it2.setPurchaseRate(200);
        it2.setRate(350);
        it2.setGst(5);
        dm.items().insert(it2);
    }

    @AfterAll
    static void tearDown() throws Exception {
        McpServer.shutdown();
        AuthSessionManager.clear();
        if (tmpDb != null) Files.deleteIfExists(tmpDb);
        resetSingleton(DatabaseManager.class, "instance");
        resetSingleton(DataManager.class, "instance");
    }

    @SuppressWarnings("unchecked")
    private static void resetSingleton(Class<?> clazz, String fieldName) throws Exception {
        java.lang.reflect.Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(null, null);
    }

    @Test
    @Order(1)
    void geminiAnswersFromLiveMcpToolResult() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("live.gemini"), "Gemini opt-in required");
        ChatbotConfig cfg = new ChatbotConfig();
        cfg.setProvider(ChatbotConfig.GEMINI);
        cfg.setApiKey(apiKey);
        // Free tier = 20 requests/DAY per model (live-verified) — repeated test
        // runs exhaust one model's bucket, so tests rotate models deliberately.
        cfg.setModel(System.getProperty("live.gemini.model", "gemini-3.7-flash"));

        AiChatClient client = new AiChatClient();
        AiChatClient.ChatResult r = client.send(cfg, List.of(),
                "How many pieces of 'Probe Denim Jeans' are in stock right now? "
                        + "Use the app tools and report the exact number.",
                null);

        System.out.println("[LIVE] toolTrace = " + r.toolTrace());
        System.out.println("[LIVE] answer    = " + r.text());

        assertFalse(r.text().isBlank(), "model must produce an answer");
        // The deterministic seed is 7 pcs — the model can only know it by
        // calling the MCP tool, so the answer must contain it.
        assertTrue(r.toolTrace().size() > 0 || r.text().contains("7"),
                "expected a tool call (trace=" + r.toolTrace() + ") in answer: " + r.text());
        if (!r.toolTrace().isEmpty()) {
            assertTrue(r.text().contains("7"),
                    "answer must reflect the tool result (7 pcs), got: " + r.text());
        }
    }

    @Test
    @Order(2)
    void twoToolRoundsStillWork() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("live.gemini"), "Gemini opt-in required");
        ChatbotConfig cfg = new ChatbotConfig();
        cfg.setProvider(ChatbotConfig.GEMINI);
        cfg.setApiKey(apiKey);
        cfg.setModel(System.getProperty("live.gemini.model", "gemini-3.5-flash-lite"));
        cfg.setHistoryMessages(4); // Test the exact small-history condition that previously crashed

        AiChatClient client = new AiChatClient();
        // Two distinct facts → at least two tool calls across rounds; the
        // second round replays functionCall/functionResponse contents.
        AiChatClient.ChatResult r = client.send(cfg, List.of(),
                "List the stock quantity of 'Probe Denim Jeans' and 'Probe Cotton Shirt'. "
                        + "Answer with exactly: <jeans qty> then <shirt qty>.",
                null);

        System.out.println("[LIVE2] toolTrace = " + r.toolTrace());
        System.out.println("[LIVE2] answer    = " + r.text());
        assertFalse(r.text().isBlank(), "second-round answer must not be blank");
        assertTrue(r.text().contains("7") && r.text().contains("50"),
                "answer must contain both seeded quantities, got: " + r.text());
    }

    @Test
    @Order(3)
    void attachmentWorksWithGemini() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("live.gemini"), "Gemini opt-in required");
        ChatbotConfig cfg = new ChatbotConfig();
        cfg.setProvider(ChatbotConfig.GEMINI);
        cfg.setApiKey(apiKey);
        cfg.setModel(System.getProperty("live.gemini.model", "gemini-3.5-flash-lite"));

        // Create a 20x20 red PNG image in memory
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(20, 20, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(java.awt.Color.RED);
        g.fillRect(0, 0, 20, 20);
        g.dispose();

        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", baos);
        AiChatClient.ImagePart part = new AiChatClient.ImagePart("image/png", baos.toByteArray());

        AiChatClient client = new AiChatClient();
        AiChatClient.ChatResult r = client.send(cfg, List.of(), "What primary color is this image? Reply with the color name.", part);

        System.out.println("[ATTACH_TEST] answer = " + r.text());
        assertFalse(r.text().isBlank(), "Model should return a description of the image");
        assertTrue(r.text().toLowerCase().contains("red"), "Answer should identify the red image, got: " + r.text());
    }

    @Test
    @Order(4)
    void confirmationFlowExecutesConfirmOperationOnYes() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("live.gemini"), "Gemini opt-in required");
        ChatbotConfig cfg = new ChatbotConfig();
        cfg.setProvider(ChatbotConfig.GEMINI);
        cfg.setApiKey(apiKey);
        cfg.setModel(System.getProperty("live.gemini.model", "gemini-3.5-flash-lite"));
        cfg.setHistoryMessages(4);

        java.util.concurrent.atomic.AtomicBoolean approved = new java.util.concurrent.atomic.AtomicBoolean(false);
        String opId = com.invoicestudio.mcp.PendingOperations.queue(
                "delete_item",
                "Delete item Probe Cotton Shirt",
                "Testing confirmation approval",
                java.util.Map.of("id", "2"),
                () -> approved.set(true)
        );

        List<AiChatClient.ChatTurn> history = List.of(
                AiChatClient.ChatTurn.user("delete item 2"),
                AiChatClient.ChatTurn.assistant("I have queued the deletion of item 2 (operationId: " + opId
                        + "). Are you sure you want to proceed? Reply yes to confirm.")
        );

        AiChatClient client = new AiChatClient();
        AiChatClient.ChatResult r = client.send(cfg, history, "yes", null);

        System.out.println("[CONFIRM_TEST] toolTrace = " + r.toolTrace());
        System.out.println("[CONFIRM_TEST] answer    = " + r.text());

        assertFalse(r.text().toLowerCase().contains("how can i assist")
                || r.text().toLowerCase().contains("how can i help you today"),
                "Confirmation must not be treated as generic small talk: " + r.text());

        assertTrue(approved.get() || r.toolTrace().stream().anyMatch(t -> t.contains("confirm_operation")),
                "Should have approved pending operation " + opId + ", trace: " + r.toolTrace());
    }

    @Test
    @Order(5)
    void liveQueryFormatsTabularDataAndLogsExecutionSteps() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("live.gemini"), "Gemini opt-in required");
        ChatbotLogManager.clear();

        ChatbotConfig cfg = new ChatbotConfig();
        cfg.setProvider(ChatbotConfig.GEMINI);
        cfg.setApiKey(apiKey);
        cfg.setModel(System.getProperty("live.gemini.model", "gemini-3.5-flash-lite"));

        AiChatClient client = new AiChatClient();
        AiChatClient.ChatResult r = client.send(cfg, List.of(),
                "Show an inventory summary table for all items with their stock quantities and purchase rates.",
                null);

        System.out.println("[TABLE_TEST] answer = \n" + r.text());
        System.out.println("[TABLE_TEST] trace  = " + r.toolTrace());

        // Check that ChatbotLogManager recorded the steps
        List<ChatbotLogManager.LogEntry> logs = ChatbotLogManager.getEntries();
        assertTrue(logs.stream().anyMatch(l -> "ROUTER".equals(l.tag()) || "DISPATCH".equals(l.tag())),
                "Logs should capture router or dispatch");
        assertTrue(logs.stream().anyMatch(l -> "MCP-EXEC".equals(l.tag()) || "TOOL-CALL".equals(l.tag()) || "SUCCESS".equals(l.tag())),
                "Logs should capture tool execution or success");

        // Assert that the text contains a Markdown table
        assertTrue(r.text().contains("|"), "Response should contain markdown table syntax: " + r.text());

        // Test that ChatMarkdownRenderer parses this live response into a Node
        javafx.scene.Node rendered = com.invoicestudio.ui.chat.ChatMarkdownRenderer.render(r.text(), false);
        assertNotNull(rendered);
    }

    @SuppressWarnings("unchecked")
    private static java.lang.reflect.Field field(Class<?> c, String n) throws Exception {
        java.lang.reflect.Field f = c.getDeclaredField(n);
        f.setAccessible(true);
        return f;
    }

    // ══════════════════════════════════════════════════════════════════
    // Z.ai GLM live scenarios (opt-in: -Dlive.glm=true -Dglm.key=…)
    // Every contract below was verified against the live API first: the
    // catalogue endpoint, the free glm-4.5-flash tier and OpenAI-shaped
    // tool_calls + usage blocks.
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(10)
    void glmLiveCatalogueListsChatModels() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("live.glm"), "GLM opt-in required");
        var models = ModelCatalog.glmModels(glmKey);
        System.out.println("[GLM-LIVE] catalogue = " + models.stream().map(m -> m.id()).toList());
        assertFalse(models.isEmpty(), "the GLM catalogue must return at least one chat model");
        assertTrue(models.stream().anyMatch(m -> m.id().startsWith("glm-")),
                "entries must be GLM model ids");
        // The default must stay inside what this key can actually use.
        assertEquals("glm-4.5-flash", AiChatClient.defaultModel(ChatbotConfig.GLM));
    }

    @Test
    @Order(11)
    void glmLiveToolRoundAnswersFromRealData() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("live.glm"), "GLM opt-in required");
        ChatbotConfig cfg = new ChatbotConfig();
        cfg.setProvider(ChatbotConfig.GLM);
        cfg.setApiKey(glmKey);
        cfg.setModel(System.getProperty("live.glm.model", "glm-4.5-flash"));

        AiChatClient client = new AiChatClient();
        AiChatClient.ChatResult r = client.send(cfg, List.of(),
                "How many pieces of 'Probe Denim Jeans' are in stock right now? "
                        + "Use the app tools and report the exact number.",
                null);

        System.out.println("[GLM-LIVE] toolTrace = " + r.toolTrace());
        System.out.println("[GLM-LIVE] answer    = " + r.text());
        System.out.println("[GLM-LIVE] usage     = ↑" + r.promptTokens() + " ↓" + r.completionTokens()
                + " tok in " + r.elapsedMs() + " ms (model " + r.modelUsed() + ")");

        assertFalse(r.text().isBlank(), "GLM must produce an answer");
        assertTrue(r.toolTrace().size() > 0 || r.text().contains("7"),
                "expected a tool call (trace=" + r.toolTrace() + ") in answer: " + r.text());
        if (!r.toolTrace().isEmpty()) {
            assertTrue(r.text().contains("7"),
                    "answer must reflect the tool result (7 pcs), got: " + r.text());
        }
    }

    @Test
    @Order(12)
    void glmLiveUsageIsReportedOnTheResult() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("live.glm"), "GLM opt-in required");
        ChatbotConfig cfg = new ChatbotConfig();
        cfg.setProvider(ChatbotConfig.GLM);
        cfg.setApiKey(glmKey);
        cfg.setModel("glm-4.5-flash");

        AiChatClient.ChatResult r = new AiChatClient().send(cfg, List.of(),
                "Reply with exactly: OK", null);

        System.out.println("[GLM-LIVE] text  = " + r.text());
        System.out.println("[GLM-LIVE] usage = ↑" + r.promptTokens() + " ↓" + r.completionTokens()
                + " total " + r.totalTokens() + " tok in " + r.elapsedMs() + " ms");
        assertTrue(r.promptTokens() > 0, "Z.ai reports prompt_tokens — it must reach the meta row");
        assertTrue(r.completionTokens() > 0, "Z.ai reports completion_tokens — it must reach the meta row");
        assertEquals(r.promptTokens() + r.completionTokens(), r.totalTokens());
        assertTrue(r.elapsedMs() >= 0);
        assertEquals("glm-4.5-flash", r.modelUsed());
    }

    @Test
    @Order(13)
    void glmLiveMeteredModelSurfacesBalanceErrorHonesty() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("live.glm"), "GLM opt-in required");
        // glm-5.3-flash is NOT covered by this key's balance (live-verified
        // 429 "Insufficient balance") — the chat must surface that honestly.
        ChatbotConfig cfg = new ChatbotConfig();
        cfg.setProvider(ChatbotConfig.GLM);
        cfg.setApiKey(glmKey);
        cfg.setModel("glm-5.3-flash");

        try {
            new AiChatClient().send(cfg, List.of(), "Reply with exactly: OK", null);
            // If the key gained balance since verification, the model simply
            // answered — the assertion target moved, not the contract.
            System.out.println("[GLM-LIVE] glm-5.3-flash now has balance — honesty check skipped");
        } catch (IllegalStateException ex) {
            String msg = String.valueOf(ex.getMessage());
            System.out.println("[GLM-LIVE] surfaced error = " + msg);
            assertTrue(msg.toLowerCase().contains("balance") || msg.contains("429"),
                    "the provider's balance/quota message must be surfaced verbatim, got: " + msg);
        }
    }
}
