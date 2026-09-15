package com.invoicestudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.db.DatabaseManager;
import com.invoicestudio.db.LabelPrintHistoryDao;
import com.invoicestudio.db.VariableDao;
import com.invoicestudio.model.LabelConfig;
import com.invoicestudio.model.LabelPrintHistory;
import com.invoicestudio.model.Template;
import com.invoicestudio.model.TemplateElement;
import com.invoicestudio.model.ElementType;
import com.invoicestudio.model.VariableDef;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless-safe tests for Barcode Mode (label printing on strip stock):
 * strip geometry, print-queue expansion, JSON round-trips, barcode
 * symbologies and the label print history DAO.
 */
class LabelPrintLogicTest {

    // ------------------------------------------------------------------
    // Strip geometry
    // ------------------------------------------------------------------

    @Test
    void testRequiredStripWidthSingleColumn() {
        LabelConfig c = new LabelConfig();
        c.setColumns(1);
        c.setLabelWidth(50);
        c.setLabelHeight(25);
        c.setGapX(3);
        c.setMarginL(0);
        c.setMarginR(0);
        c.setStripWidth(60);
        assertEquals(50.0, LabelGeometryService.requiredStripWidth(c), 0.001);
        assertTrue(LabelGeometryService.fitsStrip(c));
        // 10 mm of slack on the strip is centered: label starts at 5 mm
        assertArrayEquals(new double[]{5.0}, LabelGeometryService.columnOffsets(c), 0.001);
    }

    @Test
    void testRequiredStripWidthMultiColumnWithGapsAndMargins() {
        LabelConfig c = new LabelConfig();
        c.setColumns(3);
        c.setLabelWidth(30);
        c.setGapX(4);
        c.setMarginL(2);
        c.setMarginR(2);
        // 2 + 2 + 3*30 + 2*4 = 102
        assertEquals(102.0, LabelGeometryService.requiredStripWidth(c), 0.001);
        c.setStripWidth(102);
        assertTrue(LabelGeometryService.fitsStrip(c));

        double[] xs = LabelGeometryService.columnOffsets(c);
        assertEquals(3, xs.length);
        assertEquals(2.0, xs[0], 0.001);
        assertEquals(36.0, xs[1], 0.001); // 2 + 30 + 4
        assertEquals(70.0, xs[2], 0.001); // 36 + 30 + 4
    }

    @Test
    void testOverfullStripDoesNotFit() {
        LabelConfig c = new LabelConfig();
        c.setColumns(2);
        c.setLabelWidth(50);
        c.setGapX(3);
        c.setStripWidth(100); // needs 103
        assertFalse(LabelGeometryService.fitsStrip(c));
    }

    @Test
    void testOrientationTransposesPhysicalCell() {
        LabelConfig c = new LabelConfig();
        c.setLabelWidth(25);   // design: portrait 25 x 50
        c.setLabelHeight(50);
        c.setOrientation("0");
        assertEquals(25.0, LabelGeometryService.physicalCellWidth(c), 0.001);
        assertEquals(50.0, LabelGeometryService.physicalCellHeight(c), 0.001);

        c.setOrientation("90");
        assertEquals(50.0, LabelGeometryService.physicalCellWidth(c), 0.001,
                "90° print rotation makes the design height the physical width");
        assertEquals(25.0, LabelGeometryService.physicalCellHeight(c), 0.001);

        c.setOrientation("270");
        assertEquals(50.0, LabelGeometryService.physicalCellWidth(c), 0.001);
        c.setOrientation("180");
        assertEquals(25.0, LabelGeometryService.physicalCellWidth(c), 0.001);

        // Strip math uses the physical cell
        c.setOrientation("90");
        c.setColumns(1);
        c.setGapX(0);
        c.setMarginL(0);
        c.setMarginR(0);
        c.setStripWidth(50);
        assertEquals(50.0, LabelGeometryService.requiredStripWidth(c), 0.001);
        assertEquals(25.0, LabelGeometryService.pageSizeMm(c)[1], 0.001,
                "Page height follows the physical (rotated) cell height");
    }

