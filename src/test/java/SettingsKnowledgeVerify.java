import com.invoicestudio.model.*;
import com.invoicestudio.db.DatabaseManager;
import com.invoicestudio.db.TemplateDao;
import com.invoicestudio.db.VariableDao;
import com.invoicestudio.ui.StudioApp;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Slider;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Labeled;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runtime verification for the TSC print-quality round:
 *  A) Settings → Knowledge tab exists and browses topics (topic switch works)
 *  B) Settings → Print tab carries the Brightness Threshold slider wired to
 *     the live hint + ramp marker
 *  C) the DESIGN CANVAS resolves barcode variables to their FIRST value:
 *     a text element authored as "Size {{size}}" paints as "Size 28"
 * Exit code 0 = all verified, no uncaught exceptions.
 */
public class SettingsKnowledgeVerify extends StudioApp {

    static final List<String> failures = new CopyOnWriteArrayList<>();
    static final List<String> passed = new CopyOnWriteArrayList<>();
    static final List<Throwable> uncaught = new CopyOnWriteArrayList<>();
    static final File SHOT_DIR = new File(System.getProperty("smoke.shots", "screenshots"));

    Stage stage;
    int seq = 0;
    Template label;
    final List<Runnable> plan = new ArrayList<>();
    int planIdx = 0;

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> {
            uncaught.add(ex);
            System.err.println("[UNCAUGHT][" + t.getName() + "] " + ex);
            ex.printStackTrace();
        });
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            UserSession s = new UserSession();
            s.setUserId("smoke-user");
            s.setEmail("smoke@invoicestudio.local");
            s.setDisplayName("Smoke Tester");
            s.setIdToken("");
            s.setRefreshToken("");
            s.setExpiresAtMillis(System.currentTimeMillis() + 7L * 24 * 3600 * 1000);
            s.setRememberMe(true);
            new com.invoicestudio.db.AuthDao(db).saveSession(s);
            System.out.println("[SEED] auth session written");
        } catch (Throwable t) {
            t.printStackTrace();
        }
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        super.start(stage);
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if ("JavaFX Application Thread".equals(t.getName())) {
                t.setUncaughtExceptionHandler((th, ex) -> {
                    uncaught.add(ex);
                    System.err.println("[UNCAUGHT-FX] " + ex);
                    ex.printStackTrace();
                });
            }
        }
        settleThen(6.0, this::buildPlan);
    }

    void buildPlan() {
        seed();
        plan.add(this::step01OpenSettings);
        plan.add(this::step02KnowledgeTab);
        plan.add(this::step03KnowledgeTopicSwitch);
        plan.add(this::step04PrintTabThresholdSlider);
        plan.add(this::step05ThresholdInteractive);
        plan.add(this::step06CanvasResolvesVariables);
        planIdx = 0;
        runNext();
    }

    void seed() {
        try {
            DatabaseManager db = DatabaseManager.getInstance();
            VariableDao vdao = new VariableDao(db);

            VariableDef size = new VariableDef("size", "Size", "text", false);
            size.setScope("barcode");
            size.setChoices("28,S,M,L,XL");
            vdao.saveVariable(size);

            label = new Template("tpl_skv_label", "Knowledge Verify Label",
                    new PageConfig(PageSizeName.CUSTOM, 50, 25, "portrait", new PageConfig.Margins(0, 0, 0, 0)),
                    new ArrayList<>());
            label.setMode("label");
            LabelConfig cfg = new LabelConfig();
            cfg.setStripWidth(108.0);
            cfg.setColumns(2);
            cfg.setLabelWidth(50.0);
            cfg.setLabelHeight(25.0);
            label.setLabelConfig(cfg);

            TemplateElement sizeTxt = new TemplateElement();
            sizeTxt.setId("skv_size");
            sizeTxt.setType(ElementType.TEXT);
            sizeTxt.setX(2); sizeTxt.setY(9); sizeTxt.setW(46); sizeTxt.setH(5);
            sizeTxt.setText("Size {{size}}");
            sizeTxt.setFontSize(7.5);
            sizeTxt.setColor("#111111");
            sizeTxt.setZIndex(0);
            label.getElements().add(sizeTxt);

            TemplateElement code = new TemplateElement();
            code.setId("skv_code");
            code.setType(ElementType.BARCODE);
            code.setX(5); code.setY(15); code.setW(40); code.setH(8);
            code.setBarcodeData("{{size}}");
            code.setZIndex(1);
            label.getElements().add(code);

            new TemplateDao(db).saveTemplate(label);
            System.out.println("[SEED] label template + barcode variable written");
        } catch (Throwable t) {
            failures.add("seed — " + t);
            t.printStackTrace();
        }
    }

    // ------------------------------------------------------------------
    // Steps
    // ------------------------------------------------------------------

    void step01OpenSettings() {
        Button btn = buttonWithText("Settings");
        if (btn == null) { fail("01-settings-open", "sidebar Settings button not found"); next(); return; }
        asyncFire(btn, "settings-open");
        then(r -> tabPaneIn(stage.getScene()) != null, "01-settings-open", "Settings TabPane not found");
    }

    void step02KnowledgeTab() {
        TabPane tp = tabPaneIn(stage.getScene());
        Tab knowledge = null;
        for (Tab t : tp.getTabs()) if ("Knowledge".equals(t.getText())) knowledge = t;
        if (knowledge == null) { fail("02-knowledge-tab", "Knowledge tab missing"); next(); return; }
        TabPane finalTp = tp;
        Tab sel = knowledge;
        Platform.runLater(() -> finalTp.getSelectionModel().select(sel));
        then(r -> {
            ListView<?> lv = findFirst(stage.getScene().getRoot(), ListView.class);
            return lv != null && lv.getItems().size() >= 10;
        }, "02-knowledge-tab", "knowledge topic list not rendered (expected ≥10 topics)");
    }

    void step03KnowledgeTopicSwitch() {
        ListView<?> lv = findFirst(stage.getScene().getRoot(), ListView.class);
        if (lv == null) { fail("03-knowledge-browse", "topic list vanished"); next(); return; }
        // Select the Brightness Threshold topic BY TITLE — the topic list can
        // grow (a "Blank Labels" guide was added) and indexes must not matter.
        int idx = -1;
        for (int i = 0; i < lv.getItems().size(); i++) {
            Object item = lv.getItems().get(i);
            String text = item != null ? item.toString() : "";
            if (text.contains("Brightness Threshold")) { idx = i; break; }
        }
        if (idx < 0) { fail("03-knowledge-browse", "threshold topic missing from list"); next(); return; }
        final int target = idx;
        Platform.runLater(() -> lv.getSelectionModel().select(target));
        then(r -> {
            String big = allLabelText(stage.getScene().getRoot());
            return big.contains("BRIGHTNESS THRESHOLD") || big.contains("sharp black");
        }, "03-knowledge-browse", "switching topics did not show the threshold article");
    }

    void step04PrintTabThresholdSlider() {
        TabPane tp = tabPaneIn(stage.getScene());
        Tab print = null;
        for (Tab t : tp.getTabs()) if ("Print".equals(t.getText())) print = t;
        if (print == null) { fail("04-print-threshold", "Print tab missing"); next(); return; }
        TabPane finalTp = tp;
        Tab sel = print;
        Platform.runLater(() -> finalTp.getSelectionModel().select(sel));
        then(r -> {
            Slider s = findFirst(stage.getScene().getRoot(), Slider.class);
            return s != null && Math.abs(s.getMax() - 255) < 0.01 && Math.abs(s.getValue() - 150) < 0.01;
        }, "04-print-threshold", "threshold slider missing or not defaulted to 150");
    }

    void step05ThresholdInteractive() {
        Slider s = findFirst(stage.getScene().getRoot(), Slider.class);
        if (s == null) { fail("05-threshold-interactive", "slider vanished"); next(); return; }
        Platform.runLater(() -> s.setValue(180));
        then(r -> {
            String big = allLabelText(stage.getScene().getRoot());
            boolean hint = big.contains("≤ 180 burn sharp black");
            Label val = valueLabelNear(stage.getScene().getRoot());
            return hint && val != null && "180".equals(val.getText());
        }, "05-threshold-interactive", "hint/value did not follow the slider to 180");
    }

    void step06CanvasResolvesVariables() {
        Platform.runLater(() -> {
            try {
                showTemplateDesigner(label);
            } catch (Throwable t) {
                failures.add("designer-open — " + t);
                t.printStackTrace();
            }
        });
        then(r -> {
            if (countNodes(stage.getScene().getRoot()) < 50) return false;
            // The CANVAS must paint the resolved value. Raw "{{size}}" may
            // legitimately appear in the layers/properties panels (they show
            // the editable source text) — so this is a positive-only check.
            return allLabelText(stage.getScene().getRoot()).contains("Size 28");
        }, "06-canvas-resolves-variables", "canvas did not resolve {{size}} to first value 28");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    void next() {
        PauseTransition p = new PauseTransition(Duration.millis(700));
        p.setOnFinished(e -> runNext());
        p.play();
    }

    void then(java.util.function.Predicate<SettingsKnowledgeVerify> verify, String name, String failWhy) {
        PauseTransition p = new PauseTransition(Duration.millis(1300));
        p.setOnFinished(e -> {
            boolean ok = false;
            try {
                ok = verify.test(this);
            } catch (Throwable ex) {
                fail(name, "verify threw: " + ex);
                ex.printStackTrace();
            }
            if (ok) pass(name);
            else fail(name, failWhy);
            shotScene(stage.getScene(), name);
            next();
        });
        p.play();
    }

    void runNext() {
        if (planIdx >= plan.size()) { finish(); return; }
        Runnable step = plan.get(planIdx++);
        Platform.runLater(() -> {
            try {
                step.run();
            } catch (Throwable t) {
                failures.add("step-crash — " + t);
                t.printStackTrace();
                next();
            }
        });
    }

    void finish() {
        System.out.println();
        System.out.println("=== SETTINGS / KNOWLEDGE VERIFY DONE ===");
        System.out.println("PASSED: " + passed.size());
        passed.forEach(s -> System.out.println("  [PASS] " + s));
        System.out.println("FAILED: " + failures.size());
        failures.forEach(s -> System.out.println("  [FAIL] " + s));
        System.out.println("UNCAUGHT (any thread): " + uncaught.size());
        uncaught.forEach(t -> System.out.println("  - " + t));
        boolean ok = failures.isEmpty() && uncaught.isEmpty();
        System.out.println(ok ? "SETTINGS KNOWLEDGE VERIFY: SUCCESS" : "SETTINGS KNOWLEDGE VERIFY: FAILED");
        Platform.exit();
        Runtime.getRuntime().halt(ok ? 0 : 1);
    }

    void pass(String n) { passed.add(n); System.out.println("[PASS] " + n); }
    void fail(String n, String why) { failures.add(n + " — " + why); System.out.println("[FAIL] " + n + " — " + why); }

    TabPane tabPaneIn(Scene scene) {
        return scene == null ? null : findFirst(scene.getRoot(), TabPane.class);
    }

    Button buttonWithText(String text) {
        List<Button> out = new ArrayList<>();
        collect(stage.getScene().getRoot(), Button.class, out);
        for (Button b : out) {
            if (text.equalsIgnoreCase(b.getText() != null ? b.getText().trim() : "")) return b;
        }
        return null;
    }

    /** Concatenated text of every Labeled in the tree (canvas text included). */
    String allLabelText(Node root) {
        List<Labeled> out = new ArrayList<>();
        collect(root, Labeled.class, out);
        StringBuilder sb = new StringBuilder();
        for (Labeled l : out) {
            if (l.getText() != null) sb.append(l.getText()).append("\n");
        }
        return sb.toString();
    }

    /** The gold value label sitting next to the threshold slider. */
    Label valueLabelNear(Node root) {
        List<Label> out = new ArrayList<>();
        collect(root, Label.class, out);
        for (Label l : out) {
            if (l.getStyle() != null && l.getStyle().contains("#d9a13b")
                    && l.getText() != null && l.getText().matches("\\d{1,3}")) return l;
        }
        return null;
    }

    void asyncFire(Button btn, String what) {
        Platform.runLater(() -> {
            try {
                btn.fire();
            } catch (Throwable t) {
                fail(what, String.valueOf(t));
                t.printStackTrace();
            }
        });
    }

    static <T extends Node> T findFirst(Node root, Class<T> type) {
        if (type.isInstance(root)) return type.cast(root);
        if (root instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) {
                T found = findFirst(ch, type);
                if (found != null) return found;
            }
        }
        return null;
    }

    static <T extends Node> void collect(Node root, Class<T> type, List<T> out) {
        if (type.isInstance(root)) out.add(type.cast(root));
        if (root instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) {
                collect(ch, type, out);
            }
        }
    }

    static int countNodes(Node n) {
        if (n == null) return 0;
        int c = 1;
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) c += countNodes(ch);
        }
        return c;
    }

    void shotScene(Scene scene, String base) {
        try {
            if (scene == null) return;
            if (!SHOT_DIR.exists()) SHOT_DIR.mkdirs();
            double w = Math.max(1, scene.getWidth()), h = Math.max(1, scene.getHeight());
            WritableImage img = scene.snapshot(new WritableImage((int) w, (int) h));
            File f = new File(SHOT_DIR, String.format("sk-%02d-%s.png", ++seq,
                    base.replaceAll("[^A-Za-z0-9_-]", "_")));
            ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", f);
            System.out.println("[SHOT] " + f.getAbsolutePath());
        } catch (Throwable t) {
            System.out.println("[SHOT-FAIL] " + base + ": " + t);
        }
    }

    void settleThen(double seconds, Runnable r) {
        PauseTransition p = new PauseTransition(Duration.seconds(seconds));
        p.setOnFinished(e -> r.run());
        p.play();
    }
}
