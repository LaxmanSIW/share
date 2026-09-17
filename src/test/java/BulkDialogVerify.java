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
import javafx.scene.control.Spinner;
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
 *  H) Enter in the COPIES column moves to the NEXT row's FIRST column
 *     (spreadsheet wrap) — never straight down into another Copies box
 *  I) rows are COMPACT (the embedded combo editor must not inherit the
 *     app-wide 7px text-field padding) — measured, asserted < 40px
 *  J) no bright azure/cyan pixels anywhere in the dialog scene
 *     (Modena selection/focus lookups fully themed away)
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
        plan.add(this::step10EnterInCopiesWrapsToFirstColumn);
        plan.add(this::step11RowsAreCompact);
        plan.add(this::step12NoAzurePixels);
        plan.add(this::step13FillAllCopies);
        plan.add(this::step14ClosePersists);
        plan.add(this::step15ReopenRestores);
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

    void step10EnterInCopiesWrapsToFirstColumn() {
        Stage dlg = bulkDialog();
        TableView<?> tv = tableIn(dlg);
        if (tv == null) { fail("10-copies-wrap", "no table"); next(); return; }
        // Fill row 0 so the Enter-wrap appends exactly one new row.
        Platform.runLater(() -> {
            try {
                Object row = tv.getItems().get(0);
                if (row instanceof LabelBulkPrintDialog.PrintRow pr) {
                    pr.prop("size").setValue("28");
                }
                // Focus the COPIES column of row 0 and fire Enter on the CELL
                // (the dialog's scene filter only routes Enter targeted at a
                // TableCell/TableRow — mimics real keyboard focus on the cell).
                int lastCol = tv.getColumns().size() - 1;
                @SuppressWarnings({"unchecked", "rawtypes"})
                javafx.scene.control.TableView.TableViewSelectionModel sel = tv.getSelectionModel();
                sel.clearAndSelect(0, (TableColumn) tv.getColumns().get(lastCol));
                TableCell<?, ?> cell = materializedCell(tv, 0, lastCol);
                if (cell == null) throw new IllegalStateException("copies cell not materialized");
                KeyEvent enter = new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                        KeyCode.ENTER, false, false, false, false);
                javafx.event.Event.fireEvent(cell, enter);
            } catch (Throwable t) {
                failures.add("10-copies-wrap — " + t);
                t.printStackTrace();
            }
        });
        then(r -> {
            int selRow = tv.getSelectionModel().getSelectedIndex();
            var selCol = tv.getSelectionModel().getSelectedCells().isEmpty()
                    ? null : tv.getSelectionModel().getSelectedCells().get(0).getTableColumn();
            boolean wrapped = tv.getItems().size() == 2
                    && selRow == 1
                    && selCol == tv.getColumns().get(0);
            if (wrapped) {
                pass("10-copies-wrap — Enter in Copies moved to next row's FIRST column, one row added");
            } else {
                fail("10-copies-wrap", "rows=" + tv.getItems().size() + " selRow=" + selRow
                        + " selCol=" + (selCol == null ? "null"
                        : tv.getColumns().indexOf(selCol)) + " (want row 1, col 0)");
            }
            return true;
        }, "10-copies-wrap", "see failure detail");
    }

    void step11RowsAreCompact() {
        Stage dlg = bulkDialog();
        TableView<?> tv = tableIn(dlg);
        if (tv == null) { fail("11-rows-compact", "no table"); next(); return; }
        then(r -> {
            double h = -1;
            for (javafx.scene.Node n : tv.lookupAll(".table-row-cell")) {
                if (n.isVisible() && h < 0) h = n.getBoundsInParent().getHeight();
            }
            // Diagnostics: what drives the height?
            for (javafx.scene.Node n : tv.lookupAll(".table-row-cell")) {
                if (n instanceof javafx.scene.control.TableRow<?> row && row.isVisible()) {
                    System.out.println("[DIAG] row h=" + row.getHeight());
                    for (javafx.scene.Node cell : row.getChildrenUnmodifiable()) {
                        if (cell instanceof TableCell<?, ?> tc && tc.getGraphic() != null) {
                            javafx.scene.Node g = tc.getGraphic();
                            System.out.println("[DIAG]   cell col=" + tc.getTableColumn().getText()
                                    + " cellH=" + tc.getHeight() + " graphic=" + g.getClass().getSimpleName()
                                    + " gH=" + g.getBoundsInParent().getHeight()
                                    + " pad=" + tc.getPadding()
                                    + (g instanceof javafx.scene.control.ComboBox<?> cb && cb.getEditor() != null
                                        ? " editorPad=" + cb.getEditor().getPadding() : ""));
                        }
                    }
                }
            }
            boolean compact = h > 0 && h < 40;
            if (compact) {
                pass(String.format("11-rows-compact — row height %.1fpx (< 40)", h));
            } else {
                fail("11-rows-compact", "row height " + String.format("%.1f", h) + "px (want < 40)");
            }
            return true;
        }, "11-rows-compact", "see failure detail");
    }

    void step12NoAzurePixels() {
        Stage dlg = bulkDialog();
        if (dlg == null) { fail("12-no-azure", "no dialog"); next(); return; }
        then(r -> {
            int azure = azurePixels(dlg.getScene());
            if (azure == 0) {
                pass("12-no-azure — zero azure FILLED regions (runs ≥20px) in the dialog");
            } else {
                fail("12-no-azure", "azure fill detected: longest run " + azure + "px (Modena selection box)");
            }
            return true;
        }, "12-no-azure", "see failure detail");
    }

    // ─── Seamless repeat-session features ─────────────────────────────────

    /** Finds the fill-all spinner — the only Spinner in the bulk dialog. */
    @SuppressWarnings("unchecked")
    Spinner<Integer> fillAllSpinner(TableView<?> tv) {
        List<Spinner> found = new ArrayList<>();
        collect(tv, Spinner.class, found);
        return found.isEmpty() ? null : (Spinner<Integer>) found.get(0);
    }

    /**
     * 13: the Copies header spinner overrides every row; an individual edit
     * afterwards sticks; a NEW row inherits the fill value.
     */
    void step13FillAllCopies() {
        Stage dlg = bulkDialog();
        TableView<?> tv = tableIn(dlg);
        if (tv == null) { fail("13-fill-all", "no table"); next(); return; }
        Spinner<Integer> sp = fillAllSpinner(tv);
        if (sp == null) { fail("13-fill-all", "fill-all spinner not found in Copies header"); next(); return; }
        shotScene(dlg.getScene(), "bulk-fillall");
        Platform.runLater(() -> {
            try {
                // (a) Override ALL rows: set the spinner to 10.
                sp.getValueFactory().setValue(10);
                int afterFill = rowsAllCopies(tv);
                boolean overrideOk = afterFill == 10;

                // (b) Individual edit: row 0 → 3; spinner must not re-stamp.
                LabelBulkPrintDialog.PrintRow r0 = (LabelBulkPrintDialog.PrintRow) tv.getItems().get(0);
                r0.copies.set(3);

                // (c) A new row inherits the fill value (10), not 1 or 3.
                Button addBtn = null;
                for (javafx.scene.Node n : dlg.getScene().getRoot().lookupAll(".button")) {
                    if (n instanceof Button b && b.getText() != null && b.getText().startsWith("+ Add Row")) { addBtn = b; break; }
                }
                if (addBtn == null) throw new IllegalStateException("add button not found");
                addBtn.fire();
                int nRows = tv.getItems().size();
                LabelBulkPrintDialog.PrintRow last = (LabelBulkPrintDialog.PrintRow) tv.getItems().get(nRows - 1);
                boolean inheritOk = last.getCopies() == 10;

                if (overrideOk && r0.getCopies() == 3 && inheritOk) {
                    pass("13-fill-all — spinner set all rows to 10; row-0 edit to 3 stuck; new row inherited 10");
                } else {
                    fail("13-fill-all", "override=" + overrideOk + " (all=" + afterFill
                            + ") indivEdit=" + (r0.getCopies() == 3) + " inherit=" + inheritOk
                            + " (last=" + last.getCopies() + ")");
                }
            } catch (Throwable t) {
                failures.add("13-fill-all — " + t);
                t.printStackTrace();
            }
            next();
        });
    }

    private static int rowsAllCopies(TableView<?> tv) {
        int v = -1;
        for (Object o : tv.getItems()) {
            LabelBulkPrintDialog.PrintRow r = (LabelBulkPrintDialog.PrintRow) o;
            if (v < 0) v = r.getCopies();
            else if (r.getCopies() != v) return -2; // not uniform
        }
        return v;
    }

    /** Rows carrying values when the dialog closed — restore must match +1. */
    int rememberedRows = -1;
    String rememberedValue0 = null;
    int rememberedCopies0 = -1;

    /**
     * 14: closing the dialog persists the session (rows + printer JSON) and
     * the stage actually leaves the window list. Reopen happens in step 15 —
     * the check must complete BEFORE the new dialog can exist.
     */
    void step14ClosePersists() {
        Stage dlg = bulkDialog();
        TableView<?> tv = tableIn(dlg);
        if (tv == null) { fail("14-close-persists", "no table"); next(); return; }
        rememberedRows = 0;
        for (Object o : tv.getItems()) {
            if (((LabelBulkPrintDialog.PrintRow) o).hasAnyValue()) rememberedRows++;
        }
        rememberedValue0 = ((LabelBulkPrintDialog.PrintRow) tv.getItems().get(0)).get("size");
        rememberedCopies0 = ((LabelBulkPrintDialog.PrintRow) tv.getItems().get(0)).getCopies();
        Platform.runLater(() -> {
            try { dlg.close(); } catch (Throwable t) { failures.add("14-close-persists — close: " + t); }
        });
        then(r -> {
            boolean gone = bulkDialog() == null;
            boolean saved = java.nio.file.Files.isRegularFile(
                    java.nio.file.Path.of(System.getProperty("invoicestudio.data.dir", "."), "bulk-print-state.json"));
            if (gone && saved) {
                pass("14-close-persists — dialog closed and session JSON written");
            } else {
                fail("14-close-persists", "gone=" + gone + " stateFile=" + saved);
            }
            return true;
        }, "14-close-persists", "see failure detail");
    }

    /**
     * 15: reopening the bulk dialog restores rows, values and copies —
     * with NO trailing empty row (the grid ends on the last real line;
     * Enter / + Add Row creates a new row on demand).
     */
    void step15ReopenRestores() {
        Button btn = contentButton("Bulk Print");
        asyncFire(btn, "bulk-reopen");
        then(r -> {
            Stage dlg2 = bulkDialog();
            if (dlg2 == null) return false;
            TableView<?> tv2 = tableIn(dlg2);
            if (tv2 == null || tv2.getItems().isEmpty()) return false;
            LabelBulkPrintDialog.PrintRow r0b = (LabelBulkPrintDialog.PrintRow) tv2.getItems().get(0);
            boolean restored = rememberedValue0 != null
                    && rememberedValue0.equals(r0b.get("size"))
                    && r0b.getCopies() == rememberedCopies0;
            // Exactly the remembered rows — a phantom blank tail must NOT exist.
            boolean exactRows = tv2.getItems().size() == rememberedRows;
            LabelBulkPrintDialog.PrintRow last =
                    (LabelBulkPrintDialog.PrintRow) tv2.getItems().get(tv2.getItems().size() - 1);
            boolean lastIsReal = last.hasAnyValue();
            shotScene(dlg2.getScene(), "bulk-reopened");
            if (restored && exactRows && lastIsReal) {
                pass("15-reopen-restore — '" + rememberedValue0 + "'/" + rememberedCopies0
                        + " came back after reopen, " + rememberedRows
                        + " real rows, NO trailing blank row");
            } else {
                fail("15-reopen-restore", "restored=" + restored + " exactRows=" + exactRows
                        + " (" + tv2.getItems().size() + " vs " + rememberedRows + ")"
                        + " lastIsReal=" + lastIsReal);
            }
            return true;
        }, "15-reopen-restore", "reopen did not restore remembered state");
    }

    /** First visible TableCell at (rowIndex, colIndex), or null if virtualized away. */
    @SuppressWarnings("rawtypes")
    TableCell<?, ?> materializedCell(TableView<?> tv, int rowIndex, int colIndex) {
        List<TableCell> cells = new ArrayList<>();
        collect(tv, TableCell.class, cells);
        for (TableCell cell : cells) {
            if (cell.isVisible() && cell.getIndex() == rowIndex
                    && tv.getColumns().indexOf(cell.getTableColumn()) == colIndex) {
                return cell;
            }
        }
        return null;
    }

    /**
     * Detects bright-azure FILLED REGIONS (Modena selection/focus boxes):
     * contiguous horizontal runs of azure pixels >= 20px wide. LCD sub-pixel
     * text anti-aliasing also produces scattered 1-3px azure fringe pixels on
     * every glyph — those are rendering artifacts, not UI chrome, and are
     * deliberately ignored by the run-length threshold.
     */
    int azurePixels(javafx.scene.Scene scene) {
        try {
            double w = Math.max(1, scene.getWidth()), h = Math.max(1, scene.getHeight());
            WritableImage img = scene.snapshot(new WritableImage((int) w, (int) h));
            int runMax = 0;
            int runStartX = -1, runY = -1;
            for (int y = 0; y < img.getHeight(); y++) {
                int run = 0;
                for (int x = 0; x < img.getWidth(); x++) {
                    javafx.scene.paint.Color c = img.getPixelReader().getColor(x, y);
                    int rr = (int) (c.getRed() * 255), gg = (int) (c.getGreen() * 255), bb = (int) (c.getBlue() * 255);
                    boolean azure = bb > 190 && gg > 120 && gg < 235 && rr < 100;
                    if (azure) {
                        run++;
                        if (run > runMax) { runMax = run; runStartX = x - run + 1; runY = y; }
                    } else {
                        run = 0;
                    }
                }
            }
            if (runMax >= 20) {
                System.out.println("[DIAG] azure FILL: run=" + runMax + "px starting at (" + runStartX + "," + runY + ")");
                return runMax;
            }
            return 0;
        } catch (Throwable t) {
            failures.add("12-no-azure — scan threw: " + t);
            return -1;
        }
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
        // Close every remaining window BEFORE Platform.exit(): the bulk dialog
        // was opened with showAndWait (a parked nested event loop), and exiting
        // while that loop is still parked force-pops it — throwing "Key not
        // associated with a running event loop" and hard-crashing glass.dll in
        // native teardown. A normal close() unparks the loop cleanly, exactly
        // like the user clicking the dialog's X.
        for (Window w : new ArrayList<>(Window.getWindows())) {
            if (w instanceof Stage st && st.isShowing()) {
                try { st.close(); } catch (Throwable ignored) { }
            }
        }
        Platform.exit();
        // Let the FX toolkit finish its native teardown before forcing the exit
        // code: an immediate halt() races glass.dll's window teardown and hard-
        // crashes the JVM (EXCEPTION_ACCESS_VIOLATION) after the verdict is
        // already logged. A short delayed halt on a daemon thread preserves the
        // verdict exit code without killing teardown mid-flight.
        Thread halt = new Thread(() -> {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
            Runtime.getRuntime().halt(ok ? 0 : 1);
        }, "harness-halt");
        halt.setDaemon(true);
        halt.start();
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