    @Test
    void testPageAndFeedPitch() {
        LabelConfig c = new LabelConfig();
        c.setLabelWidth(50);
        c.setLabelHeight(25);
        c.setGapY(3);
        c.setStripWidth(50);
        assertArrayEquals(new double[]{50.0, 25.0}, LabelGeometryService.pageSizeMm(c), 0.001);
        assertEquals(28.0, LabelGeometryService.feedPitchMm(c), 0.001);

        // Rotated: feed pitch follows the transposed height
        c.setOrientation("90");
        assertEquals(53.0, LabelGeometryService.feedPitchMm(c), 0.001);
    }

    // ------------------------------------------------------------------
    // Queue expansion
    // ------------------------------------------------------------------

    private static LabelGeometryService.PrintLine line(Map<String, String> values, int copies) {
        return new LabelGeometryService.PrintLine(new LinkedHashMap<>(values), copies);
    }

    @Test
    void testTotalLabelsAndPages() {
        List<LabelGeometryService.PrintLine> lines = List.of(
                line(Map.of("item", "A", "size", "S"), 20),
                line(Map.of("item", "B", "size", "M"), 10),
                line(Map.of("item", "C", "size", "S"), 11));
        assertEquals(41, LabelGeometryService.totalLabels(lines));

        LabelConfig oneCol = new LabelConfig();
        oneCol.setColumns(1);
        assertEquals(41, LabelGeometryService.totalPages(lines, oneCol));

        LabelConfig twoCol = new LabelConfig();
        twoCol.setColumns(2);
        assertEquals(21, LabelGeometryService.totalPages(lines, twoCol));
    }

    @Test
    void testSlotExpansionFillsRowsLeftToRight() {
        LabelConfig c = new LabelConfig();
        c.setColumns(2);
        c.setLabelWidth(40);
        c.setGapX(2);
        c.setStripWidth(84);

        List<LabelGeometryService.PrintLine> lines = List.of(
                line(Map.of("item", "A"), 3),
                line(Map.of("item", "B"), 1));

        List<LabelGeometryService.LabelSlot> slots = LabelGeometryService.expandSlots(lines, c);
        assertEquals(4, slots.size());

        // Page 0: A, A · page 1: A, B
        assertEquals(0, slots.get(0).pageIndex);
        assertEquals(0, slots.get(0).columnIndex);
        assertEquals("A", slots.get(0).values.get("item"));
        assertEquals(0, slots.get(1).pageIndex);
        assertEquals(1, slots.get(1).columnIndex);
        assertEquals(1, slots.get(2).pageIndex);
        assertEquals(0, slots.get(2).columnIndex);
        assertEquals("A", slots.get(2).values.get("item"));
        assertEquals(1, slots.get(3).pageIndex);
        assertEquals(1, slots.get(3).columnIndex);
        assertEquals("B", slots.get(3).values.get("item"));

        // Offsets come from the geometry service (2 mm slack centered → 1 mm lead)
        assertEquals(1.0, slots.get(0).xMm, 0.001);
        assertEquals(43.0, slots.get(1).xMm, 0.001);
    }

    @Test
    void testEmptyQueueProducesNothing() {
        assertTrue(LabelGeometryService.expandSlots(List.of(), new LabelConfig()).isEmpty());
        assertEquals(0, LabelGeometryService.totalPages(List.of(), new LabelConfig()));
    }

    @Test
    void testSummaryAndJson() {
        List<String> order = List.of("item", "length", "size");
        List<LabelGeometryService.PrintLine> lines = List.of(
                line(Map.of("item", "A", "length", "19", "size", "S"), 20),
                line(Map.of("item", "B", "length", "20", "size", "M"), 10));

        String summary = LabelGeometryService.summarize(lines, order);
        assertTrue(summary.contains("A · 19 · S) × 20"));
        assertTrue(summary.contains("B · 20 · M) × 10"));

        String json = LabelGeometryService.linesToJson(lines, order);
        assertTrue(json.startsWith("["));
        assertTrue(json.contains("\"item\":\"A\""));
        assertTrue(json.contains("\"copies\":20"));
        assertTrue(json.contains("\"copies\":10"));
    }

