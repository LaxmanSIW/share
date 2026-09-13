package com.invoicestudio.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.db.DatabaseManager;
import com.invoicestudio.model.Bill;
import com.invoicestudio.model.BillStatus;
import com.invoicestudio.model.UserSession;
import com.invoicestudio.service.AuthSessionManager;
import com.invoicestudio.ui.DataManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test of the InvoiceStudio MCP server over real HTTP:
 * protocol handshake (initialize / tools/list / tools/call), auth,
 * the destructive-operation confirmation flow, real business flows
 * (invoice → GST → stock → payment → reports) and lifecycle.
 *
 * Note: DataManager is a JVM-wide singleton shared with other test classes,
 * so every assertion here is scoped to entities this test created.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpServerTest {

    private static final String TEST_DB = "test_mcp_server.db";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int PORT = 7899;
    private static final String TOKEN = "test-token-123";

    private static DataManager dm;
    private static McpConfig config;
    private static HttpClient http;

    private static String buyerId;
    private static String itemId;
    private static String billId;
    private static String purchaseId;

    @BeforeAll
    static void setUp() {
        new File(TEST_DB).delete();
        com.invoicestudio.db.DatabaseManager.initCustom("jdbc:sqlite:" + TEST_DB);
        AuthSessionManager.setActiveSession(new UserSession(
                "uid_mcp_test", "ai@test.in", "MCP Test Traders",
                "tok", "ref", System.currentTimeMillis() + 3600_000L, true));
        dm = DataManager.init(com.invoicestudio.db.DatabaseManager.getInstance());

        config = new McpConfig();
        config.setPort(PORT);
        config.setRequireToken(true);
        config.setToken(TOKEN);
        config.setAutoStart(false);

        http = HttpClient.newHttpClient();
        String err = McpServer.start(config);
        assertNull(err, "MCP server should start: " + err);
    }

    @AfterAll
    static void tearDown() throws Exception {
        McpServer.shutdown();
        PendingOperations.clearAll();
        new File(TEST_DB).delete();

        // DataManager/DatabaseManager are JVM-wide singletons shared with the
        // other suite classes. This class initialized them, so it must release
        // them — otherwise the next suite's DataManager.init(db) is a no-op and
        // its assertions read this suite's database.
        resetSingleton(DatabaseManager.class, "instance");
        resetSingleton(DataManager.class, "instance");
        com.invoicestudio.service.AuthSessionManager.clear();
    }

    private static void resetSingleton(Class<?> clazz, String fieldName) throws Exception {
        java.lang.reflect.Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(null, null);
    }

    // ------------------------------------------------------------------
    // HTTP / JSON-RPC helpers
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rpc(String method, Map<String, Object> params, String token) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("jsonrpc", "2.0");
        body.put("id", 1);
        body.put("method", method);
        if (params != null) body.put("params", params);

        HttpRequest.Builder rb = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + PORT + "/mcp"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
        if (token != null) rb.header("Authorization", "Bearer " + token);

        HttpResponse<String> resp = http.send(rb.build(), HttpResponse.BodyHandlers.ofString());
        return Map.of("_status", resp.statusCode(), "_body", MAPPER.readValue(resp.body(), Map.class));
    }

    private static Map<String, Object> rpc(String method, Map<String, Object> params) throws Exception {
        return rpc(method, params, TOKEN);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(Map<String, Object> resp) {
        assertEquals(200, resp.get("_status"));
        return (Map<String, Object>) ((Map<String, Object>) resp.get("_body")).get("result");
    }

    /** Tool result text parsed as JSON (object or array). */
    @SuppressWarnings("unchecked")
    private static Object callToolRaw(String name, Map<String, Object> args) throws Exception {
        Map<String, Object> res = result(rpc("tools/call",
                Map.of("name", name, "arguments", args == null ? Map.of() : args)));
        List<Map<String, Object>> content = (List<Map<String, Object>>) res.get("content");
        String text = (String) content.get(0).get("text");
        return MAPPER.readValue(text, Object.class);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> callTool(String name, Map<String, Object> args) throws Exception {
        return (Map<String, Object>) callToolRaw(name, args);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> callToolList(String name, Map<String, Object> args) throws Exception {
        return (List<Map<String, Object>>) callToolRaw(name, args);
    }

    // ------------------------------------------------------------------
    // Protocol & auth
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void rejectsRequestsWithoutToken() throws Exception {
        Map<String, Object> resp = rpc("tools/list", Map.of(), null);
        assertEquals(401, resp.get("_status"), "missing token must be rejected");
    }

    @Test
    @Order(2)
    void rejectsRequestsWithWrongToken() throws Exception {
        Map<String, Object> resp = rpc("tools/list", Map.of(), "wrong-token");
        assertEquals(401, resp.get("_status"));
    }

    @Test
    @Order(3)
    void initializeHandshakeWorks() throws Exception {
        Map<String, Object> res = result(rpc("initialize", Map.of()));
        assertEquals(McpServer.SERVER_NAME,
                ((Map<?, ?>) res.get("serverInfo")).get("name"));
        assertNotNull(res.get("protocolVersion"));
        assertNotNull(res.get("capabilities"));
    }

    @Test
    @Order(4)
    void toolsListExposesFullSurface() throws Exception {
        Map<String, Object> res = result(rpc("tools/list", Map.of()));
        List<Map<String, Object>> tools = (List<Map<String, Object>>) res.get("tools");
        assertTrue(tools.size() >= 30, "expected 30+ tools, got " + tools.size());
        List<String> names = tools.stream().map(t -> (String) t.get("name")).toList();
        assertTrue(names.contains("get_app_guide"));
        assertTrue(names.contains("create_bill"));
        assertTrue(names.contains("delete_bill"));
        assertTrue(names.contains("financial_summary"));
        assertTrue(names.contains("confirm_operation"));
        String delDesc = tools.stream().filter(t -> "delete_bill".equals(t.get("name")))
                .map(t -> (String) t.get("description")).findFirst().orElse("");
        assertTrue(delDesc.startsWith("[CONFIRMATION REQUIRED]"),
                "delete tools must advertise the confirmation requirement");
    }

    @Test
    @Order(5)
    void unknownMethodReturnsProtocolError() throws Exception {
        Map<String, Object> resp = rpc("bogus/method", Map.of());
        Map<String, Object> body = (Map<String, Object>) resp.get("_body");
        Map<String, Object> err = (Map<String, Object>) body.get("error");
        assertEquals(-32601, ((Number) err.get("code")).intValue());
    }

    @Test
    @Order(6)
    void unknownToolIsCleanError() throws Exception {
        Map<String, Object> resp = rpc("tools/call",
                Map.of("name", "no_such_tool", "arguments", Map.of()));
        Map<String, Object> body = (Map<String, Object>) resp.get("_body");
        assertNotNull(body.get("error"));
    }

    // ------------------------------------------------------------------
    // Knowledge tools
    // ------------------------------------------------------------------

    @Test
    @Order(10)
    void appGuideIsCompleteManual() throws Exception {
        Map<String, Object> resp = result(rpc("tools/call",
                Map.of("name", "get_app_guide", "arguments", Map.of())));
        List<Map<String, Object>> content = (List<Map<String, Object>>) resp.get("content");
        String text = (String) content.get(0).get("text");
        assertTrue(text.contains("InvoiceStudio"));
        assertTrue(text.contains("Template Designer"));
        assertTrue(text.contains("stock"));
        assertTrue(text.length() > 3000, "guide must be a real manual, got " + text.length() + " chars");
    }

    @Test
    @Order(11)
    void mcpDocsExplainSafetyModel() throws Exception {
        Map<String, Object> resp = result(rpc("tools/call",
                Map.of("name", "get_mcp_docs", "arguments", Map.of())));
        List<Map<String, Object>> content = (List<Map<String, Object>>) resp.get("content");
        String text = (String) content.get(0).get("text");
        assertTrue(text.contains("requiresConfirmation") || text.contains("confirmation"));
        assertTrue(text.contains("127.0.0.1"));
    }

    @Test
    @Order(12)
    void settingsAreReadable() throws Exception {
        Map<String, Object> res = callTool("get_settings", Map.of());
        assertNotNull(res.get("businessName"));
        assertNotNull(res.get("currency"));
    }

    @Test
    @Order(13)
    void serverStatusReflectsRunningState() throws Exception {
        Map<String, Object> res = callTool("server_status", Map.of());
        assertEquals(Boolean.TRUE, res.get("running"));
        assertTrue(((Number) res.get("toolCount")).intValue() >= 30);
        assertEquals(0, ((Number) res.get("pendingConfirmations")).intValue());
    }

    // ------------------------------------------------------------------
    // Business flow: masters → invoice → confirmation → reports
    // ------------------------------------------------------------------

    @Test
    @Order(20)
    void createMasterDataViaMcp() throws Exception {
        Map<String, Object> buyer = callTool("create_buyer", Map.of(
                "name", "MCP Fleet Buyers", "gst", "27AABCU9603R1ZM",
                "stateCode", "27", "phone", "9876543210"));
        assertEquals(Boolean.TRUE, buyer.get("ok"));
        buyerId = (String) buyer.get("id");

        Map<String, Object> item = callTool("create_item", Map.of(
                "name", "MCP Brake Pad Set", "hsn", "8708", "unit", "SET",
                "rate", 850.0, "gst", 28.0, "purchaseRate", 600.0,
                "openingStock", 10.0, "reorderLevel", 5.0));
        assertEquals(Boolean.TRUE, item.get("ok"));
        itemId = (String) item.get("id");

        Map<String, Object> supplier = callTool("create_supplier", Map.of(
                "name", "MCP OEM Spares Dealer", "gst", "27AAECS1234F1Z5",
                "stateCode", "27", "openingBalance", 1500.0));
        assertEquals(Boolean.TRUE, supplier.get("ok"));

        // directory reflects them
        List<Map<String, Object>> items = callToolList("list_items", Map.of("query", "MCP Brake Pad"));
        assertEquals(1, items.size());
        assertEquals(10.0, ((Number) items.get(0).get("stock")).doubleValue(), 0.001,
                "opening stock must surface in list_items");

        List<Map<String, Object>> suppliers = callToolList("list_suppliers", Map.of("query", "MCP OEM"));
        assertEquals(1, suppliers.size());
        assertEquals(1500.0, ((Number) suppliers.get(0).get("payableBalance")).doubleValue(), 0.01);
    }

    @Test
    @Order(22)
    void createInvoiceWithCatalogItemComputesGstAndStock() throws Exception {
        Map<String, Object> res = callTool("create_bill", Map.of(
                "buyerId", buyerId,
                "items", List.of(Map.of("itemId", itemId, "qty", 4.0)),
                "paid", true, "paymentMode", "UPI"));

        assertEquals(Boolean.TRUE, res.get("ok"));
        // 4 × 850 = 3400 taxable; intra-state 28% → CGST 476 + SGST 476; grand 4352
        assertEquals(4352.0, ((Number) res.get("grandTotal")).doubleValue(), 0.01);
        assertEquals("PAID", res.get("status"));
        assertEquals(Boolean.FALSE, res.get("interState"));
        billId = (String) res.get("id");

        // full bill readable back
        Map<String, Object> full = callTool("get_bill", Map.of("id", billId));
        assertEquals("MCP Fleet Buyers", full.get("buyer"));
        List<?> lines = (List<?>) full.get("items");
        assertEquals(1, lines.size());

        // stock OUT recorded: 10 − 4 = 6
        assertEquals(6.0, closingQty(), 0.001);
    }

    @Test
    @Order(23)
    void updateBillStatusRequiresConfirmation() throws Exception {
        Map<String, Object> res = callTool("update_bill_status",
                Map.of("id", billId, "status", "CANCELLED"));
        assertEquals(Boolean.TRUE, res.get("requiresConfirmation"));
        String opId = (String) res.get("operationId");
        assertNotNull(opId);

        // bill unchanged until approved
        assertEquals(BillStatus.PAID, dm.bills().getBillById(billId).getStatus());

        Map<String, Object> conf = callTool("confirm_operation",
                Map.of("operationId", opId, "approve", true));
        assertEquals(Boolean.TRUE, conf.get("ok"));
        assertEquals(BillStatus.CANCELLED, dm.bills().getBillById(billId).getStatus());

        // also visible in list_bills filter
        List<Map<String, Object>> cancelled = callToolList("list_bills",
                Map.of("query", billId, "status", "CANCELLED"));
        assertEquals(1, cancelled.size());
    }

    @Test
    @Order(24)
    void purchasePaymentAndReports() throws Exception {
        Map<String, Object> res = callTool("create_purchase", Map.of(
                "supplierName", "MCP OEM Spares Dealer",
                "supplierBillNo", "OEM-2231",
                "items", List.of(Map.of("itemId", itemId, "qty", 10.0, "rate", 600.0)),
                "freight", 100.0));
        assertEquals(Boolean.TRUE, res.get("ok"));
        // taxable 6000, GST 28% intra = 1680 total (CGST 840 + SGST 840) → grand 7680
        // amountPayable adds freight 100 → 7780 (this is what gets paid, not grandTotal)
        assertEquals(7680.0, ((Number) res.get("grandTotal")).doubleValue(), 0.01);
        assertEquals(7780.0, ((Number) res.get("amountPayable")).doubleValue(), 0.01);
        assertEquals(Boolean.FALSE, res.get("interState"));
        purchaseId = (String) res.get("id");

        // stock back up: 6 + 10 = 16
        assertEquals(16.0, closingQty(), 0.001);

        // supplier payable: 1500 opening + 7780 due = 9280
        List<Map<String, Object>> suppliers = callToolList("list_suppliers", Map.of("query", "MCP OEM"));
        double payableBefore = ((Number) suppliers.get(0).get("payableBalance")).doubleValue();
        assertEquals(9280.0, payableBefore, 0.01, "supplier payable before payment");

        // partial payment 5000 → bill due 7780 - 5000 = 2780 remaining
        // (the 1500 opening balance is not part of this bill)
        Map<String, Object> pay = callTool("pay_purchase",
                Map.of("id", purchaseId, "amount", 5000.0, "mode", "Bank Transfer"));
        assertEquals(2780.0, ((Number) pay.get("remaining")).doubleValue(), 0.01);
        assertEquals(Boolean.FALSE, pay.get("fullySettled"));

        // supplier total payable now: 1500 opening + 2780 bill due = 4280
        List<Map<String, Object>> suppliersAfter = callToolList("list_suppliers", Map.of("query", "MCP OEM"));
        assertEquals(4280.0, ((Number) suppliersAfter.get(0).get("payableBalance")).doubleValue(), 0.01);

        // financial summary structure is complete (values are aggregate across shared test data)
        Map<String, Object> fin = callTool("financial_summary", Map.of());
        assertNotNull(fin.get("trading"));
        assertNotNull(fin.get("profitAndLoss"));
        assertNotNull(fin.get("balanceSheet"));
        assertNotNull(fin.get("gst"));
        assertNotNull(fin.get("period"));

        // profitability report runs; the item was cancelled in Order 23 so its
        // sale is (correctly) excluded — just verify the report shape is a list
        List<Map<String, Object>> prof = callToolList("profitability_report", Map.of());
        assertNotNull(prof);

        // stock report low-stock structure exists (item at 16 > reorder 5, not low)
        Object sr = callToolRaw("stock_report", Map.of());
        assertTrue(sr.toString().contains("lowStock"));
    }

    @Test
    @Order(25)
    void recordExpenseRoutesToCorrectHead() throws Exception {
        Map<String, Object> direct = callTool("record_expense", Map.of(
                "category", "Freight Inward", "amount", 250.0, "payee", "MCP Transport Co"));
        assertEquals("DIRECT (Trading A/c)", direct.get("head"));

        Map<String, Object> indirect = callTool("record_expense", Map.of(
                "category", "Office Rent", "amount", 12000.0));
        assertEquals("INDIRECT (P&L)", indirect.get("head"));

        List<Map<String, Object>> all = callToolList("list_expenses", Map.of("limit", 500));
        assertTrue(all.stream().anyMatch(e -> "MCP Transport Co".equals(e.get("payee"))));
    }

    @Test
    @Order(26)
    void rejectFlowLeavesDataUntouched() throws Exception {
        Map<String, Object> res = callTool("delete_item", Map.of("id", itemId));
        assertEquals(Boolean.TRUE, res.get("requiresConfirmation"));
        String opId = (String) res.get("operationId");

        callTool("confirm_operation", Map.of("operationId", opId, "approve", false));
        assertNotNull(dm.items().getItemById(itemId), "item must survive a rejected delete");
    }

    @Test
    @Order(27)
    void templateVariablesAndIdentityViaMcp() throws Exception {
        // create with elements (positions in mm)
        Map<String, Object> created = callTool("create_template", Map.of(
                "name", "MCP Thermal Bill", "pageSize", "THERMAL_80",
                "elements", List.of(
                        Map.of("type", "TEXT", "name", "Shop Name", "x", 5.0, "y", 4.0,
                                "w", 60.0, "h", 8.0, "text", "MCP TEST TRADERS"),
                        Map.of("type", "TABLE", "name", "Items", "x", 4.0, "y", 30.0,
                                "w", 70.0, "h", 60.0))));
        assertEquals(Boolean.TRUE, created.get("ok"));
        String tplId = (String) created.get("id");
        assertEquals(2, ((Number) created.get("elements")).intValue());

        // full anatomy readable back (pageSize renders in display form)
        Map<String, Object> full = callTool("get_template", Map.of("id", tplId));
        assertEquals("Thermal 80", full.get("pageSize"));
        List<?> els = (List<?>) full.get("elements");
        assertEquals(2, els.size());

        // duplicate as a starting point
        Map<String, Object> dup = callTool("duplicate_template",
                Map.of("id", tplId, "newName", "MCP Thermal Copy"));
        assertEquals(Boolean.TRUE, dup.get("ok"));

        // variables list usable for bindings
        List<Map<String, Object>> vars = callToolList("list_variables", Map.of());
        assertNotNull(vars);

        // whoami: whose books + auth model, no secrets
        Map<String, Object> me = callTool("whoami", Map.of());
        assertEquals(Boolean.TRUE, me.get("loggedIn"));
        assertEquals("uid_mcp_test", me.get("userId"));
        assertTrue(String.valueOf(me.get("authModel")).contains("bearer"));
        assertFalse(me.containsKey("idToken") || me.containsKey("refreshToken"),
                "must not leak Firebase session tokens");

        // update requires confirmation then lands
        Map<String, Object> upd = callTool("update_template",
                Map.of("id", tplId, "name", "MCP Thermal Renamed"));
        assertEquals(Boolean.TRUE, upd.get("requiresConfirmation"));
        callTool("confirm_operation", Map.of("operationId", upd.get("operationId"), "approve", true));
        assertEquals("MCP Thermal Renamed", dm.templates().getTemplateById(tplId).getName());
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    @Test
    @Order(30)
    void stopKillsEndpointAndPendingOps() throws Exception {
        callTool("delete_item", Map.of("id", itemId));
        assertEquals(1, PendingOperations.pending().size());

        McpServer.stop();
        assertFalse(McpServer.isRunning());
        assertEquals(0, PendingOperations.pending().size(), "stop must discard unapproved ops");

        assertThrows(Exception.class, () -> rpc("tools/list", Map.of()));

        String err = McpServer.start(config);
        assertNull(err);
        assertTrue(McpServer.isRunning());
    }

    @Test
    @Order(31)
    void auditLogRecordsToolCalls() throws Exception {
        List<?> log = callToolList("audit_log", Map.of("limit", 500));
        assertTrue(log.size() >= 10, "audit trail must capture the session activity");
        String joined = log.toString();
        assertTrue(joined.contains("[TOOL]"));
        assertTrue(joined.contains("[CONFIRMED]") || joined.contains("[REJECTED]"));
    }

    /** Closing stock of the test item from the stock_report tool. */
    @SuppressWarnings("unchecked")
    private double closingQty() throws Exception {
        Object raw = callToolRaw("stock_report", Map.of());
        List<Map<String, Object>> outer = (List<Map<String, Object>>) raw;
        Map<String, Object> bundle = (Map<String, Object>) outer.get(0);
        List<Map<String, Object>> rows = (List<Map<String, Object>>) bundle.get("stock");
        return rows.stream()
                .filter(r -> itemId.equals(r.get("itemId")))
                .mapToDouble(r -> ((Number) r.get("closing")).doubleValue())
                .findFirst().orElse(-1);
    }
}
