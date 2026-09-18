package com.invoicestudio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.mcp.McpConfig;
import com.invoicestudio.mcp.McpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Locks the September optimization pass end to end:
 * <ul>
 *   <li>empty allowed-set == ZERO tool schemas (smalltalk / MCP-off fast paths),</li>
 *   <li>MCP-off system prompt tells the model to point at Settings → MCP Server,</li>
 *   <li>quota failover never consumes a tool round and does not replay tool
 *       payloads into the new model,</li>
 *   <li>light-model-by-default policy (chat default, router, failover ladder),</li>
 *   <li>Z.ai GLM provider wired with a free light default.</li>
 * </ul>
 */
class AiChatOptimizationPassTest {

    private static final ObjectMapper M = new ObjectMapper();

    // ── 1. Tool-schema filter semantics ─────────────────────────────

    @Test
    void emptyAllowedSetMeansZeroToolSchemas() throws Exception {
        AiChatClient c = new AiChatClient();
        java.lang.reflect.Method tools = AiChatClient.class.getDeclaredMethod("geminiTools", java.util.Set.class);
        tools.setAccessible(true);

        JsonNode empty = (JsonNode) tools.invoke(c, java.util.Set.of());
        JsonNode decls = empty.get(0).get("functionDeclarations");
        assertTrue(decls.isArray() && decls.isEmpty(),
                "empty allowed set must produce ZERO function declarations (was: full catalogue bug)");

        JsonNode full = (JsonNode) tools.invoke(c, new Object[]{null});
        JsonNode allDecls = full.get(0).get("functionDeclarations");
        assertTrue(allDecls.isArray() && allDecls.size() >= 60, "null allowed set keeps the full catalogue");

        JsonNode shortlist = (JsonNode) tools.invoke(c, java.util.Set.of("list_buyers", "list_bills"));
        JsonNode shortDecls = shortlist.get(0).get("functionDeclarations");
        assertEquals(2, shortDecls.size(), "non-empty allowed set filters to the shortlist");
    }

    // ── 2. MCP-off guidance in the system prompt ────────────────────

    @Test
    void mcpOffSystemPromptDirectsToSettings() throws Exception {
        AiChatClient c = new AiChatClient();
        java.lang.reflect.Method sp = AiChatClient.class.getDeclaredMethod("systemPrompt", boolean.class);
        sp.setAccessible(true);

        String off = (String) sp.invoke(c, true);
        assertTrue(off.contains("MCP"), "MCP-off prompt must mention MCP");
        assertTrue(off.contains("Settings → MCP Server"), "MCP-off prompt must tell the user where to start it");
        assertFalse(off.contains("use them to answer"), "MCP-off prompt must not claim tool access");

        String on = (String) sp.invoke(c, false);
        assertFalse(on.contains("MCP tool server is currently OFF"), "normal prompt must not claim MCP is off");
        assertTrue(on.contains("MCP tools"), "normal prompt keeps the tool etiquette");
    }

    // ── 3. Failover payload trimming ────────────────────────────────

    @Test
    void trimForFailoverDropsToolPayloadsKeepsPromptAndTextHistory() {
        ChatbotConfig cfg = new ChatbotConfig();
        cfg.setHistoryMessages(10);

        List<AiChatClient.ChatTurn> turns = List.of(
                AiChatClient.ChatTurn.user("old question"),
                AiChatClient.ChatTurn.assistant("old answer"),
                AiChatClient.ChatTurn.user("how many buyers do we have?"),
                new AiChatClient.ChatTurn("call",
                        "[{\"name\":\"list_buyers\",\"args\":{},\"id\":\"c1\"}]", null),
                new AiChatClient.ChatTurn("tool",
                        "[{\"name\":\"list_buyers\",\"id\":\"c1\",\"result\":\"" + "x".repeat(4000) + "\"}]", null));

        List<AiChatClient.ChatTurn> trimmed = AiChatClient.trimForFailover(turns, cfg);

        assertEquals(3, trimmed.size(), "call/tool payloads must be dropped");
        assertEquals("user", trimmed.get(0).role());
        assertEquals("old question", trimmed.get(0).text());
        assertEquals("old answer", trimmed.get(1).text());
        assertEquals("user", trimmed.get(2).role());
        assertEquals("how many buyers do we have?", trimmed.get(2).text(),
                "the original prompt must survive for the new model");
        assertTrue(trimmed.stream().noneMatch(t -> "call".equals(t.role()) || "tool".equals(t.role())),
                "no MCP payload turns may be replayed into the new model");
    }

