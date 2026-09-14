package com.invoicestudio.service;

import com.invoicestudio.db.LabelPrintHistoryDao;
import com.invoicestudio.db.DatabaseManager;
import com.invoicestudio.model.LabelPrintHistory;
import com.invoicestudio.model.LabelConfig;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.transform.Rotate;

import java.util.List;
import java.util.Map;

/**
 * Barcode-Mode print pipeline (thermal strip stock, e.g. TSC TA210).
 * <p>
 * One printed page = ONE strip row carrying up to {@code columns} labels.
 * Slots are filled left → right from the Bulk Print queue; the last page
 * simply leaves the remaining columns blank. The feed-direction gap (gapY)
 * is advanced by the printer's gap sensor and never printed.
 * <p>
 * Paper handling: JavaFX cannot invent custom Paper instances, so the
 * service matches the printer's supported forms against the strip size
 * (the label size the shop configured in the TSC driver). On no match it
 * falls back to the printer default and scales like {@code PrintingService}.
 */
public final class LabelPrintService {

    private LabelPrintService() {}

    /** Result of a print run — used for toasts and history logging. */
    public record PrintResult(boolean success, int pages, int labels, String message) {}

    /**
     * Prints the queue. Must run on the JavaFX Application Thread.
     *
     * @param template      label-mode template (design canvas = one label cell)
     * @param settings      live settings
     * @param lines         bulk print queue (variable values + copies)
     * @param variableOrder ordered variable keys (for the history summary)
     * @param printer       target printer (null = system default)
     */
    public static PrintResult printLabels(Template template, Settings settings,
                                          List<LabelGeometryService.PrintLine> lines,
                                          List<String> variableOrder,
                                          Printer printer, boolean silentHistory) {
        if (template == null || !template.isLabelMode()) {
            return new PrintResult(false, 0, 0, "Template is not in Barcode Mode.");
        }
        LabelConfig cfg = template.labelOrNew();
        cfg.sanitize();

        int labels = LabelGeometryService.totalLabels(lines);
        if (labels == 0) {
            return new PrintResult(false, 0, 0, "Nothing to print — the queue is empty.");
        }

        Printer target = printer != null ? printer : Printer.getDefaultPrinter();
        if (target == null) {
            return new PrintResult(false, 0, 0, "No printer installed.");
        }
        PrinterJob job = PrinterJob.createPrinterJob(target);
        if (job == null) {
            return new PrintResult(false, 0, 0, "Could not open a print job for " + target.getName() + ".");
        }

        double[] pageSize = LabelGeometryService.pageSizeMm(cfg);
        PageLayout layout = matchLayout(target, pageSize[0], pageSize[1]);
        job.getJobSettings().setJobName("Labels — " + template.getName());
        job.getJobSettings().setPageLayout(layout);

        double pageWpx = pageSize[0] * LabelRenderUtil.MM_PX;
        double pageHpx = pageSize[1] * LabelRenderUtil.MM_PX;

        // Printable width inside hardware margins → scale (same 0.75 mapping as bills)
        double printableW = layout.getPrintableWidth();
        double scale = PrintingService.BASE_SCALE;
        double fitX = printableW / pageWpx;
        double slackLimit = PrintingService.BASE_SCALE / 1.06;
        if (fitX < slackLimit) {
            scale = Math.min(PrintingService.BASE_SCALE, fitX);
        }

        Map<Integer, List<LabelGeometryService.LabelSlot>> byPage =
                LabelGeometryService.slotsByPage(LabelGeometryService.expandSlots(lines, cfg));

        boolean rotate = !"0".equals(cfg.getOrientation());
        double angle = Double.parseDouble(cfg.getOrientation());
        double cellWmm = LabelGeometryService.physicalCellWidth(cfg);
        double cellHmm = LabelGeometryService.physicalCellHeight(cfg);

        int printed = 0;
        try {
            for (Map.Entry<Integer, List<LabelGeometryService.LabelSlot>> e : byPage.entrySet()) {
                Pane pageNode = buildStripRowPage(template, settings, e.getValue(),
                        cfg, pageWpx, pageHpx, cellWmm, cellHmm, rotate, angle);

                Group printGroup = new Group(pageNode);
                printGroup.getTransforms().setAll(new javafx.scene.transform.Scale(scale, scale, 0, 0));

                if (job.printPage(layout, printGroup)) {
                    printed++;
                } else {
                    return new PrintResult(false, printed, printed * cfg.getColumns(),
                            "Printer stopped at page " + (e.getKey() + 1) + " of " + byPage.size() + ".");
                }
            }
            job.endJob();
        } catch (Exception ex) {
            return new PrintResult(false, printed, printed * cfg.getColumns(),
                    "Print failed: " + ex.getMessage());
        }

        logHistory(template, target, lines, variableOrder, byPage.size(), labels);
        return new PrintResult(true, byPage.size(), labels, "Sent " + labels + " labels (" + byPage.size() + " strip rows) to " + target.getName() + ".");
    }

