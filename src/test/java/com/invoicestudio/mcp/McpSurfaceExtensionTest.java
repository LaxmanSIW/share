package com.invoicestudio.mcp;

import com.invoicestudio.model.ElementType;
import com.invoicestudio.model.Template;
import com.invoicestudio.model.TemplateElement;
import com.invoicestudio.model.UserSession;
import com.invoicestudio.service.AuthSessionManager;
import com.invoicestudio.ui.DataManager;
import com.invoicestudio.ui.ShortcutManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the MCP surface added with the shortcut system, batch invoice
 * export, label print state/queue and expense-account lifecycle tools.
 *
 * <p>Runs the tools directly through {@link McpToolRegistry#call} against an
 * isolated per-user database — same dispatch the HTTP layer uses, same
 * singleton-release contract as the other MCP suites.</p>
 */
class McpSurfaceExtensionTest {

    private static final String TEST_DB = "test_mcp_surface.db";
    private static DataManager dm;

    @SuppressWarnings("unchecked")
    private static Object tool(String name, Map<String, Object> args) throws Exception {
        return McpToolRegistry.call(name, args);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toolMap(String name, Map<String, Object> args) throws Exception {
        return (Map<String, Object>) tool(name, args);
    }

    @BeforeAll
    static void setUp() {
        new File(TEST_DB).delete();
        com.invoicestudio.db.DatabaseManager.initCustom("jdbc:sqlite:" + TEST_DB);
        AuthSessionManager.setActiveSession(new UserSession(
                "uid_surface_test", "surface@test.in", "Surface Test Traders",
                "tok", "ref", System.currentTimeMillis() + 3600_000L, true));
        dm = DataManager.init(com.invoicestudio.db.DatabaseManager.getInstance());
        // Headless tests: the app (AppShortcuts) is what registers actions at
        // startup. Register a probe so the shortcut tools have a surface —
        // register() is idempotent by id, so this never disturbs real ids.
        ShortcutManager.register("test.probe", "Test", "Test Probe", "Ctrl+Alt+9", () -> {}, true);
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

    // ------------------------------------------------------------------
    // Shortcuts
    // ------------------------------------------------------------------

    @Test
    void listShortcutsMirrorsTheRegistry() throws Exception {
        List<?> rows = (List<?>) tool("list_shortcuts", Map.of());
        assertFalse(rows.isEmpty(), "registered actions (incl. the probe) should be listed");
        assertTrue(rows.stream().anyMatch(o -> "test.probe".equals(((Map<?, ?>) o).get("actionId"))),
                "the probe action must appear in the listing");
        for (Object o : rows) {
            Map<?, ?> m = (Map<?, ?>) o;
            assertTrue(m.containsKey("binding"));
            assertTrue(m.containsKey("defaultBinding"));
        }
    }

    @Test
    void rebindShortcutValidatesLikeTheDialog() throws Exception {
        // Unknown action id → error pointing at list_shortcuts
        Exception e1 = assertThrows(Exception.class,
                () -> tool("rebind_shortcut", Map.of("actionId", "nope.missing", "combo", "Ctrl+9")));
        assertTrue(e1.getMessage().contains("list_shortcuts"));

        // Plain letter → TOO_SIMPLE rejection, nothing persisted
        Exception e2 = assertThrows(Exception.class,
                () -> tool("rebind_shortcut", Map.of("actionId", "test.probe", "combo", "Q")));
        assertTrue(e2.getMessage().toLowerCase().contains("modifier"));

        // The confirmation gate: direct call queues a PendingOp, does NOT bind yet
        Map<String, Object> res = toolMap("rebind_shortcut",
                Map.of("actionId", "test.probe", "combo", "Ctrl+Shift+I"));
        assertEquals(Boolean.TRUE, res.get("requiresConfirmation"));
        assertEquals("Ctrl+Alt+9", ShortcutManager.comboOf("test.probe"),
                "binding must not change before approval");
        String opId = String.valueOf(res.get("operationId"));
        PendingOperations.approve(opId);
        assertEquals("Ctrl+Shift+I", ShortcutManager.comboOf("test.probe"),
                "approval must apply the validated binding");

        // Restore the default through the gated reset path
        Map<String, Object> rst = toolMap("reset_shortcut", Map.of("actionId", "test.probe"));
        PendingOperations.approve(String.valueOf(rst.get("operationId")));
        assertEquals("Ctrl+Alt+9", ShortcutManager.comboOf("test.probe"));
    }

    @Test
    void resetAllShortcutIsConfirmGated() throws Exception {
        Map<String, Object> res = toolMap("reset_shortcut", Map.of("resetAll", true));
        assertEquals(Boolean.TRUE, res.get("requiresConfirmation"));
        assertTrue(String.valueOf(res.get("summary")).contains("EVERY"),
                "reset-all summary must say it resets every shortcut");
        PendingOperations.reject(String.valueOf(res.get("operationId")));
    }

    // ------------------------------------------------------------------
    // Batch invoice export
    // ------------------------------------------------------------------

    @Test
    void exportBillsPdfRendersAndReports() throws Exception {
        // A fresh test DB has no preset templates (those are seeded at app
        // startup) — create one so the exporter has something to render with.
        tool("create_template", Map.of("name", "SURF Export Tpl", "pageSize", "A4"));
        tool("create_buyer", Map.of("name", "SURF Export Buyer"));
        for (int i = 0; i < 2; i++) {
            tool("create_bill", Map.of(
                    "buyerName", "SURF Export Buyer",
                    "items", List.of(Map.of("desc", "Trouser lot " + i, "qty", 2, "rate", 250.0))));
        }
        File out = new File("target/test-mcp-export");
        Map<String, Object> res = toolMap("export_bills_pdf", Map.of(
                "query", "SURF Export Buyer", "dir", out.getAbsolutePath()));
        assertEquals(Boolean.TRUE, res.get("ok"));
        assertEquals(0, ((Number) res.get("failed")).intValue());
        assertEquals(2, ((Number) res.get("exported")).intValue());
        assertEquals(2, out.listFiles(f -> f.getName().endsWith(".pdf")).length,
                "one PDF per selected invoice");
    }

    @Test
    void exportBillsPdfWithoutSelectionErrors() {
        Exception e = assertThrows(Exception.class,
                () -> tool("export_bills_pdf", Map.of("query", "NO SUCH BUYER XY")));
        assertTrue(e.getMessage().contains("No invoices match"));
    }

    // ------------------------------------------------------------------
    // Label print state + queue
    // ------------------------------------------------------------------

    private static String makeLabelTemplate(String name) throws Exception {
        Map<String, Object> res = toolMap("create_template", Map.of("name", name, "pageSize", "A4"));
        String id = String.valueOf(res.get("id"));
        Template t = dm.templates().getTemplateById(id);
        t.setMode("label");
        TemplateElement e = new TemplateElement();
        e.setId("el_bar");
        e.setType(ElementType.BARCODE);
        e.setBinding("{{barcode}}");
        t.getElements().add(e);
        dm.templates().saveTemplate(t);
        return id;
    }

    @Test
    void labelPrintStateRoundTrips() throws Exception {
        String tid = makeLabelTemplate("SURF Label Tpl");
        Map<String, Object> empty = toolMap("get_label_print_state", Map.of("templateId", tid));
        assertEquals(Boolean.FALSE, empty.get("remembered"));

        com.invoicestudio.service.BulkPrintStateStore.save(tid,
                new com.invoicestudio.service.BulkPrintStateStore.TemplateState(
                        List.of(new com.invoicestudio.service.BulkPrintStateStore.Row(
                                Map.of("barcode", "8901234567890"), 3)),
                        "POS-80"));
        Map<String, Object> st = toolMap("get_label_print_state", Map.of("templateId", tid));
        assertEquals(Boolean.TRUE, st.get("remembered"));
        assertEquals("POS-80", st.get("printer"));
        Map<?, ?> row = (Map<?, ?>) ((List<?>) st.get("rows")).get(0);
        assertEquals(3, ((Number) row.get("copies")).intValue());
    }

    @Test
    void printLabelsRefusesBillTemplatesAndEmptyQueues() throws Exception {
        Map<String, Object> billTpl = toolMap("create_template", Map.of("name", "SURF Bill Tpl"));
        Exception e1 = assertThrows(Exception.class,
                () -> tool("print_labels", Map.of("templateId", billTpl.get("id"),
                        "lines", List.of(Map.of("variableValues", Map.of(), "copies", 1)))));
        assertTrue(e1.getMessage().contains("Barcode Mode"));

        String tid = makeLabelTemplate("SURF Label Tpl 2");
        Exception e2 = assertThrows(Exception.class,
                () -> tool("print_labels", Map.of("templateId", tid)));
        assertTrue(e2.getMessage().contains("lines is required"));
    }

    // ------------------------------------------------------------------
    // Expense account lifecycle
    // ------------------------------------------------------------------

    @Test
    void updateExpenseAccountRenamesPropagatesArchives() throws Exception {
        Map<String, Object> acc = toolMap("create_expense_account", Map.of("name", "SURF Old Name"));
        tool("record_expense", Map.of("category", "Office Rent", "amount", 500.0,
                "payee", "SURF Old Name"));

        Map<String, Object> res = toolMap("update_expense_account",
                Map.of("id", acc.get("id"), "name", "SURF New Name"));
        assertEquals(Boolean.TRUE, res.get("requiresConfirmation"));
        PendingOperations.approve(String.valueOf(res.get("operationId")));

        long renamed = dm.getAllExpenses().stream()
                .filter(e -> "SURF New Name".equals(e.getPayee())).count();
        assertEquals(1, renamed, "rename must propagate to vouchers");
        assertEquals("SURF New Name",
                dm.expenseAccounts().getAccountById(String.valueOf(acc.get("id"))).getName());

        Map<String, Object> arch = toolMap("update_expense_account",
                Map.of("id", acc.get("id"), "archived", true));
        PendingOperations.approve(String.valueOf(arch.get("operationId")));
        assertTrue(dm.expenseAccounts().getAccountById(String.valueOf(acc.get("id"))).isArchived());
    }

    @Test
    void deleteExpenseAccountIsGuardedWhenInUse() throws Exception {
        Map<String, Object> acc = toolMap("create_expense_account", Map.of("name", "SURF Guarded"));
        tool("record_expense", Map.of("category", "Transport", "amount", 90.0,
                "payee", "SURF Guarded"));

        Map<String, Object> res = toolMap("delete_expense_account", Map.of("id", acc.get("id")));
        assertEquals(Boolean.TRUE, res.get("requiresConfirmation"));
        Exception e = assertThrows(Exception.class,
                () -> PendingOperations.approve(String.valueOf(res.get("operationId"))),
                "execution must refuse while vouchers reference the name");
        assertTrue(e.getMessage().contains("voucher"));

        // The blocked op stays queued → reject it so it doesn't linger
        PendingOperations.reject(String.valueOf(res.get("operationId")));

        // An unused account deletes cleanly
        Map<String, Object> free = toolMap("create_expense_account", Map.of("name", "SURF Free"));
        Map<String, Object> del = toolMap("delete_expense_account", Map.of("id", free.get("id")));
        PendingOperations.approve(String.valueOf(del.get("operationId")));
        assertNull(dm.expenseAccounts().getAccountById(String.valueOf(free.get("id"))));
    }
}