    // ------------------------------------------------------------------
    // JSON round-trips (backward compatibility)
    // ------------------------------------------------------------------

    @Test
    void testTemplateLabelModeRoundTrip() throws Exception {
        Template t = new Template();
        t.setId("tpl_t");
        t.setName("Label Test");
        t.setMode("label");
        LabelConfig cfg = new LabelConfig();
        cfg.setStripWidth(108);
        cfg.setColumns(2);
        cfg.setLabelWidth(50);
        cfg.setLabelHeight(25);
        cfg.setGapX(3);
        cfg.setCornerRadius(2.5);
        cfg.setOrientation("90");
        t.setLabelConfig(cfg);

        TemplateElement code = new TemplateElement();
        code.setId("el_1");
        code.setType(ElementType.BARCODE);
        code.setBarcodeData("{{barcode}}");
        code.setBarcodeFormat("EAN_13");
        t.getElements().add(code);

        ObjectMapper m = new ObjectMapper();
        String json = m.writeValueAsString(t);
        Template back = m.readValue(json, Template.class);

        assertTrue(back.isLabelMode());
        assertEquals(2, back.labelOrNew().getColumns());
        assertEquals(108.0, back.labelOrNew().getStripWidth(), 0.001);
        assertEquals("90", back.labelOrNew().getOrientation());
        assertEquals(2.5, back.labelOrNew().getCornerRadius(), 0.001);
        assertEquals("EAN_13", back.getElements().get(0).getBarcodeFormat());
    }

    @Test
    void testLegacyTemplateJsonStillLoads() throws Exception {
        // A pre-barcode-mode template JSON: no mode, no labelConfig, no barcodeFormat
        String legacyJson = "{\"id\":\"tpl_old\",\"name\":\"Old Bill\",\"page\":{\"sizeName\":\"A4\",\"width\":210,\"height\":297},\"elements\":[{\"id\":\"e1\",\"type\":\"BARCODE\",\"barcodeData\":\"{{invoice_no}}\",\"barcodeShowText\":true}]}";
        Template t = new ObjectMapper().readValue(legacyJson, Template.class);

        assertFalse(t.isLabelMode(), "Legacy templates must stay bill templates");
        assertNotNull(t.labelOrNew());
        assertEquals(1, t.labelOrNew().getColumns(), "Default label config is safe to touch");
        assertEquals("CODE_128", t.getElements().get(0).getBarcodeFormat(),
                "Missing symbology falls back to the legacy Code 128");
    }

    @Test
    void testLabelConfigSanitizeRepairsNonsense() {
        LabelConfig c = new LabelConfig();
        c.setStripWidth(-5);
        c.setColumns(99);
        c.setLabelWidth(0);
        c.setLabelHeight(-1);
        c.setGapX(-3);
        c.setOrientation("45");
        c.sanitize();
        assertEquals(100.0, c.getStripWidth(), 0.001);
        assertEquals(8, c.getColumns());
        assertEquals(50.0, c.getLabelWidth(), 0.001);
        assertEquals(25.0, c.getLabelHeight(), 0.001);
        assertEquals(0.0, c.getGapX(), 0.001);
        assertEquals("0", c.getOrientation());
    }

    // ------------------------------------------------------------------
    // Variables: barcode scope + choices
    // ------------------------------------------------------------------

    @Test
    void testVariableDefChoicesParsing() {
        VariableDef v = new VariableDef("size", "Size", "text", false);
        v.setScope("barcode");
        v.setChoices(" S , M , , L ,XL");
        assertEquals(List.of("S", "M", "L", "XL"), v.choicesList());
        v.setChoices(null);
        assertTrue(v.choicesList().isEmpty());
    }

    @Test
    void testBarcodeScopeVariablePersistence() {
        VariableDef v = new VariableDef("tst_size", "Test Size", "text", false);
        v.setScope("barcode");
        v.setChoices("S,M,L,XL,XXL");
        variableDao.saveVariable(v);

        List<VariableDef> barcodeVars = variableDao.getBarcodeScopeVariables();
        assertTrue(barcodeVars.stream().anyMatch(x -> "tst_size".equals(x.getKey())));
        VariableDef loaded = barcodeVars.stream()
                .filter(x -> "tst_size".equals(x.getKey())).findFirst().orElseThrow();
        assertEquals("barcode", loaded.getScope());
        assertEquals("S,M,L,XL,XXL", loaded.getChoices());
    }

