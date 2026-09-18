package com.invoicestudio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.invoicestudio.mcp.McpConfig;
import com.invoicestudio.mcp.McpServer;
import com.invoicestudio.mcp.McpToolRegistry;
import com.invoicestudio.mcp.PendingOperations;
import com.invoicestudio.model.UserSession;
import com.invoicestudio.service.AuthSessionManager;
import com.invoicestudio.ui.DataManager;
import com.invoicestudio.ui.ShortcutManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The FULL MCP tool surface driven END-TO-END through the REAL
 * {@link AiChatClient} loop — the "act as the AI" harness the user asked for:
 * a local Gemini-shaped stub plays the model (scripted functionCall / ROUTE /
 * text decisions), while everything between the user's message and the tool
 * result — routing, payload building, provider round trips, schema
 * shortlisting, tool execution, confirmation gating, result replay — is
 * production code.
 *
 * <p>Coverage goals (user brief: "not just 1 or 2 but ALL mcp tools", plus
 * history-context scenarios and response-time findings):</p>
 * <ol>
 *   <li>every tool in {@link McpToolRegistry} executed through the chat loop,
 *       including the full requiresConfirmation → confirm_operation flow for
 *       gated tools;</li>
 *   <li>router shortlist + one-shot escalation to the full catalogue;</li>
 *   <li>the tool-round safety stop;</li>
 *   <li>multi-tool rounds (2 calls, 2 results, one round);</li>
 *   <li>history window bounding in real payloads;</li>
 *   <li>greeting-vs-confirmation precedence (the "hi" must never eat a
 *       pending confirmation);</li>
 *   <li>latency probes proving the client layer itself adds ~nothing —
 *       the wall time lives in the provider round trips (free-tier queues).</li>
 * </ol>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiChatFullSurfaceStubTest {

    private static final ObjectMapper M = new ObjectMapper();
    private static HttpServer stub;
    private static DataManager dm;
    private static java.nio.file.Path knowledgeTmp;

    /** Scripted responses, popped per request. Each test sets its own script. */
    private static final Deque<String> SCRIPT = new ArrayDeque<>();
    /** Every request body the assistant sent, in order. */
    private static final List<JsonNode> CAPTURED = new CopyOnWriteArrayList<>();

    // Seeded entity ids shared across the checklist
    private static String buyerId, supplierId, itemId, categoryId, transportId, templateId,
            labelTplId, variableKey, billId, bill2Id, bill3Id, purchaseId, purchase2Id,
            expenseId, expense2Id, expenseAccountId, expenseAccount2Id, knowledgeId,
            knowledge2Id, dupTemplateId, throwawayCatId, throwawayVarKey, throwawayTransportId;

    private static int baseline() { return CAPTURED.size(); }

    @BeforeAll
    static void startStubMcpAndData() throws Exception {
        IOException last = null;
        for (int port : new int[]{17903, 18347, 19159, 19733}) {
            McpConfig mc = new McpConfig();
            mc.setPort(port);
            mc.setRequireToken(false);
            if (McpServer.start(mc) == null) break;
        }
        if (!McpServer.isRunning()) throw new IllegalStateException("MCP server failed to start");

        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", AiChatFullSurfaceStubTest::handle);
        stub.start();

        var tmpDb = Files.createTempFile("surface-stub", ".db");
        Files.deleteIfExists(tmpDb);
        com.invoicestudio.db.DatabaseManager.initCustom("jdbc:sqlite:" + tmpDb.toAbsolutePath());
        AuthSessionManager.setActiveSession(new UserSession(
                "uid_surface_ai", "surface-ai@test.in", "Surface AI Traders",
                "tok", "ref", System.currentTimeMillis() + 3600_000L, true));
        dm = DataManager.init(com.invoicestudio.db.DatabaseManager.getInstance());

        // Isolate the Knowledge library so create/update/append/delete tests
        // never touch the real library.
        knowledgeTmp = Files.createTempFile("surface-knowledge", ".json");
        Files.deleteIfExists(knowledgeTmp);
        var isolated = com.invoicestudio.service.KnowledgeRepository.createCustom(knowledgeTmp);
        var f = com.invoicestudio.service.KnowledgeRepository.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, isolated);

        // Headless: register a probe shortcut action for rebind/reset tests.
        ShortcutManager.register("test.probe", "Test", "Test Probe", "Ctrl+Alt+9", () -> {}, true);

        seedData();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (stub != null) stub.stop(0);
        McpServer.shutdown();
        PendingOperations.clearAll();
        AuthSessionManager.clear();
        var f = com.invoicestudio.service.KnowledgeRepository.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
        Files.deleteIfExists(knowledgeTmp);
        var r1 = com.invoicestudio.db.DatabaseManager.class.getDeclaredField("instance");
        r1.setAccessible(true); r1.set(null, null);
        var r2 = DataManager.class.getDeclaredField("instance");
        r2.setAccessible(true); r2.set(null, null);
    }

    @BeforeEach void resetScript() { SCRIPT.clear(); PendingOperations.clearAll(); }
    @AfterEach  void ensureDrained() { SCRIPT.clear(); PendingOperations.clearAll(); }

    // ── seeding (DAO direct + registry, mirroring the vetted suites) ──

    private static void seedData() throws Exception {
        dm.buyers().saveBuyer(new com.invoicestudio.model.Buyer(
                "byr_s1", "Surface Buyer One", "", "", "", ""));
        dm.buyers().saveBuyer(new com.invoicestudio.model.Buyer(
                "byr_s2", "Surface Buyer Two", "", "", "", ""));
        dm.suppliers().saveSupplier(new com.invoicestudio.model.Supplier(
                "sup_s1", "Surface Supplier One"));
        dm.suppliers().saveSupplier(new com.invoicestudio.model.Supplier(
                "sup_s2", "Surface Supplier Two"));
        // Throwaway rows whose WHOLE purpose is to be deleted through the
        // chat loop later in the checklist (registry deletes refuse missing
        // ids — fail-fast by design).
        dm.buyers().saveBuyer(new com.invoicestudio.model.Buyer(
                "byr_del1", "Surface Throwaway Buyer", "", "", "", ""));
        dm.suppliers().saveSupplier(new com.invoicestudio.model.Supplier(
                "sup_del1", "Surface Throwaway Supplier"));
        Map<?, ?> throwawayCat = asMap(McpToolRegistry.call("create_category",
                Map.of("name", "Throwaway Category")));
        throwawayCatId = String.valueOf(throwawayCat.get("id"));

        com.invoicestudio.model.ItemRecord it = new com.invoicestudio.model.ItemRecord();
        it.setId("item_s1"); it.setName("Surface Item One");
        it.setCategoryName("Gen"); it.setRate(1000); it.setGst(0);
        dm.items().saveItem(it);
        com.invoicestudio.model.ItemRecord it2 = new com.invoicestudio.model.ItemRecord();
        it2.setId("item_s2"); it2.setName("Surface Item Two");
        it2.setCategoryName("Gen"); it2.setRate(500); it2.setGst(0);
        dm.items().saveItem(it2);

        // Invoice template (vetted minimal design vocabulary)
        Map<String, Object> title = new LinkedHashMap<>();
        title.put("type", "TEXT"); title.put("name", "Title Band");
        title.put("x", 10); title.put("y", 8); title.put("w", 190); title.put("h", 12);
        title.put("text", "SURFACE INVOICE"); title.put("fontSize", 16);
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("name", "Surface Invoice Template");
        args.put("pageSize", "A4");
        args.put("elements", List.of(title));
        Map<?, ?> created = asMap(McpToolRegistry.call("create_template", args));
        templateId = String.valueOf(created.get("id"));

        // Label-mode template for the print/label-state tools
        com.invoicestudio.model.Template lt = new com.invoicestudio.model.Template();
        lt.setId("tpl_label_surface");
        lt.setName("Surface Label Template");
        lt.setMode("label");
        lt.labelOrNew();
        com.invoicestudio.model.TemplateElement code = new com.invoicestudio.model.TemplateElement();
        code.setId("el_s_code");
        code.setType(com.invoicestudio.model.ElementType.BARCODE);
        code.setName("Barcode");
        lt.getElements().add(code);
        dm.templates().saveTemplate(lt);
        labelTplId = lt.getId();
    }

    // ── stub plumbing (faithful Gemini shapes) ──────────────────────

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

    private static String text(String t) {
        ObjectNode n = M.createObjectNode();
        n.set("candidates", M.createArrayNode().add(M.createObjectNode()
                .set("content", M.createObjectNode()
                        .set("parts", M.createArrayNode().add(M.createObjectNode().put("text", t))))));
        return n.toString();
    }

    private static String call(String name, String argsJson) {
        return callMulti(List.of(Map.of(name, argsJson)));
    }

    /** One response carrying SEVERAL functionCall parts (multi-tool round). */
    private static String callMulti(List<Map<String, String>> calls) {
        ObjectNode n = M.createObjectNode();
        var parts = M.createArrayNode();
        for (Map<String, String> c : calls) {
            for (Map.Entry<String, String> e : c.entrySet()) {
                ObjectNode part = parts.addObject();
                ObjectNode fc = M.createObjectNode();
                fc.put("name", e.getKey());
                JsonNode args = silently(e.getValue());
                fc.set("args", args);
                part.set("functionCall", fc);
            }
        }
        n.set("candidates", M.createArrayNode().add(M.createObjectNode()
                .set("content", M.createObjectNode().set("parts", parts))));
        return n.toString();
    }

    private static JsonNode silently(String json) {
        try { return M.readTree(json); } catch (Exception e) { return M.createObjectNode(); }
    }

    private static ChatbotConfig cfg() {
        ChatbotConfig c = new ChatbotConfig();
        c.setProvider(ChatbotConfig.GEMINI);
        c.setApiKey("stub-key");
        c.setEndpoint("http://127.0.0.1:" + stub.getAddress().getPort() + "/");
        c.setSmartRouting(false);
        return c;
    }

    /** The functionResponse result string the loop sent back for `tool`. */
    private static String extractToolResult(List<JsonNode> reqs, String tool) {
        for (JsonNode req : reqs) {
            JsonNode contents = req.path("contents");
            if (!contents.isArray()) continue;
            for (JsonNode c : contents) {
                for (JsonNode p : c.path("parts")) {
                    JsonNode fr = p.path("functionResponse");
                    if (fr.isObject() && tool.equals(fr.path("name").asText())) {
                        return fr.path("response").path("result").toString();
                    }
                }
            }
        }
        return "";
    }

    private static Map<?, ?> asMap(Object o) { return (Map<?, ?>) o; }

    private static Map<String, Object> resultMap(String json) {
        try {
            JsonNode n = M.readTree(json);
            Map<String, Object> out = new LinkedHashMap<>();
            n.properties().forEach(e -> out.put(e.getKey(), e.getValue()));
            return out;
        } catch (Exception e) {
            return Map.of();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // The "act as the AI" runner: script → send → verify, with the full
    // requiresConfirmation → confirm_operation flow for gated tools.
    // ══════════════════════════════════════════════════════════════════

    private record Outcome(AiChatClient.ChatResult chat, String resultJson, int requestCount) {}

    /**
     * True when the tool result is a REAL confirmation gate ({\@code
     * requiresConfirmation: true} JSON field). Deliberately NOT a substring
     * match — tool RESULTS like get_mcp_docs legitimately mention the phrase
     * inside their documentation text.
     */
    private static boolean isGated(String resultJson) {
        try {
            JsonNode n = M.readTree(resultJson);
            return n.isObject() && n.path("requiresConfirmation").asBoolean(false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Runs ONE tool through the real chat loop: the stub "AI" answers the
     * user's message with a functionCall for `tool`; the loop executes it on
     * the real registry; if the tool queued a confirmation, the harness
     * answers "yes" (exactly like a user) and the confirm_operation flow
     * completes the action. Asserts no provider error slipped through.
     */
    private static Outcome runTool(ChatbotConfig c, String tool, String argsJson) throws Exception {
        int before = baseline();
        SCRIPT.add(call(tool, argsJson));
        SCRIPT.add(text("Done — " + tool + " finished."));
        AiChatClient.ChatResult r = new AiChatClient().send(c, List.of(),
                "please run " + tool, null);
        List<JsonNode> reqs = new ArrayList<>(CAPTURED.subList(before, CAPTURED.size()));
        assertTrue(r.toolTrace().stream().anyMatch(t -> t.startsWith(tool)),
                tool + " must appear in the tool trace, got: " + r.toolTrace());
        String res = extractToolResult(reqs, tool);
        assertFalse(res.isEmpty(), tool + " result must reach the model");

        if (isGated(res)) {
            String opId = PendingOperations.pending().stream()
                    .filter(o -> tool.equals(o.getTool()))
                    .map(PendingOperations.PendingOp::getId)
                    .findFirst().orElseThrow(() -> new AssertionError(
                            tool + " queued a confirmation but no PendingOp exists"));
            SCRIPT.add(call("confirm_operation",
                    "{\"operationId\":\"" + opId + "\",\"approve\":true}"));
            SCRIPT.add(text("Approved — " + tool + " is done."));
            List<AiChatClient.ChatTurn> hist = List.of(
                    AiChatClient.ChatTurn.user("please run " + tool),
                    AiChatClient.ChatTurn.assistant(r.text()));
            int b2 = baseline();
            AiChatClient.ChatResult r2 = new AiChatClient().send(c, hist, "yes", null);
            List<JsonNode> reqs2 = new ArrayList<>(CAPTURED.subList(b2, CAPTURED.size()));
            String confirmed = extractToolResult(reqs2, "confirm_operation");
            assertFalse(confirmed.isEmpty(), "confirm_operation result must reach the model");
            assertTrue(r2.toolTrace().stream().anyMatch(t -> t.contains("confirm_operation")),
                    "approval must run confirm_operation");
            assertFalse(confirmed.trim().startsWith("{\"error\""),
                    tool + " confirmation failed: " + confirmed);
            return new Outcome(r2, confirmed, reqs.size() + reqs2.size());
        }
        assertFalse(res.trim().startsWith("{\"error\""),
                tool + " returned an error result: " + res);
        return new Outcome(r, res, reqs.size());
    }

    // ══════════════════════════════════════════════════════════════════
    // 1. The FULL tool surface — every registered tool through the loop
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    void everyRegisteredToolExecutesThroughTheChatLoop() throws Exception {
        ChatbotConfig c = cfg();
        Set<String> covered = new HashSet<>();
        var exportDir = Files.createTempDirectory("surface-export").toAbsolutePath();

        // Runs one tool, records coverage; lenient variants tolerate
        // environment-bound results (e.g. no physical label printer).
        class Helper {
            void run(String tool, String args) throws Exception {
                runTool(c, tool, args);
                covered.add(tool);
            }
            String runKeepId(String tool, String args) throws Exception {
                Outcome o = runTool(c, tool, args);
                covered.add(tool);
                Object id = resultMap(o.resultJson()).get("id");
                // Unwrap Jackson nodes properly — String.valueOf(TextNode)
                // would keep the surrounding QUOTES and poison the next
                // tool's args JSON.
                String idStr = id instanceof JsonNode n ? n.asText()
                        : (id == null ? "" : String.valueOf(id));
                assertFalse(idStr.isBlank(),
                        tool + " must return an id, got: " + o.resultJson());
                return idStr;
            }
            void runLenient(String tool, String args) throws Exception {
                int before = baseline();
                SCRIPT.add(call(tool, args));
                SCRIPT.add(text("Done — " + tool + " finished."));
                AiChatClient.ChatResult r = new AiChatClient().send(c, List.of(),
                        "please run " + tool, null);
                List<JsonNode> reqs = new ArrayList<>(CAPTURED.subList(before, CAPTURED.size()));
                assertTrue(r.toolTrace().stream().anyMatch(t -> t.startsWith(tool)),
                        tool + " must appear in the trace");
                String res = extractToolResult(reqs, tool);
                assertFalse(res.isEmpty(), tool + " must return SOMETHING through the loop");
                covered.add(tool);
            }
        }
        Helper h = new Helper();

        // ── Knowledge / docs (read) ─────────────────────────────────
        h.run("get_app_guide", "{}");
        h.run("get_mcp_docs", "{}");
        h.run("get_settings", "{}");
        h.run("whoami", "{}");
        h.run("server_status", "{}");
        h.run("audit_log", "{}");
        h.run("list_knowledge", "{}");

        // ── Directory: buyers ───────────────────────────────────────
        h.run("list_buyers", "{}");
        String mcpBuyerId = h.runKeepId("create_buyer",
                "{\"name\":\"Surface MCP Buyer\",\"phone\":\"9800000001\"}");
        h.run("update_buyer", "{\"id\":\"" + mcpBuyerId + "\",\"city\":\"Surat\"}");

        // ── Directory: suppliers ────────────────────────────────────
        h.run("list_suppliers", "{}");
        String mcpSupplierId = h.runKeepId("create_supplier", "{\"name\":\"Surface MCP Supplier\"}");
        h.run("update_supplier", "{\"id\":\"" + mcpSupplierId + "\",\"city\":\"Ahmedabad\"}");

        // ── Catalog: categories & items ─────────────────────────────
        h.run("list_categories", "{}");
        String catId = h.runKeepId("create_category", "{\"name\":\"Surface Category\"}");
        categoryId = catId;
        h.run("list_items", "{}");
        String mcpItemId = h.runKeepId("create_item",
                "{\"name\":\"Surface MCP Item\",\"categoryName\":\"Surface Category\",\"rate\":250,\"gst\":5}");
        h.run("update_item", "{\"id\":\"" + mcpItemId + "\",\"rate\":300}");
        h.run("update_category", "{\"id\":\"" + throwawayCatId + "\",\"name\":\"Throwaway Category Renamed\"}");

        // ── Transports ──────────────────────────────────────────────
        h.run("list_transports", "{}");
        String trId = h.runKeepId("create_transport", "{\"name\":\"Surface Transport\"}");
        transportId = trId;
        h.run("update_transport", "{\"id\":\"" + trId + "\",\"phone\":\"9800000002\"}");
        String tr2Id = h.runKeepId("create_transport", "{\"name\":\"Throwaway Transport\"}");

        // ── Templates ───────────────────────────────────────────────
        h.run("list_templates", "{}");
        h.run("get_template_design_guide", "{}");
        h.run("get_template", "{\"id\":\"" + templateId + "\"}");
        h.run("render_template_preview", "{\"id\":\"" + templateId + "\",\"dpi\":72}");
        Map<String, Object> tplEl = new LinkedHashMap<>();
        tplEl.put("type", "TEXT"); tplEl.put("name", "MCP Title");
        tplEl.put("x", 10); tplEl.put("y", 8); tplEl.put("w", 190); tplEl.put("h", 12);
        tplEl.put("text", "MCP TEMPLATE");
        Map<String, Object> tpl2Args = new LinkedHashMap<>();
        tpl2Args.put("name", "Surface MCP Template");
        tpl2Args.put("pageSize", "A4");
        tpl2Args.put("elements", List.of(tplEl));
        String tpl2Id = h.runKeepId("create_template", M.writeValueAsString(tpl2Args));
        h.run("update_template", "{\"id\":\"" + tpl2Id + "\",\"name\":\"Surface MCP Template v2\"}");
        String dupId = h.runKeepId("duplicate_template",
                "{\"id\":\"" + templateId + "\",\"newName\":\"Surface Duplicate\"}");
        dupTemplateId = dupId;

        // ── Variables ───────────────────────────────────────────────
        h.run("list_variables", "{}");
        h.run("create_variable", "{\"key\":\"surface_var\",\"label\":\"Surface Var\"}");
        variableKey = "surface_var";
        h.run("create_variable", "{\"key\":\"throwaway_var\",\"label\":\"Throwaway\"}");
        throwawayVarKey = "throwaway_var";
        h.run("update_variable", "{\"key\":\"surface_var\",\"label\":\"Surface Var Renamed\"}");
        h.run("delete_variable", "{\"key\":\"throwaway_var\"}");

        // ── Sales: bills ────────────────────────────────────────────
        h.run("list_bills", "{}");
        billId = h.runKeepId("create_bill",
                "{\"buyerId\":\"byr_s1\",\"items\":[{\"itemId\":\"item_s1\",\"qty\":2}]}");
        h.run("get_bill", "{\"id\":\"" + billId + "\"}");
        bill2Id = h.runKeepId("create_bill",
                "{\"buyerId\":\"byr_s2\",\"items\":[{\"itemId\":\"item_s2\",\"qty\":1}]}");
        bill3Id = h.runKeepId("create_bill",
                "{\"buyerId\":\"byr_s1\",\"items\":[{\"itemId\":\"item_s2\",\"qty\":1}]}");
        h.run("pay_bill", "{\"id\":\"" + bill2Id + "\",\"amount\":100,\"mode\":\"UPI\"}");
        h.run("update_bill_status", "{\"id\":\"" + bill2Id + "\",\"status\":\"PAID\"}");

        // ── Purchases ───────────────────────────────────────────────
        h.run("list_purchases", "{}");
        purchaseId = h.runKeepId("create_purchase",
                "{\"supplierName\":\"Surface Supplier One\",\"items\":[{\"itemId\":\"item_s1\",\"qty\":5}]}");
        purchase2Id = h.runKeepId("create_purchase",
                "{\"supplierName\":\"Surface Supplier Two\",\"items\":[{\"itemId\":\"item_s2\",\"qty\":3}]}");
        h.run("pay_purchase", "{\"id\":\"" + purchaseId + "\",\"amount\":100,\"mode\":\"Cash\"}");

        // ── Expenses ────────────────────────────────────────────────
        h.run("list_expenses", "{}");
        expenseId = h.runKeepId("record_expense",
                "{\"category\":\"Office Rent\",\"amount\":1500,\"paymentMode\":\"Cash\"}");
        expense2Id = h.runKeepId("record_expense",
                "{\"category\":\"Bank Charges\",\"amount\":50,\"paymentMode\":\"Cash\"}");
        h.run("list_expense_accounts", "{}");
        expenseAccountId = h.runKeepId("create_expense_account", "{\"name\":\"Surface Account\"}");
        expenseAccount2Id = h.runKeepId("create_expense_account", "{\"name\":\"Throwaway Account\"}");
        h.run("rename_expense_account",
                "{\"id\":\"" + expenseAccount2Id + "\",\"newName\":\"Throwaway Account Renamed\"}");
        h.run("update_expense_account",
                "{\"id\":\"" + expenseAccount2Id + "\",\"notes\":\"surface note\"}");
        h.run("expense_account_report", "{}");

        // ── Reports & ledger ────────────────────────────────────────
        h.run("list_transactions", "{}");
        h.run("stock_report", "{}");
        h.run("profitability_report", "{}");
        h.run("financial_summary", "{}");
        h.run("daybook", "{}");
        h.run("export_bills_pdf",
                "{\"query\":\"Surface Buyer One\",\"dir\":\"" + exportDir + "\"}");

        // ── Shortcuts ───────────────────────────────────────────────
        h.run("list_shortcuts", "{}");
        h.run("rebind_shortcut", "{\"actionId\":\"test.probe\",\"combo\":\"Ctrl+Shift+I\"}");
        h.run("reset_shortcut", "{\"actionId\":\"test.probe\"}");

        // ── Labels ──────────────────────────────────────────────────
        h.run("get_label_print_state", "{\"templateId\":\"" + labelTplId + "\"}");
        h.runLenient("print_labels",
                "{\"templateId\":\"" + labelTplId
                        + "\",\"lines\":[{\"variableValues\":{},\"copies\":1}],\"test\":true}");
        h.run("list_label_prints", "{}");

        // ── Knowledge lifecycle (isolated library) ──────────────────
        h.run("create_knowledge",
                "{\"path\":\"AI Chatbot / 11. Surface\",\"title\":\"Surface Article\",\"markdown\":\"# Surface\"}");
        String k2 = h.runKeepId("create_knowledge",
                "{\"path\":\"AI Chatbot / 11. Surface\",\"title\":\"Surface Scratch\",\"markdown\":\"# Scratch\"}");
        knowledge2Id = k2;
        h.run("get_knowledge", "{\"id\":\"" + k2 + "\"}");
        h.run("update_knowledge", "{\"id\":\"" + k2 + "\",\"markdown\":\"# Replaced body\"}");
        h.run("append_knowledge", "{\"id\":\"" + k2 + "\",\"markdown\":\"\\nAppended block\"}");

        // ── System backup ───────────────────────────────────────────
        h.run("create_backup",
                "{\"path\":\"" + exportDir.resolve("backup.json") + "\"}");

        // ── Deletes LAST (throwaway entities only) ──────────────────
        h.run("delete_knowledge", "{\"id\":\"" + k2 + "\"}");
        h.run("delete_template", "{\"id\":\"" + dupId + "\"}");
        h.run("delete_bill", "{\"id\":\"" + bill3Id + "\"}");
        h.run("delete_purchase", "{\"id\":\"" + purchase2Id + "\"}");
        h.run("delete_expense", "{\"id\":\"" + expense2Id + "\"}");
        h.run("delete_expense_account", "{\"id\":\"" + expenseAccount2Id + "\"}");
        h.run("delete_category", "{\"id\":\"" + throwawayCatId + "\"}");
        h.run("delete_transport", "{\"id\":\"" + tr2Id + "\"}");
        h.run("delete_item", "{\"id\":\"" + mcpItemId + "\"}");
        h.run("delete_supplier", "{\"id\":\"sup_del1\"}");
        h.run("delete_buyer", "{\"id\":\"byr_del1\"}");

        // ── COVERAGE: every tool the registry exposes was driven ────
        Set<String> registry = new HashSet<>();
        for (McpToolRegistry.ToolDef t : McpToolRegistry.tools()) registry.add(t.name);
        registry.remove("confirm_operation"); // exercised inside every gated flow
        Set<String> missing = new HashSet<>(registry);
        missing.removeAll(covered);
        assertTrue(missing.isEmpty(),
                "MCP tools NOT covered by the chat loop: " + missing
                        + " (covered " + covered.size() + "/" + registry.size() + ")");
    }

    // ══════════════════════════════════════════════════════════════════
    // 2. Router / rounds / history scenarios
    // ══════════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    void routerShortlistsThenEscalatesOnceWhenTheModelGoesOffShortlist() throws Exception {
        ChatbotConfig c = cfg();
        c.setSmartRouting(true);
        SCRIPT.add(text("ROUTE: list_buyers"));                 // 1 router
        SCRIPT.add(call("list_suppliers", "{}"));               // 2 heavy, off-shortlist call
        SCRIPT.add(call("list_suppliers", "{}"));               // 3 heavy, full catalogue
        SCRIPT.add(text("Here are your suppliers."));           // 4 heavy, final text

        int before = baseline();
        AiChatClient.ChatResult r = new AiChatClient().send(c, List.of(),
                "list suppliers now", null);

        List<JsonNode> reqs = new ArrayList<>(CAPTURED.subList(before, CAPTURED.size()));
        assertEquals(4, reqs.size(), "router + shortlist + escalated + final");
        assertEquals(1, toolCount(reqs.get(1)), "shortlisted pass carries ONLY list_buyers");
        assertEquals("list_buyers", reqs.get(1).path("tools").path(0)
                .path("functionDeclarations").path(0).path("name").asText());
        assertEquals(McpToolRegistry.tools().size(), toolCount(reqs.get(2)),
                "escalated pass carries the FULL catalogue");
        assertEquals(1, r.toolTrace().stream().filter(t -> t.startsWith("list_suppliers(")).count(),
                "the off-shortlist call from req2 must NOT execute — only the escalated one");
        assertTrue(r.text().contains("Here are your suppliers."));
    }

    @Test
    @Order(3)
    void toolRoundSafetyStopStopsTheLoop() throws Exception {
        ChatbotConfig c = cfg();
        c.setMaxToolCalls(1);
        SCRIPT.add(call("list_buyers", "{}"));
        SCRIPT.add(call("list_buyers", "{}"));

        AiChatClient.ChatResult r = new AiChatClient().send(c, List.of(), "loop forever", null);

        assertTrue(r.text().contains("1 tool rounds"),
                "safety stop must report the configured round cap, got: " + r.text());
        assertTrue(r.toolTrace().size() >= 2, "both scripted calls executed before the stop");
    }

    @Test
    @Order(4)
    void twoToolCallsInOneRoundShareOneResultTurn() throws Exception {
        SCRIPT.add(callMulti(List.of(
                Map.of("list_buyers", "{}"),
                Map.of("list_suppliers", "{}"))));
        SCRIPT.add(text("Both lists are in."));

        int before = baseline();
        AiChatClient.ChatResult r = new AiChatClient().send(cfg(), List.of(),
                "list buyers and suppliers", null);

        List<JsonNode> reqs = new ArrayList<>(CAPTURED.subList(before, CAPTURED.size()));
        assertEquals(2, reqs.size(), "one tool round + final answer");
        int responses = 0;
        for (JsonNode c2 : reqs.get(1).path("contents")) {
            for (JsonNode p : c2.path("parts")) {
                if (p.path("functionResponse").isObject()) responses++;
            }
        }
        assertEquals(2, responses, "both tool results must ride back in ONE turn");
        assertTrue(r.toolTrace().stream().anyMatch(t -> t.startsWith("list_buyers(")));
        assertTrue(r.toolTrace().stream().anyMatch(t -> t.startsWith("list_suppliers(")));
    }

    @Test
    @Order(5)
    void historyWindowBoundsTheOutgoingPayload() throws Exception {
        ChatbotConfig c = cfg();
        c.setHistoryMessages(4);
        List<AiChatClient.ChatTurn> history = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            history.add(AiChatClient.ChatTurn.user("OLD_" + i + " question"));
            history.add(AiChatClient.ChatTurn.assistant("OLD_" + i + " answer"));
        }
        SCRIPT.add(text("fresh answer"));

        int before = baseline();
        AiChatClient.ChatResult r = new AiChatClient().send(c, history, "new question", null);

        assertEquals("fresh answer", r.text());
        List<JsonNode> reqs = new ArrayList<>(CAPTURED.subList(before, CAPTURED.size()));
        JsonNode payload = reqs.get(0);
        assertTrue(payload.path("contents").size() <= 5,
                "4 prior turns + current = max 5 merged contents, got "
                        + payload.path("contents").size());
        String flat = payload.toString();
        assertFalse(flat.contains("OLD_0 "), "oldest history must be trimmed");
        assertTrue(flat.contains("OLD_29"), "the most recent turns must survive");
    }

    @Test
    @Order(6)
    void confirmationPromptBeatsTheGreetingMatcher() throws Exception {
        // The assistant asked for confirmation; even a literal "hi" must go
        // to the model (with pending-approval context), never be eaten by
        // the local greeting shortcut.
        ChatbotConfig c = cfg();
        List<AiChatClient.ChatTurn> history = List.of(
                AiChatClient.ChatTurn.user("delete supplier Surface Supplier Two"),
                AiChatClient.ChatTurn.assistant(
                        "Queued: delete supplier. Reply yes to confirm the deletion."));
        SCRIPT.add(text("Waiting for your confirmation first."));

        int before = baseline();
        AiChatClient.ChatResult r = new AiChatClient().send(c, history, "hi", null);

        List<JsonNode> reqs = new ArrayList<>(CAPTURED.subList(before, CAPTURED.size()));
        assertEquals(1, reqs.size(), "confirmation context must make exactly one provider call");
        assertTrue(r.text().contains("Waiting"), "the scripted reply must win over a local greeting: " + r.text());
    }

    @Test
    @Order(7)
    void greetingsAnswerLocallyInstantlyMidChat() throws Exception {
        ChatbotConfig c = cfg();
        c.setSmartRouting(true);
        List<AiChatClient.ChatTurn> history = List.of(
                AiChatClient.ChatTurn.user("list my suppliers"),
                AiChatClient.ChatTurn.assistant("Here they are."));

        int before = baseline();
        long t0 = System.currentTimeMillis();
        AiChatClient.ChatResult r1 = new AiChatClient().send(c, history, "hi", null);
        long hiMs = System.currentTimeMillis() - t0;
        AiChatClient.ChatResult r2 = new AiChatClient().send(c, history, "thanks", null);
        long total = System.currentTimeMillis() - t0;

        assertTrue(CAPTURED.size() == before, "no provider request may leave the app");
        assertTrue(r1.text().contains("InvoiceStudio"));
        assertTrue(r2.text().contains("welcome") || r2.text().contains("Welcome"));
        assertEquals(-1, r1.totalTokens(), "local replies report no tokens");
        assertTrue(hiMs < 250, "greeting took " + hiMs + "ms — must be instant");
        assertTrue(total < 500, "two greetings took " + total + "ms");
        System.out.printf("[surface-probe] local greetings: hi=%dms, hi+thanks=%dms (0 requests)%n",
                hiMs, total);
    }

    @Test
    @Order(8)
    void clientLayerOverheadProbe() throws Exception {
        // Router + tool round + final answer with an INSTANT stub: whatever
        // time this takes is the chatbot's own overhead — proving the felt
        // slowness comes from provider round trips (free-tier queues), not
        // from our pipeline.
        ChatbotConfig c = cfg();
        c.setSmartRouting(true);
        SCRIPT.add(text("ROUTE: list_buyers"));
        SCRIPT.add(call("list_buyers", "{}"));
        SCRIPT.add(text("| Buyer | Balance |\\n|---|---|\\n| Surface | 0 |"));

        long t0 = System.currentTimeMillis();
        AiChatClient.ChatResult r = new AiChatClient().send(c, List.of(),
                "list my buyers", null);
        long ms = System.currentTimeMillis() - t0;

        assertTrue(r.toolTrace().stream().anyMatch(t -> t.startsWith("list_buyers(")));
        assertTrue(ms < 750, "3-request pipeline took " + ms + "ms with an instant provider — "
                + "our layer must stay negligible");
        System.out.printf("[surface-probe] router+tool+answer with instant provider: %dms (%d requests)%n",
                ms, 3);
    }

    private static int toolCount(JsonNode req) {
        return req.path("tools").path(0).path("functionDeclarations").isArray()
                ? req.path("tools").path(0).path("functionDeclarations").size() : 0;
    }
}
