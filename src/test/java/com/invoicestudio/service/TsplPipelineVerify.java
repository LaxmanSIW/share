package com.invoicestudio.service;

import com.invoicestudio.model.ElementType;
import com.invoicestudio.model.LabelConfig;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;
import com.invoicestudio.model.TemplateElement;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;

import javax.imageio.ImageIO;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Hardware-less end-to-end verification of the native TSPL print pipeline
 * (run under Xvfb — the snapshot needs a live FX toolkit):
 * <ol>
 *   <li>Routing: TSC names go native, non-TSC stay on the driver path.</li>
 *   <li>Dots/mm: TA210 = 8 (203 dpi), TA310 = 12 (300 dpi).</li>
 *   <li>ONE label selected → script feeds EXACTLY one label (PRINT 1,1)
 *       with SIZE/GAP declared in dots — the historical "selected 1,
 *       printed many" driver bug cannot recur.</li>
 *   <li>5 copies of one row → identical pages compress to PRINT 5,1.</li>
 *   <li>Two different rows → two strip rows, two PRINTs, correct order.</li>
 *   <li>Bitmap payload polarity: paper encodes as set bits (bit 0 = black
 *       burned), with real black ink present but never overburned.</li>
 *   <li>Rendered strip row exported as PNG for eyeball WYSIWYG check.</li>
 * </ol>
 * Exit code 0 = every assertion passed.
 */
public class TsplPipelineVerify {

    static final List<String> failures = new ArrayList<>();
    static final List<String> passed = new ArrayList<>();

    /** Captures everything the pipeline tries to spool. */
    static final class FakeTransport implements RawPrintTransport {
        String printerName;
        String jobName;
        byte[] data;
        boolean fail;

        @Override
        public Result send(String printerName, String jobName, byte[] data) {
            this.printerName = printerName;
            this.jobName = jobName;
            this.data = data;
            return fail ? new Result(false, "injected failure")
                        : new Result(true, "captured by harness");
        }
    }

