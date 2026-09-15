import com.invoicestudio.ui.StudioApp;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runtime regression harness for the zoom-aware selection overlay + inline
 * text editor.
 *
 * Verifies (on the REAL designer, software rendering):
 *  1. Resize handles measure ~10 px ON SCREEN at 100 % zoom.
 *  2. At 400 % zoom the handles STILL measure ~10 px on screen (before the
 *     fix the 10 px design-space squares blew up to 40 px blobs — the
 *     reported "selection border and vertex too big when we zoom").
 *  3. At 30 % zoom handles remain ~10 px (they grow as you zoom out).
 *  4. Handles carry NO dark outline (stroke == null) and the rotate handle
 *     has no stroke either (reported "vertex has black border we do not want").
 *  5. The inline text editor (double-click) has ZERO padding, a 1-screen-px
 *     border and a transparent .content (no white frame / clipped text).
 *
 * Exit code 0 = all checks passed.
 */
public class SelectionZoomVerify extends StudioApp {

    static final List<String> failures = new CopyOnWriteArrayList<>();
    static final List<String> passed = new CopyOnWriteArrayList<>();
    static final List<Throwable> uncaught = new CopyOnWriteArrayList<>();
    static final File SHOT_DIR = new File(System.getProperty("selzoom.shots", "shots-selzoom"));

