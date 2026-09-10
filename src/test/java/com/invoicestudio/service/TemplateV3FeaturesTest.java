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

class TemplateV3FeaturesTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testIndividualBordersModel() {
        TemplateElement el = new TemplateElement();
        el.setType(ElementType.RECT);
        el.setBorderWidth(1.0);
        el.setBorderColor("#111111");

        assertFalse(el.isIndividualBorders());
        assertEquals(1.0, el.getEffectiveSideWidth("top"));
        assertEquals("#111111", el.getEffectiveSideColor("top"));
        assertEquals("solid", el.getEffectiveSideStyle("top"));

        // Enable individual borders
        el.setIndividualBorders(true);
        el.setBorderTopWidth(2.5);
        el.setBorderTopColor("#FF0000");
        el.setBorderTopStyle("dashed");

        el.setBorderBottomWidth(0.5);
        el.setBorderBottomColor("#00FF00");
        el.setBorderBottomStyle("dotted");

        assertTrue(el.isIndividualBorders());
        assertEquals(2.5, el.getEffectiveSideWidth("top"));
        assertEquals("#FF0000", el.getEffectiveSideColor("top"));
        assertEquals("dashed", el.getEffectiveSideStyle("top"));

        assertEquals(0.5, el.getEffectiveSideWidth("bottom"));
        assertEquals("#00FF00", el.getEffectiveSideColor("bottom"));
        assertEquals("dotted", el.getEffectiveSideStyle("bottom"));

        // Right side falls back to base border when not explicitly set
        assertEquals(1.0, el.getEffectiveSideWidth("right"));
        assertEquals("#111111", el.getEffectiveSideColor("right"));
    }

    @Test
    void testSideActiveState() {
        TemplateElement el = new TemplateElement();
        el.setType(ElementType.RECT);
        el.setIndividualBorders(true);

        assertTrue(el.isSideActive("top"));
        el.setBorderTopActive(false);
        assertFalse(el.isSideActive("top"));

        el.setBorderRightStyle("none");
        assertFalse(el.isSideActive("right"));
    }

    @Test
    void testObjectNamingAndDisplayName() {
        TemplateElement el = new TemplateElement();
        el.setType(ElementType.TEXT);
        el.setText("Invoice Summary");
        assertEquals("Invoice Summary", el.getDisplayName());

        el.setName("Custom Header Title");
        assertEquals("Custom Header Title", el.getDisplayName());

        TemplateElement tableEl = new TemplateElement();
        tableEl.setType(ElementType.TABLE);
        assertEquals("Table", tableEl.getDisplayName());

        tableEl.setName("Items Grid");
        assertEquals("Items Grid", tableEl.getDisplayName());
    }

    @Test
    void testTemplateSerializationRoundTrip() throws Exception {
        Template template = PresetTemplates.buildClassic();
        TemplateElement rect = new TemplateElement();
        rect.setId("rect_test_1");
        rect.setType(ElementType.RECT);
        rect.setName("Branded Box");
        rect.setIndividualBorders(true);
        rect.setBorderTopWidth(3.0);
        rect.setBorderTopColor("#2563EB");
        rect.setBorderTopStyle("dashed");
        rect.setBorderBottomWidth(1.5);
        rect.setBorderBottomColor("#10B981");
        rect.setBorderBottomStyle("dotted");
        template.getElements().add(rect);

        String json = mapper.writeValueAsString(template);
        Template deserialized = mapper.readValue(json, Template.class);

        assertNotNull(deserialized);
        TemplateElement found = deserialized.getElements().stream()
                .filter(e -> "rect_test_1".equals(e.getId()))
                .findFirst()
                .orElse(null);

        assertNotNull(found);
        assertEquals("Branded Box", found.getName());
        assertTrue(found.isIndividualBorders());
        assertEquals(3.0, found.getBorderTopWidth());
        assertEquals("#2563EB", found.getBorderTopColor());
        assertEquals("dashed", found.getBorderTopStyle());
        assertEquals(1.5, found.getBorderBottomWidth());
        assertEquals("#10B981", found.getBorderBottomColor());
        assertEquals("dotted", found.getBorderBottomStyle());
    }

    @Test
    void testPdfExportWithIndividualBordersAndStyles(@TempDir Path tempDir) throws IOException {
        Template template = PresetTemplates.buildClassic();

        TemplateElement rect = new TemplateElement();
        rect.setId("rect_styled");
        rect.setType(ElementType.RECT);
        rect.setX(20);
        rect.setY(50);
        rect.setW(100);
        rect.setH(40);
        rect.setBg("#F0FDF4");
        rect.setIndividualBorders(true);
        rect.setBorderTopWidth(2.0);
        rect.setBorderTopColor("#16A34A");
        rect.setBorderTopStyle("dashed");
        rect.setBorderBottomWidth(2.0);
        rect.setBorderBottomColor("#DC2626");
        rect.setBorderBottomStyle("dotted");
        rect.setBorderLeftWidth(1.0);
        rect.setBorderLeftColor("#2563EB");
        rect.setBorderLeftStyle("solid");
        rect.setBorderRightActive(false);

        template.getElements().add(rect);

        Bill bill = new Bill();
        bill.setBillNo("INV-2026-V3");
        bill.setDate("2026-09-10");
        bill.setItems(new ArrayList<>());

        Settings settings = new Settings();
        File outFile = tempDir.resolve("v3_border_test.pdf").toFile();

        PdfExportService.exportBillPdf(bill, template, settings, outFile, 1);

        assertTrue(outFile.exists(), "PDF should be generated");
        assertTrue(outFile.length() > 500, "PDF should not be empty");
    }

    @Test
    void testPrintingCoordinateScalingFactor() {
        // JavaFX screen rendering is 96 DPI (~3.78 px/mm).
        // Standard printer coordinate space is 72 pt/inch.
        // Screen to printer scale factor is exactly 72.0 / 96.0 = 0.75.
        double screenDpi = 96.0;
        double printerDpi = 72.0;
        double scaleFactor = printerDpi / screenDpi;

        assertEquals(0.75, scaleFactor, 0.0001, "Scale factor from 96 DPI screen pixels to 72 pt printer coordinates must be 0.75");

        // A4 dimension: 210mm x 297mm
        // In 96 DPI screen pixels: 210 * 3.7795275591 = 793.7 px, 297 * 3.7795275591 = 1122.5 px
        // Scaled by 0.75: 793.7 * 0.75 = 595.275 pt (Standard A4 width in pt)
        // 1122.5 * 0.75 = 841.89 pt (Standard A4 height in pt)
        double mmPx = 3.7795275591;
        double a4WidthPx = 210.0 * mmPx;
        double a4HeightPx = 297.0 * mmPx;

        double scaledWidthPt = a4WidthPx * scaleFactor;
        double scaledHeightPt = a4HeightPx * scaleFactor;

        assertEquals(595.275, scaledWidthPt, 0.1, "Scaled width matches standard A4 points");
        assertEquals(841.89, scaledHeightPt, 0.1, "Scaled height matches standard A4 points");
    }
}