    // ------------------------------------------------------------------
    // Design rotation 90° CW (Rotate Design into print orientation)
    // ------------------------------------------------------------------

    @Test
    void testRotateElement90CWFullCanvasElementStaysFull() {
        // Element covering the whole 50×25 canvas must cover the whole 25×50 canvas
        double[] g = LabelGeometryService.rotateElement90CW(0, 0, 50, 25, 25);
        assertArrayEquals(new double[]{0.0, 0.0, 25.0, 50.0}, g, 0.001);
    }

    @Test
    void testRotateElement90CWTopLeftLandsTopRight() {
        // (0,0,10,5) on a 50×25 canvas → top-right corner of the 25×50 canvas
        double[] g = LabelGeometryService.rotateElement90CW(0, 0, 10, 5, 25);
        assertArrayEquals(new double[]{20.0, 0.0, 5.0, 10.0}, g, 0.001);
    }

    @Test
    void testRotateElement90CWTwiceEquals180() {
        // Two CW rotations compose to a 180° rotation of the original canvas:
        // (x,y) → (W−x−w, H−y−h), size restored, rotation +180°.
        double x = 7.5, y = 3.25, w = 12.0, h = 4.5, rot = 30.0;
        double[] first = LabelGeometryService.rotateElement90CW(x, y, w, h, 25.0);  // 50×25 → 25×50
        double[] second = LabelGeometryService.rotateElement90CW(first[0], first[1], first[2], first[3], 50.0); // → 50×25
        assertArrayEquals(new double[]{50.0 - x - w, 25.0 - y - h, w, h}, second, 0.001);
        double rotTwice = LabelGeometryService.rotateElementRotation90CW(
                LabelGeometryService.rotateElementRotation90CW(rot));
        assertEquals(210.0, rotTwice, 0.001);
    }

    @Test
    void testRotateElementRotationWrapsAt360() {
        assertEquals(90.0, LabelGeometryService.rotateElementRotation90CW(0.0), 0.001);
        assertEquals(180.0, LabelGeometryService.rotateElementRotation90CW(90.0), 0.001);
        assertEquals(270.0, LabelGeometryService.rotateElementRotation90CW(180.0), 0.001);
        assertEquals(0.0, LabelGeometryService.rotateElementRotation90CW(270.0), 0.001);
    }

    @Test
    void testRotateWholeTemplateDesignMatchesPhysicalPrint() {
        // End-to-end: a 50×25 design with two elements becomes a 25×50 design,
        // all elements inside bounds, orientation zeroed — WYSIWYG after one click.
        Template t = new Template();
        t.setName("rot"); t.setMode("label");
        LabelConfig cfg = t.labelOrNew();
        cfg.setLabelWidth(50); cfg.setLabelHeight(25);
        cfg.setOrientation("90");
        List<TemplateElement> els = new ArrayList<>();
        TemplateElement text = new TemplateElement();
        text.setType(ElementType.TEXT);
        text.setX(2); text.setY(3); text.setW(20); text.setH(6); text.setRotation(0);
        els.add(text);
        TemplateElement code = new TemplateElement();
        code.setType(ElementType.BARCODE);
        code.setX(10); code.setY(15); code.setW(30); code.setH(8); code.setRotation(90);
        els.add(code);
        t.setElements(els);

        double W = cfg.getLabelWidth(), H = cfg.getLabelHeight();
        for (TemplateElement el : t.getElements()) {
            double[] g = LabelGeometryService.rotateElement90CW(el.getX(), el.getY(), el.getW(), el.getH(), H);
            el.setX(g[0]); el.setY(g[1]); el.setW(g[2]); el.setH(g[3]);
            el.setRotation(LabelGeometryService.rotateElementRotation90CW(el.getRotation()));
        }
        cfg.setLabelWidth(H);
        cfg.setLabelHeight(W);
        cfg.setOrientation("0");

        assertEquals(25.0, cfg.getLabelWidth(), 0.001);
        assertEquals(50.0, cfg.getLabelHeight(), 0.001);
        assertEquals("0", cfg.getOrientation());
        for (TemplateElement el : t.getElements()) {
            assertTrue(el.getX() >= -0.001 && el.getY() >= -0.001, "element origin inside rotated canvas");
            assertTrue(el.getX() + el.getW() <= cfg.getLabelWidth() + 0.001, "element fits rotated width");
            assertTrue(el.getY() + el.getH() <= cfg.getLabelHeight() + 0.001, "element fits rotated height");
        }
        // the text element (2,3,20,6) lands at (25-3-6=16, 2, 6, 20)
        assertEquals(16.0, els.get(0).getX(), 0.001);
        assertEquals(2.0, els.get(0).getY(), 0.001);
        assertEquals(6.0, els.get(0).getW(), 0.001);
        assertEquals(20.0, els.get(0).getH(), 0.001);
        // rotations spun +90°
        assertEquals(90.0, els.get(0).getRotation(), 0.001);
        assertEquals(180.0, els.get(1).getRotation(), 0.001);
    }