    /** Builds one strip-row page with every slot's label rendered in place. */
    private static Pane buildStripRowPage(Template template, Settings settings,
                                          List<LabelGeometryService.LabelSlot> slots,
                                          LabelConfig cfg, double pageWpx, double pageHpx,
                                          double cellWmm, double cellHmm,
                                          boolean rotate, double angle) {
        Pane page = new Pane();
        page.setPrefSize(pageWpx, pageHpx);
        page.setMinSize(pageWpx, pageHpx);
        page.setMaxSize(pageWpx, pageHpx);
        page.setStyle("-fx-background-color: white;");

        for (LabelGeometryService.LabelSlot slot : slots) {
            // The design canvas is authored at cfg label dims (design orientation);
            // with rotation we spin that node into the transposed physical cell.
            double designWmm = cfg.getLabelWidth();
            double designHmm = cfg.getLabelHeight();
            Pane cell = LabelRenderUtil.renderLabelNode(template, slot.values, designWmm, designHmm, settings);

            if (rotate) {
                double w = designWmm * LabelRenderUtil.MM_PX;
                double h = designHmm * LabelRenderUtil.MM_PX;
                Rotate rot = new Rotate(angle, w / 2.0, h / 2.0);
                cell.getTransforms().add(rot);
            }

            // Center the (possibly rotated) artwork inside its physical cell.
            double cellWpx = cellWmm * LabelRenderUtil.MM_PX;
            double cellHpx = cellHmm * LabelRenderUtil.MM_PX;
            double offX = slot.xMm * LabelRenderUtil.MM_PX + (cellWpx - designWmm * LabelRenderUtil.MM_PX) / 2.0;
            double offY = (cellHpx - designHmm * LabelRenderUtil.MM_PX) / 2.0;
            cell.setLayoutX(offX);
            cell.setLayoutY(offY);
            page.getChildren().add(cell);
        }
        return page;
    }

    /**
     * Matches the printer's supported paper forms to the strip size.
     * Layout is always PORTRAIT (feed direction); orientation of the artwork
     * is handled by the rotation transform instead.
     */
    private static PageLayout matchLayout(Printer printer, double widthMm, double heightMm) {
        Paper best = null;
        double bestScore = Double.MAX_VALUE;
        try {
            for (Paper p : printer.getPrinterAttributes().getSupportedPapers()) {
                double pw = p.getWidth() * 25.4 / 72.0;
                double ph = p.getHeight() * 25.4 / 72.0;
                double score = Math.abs(pw - widthMm) + Math.abs(ph - heightMm) * 3.0;
                if (score < bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        } catch (Exception ignored) {}
        if (best != null && bestScore < 12.0) { // within ~12mm total drift
            try {
                return printer.createPageLayout(best, PageOrientation.PORTRAIT, Printer.MarginType.HARDWARE_MINIMUM);
            } catch (Exception ignored) {}
        }
        try {
            return printer.createPageLayout(Paper.A4, PageOrientation.PORTRAIT, Printer.MarginType.HARDWARE_MINIMUM);
        } catch (Exception ignored) {}
        return null;
    }

    /** Appends the run to label_print_history — failures never block printing. */
    private static void logHistory(Template template, Printer printer,
                                   List<LabelGeometryService.PrintLine> lines,
                                   List<String> variableOrder, int pages, int labels) {
        try {
            LabelConfig cfg = template.labelOrNew();
            LabelPrintHistory h = new LabelPrintHistory();
            h.setTemplateId(template.getId());
            h.setTemplateName(template.getName());
            h.setPrinterName(printer != null ? printer.getName() : "");
            h.setLabelWidth(LabelGeometryService.physicalCellWidth(cfg));
            h.setLabelHeight(LabelGeometryService.physicalCellHeight(cfg));
            h.setColumns(cfg.getColumns());
            h.setPages(pages);
            h.setLabels(labels);
            h.setTotalCopies(labels);
            h.setSummary(LabelGeometryService.summarize(lines, variableOrder));
            h.setLinesJson(LabelGeometryService.linesToJson(lines, variableOrder));
            new LabelPrintHistoryDao(DatabaseManager.getInstance()).insert(h);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
