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
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) return false;

        job.getJobSettings().setJobName(jobName != null ? jobName : "InvoiceStudio Print");
        job.getJobSettings().setCopies(Math.max(1, copies));

        try {
            Printer printer = job.getPrinter();
            if (printer != null) {
                PageLayout current = job.getJobSettings().getPageLayout();
                PageLayout minMarginLayout = printer.createPageLayout(
                        current.getPaper(),
                        current.getPageOrientation(),
                        Printer.MarginType.HARDWARE_MINIMUM
                );
                job.getJobSettings().setPageLayout(minMarginLayout);
            }
        } catch (Exception ignored) {}

        boolean proceed = job.showPrintDialog(owner);
        if (proceed) {
            boolean success = job.printPage(node);
            if (success) {
                job.endJob();
                return true;
            }
        }
        return false;
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
