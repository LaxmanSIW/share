package com.invoicestudio.service;

import com.invoicestudio.service.AppLog;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;
import com.invoicestudio.ui.BillPreviewPane;
import com.invoicestudio.ui.PrintPreviewDialog;
import javafx.print.*;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Window;

import java.util.Optional;

public class PrintingService {

    /**
     * Screen pixels are 96 DPI (3.78 px/mm); JavaFX printer coordinates are
     * 72 pt/inch. Ratio 72 / 96 = 0.75 maps screen pixels to 100% physical
     * size. The node is additionally shrunk only when it cannot physically
     * fit the printable area (e.g. continuous thermal rolls).
     */
    public static final double BASE_SCALE = 0.75;

    public static boolean printNode(Node node, Window owner, int copies, String jobName) {
        return printTemplate(node, null, owner, copies, jobName);
    }

    /**
     * Opens the modern print preview window and prints deterministically.
     * <p>
     * ROOT CAUSE NOTE (A4 bills printing at 100x140 mm): the native Windows
     * print dialog overwrites {@code JobSettings} with the printer driver's
     * default paper form when OK is pressed. If that form is a small custom
     * sheet (common on billing PCs), the old code re-fetched the layout and
     * shrink-fitted the whole A4 bill into it. This pipeline never consults
     * the driver for page geometry: the PageLayout is built from the paper
     * chosen in our own dialog and set into JobSettings immediately before
     * {@code printPage}, so the driver cannot override it.
     */
    public static boolean printTemplate(Node node, Template template, Window owner, int copies, String jobName) {
        // 1) Modern print window with live preview + explicit options
        try {
            PrintPreviewDialog.PreviewFactory factory = buildPreviewFactory(node);
            PrintPreviewDialog dlg = new PrintPreviewDialog(
                    owner, template, factory, copies, jobName,
                    "Print" + (jobName != null && !jobName.isBlank() ? " — " + jobName : ""));
            Optional<PrintOptions> chosen = dlg.showAndWait();
            if (chosen.isEmpty()) return false;
            return printWithOptions(node, template, chosen.get());
        } catch (Throwable dialogFailure) {
            // Headless/test environments or unexpected UI failure:
            // fall back to the native dialog, but re-assert the layout AFTER
            // it closes so the driver's default form can never win.
            return printWithNativeDialog(node, template, owner, copies, jobName);
        }
    }

    private static PrintPreviewDialog.PreviewFactory buildPreviewFactory(Node node) {
        if (node instanceof BillPreviewPane preview) {
            return effectiveTemplate -> {
                BillPreviewPane clean = new BillPreviewPane();
                clean.render(
                        effectiveTemplate != null ? effectiveTemplate : preview.getCurrentTemplate(),
                        preview.getCurrentBill(),
                        preview.getCurrentSettings(),
                        preview.getCurrentCopyIndex(),
                        preview.getCurrentPageCount());
                return clean;
            };
        }
        // Non-bill nodes (calibration sheet etc.): static preview
        return effectiveTemplate -> node;
    }

    /** Deterministic print path driven entirely by user-chosen PrintOptions. */
    private static boolean printWithOptions(Node node, Template template, PrintOptions opts) {
        Printer printer = opts.getPrinter();
        PrinterJob job = printer != null ? PrinterJob.createPrinterJob(printer) : PrinterJob.createPrinterJob();
        if (job == null) return false;

        JobSettings settings = job.getJobSettings();
        settings.setJobName(opts.getJobName());
        settings.setCopies(opts.getCopies());

        PageLayout layout = safeLayout(printer, opts.getPaper(), opts.getOrientation());
        // Asserted AFTER all dialog interaction — nothing downstream can change it
        if (layout != null) {
            settings.setPageLayout(layout);
        } else {
            layout = settings.getPageLayout();
        }

        return doPrint(job, layout, node, template);
    }

