package com.invoicestudio.service;

import com.invoicestudio.model.Bill;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;
import javafx.print.*;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Window;

public class PrintingService {

    public static boolean printNode(Node node, Window owner, int copies, String jobName) {
        return printTemplate(node, null, owner, copies, jobName);
    }

    public static boolean printTemplate(Node node, Template template, Window owner, int copies, String jobName) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) return false;

        job.getJobSettings().setJobName(jobName != null ? jobName : "InvoiceStudio Print");
        job.getJobSettings().setCopies(Math.max(1, copies));

        Printer printer = job.getPrinter();
        PageLayout pageLayout = null;

        if (printer != null) {
            PageOrientation orientation = PageOrientation.PORTRAIT;
            if (template != null && template.getPage() != null
                    && "landscape".equalsIgnoreCase(template.getPage().getOrientation())) {
                orientation = PageOrientation.LANDSCAPE;
            }

            Paper targetPaper = Paper.A4;
            if (template != null && template.getPage() != null) {
                targetPaper = resolvePaper(printer, template.getPage().getSizeName(),
                        template.getPage().getWidth(), template.getPage().getHeight());
            }

            try {
                pageLayout = printer.createPageLayout(
                        targetPaper,
                        orientation,
                        Printer.MarginType.HARDWARE_MINIMUM
                );
                job.getJobSettings().setPageLayout(pageLayout);
            } catch (Exception ignored) {
                pageLayout = job.getJobSettings().getPageLayout();
            }
        } else {
            pageLayout = job.getJobSettings().getPageLayout();
        }

        boolean proceed = job.showPrintDialog(owner);
        if (proceed) {
            // Re-fetch layout in case user changed printer or paper in print dialog
            PageLayout actualLayout = job.getJobSettings().getPageLayout();

            Node printTarget;
            double sourceW;
            double sourceH;

            if (node instanceof com.invoicestudio.ui.BillPreviewPane preview) {
                printTarget = preview.createCleanPrintNode();
                sourceW = ((Pane) printTarget).getPrefWidth();
                sourceH = ((Pane) printTarget).getPrefHeight();
            } else {
                printTarget = node;
                javafx.geometry.Bounds b = node.getBoundsInLocal();
                sourceW = b.getWidth() > 0 ? b.getWidth() : actualLayout.getPrintableWidth();
                sourceH = b.getHeight() > 0 ? b.getHeight() : actualLayout.getPrintableHeight();
            }

            double printableW = actualLayout.getPrintableWidth();
            double printableH = actualLayout.getPrintableHeight();

            // Screen pixels are 96 DPI (3.78 px/mm); JavaFX printer coordinates are 72 pt/inch.
            // Ratio 72 / 96 = 0.75 scales screen pixels to 100% physical size.
            // In addition, ensure it fits inside the printer's printable boundaries without clipping.
            double baseScale = 0.75;
            double maxFitScaleX = printableW / sourceW;
            double maxFitScaleY = printableH / sourceH;
            double scale = Math.min(baseScale, Math.min(maxFitScaleX, maxFitScaleY));

            javafx.scene.Group printGroup = new javafx.scene.Group(printTarget);
            printGroup.getTransforms().setAll(new javafx.scene.transform.Scale(scale, scale, 0, 0));

            boolean success = job.printPage(actualLayout, printGroup);
            if (success) {
                job.endJob();
                return true;
            }
        }
        return false;
    }

    private static Paper resolvePaper(Printer printer, com.invoicestudio.model.PageSizeName sizeName, double widthMm, double heightMm) {
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
        double w = 210 * 72.0 / 25.4; // A4 pt
        double h = 297 * 72.0 / 25.4;
        Pane root = new Pane();
        root.setPrefSize(w, h);
        root.setStyle("-fx-background-color: white;");

        Pane shifted = new Pane();
        double offX = (settings != null ? settings.getPrintOffsetX() : 0) * 72.0 / 25.4;
        double offY = (settings != null ? settings.getPrintOffsetY() : 0) * 72.0 / 25.4;
        shifted.setLayoutX(offX);
        shifted.setLayoutY(offY);
        shifted.setPrefSize(w, h);

        double mm = 72.0 / 25.4;

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
