import com.invoicestudio.model.LabelConfig;
import com.invoicestudio.model.Template;
import com.invoicestudio.ui.StudioApp;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Pane;
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
 * Runtime regression harness for the BarTender-style Label Stock dialog.
 *
 * Verifies (on the REAL designer, software rendering):
 *  1. The dialog opens in Barcode Mode and shows PHYSICAL stock numbers:
 *     a config saved as design 35×75 with 90° artwork prints renders as
 *     "Label on strip 75.0 × 36.0 mm" — matching BarTender's Page Setup
 *     and the physical roll (the reported orientation confusion).
 *  2. The live strip diagram actually draws (liner + label + feed-gap
 *     marker + second-row peek).
 *  3. Round-trip safety: opening the dialog and pressing OK rewrites the
 *     EXACT same config (no silent swaps of width/height/orientation).
 *  4. Editing physical size 75×36 → 60×40 with "no rotation" writes
 *     design 60×40 orientation 0, preserving the manual paper width.
 *  5. Auto-fit paper width snaps the liner to labels+gaps+margins (61 mm).
 *
 * Exit code 0 = all checks passed.
 */
public class LabelStockDialogVerify extends StudioApp {

    static final List<String> failures = new CopyOnWriteArrayList<>();
    static final List<String> passed = new CopyOnWriteArrayList<>();
    static final List<Throwable> uncaught = new CopyOnWriteArrayList<>();
    static final File SHOT_DIR = new File(System.getProperty("labelstock.shots", "shots-labelstock"));