    public static void main(String[] args) throws Exception {
        final CountDownLatch fxReady = new CountDownLatch(1);
        Platform.startup(fxReady::countDown);
        if (!fxReady.await(30, TimeUnit.SECONDS)) {
            System.err.println("[FAIL] FX toolkit did not start");
            System.exit(2);
        }
        final AtomicReference<Throwable> crash = new AtomicReference<>();
        final CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                runAll();
            } catch (Throwable t) {
                crash.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(120, TimeUnit.SECONDS)) {
            System.err.println("[FAIL] verification timed out");
            Platform.exit();
            System.exit(3);
        }
        Platform.exit();
        if (crash.get() != null) {
            System.err.println("[CRASH] " + crash.get());
            crash.get().printStackTrace();
            System.exit(4);
        }
        for (String p : passed) System.out.println("[PASS] " + p);
        for (String f : failures) System.err.println("[FAIL] " + f);
        System.out.println(failures.isEmpty()
                ? "TSPL PIPELINE VERIFY: SUCCESS (" + passed.size() + " checks)"
                : "TSPL PIPELINE VERIFY: FAILED (" + failures.size() + " failures)");
        System.exit(failures.isEmpty() ? 0 : 1);
    }

    // ------------------------------------------------------------------

    static void check(boolean cond, String name) {
        if (cond) passed.add(name);
        else failures.add(name);
    }

    static Template labelTemplate() {
        Template t = new Template();
        t.setName("TSPL Verify Label");
        t.setMode("label");
        LabelConfig cfg = new LabelConfig();
        cfg.setStripWidth(54.0);
        cfg.setColumns(1);
        cfg.setLabelWidth(50.0);
        cfg.setLabelHeight(25.0);
        cfg.setGapX(2.0);
        cfg.setGapY(3.0);
        cfg.setStockType("gap");
        t.setLabelConfig(cfg);

        TemplateElement title = new TemplateElement();
        title.setId("v-title");
        title.setType(ElementType.TEXT);
        title.setX(2); title.setY(2); title.setW(46); title.setH(6);
        title.setText("Size {{size}}");
        title.setFontSize(9);
        title.setFontWeight(700);
        t.getElements().add(title);

        TemplateElement code = new TemplateElement();
        code.setId("v-code");
        code.setType(ElementType.BARCODE);
        code.setX(5); code.setY(10); code.setW(40); code.setH(12);
        code.setBarcodeData("{{code}}"); // barcode payload lives here (NOT text)
        code.setBarcodeShowText(true);
        t.getElements().add(code);
        return t;
    }

    static LabelGeometryService.PrintLine line(String size, String code, int copies) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("size", size);
        v.put("code", code);
        return new LabelGeometryService.PrintLine(v, copies);
    }

    static int count(String hay, String needle) {
        int n = 0, i = 0;
        while ((i = hay.indexOf(needle, i)) >= 0) { n++; i += needle.length(); }
        return n;
    }

    static long setBits(byte[] data) {
        long n = 0;
        for (byte b : data) n += Integer.bitCount(b & 0xFF);
        return n;
    }

    /** Black dots = cleared bits (TSC BITMAP burns where the bit is 0). */
    static long blackDots(byte[] data) {
        return (long) data.length * 8 - setBits(data);
    }

    static void runAll() throws Exception {
        // ---- routing ------------------------------------------------
        System.setProperty("invoicestudio.print.engine", "auto");
        check(TsplPrintService.enabledFor("TSC TA210"), "routing: TSC TA210 → TSPL");
        check(TsplPrintService.enabledFor("TSC TA210 GT"), "routing: TSC TA210 GT → TSPL");
        check(!TsplPrintService.enabledFor("Microsoft Print to PDF"), "routing: PDF printer stays on driver path");
        check(!TsplPrintService.enabledFor("Zebra ZP 450"), "routing: Zebra stays on driver path");
        System.setProperty("invoicestudio.print.engine", "driver");
        check(!TsplPrintService.enabledFor("TSC TA210"), "routing: engine=driver overrides detection");
        System.setProperty("invoicestudio.print.engine", "tspl");
        check(TsplPrintService.enabledFor("Anything"), "routing: engine=tspl forces native");
        System.setProperty("invoicestudio.print.engine", "auto");

        // ---- dots per mm --------------------------------------------
        check(TsplPrintService.dotsPerMm("TSC TA210") == 8, "TA210 = 8 dots/mm (203 dpi)");
        check(TsplPrintService.dotsPerMm("TSC TA310") == 12, "TA310 = 12 dots/mm (300 dpi)");

        // ---- one label = one feed -----------------------------------
        FakeTransport tx = new FakeTransport();
        TsplPrintService.setTransport(tx);
        Template t = labelTemplate();
        LabelPrintService.PrintResult r = TsplPrintService.printLabelsNamed(
                t, new Settings(),
                List.of(line("28", "KPT-000028", 1)),
                List.of("size", "code"), "TSC TA210", true);

        check(r.success(), "single label print succeeded: " + r.message());
        check("TSC TA210".equals(tx.printerName), "job went to the selected printer");
        String script = new String(tx.data, StandardCharsets.ISO_8859_1);
        check(script.startsWith("SIZE 432 dot,200 dot\r\n"), "SIZE = strip row in dots (54×25mm @8/mm)");
        check(script.contains("\r\nGAP 24 dot,0 dot\r\n"), "GAP = feed gap in dots (3mm @8/mm)");
        check(script.contains("\r\nDIRECTION 1\r\n"), "DIRECTION 1 = preview-upright print");
        check(script.contains("CLS\r\nBITMAP 0,0,54,200,0,"), "one BITMAP sized 54×200 dots");
        check(script.endsWith("PRINT 1,1\r\n"), "exactly ONE label fed (PRINT 1,1)");
        check(count(script, "PRINT ") == 1, "no hidden extra feeds (1 PRINT total)");
        int payloadStart = script.indexOf("BITMAP 0,0,54,200,0,") + "BITMAP 0,0,54,200,0,".length();
        byte[] payload = new byte[54 * 200];
        System.arraycopy(tx.data, payloadStart, payload, 0, payload.length);
        long ink = blackDots(payload);
        check(ink > 500, "bitmap carries burned ink (" + ink + " black dots)");
        check(ink < payload.length * 8 / 2, "bitmap is not overburned ("
                + (ink * 100 / (payload.length * 8)) + "% black)");
        check(setBits(payload) > payload.length * 8 / 2,
                "polarity: paper encodes as set bits (bit 0 = black on TSC heads)");
        check(r.pages() == 1 && r.labels() == 1, "result reports 1 page / 1 label");

        // save the actual strip row node as PNG for eyeball comparison
        try {
            double[] ps = LabelGeometryService.pageSizeMm(t.labelOrNew());
            Map<Integer, List<LabelGeometryService.LabelSlot>> byPage = LabelGeometryService.slotsByPage(
                    LabelGeometryService.expandSlots(List.of(line("28", "KPT-000028", 1)), t.labelOrNew()));
            Pane page = LabelPrintService.buildStripRowPage(t, new Settings(),
                    byPage.get(0), t.labelOrNew(),
                    ps[0] * LabelRenderUtil.MM_PX, ps[1] * LabelRenderUtil.MM_PX,
                    LabelGeometryService.physicalCellWidth(t.labelOrNew()),
                    LabelGeometryService.physicalCellHeight(t.labelOrNew()), 0);
            javafx.scene.Group cssRoot = new javafx.scene.Group(page);
            javafx.scene.Scene cssScene = new javafx.scene.Scene(cssRoot);
            page.applyCss();
            page.layout();
            java.util.Deque<javafx.scene.Node> stack = new java.util.ArrayDeque<>();
            stack.push(page);
            int depth = 0;
            while (!stack.isEmpty()) {
                javafx.scene.Node n = stack.pop();
                String extra = "";
                if (n instanceof javafx.scene.control.Labeled l) {
                    extra = " text='" + l.getText() + "' font=" + l.getFont().getSize()
                            + " color=" + l.getTextFill();
                }
                System.out.println("[DEBUG] d" + depth + " " + n.getClass().getSimpleName()
                        + " bounds=" + n.getLayoutBounds()
                        + " at " + n.getLayoutX() + "," + n.getLayoutY() + extra);
                if (n instanceof javafx.scene.Parent p) {
                    depth++;
                    p.getChildrenUnmodifiable().forEach(stack::push);
                }
            }
            double fit = Math.min(816.0 / (ps[0] * LabelRenderUtil.MM_PX), 400.0 / (ps[1] * LabelRenderUtil.MM_PX));
            WritableImage img = page.snapshot(null, new WritableImage(
                    (int) (ps[0] * LabelRenderUtil.MM_PX * fit),
                    (int) (ps[1] * LabelRenderUtil.MM_PX * fit)));
            File out = new File("/home/z/my-project/download/ui-screenshots/tspl-strip-row.png");
            out.getParentFile().mkdirs();
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", out);
            passed.add("strip-row PNG exported: " + out.getAbsolutePath());
        } catch (Exception ex) {
            failures.add("PNG export failed: " + ex);
        }

        // ---- copies compress to one PRINT ---------------------------
        tx = new FakeTransport();
        TsplPrintService.setTransport(tx);
        r = TsplPrintService.printLabelsNamed(t, new Settings(),
                List.of(line("28", "KPT-000028", 5)),
                List.of("size", "code"), "TSC TA210", true);
        script = new String(tx.data, StandardCharsets.ISO_8859_1);
        check(r.success() && script.endsWith("PRINT 5,1\r\n"),
                "5 identical copies compress to PRINT 5,1 (one bitmap download)");
        check(count(script, "BITMAP ") == 1, "copies do not duplicate bitmap payload");

        // ---- two different rows stay in order -----------------------
        tx = new FakeTransport();
        TsplPrintService.setTransport(tx);
        r = TsplPrintService.printLabelsNamed(t, new Settings(),
                List.of(line("28", "KPT-000028", 1), line("30", "KPT-000030", 1)),
                List.of("size", "code"), "TSC TA210", true);
        script = new String(tx.data, StandardCharsets.ISO_8859_1);
        check(r.success() && r.pages() == 2 && r.labels() == 2,
                "two different labels = two strip rows / two labels");

        // Diff the two payloads — they must differ (different values!).
        {
            String marker = "BITMAP 0,0,54,200,0,";
            int p1 = script.indexOf(marker) + marker.length();
            int p2 = script.indexOf(marker, p1 + 10800 + 2) + marker.length(); // skip payload 1 + CRLF
            if (p1 > marker.length() && p2 > marker.length() && p2 + 10800 <= tx.data.length) {
                byte[] a = new byte[10800], b = new byte[10800];
                System.arraycopy(tx.data, p1, a, 0, 10800);
                System.arraycopy(tx.data, p2, b, 0, 10800);
                int diff = -1;
                for (int i = 0; i < a.length; i++) if (a[i] != b[i]) { diff = i; break; }
                System.out.println("[DEBUG] payload1 hash=" + java.util.Arrays.hashCode(a)
                        + " (" + blackDots(a) + " black) payload2 hash=" + java.util.Arrays.hashCode(b)
                        + " (" + blackDots(b) + " black)");
                check(diff >= 0, "two rows rasterize differently (first diff byte " + diff + ")");
            } else {
                check(false, "expected two BITMAP payloads in the script (p1=" + p1 + " p2=" + p2 + ")");
            }
        }
        check(script.endsWith("PRINT 1,1\r\n"), "last row printed last");
        check(count(script, "PRINT ") == 2, "two feeds total");
        check(script.indexOf("PRINT ") < script.lastIndexOf("PRINT "), "both PRINTs present in order");

        // ---- transport failure surfaces, no silent data loss --------
        tx = new FakeTransport();
        tx.fail = true;
        TsplPrintService.setTransport(tx);
        r = TsplPrintService.printLabelsNamed(t, new Settings(),
                List.of(line("28", "KPT-000028", 1)),
                List.of("size", "code"), "TSC TA210", true);
        check(!r.success(), "spool failure is reported to the user, not swallowed");
    }
}