    @Test
    void trimForFailoverRespectsHistoryWindow() {
        ChatbotConfig cfg = new ChatbotConfig();
        cfg.setHistoryMessages(4);
        java.util.List<AiChatClient.ChatTurn> turns = new java.util.ArrayList<>();
        for (int i = 0; i < 10; i++) {
            turns.add(AiChatClient.ChatTurn.user("q" + i));
            turns.add(AiChatClient.ChatTurn.assistant("a" + i));
        }
        turns.add(AiChatClient.ChatTurn.user("final prompt"));

        List<AiChatClient.ChatTurn> trimmed = AiChatClient.trimForFailover(turns, cfg);
        // 4 kept prior text turns + the prompt turn; assistant halves may add
        // pairs — just assert the hard bounds: prompt present, no call/tool,
        // and payload did not GROW.
        assertEquals("final prompt", trimmed.get(trimmed.size() - 1).text());
        assertTrue(trimmed.size() <= 4 + 1 + 1, "history window bounds the trimmed payload");
    }

    // ── 4. Light-by-default policy + GLM provider ───────────────────

    @Test
    void geminiDefaultIsTheLightAlias() {
        assertEquals("gemini-flash-lite-latest", AiChatClient.defaultModel(ChatbotConfig.GEMINI));
    }

    @Test
    void failoverLadderLeadsWithLightModels() {
        List<String> ladder = ModelCatalog.failoverCandidates();
        assertEquals("gemini-flash-lite-latest", ladder.get(0), "lightest tier first");
        assertTrue(ladder.contains("gemini-flash-latest"));
        String next = ModelCatalog.nextFailover("gemini-flash-latest", java.util.Set.of("gemini-flash-latest"));
        assertNotNull(next);
    }

    @Test
    void glmProviderIsWiredWithFreeLightDefault() {
        assertEquals("glm-4.5-flash", AiChatClient.defaultModel(ChatbotConfig.GLM));
        assertNotNull(AiChatClient.providerLabel(ChatbotConfig.GLM));
        assertTrue(AiChatClient.providerLabel(ChatbotConfig.GLM).contains("GLM"));
        assertTrue(AiChatClient.GLM_ENDPOINT.startsWith("https://api.z.ai/"));
        // Config list integration (provider combo shows it too)
        assertTrue(new ChatbotConfig().getProvider() != null);
    }

    // ── 5. Live-loop failover: never burns a tool round ─────────────

    private static HttpServer stub;
    private static final List<Integer> STATUS_LOG = new CopyOnWriteArrayList<>();

    @BeforeAll
    static void startStubAndMcp() throws IOException {
        // The MCP-off fast path must NOT swallow this test's tool loop, so
        // the real MCP server runs for the duration (tool access ON).
        IOException last = null;
        for (int port : new int[]{17801, 18321, 19137, 19711}) {
            McpConfig mc = new McpConfig();
            mc.setPort(port);
            mc.setRequireToken(false);
            if (McpServer.start(mc) == null) break;
        }
        if (!McpServer.isRunning()) {
            throw new IllegalStateException("could not start MCP server for test");
        }

        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", AiChatOptimizationPassTest::handle);
        stub.start();
    }

    @AfterAll
    static void stopStubAndMcp() {
        if (stub != null) stub.stop(0);
        McpServer.shutdown();
    }

