import com.invoicestudio.model.*;
import com.invoicestudio.db.DatabaseManager;
import com.invoicestudio.db.TemplateDao;
import com.invoicestudio.db.VariableDao;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.LabelBulkPrintDialog;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runtime verification for the Bulk-Print dialog behaviour round:
 *  A) dialog opens with EXACTLY one empty row (no phantom rows)
 *  B) Enter on the empty last row does NOT breed a new row
 *  C) typing a value + Enter on the last row adds EXACTLY ONE row, commits the
 *     value and moves the selection down
 *  D) commit by FOCUS LOSS (the mouse-click-away path) adds NO row
 *  E) commit by POPUP PICK (action event) adds NO row
 *  F) the preview card renders VALUES ("Size 28"), never raw {{placeholders}}
 *  G) Strip Preview opens (WYSIWYG companion view)
 * Exit code 0 = all verified, no uncaught exceptions.
 */
public class BulkDialogVerify extends StudioApp {

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
        plan.add(this::step01OpenDesigner);
        plan.add(this::step02OpenBulkPrint);
        plan.add(this::step03InitialState);
        plan.add(this::step04EnterOnEmptyLastRow);
        plan.add(this::step05TypeValueEnterAddsOneRow);
        plan.add(this::step06FocusLossCommitNoRow);
        plan.add(this::step07PopupPickCommitNoRow);
        plan.add(this::step08PreviewShowsValues);
        plan.add(this::step09StripPreview);
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

            VariableDef batch = new VariableDef("batch", "Batch No", "text", false);
            batch.setScope("barcode");
            batch.setChoices("B1,B2");
            vdao.saveVariable(batch);

            label = new Template("tpl_bdv_label", "Verify Label",
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
            name.setId("bdv_name"); name.setType(ElementType.TEXT);
            name.setX(2); name.setY(2); name.setW(46); name.setH(6);
            name.setText("KAPTON"); name.setFontSize(9); name.setFontWeight(700);
            name.setColor("#111111"); name.setZIndex(0);
            label.getElements().add(name);

            TemplateElement sizeTxt = new TemplateElement();
            sizeTxt.setId("bdv_size"); sizeTxt.setType(ElementType.TEXT);
            sizeTxt.setX(2); sizeTxt.setY(9); sizeTxt.setW(46); sizeTxt.setH(5);
            sizeTxt.setText("Size {{size}}"); sizeTxt.setFontSize(7.5);
            sizeTxt.setColor("#333333"); sizeTxt.setZIndex(1);
            label.getElements().add(sizeTxt);

            TemplateElement code = new TemplateElement();
            code.setId("bdv_code"); code.setType(ElementType.BARCODE);
            code.setX(5); code.setY(15); code.setW(40); code.setH(8);
            code.setBarcodeData("{{size}}"); code.setZIndex(2);
            label.getElements().add(code);

            new TemplateDao(db).saveTemplate(label);
            System.out.println("[SEED] label template + barcode variables written");
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

    void step03InitialState() {
        Stage dlg = bulkDialog();
        TableView<?> tv = tableIn(dlg);
        if (tv == null) { fail("03-initial-state", "no TableView"); next(); return; }
        shotScene(dlg.getScene(), "bulk-initial");
        if (tv.getItems().size() == 1 && tv.getColumns().size() == 2) {
            pass("03-initial-state — 1 empty row, 2 columns (Size + Copies)");
        } else {
            fail("03-initial-state", "rows=" + tv.getItems().size() + " cols=" + tv.getColumns().size());
        }
        next();
    }

    void step04EnterOnEmptyLastRow() {
        Stage dlg = bulkDialog();
        TableView<?> tv = tableIn(dlg);
        ComboBox<?> combo = comboForRow(tv, 0);
        if (combo == null) { fail("04-enter-empty-row", "row-0 combo not found"); next(); return; }
        Platform.runLater(() -> {
            try {
                KeyEvent enter = new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                        KeyCode.ENTER, false, false, false, false);
                EventFireHelper.fire(combo.getEditor(), enter);
            } catch (Throwable t) {
                failures.add("04-enter-empty-row — " + t);
                t.printStackTrace();
            }
        });
        then(r -> tv.getItems().size() == 1, "04-enter-empty-row",
                "Enter on empty last row must NOT add a row, rows=" + tv.getItems().size());
    }

