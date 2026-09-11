package com.invoicestudio.service;

import com.invoicestudio.model.Bill;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;
import javafx.geometry.Bounds;

import java.util.Set;
import javafx.print.*;
import javafx.scene.Node;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javafx.stage.Window;

public class PrintingService {

    public static boolean printNode(Node node, Window owner, int copies, String jobName) {
        return printNode(node, owner, copies, jobName, null, null);
    }

    /**
     * Prints a node with full control over the physical page size.
     *
     * @param contentWmm template/page width in mm  (null → printer default paper)
     * @param contentHmm template/page height in mm (null → printer default paper)
     *
     * When the template size is given, a CUSTOM PAPER of exactly that size is
     * requested (A4, 80mm receipt roll, half-letter… — whatever the template is),
     * so the printed output matches the template 1:1 instead of being laid out
     * on the printer's default sheet (which clipped or misplaced content).
     * If the driver rejects the custom size, or the printable area ends up
     * smaller than the content, the node is uniformly scaled down and centred
     * so nothing is cut off.
     */
    public static boolean printNode(Node node, Window owner, int copies, String jobName,
                                    Double contentWmm, Double contentHmm) {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) return false;

        job.getJobSettings().setJobName(jobName != null ? jobName : "InvoiceStudio Print");
        job.getJobSettings().setCopies(Math.max(1, copies));

        try {
            Printer printer = job.getPrinter();
            if (printer != null) {
                PageLayout current = job.getJobSettings().getPageLayout();
                PageOrientation orientation = current != null ? current.getPageOrientation() : PageOrientation.PORTRAIT;
                PageLayout layout;

                if (contentWmm != null && contentHmm != null && contentWmm > 0 && contentHmm > 0) {
                    // Landscape when the template itself is wider than tall
                    if (contentWmm > contentHmm) orientation = PageOrientation.LANDSCAPE;

                    // JavaFX's Paper has no public custom-size constructor, so pick
                    // the printer's SUPPORTED paper that best matches the template
                    // size (A4 for A4, 80mm roll for 80mm roll, …). When nothing is
                    // close, keep the default paper — scaleToFitLayout() then shrinks
                    // the content to the printable area so nothing gets cut off.
                    Paper best = current.getPaper();
                    double bestDiff = Double.MAX_VALUE;
                    try {
                        for (Paper p : printer.getPrinterAttributes().getSupportedPapers()) {
                            double diff = Math.abs(p.getWidth() - contentWmm * 1000.0)
                                    + Math.abs(p.getHeight() - contentHmm * 1000.0);
                            if (diff < bestDiff) {
                                bestDiff = diff;
                                best = p;
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    if (best != null && bestDiff <= 5000.0) { // within 5mm total → treat as a match
                        layout = printer.createPageLayout(best, orientation, Printer.MarginType.HARDWARE_MINIMUM);
                    } else {
                        layout = printer.createPageLayout(
                                current.getPaper(), orientation, Printer.MarginType.HARDWARE_MINIMUM);
                    }
                } else {
                    layout = printer.createPageLayout(
                            current.getPaper(),
                            current.getPageOrientation(),
                            Printer.MarginType.HARDWARE_MINIMUM);
                }
                job.getJobSettings().setPageLayout(layout);
            }
        } catch (Exception ignored) {}

        boolean proceed = job.showPrintDialog(owner);
        if (proceed) {
            boolean success = job.printPage(scaleToFitLayout(job, node));
            if (success) {
                job.endJob();
                return true;
            }
        }
        return false;
    }

    /**
     * Wraps the node so that it exactly fills the final page layout's printable
     * area: uniform scale (only shrink, never upscale) + centre. This is the
     * safety net that guarantees "nothing missing" even when the printer driver
     * overrides our custom paper with a smaller one.
     */
    private static Node scaleToFitLayout(PrinterJob job, Node node) {
        try {
            PageLayout layout = job.getJobSettings().getPageLayout();
            if (layout == null) return node;

            double printableW = layout.getPrintableWidth();  // points (72 dpi)
            double printableH = layout.getPrintableHeight();
            Bounds bounds = node.getLayoutBounds();
            double nodeW = bounds.getWidth();
            double nodeH = bounds.getHeight();
            if (nodeW <= 0 || nodeH <= 0 || printableW <= 0 || printableH <= 0) return node;

            double scale = Math.min(1.0, Math.min(printableW / nodeW, printableH / nodeH));
            double tx = (printableW - nodeW * scale) / 2.0 - bounds.getMinX() * scale;
            double ty = (printableH - nodeH * scale) / 2.0 - bounds.getMinY() * scale;

            Group wrapper = new Group(node);
            wrapper.getTransforms().addAll(
                    new Scale(scale, scale),
                    new Translate(tx, ty));
            return wrapper;
        } catch (Exception e) {
            return node;
        }
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
