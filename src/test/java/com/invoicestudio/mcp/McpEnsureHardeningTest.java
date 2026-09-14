package com.invoicestudio.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.ItemCategory;
import com.invoicestudio.model.ItemRecord;
import com.invoicestudio.model.UserSession;
import com.invoicestudio.service.AuthSessionManager;
import com.invoicestudio.ui.DataManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 2 acceptance tests: for EVERY affected create tool, one case where the
 * dependency already exists and one where it is missing and gets auto-created —
 * plus race safety, partial-failure rollback and the DB unique-index backstop.
 *
 * Runs the tools directly through {@link McpToolRegistry#call} (same dispatch
 * the HTTP layer uses) against an isolated per-user database.
 */
class McpEnsureHardeningTest {

    private static final String TEST_DB = "test_mcp_ensure.db";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static DataManager dm;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tool(String name, Map<String, Object> args) throws Exception {
        return (Map<String, Object>) McpToolRegistry.call(name, args);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> toolList(String name, Map<String, Object> args) throws Exception {
        return (List<Map<String, Object>>) McpToolRegistry.call(name, args);
    }

    @BeforeAll
    static void setUp() {
        new File(TEST_DB).delete();
        com.invoicestudio.db.DatabaseManager.initCustom("jdbc:sqlite:" + TEST_DB);
        AuthSessionManager.setActiveSession(new UserSession(
                "uid_ensure_test", "ensure@test.in", "Ensure Test Traders",
                "tok", "ref", System.currentTimeMillis() + 3600_000L, true));
        dm = DataManager.init(com.invoicestudio.db.DatabaseManager.getInstance());
    }

    @AfterAll
    static void tearDown() throws Exception {
        AuthSessionManager.clear();
        new File(TEST_DB).delete();
        // release JVM-wide singletons — same contract as the other suites
        resetSingleton(com.invoicestudio.db.DatabaseManager.class, "instance");
        resetSingleton(DataManager.class, "instance");
    }

    private static void resetSingleton(Class<?> clazz, String fieldName) throws Exception {
        java.lang.reflect.Field f = clazz.getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(null, null);
    }

    private static int countRows(String table, String where, Object... params) throws Exception {
        try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + TEST_DB);
             PreparedStatement ps = c.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE " + where)) {
            for (int i = 0; i < params.length; i++) ps.setObject(i + 1, params[i]);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // ------------------------------------------------------------------
    // create_item → category (THE REPORTED BUG)
    // ------------------------------------------------------------------

    @Test
    void itemWithExistingCategoryLinksAndCreatesNothing() throws Exception {
        ItemCategory seeded = new ItemCategory();
        seeded.setId("cat_seed_01");
        seeded.setName("Seeds Brunch");
        dm.saveCategory(seeded);

        Map<String, Object> res = tool("create_item", Map.of(
                "name", "HARD Item KnownCat", "rate", 10.0, "gst", 0.0,
                "categoryName", "seeds brunch")); // case-insensitive match

        assertEquals(Boolean.TRUE, res.get("ok"));
        assertEquals(Boolean.FALSE, res.get("existed"));
        assertEquals("cat_seed_01", res.get("category") == null ? null
                : ((ItemRecord) dm.items().getAllItems().stream()
                    .filter(i -> i.getId().equals(res.get("id"))).findFirst().orElseThrow())
                    .getCategoryId());
        assertEquals(Boolean.TRUE, ((List<?>) res.get("autoCreated")).isEmpty(),
                "existing dependency must NOT be reported as auto-created");
        assertEquals(1, countRows("categories", "LOWER(name) = 'seeds brunch'"),
                "no duplicate category may appear");
    }

    @Test
    void itemWithMissingCategoryAutoCreatesAndLinks() throws Exception {
        Map<String, Object> res = tool("create_item", Map.of(
                "name", "HARD Item NewCat", "rate", 5.0, "gst", 18.0,
                "categoryName", "Hard Auto Category"));

        assertEquals(Boolean.TRUE, res.get("ok"));
        List<Map<String, Object>> auto = (List<Map<String, Object>>) (Object) res.get("autoCreated");
        assertEquals(1, auto.size(), "auto-creation must be visible in the response");
        assertEquals("category", auto.get(0).get("type"));
        assertEquals("Hard Auto Category", auto.get(0).get("name"));

        // dependency really exists and the item links to IT (no dangling id)
        String catId = (String) auto.get(0).get("id");
        ItemRecord saved = dm.items().getAllItems().stream()
                .filter(i -> i.getId().equals(res.get("id"))).findFirst().orElseThrow();
        assertEquals(catId, saved.getCategoryId());
        assertNotNull(dm.categories().getCategoryById(catId), "auto-created category must be persisted");
    }

    @Test
    void itemWithDanglingCategoryIdCreatesTheCategoryInstead() throws Exception {
        // THE reported bug: categoryId that does not exist used to be written
        // verbatim → dangling reference. Now the category is created WITH that id.
        Map<String, Object> res = tool("create_item", Map.of(
                "name", "HARD Item GhostCat", "categoryId", "cat_ghost_77"));

        assertEquals(Boolean.TRUE, res.get("ok"));
        assertNotNull(dm.categories().getCategoryById("cat_ghost_77"),
                "referenced category id must exist after the call");
        ItemRecord saved = dm.items().getAllItems().stream()
                .filter(i -> i.getId().equals(res.get("id"))).findFirst().orElseThrow();
        assertEquals("cat_ghost_77", saved.getCategoryId());
        List<Map<String, Object>> auto = (List<Map<String, Object>>) (Object) res.get("autoCreated");
        assertEquals(1, auto.size());
        assertEquals("cat_ghost_77", auto.get(0).get("id"));
    }

    @Test
    void itemCreateIsIdempotentByName() throws Exception {
        Map<String, Object> first = tool("create_item", Map.of("name", "HARD Dup Item", "rate", 1.0));
        Map<String, Object> second = tool("create_item", Map.of("name", "hard dup item", "rate", 99.0));

        assertEquals(first.get("id"), second.get("id"), "second call must return the SAME item");
        assertEquals(Boolean.TRUE, second.get("existed"));
        assertEquals(1, countRows("items", "LOWER(name) = 'hard dup item'"));
    }

    // ------------------------------------------------------------------
    // create_buyer / create_supplier / create_transport — idempotency
    // ------------------------------------------------------------------

    @Test
    void buyerCreateBothBranches() throws Exception {
        Map<String, Object> created = tool("create_buyer", Map.of("name", "HARD Buyer Bros", "phone", "111"));
        assertEquals(Boolean.FALSE, created.get("existed"));
        List<Map<String, Object>> auto = (List<Map<String, Object>>) (Object) created.get("autoCreated");
        assertEquals(1, auto.size());
        assertEquals("buyer", auto.get(0).get("type"));

        Map<String, Object> again = tool("create_buyer", Map.of("name", "hard buyer bros", "phone", "222"));
        assertEquals(created.get("id"), again.get("id"), "same name → same buyer");
        assertEquals(Boolean.TRUE, again.get("existed"));
        assertEquals("111", dm.buyers().getBuyerById((String) created.get("id")).getPhone(),
                "existing record must not be overwritten by the second call");
    }

    @Test
    void supplierCreateBothBranches() throws Exception {
        Map<String, Object> created = tool("create_supplier", Map.of("name", "HARD Supplier Ltd"));
        assertEquals(Boolean.FALSE, created.get("existed"));

        Map<String, Object> again = tool("create_supplier", Map.of("name", "HARD Supplier Ltd"));
        assertEquals(created.get("id"), again.get("id"));
        assertEquals(Boolean.TRUE, again.get("existed"));
    }

    @Test
    void transportCreateBothBranches() throws Exception {
        Map<String, Object> created = tool("create_transport", Map.of("name", "HARD Cargo"));
        assertEquals(Boolean.FALSE, created.get("existed"));
        Map<String, Object> again = tool("create_transport", Map.of("name", "hard cargo"));
        assertEquals(created.get("id"), again.get("id"));
        assertEquals(Boolean.TRUE, again.get("existed"));
    }

    // ------------------------------------------------------------------
    // create_bill → buyer + line items
    // ------------------------------------------------------------------

    @Test
    void billWithKnownBuyerCreatesNoDirectoryEntry() throws Exception {
        Map<String, Object> buyer = tool("create_buyer", Map.of("name", "HARD Bill Buyer"));
        int buyersBefore = countRows("buyers", "1=1");

        Map<String, Object> bill = tool("create_bill", Map.of(
                "buyerName", "HARD Bill Buyer",
                "items", List.of(Map.of("desc", "line", "qty", 1, "rate", 10))));

        assertEquals(Boolean.TRUE, bill.get("ok"));
        assertEquals(Boolean.TRUE, ((List<?>) bill.get("autoCreated")).isEmpty(),
                "known buyer must not be flagged auto-created");
        assertEquals(buyersBefore, countRows("buyers", "1=1"));
    }

    @Test
    void billWithUnknownBuyerAutoCreatesDirectoryEntry() throws Exception {
        Map<String, Object> bill = tool("create_bill", Map.of(
                "buyerName", "HARD Fresh Buyer Co",
                "items", List.of(Map.of("desc", "line", "qty", 1, "rate", 10))));

        assertEquals(Boolean.TRUE, bill.get("ok"));
        List<Map<String, Object>> auto = (List<Map<String, Object>>) (Object) bill.get("autoCreated");
        assertEquals(1, auto.size(), "buyer auto-creation must be visible");
        assertEquals("buyer", auto.get(0).get("type"));
        assertNotNull(dm.buyers().getBuyerById((String) auto.get(0).get("id")));

        // second bill for the same buyer: no further auto-creation
        Map<String, Object> bill2 = tool("create_bill", Map.of(
                "buyerName", "HARD Fresh Buyer Co",
                "items", List.of(Map.of("desc", "line", "qty", 1, "rate", 10))));
        assertEquals(Boolean.TRUE, ((List<?>) bill2.get("autoCreated")).isEmpty());
    }

    @Test
    void billLineWithUnknownItemIdAutoCreatesCatalogItem() throws Exception {
        Map<String, Object> bill = tool("create_bill", Map.of(
                "buyerName", "HARD Walkin Ghost",
                "items", List.of(Map.of("itemId", "item_ghost_42", "desc", "Ghost Widget",
                        "qty", 2, "rate", 50.0, "gst", 18.0))));

        assertEquals(Boolean.TRUE, bill.get("ok"));
        List<Map<String, Object>> auto = (List<Map<String, Object>>) (Object) bill.get("autoCreated");
        assertTrue(auto.stream().anyMatch(m -> "item".equals(m.get("type"))
                && "item_ghost_42".equals(m.get("id"))), "line item auto-creation must be visible");

        ItemRecord ghost = dm.items().getItemById("item_ghost_42");
        assertNotNull(ghost, "phantom itemId must now exist in the catalog");
        assertEquals("Ghost Widget", ghost.getName());
        assertEquals(50.0, ghost.getRate());
    }

    @Test
    void billFailureRollsBackAutoCreatedDependencies() throws Exception {
        int buyersBefore = countRows("buyers", "1=1");
        // items[] missing → primary create fails AFTER the buyer was auto-created
        assertThrows(Exception.class, () -> tool("create_bill", Map.of("buyerName", "HARD Doomed Buyer")));

        assertEquals(buyersBefore, countRows("buyers", "1=1"),
                "auto-created buyer must be rolled back when the bill save fails");
    }

    // ------------------------------------------------------------------
    // create_purchase → supplier + line items
    // ------------------------------------------------------------------

    @Test
    void purchaseWithUnknownSupplierAutoCreatesInsteadOfHardError() throws Exception {
        Map<String, Object> p = tool("create_purchase", Map.of(
                "supplierName", "HARD Never Seen Suppliers",
                "items", List.of(Map.of("desc", "goods", "qty", 3, "rate", 40.0))));

        assertEquals(Boolean.TRUE, p.get("ok"), "missing supplier must auto-create, not hard-error");
        List<Map<String, Object>> auto = (List<Map<String, Object>>) (Object) p.get("autoCreated");
        assertEquals(1, auto.size());
        assertEquals("supplier", auto.get(0).get("type"));
        assertNotNull(dm.suppliers().getSupplierById((String) auto.get(0).get("id")));

        // exists-branch: second purchase for the same supplier creates nothing new
        Map<String, Object> p2 = tool("create_purchase", Map.of(
                "supplierName", "HARD Never Seen Suppliers",
                "items", List.of(Map.of("desc", "more goods", "qty", 1, "rate", 10.0))));
        assertEquals(Boolean.TRUE, ((List<?>) p2.get("autoCreated")).isEmpty());
        assertEquals(1, countRows("suppliers", "LOWER(name) = 'hard never seen suppliers'"));
    }

    @Test
    void purchaseWithKnownSupplierLinksCleanly() throws Exception {
        Map<String, Object> seeded = tool("create_supplier", Map.of("name", "HARD Known Supplier"));
        Map<String, Object> p = tool("create_purchase", Map.of(
                "supplierId", seeded.get("id"),
                "items", List.of(Map.of("desc", "goods", "qty", 1, "rate", 5.0))));

        assertEquals(Boolean.TRUE, p.get("ok"));
        assertEquals(Boolean.TRUE, ((List<?>) p.get("autoCreated")).isEmpty());
    }

    // ------------------------------------------------------------------
    // create_variable / templates — never overwrite, never duplicate
    // ------------------------------------------------------------------

    @Test
    void variableCreateNeverOverwritesExistingKey() throws Exception {
        Map<String, Object> first = tool("create_variable",
                Map.of("key", "hard_test_var", "label", "Original"));
        assertEquals(Boolean.FALSE, first.get("existed"));

        Map<String, Object> second = tool("create_variable",
                Map.of("key", "hard_test_var", "label", "OVERWRITE ATTEMPT"));
        assertEquals(Boolean.TRUE, second.get("existed"));
        assertEquals("Original", dm.variables().getAllVariables().stream()
                        .filter(v -> v.getKey().equals("hard_test_var")).findFirst().orElseThrow()
                        .getLabel(),
                "existing variable must survive unchanged");
    }

    @Test
    void templateCreateAndDuplicateAreIdempotent() throws Exception {
        Map<String, Object> t1 = tool("create_template", Map.of("name", "HARD Tpl", "pageSize", "A4"));
        Map<String, Object> t2 = tool("create_template", Map.of("name", "HARD Tpl", "pageSize", "A5"));
        assertEquals(t1.get("id"), t2.get("id"));
        assertEquals(Boolean.TRUE, t2.get("existed"));

        Map<String, Object> d1 = tool("duplicate_template", Map.of("id", t1.get("id"), "newName", "HARD Tpl Copy"));
        Map<String, Object> d2 = tool("duplicate_template", Map.of("id", t1.get("id"), "newName", "HARD Tpl Copy"));
        assertEquals(d1.get("id"), d2.get("id"));
        assertEquals(Boolean.TRUE, d2.get("existed"));
    }

    // ------------------------------------------------------------------
    // Race safety + DB backstop
    // ------------------------------------------------------------------

    @Test
    void concurrentItemCreatesShareOneCategory() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<Object>> calls = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final String itemName = "HARD Race Item " + i;
            calls.add(() -> tool("create_item", Map.of(
                    "name", itemName, "rate", 1.0, "categoryName", "HARD Race Category")));
        }
        List<Future<Object>> futures = pool.invokeAll(calls);
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        for (Future<Object> f : futures) f.get(); // propagate failures

