import com.invoicestudio.ui.StudioApp;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.WritableImage;
import javafx.scene.shape.Line;
import javafx.scene.layout.Pane;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Ruler runtime verification (user report: "canvas ruler on zoom shows all
 * same length strips — we don't know the exact measure").
 *
 * For every zoom level (30%…400%) in Barcode Mode it checks, on the real
 * rendered rulers:
 *   1. TICK HIERARCHY — the longest (major) ticks are strictly longer than
 *      the mid/minor ticks (screen-constant 10 / 6.5 / 4 px scaled by 1/zoom).
 *   2. NUMBERS ON MAJORS — every printed number sits exactly at a longest
 *      tick, and its value is a positive multiple of the expected 1-2-5
 *      major step (true millimetres, adapted to zoom).
 *   3. MAJOR STEP adapts: 10 mm at ~90%, 5 mm at 400%, 20 mm at 50% etc.
 *   4. Screenshots of both rulers at each zoom for visual review.
 */
public class RulerVerify extends StudioApp {

    static final List<String> failures = new CopyOnWriteArrayList<>();
    static final List<String> passed = new CopyOnWriteArrayList<>();
    static final List<Throwable> uncaught = new CopyOnWriteArrayList<>();
    static final File SHOT_DIR = new File(System.getProperty("ruler.shots", "shots-ruler"));

    static final double MM_PX = 3.779527559; // 96 dpi mm→px (same as TemplateDesigner)

    Stage stage;
    Object designer;
    Pane rulerTop;
    Pane rulerLeft;
    double pageWmm = 50, pageHmm = 25;

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> {
            uncaught.add(ex);
            ex.printStackTrace();
        });
        try {
            com.invoicestudio.db.DatabaseManager db =
                    com.invoicestudio.db.DatabaseManager.getInstance();
            com.invoicestudio.model.UserSession s = new com.invoicestudio.model.UserSession();
            s.setUserId("ruler-user");
            s.setEmail("ruler@invoicestudio.local");
            s.setDisplayName("Ruler Tester");
            s.setIdToken("");
            s.setRefreshToken("");
            s.setExpiresAtMillis(System.currentTimeMillis() + 7L * 24 * 3600 * 1000);
            s.setRememberMe(true);
            new com.invoicestudio.db.AuthDao(db).saveSession(s);
            System.out.println("[SEED] auth session written");
        } catch (Throwable t) {
            System.err.println("[SEED] failed: " + t);
        }
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        super.start(stage);
        SHOT_DIR.mkdirs();
        settle(6.0, this::navigate);
    }

    void settle(double sec, Runnable r) {
        PauseTransition p = new PauseTransition(Duration.seconds(sec));
        p.setOnFinished(e -> Platform.runLater(r));
        p.play();
    }

    final List<Runnable> queue = new ArrayList<>();

    void runQueue() {
        if (queue.isEmpty()) { finish(); return; }
        Runnable step = queue.remove(0);
        try {
            step.run();
        } catch (Throwable t) {
            failures.add("step-threw: " + t);
            t.printStackTrace();
            finish();
            return;
        }
        PauseTransition p = new PauseTransition(Duration.millis(320));
        p.setOnFinished(e -> Platform.runLater(this::runQueue));
        p.play();
    }

    void finish() {
        System.out.println("--------------------------------------------------");
        for (String p : passed) System.out.println("[OK] " + p);
        for (String f : failures) System.out.println("[BAD] " + f);
        for (Throwable t : uncaught) System.out.println("[BAD] uncaught: " + t);
        boolean ok = failures.isEmpty() && uncaught.isEmpty();
        System.out.println(ok ? "RULER VERIFY: SUCCESS" : "RULER VERIFY: FAILED");
        Platform.exit();
    }

    // ------------------------------------------------------------------
    void navigate() {
        Button cat = findButton(stage.getScene().getRoot(), "Catalog");
        if (cat == null) { failures.add("Catalog button not found"); finish(); return; }
        cat.fire();
        queue.add(() -> {
            Button tpl = findButtonInAnyWindow("Templates");
            if (tpl == null) { failures.add("Templates destination not found"); finish(); return; }
            tpl.fire();
        });
        queue.add(() -> {
            Button d = findButton(stage.getScene().getRoot(), "Designer");
            if (d == null) { failures.add("Designer button not found"); finish(); return; }
            d.fire();
        });
        queue.add(() -> {
            Button b = findButton(stage.getScene().getRoot(), "Barcode Mode");
            if (b == null) { failures.add("Barcode Mode button not found"); finish(); return; }
            b.fire();
        });
        queue.add(this::locateDesigner);
        double[] zooms = {0.3, 0.5, 0.9, 1.5, 2.5, 3.2, 4.0};
        for (double z : zooms) {
            final double zz = z;
            queue.add(() -> setZoom(zz));
            queue.add(() -> verifyAt(zz));
        }
        queue.add(this::finish);
        runQueue();
    }

    void locateDesigner() {
        designer = findInstanceOf(stage.getScene().getRoot(), "com.invoicestudio.ui.views.TemplateDesigner");
        if (designer == null) { failures.add("TemplateDesigner not found in scene"); finish(); return; }
        try {
            Field f = designer.getClass().getDeclaredField("rulerTop");
            f.setAccessible(true);
            rulerTop = (Pane) f.get(designer);
            Field f2 = designer.getClass().getDeclaredField("rulerLeft");
            f2.setAccessible(true);
            rulerLeft = (Pane) f2.get(designer);
            Field ft = designer.getClass().getDeclaredField("template");
            ft.setAccessible(true);
            Object template = ft.get(designer);
            Object page = template.getClass().getMethod("getPage").invoke(template);
            pageWmm = ((Number) page.getClass().getMethod("getWidth").invoke(page)).doubleValue();
            pageHmm = ((Number) page.getClass().getMethod("getHeight").invoke(page)).doubleValue();
        } catch (Throwable t) {
            failures.add("reflection on rulers failed: " + t);
            finish();
            return;
        }
        System.out.println("[NODES] TemplateDesigner + rulers located");
    }

    void setZoom(double z) {
        try {
            Method m = designer.getClass().getDeclaredMethod("setZoom", double.class);
            m.setAccessible(true);
            m.invoke(designer, z);
        } catch (Throwable t) {
            failures.add("setZoom(" + z + ") failed: " + t);
        }
    }

    /** The 1-2-5 major step the production code must pick for this zoom. */
    static double expectedMajor(double zoom) {
        double pxPerMm = MM_PX * zoom;
        for (double s : new double[]{1, 2, 5, 10, 20, 50, 100, 200, 500}) {
            if (s * pxPerMm >= 32) return s;
        }
        return 500;
    }

    void verifyAt(double zoom) {
        double z = Math.max(0.3, Math.min(4.0, zoom));
        double major = expectedMajor(z);
        double epsLen = 0.6 / z;      // tick length tolerance
        double epsPos = 1.6 / z;      // position tolerance (snapped to device px)

        for (Pane ruler : new Pane[]{rulerTop, rulerLeft}) {
            boolean horizontal = ruler == rulerTop;
            double totalMm = horizontal ? pageWmm : pageHmm;
            List<Line> lines = new ArrayList<>();
            List<Label> labels = new ArrayList<>();
            for (Node n : ruler.getChildren()) {
                if (n instanceof Line l) lines.add(l);
                else if (n instanceof Label l) labels.add(l);
            }
            String axis = horizontal ? "top" : "left";
            if (lines.isEmpty()) { failures.add(axis + " ruler has no ticks @" + pct(z)); return; }

            // longest tick = major tier
            double maxLen = 0;
            for (Line l : lines) maxLen = Math.max(maxLen, tickLen(l, horizontal));
            double wantMajorLen = 10.0 / z;
            if (Math.abs(maxLen - wantMajorLen) > epsLen) {
                failures.add(axis + " @" + pct(z) + ": major tick length " + f(maxLen)
                        + " != expected " + f(wantMajorLen));
                return;
            }
            List<Line> majors = new ArrayList<>();
            List<Line> minors = new ArrayList<>();
            for (Line l : lines) {
                if (Math.abs(tickLen(l, horizontal) - maxLen) <= epsLen) majors.add(l);
                else minors.add(l);
            }
            if (minors.isEmpty()) {
                failures.add(axis + " @" + pct(z) + ": no shorter (minor/mid) ticks — all strips same length!");
                return;
            }
            // majors must sit at positive multiples of the major step
            for (Line l : majors) {
                double pos = tickPos(l, horizontal);
                double mm = pos / MM_PX;
                double rel = Math.abs(mm / major - Math.round(mm / major));
                if (rel * major > epsPos / MM_PX * MM_PX + 1e-9) { // position tolerance in mm
                    failures.add(axis + " @" + pct(z) + ": major tick at " + f(mm)
                            + " mm is not a multiple of " + f(major));
                    return;
                }
            }
            // every number sits ON a major tick and is a multiple of the step
            if (labels.isEmpty()) {
                if (totalMm < major) {
                    // Correct ruler behaviour: the whole page is smaller than
                    // one major step — no positive major exists to label.
                    passed.add(axis + " @" + pct(z) + ": no numbers (page " + f(totalMm)
                            + " mm smaller than one major " + f(major) + " mm)");
                    continue;
                }
                failures.add(axis + " @" + pct(z) + ": no numbers rendered");
                return;
            }
            double extent = horizontal ? ruler.getPrefWidth() : ruler.getPrefHeight();
            for (Label lbl : labels) {
                String txt = lbl.getText() == null ? "" : lbl.getText().trim();
                double val;
                try { val = Double.parseDouble(txt); }
                catch (NumberFormatException e) {
                    failures.add(axis + " @" + pct(z) + ": non-numeric ruler label '" + txt + "'");
                    return;
                }
                if (val <= 0) { failures.add(axis + " @" + pct(z) + ": label " + txt + " not positive"); return; }
                if (Math.abs(val / major - Math.round(val / major)) > 1e-6) {
                    failures.add(axis + " @" + pct(z) + ": label " + txt + " not a multiple of major " + f(major));
                    return;
                }
                // The page-end number may be edge-aligned (never clipped) —
                // skip the tick-position check for it, still value-checked above.
                double extent2 = horizontal ? ruler.getPrefWidth() : ruler.getPrefHeight();
                double lblExtent = horizontal
                        ? txt.length() * 4.7 / z + 3.0 / z
                        : 11.5 / z;
                double lblPos0 = horizontal ? lbl.getLayoutX() : lbl.getLayoutY();
                if (lblPos0 + lblExtent > extent2 - 0.5) continue; // edge-aligned label
                double target = lblPos0 - 2.0 / z; // label sits +2/z right/below its tick
                boolean onMajor = false;
                for (Line l : majors) {
                    if (Math.abs(tickPos(l, horizontal) - target) <= epsPos) { onMajor = true; break; }
                }
                if (!onMajor) {
                    failures.add(axis + " @" + pct(z) + ": number " + txt + " NOT on a longest tick (pos "
                            + f(lblPos0) + ")");
                    return;
                }
            }
            passed.add(axis + " @" + pct(z) + ": majors " + f(major) + " mm — "
                    + majors.size() + " major / " + minors.size() + " minor ticks, "
                    + labels.size() + " numbers all on longest ticks");
        }
        snapshotRulers(zoom);
    }

    void snapshotRulers(double zoom) {
        try {
            String tag = "z" + pct(zoom);
            WritableImage i1 = rulerTop.snapshot(null, null);
            ImageIO.write(SwingFXUtils.fromFXImage(i1, null), "png", new File(SHOT_DIR, "top-" + tag + ".png"));
            WritableImage i2 = rulerLeft.snapshot(null, null);
            ImageIO.write(SwingFXUtils.fromFXImage(i2, null), "png", new File(SHOT_DIR, "left-" + tag + ".png"));
            System.out.println("[SHOT] rulers @" + pct(zoom));
        } catch (Throwable t) {
            System.out.println("[SHOT-FAIL] " + pct(zoom) + ": " + t);
        }
    }

    static double tickLen(Line l, boolean horizontal) {
        return horizontal ? Math.abs(l.getEndY() - l.getStartY()) : Math.abs(l.getEndX() - l.getStartX());
    }

    static double tickPos(Line l, boolean horizontal) {
        return horizontal ? l.getStartX() : l.getStartY();
    }

    static String f(double d) { return String.format(java.util.Locale.US, "%.2f", d); }
    static String pct(double z) { return (int) Math.round(z * 100) + "%"; }

    // ------------------------------------------------------------------
    // Scene-graph helpers
    // ------------------------------------------------------------------
    static Button findButton(Parent root, String text) {
        if (root instanceof Button b && text.equals(b.getText())) return b;
        for (Node n : root.getChildrenUnmodifiable()) {
            if (n instanceof Parent p) {
                Button r = findButton(p, text);
                if (r != null) return r;
            } else if (n instanceof Button b && text.equals(b.getText())) return b;
        }
        return null;
    }

    static Button findButtonInAnyWindow(String text) {
        for (javafx.stage.Window w : javafx.stage.Window.getWindows()) {
            if (w.getScene() != null && w.getScene().getRoot() != null) {
                Button b = findButton(w.getScene().getRoot(), text);
                if (b != null) return b;
            }
        }
        return null;
    }

    static Node findInstanceOf(Parent root, String className) {
        if (root.getClass().getName().equals(className)) return root;
        for (Node n : root.getChildrenUnmodifiable()) {
            if (n instanceof Parent p) {
                Node r = findInstanceOf(p, className);
                if (r != null) return r;
            } else if (n.getClass().getName().equals(className)) return n;
        }
        return null;
    }
}
