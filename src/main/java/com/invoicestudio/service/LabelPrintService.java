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
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Scale;

import java.util.ArrayList;
import java.util.Comparator;
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
 * <b>Orientation contract (WYSIWYG):</b> the page node is authored with X
 * across the strip (print-head direction) and Y along the feed — exactly the
 * picture the Strip Preview shows. The job is ALWAYS sent with a PORTRAIT
 * PageLayout, so the JavaFX print engine can never rotate the artwork (a
 * landscape layout was the historical source of "prints in a different
 * orientation than the preview"). A driver form registered transposed
 * (swapped width/height) is compensated by rotating OUR page node 90° CW in
 * a single explicit Affine — deterministic, previewable, never doubled.
 * <p>
 * Paper handling: JavaFX cannot invent custom Paper instances, so the
 * service matches the printer's supported forms against the strip size
 * (the label size the shop configured in the TSC driver). On no match it
 * falls back to A4 portrait and scales like {@code PrintingService}.
 * <p>
 * <b>Native TSPL routing:</b> printers whose driver reports a TSC name
 * (TA210 …) are printed by {@link TsplPrintService} instead — a raw
 * TSPL/TSPL2 script that owns SIZE/GAP/PRINT itself, so quantity,
 * position and orientation can never be re-interpreted by the driver.
 * This JavaFX path stays the engine for every non-TSC printer and can be
 * forced back with {@code -Dinvoicestudio.print.engine=driver}.
 */
public final class LabelPrintService {

    private LabelPrintService() {}

    /** Result of a print run — used for toasts and history logging. */
    public record PrintResult(boolean success, int pages, int labels, String message) {}

    /**
     * Pure result of matching the strip page against one printer form.
     * {@code fitWidthMm/fitHeightMm} are the form's dimensions in the frame
     * the strip page must fill; {@code transposed} says the form's NATIVE
     * registration is swapped relative to that frame.
     */
    public record FormChoice(int paperIndex, boolean transposed,
                             double fitWidthMm, double fitHeightMm) {
        /** Total mm the page misses the form by (both dimensions). */
        public double drift(double pageW, double pageH) {
            return Math.abs(fitWidthMm - pageW) + Math.abs(fitHeightMm - pageH);
        }
        /** Blank mm a containing form leaves (both dimensions). */
        public double waste(double pageW, double pageH) {
            return (fitWidthMm - pageW) + (fitHeightMm - pageH);
        }
        public boolean contains(double pageW, double pageH) {
            return fitWidthMm >= pageW - 0.5 && fitHeightMm >= pageH - 0.5;
        }
    }

    /**
     * Picks the best driver form for the strip page — PURE, unit tested.
     * <p>
     * Every form is scored in its NATIVE portrait registration first and
     * transposed second (thermal drivers register the same physical stock
     * both ways); the better-scoring frame wins per form, native on ties.
     * Selection order:
     * <ol>
     *   <li>Near-exact match within ~12 mm total drift.</li>
     *   <li>Otherwise the smallest form that still CONTAINS the strip page
     *       (prints at true size with the least blank feed).</li>
     *   <li>Otherwise simply the closest form that exists — never a blind A4
     *       fallback: sending an A4 page to a gap-sensor label printer makes
     *       it feed a full A4 worth of blank labels per printed page.</li>
     * </ol>
     *
     * @param nativeForms array of {@code {paperWidthMm, paperHeightMm}} in
     *                    the form's NATIVE registration
     * @param pageW       strip page width in mm (across the head)
     * @param pageH       strip page height in mm (feed direction)
     * @return the chosen form, or null when no usable form exists
     */
    public static FormChoice chooseForm(double[][] nativeForms, double pageW, double pageH) {
        List<FormChoice> cands = new ArrayList<>();
        if (nativeForms != null) {
            for (int i = 0; i < nativeForms.length; i++) {
                if (nativeForms[i] == null || nativeForms[i].length < 2) continue;
                double pw = nativeForms[i][0];
                double ph = nativeForms[i][1];
                if (pw <= 0 || ph <= 0) continue;
                double scoreNative = Math.abs(pw - pageW) + Math.abs(ph - pageH);
                double scoreTransposed = Math.abs(ph - pageW) + Math.abs(pw - pageH);
                boolean transposed = scoreTransposed < scoreNative; // native wins ties
                cands.add(new FormChoice(i, transposed,
                        transposed ? ph : pw, transposed ? pw : ph));
            }
        }
        if (cands.isEmpty()) return null;

        // 1) near-exact match on both dimensions
        FormChoice best = cands.stream()
                .min(Comparator.comparingDouble(c -> c.drift(pageW, pageH)))
                .orElse(null);
        if (best != null && best.drift(pageW, pageH) <= 12.0) {
            return best;
        }
        // 2) smallest form that still contains the strip page
        FormChoice fit = cands.stream()
                .filter(c -> c.contains(pageW, pageH))
                .min(Comparator.comparingDouble(c -> c.waste(pageW, pageH)))
                .orElse(null);
        if (fit != null) {
            return fit;
        }
        // 3) closest existing form — page is scaled to it, feed stays short
        return best;
    }

    /** Selection outcome handed back from the printer form matcher. */
    private record SelectedForm(PageLayout layout, boolean transposed,
                                double widthMm, double heightMm, String name) {}

    /**
     * Matches the printer's supported forms against the strip page and
     * returns the PORTRAIT PageLayout to print with. The artwork orientation
     * is never left to the print engine: portrait-only guarantees JavaFX
     * applies zero rotation; a transposed form is reported back so
     * {@link #printLabels} can rotate the page node itself.
     */
    private static SelectedForm selectForm(Printer printer, double pageWmm, double pageHmm) {
        List<Paper> papers = new ArrayList<>();
        List<double[]> dims = new ArrayList<>();
        try {
            for (Paper p : printer.getPrinterAttributes().getSupportedPapers()) {
                double pw = p.getWidth() * 25.4 / 72.0;
                double ph = p.getHeight() * 25.4 / 72.0;
                if (pw <= 0 || ph <= 0) continue;
                papers.add(p);
                dims.add(new double[]{pw, ph});
            }
        } catch (Exception ignored) {}

        FormChoice choice = chooseForm(dims.toArray(new double[0][]), pageWmm, pageHmm);
        if (choice == null) {
            // No usable forms enumerated — A4 portrait fallback, never landscape.
            PageLayout l = portraitLayout(printer, Paper.A4);
            if (l == null) return null;
            return new SelectedForm(l, false,
                    l.getPaper().getWidth() * 25.4 / 72.0,
                    l.getPaper().getHeight() * 25.4 / 72.0,
                    "A4");
        }
        Paper paper = papers.get(choice.paperIndex());
        PageLayout layout = portraitLayout(printer, paper);
        if (layout == null) return null;
        return new SelectedForm(layout, choice.transposed(),
                choice.fitWidthMm(), choice.fitHeightMm(), paper.getName());
    }

    /**
     * ALWAYS portrait (see class doc): the strip page node is authored with
     * X across the head and Y along the feed — exactly what a label-driver
     * form means by portrait. A landscape PageLayout would make JavaFX
     * rotate the whole artwork 90°.
     */
    private static PageLayout portraitLayout(Printer printer, Paper paper) {
        try {
            return printer.createPageLayout(paper, PageOrientation.PORTRAIT,
                    Printer.MarginType.HARDWARE_MINIMUM);
        } catch (Exception ignored) {
            try {
                return printer.createPageLayout(paper, PageOrientation.PORTRAIT,
                        Printer.MarginType.DEFAULT);
            } catch (Exception ignoredAgain) { /* fall through */ }
        }
        return null;
    }

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

        // TSC printers (TA210 …) speak TSPL/TSPL2 natively: a RAW script
        // declares its own SIZE/GAP/PRINT, so the spooler can never feed
        // extra labels, scale the row or rotate it (see TsplPrintService).
        if (TsplPrintService.enabledFor(target)) {
            return TsplPrintService.printLabels(template, settings, lines, variableOrder,
                    target, silentHistory);
        }

        PrinterJob job = PrinterJob.createPrinterJob(target);
        if (job == null) {
            return new PrintResult(false, 0, 0, "Could not open a print job for " + target.getName() + ".");
        }

        double[] pageSize = LabelGeometryService.pageSizeMm(cfg);
        SelectedForm form = selectForm(target, pageSize[0], pageSize[1]);
        if (form == null) {
            return new PrintResult(false, 0, 0,
                    "Printer " + target.getName() + " did not report a usable page form.");
        }
        PageLayout layout = form.layout();
        job.getJobSettings().setJobName("Labels — " + template.getName());
        job.getJobSettings().setPageLayout(layout);

        double pageWpx = pageSize[0] * LabelRenderUtil.MM_PX;
        double pageHpx = pageSize[1] * LabelRenderUtil.MM_PX;
        // A transposed driver form gets the strip row rotated by US (explicit
        // Affine below) — the source that must fit the printable area is the
        // rotated sheet.
        boolean transposeForm = form.transposed();
        double srcWpx = transposeForm ? pageHpx : pageWpx;
        double srcHpx = transposeForm ? pageWpx : pageHpx;

        // Exact px→pt mapping (0.75) shrinking only when the content genuinely
        // does not belong on the chosen form — fits BOTH dimensions.
        double scale = PrintingService.computePrintScale(srcWpx, srcHpx,
                layout.getPrintableWidth(), layout.getPrintableHeight());

        Map<Integer, List<LabelGeometryService.LabelSlot>> byPage =
                LabelGeometryService.slotsByPage(LabelGeometryService.expandSlots(lines, cfg));

        double cellWmm = LabelGeometryService.physicalCellWidth(cfg);
        double cellHmm = LabelGeometryService.physicalCellHeight(cfg);
        double angle = 0;
        try { angle = Double.parseDouble(cfg.getOrientation()); } catch (Exception ignored) {}

        int printed = 0;
        try {
            for (Map.Entry<Integer, List<LabelGeometryService.LabelSlot>> e : byPage.entrySet()) {
                Pane pageNode = buildStripRowPage(template, settings, e.getValue(),
                        cfg, pageWpx, pageHpx, cellWmm, cellHmm, angle);

                Node sheet = pageNode;
                if (transposeForm) {
                    sheet = rotatedSheet(pageNode, pageHpx);
                }

                Group printGroup = new Group(sheet);
                printGroup.getTransforms().setAll(new Scale(scale, scale, 0, 0));

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

        if (!silentHistory) {
            logHistory(template, target.getName(), lines, variableOrder, byPage.size(), labels);
        }
        String formDesc = String.format(java.util.Locale.US, " [%s %.1f×%.1f mm%s]",
                form.name(), form.widthMm(), form.heightMm(),
                transposeForm ? " · artwork auto-rotated to fit the form" : "");
        return new PrintResult(true, byPage.size(), labels, "Sent " + labels + " labels (" + byPage.size()
                + " strip rows) to " + target.getName() + formDesc + ".");
    }

    /**
     * Rotates the strip-row page 90° CLOCKWISE into a transposed driver
     * form's portrait frame with ONE explicit Affine — (x, y) → (H − y, x) —
     * so the top edge of the strip becomes the right edge and the leftmost
     * label prints at the start of the feed. A single transform (instead of
     * a Rotate + Translate pair) removes any concatenation-order ambiguity.
     */
    private static Node rotatedSheet(Pane page, double pageHpx) {
        Affine rot90cw = new Affine();
        rot90cw.setMxx(0); rot90cw.setMxy(-1); rot90cw.setTx(pageHpx);
        rot90cw.setMyx(1); rot90cw.setMyy(0); rot90cw.setTy(0);
        Group g = new Group(page);
        g.getTransforms().add(rot90cw);
        return g;
    }

    /** Builds one strip-row page with every slot's label rendered in place. */
    static Pane buildStripRowPage(Template template, Settings settings,
                                          List<LabelGeometryService.LabelSlot> slots,
                                          LabelConfig cfg, double pageWpx, double pageHpx,
                                          double cellWmm, double cellHmm, double angle) {
        Pane page = new Pane();
        page.setPrefSize(pageWpx, pageHpx);
        page.setMinSize(pageWpx, pageHpx);
        page.setMaxSize(pageWpx, pageHpx);
        page.setStyle("-fx-background-color: white;");

        for (LabelGeometryService.LabelSlot slot : slots) {
            // Identical geometry to the strip preview: the shared
            // physicalCellHolder centres the design node inside the physical
            // die-cut cell and spins it for legacy 90/270 orientations.
            double designWmm = cfg.getLabelWidth();
            double designHmm = cfg.getLabelHeight();
            Pane art = LabelRenderUtil.renderLabelNode(template, slot.values, designWmm, designHmm, settings);
            Pane cell = LabelRenderUtil.physicalCellHolder(art, designWmm, designHmm,
                    cellWmm, cellHmm, angle, false, 0);
            cell.setLayoutX(slot.xMm * LabelRenderUtil.MM_PX);
            cell.setLayoutY(0); // one row per page; the sensor advances the feed gap
            page.getChildren().add(cell);
        }
        return page;
    }

    /** Appends the run to label_print_history — failures never block printing. */
    static void logHistory(Template template, String printerName,
                                   List<LabelGeometryService.PrintLine> lines,
                                   List<String> variableOrder, int pages, int labels) {
        try {
            LabelConfig cfg = template.labelOrNew();
            LabelPrintHistory h = new LabelPrintHistory();
            h.setTemplateId(template.getId());
            h.setTemplateName(template.getName());
            h.setPrinterName(printerName != null ? printerName : "");
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
