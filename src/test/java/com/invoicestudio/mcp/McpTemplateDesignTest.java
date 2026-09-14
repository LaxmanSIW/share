package com.invoicestudio.mcp;

import com.invoicestudio.db.DatabaseManager;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the template-design toolset added on top of the MCP hardening:
 * get_template_design_guide (full design vocabulary exposure),
 * render_template_preview (saved + draft modes, bounds/binding warnings,
 * PNG output via the real print engine) and the lossless styling
 * round-trip through create_template / get_template.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpTemplateDesignTest {

    private static final String TEST_DB = "test_mcp_tdesign.db";

    private static DataManager dm;
    private static String templateId;

    @BeforeAll
    static void setUp() {
        new File(TEST_DB).delete();
        DatabaseManager.initCustom("jdbc:sqlite:" + TEST_DB);
        AuthSessionManager.setActiveSession(new UserSession(
                "uid_tdesign", "design@test.in", "Design Test Traders",
                "tok", "ref", System.currentTimeMillis() + 3600_000L, true));
        dm = DataManager.init(DatabaseManager.getInstance());
    }

    @AfterAll
    static void tearDown() throws Exception {
        PendingOperations.clearAll();
        new File(TEST_DB).delete();

        // DataManager/DatabaseManager are JVM-wide singletons shared with the
        // other suite classes. This class initialized them, so it must release
        // them — otherwise the next suite's DataManager.init(db) is a no-op and
        // its assertions read this suite's database (same contract as
        // McpServerTest / McpEnsureHardeningTest).
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
    // Design guide exposure
    // ------------------------------------------------------------------

    @Test
    @Order(1)
    void designGuideDocumentsEveryElementType() throws Exception {
        Object out = McpToolRegistry.call("get_template_design_guide", Map.of());
        assertTrue(out instanceof String, "guide should be a text document");
        String guide = (String) out;
        for (String[] type : new String[][]{
                {"TEXT", "TABLE", "IMAGE", "LINE", "RECT", "PAGENO", "QRCODE", "BARCODE"},
                {"CIRCLE", "ELLIPSE", "POLYLINE", "POLYGON", "ARC", "PATH", "STAR", "ARROW"},
                {"DIVIDER", "FREEHAND", "WATERMARK", "SVG", "ICON", "GROUP", "COMPONENT"}}) {
            for (String t : type) {
                assertTrue(guide.contains("`" + t + "`"), "guide must document element type " + t);
            }
        }
        // Workflow + preview tool + key property groups + variable bindings
        assertTrue(guide.contains("render_template_preview"), "guide must teach the preview loop");
        assertTrue(guide.contains("duplicate_template"), "guide must teach the duplicate-first workflow");
        assertTrue(guide.contains("get_template"), "guide must reference get_template anatomy reads");
        assertTrue(guide.contains("useBusinessLogo"), "guide must document the logo shortcut");
        assertTrue(guide.contains("data:image/png;base64"), "guide must document image embedding");
        assertTrue(guide.contains("{{invoice_no}}"), "guide must document bindings");
        assertTrue(guide.contains("grand_total"), "guide must document totals bindings");
        assertTrue(guide.contains("qrSource"), "guide must document QR config");
        assertTrue(guide.contains("columns"), "guide must document table columns");
        assertTrue(guide.contains("THERMAL_80"), "guide must document page sizes");
    }

    // ------------------------------------------------------------------
    // Lossless styling round-trip: create → get
    // ------------------------------------------------------------------

    @Test
    @Order(2)
    void fullStylingRoundTripsThroughCreateAndGet() throws Exception {
        Map<String, Object> title = el("Title Band", "TEXT");
        title.put("x", 20); title.put("y", 12); title.put("w", 170); title.put("h", 12);
        title.put("text", "TAX INVOICE");
        title.put("fontFamily", "Georgia");
        title.put("fontSize", 16);
        title.put("fontWeight", 700);
        title.put("color", "#0B3D2E");
        title.put("align", "center");
        title.put("letterSpacing", 1.5);

        Map<String, Object> band = el("Header Band", "RECT");
        band.put("x", 0); band.put("y", 0); band.put("w", 210); band.put("h", 30);
        band.put("bg", "#0B3D2E");
        band.put("fillType", "linear");
        band.put("gradientStartColor", "#0B3D2E");
        band.put("gradientEndColor", "#06B6D4");
        band.put("gradientAngle", 90);
        band.put("borderRadius", 0);
        band.put("borderColor", "#000000");
        band.put("borderWidth", 0.3);

        Map<String, Object> logo = el("Logo", "IMAGE");
        logo.put("x", 6); logo.put("y", 6); logo.put("w", 18); logo.put("h", 18);
        logo.put("useBusinessLogo", true);
        logo.put("objectFit", "contain");

        Map<String, Object> col1 = new LinkedHashMap<>();
        col1.put("key", "desc"); col1.put("label", "Item"); col1.put("width", 40); col1.put("align", "left");
        Map<String, Object> col2 = new LinkedHashMap<>();
        col2.put("key", "qty"); col2.put("label", "Qty"); col2.put("width", 12); col2.put("align", "center");
        Map<String, Object> col3 = new LinkedHashMap<>();
        col3.put("key", "amount"); col3.put("label", "Amount"); col3.put("width", 20); col3.put("align", "right");
        Map<String, Object> table = el("Items", "TABLE");
        table.put("x", 10); table.put("y", 70); table.put("w", 190); table.put("h", 40);
        table.put("columns", List.of(col1, col2, col3));
        table.put("headerBg", "#EFE9DB");
        table.put("headerColor", "#1A1A1A");
        table.put("rowHeight", 6.5);
        table.put("borderStyle", "grid");
        table.put("showZebra", true);
        table.put("zebraColor", "#F8F8F8");
        table.put("tableBorderColor", "#C8C8C8");

        Map<String, Object> qr = el("Pay QR", "QRCODE");
        qr.put("x", 160); qr.put("y", 240); qr.put("w", 26); qr.put("h", 26);
        qr.put("qrSource", "upi_amount");
        qr.put("qrColor", "#111111");

        Map<String, Object> wm = el("Stamp", "WATERMARK");
        wm.put("x", 30); wm.put("y", 120); wm.put("w", 150); wm.put("h", 40);
        wm.put("watermarkText", "PAID");
        wm.put("watermarkOpacity", 0.12);
        wm.put("watermarkAngle", -30);

        Map<String, Object> args = new HashMap<>();
        args.put("name", "Design Test Template " + System.nanoTime());
        args.put("pageSize", "A4");
        args.put("elements", List.of(title, band, logo, table, qr, wm));

        @SuppressWarnings("unchecked")
        Map<String, Object> created = (Map<String, Object>) McpToolRegistry.call("create_template", args);
        assertEquals(true, created.get("ok"));
        assertEquals(false, created.get("existed"));
        assertNull(created.get("warnings"), "no downgrades expected for valid types");
        templateId = String.valueOf(created.get("id"));

        @SuppressWarnings("unchecked")
        Map<String, Object> full = (Map<String, Object>) McpToolRegistry.call("get_template",
                Map.of("id", templateId));
        assertEquals("A4", full.get("pageSize"));
        assertEquals(true, full.get("autoHeight") instanceof Boolean);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> els = (List<Map<String, Object>>) full.get("elements");
        assertEquals(6, els.size(), "all elements must round-trip");

        Map<String, Object> backTitle = byName(els, "Title Band");
        assertEquals("TEXT", backTitle.get("type"));
        assertEquals("Georgia", backTitle.get("fontFamily"), "fontFamily must survive the round-trip");
        assertEquals(16, ((Number) backTitle.get("fontSize")).intValue());
        assertEquals(700, ((Number) backTitle.get("fontWeight")).intValue());
        assertEquals("#0B3D2E", backTitle.get("color"));
        assertEquals("center", backTitle.get("align"));
        assertEquals(1.5, ((Number) backTitle.get("letterSpacing")).doubleValue(), 1e-9);

        Map<String, Object> backBand = byName(els, "Header Band");
        assertEquals("linear", backBand.get("fillType"));
        assertEquals("#06B6D4", backBand.get("gradientEndColor"));
        assertEquals(90.0, ((Number) backBand.get("gradientAngle")).doubleValue(), 1e-9);

        Map<String, Object> backLogo = byName(els, "Logo");
        assertEquals(true, backLogo.get("useBusinessLogo"));
        assertEquals("contain", backLogo.get("objectFit"));

        Map<String, Object> backTable = byName(els, "Items");
        assertEquals("grid", backTable.get("borderStyle"));
        assertEquals(6.5, ((Number) backTable.get("rowHeight")).doubleValue(), 1e-9);
        assertEquals("#F8F8F8", backTable.get("zebraColor"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> cols = (List<Map<String, Object>>) backTable.get("columns");
        assertEquals(3, cols.size(), "table columns must round-trip");
        assertEquals("amount", cols.get(2).get("key"));
        assertEquals("right", cols.get(2).get("align"));

        Map<String, Object> backQr = byName(els, "Pay QR");
        assertEquals("upi_amount", backQr.get("qrSource"));
        assertEquals("#111111", backQr.get("qrColor"));

        Map<String, Object> backWm = byName(els, "Stamp");
        assertEquals("PAID", backWm.get("watermarkText"));
        assertEquals(0.12, ((Number) backWm.get("watermarkOpacity")).doubleValue(), 1e-9);
    }

    // ------------------------------------------------------------------
    // Preview: saved template
    // ------------------------------------------------------------------

    @Test
    @Order(3)
    void previewRendersSavedTemplateToPng() throws Exception {
        Object out = McpToolRegistry.call("render_template_preview",
                Map.of("id", templateId, "dpi", 96));
        assertTrue(out instanceof McpImageResult, "preview must return an image result");
        McpImageResult img = (McpImageResult) out;
        assertEquals("image/png", img.getMimeType());
        assertTrue(img.getData().length > 1000, "PNG payload must be non-trivial");
        // PNG magic bytes: 89 50 4E 47
        assertEquals(0x89, img.getData()[0] & 0xFF);
        assertEquals(0x50, img.getData()[1] & 0xFF);
        assertEquals(0x4E, img.getData()[2] & 0xFF);
        assertEquals(0x47, img.getData()[3] & 0xFF);

        Map<String, Object> meta = img.getMeta();
        assertEquals("saved", meta.get("mode"));
        assertEquals("A4", meta.get("pageSize"));
        assertEquals(210.0, ((Number) meta.get("widthMm")).doubleValue(), 1e-6);
        assertEquals(6, meta.get("elementCount"));
        assertTrue(meta.get("warnings") instanceof List, "meta must carry a warnings list");
    }

    // ------------------------------------------------------------------
    // Preview: draft mode (nothing persisted) + warnings
    // ------------------------------------------------------------------

    @Test
    @Order(4)
    void previewDraftReportsWarningsAndPersistsNothing() throws Exception {
        Map<String, Object> ok = el("Fine", "TEXT");
        ok.put("x", 10); ok.put("y", 10); ok.put("w", 40); ok.put("h", 8);
        ok.put("text", "Invoice {{invoice_no}}");

        Map<String, Object> off = el("Way Outside", "TEXT");
        off.put("x", 205); off.put("y", 290); off.put("w", 60); off.put("h", 10); // beyond A4 210x297

        Map<String, Object> badVar = el("Typo Bind", "TEXT");
        badVar.put("x", 10); badVar.put("y", 30); badVar.put("w", 60); badVar.put("h", 8);
        badVar.put("text", "Hello {{not_a_real_var}}");

        String draftName = "Unsaved Draft " + System.nanoTime();
        Map<String, Object> draft = new HashMap<>();
        draft.put("name", draftName);
        draft.put("pageSize", "A4");
        draft.put("elements", List.of(ok, off, badVar));

        int templatesBefore = dm.templates().getAllTemplates().size();

        Object out = McpToolRegistry.call("render_template_preview", Map.of("draft", draft, "dpi", 72));
        assertTrue(out instanceof McpImageResult);
        McpImageResult img = (McpImageResult) out;
        assertTrue(img.getData().length > 1000);

        Map<String, Object> meta = img.getMeta();
        assertEquals("draft", meta.get("mode"));

        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) meta.get("warnings");
        assertNotNull(warnings);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("Way Outside") && w.contains("outside the page")),
                "must flag the out-of-bounds element, got: " + warnings);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("not_a_real_var") && w.contains("unknown variable")),
                "must flag the unresolved binding, got: " + warnings);
        assertTrue(warnings.stream().noneMatch(w -> w.contains("invoice_no")),
                "a known variable must NOT be flagged, got: " + warnings);

        assertEquals(templatesBefore, dm.templates().getAllTemplates().size(),
                "draft preview must not create a template");
        assertTrue(McpEnsure.findTemplateByName(dm, draftName) == null, "draft name must not be persisted");
    }

    @Test
    @Order(5)
    void previewUnknownTemplateFailsWithClearError() {
        assertThrows(IllegalArgumentException.class,
                () -> McpToolRegistry.call("render_template_preview", Map.of("id", "tpl_does_not_exist")));
    }

    // ------------------------------------------------------------------
    // Thermal page geometry (pageFor fix: named sizes carry real dimensions)
    // ------------------------------------------------------------------

    @Test
    @Order(6)
    void thermalDraftUsesRollGeometryAndAutoHeight() throws Exception {
        Map<String, Object> head = el("Shop Name", "TEXT");
        head.put("x", 3); head.put("y", 2); head.put("w", 74); head.put("h", 8);
        head.put("text", "MY SHOP");
        head.put("fontSize", 12);
        head.put("fontWeight", 700);
        head.put("align", "center");

        Map<String, Object> table = el("Lines", "TABLE");
        table.put("x", 2); table.put("y", 20); table.put("w", 76); table.put("h", 20);
        Map<String, Object> c1 = new LinkedHashMap<>();
        c1.put("key", "desc"); c1.put("label", "Item"); c1.put("width", 60); c1.put("align", "left");
        Map<String, Object> c2 = new LinkedHashMap<>();
        c2.put("key", "amount"); c2.put("label", "Amt"); c2.put("width", 40); c2.put("align", "right");
        table.put("columns", List.of(c1, c2));
        table.put("rowHeight", 5);

        Map<String, Object> qr = el("Pay", "QRCODE");
        qr.put("x", 28); qr.put("y", 60); qr.put("w", 24); qr.put("h", 24);
        qr.put("qrSource", "upi_amount");

        Map<String, Object> draft = new HashMap<>();
        draft.put("name", "Thermal Draft");
        draft.put("pageSize", "THERMAL_80");
        draft.put("elements", List.of(head, table, qr));

        Object out = McpToolRegistry.call("render_template_preview", Map.of("draft", draft));
        assertTrue(out instanceof McpImageResult);
        McpImageResult img = (McpImageResult) out;
        Map<String, Object> meta = img.getMeta();
        assertEquals("Thermal 80", meta.get("pageSize"), "size name renders as its human label");
        assertEquals(80.0, ((Number) meta.get("widthMm")).doubleValue(), 1e-6,
                "thermal width must be 80mm, not A4's 210mm");
        assertTrue(((Number) meta.get("heightMm")).doubleValue() < 297,
                "auto-height roll must not inherit A4 height");
        assertTrue(img.getData().length > 1000);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private static Map<String, Object> el(String name, String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("type", type);
        return m;
    }

    private static Map<String, Object> byName(List<Map<String, Object>> els, String name) {
        for (Map<String, Object> e : els) {
            if (name.equals(e.get("name"))) return e;
        }
        return null;
    }
}
