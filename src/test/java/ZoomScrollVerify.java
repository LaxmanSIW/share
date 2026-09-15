import com.invoicestudio.ui.StudioApp;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Zoom / scroll runtime regression harness.
 *
 * Reproduces the reported regression: "when we zoom, the canvas always comes
 * to centre and we can't scroll horizontally" — measured in BOTH bill mode
 * (A4) and Barcode label mode. For every zoom step it records the designer
 * space point under the viewport centre before and after the zoom; if the
 * anchor holds, the drift is ~0 px. If zoom snaps back to centre the drift
 * explodes to hundreds of px.
 *
 * Exit code 0 = anchor + horizontal range verified in both modes.
 */
public class ZoomScrollVerify extends StudioApp {

    static final List<String> failures = new CopyOnWriteArrayList<>();
    static final List<String> passed = new CopyOnWriteArrayList<>();
    static final List<Throwable> uncaught = new CopyOnWriteArrayList<>();
    static final File SHOT_DIR = new File(System.getProperty("zoom.shots", "shots-zoom"));

    Stage stage;
    ScrollPane canvasSp;
    StackPane wrapper;
    Group scaleG;
    Node container; // unscaled content pane (child of scaleG)
    Node canvasNode; // the white page pane (class bill-sheet-canvas) — exact geometry anchor
    Node viewport; // the ScrollPane's .viewport child — exact viewport origin
    Button zoomIn, zoomOut;
    int seq = 0;

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> recordUncaught(t, ex));
        try {
            com.invoicestudio.db.DatabaseManager db =
                    com.invoicestudio.db.DatabaseManager.getInstance();
            com.invoicestudio.model.UserSession s = new com.invoicestudio.model.UserSession();
            s.setUserId("zoom-user");
            s.setEmail("zoom@invoicestudio.local");
            s.setDisplayName("Zoom Tester");
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

    static void recordUncaught(Thread t, Throwable ex) {
        uncaught.add(ex);
        System.err.println("[UNCAUGHT][" + t.getName() + "] " + ex);
        ex.printStackTrace();
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        super.start(stage);
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if ("JavaFX Application Thread".equals(t.getName())) {
                t.setUncaughtExceptionHandler((th, ex) -> recordUncaught(th, ex));
            }
        }
        SHOT_DIR.mkdirs();
        settle(6.0, () -> { buildQueue(); runQueue(); });
    }

    void settle(double sec, Runnable r) {
        PauseTransition p = new PauseTransition(Duration.seconds(sec));
        p.setOnFinished(e -> r.run());
        p.play();
    }

    // ------------------------------------------------------------------
    // Tiny sequential runner: each step waits a bit so the FX pulse (layout
    // pass + the setZoom runLater anchor) has definitely settled.
    // ------------------------------------------------------------------
    final List<Runnable> queue = new ArrayList<>();
    void runQueue() {
        if (queue.isEmpty()) { finish(); return; }
        Runnable step = queue.remove(0);
        try {
            step.run();
        } catch (Throwable t) {
            failures.add("step-threw: " + t);
            System.out.println("[FAIL] step threw " + t);
            t.printStackTrace();
            finish();
            return;
        }
        PauseTransition p = new PauseTransition(Duration.millis(240));
        p.setOnFinished(e -> Platform.runLater(this::runQueue));
        p.play();
    }

    void finish() {
        System.out.println("--------------------------------------------------");
        for (String p : passed) System.out.println("[OK] " + p);
        for (String f : failures) System.out.println("[BAD] " + f);
        for (Throwable t : uncaught) System.out.println("[BAD] uncaught: " + t);
        boolean ok = failures.isEmpty() && uncaught.isEmpty();
        System.out.println(ok ? "ZOOM VERIFY: SUCCESS" : "ZOOM VERIFY: FAILED");
        Platform.exit();
    }

    // ------------------------------------------------------------------
    // Scenarios
    // ------------------------------------------------------------------
    void buildQueue() {
        // Navigate: Catalog → Templates → Designer
        queue.add(() -> {
            Button cat = findButton(stage.getScene().getRoot(), "Catalog");
            if (cat == null) { failures.add("Catalog button not found"); finish(); return; }
            cat.fire();
        });
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
            if (!locateCanvasNodes()) { finish(); return; }
            zoomIn = findButtonInsideDesigner("+");
            zoomOut = findButtonInsideDesigner("\u2212");
            if (zoomIn == null || zoomOut == null) {
                failures.add("zoom +/- buttons not found (in=" + zoomIn + " out=" + zoomOut + ")");
                finish(); return;
            }
            System.out.println("[NODES] found canvas ScrollPane, wrapper, scale group, zoom buttons");
            shot("bill-0-initial");
            metric("bill-initial");
        });

        billScenario();
        labelScenario();

        queue.add(this::finish);
    }

    void billScenario() {
        queue.add(() -> { canvasSp.setHvalue(1); canvasSp.setVvalue(1); });
        queue.add(() -> { metric("bill-scrolled-br"); shot("bill-1-scrolled-br"); });

        // zoom in 10 steps, checking anchor drift at each step
        addZoomInSteps("bill", 10);
        queue.add(() -> checkHorizontalRange("bill"));
        queue.add(() -> { canvasSp.setHvalue(0); });
        queue.add(() -> { metric("bill-h0-left-edge"); shot("bill-2-h0"); });
        queue.add(() -> checkEdge("bill", "left", 0));
        queue.add(() -> { canvasSp.setHvalue(1); });
        queue.add(() -> { metric("bill-h1-right-edge"); shot("bill-3-h1"); });
        queue.add(() -> checkEdge("bill", "right", 1));
        addZoomOutSteps("bill", 10);
    }

    void labelScenario() {
        queue.add(() -> {
            Button b = findButton(stage.getScene().getRoot(), "Barcode Mode");
            if (b == null) { failures.add("Barcode Mode button not found"); return; }
            b.fire();
        });
        queue.add(() -> { metric("label-initial"); shot("label-0-initial"); });
        queue.add(() -> { canvasSp.setHvalue(1); canvasSp.setVvalue(1); });
        queue.add(() -> { metric("label-scrolled-br"); shot("label-1-scrolled-br"); });
        addZoomInSteps("label", 12);
        queue.add(() -> checkHorizontalRange("label"));
        queue.add(() -> { canvasSp.setHvalue(0); });
        queue.add(() -> { metric("label-h0-left-edge"); shot("label-2-h0"); });
        queue.add(() -> checkEdge("label", "left", 0));
        queue.add(() -> { canvasSp.setHvalue(1); });
        queue.add(() -> { metric("label-h1-right-edge"); shot("label-3-h1"); });
        queue.add(() -> checkEdge("label", "right", 1));
    }

    /** Zooms in n steps; after each step measures anchor drift of the viewport-centre point. */
    void addZoomInSteps(String mode, int n) {
        for (int i = 1; i <= n; i++) {
            final int step = i;
            final double[] pre = new double[2];
            queue.add(() -> { double[] c = centrePoint(); pre[0] = c[0]; pre[1] = c[1]; });
            queue.add(() -> zoomIn.fire());
            queue.add(() -> {
                double[] post = centrePoint();
                double drift = Math.hypot(post[0] - pre[0], post[1] - pre[1]);
                metric(mode + "-zin-" + step);
                boolean ok = drift <= 15.0;
                System.out.printf("[DRIFT] %s zin %d: %.1f px %s%n", mode, step, drift, ok ? "(anchored)" : "(CENTRE SNAP!)");
                (ok ? passed : failures).add(mode + " zoom-in step " + step + " anchor drift " + String.format("%.1f", drift) + " px");
                if (step == n || step == n / 2) shot(mode + "-zin-" + step);
            });
        }
    }

    void addZoomOutSteps(String mode, int n) {
        for (int i = 1; i <= n; i++) {
            final int step = i;
            final double[] pre = new double[2];
            queue.add(() -> { double[] c = centrePoint(); pre[0] = c[0]; pre[1] = c[1]; });
            queue.add(() -> zoomOut.fire());
            queue.add(() -> {
                double[] post = centrePoint();
                double drift = Math.hypot(post[0] - pre[0], post[1] - pre[1]);
                metric(mode + "-zout-" + step);
                boolean ok = drift <= 15.0;
                System.out.printf("[DRIFT] %s zout %d: %.1f px %s%n", mode, step, drift, ok ? "(anchored)" : "(CENTRE SNAP!)");
                (ok ? passed : failures).add(mode + " zoom-out step " + step + " anchor drift " + String.format("%.1f", drift) + " px");
            });
        }
    }

    void checkHorizontalRange(String mode) {
        Bounds vp = canvasSp.getViewportBounds();
        double stackW = Math.max(wrapper.getWidth(), vp.getWidth());
        double rangeW = stackW - vp.getWidth();
        System.out.printf("[RANGE] %s stackW=%.0f vpW=%.0f rangeW=%.0f hmax=%.2f%n",
                mode, stackW, vp.getWidth(), rangeW, canvasSp.getHmax());
        if (rangeW > 40) {
            passed.add(mode + " horizontal scroll range exists (" + (int) rangeW + " px)");
        } else {
            failures.add(mode + " horizontal scroll range ~0 (" + (int) rangeW + " px) — cannot scroll horizontally");
        }
    }

    void checkEdge(String mode, String edge, int hval) {
        double hv = canvasSp.getHvalue();
        if (Math.abs(hv - hval) > 0.001) {
            failures.add(mode + " " + edge + " edge unreachable: hvalue=" + hv + " after setHvalue(" + hval + ")");
            return;
        }
        passed.add(mode + " " + edge + " edge reachable (hvalue=" + hv + ")");
    }

    // ------------------------------------------------------------------
    // Geometry — EXACT: canvas origin via localToScene relative to the
    // ScrollPane .viewport origin; zoom from canvasContainer.getScaleX().
    // (scaleGroup layoutBounds are polluted by guide/legend layers in label
    // mode, so they are NOT used here.)
    // ------------------------------------------------------------------
    double zoomLevel() {
        if (container instanceof javafx.scene.layout.Region r) return r.getScaleX();
        return 1;
    }

    /** Designer-space point currently under the viewport centre. */
    double[] centrePoint() {
        javafx.geometry.Point2D co = canvasNode.localToScene(0, 0);
        javafx.geometry.Point2D vo = viewport.localToScene(0, 0);
        Bounds vp = canvasSp.getViewportBounds();
        double z = zoomLevel();
        // canvas origin relative to viewport top-left, in screen px
        double relX = co.getX() - vo.getX();
        double relY = co.getY() - vo.getY();
        return new double[]{(vp.getWidth() / 2.0 - relX) / z,
                            (vp.getHeight() / 2.0 - relY) / z};
    }

    void metric(String tag) {
        Bounds vp = canvasSp.getViewportBounds();
        javafx.geometry.Point2D co = canvasNode.localToScene(0, 0);
        javafx.geometry.Point2D vo = viewport.localToScene(0, 0);
        double relX = co.getX() - vo.getX();
        double relY = co.getY() - vo.getY();
        javafx.scene.layout.Region cont = (javafx.scene.layout.Region) container;
        Bounds gb = scaleG.getLayoutBounds();
        System.out.printf(Locale.US,
                "[METRIC] %-22s vp=%.0fx%.0f wrap=%.0fx%.0f canvas@vp=(%.0f,%.0f) h=%.3f v=%.3f"
                        + " scaleX=%.3f pref=%.0fx%.0f laid=%.0fx%.0f gb=%.0fx%.0f%n",
                tag, vp.getWidth(), vp.getHeight(), wrapper.getWidth(), wrapper.getHeight(),
                relX, relY, canvasSp.getHvalue(), canvasSp.getVvalue(),
                cont.getScaleX(), cont.getPrefWidth(), cont.getPrefHeight(),
                cont.getWidth(), cont.getHeight(), gb.getWidth(), gb.getHeight());
    }

    // ------------------------------------------------------------------
    // Node discovery
    // ------------------------------------------------------------------
    boolean locateCanvasNodes() {
        Parent root = stage.getScene().getRoot();
        for (ScrollPane sp : findAll(root, ScrollPane.class)) {
            if (sp.getContent() instanceof StackPane stack) {
                for (Node ch : stack.getChildren()) {
                    if (ch instanceof Group g && !g.getChildren().isEmpty()
                            && g.getChildren().get(0) instanceof javafx.scene.layout.Pane pane) {
                        canvasSp = sp; wrapper = stack; scaleG = g; container = pane;
                        viewport = sp.lookup(".viewport");
                        canvasNode = findByStyleClass(pane, "bill-sheet-canvas");
                        if (viewport == null || canvasNode == null) {
                            failures.add("viewport/canvas node not found (viewport=" + viewport
                                    + " canvas=" + canvasNode + ")");
                            return false;
                        }
                        return true;
                    }
                }
            }
        }
        failures.add("canvas ScrollPane not found in scene");
        return false;
    }

    Node findByStyleClass(Node n, String styleClass) {
        if (n.getStyleClass().contains(styleClass)) return n;
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) {
                Node r = findByStyleClass(ch, styleClass);
                if (r != null) return r;
            }
        }
        return null;
    }

    Node designerRoot() {
        for (Node n : new Node[]{stage.getScene().getRoot()}) {
            Node found = findByClass(n, "TemplateDesigner");
            if (found != null) return found;
        }
        return stage.getScene().getRoot();
    }

    Button findButtonInsideDesigner(String text) {
        Button b = findButtonExact(designerRoot(), text);
        return b != null ? b : findButtonExact(stage.getScene().getRoot(), text);
    }

    Button findButton(Parent root, String text) {
        Button exact = findButtonExact(root, text);
        return exact != null ? exact : findButtonContaining(root, text);
    }

    Button findButtonExact(Node n, String text) {
        if (n instanceof Button b && text.equals(b.getText())) return b;
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) {
                Button r = findButtonExact(ch, text);
                if (r != null) return r;
            }
        }
        return null;
    }

    Button findButtonContaining(Node n, String text) {
        if (n instanceof Button b && b.getText() != null && b.getText().contains(text)) return b;
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) {
                Button r = findButtonContaining(ch, text);
                if (r != null) return r;
            }
        }
        return null;
    }

    Node findByClass(Node n, String simpleName) {
        if (n.getClass().getSimpleName().equals(simpleName)) return n;
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) {
                Node r = findByClass(ch, simpleName);
                if (r != null) return r;
            }
        }
        return null;
    }

    <T extends Node> List<T> findAll(Node n, Class<T> type) {
        List<T> out = new ArrayList<>();
        collect(n, type, out);
        return out;
    }

    <T extends Node> void collect(Node n, Class<T> type, List<T> out) {
        if (type.isInstance(n)) out.add(type.cast(n));
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) collect(ch, type, out);
        }
    }

    Button findButtonInAnyWindow(String text) {
        for (Window w : Window.getWindows()) {
            if (w.getScene() != null && w.getScene().getRoot() != null) {
                Button b = findButton(w.getScene().getRoot(), text);
                if (b != null) return b;
            }
        }
        return null;
    }

    void shot(String name) {
        try {
            Scene sc = stage.getScene();
            WritableImage img = sc.snapshot(null);
            File f = new File(SHOT_DIR, String.format("%02d-%s.png", ++seq, name.replaceAll("[^A-Za-z0-9_-]", "_")));
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", f);
            System.out.println("[SHOT] " + f.getAbsolutePath());
        } catch (Exception ex) {
            System.err.println("[SHOT] failed: " + ex);
        }
    }
}
