package com.invoicestudio.service;

import com.invoicestudio.model.LabelConfig;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;
import javafx.application.Platform;
import javafx.print.Printer;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.transform.Transform;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Native TSPL/TSPL2 print pipeline for TSC printers (TA210 et al.).
 * <p>
 * <b>WYSIWYG contract:</b> pages are rendered by the exact same renderer
 * the Strip Preview uses ({@link LabelPrintService#buildStripRowPage} +
 * {@link LabelRenderUtil}), rasterized at the printer's native dot grid
 * (TA210 = 203 dpi = 8 dots/mm), and streamed as one TSPL script that
 * declares its own {@code SIZE}/{@code GAP}/{@code PRINT}. The driver is
 * never asked to scale, rotate or page — so the strip row the user sees
 * is byte-for-byte the page the head burns, and {@code PRINT 1,1} feeds
 * exactly one label (the historical "selected one, printed many" bug was
 * the GDI driver guessing the stock).
 * <p>
 * Routing lives in {@link #enabledFor(Printer)}: TSC-named printers go
 * native automatically; every other printer keeps the JavaFX/driver path
 * unchanged. {@code invoicestudio.print.engine} system property overrides:
 * {@code tspl} forces native everywhere, {@code driver} disables it.
 */
public final class TsplPrintService {

    private TsplPrintService() {}

    /** Transport used to spool RAW jobs (replaceable for tests). */
    private static volatile RawPrintTransport transport = new JavaxRawPrintTransport();

    /**
     * Background spooler pool. The RAW transport waits up to
     * {@link JavaxRawPrintTransport#SPOOL_WAIT_SECONDS} for the spooler to
     * accept/finish the job — waiting on the FX Application Thread froze the
     * whole app until the job completed ("app stops after printing"), so the
     * UI now only renders + builds the script on FX and hands the blocking
     * spool to this daemon pool.
     */
    private static final ExecutorService SPOOL_POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "invoicestudio-tspl-spool");
        t.setDaemon(true);
        return t;
    });

    /** Swaps the spool transport — unit/runtime-harness seam. */
    public static void setTransport(RawPrintTransport t) {
        transport = t != null ? t : new JavaxRawPrintTransport();
    }

    // ------------------------------------------------------------------
    // Printer model / routing
    // ------------------------------------------------------------------

    /** Dots per mm for a printer name: TA300/TA310 (300 dpi) = 12, TA200/TA210 and unknown = 8 (203 dpi). */
    public static int dotsPerMm(Printer printer) {
        return dotsPerMm(printer != null ? printer.getName() : null);
    }

    /** Name-based dot density (the RAW spooler only ever knows the name). */
    public static int dotsPerMm(String printerName) {
        String prop = System.getProperty("invoicestudio.tspl.dotsPerMm");
        if (prop != null) {
            try { return Math.max(4, Integer.parseInt(prop.trim())); } catch (Exception ignored) {}
        }
        String name = printerName != null ? printerName.toLowerCase(Locale.ROOT) : "";
        boolean dpi300 = name.contains("ta300") || name.contains("ta310") || name.contains("12 dot");
        return dpi300 ? 12 : 8;
    }

    /**
     * True when the given printer should print through the native TSPL
     * pipeline. Auto: name contains TSC / TA2xx / TA3xx. Override with
     * {@code -Dinvoicestudio.print.engine=tspl|driver}.
     */
    public static boolean enabledFor(Printer printer) {
        return enabledFor(printer != null ? printer.getName() : null);
    }

    /** Name-based routing (the spooler transport resolves by name too). */
    public static boolean enabledFor(String printerName) {
        String engine = System.getProperty("invoicestudio.print.engine", "auto");
        if ("driver".equalsIgnoreCase(engine)) return false;
        if ("tspl".equalsIgnoreCase(engine)) return true;
        String name = printerName != null ? printerName.toLowerCase(Locale.ROOT) : "";
        return name.contains("tsc") || name.contains("ta210") || name.contains("ta200")
                || name.contains("ta300") || name.contains("ta310");
    }

    // ------------------------------------------------------------------
    // Print run
    // ------------------------------------------------------------------

    /**
     * Prints the queue through TSPL. Same contract as
     * {@link LabelPrintService#printLabels}; must run on the FX thread
     * (rendering + snapshot). Blocks until the spooler responds — the
     * UI-facing entry point is {@link #printLabelsQueued}.
     */
    public static LabelPrintService.PrintResult printLabels(Template template, Settings settings,
                                                            List<LabelGeometryService.PrintLine> lines,
                                                            List<String> variableOrder,
                                                            Printer printer, boolean silentHistory) {
        return printLabelsNamed(template, settings, lines, variableOrder,
                printer != null ? printer.getName() : null, silentHistory);
    }

    /**
     * Name-driven engine — identical to {@link #printLabels} but keyed by
     * the spooler printer NAME (what the RAW transport resolves anyway),
     * so the whole pipeline is runnable in a hardware-less harness.
     */
    public static LabelPrintService.PrintResult printLabelsNamed(Template template, Settings settings,
                                                                 List<LabelGeometryService.PrintLine> lines,
                                                                 List<String> variableOrder,
                                                                 String printerName, boolean silentHistory) {
        PreparedJob job = prepare(template, settings, lines, variableOrder, printerName);
        if (job.error != null) return job.error;

        RawPrintTransport.Result sent = transport.send(job.printerName,
                "Labels — " + job.template.getName(), job.script);
        if (!sent.success()) {
            return new LabelPrintService.PrintResult(false, 0, job.labels,
                    sent.message() + " — falling back is available by printing with a non-TSC printer selected.");
        }

        if (!silentHistory) {
            LabelPrintService.logHistory(job.template, job.printerName, lines, variableOrder,
                    job.pages.size(), job.labels);
        }
        return successResult(job);
    }

    /**
     * UI-facing NON-BLOCKING run: renders + builds the TSPL script on the
     * calling (FX) thread, then hands the potentially slow RAW spool
     * (the transport waits for the spooler up to 30 s) to a background
     * daemon thread. Returns immediately with an intermediate "sending"
     * result; when the spool settles, the history entry is written and
     * {@code onDone} receives the final result on the FX thread.
     * <p>
     * Validation/rasterization failures return a failure result synchronously
     * and never invoke {@code onDone}. Called from a non-FX thread (tests,
     * CLIs) this degrades to the synchronous behaviour of
     * {@link #printLabelsNamed} followed by {@code onDone} on that thread.
     */
    public static LabelPrintService.PrintResult printLabelsQueued(Template template, Settings settings,
                                                                  List<LabelGeometryService.PrintLine> lines,
                                                                  List<String> variableOrder,
                                                                  Printer printer, boolean silentHistory,
                                                                  Consumer<LabelPrintService.PrintResult> onDone) {
        String printerName = printer != null ? printer.getName() : null;
        PreparedJob job = prepare(template, settings, lines, variableOrder, printerName);
        if (job.error != null) return job.error;

        LabelPrintService.PrintResult queued = new LabelPrintService.PrintResult(true,
                job.pages.size(), job.labels,
                String.format(Locale.US,
                        "Rendering done — sending %d label%s to %s… (the script is spooling in the background)",
                        job.labels, job.labels == 1 ? "" : "s", job.printerName));

        Runnable finish = () -> {
            RawPrintTransport.Result sent = transport.send(job.printerName,
                    "Labels — " + job.template.getName(), job.script);
            LabelPrintService.PrintResult finalResult;
            if (sent.success()) {
                finalResult = successResult(job);
                if (!silentHistory) {
                    LabelPrintService.logHistory(job.template, job.printerName, lines, variableOrder,
                            job.pages.size(), job.labels);
                }
            } else {
                finalResult = new LabelPrintService.PrintResult(false, 0, job.labels,
                        sent.message() + " — falling back is available by printing with a non-TSC printer selected.");
            }
            if (onDone != null) {
                if (Platform.isFxApplicationThread()) onDone.accept(finalResult);
                else Platform.runLater(() -> onDone.accept(finalResult));
            }
        };

        if (Platform.isFxApplicationThread()) {
            SPOOL_POOL.execute(finish);
        } else {
            finish.run();
        }
        return queued;
    }

    // ------------------------------------------------------------------
    // Shared run preparation (FX-thread rendering → TSPL bytes)
    // ------------------------------------------------------------------

    /** Everything needed to spool one job, built entirely on the FX thread. */
    private record PreparedJob(Template template, Settings settings, String printerName, byte[] script,
                               List<TsplCommandBuilder.TsplPage> pages,
                               int labels, LabelConfig cfg, double stripWidthMm,
                               int widthDots, int heightDots, int gapDots,
                               LabelPrintService.PrintResult error) {}

    private static PreparedJob prepare(Template template, Settings settings,
                                       List<LabelGeometryService.PrintLine> lines,
                                       List<String> variableOrder, String printerName) {
        if (template == null || !template.isLabelMode()) {
            return new PreparedJob(null, settings, printerName, null, null, 0, null, 0, 0, 0, 0,
                    new LabelPrintService.PrintResult(false, 0, 0, "Template is not in Barcode Mode."));
        }
        LabelConfig cfg = template.labelOrNew();
        cfg.sanitize();

        int labels = LabelGeometryService.totalLabels(lines);
        if (labels == 0) {
            return new PreparedJob(null, settings, printerName, null, null, 0, cfg, 0, 0, 0, 0,
                    new LabelPrintService.PrintResult(false, 0, 0, "Nothing to print — the queue is empty."));
        }
        String name = printerName != null && !printerName.isBlank()
                ? printerName : "(default printer)";

        int dpm = dotsPerMm(name);
        double[] pageSize = LabelGeometryService.pageSizeMm(cfg);
        int widthDots = Math.max(1, (int) Math.round(pageSize[0] * dpm));
        int heightDots = Math.max(1, (int) Math.round(pageSize[1] * dpm));
        int gapDots = Math.max(0, (int) Math.round(cfg.getGapY() * dpm));
        int direction = directionSetting();

        double cellWmm = LabelGeometryService.physicalCellWidth(cfg);
        double cellHmm = LabelGeometryService.physicalCellHeight(cfg);
        double angle = 0;
        try { angle = Double.parseDouble(cfg.getOrientation()); } catch (Exception ignored) {}

        double pageWpx = pageSize[0] * LabelRenderUtil.MM_PX;
        double pageHpx = pageSize[1] * LabelRenderUtil.MM_PX;

        // Render every strip row with the preview renderer, then rasterize
        // at printer dot density (2× supersampled for clean 203-dpi edges).
        List<TsplCommandBuilder.TsplPage> pages = new ArrayList<>();
        try {
            Map<Integer, List<LabelGeometryService.LabelSlot>> byPage =
                    LabelGeometryService.slotsByPage(
                            LabelGeometryService.expandSlots(lines, cfg));
            for (Map.Entry<Integer, List<LabelGeometryService.LabelSlot>> e : byPage.entrySet()) {
                Pane pageNode = LabelPrintService.buildStripRowPage(template, settings, e.getValue(),
                        cfg, pageWpx, pageHpx, cellWmm, cellHmm, angle);
                pages.add(rasterize(pageNode, widthDots, heightDots, dpm, settings));
            }
        } catch (Exception ex) {
            return new PreparedJob(null, settings, name, null, null, labels, cfg, pageSize[0], widthDots, heightDots, gapDots,
                    new LabelPrintService.PrintResult(false, 0, labels,
                            "Could not rasterize the label: " + ex.getMessage()));
        }
        if (pages.isEmpty()) {
            return new PreparedJob(null, settings, name, null, null, 0, cfg, pageSize[0], widthDots, heightDots, gapDots,
                    new LabelPrintService.PrintResult(false, 0, 0, "Nothing to print — the queue is empty."));
        }

        byte[] script = TsplCommandBuilder.build(cfg, pages, widthDots, heightDots, gapDots, direction);
        return new PreparedJob(template, settings, name, script, pages, labels, cfg,
                pageSize[0], widthDots, heightDots, gapDots, null);
    }

    private static LabelPrintService.PrintResult successResult(PreparedJob job) {
        StringBuilder msg = new StringBuilder(String.format(Locale.US,
                "Sent %d label%s (%d strip row%s) to %s via TSPL [SIZE %d×%d dots, GAP %d dots, %s sensor, threshold %d].",
                job.labels, job.labels == 1 ? "" : "s", job.pages.size(), job.pages.size() == 1 ? "" : "s",
                job.printerName, job.widthDots, job.heightDots, job.gapDots,
                "continuous".equalsIgnoreCase(job.cfg().getStockType()) ? "continuous" : "gap",
                job.settings().getBarcodeThreshold()));
        if (job.stripWidthMm() > TsplCommandBuilder.TA210_MAX_PRINT_MM + 0.01
                && job.printerName.toLowerCase(Locale.ROOT).contains("ta210")) {
            msg.append(String.format(Locale.US,
                    " WARNING: strip is %.1f mm wide but the TA210 head prints at most %.0f mm — the right side will clip.",
                    job.stripWidthMm(), TsplCommandBuilder.TA210_MAX_PRINT_MM));
        }
        return new LabelPrintService.PrintResult(true, job.pages.size(), job.labels, msg.toString());
    }

    /** DIRECTION 1 by default (preview-upright); overridable for support. */
    static int directionSetting() {
        String prop = System.getProperty("invoicestudio.tspl.direction");
        if (prop != null && prop.trim().equals("0")) return 0;
        return 1;
    }

    /** Sysprop knob kept for tests/support — overrides the Settings value. */
    static int effectiveThreshold(Settings settings) {
        try {
            String prop = System.getProperty("invoicestudio.tspl.threshold", "").trim();
            if (!prop.isEmpty()) {
                int v = Integer.parseInt(prop);
                if (v >= 0 && v <= 255) return v;
            }
        } catch (Exception ignored) {}
        int t = settings != null ? settings.getBarcodeThreshold() : MonoImage.DEFAULT_THRESHOLD;
        return t < 0 || t > 255 ? MonoImage.DEFAULT_THRESHOLD : t;
    }

    /**
     * Snapshots the 96-dpi page node at 2× the printer dot grid, then
     * downsamples + thresholds into the 1-bit TSPL bitmap payload.
     * <p>
     * Alpha-aware: a thermal head has exactly two states (burn black / leave
     * white), so any pixel the snapshot leaves TRANSPARENT counts as white
     * paper — otherwise unstyled holders/gaps would rasterize as ARGB 0,
     * read as "darkest black" and burn giant black blocks with white
     * content-shaped holes (the reported "prints black, objects white").
     */
    private static TsplCommandBuilder.TsplPage rasterize(Pane pageNode, int widthDots, int heightDots,
                                                         int dpm, Settings settings) {
        int ssDpm = dpm * 2;                       // supersample factor
        int pxW = widthDots * 2, pxH = heightDots * 2;

        // The page node is never attached to a Scene. Without a Scene the
        // user-agent stylesheet never loads, Controls (Label/Text) never get
        // their skin and print BLANK (self-drawing nodes like ImageView are
        // unaffected — the bug used to hide text but keep barcodes). A
        // transient one-off Scene gives the node the real live-scene CSS +
        // layout passes, then we detach again.
        javafx.scene.Group cssRoot = new javafx.scene.Group(pageNode);
        javafx.scene.Scene cssScene = new javafx.scene.Scene(cssRoot);
        pageNode.applyCss();
        pageNode.layout();

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(javafx.scene.paint.Color.WHITE); // transparent → paper, never ink
        double factor = (double) ssDpm / LabelRenderUtil.MM_PX;
        sp.setTransform(Transform.scale(factor, factor));

        WritableImage img;
        try {
            img = pageNode.snapshot(sp, new WritableImage(pxW, pxH));
        } finally {
            cssRoot.getChildren().clear(); // detach — node is reusable
        }
        PixelReader pr = img.getPixelReader();
        int actW = (int) Math.min(pxW, img.getWidth());
        int actH = (int) Math.min(pxH, img.getHeight());

        int[] gray = new int[pxW * pxH];
        int threshold = effectiveThreshold(settings);

        for (int y = 0; y < pxH; y++) {
            int sy = Math.min(y, actH - 1);
            for (int x = 0; x < pxW; x++) {
                int sx = Math.min(x, actW - 1);
                int argb = pr.getArgb(sx, sy);
                // alpha < 16 (≈6% opaque) → white paper; else grayscale
                gray[y * pxW + x] = (argb >>> 24) < 16 ? 255 : MonoImage.luminance(argb);
            }
        }
        int[] dots = MonoImage.downsample2x(gray, pxW, pxH);
        boolean[] black = MonoImage.threshold(dots, widthDots, heightDots, threshold);
        return new TsplCommandBuilder.TsplPage(
                TsplCommandBuilder.packBits(black, widthDots, heightDots),
                TsplCommandBuilder.widthBytes(widthDots), heightDots);
    }
}