    // ------------------------------------------------------------------
    // Label print history DAO
    // ------------------------------------------------------------------

    @Test
    void testHistoryInsertListDeleteClear() {
        LabelPrintHistory h = new LabelPrintHistory();
        h.setTemplateId("tpl_t");
        h.setTemplateName("Garment Tag");
        h.setPrinterName("TSC TA210");
        h.setLabelWidth(50);
        h.setLabelHeight(25);
        h.setColumns(2);
        h.setPages(11);
        h.setLabels(21);
        h.setTotalCopies(21);
        h.setSummary("(A · 19 · S) × 20; (B · 19 · S) × 1");
        h.setLinesJson("[{\"item\":\"A\",\"copies\":20}]");
        dao.insert(h);
        dao.insert(h); // id stays stable → still one row with this id

        List<LabelPrintHistory> all = dao.getAll();
        LabelPrintHistory saved = all.stream()
                .filter(x -> "Garment Tag".equals(x.getTemplateName()))
                .findFirst().orElseThrow();
        assertEquals("TSC TA210", saved.getPrinterName());
        assertEquals(21, saved.getLabels());
        assertEquals(2, saved.getColumns());
        assertEquals(50.0, saved.getLabelWidth(), 0.001);
        assertNotNull(saved.getCreatedAt());
        assertFalse(saved.getId().isBlank());
        // user partitioning: recorded under the active test session user
        assertEquals("test_suite_user", saved.getUserId());

        assertTrue(dao.delete(saved.getId()));
        int before = dao.getAll().size();
        dao.clearAll();
        assertTrue(dao.getAll().size() <= before);
    }

    // ------------------------------------------------------------------
    // Barcode symbologies
    // ------------------------------------------------------------------

    @Test
    void testBarcodeFormatsRender() {
        BufferedImage code128 = BarcodeService.generateBarcodeBufferedImage("ABC-123", 200, 60, true, "CODE_128");
        assertEquals(200, code128.getWidth());
        assertEquals(60, code128.getHeight());

        BufferedImage ean = BarcodeService.generateBarcodeBufferedImage("5901234123457", 200, 60, false, "EAN_13");
        assertNotNull(ean);

        // Bad EAN payload → safe Code 128 fallback, never an exception
        BufferedImage fallback = BarcodeService.generateBarcodeBufferedImage("S", 200, 60, false, "EAN_13");
        assertNotNull(fallback);

        BufferedImage qr = BarcodeService.generateBarcodeBufferedImage("hello", 80, 80, true, "QR_CODE");
        assertNotNull(qr);

        // Legacy overload still works
        assertNotNull(BarcodeService.generateBarcodeBufferedImage("INV-1", 180, 50, true));
    }

    // ------------------------------------------------------------------
    // Printer form matching (orientation-safe WYSIWYG printing)
    // ------------------------------------------------------------------