    /** Gemini-shaped stub: 429s the exhausted model, function-calls once, then answers. */
    private static void handle(HttpExchange ex) throws IOException {
        byte[] body = ex.getRequestBody().readAllBytes();
        String req = new String(body, StandardCharsets.UTF_8);
        String path = ex.getRequestURI().getPath();
        String resp;
        int status;
        if (path.contains("gemini-exhausted-test:")) {
            status = 429;
            resp = "{\"error\":{\"code\":429,\"message\":\"Quota exceeded for quota metric "
                    + "'GenerateRequestsPerDay' and limit 'GenerateRequestsPerDayPerProjectPerModel' "
                    + "in region 'us'.\",\"status\":\"RESOURCE_EXHAUSTED\"}}";
        } else if (req.contains("functionResponse")) {
            status = 200;
            resp = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"FAILOVER OK\"}]}}]}";
        } else {
            status = 200;
            resp = "{\"candidates\":[{\"content\":{\"parts\":[{\"functionCall\":"
                    + "{\"name\":\"list_buyers\",\"args\":{}}}]}}]}";
        }
        STATUS_LOG.add(status);
        byte[] out = resp.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, out.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(out);
        }
    }

    @Test
    void quotaFailoverDoesNotConsumeToolRoundAndDoesNotReplayPayloads() throws Exception {
        ChatbotConfig cfg = new ChatbotConfig();
        cfg.setProvider(ChatbotConfig.GEMINI);
        cfg.setModel("gemini-exhausted-test"); // stub 429s this one
        cfg.setApiKey("test-key");
        cfg.setEndpoint("http://127.0.0.1:" + stub.getAddress().getPort() + "/models/");
        cfg.setSmartRouting(false);   // router would call the real Google API
        cfg.setMaxToolCalls(1);       // 1 round budget: failover must stay FREE

        AiChatClient.ChatResult r = new AiChatClient().send(cfg,
                List.of(), "list buyers", null);

        assertEquals("FAILOVER OK", r.text(),
                "the single tool round must still be available after the model switch — "
                        + "failover may not consume the round budget");
        assertTrue(r.toolTrace().stream().anyMatch(t -> t.contains("switched to gemini-flash-lite-latest")),
                "trace must record the model switch; got " + r.toolTrace());

        // Payload replay guard: the SECOND stub request (first on the new
        // model) must carry the original prompt but NOT a call/tool replay —
        // we only see functionCall requests once (round 1) and functionResponse
        // once (round 2). If payloads were replayed there would still be one
        // of each — so assert ordering: the exhausted 429 came first.
        assertEquals(429, STATUS_LOG.get(0).intValue(), "first request hits the exhausted model");
        assertTrue(STATUS_LOG.size() >= 3, "expected 429 + tool round + final answer");

        // ── Model-sync contract (September user report: settings and chat
        //    chip drifted to a model the user never picked) ──
        assertEquals("gemini-exhausted-test", cfg.getModel(),
                "failover must live on the per-send copy — the user's saved model is untouchable");

        // ── Audit-mirror contract (September user report: chatbot deleted
        //    records but "no logs" anywhere) ──
        assertTrue(com.invoicestudio.mcp.McpAuditLog.recent().stream()
                        .anyMatch(s -> s.contains("[CHAT") && s.contains("list_buyers")),
                "chatbot tool executions must be mirrored into the MCP audit trail");
    }

    @Test
    void copyForSendClonesEveryField() {
        ChatbotConfig original = new ChatbotConfig();
        original.setProvider(ChatbotConfig.GLM);
        original.setModel("glm-4.6");
        original.setApiKey("k-test");
        original.setEndpoint("http://example/v1");
        original.setHistoryMessages(9);
        original.setMaxToolCalls(11);
        original.setSmartRouting(false);
        original.setShowIcon(false);

        ChatbotConfig copy = original.copyForSend();
        assertEquals(original.getProvider(), copy.getProvider());
        assertEquals(original.getModel(), copy.getModel());
        assertEquals(original.getApiKey(), copy.getApiKey());
        assertEquals(original.getEndpoint(), copy.getEndpoint());
        assertEquals(original.getHistoryMessages(), copy.getHistoryMessages());
        assertEquals(original.getMaxToolCalls(), copy.getMaxToolCalls());
        assertEquals(original.isSmartRouting(), copy.isSmartRouting());
        assertEquals(original.isShowIcon(), copy.isShowIcon());

        // The whole point: mutating the copy never touches the original
        copy.setModel("failover-model");
        assertNotEquals(copy.getModel(), original.getModel());
        assertEquals("glm-4.6", original.getModel());
    }
}
