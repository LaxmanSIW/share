package com.invoicestudio.service;

import com.invoicestudio.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageMarginTest {

    @Test
    void testPageConfigMarginDefaults() {
        PageConfig config = new PageConfig();
        assertNotNull(config.getMargin(), "Margins should never be null");
        assertEquals(8.0, config.getMargin().getTop(), 0.001);
        assertEquals(8.0, config.getMargin().getBottom(), 0.001);
        assertEquals(8.0, config.getMargin().getLeft(), 0.001);
        assertEquals(8.0, config.getMargin().getRight(), 0.001);
    }

    @Test
    void testPageConfigCustomMargins() {
        PageConfig config = new PageConfig(PageSizeName.A4, 210, 297, "portrait",
                new PageConfig.Margins(12, 10, 15, 14));
        assertEquals(12.0, config.getMargin().getTop(), 0.001);
        assertEquals(10.0, config.getMargin().getRight(), 0.001);
        assertEquals(15.0, config.getMargin().getBottom(), 0.001);
        assertEquals(14.0, config.getMargin().getLeft(), 0.001);

        config.getMargin().setTop(5.0);
        config.getMargin().setBottom(6.0);
        assertEquals(5.0, config.getMargin().getTop(), 0.001);
        assertEquals(6.0, config.getMargin().getBottom(), 0.001);
    }

    @Test
    void testPresetTemplatesHaveConfiguredMargins() {
        for (Template t : PresetTemplates.getAllPresets()) {
            assertNotNull(t.getPage(), "Template page must not be null: " + t.getName());
            assertNotNull(t.getPage().getMargin(), "Template margin must not be null: " + t.getName());
            assertTrue(t.getPage().getMargin().getTop() >= 0);
            assertTrue(t.getPage().getMargin().getBottom() >= 0);
            assertTrue(t.getPage().getMargin().getLeft() >= 0);
            assertTrue(t.getPage().getMargin().getRight() >= 0);
        }
    }

    @Test
    void testPdfExportWithMargins(@TempDir Path tempDir) throws IOException {
        Template template = PresetTemplates.buildClassic();
        template.getPage().getMargin().setTop(15.0);
        template.getPage().getMargin().setBottom(20.0);
        template.getPage().getMargin().setLeft(12.0);
        template.getPage().getMargin().setRight(12.0);

        Bill bill = new Bill();
        bill.setBillNo("INV-MARGIN-TEST");
        BillItem item = new BillItem();
        item.setDesc("Test Item 1");
        item.setQty(2);
        item.setRate(500);
        item.setGst(18);
        bill.setItems(List.of(item));

        Settings settings = new Settings();
        File pdfOut = tempDir.resolve("margin_invoice.pdf").toFile();

        PdfExportService.exportBillPdf(bill, template, settings, pdfOut, 1);

        assertTrue(pdfOut.exists(), "PDF should be generated");
        assertTrue(pdfOut.length() > 500, "PDF should contain valid content");
    }
}
