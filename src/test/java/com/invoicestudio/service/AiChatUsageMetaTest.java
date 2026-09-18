package com.invoicestudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.invoicestudio.mcp.McpConfig;
import com.invoicestudio.mcp.McpServer;
import com.invoicestudio.mcp.McpToolRegistry;
import com.invoicestudio.model.UserSession;
import com.invoicestudio.service.AuthSessionManager;
import com.invoicestudio.ui.DataManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The per-message token meter (round 3): usage blocks reported by the provider
 * must be parsed, SUMMED across every request of a send (router + tool rounds),
 * and surfaced on {@link AiChatClient.ChatResult} — the data that feeds the
 * meta row beside the copy icon, the TOKENS log lines and the coin counter.
 * Runs the REAL send loop against a faithful local Gemini simulator; only the
 * provider is scripted.
 */
class AiChatUsageMetaTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static HttpServer stub;
    private static DataManager dm;

    private static final Deque<String> SCRIPT = new ArrayDeque<>();

    @BeforeAll
    static void startStubMcpAndData() throws Exception {
        IOException last = null;
        for (int port : new int[]{17811, 18335, 19147}) {
            McpConfig mc = new McpConfig();
            mc.setPort(port);
            mc.setRequireToken(false);
            if (McpServer.start(mc) == null) break;
        }
        if (!McpServer.isRunning()) throw new IllegalStateException("MCP server failed to start");

        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", AiChatUsageMetaTest::handle);
        stub.start();

        var tmpDb = Files.createTempFile("usage-meta", ".db");
        Files.deleteIfExists(tmpDb);
        com.invoicestudio.db.DatabaseManager.initCustom("jdbc:sqlite:" + tmpDb.toAbsolutePath());
        AuthSessionManager.setActiveSession(new UserSession(
                "uid_usage", "usage@test.in", "Usage Traders",
                "tok", "ref", System.currentTimeMillis() + 3600_000L, true));
        dm = DataManager.init(com.invoicestudio.db.DatabaseManager.getInstance());
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (stub != null) stub.stop(0);
        McpServer.shutdown();
        AuthSessionManager.clear();
        var r1 = com.invoicestudio.db.DatabaseManager.class.getDeclaredField("instance");
        r1.setAccessible(true); r1.set(null, null);
        var r2 = DataManager.class.getDeclaredField("instance");
        r2.setAccessible(true); r2.set(null, null);
    }

    private static void handle(HttpExchange ex) throws IOException {
        byte[] body = ex.getRequestBody().readAllBytes();
        String next = SCRIPT.poll();
        if (next == null) next = "{\"error\":{\"code\":500,\"message\":\"stub script empty\"}}";
        byte[] out = next.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, out.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(out);
        }
    }

    /** Gemini text answer WITH a usageMetadata block. */
    private static String text(String t, long promptTok, long candTok) {
        ObjectNode n = M.createObjectNode();
        ObjectNode cand = M.createObjectNode().set("content", M.createObjectNode()
                .set("parts", M.createArrayNode().add(M.createObjectNode().put("text", t))));
        n.set("candidates", M.createArrayNode().add(cand));
        ObjectNode usage = M.createObjectNode();
        usage.put("promptTokenCount", promptTok);
        usage.put("candidatesTokenCount", candTok);
        usage.put("totalTokenCount", promptTok + candTok);
        n.set("usageMetadata", usage);
        return n.toString();
    }

    /** Gemini functionCall answer WITH a usageMetadata block. */
    private static String call(String name, long promptTok, long candTok) {
        ObjectNode n = M.createObjectNode();
        ObjectNode fc = M.createObjectNode().put("name", name);
        fc.set("args", M.createObjectNode());
        n.set("candidates", M.createArrayNode().add(M.createObjectNode()
                .set("content", M.createObjectNode()
                        .set("parts", M.createArrayNode().add(M.createObjectNode().set("functionCall", fc))))));
        ObjectNode usage = M.createObjectNode();
        usage.put("promptTokenCount", promptTok);
        usage.put("candidatesTokenCount", candTok);
        n.set("usageMetadata", usage);
        return n.toString();
    }

    private static ChatbotConfig cfg() {
        ChatbotConfig c = new ChatbotConfig();
        c.setProvider(ChatbotConfig.GEMINI);
        c.setApiKey("stub-key");
        c.setEndpoint("http://127.0.0.1:" + stub.getAddress().getPort() + "/");
        c.setSmartRouting(false); // script exactly the requests of each scenario
        return c;
    }

    @Test
    void singleRequestUsageIsReportedVerbatim() throws Exception {
        // NOTE: not a greeting — pure smalltalk is answered LOCALLY now
        // (zero requests, no usage block), so the verbatim-usage contract is
        // exercised with a conversational-but-not-smalltalk message.
        SCRIPT.add(text("Hello! How can I help?", 100, 20));
        AiChatClient.ChatResult r = new AiChatClient().send(cfg(), List.of(),
                "tell me about yourself briefly", null);

        assertEquals("Hello! How can I help?", r.text());
        assertEquals(100, r.promptTokens(), "prompt tokens must come from the provider usage block");
        assertEquals(20, r.completionTokens());
        assertEquals(120, r.totalTokens());
        assertTrue(r.elapsedMs() >= 0, "wall time must be recorded");
        assertFalse(r.modelUsed().isBlank(), "the model that answered must be reported");
    }

    @Test
    void toolLoopUsageIsTheSumOfEveryRequest() throws Exception {
        dm.suppliers().saveSupplier(new com.invoicestudio.model.Supplier(
                "sup_usage_1", "Usage Probe Supplier"));

        // Round 1: model asks for a tool (usage A). Round 2: final text (usage B).
        SCRIPT.add(call("list_suppliers", 500, 30));
        SCRIPT.add(text("| Supplier | Balance |\n|---|---|\n| Usage Probe Supplier | 0 |", 700, 50));

        AiChatClient.ChatResult r = new AiChatClient().send(cfg(), List.of(),
                "list my suppliers", null);

        assertEquals(500 + 700, r.promptTokens(), "prompt tokens must SUM across tool rounds");
        assertEquals(30 + 50, r.completionTokens(), "completion tokens must SUM across tool rounds");
        assertEquals(1280, r.totalTokens());
        assertTrue(r.toolTrace().stream().anyMatch(t -> t.startsWith("list_suppliers")));
        assertTrue(r.elapsedMs() >= 0);

        // The TOKENS trace must have been emitted for the logs / coin counter.
        boolean tokenTrace = ChatbotLogManager.getEntries().stream()
                .anyMatch(e -> "TOKENS".equals(e.tag())
                        && e.message() != null && e.message().contains("total 1280"));
        assertTrue(tokenTrace, "a TOKENS log line with the send total must exist");
        // And one HTTP trace line per real request (2 rounds → at least 2).
        long httpLines = ChatbotLogManager.getEntries().stream()
                .filter(e -> "HTTP".equals(e.tag())).count();
        assertTrue(httpLines >= 2, "every provider request must leave one HTTP trace line");
    }

    @Test
    void providersWithoutUsageProduceNoFakeNumbers() {
        AiChatClient.ChatResult compat = new AiChatClient.ChatResult("text", List.of("tool"));
        assertEquals(-1, compat.promptTokens(), "compat constructor means 'no usage data'");
        assertEquals(-1, compat.completionTokens());
        assertEquals(-1, compat.totalTokens());
        assertEquals(-1, compat.elapsedMs());
    }

    @Test
    void glmCatalogueParseFiltersNonChatEntries() throws Exception {
        var one = M.readTree("{\"id\":\"glm-5.3-flash\",\"object\":\"model\",\"owned_by\":\"z-ai\"}");
        var info = ModelCatalog.parseGlm(one);
        assertNotNull(info);
        assertEquals("glm-5.3-flash", info.id());
        assertEquals("glm-5.3-flash", info.displayName(), "GLM entries carry no display name — id doubles");

        assertNull(ModelCatalog.parseGlm(M.readTree("{\"id\":\"\"}")), "blank id rejected");
        assertNull(ModelCatalog.parseGlm(M.readTree("{\"id\":\"glm-4-embedding\"}")),
                "embedding models must not appear in the chat picker");
        assertNull(ModelCatalog.parseGlm(M.readTree("{\"id\":\"cogvideox-video\"}")),
                "video models must not appear in the chat picker");
        assertFalse(ModelCatalog.hasLiveCatalogue(ChatbotConfig.OPENAI),
                "providers without a catalogue endpoint must say so");
        assertTrue(ModelCatalog.hasLiveCatalogue(ChatbotConfig.GLM));
        assertTrue(ModelCatalog.hasLiveCatalogue(ChatbotConfig.GEMINI));
    }
}