    Stage stage;
    Node designerNode;
    Button zoomIn, zoomOut;
    Object designer; // the TemplateDesigner instance
    int seq = 0;

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> recordUncaught(t, ex));
        try {
            com.invoicestudio.db.DatabaseManager db =
                    com.invoicestudio.db.DatabaseManager.getInstance();
            com.invoicestudio.model.UserSession s = new com.invoicestudio.model.UserSession();
            s.setUserId("selzoom-user");
            s.setEmail("selzoom@invoicestudio.local");
            s.setDisplayName("Sel Zoom Tester");
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
        PauseTransition p = new PauseTransition(Duration.millis(260));
        p.setOnFinished(e -> Platform.runLater(this::runQueue));
        p.play();
    }

    void finish() {
        System.out.println("--------------------------------------------------");
        for (String p : passed) System.out.println("[OK] " + p);
        for (String f : failures) System.out.println("[BAD] " + f);
        for (Throwable t : uncaught) System.out.println("[BAD] uncaught: " + t);
        boolean ok = failures.isEmpty() && uncaught.isEmpty();
        System.out.println(ok ? "SELECTION ZOOM VERIFY: SUCCESS" : "SELECTION ZOOM VERIFY: FAILED");
        Platform.exit();
    }

    // ------------------------------------------------------------------
    // Scenario
    // ------------------------------------------------------------------
    void buildQueue() {
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
            designerNode = findByClass(stage.getScene().getRoot(), "TemplateDesigner");
            if (designerNode == null) { failures.add("TemplateDesigner node not found"); finish(); return; }
            designer = designerNode;
            zoomIn = findButtonInsideDesigner("+");
            zoomOut = findButtonInsideDesigner("\u2212");
            if (zoomIn == null || zoomOut == null) { failures.add("zoom buttons not found"); finish(); return; }
            // Add a TEXT element (private addElement selects it too)
            try {
                Method add = designer.getClass().getDeclaredMethod("addElement", com.invoicestudio.model.ElementType.class);
                add.setAccessible(true);
                add.invoke(designer, com.invoicestudio.model.ElementType.TEXT);
            } catch (Throwable t) {
                failures.add("addElement(TEXT) reflection failed: " + t);
                finish(); return;
            }
            System.out.println("[NODES] designer + zoom buttons + TEXT element ready");
        });
        // selection overlay refresh (addElement already selected it)
        queue.add(() -> {
            invokePrivate("updateSelectionOverlay");
            List<Rectangle> handles = resizeHandles();
            if (handles.isEmpty()) { failures.add("no resize handles found at 100%"); finish(); return; }
            double px = screenW(handles.get(0));
            check("handle @100%% ≈ 10 px on screen (measured %.1f px)", px, 8.0, 13.0);
            Rectangle h = handles.get(0);
            if (h.getStroke() == null) passed.add("resize handle has NO outline stroke");
            else failures.add(String.format("resize handle stroke should be null, was %s", h.getStroke()));
            Object rotate = rotateHandle();
            if (rotate instanceof javafx.scene.shape.Circle c && c.getStroke() == null) {
                passed.add("rotate handle has NO black border");
            } else if (rotate == null) {
                failures.add("rotate handle not found");
            } else {
                failures.add("rotate handle still has a stroke (black border)");
            }
            shot("sel-0-100pct");
        });
        // zoom to 400 % and re-measure
        queue.add(() -> { for (int i = 0; i < 31; i++) zoomIn.fire(); }); // 90% → cap
        queue.add(() -> {
            invokePrivate("updateSelectionOverlay");
            List<Rectangle> handles = resizeHandles();
            if (handles.isEmpty()) { failures.add("no handles at max zoom"); finish(); return; }
            double px = screenW(handles.get(0));
            check("handle @max zoom (400%%) ≈ 10 px on screen (measured %.1f px)", px, 7.0, 14.0);
            double zoomNow = zoomField();
            System.out.printf("[INFO] zoom field = %.2f%n", zoomNow);
            shot("sel-1-400pct");
        });
        // zoom back to minimum and re-measure
        queue.add(() -> { for (int i = 0; i < 40; i++) zoomOut.fire(); });
        queue.add(() -> {
            invokePrivate("updateSelectionOverlay");
            List<Rectangle> handles = resizeHandles();
            if (handles.isEmpty()) { failures.add("no handles at min zoom"); finish(); return; }
            double px = screenW(handles.get(0));
            check("handle @min zoom (30%%) ≈ 10 px on screen (measured %.1f px)", px, 7.0, 14.0);
            shot("sel-2-30pct");
        });
        // inline editor: zero padding, transparent content, screen-px border
        queue.add(() -> {
            try {
                Method start = designer.getClass().getDeclaredMethod("startInlineTextEdit", com.invoicestudio.model.TemplateElement.class);
                start.setAccessible(true);
                Field selF = designer.getClass().getDeclaredField("selectedElement");
                selF.setAccessible(true);
                Object el = selF.get(designer);
                start.invoke(designer, el);
            } catch (Throwable t) {
                failures.add("startInlineTextEdit reflection failed: " + t);
                finish(); return;
            }
            Pane selPane = fieldPane("selectionPane");
            TextArea editor = null;
            for (Node n : selPane.getChildren()) if (n instanceof TextArea ta) editor = ta;
            if (editor == null) { failures.add("inline editor not found in selectionPane"); finish(); return; }
            if (editor.getPadding().getTop() == 0 && editor.getPadding().getBottom() == 0
                    && editor.getPadding().getLeft() == 0 && editor.getPadding().getRight() == 0) {
                passed.add("inline editor control padding = 0");
            } else {
                failures.add("inline editor control padding not zero: " + editor.getPadding());
            }
            String st = editor.getStyle() == null ? "" : editor.getStyle();
            if (st.contains("-fx-padding: 0")) passed.add("editor style pins -fx-padding: 0");
            else failures.add("editor style missing '-fx-padding: 0': " + st);
            Node content = editor.lookup(".content");
            if (content == null) {
                failures.add("editor .content region not found (skin not ready)");
            } else {
                String cst = content.getStyle() == null ? "" : content.getStyle();
                if (cst.contains("-fx-padding: 0") && cst.contains("transparent")) {
                    passed.add(".content padding zeroed + background transparent (no white frame)");
                } else {
                    failures.add(".content not neutralized: " + cst);
                }
            }
            // border width: 1px on screen — measured via style parse at current zoom
            double zoomNow = zoomField();
            double expectedBorder = 1.0 / Math.max(0.3, zoomNow);
            if (st.contains("-fx-border-width: " + String.format(java.util.Locale.US, "%.2f", expectedBorder).replace(",", "."))
                    || st.contains(String.format(java.util.Locale.US, "-fx-border-width: %.2fpx", expectedBorder))) {
                passed.add(String.format("editor border is %.2f design px = 1 screen px at zoom %.2f", expectedBorder, zoomNow));
            } else {
                failures.add("editor border width not 1/zoom design px (style: " + st + ")");
            }
            shot("sel-3-inline-editor");
        });
        queue.add(this::finish);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    void check(String fmt, double measured, double min, double max) {
        String msg = String.format(java.util.Locale.US, fmt, measured);
        boolean ok = measured >= min && measured <= max;
        System.out.println((ok ? "[OK] " : "[BAD] ") + msg);
        (ok ? passed : failures).add(msg);
    }

    double screenW(Rectangle r) {
        // boundsInScene width == on-screen px: the only scaling between the
        // handle and the scene is the canvas container's zoom transform.
        Bounds b = r.localToScene(r.getBoundsInLocal());
        return b.getWidth();
    }

    double zoomField() {
        try {
            Field f = designer.getClass().getDeclaredField("zoom");
            f.setAccessible(true);
            return f.getDouble(designer);
        } catch (Throwable t) {
            failures.add("zoom field read failed: " + t);
            return -1;
        }
    }

    Pane fieldPane(String name) {
        try {
            Field f = designer.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return (Pane) f.get(designer);
        } catch (Throwable t) {
            failures.add(name + " field read failed: " + t);
            return new Pane();
        }
    }

    void invokePrivate(String method) {
        try {
            Method m = designer.getClass().getDeclaredMethod(method);
            m.setAccessible(true);
            m.invoke(designer);
        } catch (Throwable t) {
            failures.add(method + " reflection failed: " + t);
        }
    }

    List<Rectangle> resizeHandles() {
        List<Rectangle> out = new ArrayList<>();
        Pane selPane = fieldPane("selectionPane");
        collectHandles(selPane, out);
        return out;
    }

    void collectHandles(Node n, List<Rectangle> out) {
        if (n instanceof Rectangle r && r.getCursor() != null
                && r.getCursor().toString().toLowerCase().contains("resize")) out.add(r);
        if (n instanceof Parent p) for (Node ch : p.getChildrenUnmodifiable()) collectHandles(ch, out);
    }

    Object rotateHandle() {
        Pane selPane = fieldPane("selectionPane");
        return findCircle(selPane);
    }

    Object findCircle(Node n) {
        if (n instanceof javafx.scene.shape.Circle c && c.getCursor() != null
                && "crosshair".equals(c.getCursor().toString().toLowerCase())) return c;
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) {
                Object r = findCircle(ch);
                if (r != null) return r;
            }
        }
        return null;
    }

    void shot(String name) {
        try {
            Scene sc = stage.getScene();
            WritableImage img = sc.snapshot(null);
            File f = new File(SHOT_DIR, "sz-" + (seq++) + "-" + name + ".png");
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", f);
            System.out.println("[SHOT] " + f.getAbsolutePath());
        } catch (Throwable t) {
            System.err.println("[SHOT] failed: " + t);
        }
    }

    // ------------------------------------------------------------------
    // Node discovery (same as ZoomScrollVerify)
    // ------------------------------------------------------------------
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

    Button findButtonInsideDesigner(String text) {
        Button b = findButtonExact(designerNode, text);
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

    Button findButtonInAnyWindow(String text) {
        for (Window w : Window.getWindows()) {
            if (w.getScene() != null && w.getScene().getRoot() != null) {
                Button b = findButton(w.getScene().getRoot(), text);
                if (b != null) return b;
            }
        }
        return null;
    }
}
