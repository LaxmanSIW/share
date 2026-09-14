package com.invoicestudio.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.Buyer;
import com.invoicestudio.model.UserSession;
import com.invoicestudio.service.AuthSessionManager;
import com.invoicestudio.ui.DataManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 16 acceptance tests: transport CRUD is now first-class in MCP —
 * update_transport (name/phone/vehicleNumber) and delete_transport with a
 * buyer-reference guard (refuses while buyers use it as their default;
 * force:true clears those references and deletes).
 */
class McpTransportCrudTest {

    private static final String TEST_DB = "test_mcp_transport.db";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static DataManager dm;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tool(String name, Map<String, Object> args) throws Exception {
        return (Map<String, Object>) McpToolRegistry.call(name, args);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toolList(String name) throws Exception {
        return (List<Map<String, Object>>) McpToolRegistry.call(name, Map.of());
    }

    @BeforeAll
    static void setUp() {
        new File(TEST_DB).delete();
        com.invoicestudio.db.DatabaseManager.initCustom("jdbc:sqlite:" + TEST_DB);
        AuthSessionManager.setActiveSession(new UserSession(
                "uid_transport_test", "transport@test.in", "Transport Test Traders",
                "tok", "ref", System.currentTimeMillis() + 3600_000L, true));
        dm = DataManager.init(com.invoicestudio.db.DatabaseManager.getInstance());
    }

    @AfterAll
    static void tearDown() throws Exception {
        AuthSessionManager.clear();
        new File(TEST_DB).delete();
        resetSingleton(com.invoicestudio.db.DatabaseManager.class, "instance");
        resetSingleton(DataManager.class, "instance");
    }

    private static void resetSingleton(Class<?> clazz, String fieldName) throws Exception {
        java.lang.reflect.Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(null, null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> args(Object... kv) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    private static String createTransport(String name, String phone, String vehicle) throws Exception {
        Map<String, Object> res = tool("create_transport", args("name", name, "phone", phone, "vehicleNumber", vehicle));
        assertEquals(Boolean.TRUE, res.get("ok"));
        assertFalse((Boolean) res.get("existed"), "transport " + name + " should be newly created");
        return (String) res.get("id");
    }

    @Test
    void testUpdateTransportChangesFieldsAndPersists() throws Exception {
        String id = createTransport("Sharma Roadlines", "9876543210", "MH12 AB 1234");

        Map<String, Object> res = tool("update_transport",
                args("id", id, "phone", "9999999999", "vehicleNumber", "MH14 XY 9999"));
        assertNotNull(res.get("operationId"), "update_transport must queue an operation: " + res);
        assertTrue(PendingOperations.approve((String) res.get("operationId")), "approved op must execute");

        Map<String, Object> row = toolList("list_transports").stream()
                .filter(t -> id.equals(t.get("id"))).findFirst().orElseThrow();
        assertEquals("9999999999", row.get("phone"), "phone must persist after update");
        assertEquals("MH14 XY 9999", row.get("vehicleNumber"), "vehicleNumber must persist after update");
        assertEquals("Sharma Roadlines", row.get("name"), "untouched field stays");
    }

    @Test
    void testUpdateTransportWithoutFieldsIsRejectedAtApproval() throws Exception {
        String id = createTransport("Verma Transport", "9000000000", "DL01 CV 4321");
        // confirmable() queues without executing — validation fires at approval
        Map<String, Object> res = tool("update_transport", args("id", id));
        String opId = (String) res.get("operationId");
        Exception ex = assertThrows(Exception.class, () -> PendingOperations.approve(opId));
        assertTrue(String.valueOf(ex.getMessage()).contains("Nothing to update"),
                "no-field update must be rejected with guidance, got: " + ex.getMessage());
    }

    @Test
    void testUpdateUnknownTransportFailsAtApproval() throws Exception {
        Map<String, Object> res = tool("update_transport", args("id", "trn_missing_1", "phone", "100"));
        assertNotNull(res.get("operationId"), "queue must accept and validate at approval");
        String opId = (String) res.get("operationId");
        Exception ex = assertThrows(Exception.class, () -> PendingOperations.approve(opId));
        assertTrue(String.valueOf(ex.getMessage()).contains("not found"),
                "unknown id must fail at APPROVAL time, got: " + ex.getMessage());
        assertEquals(0, PendingOperations.pending().size(), "failed op must be consumed");
    }

    @Test
    void testDeleteTransportRefusedWhileBuyersReferenceIt() throws Exception {
        String trId = createTransport("Patil Carriers", "9111111111", "MH02 KL 7777");
        // buyer auto-links the transport as its default
        Map<String, Object> buyerRes = tool("create_buyer",
                args("name", "RefGuard Traders", "transportName", "Patil Carriers"));
        assertTrue(Boolean.TRUE.equals(buyerRes.get("ok")), "create_buyer with transportName must succeed: " + buyerRes);

        Map<String, Object> res = tool("delete_transport", args("id", trId));
        String opId = (String) res.get("operationId");
        Exception ex = assertThrows(Exception.class, () -> PendingOperations.approve(opId),
                "delete must be refused while a buyer references the transport");
        String msg = String.valueOf(ex.getMessage());
        assertTrue(msg.contains("default transport"), "refusal must explain the guard: " + msg);
        assertTrue(msg.contains("force:true"), "refusal must point at the force escape hatch: " + msg);

        // transport still present
        String finalTrId = trId;
        assertTrue(toolList("list_transports").stream().anyMatch(t -> finalTrId.equals(t.get("id"))),
                "refused delete must not remove the transport");
    }

    @Test
    void testDeleteTransportForceClearsBuyerReferences() throws Exception {
        String trId = createTransport("Deshmukh Logistics", "9222222222", "MH05 GH 2222");
        tool("create_buyer", args("name", "ForceClear Traders", "transportName", "Deshmukh Logistics"));

        Buyer before = dm.getAllBuyers().stream()
                .filter(b -> "ForceClear Traders".equals(b.getName())).findFirst().orElseThrow();
        assertEquals(trId, before.getDefaultTransportId(), "buyer should reference the transport before force delete");

        Map<String, Object> res = tool("delete_transport", args("id", trId, "force", "true"));
        assertTrue(PendingOperations.approve((String) res.get("operationId")), "force delete must execute");

        assertTrue(toolList("list_transports").stream().noneMatch(t -> trId.equals(t.get("id"))),
                "transport must be gone after force delete");
        Buyer after = dm.getAllBuyers().stream()
                .filter(b -> "ForceClear Traders".equals(b.getName())).findFirst().orElseThrow();
        assertEquals("", after.getDefaultTransportId(), "buyer's default transport must be cleared, not dangling");
    }

    @Test
    void testDeleteUnreferencedTransportSucceeds() throws Exception {
        String trId = createTransport("Lone Star Movers", "9333333333", "TS09 AA 1111");
        Map<String, Object> res = tool("delete_transport", args("id", trId));
        assertTrue(PendingOperations.approve((String) res.get("operationId")));
        String finalTrId = trId;
        assertTrue(toolList("list_transports").stream().noneMatch(t -> finalTrId.equals(t.get("id"))));
    }

    @Test
    void testDeleteTransportDescribeSummaryMentionsForce() {
        String plain = McpToolRegistry.describeOpForTest("delete_transport", args("id", "trn_x"));
        assertTrue(plain.contains("refused"), "plain summary should mention the guard: " + plain);
        String forced = McpToolRegistry.describeOpForTest("delete_transport", args("id", "trn_x", "force", "true"));
        assertTrue(forced.contains("CLEAR"), "forced summary should spell out the reference clearing: " + forced);
    }
}