    void step05TypeValueEnterAddsOneRow() {
        Stage dlg = bulkDialog();
        TableView<?> tv = tableIn(dlg);
        ComboBox<?> combo = comboForRow(tv, 0);
        if (combo == null) { fail("05-enter-adds-one", "row-0 combo not found"); next(); return; }
        Platform.runLater(() -> {
            try {
                ((TextField) combo.getEditor()).setText("28");
                KeyEvent enter = new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                        KeyCode.ENTER, false, false, false, false);
                EventFireHelper.fire(combo.getEditor(), enter);
            } catch (Throwable t) {
                failures.add("05-enter-adds-one — " + t);
                t.printStackTrace();
            }
        });
        then(r -> {
            boolean oneAdded = tv.getItems().size() == 2;
            boolean value = rowValue(tv, 0).equals("28");
            boolean moved = tv.getSelectionModel().getSelectedIndex() == 1;
            if (oneAdded && value && moved) {
                pass("05-enter-adds-one — value committed, exactly ONE row added, selection moved down");
                return true;
            }
            fail("05-enter-adds-one", "rows=" + tv.getItems().size() + " row0="
                    + rowValue(tv, 0) + " sel=" + tv.getSelectionModel().getSelectedIndex());
            return true; // failure already recorded above
        }, "05-enter-adds-one", "see failure detail");
    }

    void step06FocusLossCommitNoRow() {
        Stage dlg = bulkDialog();
        TableView<?> tv = tableIn(dlg);
        ComboBox<?> combo = comboForRow(tv, 1);
        if (combo == null) { fail("06-focus-loss", "row-1 combo not found"); next(); return; }
        Platform.runLater(() -> {
            try {
                ((TextField) combo.getEditor()).setText("M");
                // Simulate the mouse-click-away: move focus off the editor.
                tv.requestFocus();
            } catch (Throwable t) {
                failures.add("06-focus-loss — " + t);
                t.printStackTrace();
            }
        });
        then(r -> {
            boolean sameRows = tv.getItems().size() == 2;
            boolean value = rowValue(tv, 1).equals("M");
            if (sameRows && value) {
                pass("06-focus-loss — commit on focus loss writes the value and adds NO row");
            } else {
                fail("06-focus-loss", "rows=" + tv.getItems().size() + " row1=" + rowValue(tv, 1));
            }
            return true;
        }, "06-focus-loss", "see failure detail");
    }

    void step07PopupPickCommitNoRow() {
        Stage dlg = bulkDialog();
        TableView<?> tv = tableIn(dlg);
        ComboBox<?> combo = comboForRow(tv, 1);
        if (combo == null) { fail("07-popup-pick", "row-1 combo not found"); next(); return; }
        Platform.runLater(() -> {
            try {
                // What a popup selection does: editor text updated + action fired.
                ((TextField) combo.getEditor()).setText("XL");
                combo.fireEvent(new ActionEvent());
            } catch (Throwable t) {
                failures.add("07-popup-pick — " + t);
                t.printStackTrace();
            }
        });
        then(r -> {
            boolean sameRows = tv.getItems().size() == 2;
            boolean value = rowValue(tv, 1).equals("XL");
            if (sameRows && value) {
                pass("07-popup-pick — pick commit writes the value and adds NO row");
            } else {
                fail("07-popup-pick", "rows=" + tv.getItems().size() + " row1=" + rowValue(tv, 1));
            }
            return true;
        }, "07-popup-pick", "see failure detail");
    }

    void step08PreviewShowsValues() {
        Stage dlg = bulkDialog();
        if (dlg == null) { fail("08-preview-values", "no dialog"); next(); return; }
        shotScene(dlg.getScene(), "bulk-with-values");
        StackPane card = previewCard(dlg.getScene());
        if (card == null) { fail("08-preview-values", "preview card not found"); next(); return; }
        List<Label> labels = new ArrayList<>();
        collect((Parent) card, Label.class, labels);
        boolean hasPlaceholder = labels.stream().anyMatch(l -> l.getText() != null && l.getText().contains("{{"));
        boolean hasValue = labels.stream().anyMatch(l -> l.getText() != null && l.getText().contains("28"));
        boolean hasStatic = labels.stream().anyMatch(l -> l.getText() != null && l.getText().contains("KAPTON"));
        if (!hasPlaceholder && hasValue && hasStatic) {
            pass("08-preview-values — preview renders fetched values (Size 28 / KAPTON), no {{placeholders}}");
        } else {
            List<String> texts = new ArrayList<>();
            labels.forEach(l -> texts.add(l.getText()));
            fail("08-preview-values", "placeholder=" + hasPlaceholder + " value=" + hasValue
                    + " static=" + hasStatic + " texts=" + texts);
        }
        next();
    }

    void step09StripPreview() {
        Button btn = contentButton("Strip Preview");
        asyncFire(btn, "strip-preview-open");
        then(r -> {
            for (Window w : Window.getWindows()) {
                if (w instanceof Stage st && st.getTitle() != null
                        && st.getTitle().startsWith("Strip Preview")) {
                    return true;
                }
            }
            return false;
        }, "09-strip-preview-open", "Strip Preview stage not found");
    }

    // ------------------------------------------------------------------
    // Engine (same pattern as BarcodeFixVerify)
    // ------------------------------------------------------------------

    void runNext() {
        if (planIdx >= plan.size()) { finish(); return; }
        Runnable step = plan.get(planIdx);
        planIdx++;
        try {
            step.run();
        } catch (Throwable ex) {
            failures.add("step" + planIdx + " threw: " + ex);
            ex.printStackTrace();
            next();
        }
    }

    void next() {
        PauseTransition p = new PauseTransition(Duration.millis(700));
        p.setOnFinished(e -> runNext());
        p.play();
    }

    /** Waits for a render pause, evaluates verify on the FX thread, logs pass/fail, advances. */
    void then(java.util.function.Predicate<BulkDialogVerify> verify, String name, String failWhy) {
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
        // close any open strip preview before exiting
        for (Window w : new ArrayList<>(Window.getWindows())) {
            if (w instanceof Stage st && st.getTitle() != null && st.getTitle().startsWith("Strip Preview")) {
                st.close();
            }
        }
        Stage dlg = bulkDialog();
        if (dlg != null) shotScene(dlg.getScene(), "bulk-final");
        System.out.println();
        System.out.println("=== BULK DIALOG VERIFY DONE ===");
        System.out.println("PASSED: " + passed.size());
        passed.forEach(s -> System.out.println("  [PASS] " + s));
        System.out.println("FAILED: " + failures.size());
        failures.forEach(s -> System.out.println("  [FAIL] " + s));
        System.out.println("UNCAUGHT: " + uncaught.size());
        uncaught.forEach(t -> System.out.println("  " + t));
        boolean ok = failures.isEmpty() && uncaught.isEmpty();
        System.out.println(ok ? "BULK VERIFY: SUCCESS" : "BULK VERIFY: FAILED");
        Platform.exit();
        Runtime.getRuntime().halt(ok ? 0 : 1);
    }

    void pass(String n) { passed.add(n); System.out.println("[PASS] " + n); }
    void fail(String n, String why) { failures.add(n + " — " + why); System.out.println("[FAIL] " + n + " — " + why); }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Fire an event through the node's real dispatch chain (scene filter included). */
    static final class EventFireHelper {
        static void fire(Node target, KeyEvent event) {
            javafx.event.Event.fireEvent(target, event);
        }
    }

    TableView<?> tableIn(Stage dlg) {
        if (dlg == null) return null;
        return findFirst(dlg.getScene().getRoot(), TableView.class);
    }

    /** The VarCell combo editing the given row index (searches materialised cells). */
    @SuppressWarnings({"rawtypes", "unchecked"})
    ComboBox<?> comboForRow(TableView<?> tv, int rowIndex) {
        List<TableCell> cells = new ArrayList<>();
        collect(tv, TableCell.class, cells);
        for (TableCell cell : cells) {
            if (cell.getIndex() == rowIndex && cell.getGraphic() instanceof ComboBox<?> cb
                    && cell.getTableColumn() != null
                    && cell.getTableColumn().getUserData() instanceof String) {
                return cb;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    String rowValue(TableView<?> tv, int rowIndex) {
        Object row = tv.getItems().get(rowIndex);
        if (row instanceof LabelBulkPrintDialog.PrintRow pr) {
            return pr.get("size");
        }
        return "<" + (row == null ? "null" : row.getClass().getSimpleName()) + ">";
    }
    /** The dark preview card (StackPane with the #0F172A style). */
    StackPane previewCard(Scene scene) {
        List<StackPane> panes = new ArrayList<>();
        collect(scene.getRoot(), StackPane.class, panes);
        for (StackPane p : panes) {
            if (p.getStyle() != null && p.getStyle().contains("#0F172A") && !p.getChildren().isEmpty()) {
                return p;
            }
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

    Stage bulkDialog() {
        for (Window w : Window.getWindows()) {
            if (w instanceof Stage st && st.getTitle() != null
                    && st.getTitle().startsWith("Bulk Label Print")) return st;
        }
        return null;
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
            File f = new File(SHOT_DIR, String.format("bv-%02d-%s.png", ++seq,
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