    javafx.stage.Stage stage;
    Node designerNode;
    Object designer;
    int seq = 0;

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((t, ex) -> recordUncaught(t, ex));
        try {
            com.invoicestudio.db.DatabaseManager db =
                    com.invoicestudio.db.DatabaseManager.getInstance();
            com.invoicestudio.model.UserSession s = new com.invoicestudio.model.UserSession();
            s.setUserId("labelstock-user");
            s.setEmail("labelstock@invoicestudio.local");
            s.setDisplayName("Label Stock Tester");
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
    public void start(javafx.stage.Stage stage) {
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
        PauseTransition p = new PauseTransition(Duration.millis(300));
        p.setOnFinished(e -> Platform.runLater(this::runQueue));
        p.play();
    }

    void finish() {
        System.out.println("--------------------------------------------------");
        for (String p : passed) System.out.println("[OK] " + p);
        for (String f : failures) System.out.println("[BAD] " + f);
        for (Throwable t : uncaught) System.out.println("[BAD] uncaught: " + t);
        boolean ok = failures.isEmpty() && uncaught.isEmpty();
        System.out.println(ok ? "LABEL STOCK VERIFY: SUCCESS" : "LABEL STOCK VERIFY: FAILED");
        Platform.exit();
        if (!ok) System.exit(1);
    }

    // ------------------------------------------------------------------
    // Scenario
    // ------------------------------------------------------------------
    void buildQueue() {
        // navigate: Catalog → Templates → Designer
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
            // enter Barcode Mode (public shortcut → toggleBarcodeMode)
            try {
                Method m = designer.getClass().getDeclaredMethod("enterBarcodeModeFromShortcut");
                m.setAccessible(true);
                m.invoke(designer);
            } catch (Throwable t) {
                failures.add("enterBarcodeModeFromShortcut failed: " + t);
                finish(); return;
            }
        });
        // seed the user's real 77 mm roll config (design 35×75, artwork 90°)
        queue.add(() -> {
            Template t = template();
            if (t == null) { failures.add("template field not reachable"); finish(); return; }
            LabelConfig c = t.labelOrNew();
            c.setStripWidth(77.0);
            c.setColumns(1);
            c.setLabelWidth(35.0);
            c.setLabelHeight(75.0);
            c.setGapX(3.0);
            c.setGapY(3.0);
            c.setCornerRadius(2.0);
            c.setOrientation("90");
            c.setMarginL(0.5);
            c.setMarginR(0.5);
            c.sanitize();
            invokePrivate("syncPageFromLabelConfig");
            System.out.println("[SEED] cfg = design 35x75 @ 90°, strip 77, 1 across");
        });
        // ── Session 1: open the dialog ──
        queue.add(() -> Platform.runLater(() -> invokePrivate("showLabelSettingsDialog")));
        queue.add(() -> {}); // settle steps while the modal pumps
        queue.add(() -> {});
        queue.add(() -> {
            Parent dlgRoot = dialogRoot();
            if (dlgRoot == null) { failures.add("Label Stock dialog did not open"); finish(); return; }
            CheckBox auto = checkboxContaining(dlgRoot, "Auto-fit paper width");
            if (auto == null) failures.add("Auto-fit paper width checkbox missing");
            else if (!auto.isSelected()) passed.add("auto-fit OFF for a manual 77 mm liner (preserved)");
            else failures.add("auto-fit should start OFF when saved width ≠ required");

            if (!spinnersContain(dlgRoot, 75.0)) failures.add("physical width spinner 75.0 not found (design 35×75 @90°)");
            else passed.add("physical width 75.0 shown for design 35×75 @ 90° (BarTender-style)");
            if (!spinnersContain(dlgRoot, 35.0)) failures.add("physical height spinner 35.0 not found (design width 35 → physical height 35)");
            else passed.add("physical height 35.0 shown (design 35×75 @ 90° → 75 across × 35 feed)");
            if (!spinnersContain(dlgRoot, 77.0)) failures.add("paper width spinner 77.0 not found");
            else passed.add("paper (liner) width 77.0 shown");

            ComboBox<?> orient = comboBoxContaining(dlgRoot, "Prints as designed");
            if (orient == null) failures.add("Artwork direction combo missing");
            else if ("Rotates 90° clockwise at print".equals(orient.getSelectionModel().getSelectedItem()))
                passed.add("artwork direction shows 'Rotates 90° clockwise at print'");
            else failures.add("artwork combo selection wrong: " + orient.getSelectionModel().getSelectedItem());

            Label fits = labelContaining(dlgRoot, "Labels need 76.0 mm");
            if (fits != null && fits.getText().contains("✓")) passed.add("fits line: ✓ Labels need 76.0 mm; paper 77.0");
            else failures.add("fits line wrong/not found: " + (fits == null ? "null" : fits.getText()));

            Pane diag = diagramPane(dlgRoot);
            if (diag == null) failures.add("strip diagram pane not found");
            else if (diag.getChildren().size() >= 4) passed.add("strip diagram drew " + diag.getChildren().size() + " nodes (liner+label+gap+peek)");
            else failures.add("strip diagram too empty: " + diag.getChildren().size());

            Label cap = labelContaining(dlgRoot, "Label on strip 75.0 × 35.0 mm");
            if (cap != null) passed.add("caption shows 'Label on strip 75.0 × 35.0 mm'");
            else failures.add("BarTender-style caption not found");

            shotDialog("labelstock-1-open");
        });
        // round-trip: OK must rewrite the identical config
        queue.add(() -> {
            Button ok = findButtonInAnyWindow("OK");
            if (ok == null) { failures.add("dialog OK button not found"); finish(); return; }
            ok.fire();
        });
        queue.add(() -> {
            LabelConfig c = template().labelOrNew();
            boolean same = Math.abs(c.getLabelWidth() - 35.0) < 1e-9
                    && Math.abs(c.getLabelHeight() - 75.0) < 1e-9
                    && "90".equals(c.getOrientation())
                    && Math.abs(c.getStripWidth() - 77.0) < 1e-9
                    && c.getColumns() == 1;
            if (same) passed.add("round-trip: OK rewrote the identical config (35×75 design, 90°, strip 77)");
            else failures.add("round-trip broke config: " + c.getLabelWidth() + "x" + c.getLabelHeight()
                    + " @" + c.getOrientation() + " strip " + c.getStripWidth());
        });
        // ── Session 2: physical edits, no rotation ──
        queue.add(() -> Platform.runLater(() -> invokePrivate("showLabelSettingsDialog")));
        queue.add(() -> {});
        queue.add(() -> {});
        queue.add(() -> {
            Parent dlgRoot = dialogRoot();
            if (dlgRoot == null) { failures.add("dialog did not re-open (session 2)"); finish(); return; }
            ComboBox<?> orient = comboBoxContaining(dlgRoot, "Prints as designed");
            orient.getSelectionModel().select(0); // "Prints as designed (no rotation)"
            setSpinner(dlgRoot, 75.0, 60.0);
            setSpinner(dlgRoot, 35.0, 40.0);
            Label cap = labelContaining(dlgRoot, "Label on strip 60.0 × 40.0 mm");
            if (cap != null) passed.add("caption updates live: 'Label on strip 60.0 × 40.0 mm'");
            else failures.add("caption did not update after physical edits");
            Label art = labelContaining(dlgRoot, "no rotation");
            if (art != null) passed.add("artwork caption switches to 'no rotation' for 0°");
            else failures.add("artwork caption not updated for 0°");
            shotDialog("labelstock-2-edited");
            findButtonInAnyWindow("OK").fire();
        });
        queue.add(() -> {
            LabelConfig c = template().labelOrNew();
            boolean ok = Math.abs(c.getLabelWidth() - 60.0) < 1e-9
                    && Math.abs(c.getLabelHeight() - 40.0) < 1e-9
                    && "0".equals(c.getOrientation())
                    && Math.abs(c.getStripWidth() - 77.0) < 1e-9;
            if (ok) passed.add("physical edits map back: design 60×40 @ 0°, manual paper width kept at 77");
            else failures.add("session 2 write-back wrong: " + c.getLabelWidth() + "x" + c.getLabelHeight()
                    + " @" + c.getOrientation() + " strip " + c.getStripWidth());
        });
        // ── Session 3: auto-fit paper width ──
        queue.add(() -> Platform.runLater(() -> invokePrivate("showLabelSettingsDialog")));
        queue.add(() -> {});
        queue.add(() -> {});
        queue.add(() -> {
            Parent dlgRoot = dialogRoot();
            if (dlgRoot == null) { failures.add("dialog did not re-open (session 3)"); finish(); return; }
            CheckBox auto = checkboxContaining(dlgRoot, "Auto-fit paper width");
            if (auto == null) { failures.add("auto checkbox missing (session 3)"); finish(); return; }
            // CheckBox.fire() TOGGLES the state — one fire turns unchecked →
            // checked AND fires the ActionEvent that runs the auto-fit.
            auto.fire();
            // required = 0.5 + 60 + 0.5 = 61.0
            if (spinnersContain(dlgRoot, 61.0)) passed.add("auto-fit snapped paper width to 61.0 mm (0.5+60+0.5)");
            else failures.add("auto-fit did not snap paper width to 61.0");
            shotDialog("labelstock-3-autofit");
            findButtonInAnyWindow("OK").fire();
        });
        queue.add(() -> {
            LabelConfig c = template().labelOrNew();
            if (Math.abs(c.getStripWidth() - 61.0) < 1e-9)
                passed.add("auto-fit saved: strip width 61.0 mm");
            else failures.add("auto-fit not saved: strip " + c.getStripWidth());
        });
        queue.add(this::finish);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    Template template() {
        try {
            Field f = designer.getClass().getDeclaredField("template");
            f.setAccessible(true);
            return (Template) f.get(designer);
        } catch (Throwable t) {
            return null;
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

    Parent dialogRoot() {
        for (Window w : Window.getWindows()) {
            Scene sc = w.getScene();
            if (sc == null || sc.getRoot() == null) continue;
            if (checkboxContaining(sc.getRoot(), "Auto-fit paper width") != null) return sc.getRoot();
        }
        return null;
    }

    Pane diagramPane(Parent root) {
        List<Pane> all = new ArrayList<>();
        collectPanes(root, all);
        Pane best = null;
        for (Pane p : all) {
            String st = p.getStyle() == null ? "" : p.getStyle();
            if (st.contains("1E293B") && p.getChildren().size() >= 4) best = p;
        }
        return best;
    }

    void collectPanes(Node n, List<Pane> out) {
        if (n instanceof Pane p) out.add(p);
        if (n instanceof Parent p) for (Node ch : p.getChildrenUnmodifiable()) collectPanes(ch, out);
    }

    boolean spinnersContain(Parent root, double val) {
        List<Spinner<?>> sp = new ArrayList<>();
        collectSpinners(root, sp);
        for (Spinner<?> s : sp) {
            Object v = s.getValue();
            if (v instanceof Number nb && Math.abs(nb.doubleValue() - val) < 1e-9) return true;
        }
        return false;
    }

    void setSpinner(Parent root, double current, double next) {
        List<Spinner<?>> sp = new ArrayList<>();
        collectSpinners(root, sp);
        for (Spinner<?> s : sp) {
            Object v = s.getValue();
            if (v instanceof Number nb && Math.abs(nb.doubleValue() - current) < 1e-9) {
                @SuppressWarnings("unchecked")
                javafx.scene.control.SpinnerValueFactory<Double> vf =
                        (javafx.scene.control.SpinnerValueFactory<Double>) s.getValueFactory();
                vf.setValue(next);
                return;
            }
        }
        failures.add("spinner with value " + current + " not found for edit");
    }

    void collectSpinners(Node n, List<Spinner<?>> out) {
        if (n instanceof Spinner<?> s) out.add(s);
        if (n instanceof Parent p) for (Node ch : p.getChildrenUnmodifiable()) collectSpinners(ch, out);
    }

    CheckBox checkboxContaining(Node n, String text) {
        if (n instanceof CheckBox cb && cb.getText() != null && cb.getText().contains(text)) return cb;
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) {
                CheckBox r = checkboxContaining(ch, text);
                if (r != null) return r;
            }
        }
        return null;
    }