    @Test
    void testChooseFormNativeExactMatch() {
        // Strip page 102 × 28.6 and a driver form registered natively 101.6 × 28.6
        double[][] forms = {{210.0, 297.0}, {101.6, 28.6}, {76.0, 50.0}};
        LabelPrintService.FormChoice c = LabelPrintService.chooseForm(forms, 102.0, 28.6);
        assertNotNull(c);
        assertEquals(1, c.paperIndex());
        assertFalse(c.transposed(), "native match must not transpose");
        assertEquals(101.6, c.fitWidthMm(), 0.001);
        assertEquals(28.6, c.fitHeightMm(), 0.001);
    }

    @Test
    void testChooseFormTransposedRegistration() {
        // Driver form registered SWAPPED (28.6 wide × 101.6 feed): the best
        // frame for the 102 × 28.6 strip page is the transposed one, and the
        // choice must say so — print then rotates the page node itself and
        // keeps the PageLayout portrait (JavaFX never rotates).
        double[][] forms = {{210.0, 297.0}, {28.6, 101.6}};
        LabelPrintService.FormChoice c = LabelPrintService.chooseForm(forms, 102.0, 28.6);
        assertNotNull(c);
        assertEquals(1, c.paperIndex());
        assertTrue(c.transposed(), "swapped registration must be detected");
        assertEquals(101.6, c.fitWidthMm(), 0.001);
        assertEquals(28.6, c.fitHeightMm(), 0.001);
    }

    @Test
    void testChooseFormSmallestContainingForm() {
        // No near-exact form (best drift 22 > 12): must pick the smallest
        // form that CONTAINS the page, not the closest.
        double[][] forms = {{200.0, 100.0}, {110.0, 32.0}, {120.0, 50.0}};
        LabelPrintService.FormChoice c = LabelPrintService.chooseForm(forms, 100.0, 20.0);
        assertNotNull(c);
        assertEquals(1, c.paperIndex(), "110×32 contains 100×20 with least waste");
        assertTrue(c.contains(100.0, 20.0));
    }

    @Test
    void testChooseFormClosestFallbackWhenNothingFits() {
        // Every form too small: fall back to the closest that exists.
        double[][] forms = {{60.0, 40.0}, {50.0, 25.0}};
        LabelPrintService.FormChoice c = LabelPrintService.chooseForm(forms, 100.0, 20.0);
        assertNotNull(c);
        // form 0 drift |60-100|+|40-20| = 60; form 1 drift |50-100|+|25-20| = 55 → closest is form 1
        assertEquals(1, c.paperIndex());
        assertFalse(c.transposed());
    }

    @Test
    void testChooseFormNativeWinsTiesAndSkipsInvalid() {
        // Square-ish tie: both frames score identically → native preferred;
        // zero/negative and malformed entries are skipped.
        double[][] forms = {{50.0, 50.0}, {0.0, 0.0}, {40.0}};
        LabelPrintService.FormChoice c = LabelPrintService.chooseForm(forms, 50.0, 50.0);
        assertNotNull(c);
        assertEquals(0, c.paperIndex());
        assertFalse(c.transposed());
    }

    @Test
    void testChooseFormNoUsableFormsReturnsNull() {
        assertNull(LabelPrintService.chooseForm(new double[][]{{0.0, 10.0}}, 100.0, 20.0));
        assertNull(LabelPrintService.chooseForm(null, 100.0, 20.0));
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private static DatabaseManager db;
    private static VariableDao variableDao;
    private static LabelPrintHistoryDao dao;
    private static final String TEST_DB_FILE = "test_label_studio.db";

    @BeforeAll
    static void setUp() {
        new File(TEST_DB_FILE).delete();
        com.invoicestudio.service.AuthSessionManager.setActiveSession(
                new com.invoicestudio.model.UserSession("test_suite_user", "test@invoicestudio.test",
                        "Test User", "id_tok", "ref_tok", System.currentTimeMillis() + 86400000L, true));
        db = DatabaseManager.initCustom("jdbc:sqlite:" + TEST_DB_FILE);
        variableDao = new VariableDao(db);
        dao = new LabelPrintHistoryDao(db);
    }

    @AfterAll
    static void tearDown() {
        com.invoicestudio.service.AuthSessionManager.clear();
        new File(TEST_DB_FILE).delete();
    }
}
