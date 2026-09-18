package com.invoicestudio.mcp;

import com.invoicestudio.model.UserSession;
import com.invoicestudio.service.AuthSessionManager;
import com.invoicestudio.service.KnowledgeRepository;
import com.invoicestudio.ui.DataManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Knowledge Hub MCP surface (list/get/create/update/append/
 * delete) plus the pay_bill receipt tool, the update_variable editor-parity
 * tool and the label-print-history reader — the September coverage pass.
 *
 * <p>The KnowledgeRepository singleton is pointed at an isolated temp copy
 * (persistToDevSource=false) so tests can never touch the bundled
 * knowledge-hub.json or the developer source tree.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpKnowledgeToolsTest {

    private static final String TEST_DB = "test_mcp_knowledge.db";
    private static DataManager dm;
    private static KnowledgeRepository isolatedRepo;
    private static File tempJson;
    private static final AtomicInteger SEQ = new AtomicInteger();

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toolMap(String name, Map<String, Object> args) throws Exception {
        return (Map<String, Object>) McpToolRegistry.call(name, args);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toolList(String name, Map<String, Object> args) throws Exception {
        return (List<Map<String, Object>>) McpToolRegistry.call(name, args);
    }

    @BeforeAll
    static void setUp() throws Exception {
        new File(TEST_DB).delete();
        com.invoicestudio.db.DatabaseManager.initCustom("jdbc:sqlite:" + TEST_DB);
        AuthSessionManager.setActiveSession(new UserSession(
                "uid_knowledge_test", "knowledge@test.in", "Knowledge Test Traders",
                "tok", "ref", System.currentTimeMillis() + 3600_000L, true));
        dm = DataManager.init(com.invoicestudio.db.DatabaseManager.getInstance());

        // Swap the KnowledgeRepository singleton for an isolated copy: the
        // registry's knowledge() helper reads getInstance(), so pointing the
        // singleton at a temp file (dev-source persistence OFF) keeps the
        // bundled JSON and the dev tree untouched.
        tempJson = File.createTempFile("knowledge-mcp-test-", ".json");
        tempJson.delete();
        isolatedRepo = KnowledgeRepository.createCustom(tempJson.toPath());
        var f = KnowledgeRepository.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, isolatedRepo);
    }

    @AfterAll
    static void tearDown() throws Exception {
        AuthSessionManager.clear();
        // restore the lazy singleton contract
        var f = KnowledgeRepository.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
        if (tempJson != null) tempJson.delete();
        new File(TEST_DB).delete();
        var r1 = com.invoicestudio.db.DatabaseManager.class.getDeclaredField("instance");
        r1.setAccessible(true); r1.set(null, null);
        var r2 = DataManager.class.getDeclaredField("instance");
        r2.setAccessible(true); r2.set(null, null);
    }

    private static String unique(String base) {
        return base + " " + SEQ.incrementAndGet();
    }

    // ------------------------------------------------------------------
    // Knowledge Hub CRUD
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void listKnowledgeShipsSeededChapters() throws Exception {
        List<Map<String, Object>> rows = toolList("list_knowledge", Map.of());
        assertFalse(rows.isEmpty(), "seeded articles must be listable");
        assertTrue(rows.stream().anyMatch(m -> String.valueOf(m.get("path")).startsWith("AI Chatbot")),
                "AI Chatbot chapters must be present");
        assertTrue(rows.stream().anyMatch(m -> String.valueOf(m.get("id")).equals("art_ai_14_bug_playbook")),
                "the new 09. Bug Playbook chapter must ship in the seed");
        // compact projection only — no markdown in the list payload (tokens)
        for (Map<String, Object> m : rows) {
            assertFalse(m.containsKey("markdown"), "list must not carry full bodies");
            assertTrue(m.containsKey("markdownChars"), "list must carry the body size");
        }
    }

    @Test
    @Order(2)
    void listKnowledgeFiltersByPathAndQuery() throws Exception {
        List<Map<String, Object>> ai = toolList("list_knowledge", Map.of("path", "AI Chatbot"));
        assertFalse(ai.isEmpty());
        assertTrue(ai.stream().allMatch(m -> String.valueOf(m.get("path")).contains("AI Chatbot")),
                "path filter must restrict results");

        List<Map<String, Object>> hit = toolList("list_knowledge", Map.of("query", "TSPL"));
        assertTrue(hit.stream().anyMatch(m -> String.valueOf(m.get("path")).contains("TSPL")),
                "query must match body/path/title");
    }

    @Test
    @Order(3)
    void createGetListRoundTrip() throws Exception {
        String title = unique("MCP Round Trip");
        Map<String, Object> created = toolMap("create_knowledge", Map.of(
                "path", "AI Chatbot / 09. Bug Playbook & Token-Light Speed",
                "title", title,
                "subtitle", "round trip test",
                "markdown", "## One\nbody text"));
        assertEquals(true, created.get("ok"));
        assertEquals(false, created.get("existed"));
        String id = String.valueOf(created.get("id"));

        Map<String, Object> got = toolMap("get_knowledge", Map.of("id", id));
        assertEquals(title, got.get("title"));
        assertTrue(String.valueOf(got.get("markdown")).contains("body text"));

        // Idempotent by (path, title): same create returns the same article.
        Map<String, Object> again = toolMap("create_knowledge", Map.of(
                "path", "AI Chatbot / 09. Bug Playbook & Token-Light Speed",
                "title", title,
                "markdown", "## One\nbody text"));
        assertEquals(true, again.get("existed"));
        assertEquals(id, again.get("id"), "duplicate create must NOT stack a twin");
    }

    @Test
    @Order(4)
    void updateAndAppendAreConfirmationGatedAndWork() throws Exception {
        String title = unique("Gated Mutations");
        String id = String.valueOf(toolMap("create_knowledge", Map.of(
                "path", "AI Chatbot / 09. Bug Playbook & Token-Light Speed",
                "title", title,
                "markdown", "## Original\nkeep me")).get("id"));

        // update → confirmation gate
        Map<String, Object> gated = toolMap("update_knowledge", Map.of("id", id,
                "markdown", "## Replaced\nnew body only"));
        assertEquals(true, gated.get("requiresConfirmation"));
        String opId = String.valueOf(gated.get("operationId"));
        assertTrue(String.valueOf(gated.get("summary")).contains("body REPLACED"),
                "the summary must warn that the body is replaced");
        assertTrue(PendingOperations.approve(opId), "approve must execute");
        assertEquals("## Replaced\nnew body only",
                toolMap("get_knowledge", Map.of("id", id)).get("markdown"));

        // append → confirmation gate → block at the end
        Map<String, Object> gatedAppend = toolMap("append_knowledge", Map.of("id", id,
                "markdown", "## Appended\nextra note"));
        assertEquals(true, gatedAppend.get("requiresConfirmation"));
        assertTrue(PendingOperations.approve(String.valueOf(gatedAppend.get("operationId"))));
        String body = String.valueOf(toolMap("get_knowledge", Map.of("id", id)).get("markdown"));
        assertTrue(body.contains("new body only") && body.contains("extra note"),
                "append must KEEP the original body and add the block at the end");
        assertTrue(body.indexOf("new body only") < body.indexOf("extra note"),
                "appended block must be at the END");
    }

    @Test
    @Order(5)
    void appendIsIdempotentOnRetry() throws Exception {
        String title = unique("Append Retry");
        String id = String.valueOf(toolMap("create_knowledge", Map.of(
                "path", "AI Chatbot / 09. Bug Playbook & Token-Light Speed",
                "title", title,
                "markdown", "## Base")).get("id"));

        String block = "## Dated note\nsame every time";
        for (int i = 0; i < 2; i++) {
            Map<String, Object> op = toolMap("append_knowledge", Map.of("id", id, "markdown", block));
            assertTrue(PendingOperations.approve(String.valueOf(op.get("operationId"))));
        }
        String body = String.valueOf(toolMap("get_knowledge", Map.of("id", id)).get("markdown"));
        assertEquals(1, body.split("## Dated note", -1).length - 1,
                "retrying the same append must not duplicate the block");
    }

    @Test
    @Order(6)
    void deleteIsGatedAndRefusesUnknownIds() throws Exception {
        String id = String.valueOf(toolMap("create_knowledge", Map.of(
                "path", "AI Chatbot / 09. Bug Playbook & Token-Light Speed",
                "title", unique("Delete Me"),
                "markdown", "x")).get("id"));

        Map<String, Object> op = toolMap("delete_knowledge", Map.of("id", id));
        assertEquals(true, op.get("requiresConfirmation"));
        assertTrue(PendingOperations.approve(String.valueOf(op.get("operationId"))));

        Exception ex = assertThrows(Exception.class,
                () -> toolMap("get_knowledge", Map.of("id", id)));
        assertTrue(String.valueOf(ex.getMessage()).contains("not found"));

        // Fail-fast BEFORE the gate: unknown ids never queue an operation
        Exception ff = assertThrows(Exception.class,
                () -> toolMap("update_knowledge", Map.of("id", "art_missing", "title", "x")));
        assertTrue(String.valueOf(ff.getMessage()).contains("not found"));
        assertTrue(PendingOperations.pending().stream().noneMatch(o -> "update_knowledge".equals(o.getTool())),
                "unknown-id update must not queue a pending operation");
    }

    @Test
    @Order(7)
    void knowledgeMutationsNotifyListeners() throws Exception {
        AtomicInteger notified = new AtomicInteger();
        Runnable l = notified::incrementAndGet;
        KnowledgeRepository.addChangeListener(l);
        try {
            String id = String.valueOf(toolMap("create_knowledge", Map.of(
                    "path", "AI Chatbot / 09. Bug Playbook & Token-Light Speed",
                    "title", unique("Listener"),
                    "markdown", "x")).get("id"));
            assertTrue(notified.get() >= 1, "create must notify (drives the live UI refresh)");
            Map<String, Object> op = toolMap("delete_knowledge", Map.of("id", id));
            PendingOperations.approve(String.valueOf(op.get("operationId")));
            assertTrue(notified.get() >= 2, "delete must notify");
        } finally {
            KnowledgeRepository.removeChangeListener(l);
        }
    }

    // ------------------------------------------------------------------
    // pay_bill — receipts against invoices
    // ------------------------------------------------------------------

    @Test
    @Order(8)
    void payBillRecordsReceiptAndAutoMarksPaid() throws Exception {
        // Seed buyer + item + bill through the same MCP surface
        Map<String, Object> buyer = toolMap("create_buyer", Map.of("name", unique("Pay Bill Buyer")));
        Map<String, Object> item = toolMap("create_item", Map.of("name", unique("Pay Bill Item"), "rate", 1000.0));
        Map<String, Object> bill = toolMap("create_bill", Map.of(
                "buyerId", buyer.get("id"),
                "items", List.of(Map.of("itemId", item.get("id"), "qty", 3)))); // 3000 + GST

        String billId = String.valueOf(bill.get("id"));

        // Partial payment
        Map<String, Object> p1 = toolMap("pay_bill", Map.of("id", billId, "amount", 1000.0, "mode", "UPI"));
        assertEquals(true, p1.get("ok"));
        assertEquals(false, p1.get("fullySettled"));
        // Overpay attempt is clamped to the remaining balance
        Map<String, Object> p2 = toolMap("pay_bill", Map.of("id", billId, "amount", 99999.0));
        assertEquals(true, p2.get("fullySettled"), "over-clamp must settle the invoice");
        assertEquals(2000.0, ((Number) p2.get("paid")).doubleValue(), 0.01,
                "second payment must be clamped to remaining 2000");
        assertEquals("PAID", String.valueOf(p2.get("status")));

        // A fully-paid invoice refuses further receipts
        Exception ex = assertThrows(Exception.class, () -> toolMap("pay_bill", Map.of("id", billId)));
        assertTrue(String.valueOf(ex.getMessage()).contains("already fully paid"));

        // Unknown bill id
        Exception nf = assertThrows(Exception.class,
                () -> toolMap("pay_bill", Map.of("id", "bill_missing")));
        assertTrue(String.valueOf(nf.getMessage()).contains("not found"));
    }

    // ------------------------------------------------------------------
    // update_variable — Variables editor parity
    // ------------------------------------------------------------------

    @Test
    @Order(9)
    void updateVariableEditsCustomAndGuardsBuiltin() throws Exception {
        String key = "mcp_test_var_" + SEQ.incrementAndGet();
        toolMap("create_variable", Map.of("key", key, "label", "Old", "defaultValue", "1"));

        Map<String, Object> op = toolMap("update_variable", Map.of(
                "key", key, "label", "New Label", "defaultValue", "42"));
        assertEquals(true, op.get("requiresConfirmation"));
        assertTrue(PendingOperations.approve(String.valueOf(op.get("operationId"))));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> vars = (List<Map<String, Object>>) McpToolRegistry.call("list_variables", Map.of());
        Map<String, Object> edited = vars.stream()
                .filter(v -> key.equals(v.get("key"))).findFirst().orElseThrow();
        assertEquals("New Label", edited.get("label"));
        assertEquals("42", edited.get("defaultValue"));

        // Builtin variables are read-only
        Map<String, Object> builtin = vars.stream()
                .filter(v -> Boolean.TRUE.equals(v.get("builtin"))).findFirst().orElse(null);
        if (builtin != null) {
            Exception ex = assertThrows(Exception.class, () -> {
                Map<String, Object> g = toolMap("update_variable",
                        Map.of("key", builtin.get("key"), "label", "Nope"));
                PendingOperations.approve(String.valueOf(g.get("operationId")));
            });
            assertTrue(String.valueOf(ex.getMessage()).contains("fixed app variable"),
                    "builtin edit must be refused");
        }

        // Unknown key fails fast before the gate
        Exception nf = assertThrows(Exception.class,
                () -> toolMap("update_variable", Map.of("key", "nope_missing", "label", "x")));
        assertTrue(String.valueOf(nf.getMessage()).contains("not found"));
    }

    // ------------------------------------------------------------------
    // list_label_prints — history reader
    // ------------------------------------------------------------------

    @Test
    @Order(10)
    void listLabelPrintsReturnsListShape() throws Exception {
        List<Map<String, Object>> rows = toolList("list_label_prints", Map.of());
        // Empty history is fine — shape is the contract
        assertTrue(rows instanceof List, "must return a list");
    }

    // ------------------------------------------------------------------
    // System-prompt: pending approvals are spelled out with exact ids
    // ------------------------------------------------------------------

    @Test
    @Order(11)
    void systemPromptCarriesPendingApprovalIds() throws Exception {
        String id = String.valueOf(toolMap("create_knowledge", Map.of(
                "path", "AI Chatbot / 09. Bug Playbook & Token-Light Speed",
                "title", unique("Prompt Injection"),
                "markdown", "x")).get("id"));
        Map<String, Object> op = toolMap("delete_knowledge", Map.of("id", id));
        String opId = String.valueOf(op.get("operationId"));

        String with = AiChatClientPromptBridge.systemPromptForTest(false);
        assertTrue(with.contains("PENDING APPROVALS"), "pending ops must be listed");
        assertTrue(with.contains(opId), "the exact operationId must be in the prompt");
        assertTrue(with.contains("delete_knowledge"), "the tool name must be listed");

        // After rejection the hint disappears
        PendingOperations.reject(opId);
        String after = AiChatClientPromptBridge.systemPromptForTest(false);
        assertFalse(after.contains(opId), "handled operations must vanish from the prompt");
    }

    /** Test bridge: package-private access to the system prompt builder. */
    static final class AiChatClientPromptBridge {
        static String systemPromptForTest(boolean mcpOff) throws Exception {
            var m = Class.forName("com.invoicestudio.service.AiChatClient")
                    .getDeclaredMethod("systemPrompt", boolean.class);
            m.setAccessible(true);
            return (String) m.invoke(Class.forName("com.invoicestudio.service.AiChatClient")
                    .getDeclaredConstructor().newInstance(), mcpOff);
        }
    }
}