    ComboBox<?> comboBoxContaining(Node n, String text) {
        if (n instanceof ComboBox<?> cb) {
            boolean hit = false;
            for (Object it : cb.getItems()) if (String.valueOf(it).contains(text)) hit = true;
            if (hit) return cb;
        }
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) {
                ComboBox<?> r = comboBoxContaining(ch, text);
                if (r != null) return r;
            }
        }
        return null;
    }

    Label labelContaining(Node n, String text) {
        if (n instanceof Label l && l.getText() != null && l.getText().contains(text)) return l;
        if (n instanceof Parent p) {
            for (Node ch : p.getChildrenUnmodifiable()) {
                Label r = labelContaining(ch, text);
                if (r != null) return r;
            }
        }
        return null;
    }

    void shotDialog(String name) {
        try {
            for (Window w : Window.getWindows()) {
                Scene sc = w.getScene();
                if (sc == null || sc.getRoot() == null) continue;
                if (checkboxContaining(sc.getRoot(), "Auto-fit paper width") != null) {
                    WritableImage img = sc.snapshot(null);
                    File f = new File(SHOT_DIR, "ls-" + (seq++) + "-" + name + ".png");
                    ImageIO.write(SwingFXUtils.fromFXImage(img, null), "png", f);
                    System.out.println("[SHOT] " + f.getAbsolutePath());
                    return;
                }
            }
        } catch (Throwable t) {
            System.err.println("[SHOT] failed: " + t);
        }
    }

    // node discovery (same as SelectionZoomVerify)
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