        assertEquals(1, countRows("categories", "LOWER(name) = 'hard race category'"),
                "8 concurrent creates must yield exactly ONE category row");
    }

    @Test
    void uniqueIndexBackstopsCategoryDuplicates() {
        // the migration must have installed a hard DB-level guard
        assertThrows(Exception.class, () -> {
            try (Connection c = DriverManager.getConnection("jdbc:sqlite:" + TEST_DB);
                 PreparedStatement ps = c.prepareStatement(
                         "INSERT INTO categories (id, user_id, name, created_at, updated_at) VALUES ('cat_dup_x', 'uid_ensure_test', 'HARD RACE CATEGORY', 't', 't')")) {
                ps.executeUpdate();
            }
        }, "second category with same (user_id, case-insensitive name) must violate the unique index");
    }

    // ------------------------------------------------------------------
    // update_item → category reassignment (THE FOLLOW-UP GAP)
    // create_item is idempotent by name, so before this fix there was NO
    // way at all to move an item to another category via MCP.
    // ------------------------------------------------------------------

    private ItemRecord itemByName(String name) {
        return dm.items().getAllItems().stream()
                .filter(i -> i.getName().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void updateItemMovesItemToExistingCategory() throws Exception {
        tool("create_item", Map.of("name", "HARD Mover A", "rate", 20.0,
                "gst", 18.0, "categoryName", "HARD Old Home"));
        String itemId = itemByName("HARD Mover A").getId();

        Map<String, Object> res = tool("update_item", Map.of(
                "id", itemId, "categoryName", "HARD New Home"));
        assertEquals(Boolean.TRUE, res.get("requiresConfirmation"));
        String summary = String.valueOf(res.get("summary")).toLowerCase();
        assertTrue(summary.contains("move category"),
                "confirm summary must surface the category move, got: " + res.get("summary"));

        assertTrue(PendingOperations.approve((String) res.get("operationId")),
                "approved op must execute");
        ItemRecord after = dm.items().getItemById(itemId);
        assertEquals("HARD New Home", after.getCategoryName(), "item must be moved");
        assertNotNull(after.getCategoryId());
        assertFalse(after.getCategoryId().isBlank(), "category id must be linked, not dangling");
        assertEquals(1, countRows("categories", "LOWER(name) = 'hard new home'"));
    }

    @Test
    void updateItemAutoCreatesMissingCategoryAndKeepsOtherFields() throws Exception {
        tool("create_item", Map.of("name", "HARD Mover B", "rate", 30.0, "gst", 5.0));
        String itemId = itemByName("HARD Mover B").getId();

        Map<String, Object> res = tool("update_item", Map.of(
                "id", itemId, "categoryName", "HARD Fresh Category", "rate", 42.0));
        assertTrue(PendingOperations.approve((String) res.get("operationId")));

        ItemRecord after = dm.items().getItemById(itemId);
        assertEquals("HARD Fresh Category", after.getCategoryName());
        assertEquals(42.0, after.getRate(), 1e-9, "other update fields must still apply");
        assertEquals(5.0, after.getGst(), 1e-9, "untouched fields must be preserved");
        assertEquals(1, countRows("categories", "LOWER(name) = 'hard fresh category'"),
                "missing target category must be auto-created exactly once");
    }

    @Test
    void createItemStillNeverUpdatesExistingItemCategory() throws Exception {
        tool("create_item", Map.of("name", "HARD Sticky", "categoryName", "HARD Keep Me"));
        String catIdBefore = itemByName("HARD Sticky").getCategoryId();

        // the AI's failed workaround from the field report: re-create under a new
        // category — must stay a no-op (existed=true), never silently move it
        Map<String, Object> res = tool("create_item", Map.of(
                "name", "HARD Sticky", "categoryName", "HARD Other Place"));
        assertEquals(Boolean.TRUE, res.get("existed"));
        assertTrue(String.valueOf(res.get("note")).contains("update_item"),
                "note must point the AI at the correct tool");

        ItemRecord it = itemByName("HARD Sticky");
        assertEquals("HARD Keep Me", it.getCategoryName(), "category must NOT change via create");
        assertEquals(catIdBefore, it.getCategoryId());
    }

    @Test
    void updateItemWithUnknownIdFailsAtApprovalNotQueueTime() throws Exception {
        // confirmable() queues first and executes on approval — existence is
        // re-validated at EXECUTION time (same contract as every update tool)
        Map<String, Object> res = tool("update_item",
                Map.of("id", "item_does_not_exist", "categoryName", "HARD Nowhere"));
        assertEquals(Boolean.TRUE, res.get("requiresConfirmation"));
        String opId = (String) res.get("operationId");

        assertThrows(Exception.class, () -> PendingOperations.approve(opId),
                "approval of an unknown item id must fail loudly, not silently");
        assertEquals(0, PendingOperations.pending().size(),
                "failed op must be consumed, nothing half-done left pending");
        assertEquals(0, countRows("categories", "LOWER(name) = 'hard nowhere'"),
                "target category must NOT be auto-created when the update aborts");
    }
}
