package com.invoicestudio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.invoicestudio.mcp.McpAuditLog;
import com.invoicestudio.mcp.McpConfig;
import com.invoicestudio.mcp.McpServer;
import com.invoicestudio.mcp.McpToolRegistry;
import com.invoicestudio.mcp.PendingOperations;
import com.invoicestudio.model.UserSession;
import com.invoicestudio.service.AuthSessionManager;
import com.invoicestudio.ui.DataManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The user-perspective QA checklist from Knowledge Hub chapter
 * "AI Chatbot / 09. Bug Playbook & Token-Light Speed", executed END TO END
 * against a faithful local Gemini simulator (real REST shape: system_instruction,
 * contents[].parts[], functionCall / functionResponse, tools[].functionDeclarations).
 *
 * <p>This is the network-free stand-in for {@link AiChatLiveMcpTest}: it runs the
 * REAL {@link AiChatClient} send loop, the REAL router, the REAL MCP tool
 * executions and the REAL confirmation model — only the provider is scripted.
 * Every assertion below maps to a bug a user actually reported.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiChatUserJourneyStubTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static HttpServer stub;
    private static DataManager dm;

    /** Scripted responses, popped per request. Each test sets its own script. */
    private static final Deque<String> SCRIPT = new ArrayDeque<>();
    /** Every request body the assistant sent, in order. */
    private static final List<JsonNode> CAPTURED = new CopyOnWriteArrayList<>();

    private static int baseline() {
        return CAPTURED.size();
    }

    private static List<JsonNode> capturedSince(int base) {
        return new ArrayList<>(CAPTURED.subList(base, CAPTURED.size()));
    }

    @BeforeAll
    static void startStubMcpAndData() throws Exception {
        IOException last = null;
        for (int port : new int[]{17803, 18327, 19139, 19713}) {
            McpConfig mc = new McpConfig();
            mc.setPort(port);
            mc.setRequireToken(false);
            if (McpServer.start(mc) == null) break;
        }
        if (!McpServer.isRunning()) throw new IllegalStateException("MCP server failed to start");

        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", AiChatUserJourneyStubTest::handle);
        stub.start();

        var tmpDb = Files.createTempFile("journey-stub", ".db");
        Files.deleteIfExists(tmpDb);
        com.invoicestudio.db.DatabaseManager.initCustom("jdbc:sqlite:" + tmpDb.toAbsolutePath());
        AuthSessionManager.setActiveSession(new UserSession(
                "uid_journey", "journey@test.in", "Journey Traders",
                "tok", "ref", System.currentTimeMillis() + 3600_000L, true));
        dm = DataManager.init(com.invoicestudio.db.DatabaseManager.getInstance());
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (stub != null) stub.stop(0);
        McpServer.shutdown();
        PendingOperations.clearAll();
        AuthSessionManager.clear();
        var r1 = com.invoicestudio.db.DatabaseManager.class.getDeclaredField("instance");
        r1.setAccessible(true); r1.set(null, null);
        var r2 = DataManager.class.getDeclaredField("instance");
        r2.setAccessible(true); r2.set(null, null);
    }

    private static void handle(HttpExchange ex) throws IOException {
        byte[] body = ex.getRequestBody().readAllBytes();
        try {
            CAPTURED.add(M.readTree(body));
        } catch (Exception ignore) {
            CAPTURED.add(M.createObjectNode());
        }
        String next = SCRIPT.poll();
        if (next == null) next = "{\"error\":{\"code\":500,\"message\":\"stub script empty\"}}";
        byte[] out = next.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(200, out.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(out);
        }
    }

    // ── scripting helpers (faithful Gemini shapes) ──────────────────

    private static String text(String t) {
        ObjectNode n = M.createObjectNode();
        n.set("candidates", M.createArrayNode().add(M.createObjectNode()
                .set("content", M.createObjectNode()
                        .set("parts", M.createArrayNode().add(M.createObjectNode().put("text", t))))));
        return n.toString();
    }

    private static String call(String name, String argsJson) {
        ObjectNode n = M.createObjectNode();
        ObjectNode fc = M.createObjectNode();
        fc.put("name", name);
        fc.set("args", argsJson.isEmpty() ? M.createObjectNode() : (ObjectNode) silently(argsJson));
        n.set("candidates", M.createArrayNode().add(M.createObjectNode()
                .set("content", M.createObjectNode()
                        .set("parts", M.createArrayNode().add(M.createObjectNode().set("functionCall", fc))))));
        return n.toString();
    }

    private static JsonNode silently(String json) {
        try {
            return M.readTree(json);
        } catch (Exception e) {
            return M.createObjectNode();
        }
    }

    private static ChatbotConfig cfg() {
        ChatbotConfig c = new ChatbotConfig();
        c.setProvider(ChatbotConfig.GEMINI);
        c.setApiKey("stub-key");
        c.setEndpoint("http://127.0.0.1:" + stub.getAddress().getPort() + "/");
        // Router OFF by default here: the router is a separate no-schema
        // request that would consume the first scripted response. The two
        // router-focused tests switch it on explicitly.
        c.setSmartRouting(false);
        return c;
    }

    // ── payload inspectors ───────────────────────────────────────────

    private static boolean hasTools(JsonNode req) {
        return req.has("tools") && req.path("tools").path(0).path("functionDeclarations").isArray();
    }

    private static int toolCount(JsonNode req) {
        return hasTools(req) ? req.path("tools").path(0).path("functionDeclarations").size() : 0;
    }

    private static String systemText(JsonNode req) {
        return req.path("system_instruction").path("parts").path(0).path("text").asText("");
    }

    /** True when any functionResponse result string in the payload contains needle. */
    private static boolean anyFunctionResponseContains(JsonNode req, String needle) {
        JsonNode contents = req.path("contents");
        if (!contents.isArray()) return false;
        for (JsonNode c : contents) {
            for (JsonNode p : c.path("parts")) {
                JsonNode fr = p.path("functionResponse");
                // geminiContents re-parses the result string into a REAL JSON
                // node — serialize it back before matching.
                if (fr.isObject() && fr.path("response").path("result").toString().contains(needle)) return true;
            }
        }
        return false;
    }

    private static boolean anyFunctionCallFor(JsonNode req, String tool) {
        JsonNode contents = req.path("contents");
        if (!contents.isArray()) return false;
        for (JsonNode c : contents) {
            for (JsonNode p : c.path("parts")) {
                if (tool.equals(p.path("functionCall").path("name").asText(""))) return true;
            }
        }
        return false;
    }

    private static AiChatClient.ChatResult send(String userText, List<AiChatClient.ChatTurn> history) throws Exception {
        return new AiChatClient().send(cfg(), history, userText, null);
    }

    // ══════════════════════════════════════════════════════════════════
    // The checklist (chapter 09, section 3)
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    void checklist1_greetingIsInstantZeroSchema() throws Exception {
        // Round-4 behavior: greetings are answered LOCALLY — zero provider
        // requests at all (the old schema-free dispatch still cost one flash
        // round trip; users felt it as "even a hi takes ages"). The stub must
        // stay EMPTY: any request here means the local shortcut regressed.
        ChatbotConfig c = cfg();
        c.setSmartRouting(true); // the shortcut works with routing on…
        AiChatClient.ChatResult r = new AiChatClient().send(c, List.of(), "hi!", null);

        assertTrue(r.toolTrace().isEmpty(), "greeting must not run tools");
        assertTrue(CAPTURED.isEmpty(), "greeting must be answered with ZERO provider requests");
        assertTrue(r.text().contains("InvoiceStudio"), "local reply introduces the assistant");
        assertEquals(-1, r.totalTokens(), "no tokens can be reported for a local reply");
        assertEquals("local", r.modelUsed());
        assertTrue(r.elapsedMs() < 250, "local reply must be instant, was " + r.elapsedMs() + "ms");

        // …and mid-chat too (this was the actual regression: history present).
        AiChatClient.ChatResult r2 = new AiChatClient().send(c, List.of(
                AiChatClient.ChatTurn.user("list my suppliers"),
                AiChatClient.ChatTurn.assistant("Here are your suppliers.")), "hello", null);
        assertTrue(r2.toolTrace().isEmpty());
        assertTrue(CAPTURED.isEmpty(), "mid-chat greeting must also be local (0 requests)");
        assertTrue(r2.text().contains("InvoiceStudio"));
    }

    @Test
    @Order(2)
    void checklist2_readQuestionPrunesSchemasAndAnswersFromLiveTool() throws Exception {
        dm.suppliers().saveSupplier(new com.invoicestudio.model.Supplier(
                "sup_probe_1", "Journey Probe Supplier"));

        ChatbotConfig c = cfg();
        c.setSmartRouting(true); // router decides which schemas to carry
        SCRIPT.add(text("ROUTE: list_suppliers"));
        SCRIPT.add(call("list_suppliers", "{}"));
        SCRIPT.add(text("| Supplier | Balance |\n|---|---|\n| Journey Probe Supplier | 0 |"));

        int before = baseline();
        AiChatClient.ChatResult r = new AiChatClient().send(c, List.of(),
                "list my suppliers with balances", null);

        List<JsonNode> reqs = capturedSince(before);
        assertEquals(3, reqs.size(), "router + tool round + final answer");
        assertFalse(hasTools(reqs.get(0)), "router request carries no schemas");
        assertEquals(1, toolCount(reqs.get(1)), "heavy pass carries ONLY the shortlisted tool");
        assertEquals("list_suppliers", reqs.get(1).path("tools").path(0).path("functionDeclarations")
                .path(0).path("name").asText());
        assertTrue(anyFunctionResponseContains(reqs.get(2), "Journey Probe Supplier"),
                "the model must receive the REAL tool result back");
        assertTrue(r.toolTrace().stream().anyMatch(t -> t.startsWith("list_suppliers")));
    }

    @Test
    @Order(3)
    void checklist3_deleteSupplierQueuesThenConfirmCompletes() throws Exception {
        // Seed two suppliers — the user's exact "created, delete the last one" flow.
        dm.suppliers().saveSupplier(new com.invoicestudio.model.Supplier(
                "sup_keep_1", "Keep Me Traders"));
        dm.suppliers().saveSupplier(new com.invoicestudio.model.Supplier(
                "sup_last_1", "Last One Traders"));
        assertNotNull(dm.suppliers().getSupplierById("sup_keep_1"));
        assertNotNull(dm.suppliers().getSupplierById("sup_last_1"));

        // ── Send 1: "delete the last supplier" → tool → requiresConfirmation ──
        SCRIPT.add(call("delete_supplier", "{\"id\":\"sup_last_1\"}"));
        SCRIPT.add(text("Queued: delete supplier Last One Traders. Approve in Settings → MCP Server, or reply yes."));

        int before1 = baseline();
        AiChatClient.ChatResult r1 = send("delete the supplier named Last One Traders", List.of());

        List<JsonNode> reqs1 = capturedSince(before1);
        assertTrue(r1.toolTrace().stream().anyMatch(t -> t.startsWith("delete_supplier")),
                "delete_supplier must be requested by the model");
        assertEquals(2, reqs1.size());
        assertTrue(anyFunctionResponseContains(reqs1.get(1), "requiresConfirmation"),
                "the queued-confirmation result must reach the model");
        assertTrue(anyFunctionResponseContains(reqs1.get(1), "sup_last_1"),
                "the confirmation payload must carry the operationId + summary");
        assertTrue(dm.suppliers().getSupplierById("sup_last_1") != null,
                "nothing is deleted before approval");
        assertTrue(McpAuditLog.recent().stream().anyMatch(s -> s.contains("[CONFIRM-REQUESTED] delete_supplier")),
                "the audit trail must show the queued destructive op");

        // The queued op must carry the injected system prompt with the exact id.
        String opId = PendingOperations.pending().stream()
                .filter(o -> "delete_supplier".equals(o.getTool()))
                .map(PendingOperations.PendingOp::getId)
                .findFirst().orElseThrow();

        // ── Send 2: "yes" → confirm_operation → executed ──
        SCRIPT.add(call("confirm_operation",
                "{\"operationId\":\"" + opId + "\",\"approve\":true}"));
        SCRIPT.add(text("Deleted. The supplier no longer exists."));

        List<AiChatClient.ChatTurn> history = List.of(
                AiChatClient.ChatTurn.user("delete the supplier named Last One Traders"),
                AiChatClient.ChatTurn.assistant(r1.text()));
        int before2 = baseline();
        AiChatClient.ChatResult r2 = send("yes", history);

        String sys = systemText(capturedSince(before2).get(0));
        assertTrue(sys.contains("PENDING APPROVALS"),
                "the system prompt must spell out queued operations");
        assertTrue(sys.contains(opId),
                "the system prompt must carry the EXACT operationId (no guessing)");

        assertTrue(r2.toolTrace().stream().anyMatch(t -> t.contains("confirm_operation")),
                "the model must confirm via confirm_operation");
        assertNull(dm.suppliers().getSupplierById("sup_last_1"),
                "after approval the supplier is really gone");
        assertTrue(McpAuditLog.recent().stream().anyMatch(s -> s.contains("[CONFIRMED] delete_supplier")),
                "the audit trail must show the approved execution");
        assertTrue(McpAuditLog.recent().stream().anyMatch(s -> s.contains("[CHAT] confirm_operation")),
                "chatbot executions must be mirrored into the MCP audit");
        assertTrue(PendingOperations.pending().isEmpty(), "no ops left dangling");
    }

    @Test
    @Order(4)
    void checklist4_payBillRecordsRealReceipt() throws Exception {
        dm.buyers().saveBuyer(new com.invoicestudio.model.Buyer(
                "byr_pay_1", "Pay Journey Buyer", "", "", "", ""));
        com.invoicestudio.model.ItemRecord it = new com.invoicestudio.model.ItemRecord();
        it.setId("item_pay_1");
        it.setName("Pay Journey Item");
        it.setCategoryName("Gen");
        it.setRate(1000);
        it.setGst(0);
        dm.items().saveItem(it);

        AiChatClient.ChatResult seed = null;
        Map<String, Object> bill = null;
        try {
            bill = asMap(McpToolRegistry.call("create_bill", Map.of(
                    "buyerId", "byr_pay_1",
                    "items", List.of(Map.of("itemId", "item_pay_1", "qty", 2)))));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String billId = String.valueOf(bill.get("id"));
        double grand = ((Number) bill.get("grandTotal")).doubleValue();
        assertEquals(2000.0, grand, 0.01);

        SCRIPT.add(call("pay_bill", "{\"id\":\"" + billId + "\",\"mode\":\"UPI\"}"));
        SCRIPT.add(text("Receipt recorded — the invoice is now fully paid."));

        int before = baseline();
        AiChatClient.ChatResult r = send("record the full payment against bill " + billId + " via UPI", List.of());

        assertTrue(r.toolTrace().stream().anyMatch(t -> t.startsWith("pay_bill")),
                "the model must pick pay_bill (not update_bill_status)");
        assertTrue(anyFunctionResponseContains(capturedSince(before).get(1), "\"fullySettled\":true"),
                "the tool result must report full settlement");

        com.invoicestudio.model.Bill b = dm.bills().getBillById(billId);
        assertEquals(com.invoicestudio.model.BillStatus.PAID, b.getStatus(),
                "the invoice must flip to PAID");
        assertEquals(1, b.getPayments().size(), "a real payment record must exist");
        assertEquals(2000.0, b.getPayments().get(0).getAmount(), 0.01);
        assertNotNull(b.getPaidAt());
    }

    @Test
    @Order(5)
    void checklist5_knowledgeAppendViaAssistantWithConfirmation() throws Exception {
        // Isolate the knowledge library: the assistant will APPEND for real.
        var tmp = Files.createTempFile("journey-knowledge", ".json");
        Files.deleteIfExists(tmp);
        var isolated = com.invoicestudio.service.KnowledgeRepository.createCustom(tmp);
        var f = com.invoicestudio.service.KnowledgeRepository.class.getDeclaredField("instance");
        f.setAccessible(true);
        var savedInstance = f.get(null);
        f.set(null, isolated);
        try {
            SCRIPT.add(call("append_knowledge",
                    "{\"id\":\"art_ai_14_bug_playbook\",\"markdown\":\"\\n## Live verification\\nappend landed\"}"));
            SCRIPT.add(text("I can append that note. Reply yes to confirm."));

            int before1 = baseline();
            AiChatClient.ChatResult r1 = send(
                    "append a 'Live verification' note to the bug playbook chapter", List.of());
            assertTrue(r1.toolTrace().stream().anyMatch(t -> t.startsWith("append_knowledge")));
            assertTrue(anyFunctionResponseContains(capturedSince(before1).get(1), "requiresConfirmation"));
            assertFalse(isolated.getArticleById("art_ai_14_bug_playbook").orElseThrow()
                    .markdown().contains("append landed"), "nothing written before approval");

            String opId = PendingOperations.pending().stream()
                    .filter(o -> "append_knowledge".equals(o.getTool()))
                    .map(PendingOperations.PendingOp::getId).findFirst().orElseThrow();
            SCRIPT.add(call("confirm_operation",
                    "{\"operationId\":\"" + opId + "\",\"approve\":true}"));
            SCRIPT.add(text("Done — the chapter now carries the live verification note."));

            AiChatClient.ChatResult r2 = send("yes", List.of(
                    AiChatClient.ChatTurn.user("append a 'Live verification' note to the bug playbook chapter"),
                    AiChatClient.ChatTurn.assistant(r1.text())));

            assertTrue(r2.toolTrace().stream().anyMatch(t -> t.contains("confirm_operation")));
            assertTrue(isolated.getArticleById("art_ai_14_bug_playbook").orElseThrow()
                    .markdown().contains("append landed"), "the append must execute after approval");
        } finally {
            f.set(null, savedInstance);
            Files.deleteIfExists(tmp);
        }
    }

    @Test
    @Order(6)
    void checklist6_mcpOffStillRepliesInstantlyWithGuidance() throws Exception {
        McpServer.shutdown(); // the checklist's final state: server OFF
        try {
            SCRIPT.add(text("The MCP server is off — please start MCP by going to Settings → MCP Server."));
            int before = baseline();
            AiChatClient.ChatResult r = send("list my suppliers", List.of());

            List<JsonNode> reqs = capturedSince(before);
            assertEquals(1, reqs.size(), "MCP-off must be a single fast request");
            assertFalse(hasTools(reqs.get(0)), "MCP-off sends zero tool schemas");
            assertTrue(systemText(reqs.get(0)).contains("Settings → MCP Server"),
                    "the system prompt must carry the user guidance");
            assertTrue(r.toolTrace().isEmpty());
            assertTrue(r.text().contains("Settings → MCP Server"));
        } finally {
            // restore for any later test in the same JVM
            McpConfig mc = new McpConfig();
            mc.setPort(17803);
            mc.setRequireToken(false);
            McpServer.start(mc);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }
}
