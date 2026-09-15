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
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Focused runtime verification for the Barcode-Mode fix round:
 *  A) Bulk Print dialog — label preview contained in its card (no spill over header)
 *  B) Bulk Print columns — only variables the template actually uses (+ no unused "batch" var)
 *  C) Variable combo — dropdown shows the FULL comma-separated choices list
 *  D) Label Stock dialog — "Rotate Design 90°" present; clicking it rotates design + zeroes orientation
 * Exit code 0 = all verified, no uncaught exceptions.
 */
public class BarcodeFixVerify extends StudioApp {

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

    // ------------------------------------------------------------------
    // Plan
    // ------------------------------------------------------------------

    void buildPlan() {
        seed();
        plan.add(this::step01OpenDesigner);
        plan.add(this::step02OpenBulkPrint);
        plan.add(this::step03VerifyColumns);
        plan.add(this::step04OpenDropdown);
        plan.add(this::step05ShotAndCloseBulk);
        plan.add(this::step06OpenLabelStock);
        plan.add(this::step07ClickRotate);
        plan.add(this::step08ConfirmStockDialog);
        plan.add(this::step09VerifyRotatedTemplate);
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

            // deliberately NOT used in the template — must not appear in Bulk Print
            VariableDef batch = new VariableDef("batch", "Batch No", "text", false);
            batch.setScope("barcode");
            batch.setChoices("B1,B2");
            vdao.saveVariable(batch);

            label = new Template("tpl_vrf_label", "Verify Label",
                    new PageConfig(PageSizeName.CUSTOM, 50, 25, "portrait", new PageConfig.Margins(0, 0, 0, 0)),
                    new ArrayList<>());
            label.setMode("label");
            LabelConfig cfg = new LabelConfig();
            cfg.setStripWidth(108.0);
            cfg.setColumns(2);
            cfg.setLabelWidth(50.0);
            cfg.setLabelHeight(25.0);
            label.setLabelConfig(cfg);

            TemplateElement name = new TemplateElement();
            name.setId("vrf_name"); name.setType(ElementType.TEXT);
            name.setX(2); name.setY(2); name.setW(46); name.setH(6);
            name.setText("KAPTON"); name.setFontSize(9); name.setFontWeight(700);
            name.setColor("#111111"); name.setZIndex(0);
            label.getElements().add(name);

            TemplateElement sizeTxt = new TemplateElement();
            sizeTxt.setId("vrf_size"); sizeTxt.setType(ElementType.TEXT);
            sizeTxt.setX(2); sizeTxt.setY(9); sizeTxt.setW(46); sizeTxt.setH(5);
            sizeTxt.setText("Size {{size}}"); sizeTxt.setFontSize(7.5);
            sizeTxt.setColor("#333333"); sizeTxt.setZIndex(1);
            label.getElements().add(sizeTxt);

            TemplateElement code = new TemplateElement();
            code.setId("vrf_code"); code.setType(ElementType.BARCODE);
            code.setX(5); code.setY(15); code.setW(40); code.setH(8);
            code.setBarcodeData("{{size}}"); code.setZIndex(2);
            label.getElements().add(code);

            new TemplateDao(db).saveTemplate(label);
            System.out.println("[SEED] label template + 2 barcode variables written");
        } catch (Throwable t) {
            failures.add("seed — " + t);
            t.printStackTrace();
        }
    }

    // ------------------------------------------------------------------
    // Steps
    // ------------------------------------------------------------------

    void step01OpenDesigner() {
        Platform.runLater(() -> {
            try {
                showTemplateDesigner(label);
            } catch (Throwable t) {
                failures.add("designer-open — " + t);
                t.printStackTrace();
            }
        });
        then(r -> countNodes(stage.getScene().getRoot()) > 50,
                "01-designer-open", "designer did not render enough nodes");
    }

    void step02OpenBulkPrint() {
        Button btn = contentButton("Bulk Print");
        asyncFire(btn, "bulk-print-open");
        then(r -> bulkDialog() != null, "02-bulk-print-open", "Bulk Label Print stage not found");
    }

    void step03VerifyColumns() {
        Stage dlg = bulkDialog();
        if (dlg == null) { fail("03-bulk-columns", "no dialog"); next(); return; }
        TableView<?> tv = findFirst(dlg.getScene().getRoot(), TableView.class);
        if (tv == null) {
            fail("03-bulk-columns", "no TableView in bulk dialog");
        } else {
            List<String> headers = new ArrayList<>();
            for (Object c : tv.getColumns()) {
                headers.add(((TableColumn<?, ?>) c).getText());
            }
            boolean hasSize = headers.stream().anyMatch(h -> h.contains("{{size}}"));
            boolean hasBatch = headers.stream().anyMatch(h -> h.contains("{{batch}}"));
            boolean hasCopies = headers.stream().anyMatch(h -> h.contains("Copies"));
            if (hasSize && hasCopies && !hasBatch && headers.size() == 2) {
                pass("03-bulk-columns — only template-used variables: " + headers);
            } else {
                fail("03-bulk-columns", "headers=" + headers + " (expected exactly Size + Copies, no Batch)");
            }
        }
        shotScene(dlg.getScene(), "bulk-dialog");
        next();
    }

    void step04OpenDropdown() {
        Stage dlg = bulkDialog();
        ComboBox<?> combo = findFirst(dlg.getScene().getRoot(), ComboBox.class);
        if (combo == null) { fail("04-dropdown-choices", "no ComboBox found"); next(); return; }
        Platform.runLater(() -> {
            try {
                combo.show();
            } catch (Throwable t) {
                failures.add("04-dropdown-choices — " + t);
            }
        });
        then(r -> {
            if (combo.getItems().size() == 5) {
                pass("04-dropdown-choices — full list visible: " + combo.getItems());
                return true;
            }
            return false;
        }, "04-dropdown-choices", "dropdown items != 5 choices: " + (combo != null ? combo.getItems() : List.of()));
    }

    void step05ShotAndCloseBulk() {
        Stage dlg = bulkDialog();
        if (dlg != null) {
            // hide any open popup first (snapshot the window list — hide()
            // mutates it while we iterate), then screenshot the whole dialog
            List<Window> wins = new ArrayList<>(Window.getWindows());
            for (Window w : wins) {
                if (w instanceof javafx.stage.PopupWindow p) p.hide();
            }
            shotScene(dlg.getScene(), "bulk-dialog-with-prompt");
            Platform.runLater(() -> {
                Button close = buttonInScene(dlg.getScene(), "Close");
                if (close != null) close.fire();
            });
        }
        then(r -> bulkDialog() == null, "05-bulk-close", "bulk dialog still open");
    }

    void step06OpenLabelStock() {
        Button btn = contentButton("Label Stock");
        asyncFire(btn, "label-stock-open");
        then(r -> openDialogPane() != null, "06-label-stock-open", "Label Stock dialog not open");
    }

    void step07ClickRotate() {
        DialogPane dp = openDialogPane();
        if (dp == null) { fail("07-rotate-click", "no dialog"); next(); return; }
        shotScene(dp.getScene(), "label-stock-dialog");
        Button rot = buttonInScene(dp.getScene(), "↻  Rotate Design 90° into print orientation");
        if (rot == null) {
            fail("07-rotate-click", "Rotate Design button not found in Label Stock dialog");
            next();
            return;
        }
        Platform.runLater(rot::fire);
        then(r -> "25.0".equals(String.valueOf(cfg().getLabelWidth())) && "0".equals(cfg().getOrientation()),
                "07-rotate-click", "design not rotated: W=" + cfg().getLabelWidth()
                        + " orient=" + cfg().getOrientation());
    }

    void step08ConfirmStockDialog() {
        DialogPane dp = openDialogPane();
        if (dp == null) { fail("08-stock-ok", "no dialog"); next(); return; }
        shotScene(dp.getScene(), "label-stock-after-rotate");
        Platform.runLater(() -> {
            Button ok = (Button) dp.lookupButton(ButtonType.OK);
            if (ok != null) ok.fire();
        });
        then(r -> openDialogPane() == null, "08-stock-ok", "Label Stock dialog still open");
    }

    void step09VerifyRotatedTemplate() {
        try {
            LabelConfig c = cfg();
            boolean dims = Math.abs(c.getLabelWidth() - 25.0) < 0.01 && Math.abs(c.getLabelHeight() - 50.0) < 0.01;
            boolean orient = "0".equals(c.getOrientation());
            boolean page = Math.abs(label.getPage().getWidth() - 25.0) < 0.01
                    && Math.abs(label.getPage().getHeight() - 50.0) < 0.01;
            TemplateElement nameEl = label.getElements().get(0);
            TemplateElement codeEl = label.getElements().get(2);
            // name (2,2,46,6) → (25-2-6=17, 2, 6, 46); rotation 90
            boolean nameRot = Math.abs(nameEl.getX() - 17.0) < 0.01 && Math.abs(nameEl.getW() - 6.0) < 0.01
                    && Math.abs(nameEl.getH() - 46.0) < 0.01 && nameEl.getRotation() == 90.0;
            // code (5,15,40,8) → (25-15-8=2, 5, 8, 40); rotation 90
            boolean codeRot = Math.abs(codeEl.getX() - 2.0) < 0.01 && Math.abs(codeEl.getY() - 5.0) < 0.01
                    && Math.abs(codeEl.getH() - 40.0) < 0.01 && codeEl.getRotation() == 90.0;
            if (dims && orient && page && nameRot && codeRot) {
                pass("09-rotated-template — canvas 25×50, orientation 0, elements spun 90° CW");
            } else {
                fail("09-rotated-template", "dims=" + dims + " orient=" + orient + " page=" + page
                        + " nameRot=" + nameRot + " codeRot=" + codeRot
                        + " name=" + nameEl.getX() + "," + nameEl.getY() + "," + nameEl.getW() + "," + nameEl.getH()
                        + " r" + nameEl.getRotation());
            }
        } catch (Throwable t) {
            fail("09-rotated-template", String.valueOf(t));
            t.printStackTrace();
        }
        shotScene(stage.getScene(), "10-canvas-after-rotate");
        next();
    }

    // ------------------------------------------------------------------
    // Engine
    // ------------------------------------------------------------------

    void runNext() {
        if (planIdx >= plan.size()) { finish(); return; }
        Runnable step = plan.get(planIdx);
        try {
            step.run();
        } catch (Throwable ex) {
            failures.add("step" + planIdx + " threw: " + ex);
            ex.printStackTrace();
            then(r -> true, "step" + planIdx + "-recovered", "");
        }
    }

    void next() {
        planIdx++;
        PauseTransition p = new PauseTransition(Duration.millis(700));
        p.setOnFinished(e -> runNext());
        p.play();
    }

    /** Waits for a render pause, evaluates verify on the FX thread, logs pass/fail, advances. */
    void then(Predicate<BarcodeFixVerify> verify, String name, String failWhy) {
        PauseTransition p = new PauseTransition(Duration.millis(1300));
        p.setOnFinished(e -> {
            boolean ok = false;
            try {
                ok = verify.test(this);
            } catch (Throwable ex) {
                fail(name, "verify threw: " + ex);
                ex.printStackTrace();
            }
            if (ok) {
                if (failures.stream().noneMatch(f -> f.startsWith(name))) pass(name);
            } else {
                if (failures.stream().noneMatch(f -> f.startsWith(name))) fail(name, failWhy);
            }
            shotScene(stage.getScene(), name);
            next();
        });
        p.play();
    }

    void finish() {
        System.out.println();
        System.out.println("=== BARCODE FIX VERIFY DONE ===");
        System.out.println("PASSED: " + passed.size());
        passed.forEach(s -> System.out.println("  [PASS] " + s));
        System.out.println("FAILED: " + failures.size());
        failures.forEach(s -> System.out.println("  [FAIL] " + s));
        System.out.println("UNCAUGHT: " + uncaught.size());
        uncaught.forEach(t -> System.out.println("  " + t));
        boolean ok = failures.isEmpty() && uncaught.isEmpty();
        System.out.println(ok ? "BARCODE VERIFY: SUCCESS" : "BARCODE VERIFY: FAILED");
        Platform.exit();
        Runtime.getRuntime().halt(ok ? 0 : 1);
    }

    void pass(String n) { passed.add(n); System.out.println("[PASS] " + n); }
    void fail(String n, String why) { failures.add(n + " — " + why); System.out.println("[FAIL] " + n + " — " + why); }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    LabelConfig cfg() { return label.labelOrNew(); }

    Stage bulkDialog() {
        for (Window w : Window.getWindows()) {
            if (w instanceof Stage st && st.getTitle() != null
                    && st.getTitle().startsWith("Bulk Label Print")) return st;
        }
        return null;
    }

    DialogPane openDialogPane() {
        for (Window w : Window.getWindows()) {
            if (w.getScene() != null && w.getScene().getRoot() instanceof DialogPane dp) return dp;
        }
        return null;
    }

    Button contentButton(String text) {
        Node n = stage.getScene().getRoot().lookup(".content-area");
        Button b = n instanceof Parent p ? findButton(p, text) : null;
        if (b == null) throw new IllegalStateException("content button not found: " + text);
        return b;
    }

    Button findButton(Parent root, String exactText) {
        if (root instanceof Button b && exactText.equals(b.getText())) return b;
        for (Node ch : root.getChildrenUnmodifiable()) {
            if (ch instanceof Parent p) {
                Button found = findButton(p, exactText);
                if (found != null) return found;
            } else if (ch instanceof Button b && exactText.equals(b.getText())) {
                return b;
            }
        }
        return null;
    }

    Button buttonInScene(Scene scene, String exactText) {
        return findButton((Parent) scene.getRoot(), exactText);
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
            File f = new File(SHOT_DIR, String.format("bc-%02d-%s.png", ++seq,
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