    /**
     * Legacy fallback using the native print dialog. The layout the user gets
     * is rebuilt from the template on the final printer and RE-ASSERTED into
     * JobSettings after the dialog returns (the native dialog resets it to the
     * driver's default form — the historical source of wrong print sizes).
     */
    private static boolean printWithNativeDialog(Node node, Template template, Window owner, int copies, String jobName) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) return false;

        job.getJobSettings().setJobName(jobName != null ? jobName : "InvoiceStudio Print");
        job.getJobSettings().setCopies(Math.max(1, copies));

        boolean proceed = job.showPrintDialog(owner);
        if (!proceed) return false;

        // The dialog has closed: rebuild OUR layout and force it back in
        Printer finalPrinter = job.getPrinter();
        PageOrientation orientation = PageOrientation.PORTRAIT;
        Paper targetPaper = Paper.A4;
        if (template != null && template.getPage() != null) {
            if ("landscape".equalsIgnoreCase(template.getPage().getOrientation())) {
                orientation = PageOrientation.LANDSCAPE;
            }
            targetPaper = resolvePaper(finalPrinter, template.getPage().getSizeName(),
                    template.getPage().getWidth(), template.getPage().getHeight());
        }
        PageLayout asserted = safeLayout(finalPrinter, targetPaper, orientation);
        if (asserted != null) {
            job.getJobSettings().setPageLayout(asserted);
        } else {
            asserted = job.getJobSettings().getPageLayout();
        }

        return doPrint(job, asserted, node, template);
    }

    /** Shared geometry + rendering: maps the (96 DPI) preview node onto the (72 pt/inch) page. */
    private static boolean doPrint(PrinterJob job, PageLayout layout, Node node, Template template) {
        Node printTarget;
        double sourceW;
        double sourceH;

        if (node instanceof BillPreviewPane preview) {
            printTarget = preview.createCleanPrintNode();
            sourceW = ((Pane) printTarget).getPrefWidth();
            sourceH = ((Pane) printTarget).getPrefHeight();
        } else {
            printTarget = node;
            javafx.geometry.Bounds b = node.getBoundsInLocal();
            sourceW = b.getWidth() > 0 ? b.getWidth() : layout.getPrintableWidth();
            sourceH = b.getHeight() > 0 ? b.getHeight() : layout.getPrintableHeight();
        }

        double printableW = layout.getPrintableWidth();
        double printableH = layout.getPrintableHeight();
        double scale = computePrintScale(sourceW, sourceH, printableW, printableH);

        javafx.scene.Group printGroup = new javafx.scene.Group(printTarget);
        printGroup.getTransforms().setAll(new javafx.scene.transform.Scale(scale, scale, 0, 0));

        boolean success = job.printPage(layout, printGroup);
        if (success) {
            job.endJob();
            return true;
        }
        return false;
    }

    /**
     * Exact px->pt mapping (0.75), shrinking only when the content genuinely
     * does not belong on the chosen paper.
     * <p>
     * A 96-DPI A4 preview node (794 px) maps to 595.5 pt = a true 210 mm A4.
     * With hardware-minimum margins the printable width is ~571 pt, i.e. the
     * outermost ~4% of the sheet falls into the printer's unprintable strip.
     * Shrinking for that would make every bill print 3-4% small (and the
     * calibration ruler inaccurate), so a ~6% slack is tolerated at exact
     * size — the driver clips only what no printer could print anyway.
     * Real mismatches (A4 design on A5 paper, long thermal rolls) overflow
     * far beyond the slack and shrink to fit so nothing is lost.
     */
    public static double computePrintScale(double sourceW, double sourceH, double printableW, double printableH) {
        if (sourceW <= 0 || sourceH <= 0) return BASE_SCALE;
        double fitX = printableW / sourceW;
        double fitY = printableH / sourceH;
        double scale = BASE_SCALE;
        double slackLimit = BASE_SCALE / 1.06; // tolerate ~6% hardware-margin overflow
        if (fitX < slackLimit || fitY < slackLimit) {
            scale = Math.min(BASE_SCALE, Math.min(fitX, fitY));
        }
        return scale;
    }

    /** Builds a PageLayout, falling back to the printer's default on any driver error. */
    private static PageLayout safeLayout(Printer printer, Paper paper, PageOrientation orientation) {
        if (printer != null) {
            try {
                return printer.createPageLayout(paper, orientation, Printer.MarginType.HARDWARE_MINIMUM);
            } catch (Exception ignored) {
            AppLog.debug(ignored);
                try {
                    return printer.createPageLayout(paper, orientation, Printer.MarginType.DEFAULT);
                } catch (Exception ignoredAgain) { /* fall through */ }
            }
        }
        return null; // caller may keep the job default
    }

    public static Paper resolvePaper(Printer printer, com.invoicestudio.model.PageSizeName sizeName, double widthMm, double heightMm) {
        if (sizeName == null) sizeName = com.invoicestudio.model.PageSizeName.A4;

        return switch (sizeName) {
            case A4 -> Paper.A4;
            case A5 -> Paper.A5;
            case LETTER -> Paper.NA_LETTER;
            case LEGAL -> Paper.LEGAL;
            case THERMAL_80, THERMAL_58, CUSTOM -> {
                if (printer != null) {
                    for (Paper p : printer.getPrinterAttributes().getSupportedPapers()) {
                        double pW = p.getWidth() * 25.4 / 72.0; // mm
                        double pH = p.getHeight() * 25.4 / 72.0;
                        if (Math.abs(pW - widthMm) < 5.0 && Math.abs(pH - heightMm) < 15.0) {
                            yield p;
                        }
                    }
                    for (Paper p : printer.getPrinterAttributes().getSupportedPapers()) {
                        String name = p.getName().toLowerCase();
                        if (sizeName == com.invoicestudio.model.PageSizeName.THERMAL_80 && (name.contains("80") || name.contains("roll") || name.contains("receipt"))) {
                            yield p;
                        }
                    }
                }
                yield Paper.A4;
            }
        };
    }

    public static Pane createCalibrationSheetNode(Settings settings) {
        // Built at 96 DPI screen pixels like BillPreviewPane, so the print
        // pipeline's 0.75 px->pt mapping produces a TRUE-SIZE ruler sheet
        // (10 mm grid = exactly 10 mm on paper).
        double w = 210 * 3.7795275591; // A4 px @96dpi
        double h = 297 * 3.7795275591;
        Pane root = new Pane();
        root.setPrefSize(w, h);
        root.setStyle("-fx-background-color: white;");

        Pane shifted = new Pane();
        double offX = (settings != null ? settings.getPrintOffsetX() : 0) * 3.7795275591;
        double offY = (settings != null ? settings.getPrintOffsetY() : 0) * 3.7795275591;
        shifted.setLayoutX(offX);
        shifted.setLayoutY(offY);
        shifted.setPrefSize(w, h);

        double mm = 3.7795275591;

        // 10mm Grid lines
        for (int x = 10; x < 210; x += 10) {
            Line vl = new Line(x * mm, 0, x * mm, 297 * mm);
            vl.setStroke(x % 50 == 0 ? Color.web("#8a8a8a") : Color.web("#d8d4cc"));
            vl.setStrokeWidth(x % 50 == 0 ? 0.8 : 0.4);
            shifted.getChildren().add(vl);
        }
        for (int y = 10; y < 297; y += 10) {
            Line hl = new Line(0, y * mm, 210 * mm, y * mm);
            hl.setStroke(y % 50 == 0 ? Color.web("#8a8a8a") : Color.web("#d8d4cc"));
            hl.setStrokeWidth(y % 50 == 0 ? 0.8 : 0.4);
            shifted.getChildren().add(hl);
        }

        // Top and Left ruler ticks & labels
        for (int x = 10; x < 210; x += 10) {
            boolean major = x % 50 == 0;
            Line tick = new Line(x * mm, 0, x * mm, major ? 8 * mm : 4 * mm);
            tick.setStroke(Color.BLACK);
            tick.setStrokeWidth(major ? 1.2 : 0.6);
            shifted.getChildren().add(tick);

            if (major) {
                Text t = new Text(String.valueOf(x));
                t.setFont(Font.font("Segoe UI", 9));
                t.setFill(Color.BLACK);
                t.setX(x * mm + 2);
                t.setY(10 * mm);
                shifted.getChildren().add(t);
            }
        }
        for (int y = 10; y < 297; y += 10) {
            boolean major = y % 50 == 0;
            Line tick = new Line(0, y * mm, major ? 8 * mm : 4 * mm, y * mm);
            tick.setStroke(Color.BLACK);
            tick.setStrokeWidth(major ? 1.2 : 0.6);
            shifted.getChildren().add(tick);

            if (major) {
                Text t = new Text(String.valueOf(y));
                t.setFont(Font.font("Segoe UI", 9));
                t.setFill(Color.BLACK);
                t.setX(10 * mm);
                t.setY(y * mm - 2);
                shifted.getChildren().add(t);
            }
        }

        // Corner crop marks
        addCropMarks(shifted, 210 * mm, 297 * mm, 10 * mm);

        // Calibration Title & Note
        Text title = new Text("PRINTER CALIBRATION SHEET");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        title.setFill(Color.BLACK);
        title.setX(30 * mm);
        title.setY(30 * mm);
        shifted.getChildren().add(title);

        Text note = new Text(String.format("Current Offsets: X = %.1f mm, Y = %.1f mm\nUse a physical ruler to measure distance from paper edge to the 0/0 origin.",
                settings != null ? settings.getPrintOffsetX() : 0,
                settings != null ? settings.getPrintOffsetY() : 0));
        note.setFont(Font.font("Segoe UI", 11));
        note.setFill(Color.BLACK);
        note.setX(30 * mm);
        note.setY(36 * mm);
        shifted.getChildren().add(note);

        root.getChildren().add(shifted);
        return root;
    }

    private static void addCropMarks(Pane pane, double w, double h, double leg) {
        // Top Left
        pane.getChildren().add(new Line(0, 0, leg, 0));
        pane.getChildren().add(new Line(0, 0, 0, leg));
        // Top Right
        pane.getChildren().add(new Line(w, 0, w - leg, 0));
        pane.getChildren().add(new Line(w, 0, w, leg));
        // Bottom Left
        pane.getChildren().add(new Line(0, h, leg, h));
        pane.getChildren().add(new Line(0, h, 0, h - leg));
        // Bottom Right
        pane.getChildren().add(new Line(w, h, w - leg, h));
        pane.getChildren().add(new Line(w, h, w, h - leg));
    }

    public boolean printCalibrationSheet(double offX, double offY) {
        Settings s = new Settings();
        s.setPrintOffsetX(offX);
        s.setPrintOffsetY(offY);
        Pane sheet = createCalibrationSheetNode(s);
        return printNode(sheet, null, 1, "Printer Calibration Sheet");
    }
}
