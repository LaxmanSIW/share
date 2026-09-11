package com.invoicestudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.invoicestudio.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TemplateDesignerV4Test {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testUnitConverterMath() {
        // MM <-> PX (96 DPI: 1 inch = 25.4 mm = 96 px => 1 mm = 96 / 25.4 = 3.7795 px)
        assertEquals(96.0, UnitConverter.mmToPx(25.4), 0.001);
        assertEquals(25.4, UnitConverter.pxToMm(96.0), 0.001);

        // MM <-> PT (72 DPI: 1 inch = 25.4 mm = 72 pt => 1 mm = 72 / 25.4 = 2.8346 pt)
        assertEquals(72.0, UnitConverter.mmToPt(25.4), 0.001);
        assertEquals(25.4, UnitConverter.ptToMm(72.0), 0.001);

        // toMm / fromMm across units
        assertEquals(10.0, UnitConverter.toMm(1.0, UnitConverter.Unit.CM), 0.001);
        assertEquals(1.0, UnitConverter.fromMm(10.0, UnitConverter.Unit.CM), 0.001);

        assertEquals(25.4, UnitConverter.toMm(1.0, UnitConverter.Unit.IN), 0.001);
        assertEquals(25.4, UnitConverter.toMm(1.0, UnitConverter.Unit.INCH), 0.001);
        assertEquals(1.0, UnitConverter.fromMm(25.4, UnitConverter.Unit.IN), 0.001);

        assertEquals(15.0, UnitConverter.toMm(15.0, UnitConverter.Unit.MM), 0.001);
        assertEquals(15.0, UnitConverter.fromMm(15.0, UnitConverter.Unit.MM), 0.001);

        // Formatting
        assertEquals("10.0 mm", UnitConverter.format(10.0, UnitConverter.Unit.MM));
        assertEquals("2.50 in", UnitConverter.format(2.5, UnitConverter.Unit.IN));
        assertEquals("100 px", UnitConverter.format(100.0, UnitConverter.Unit.PX));
    }

    @Test
    void testComponentPresetsGeneration() {
        for (ComponentPreset.PresetType type : ComponentPreset.PresetType.values()) {
            List<TemplateElement> elements = ComponentPreset.createComponent(type, 10.0, 20.0);
            assertNotNull(elements, "Elements list for " + type + " should not be null");
            assertFalse(elements.isEmpty(), "Elements list for " + type + " should not be empty");

            for (TemplateElement el : elements) {
                assertNotNull(el.getId(), "Element ID should be populated");
                assertNotNull(el.getType(), "Element type should not be null");
                assertTrue(el.getW() > 0, "Element width should be > 0");
                assertTrue(el.getH() > 0, "Element height should be > 0");
                assertNotNull(el.getComponentType(), "ComponentType metadata should be set");
            }
        }
    }

    @Test
    void testUniversalTemplateElementSerialization() throws Exception {
        Template template = new Template();
        template.setId("tpl_v4_test");
        template.setName("Universal 2D Template Test");
        template.setElements(new ArrayList<>());

        // 1. Star with Gradient & Drop Shadow
        TemplateElement star = new TemplateElement();
        star.setId("star_1");
        star.setType(ElementType.STAR);
        star.setName("Gold Rating Star");
        star.setX(15); star.setY(20); star.setW(30); star.setH(30);
        star.setStarPoints(5);
        star.setInnerRadius(6.0);
        star.setOuterRadius(14.0);
        star.setFillType("linear");
        star.setGradientStartColor("#F59E0B");
        star.setGradientEndColor("#D97706");
        star.setGradientAngle(45.0);
        star.setShadowEnabled(true);
        star.setShadowColor("#00000044");
        star.setShadowBlur(5.0);
        star.setShadowOffsetX(2.0);
        star.setShadowOffsetY(2.0);
        star.setRotation(15.0);
        template.getElements().add(star);

        // 2. Arrow with custom stroke & flips
        TemplateElement arrow = new TemplateElement();
        arrow.setId("arrow_1");
        arrow.setType(ElementType.ARROW);
        arrow.setX(60); arrow.setY(20); arrow.setW(50); arrow.setH(15);
        arrow.setArrowHeadLength(7.0);
        arrow.setArrowHeadWidth(8.0);
        arrow.setArrowHeadStyle("STEALTH");
        arrow.setStrokeEnabled(true);
        arrow.setBorderColor("#2563EB");
        arrow.setBorderWidth(1.2);
        arrow.setDashPattern("5,5");
        arrow.setFlipHorizontal(true);
        arrow.setScaleX(1.1);
        arrow.setScaleY(0.9);
        template.getElements().add(arrow);

        // 3. Custom Path & Watermark
        TemplateElement path = new TemplateElement();
        path.setId("path_1");
        path.setType(ElementType.PATH);
        path.setPathData("M 10,30 A 20,20 0 0,1 50,30 A 20,20 0 0,1 90,30 Q 90,60 50,90 Q 10,60 10,30 Z");
        path.setBg("#EF4444");
        template.getElements().add(path);

        TemplateElement watermark = new TemplateElement();
        watermark.setId("wm_1");
        watermark.setType(ElementType.WATERMARK);
        watermark.setWatermarkText("ORIGINAL COPY");
        watermark.setWatermarkOpacity(0.15);
        watermark.setWatermarkAngle(-45.0);
        watermark.setBinding("invoice.status");
        watermark.setVisibleCondition("invoice.total > 0");
        template.getElements().add(watermark);

        // Roundtrip serialization
        String json = mapper.writeValueAsString(template);
        Template deserialized = mapper.readValue(json, Template.class);

        assertNotNull(deserialized);
        assertEquals(4, deserialized.getElements().size());

        TemplateElement sOut = deserialized.getElements().stream().filter(e -> "star_1".equals(e.getId())).findFirst().orElseThrow();
        assertEquals(ElementType.STAR, sOut.getType());
        assertEquals("Gold Rating Star", sOut.getName());
        assertEquals(5, sOut.getStarPoints());
        assertEquals(6.0, sOut.getInnerRadius());
        assertEquals(14.0, sOut.getOuterRadius());
        assertEquals("linear", sOut.getFillType());
        assertEquals("#F59E0B", sOut.getGradientStartColor());
        assertEquals("#D97706", sOut.getGradientEndColor());
        assertEquals(45.0, sOut.getGradientAngle());
        assertTrue(sOut.isShadowEnabled());
        assertEquals(15.0, sOut.getRotation());

        TemplateElement aOut = deserialized.getElements().stream().filter(e -> "arrow_1".equals(e.getId())).findFirst().orElseThrow();
        assertEquals("STEALTH", aOut.getArrowHeadStyle());
        assertEquals(7.0, aOut.getArrowHeadLength());
        assertTrue(aOut.isFlipHorizontal());
        assertEquals(1.1, aOut.getScaleX(), 0.001);

        TemplateElement wOut = deserialized.getElements().stream().filter(e -> "wm_1".equals(e.getId())).findFirst().orElseThrow();
        assertEquals("ORIGINAL COPY", wOut.getWatermarkText());
        assertEquals(0.15, wOut.getWatermarkOpacity(), 0.001);
        assertEquals(-45.0, wOut.getWatermarkAngle(), 0.001);
        assertEquals("invoice.status", wOut.getBinding());
        assertEquals("invoice.total > 0", wOut.getVisibleCondition());
    }

    @Test
    void testVectorShapesAndEffectsPdfExport(@TempDir Path tempDir) throws IOException {
        Template template = PresetTemplates.buildClassic();
        template.getElements().clear();

        // Add a variety of universal 2D shapes
        // 1. Star
        TemplateElement star = new TemplateElement();
        star.setId("star_pdf");
        star.setType(ElementType.STAR);
        star.setX(15); star.setY(15); star.setW(25); star.setH(25);
        star.setBg("#F59E0B");
        star.setBorderColor("#B45309");
        star.setBorderWidth(0.5);
        star.setStarPoints(5);
        template.getElements().add(star);

        // 2. Circle
        TemplateElement circle = new TemplateElement();
        circle.setId("circle_pdf");
        circle.setType(ElementType.CIRCLE);
        circle.setX(45); circle.setY(15); circle.setW(25); circle.setH(25);
        circle.setBg("#60A5FA");
        circle.setBorderColor("#1D4ED8");
        circle.setBorderWidth(0.8);
        template.getElements().add(circle);

        // 3. Ellipse
        TemplateElement ellipse = new TemplateElement();
        ellipse.setId("ellipse_pdf");
        ellipse.setType(ElementType.ELLIPSE);
        ellipse.setX(75); ellipse.setY(15); ellipse.setW(35); ellipse.setH(25);
        ellipse.setBg("#A78BFA");
        ellipse.setBorderColor("#6D28D9");
        template.getElements().add(ellipse);

        // 4. Arrow
        TemplateElement arrow = new TemplateElement();
        arrow.setId("arrow_pdf");
        arrow.setType(ElementType.ARROW);
        arrow.setX(115); arrow.setY(20); arrow.setW(40); arrow.setH(15);
        arrow.setBg("#10B981");
        arrow.setBorderColor("#047857");
        arrow.setBorderWidth(1.0);
        arrow.setArrowHeadStyle("TRIANGLE");
        template.getElements().add(arrow);

        // 5. Divider
        TemplateElement divider = new TemplateElement();
        divider.setId("div_pdf");
        divider.setType(ElementType.DIVIDER);
        divider.setX(15); divider.setY(45); divider.setW(180); divider.setH(4);
        divider.setDividerOrientation("HORIZONTAL");
        divider.setDividerStyle("DASHED");
        divider.setBorderColor("#94A3B8");
        divider.setBorderWidth(0.6);
        template.getElements().add(divider);

        // 6. Polygon (Triangle)
        TemplateElement poly = new TemplateElement();
        poly.setId("poly_pdf");
        poly.setType(ElementType.POLYGON);
        poly.setX(15); poly.setY(55); poly.setW(30); poly.setH(25);
        poly.setPoints("15,0 30,25 0,25");
        poly.setBg("#F43F5E");
        poly.setBorderColor("#BE123C");
        poly.setBorderWidth(0.6);
        template.getElements().add(poly);

        // 7. Watermark
        TemplateElement wm = new TemplateElement();
        wm.setId("wm_pdf");
        wm.setType(ElementType.WATERMARK);
        wm.setX(20); wm.setY(80); wm.setW(170); wm.setH(50);
        wm.setWatermarkText("TAX INVOICE");
        wm.setWatermarkOpacity(0.10);
        wm.setWatermarkAngle(-25.0);
        template.getElements().add(wm);

        Bill bill = new Bill();
        bill.setBillNo("INV-V4-VECTOR");
        bill.setDate("2026-09-10");
        bill.setItems(new ArrayList<>());

        Settings settings = new Settings();
        File outFile = tempDir.resolve("v4_vector_shapes_test.pdf").toFile();

        PdfExportService.exportBillPdf(bill, template, settings, outFile, 1);

        assertTrue(outFile.exists(), "PDF should be generated");
        assertTrue(outFile.length() > 800, "PDF should contain vector drawing instructions");
    }

    @Test
    void testComponentPresetPdfExport(@TempDir Path tempDir) throws IOException {
        Template template = new Template();
        template.setName("Component Presets Invoice");
        template.setElements(new ArrayList<>());

        // Combine multiple component presets into a complete invoice
        template.getElements().addAll(ComponentPreset.createComponent(ComponentPreset.PresetType.INVOICE_HEADER, 15.0, 15.0));
        template.getElements().addAll(ComponentPreset.createComponent(ComponentPreset.PresetType.CUSTOMER_ADDRESS, 15.0, 50.0));
        template.getElements().addAll(ComponentPreset.createComponent(ComponentPreset.PresetType.INVOICE_TOTALS, 120.0, 160.0));
        template.getElements().addAll(ComponentPreset.createComponent(ComponentPreset.PresetType.BANK_DETAILS, 15.0, 160.0));
        template.getElements().addAll(ComponentPreset.createComponent(ComponentPreset.PresetType.PAYMENT_TERMS, 15.0, 210.0));
        template.getElements().addAll(ComponentPreset.createComponent(ComponentPreset.PresetType.SIGNATURE_SECTION, 130.0, 230.0));
        template.getElements().addAll(ComponentPreset.createComponent(ComponentPreset.PresetType.DOCUMENT_FOOTER, 15.0, 270.0));

        Bill bill = new Bill();
        bill.setBillNo("INV-COMP-001");
        bill.setDate("2026-09-10");
        bill.setItems(new ArrayList<>());

        Settings settings = new Settings();
        File outFile = tempDir.resolve("v4_components_test.pdf").toFile();

        PdfExportService.exportBillPdf(bill, template, settings, outFile, 1);

        assertTrue(outFile.exists(), "Component invoice PDF should be generated");
        assertTrue(outFile.length() > 1000, "Component invoice PDF should contain all rendered sections");
    }

    @Test
    void testLegacyTemplateBackwardsCompatibilityWithTableAndVariables(@TempDir Path tempDir) throws IOException {
        // Build classic preset and ensure all table columns, zebra striping, and variable tokens work
        Template classic = PresetTemplates.buildClassic();
        assertNotNull(classic);
        assertFalse(classic.getElements().isEmpty());

        TemplateElement table = classic.getElements().stream()
                .filter(e -> e.getType() == ElementType.TABLE)
                .findFirst()
                .orElse(null);

        assertNotNull(table, "Classic template must retain TABLE element");
        assertNotNull(table.getColumns(), "Table must have columns");
        assertFalse(table.getColumns().isEmpty(), "Columns list should not be empty");

        // Verify standard columns exist
        boolean hasDesc = table.getColumns().stream().anyMatch(c -> "desc".equalsIgnoreCase(c.getKey()));
        assertTrue(hasDesc, "Table must have description column");

        Bill bill = new Bill();
        bill.setBillNo("INV-LEGACY-001");
        bill.setDate("2026-09-10");
        bill.getVariables().put("buyer.name", "Acme Industries");
        bill.getVariables().put("buyer_name", "Acme Industries");
        bill.getVariables().put("invoice.number", "INV-LEGACY-001");

        List<BillItem> items = new ArrayList<>();
        BillItem item1 = new BillItem();
        item1.setDesc("Enterprise Software License");
        item1.setQty(2.0);
        item1.setRate(500.0);
        items.add(item1);

        BillItem item2 = new BillItem();
        item2.setDesc("Implementation Support");
        item2.setQty(1.0);
        item2.setRate(500.0);
        items.add(item2);

        bill.setItems(items);

        Settings settings = new Settings();
        File outFile = tempDir.resolve("legacy_compatibility_test.pdf").toFile();

        PdfExportService.exportBillPdf(bill, classic, settings, outFile, 1);

        assertTrue(outFile.exists(), "Legacy template PDF must export successfully");
        assertTrue(outFile.length() > 1000, "Legacy template PDF must have full content");
    }

    @Test
    void testShapeRotationAndTransforms() {
        TemplateElement shape = new TemplateElement();
        shape.setId("shape_rot_1");
        shape.setType(ElementType.RECT);
        shape.setX(20.0);
        shape.setY(30.0);
        shape.setW(60.0);
        shape.setH(40.0);
        shape.setRotation(45.0);
        shape.setScaleX(1.2);
        shape.setScaleY(0.8);
        shape.setFlipHorizontal(true);
        shape.setFlipVertical(false);

        assertEquals(45.0, shape.getRotation(), 0.001);
        assertEquals(1.2, shape.getScaleX(), 0.001);
        assertEquals(0.8, shape.getScaleY(), 0.001);
        assertTrue(shape.isFlipHorizontal());
        assertFalse(shape.isFlipVertical());

        javafx.scene.layout.Pane node = new javafx.scene.layout.Pane();
        DesignObjectRenderer.applyEffectsAndTransforms(node, shape);
        assertEquals(45.0, node.getRotate(), 0.001, "Rotation must be applied to JavaFX node");
        assertEquals(-1.2, node.getScaleX(), 0.001, "Flip horizontal should invert scaleX");
        assertEquals(0.8, node.getScaleY(), 0.001);
    }

    @Test
    void testSvgVectorParser() {
        String svgXml = """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
                    <rect x="5" y="5" width="90" height="90" fill="#F1F5F9" stroke="#64748B" stroke-width="2"/>
                    <circle cx="50" cy="50" r="30" fill="#3B82F6"/>
                    <path d="M 30 50 L 45 65 L 70 35" fill="none" stroke="#FFFFFF" stroke-width="4"/>
                </svg>
                """;

        SvgVectorParser.ParsedSvg parsed = SvgVectorParser.parseSvg(svgXml);
        assertNotNull(parsed, "Parsed SVG result should not be null");
        assertTrue(parsed.hasViewBox, "SVG has viewBox");
        assertEquals(100.0, parsed.width, 0.001);
        assertEquals(100.0, parsed.height, 0.001);
        assertEquals(3, parsed.shapes.size(), "Should have extracted rect, circle, and path shapes");

        TemplateElement el = new TemplateElement();
        el.setType(ElementType.SVG);
        el.setSvgSource(svgXml);
        el.setW(50.0);
        el.setH(50.0);

        // Verify JavaFX rendering
        javafx.scene.Node fxNode = SvgVectorParser.renderToJavaFx(el, 200, 200);
        assertNotNull(fxNode, "JavaFX vector render should produce a node");
        assertTrue(fxNode instanceof javafx.scene.Group, "JavaFX vector render should be a Group");
        assertEquals(3, ((javafx.scene.Group) fxNode).getChildren().size());

        // Verify Java2D rendering
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(200, 200, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g2 = img.createGraphics();
        assertDoesNotThrow(() -> SvgVectorParser.renderToGraphics2D(g2, el, 0, 0, 200, 200));
        g2.dispose();
    }

    @Test
    void testCustomComponentPersistenceAndInstantiation() throws Exception {
        List<TemplateElement> source = new ArrayList<>();
        TemplateElement bg = new TemplateElement();
        bg.setId("orig_1");
        bg.setType(ElementType.RECT);
        bg.setX(100.0);
        bg.setY(150.0);
        bg.setW(80.0);
        bg.setH(30.0);
        source.add(bg);

        TemplateElement text = new TemplateElement();
        text.setId("orig_2");
        text.setType(ElementType.TEXT);
        text.setX(110.0);
        text.setY(160.0);
        text.setW(60.0);
        text.setH(15.0);
        text.setText("Customer Note");
        source.add(text);

        CustomComponent saved = CustomComponentManager.saveComponent("Test Banner Block", "Testing save", source);
        assertNotNull(saved);
        assertNotNull(saved.getId());
        assertEquals("Test Banner Block", saved.getName());
        assertEquals(2, saved.getElements().size());

        // Verify normalized relative coordinates (origin at 0,0)
        assertEquals(0.0, saved.getElements().get(0).getX(), 0.001);
        assertEquals(0.0, saved.getElements().get(0).getY(), 0.001);
        assertEquals(10.0, saved.getElements().get(1).getX(), 0.001);
        assertEquals(10.0, saved.getElements().get(1).getY(), 0.001);

        // Instantiate at (25, 40)
        List<TemplateElement> instantiated = CustomComponentManager.instantiateComponent(saved, 25.0, 40.0);
        assertEquals(2, instantiated.size());

        TemplateElement instBg = instantiated.get(0);
        TemplateElement instText = instantiated.get(1);

        assertNotEquals("orig_1", instBg.getId(), "Must generate fresh ID");
        assertNotEquals("orig_2", instText.getId(), "Must generate fresh ID");
        assertEquals(25.0, instBg.getX(), 0.001);
        assertEquals(40.0, instBg.getY(), 0.001);
        assertEquals(35.0, instText.getX(), 0.001);
        assertEquals(50.0, instText.getY(), 0.001);

        // Both must share the same groupId for synchronized group drag
        assertNotNull(instBg.getGroupId());
        assertEquals(instBg.getGroupId(), instText.getGroupId(), "Group elements must share groupId");
        assertTrue(instBg.isGrouped());
        assertTrue(instText.isGrouped());

        // Cleanup
        CustomComponentManager.deleteComponent(saved.getId());
    }

    @Test
    void testAdvancedTypographyAttributes() {
        TemplateElement textEl = new TemplateElement();
        textEl.setText("invoice studio professional billing");
        textEl.setLineSpacing(4.0);
        textEl.setLetterSpacing(1.5);
        textEl.setWordSpacing(2.0);
        textEl.setTextTransform("uppercase");

        assertEquals(4.0, textEl.getLineSpacing(), 0.001);
        assertEquals(1.5, textEl.getLetterSpacing(), 0.001);
        assertEquals(2.0, textEl.getWordSpacing(), 0.001);
        assertEquals("uppercase", textEl.getTextTransform());

        // Test text transform logic
        assertEquals("INVOICE STUDIO PROFESSIONAL BILLING", DesignObjectRenderer.applyTextTransform(textEl.getText(), "uppercase", false));
        assertEquals("invoice studio professional billing", DesignObjectRenderer.applyTextTransform(textEl.getText(), "lowercase", false));
        assertEquals("Invoice Studio Professional Billing", DesignObjectRenderer.applyTextTransform(textEl.getText(), "capitalize", false));
        assertEquals("invoice studio professional billing", DesignObjectRenderer.applyTextTransform(textEl.getText(), "none", false));
    }

    @Test
    void testMultiElementGroupMovementLogic() {
        String gid = "grp_test_sync";
        TemplateElement el1 = new TemplateElement();
        el1.setGroupId(gid); el1.setX(10.0); el1.setY(20.0);

        TemplateElement el2 = new TemplateElement();
        el2.setGroupId(gid); el2.setX(30.0); el2.setY(25.0);

        assertTrue(el1.isGrouped());
        assertTrue(el2.isGrouped());
        assertEquals(el1.getGroupId(), el2.getGroupId());

        // Simulate group displacement of dx = +15, dy = +10
        double dx = 15.0;
        double dy = 10.0;

        List<TemplateElement> group = List.of(el1, el2);
        for (TemplateElement elem : group) {
            elem.setX(elem.getX() + dx);
            elem.setY(elem.getY() + dy);
        }

        assertEquals(25.0, el1.getX(), 0.001);
        assertEquals(30.0, el1.getY(), 0.001);
        assertEquals(45.0, el2.getX(), 0.001);
        assertEquals(35.0, el2.getY(), 0.001);
    }

    @Test
    void testV4TypographyTrackingAndWeights() {
        TemplateElement el = new TemplateElement();
        el.setText("INVOICE STUDIO");
        el.setFontWeight(300);
        assertFalse(el.isBold());
        el.setBold(true);
        assertEquals(700, el.getFontWeight());
        assertTrue(el.isBold());

        el.setUnderline(true);
        assertTrue(el.isUnderline());

        el.setStrikethrough(true);
        assertTrue(el.isStrikethrough());

        el.setLetterSpacing(4.0);
        el.setWordSpacing(2.0);

        String tracked = DesignObjectRenderer.applyTypographyTracking("INVOICE STUDIO", 4.0, 2.0);
        assertNotNull(tracked);
        assertTrue(tracked.length() > "INVOICE STUDIO".length(), "Tracked text should include micro-spaces");
        assertTrue(tracked.contains("INVOICE") || tracked.startsWith("I"));

        // No tracking when 0
        assertEquals("TEST", DesignObjectRenderer.applyTypographyTracking("TEST", 0, 0));
    }

    @Test
    void testV4RenderContextParcelAndLogisticsVariables() {
        Bill bill = new Bill();
        bill.setBillNo("INV-2026-009");
        bill.setParcel(7);
        bill.getVariables().put("transport_name", "SuperFast Express");
        bill.getVariables().put("vehicle_no", "MH-12-AB-1234");
        bill.getVariables().put("transport_phone", "+91 9876543210");

        RenderContext ctx = new RenderContext(bill, new Settings(), 0, 1, 1);
        assertEquals("7", ctx.resolveText("{{parcel}}"));
        assertEquals("7", ctx.resolveText("{{parcels}}"));
        assertEquals("SuperFast Express", ctx.resolveText("{{transport_name}}"));
        assertEquals("MH-12-AB-1234", ctx.resolveText("{{vehicle_no}}"));
        assertEquals("+91 9876543210", ctx.resolveText("{{transport_phone}}"));
    }
}

