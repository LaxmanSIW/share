package com.invoicestudio.service;

import com.invoicestudio.model.PageSizeName;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;
import javafx.print.Paper;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Headless-safe tests for the deterministic print geometry introduced with
 * the modern print preview dialog (fix for A4 bills printing at 100x140 mm).
 */
class PrintServiceLogicTest {

    @Test
    void testBaseScaleMaps96DpiPixelsToTruePhysicalSize() {
        // A4 preview node at 96 DPI screen pixels
        double a4PxW = 210.0 * 3.7795275591; // 794 px
        double a4PxH = 297.0 * 3.7795275591; // 1123 px

        // A4 printable area with hardware-minimum margins (~571 x 818 pt)
        double printableW = Paper.A4.getWidth() - 24;
        double printableH = Paper.A4.getHeight() - 24;

        double scale = PrintingService.computePrintScale(a4PxW, a4PxH, printableW, printableH);

        // 794 px * 0.75 = 595.5 pt = a true 210 mm A4. The ~4% hardware-margin
        // overflow is tolerated (unprintable strip anyway) so the bill prints
        // at exactly the same size as the PDF export.
        assertEquals(PrintingService.BASE_SCALE, scale, 0.0001,
                "A4 on A4 must print at exact 100% size, matching the PDF output");
    }

    @Test
    void testA4DesignOnA5PaperShrinksToFit() {
        // 794 x 1123 px A4 design chosen onto A5 paper (printable ~396 x 571 pt)
        double a5PrintableW = Paper.A5.getWidth() - 24;
        double a5PrintableH = Paper.A5.getHeight() - 24;
        double scale = PrintingService.computePrintScale(794, 1123, a5PrintableW, a5PrintableH);
        assertEquals(a5PrintableW / 794.0, scale, 0.001,
                "Real paper mismatch must shrink so no bill content is lost");
        assertTrue(scale < PrintingService.BASE_SCALE / 1.06);
    }

    @Test
    void testOversizedRollContentShrinksToFit() {
        // 80mm roll with many items: node height grows far beyond the page
        double scale = PrintingService.computePrintScale(302, 3000, 210, 500);
        assertEquals(500.0 / 3000.0, scale, 0.0001,
                "Continuous roll content must shrink to fit the printable height");
    }

    @Test
    void testDegenerateSourceFallsBackToBaseScale() {
        assertEquals(PrintingService.BASE_SCALE, PrintingService.computePrintScale(0, 0, 100, 100), 0.0001);
        assertEquals(PrintingService.BASE_SCALE, PrintingService.computePrintScale(-5, 100, 100, 100), 0.0001);
    }

    @Test
    void testResolvePaperStandardSizes() {
        assertEquals(Paper.A4, PrintingService.resolvePaper(null, PageSizeName.A4, 210, 297));
        assertEquals(Paper.A5, PrintingService.resolvePaper(null, PageSizeName.A5, 148, 210));
        assertEquals(Paper.NA_LETTER, PrintingService.resolvePaper(null, PageSizeName.LETTER, 215.9, 279.4));
        assertEquals(Paper.LEGAL, PrintingService.resolvePaper(null, PageSizeName.LEGAL, 215.9, 355.6));
    }

    @Test
    void testResolvePaperNullSizeNameDefaultsToA4() {
        assertEquals(Paper.A4, PrintingService.resolvePaper(null, null, 210, 297));
    }

    @Test
    void testResolvePaperCustomWithNoPrinterFallsBackToA4() {
        // Custom / thermal without a printer: cannot match driver forms -> A4 fallback
        assertEquals(Paper.A4, PrintingService.resolvePaper(null, PageSizeName.CUSTOM, 100, 140));
        assertEquals(Paper.A4, PrintingService.resolvePaper(null, PageSizeName.THERMAL_80, 80, 500));
    }

    @Test
    void testCalibrationSheetIsBuiltAt96DpiPixels() {
        // The calibration sheet is a physical ruler: it MUST be built in the
        // same 96-DPI px space as bill previews so the 0.75 px->pt mapping
        // prints a true 210 x 297 mm sheet with an exact 10 mm grid.
        Pane sheet = PrintingService.createCalibrationSheetNode(new Settings());
        assertEquals(210.0 * 3.7795275591, sheet.getPrefWidth(), 0.01);
        assertEquals(297.0 * 3.7795275591, sheet.getPrefHeight(), 0.01);
    }

    @Test
    void testPrintOptionsNormalizesValues() {
        PrintOptions opts = new PrintOptions(null, null, null, 0, null);
        assertEquals(Paper.A4, opts.getPaper());
        assertEquals(javafx.print.PageOrientation.PORTRAIT, opts.getOrientation());
        assertEquals(1, opts.getCopies());
        assertEquals("InvoiceStudio Print", opts.getJobName());

        PrintOptions copies = new PrintOptions(null, Paper.A5, javafx.print.PageOrientation.LANDSCAPE, 7, "Bill 12");
        assertEquals(Paper.A5, copies.getPaper());
        assertEquals(javafx.print.PageOrientation.LANDSCAPE, copies.getOrientation());
        assertEquals(7, copies.getCopies());
        assertEquals("Bill 12", copies.getJobName());
    }

    @Test
    void testPaperSizeTextIsMillimeters() {
        String a4 = PrintOptions.paperSizeText(Paper.A4);
        assertTrue(a4.contains("210"), "A4 width should read 210 mm: " + a4);
        assertTrue(a4.contains("297"), "A4 height should read 297 mm: " + a4);
        assertEquals("?", PrintOptions.paperSizeText(null));
    }

    @Test
    void testEffectiveTemplatePreviewKeepsIdentityButNotMutatingBase() {
        // Mirrors PrintPreviewDialog.buildEffectiveTemplate(): a FRESH PageConfig
        // (+ fresh Margins) so changing preview size never touches the caller's template
        Template base = new Template();
        base.setId("t1");
        base.setName("Gold Classic");

        com.invoicestudio.model.PageConfig src = base.getPage();
        com.invoicestudio.model.PageConfig.Margins m = src.getMargin();
        com.invoicestudio.model.PageConfig eff = new com.invoicestudio.model.PageConfig(
                src.getSizeName(), src.getWidth(), src.getHeight(), src.getOrientation(),
                new com.invoicestudio.model.PageConfig.Margins(m.getTop(), m.getRight(), m.getBottom(), m.getLeft()));

        Template copy = new Template(base.getId(), base.getName(), eff, base.getElements());
        copy.setPrintOffsetX(base.getPrintOffsetX());
        copy.setPrintOffsetY(base.getPrintOffsetY());

        assertEquals(base.getId(), copy.getId());
        assertEquals(base.getName(), copy.getName());
        assertEquals(base.getPrintOffsetX(), copy.getPrintOffsetX(), 0.001);

        // Changing the copy's page must never touch the caller's template
        copy.getPage().setWidth(148);
        copy.getPage().setHeight(210);
        copy.getPage().getMargin().setTop(99);
        assertEquals(210.0, base.getPage().getWidth(), 0.001, "Base template width must stay A4");
        assertEquals(297.0, base.getPage().getHeight(), 0.001, "Base template height must stay A4");
        assertEquals(8.0, base.getPage().getMargin().getTop(), 0.001, "Base template margins must not be aliased");
    }
}
