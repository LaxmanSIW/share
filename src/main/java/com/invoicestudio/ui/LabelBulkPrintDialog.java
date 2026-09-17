package com.invoicestudio.ui;

import com.invoicestudio.service.AppLog;
import com.invoicestudio.model.LabelConfig;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;
import com.invoicestudio.model.VariableDef;
import com.invoicestudio.service.BulkPrintStateStore;
import com.invoicestudio.service.LabelGeometryService;
import com.invoicestudio.service.LabelPrintService;
import com.invoicestudio.service.LabelRenderUtil;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.print.Printer;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bulk Label Print popup (Barcode Mode).
 * <p>
 * One row per print line: one editable cell per barcode variable
 * (quick-picks from the variable's possible values, free typing allowed)
 * plus a copies cell.
 * <p>
 * Editing model (deliberately decoupled from TableView's edit machinery):
 * every cell writes its value DIRECTLY into the row model when it commits
 * (popup pick, focus loss, Enter) — committing NEVER navigates and NEVER
 * creates rows. Only an explicit ENTER moves down, and only an Enter on the
 * last row that already carries values spawns a fresh row, so the table can
 * never silently fill itself with empty lines (the old version auto-added a
 * row on every commit — a single mouse click or focus loss at the bottom
 * appended a blank print line).
 * <p>
 * Keyboard map:
 * <ul>
 *   <li>Enter — commit cell, move DOWN (adds a new row only when the last
 *       row already has values)</li>
 *   <li>Tab / Shift+Tab — next / previous cell</li>
 *   <li>↑ ↓ ← → — move between rows / cells</li>
 *   <li>Insert or Alt+N — add row · Ctrl+Delete — remove selected row</li>
 *   <li>Ctrl+Enter — Print All · F4 — close</li>
 * </ul>
 * <p>
 * The preview card renders the label with REAL values — business/buyer
 * variables are fetched, barcode variables show the first possible value —
 * and it re-renders live while the first row is typed, in the exact
 * physical print orientation, so "what you see is what prints".
 */
public class LabelBulkPrintDialog extends Stage {

    /** Observable row model backing the table. */
    public static class PrintRow {
        public final Map<String, StringProperty> values = new LinkedHashMap<>();
        public final IntegerProperty copies = new SimpleIntegerProperty(1);

        public StringProperty prop(String key) {
            return values.computeIfAbsent(key, k -> new SimpleStringProperty(""));
        }
        public String get(String key) {
            StringProperty p = values.get(key);
            return p != null && p.get() != null ? p.get() : "";
        }
        public boolean hasAnyValue() {
            for (StringProperty p : values.values()) {
                if (p.get() != null && !p.get().isBlank()) return true;
            }
            return false;
        }
        public int getCopies() { return Math.max(1, copies.get()); }
    }

    private final Template template;
    private final Settings settings;
    private final List<VariableDef> barcodeVars;
    private final LabelConfig cfg;
    private final TableView<PrintRow> table = new TableView<>();
    private final ObservableList<PrintRow> rows = FXCollections.observableArrayList();
    private final Label totalLbl = new Label("0 labels");
    private final ComboBox<Printer> printerBox = new ComboBox<>();
    /** Copies header control — overrides every row's copies in one action. */
    private Spinner<Integer> fillAllSpinner;
    /** Guards applyFillAll while the spinner value is set programmatically. */
    private boolean fillSyncing = false;

    // ── live preview state ──
    private final StackPane previewHolder = new StackPane();
    private PrintRow previewRow;                 // row whose values drive the preview
    private final List<PropListener> previewListeners = new ArrayList<>();
    private boolean previewScheduled = false;

    // ── spool state (the RAW spooler runs on a background thread) ──
    private Button testBtn;
    private Button printBtn;
    private Button calibrateBtn;
    private boolean spooling = false;

    /** Pairs a property with its listener so a detached first row releases them. */
    private record PropListener(StringProperty prop, ChangeListener<String> listener) {}

    public LabelBulkPrintDialog(javafx.stage.Window owner, Template template, Settings settings,
                                List<VariableDef> barcodeVars, LabelConfig cfg) {
        this.template = template;
        this.settings = settings;
        this.barcodeVars = barcodeVars != null ? barcodeVars : List.of();
        this.cfg = cfg;

        initOwner(owner);
        DialogHelper.applyAppIcon(this); // logo in title bar from the very first frame
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Bulk Label Print — " + template.getName());
        setMinWidth(680);
        setMinHeight(460);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));
        root.getStyleClass().add("bg-app");

        // ── Top: info + live preview of the label design ──
        HBox top = new HBox(14);
        top.setAlignment(Pos.CENTER_LEFT);

        previewHolder.setAlignment(Pos.CENTER);
        previewHolder.setPrefSize(160, 160);
        previewHolder.setMinSize(160, 160);
        previewHolder.setMaxSize(160, 160);
        previewHolder.setStyle("-fx-background-color: #0F172A; -fx-background-radius: 6;");
        previewHolder.setPadding(new Insets(6));
        // Hard clip — the preview can never paint outside its card.
        javafx.scene.shape.Rectangle previewClip = new javafx.scene.shape.Rectangle(160, 160);
        previewClip.setArcWidth(10);
        previewClip.setArcHeight(10);
        previewHolder.setClip(previewClip);
        Tooltip.install(previewHolder, new Tooltip(
                "Live preview — exactly what the printer outputs. Barcode variables show the "
                        + "first possible value until you type values in row 1."));

        VBox info = new VBox(6);
        Label title = new Label("Print many labels with different values");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: -color-fg;");
        Label sub = new Label("One row = one print line. Type values into the variable columns, set copies, then Print All.\n"
                + "Enter opens the dropdown — Enter picks the value, ↑↓ browse, Esc closes. Enter in Copies starts the next row’s first cell.\n"
                + "The Copies header spinner sets ALL rows at once — and your last session is remembered for next time.");
        sub.setWrapText(true);
        sub.getStyleClass().add("text-muted");
        info.getChildren().addAll(title, sub);

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        top.getChildren().addAll(info, topSpacer, previewHolder);
        VBox.setVgrow(info, Priority.ALWAYS);
        root.setTop(top);
        refreshPreview(); // placeholder render (first choices) before rows exist

        // ── Center: the print-line table ──
        buildTable();
        table.setItems(rows);
        addRow();
        root.setCenter(table);

        // ── Bottom: two rows so nothing is ever clipped at minimum width ──
        VBox bottom = new VBox(8);
        bottom.setPadding(new Insets(10, 0, 0, 0));

        HBox rowActions = new HBox(10);
        rowActions.setAlignment(Pos.CENTER_LEFT);

        Button addBtn = new Button("+ Add Row");
        addBtn.getStyleClass().addAll("button-sm", "button-secondary");
        addBtn.setTooltip(new Tooltip("Add a print line (Insert / Alt+N)"));
        addBtn.setOnAction(e -> { addRow(); focusLastRow(); });

        Button delBtn = new Button("Delete Row");
        delBtn.getStyleClass().addAll("button-sm", "button-secondary");
        delBtn.setTooltip(new Tooltip("Remove the selected row (Ctrl+Delete)"));
        delBtn.setOnAction(e -> deleteSelectedRow());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        totalLbl.getStyleClass().add("text-muted");

        rowActions.getChildren().addAll(addBtn, delBtn, spacer, totalLbl);

        HBox rowPrint = new HBox(10);
        rowPrint.setAlignment(Pos.CENTER_LEFT);

        printerBox.getItems().addAll(Printer.getAllPrinters());
        Printer def = Printer.getDefaultPrinter();
        printerBox.setValue(def);
        printerBox.setPrefWidth(240);
        printerBox.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(printerBox, Priority.ALWAYS);
        printerBox.setTooltip(new Tooltip("Target printer (e.g. TSC TA210)"));

        Button testBtn = new Button("Test Print (1)");
        testBtn.getStyleClass().addAll("button-sm", "button-secondary");
        testBtn.setTooltip(new Tooltip("Print one label with the first row's values to verify alignment"));
        testBtn.setOnAction(e -> printTest());
        this.testBtn = testBtn;

        Button printBtn = new Button("Print All  (Ctrl+Enter)");
        printBtn.getStyleClass().addAll("gold-btn");
        printBtn.setOnAction(e -> printAll());
        this.printBtn = printBtn;

        Button calibrateBtn = new Button("Calibrate Sensor");
        calibrateBtn.getStyleClass().addAll("button-sm", "button-secondary");
        calibrateBtn.setTooltip(new Tooltip(
                "TSC / TSPL printers only — sends AUTODETECT: the printer feeds a few labels while it measures "
                        + "the die-cut pitch, then stores the result. Use it when the printer feeds extra blank "
                        + "labels around your printed ones."));
        calibrateBtn.setOnAction(e -> calibrateSensor());
        this.calibrateBtn = calibrateBtn;

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().addAll("button-sm", "button-secondary");
        closeBtn.setOnAction(e -> close());

        rowPrint.getChildren().addAll(printerBox, calibrateBtn, testBtn, printBtn, closeBtn);
        bottom.getChildren().addAll(rowActions, rowPrint);
        root.setBottom(bottom);

        // Last session comes back (rows/printer/spinner) — called here so the
        // printer list is populated and the spinner exists before restoring.
        restoreLastState();

        Scene scene = new Scene(root, 760, 540);
        com.invoicestudio.ui.DialogHelper.styleScene(scene);
        setScene(scene);
        installKeyboardShortcuts(scene);
        // Remember this session (rows + printer) for the next bulk print of
        // this template — whether the user prints, tests or just closes.
        setOnHidden(e -> persistState());
        Platform.runLater(() -> {
            if (!rows.isEmpty() && table.getColumns().size() > 0) {
                table.getSelectionModel().select(0, table.getColumns().get(0));
                table.requestFocus();
            }
        });
    }

    // ─── Table construction ───────────────────────────────────────────────

    private void buildTable() {
        table.setEditable(true);
        table.getStyleClass().add("bulk-grid");
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.setPlaceholder(new Label("No print lines — press Insert to add one."));
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);

        for (VariableDef var : barcodeVars) {
            final String key = var.getKey();
            final String header = (var.getLabel() != null && !var.getLabel().isBlank())
                    ? var.getLabel() : key;
            final List<String> choices = var.choicesList();

            TableColumn<PrintRow, String> col = new TableColumn<>(header + "  {{" + key + "}}");
            col.setMinWidth(120);
            col.setCellValueFactory(param -> param.getValue().prop(key));
            col.setUserData(key); // VarCell reads the variable key back from here
            col.setCellFactory(tc -> new VarCell(choices));
            table.getColumns().add(col);
        }

        TableColumn<PrintRow, Number> copiesCol = new TableColumn<>("Copies");
        copiesCol.setMinWidth(150);
        copiesCol.setCellValueFactory(param -> param.getValue().copies);
        copiesCol.setCellFactory(tc -> new CopiesCell());
        copiesCol.setGraphic(buildFillAllCopies());
        table.getColumns().add(copiesCol);

        // Structural changes: refresh totals AND the preview's source row.
        rows.addListener((javafx.collections.ListChangeListener<PrintRow>) c -> {
            updateTotals();
            attachPreviewRow();
        });
    }

    // ─── Fill-all Copies (header spinner) ─────────────────────────────────

    /**
     * The Copies column header control: an editable spinner with
     * increment/decrement buttons. Typing a number (Enter or focus loss) or
     * clicking ± overrides EVERY row's copies; rows stay individually
     * editable afterwards, and new rows inherit the current fill value.
     */
    private Node buildFillAllCopies() {
        Spinner<Integer> sp = new Spinner<>(1, 9999, 1);
        this.fillAllSpinner = sp;
        sp.setEditable(true);
        sp.getStyleClass().add("bulk-fill-spinner");
        sp.setPrefWidth(96);
        sp.setMaxWidth(96);
        sp.setTooltip(new Tooltip(
                "Set ALL rows’ copies at once — type a number and press Enter, or use ±. "
                        + "Individual cells stay editable afterwards; new rows inherit this value."));
        // Typed values must commit — on Enter AND on focus loss (same rule
        // as the grid cells); commitValue() parses the editor text into the
        // value factory and ignores non-numeric junk.
        sp.focusedProperty().addListener((obs, o, n) -> { if (!n) sp.commitValue(); });
        sp.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                sp.commitValue();
                e.consume();
            }
        });
        // Buttons, arrows and committed typing all funnel through here.
        sp.valueProperty().addListener((obs, o, n) -> {
            if (n != null && !fillSyncing) applyFillAll(n);
        });
        return sp;
    }

    /** Overrides every existing row's copies with v. */
    private void applyFillAll(int v) {
        int val = Math.max(1, v);
        boolean changed = false;
        for (PrintRow r : rows) {
            if (r.copies.get() != val) { r.copies.set(val); changed = true; }
        }
        if (changed) updateTotals();
    }

    /** Editable combo cell: quick-pick from possible values + free typing. */
    private class VarCell extends TableCell<PrintRow, String> {
        private final ComboBox<String> combo = new ComboBox<>();
        private final List<String> choices;
        /** Guards programmatic text/value updates from re-triggering commit or filter. */
        private boolean setting = false;

        VarCell(List<String> choices) {
            this.choices = choices;
            combo.setEditable(true);
            combo.getStyleClass().add("fs-11");
            combo.setItems(FXCollections.observableArrayList(choices));
            combo.setPrefWidth(150);
            combo.setMaxWidth(Double.MAX_VALUE);
            if (!choices.isEmpty()) {
                combo.setPromptText("e.g. " + String.join(" / ",
                        choices.subList(0, Math.min(3, choices.size()))));
            }
            // The popup ALWAYS offers the FULL possible-values list.
            combo.showingProperty().addListener((obs, was, now) -> {
                if (now && !setting) {
                    combo.setItems(FXCollections.observableArrayList(choices));
                }
            });
            // Commit = write straight into the row model. Popup picks and
            // focus loss commit WITHOUT navigating and WITHOUT adding rows.
            combo.setOnAction(e -> {
                if (!setting) writeValue();
            });
            combo.focusedProperty().addListener((obs, o, n) -> {
                if (!n && !setting) writeValue();
            });
            // Type-to-filter narrows the quick-picks ONLY on real keystrokes —
            // never on programmatic updates from updateItem().
            combo.getEditor().addEventFilter(KeyEvent.KEY_RELEASED, e -> {
                if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.ESCAPE
                        || e.getCode() == KeyCode.TAB || e.getCode() == KeyCode.UP
                        || e.getCode() == KeyCode.DOWN) {
                    return;
                }
                String v = combo.getEditor().getText();
                List<String> filtered = (v == null || v.isEmpty()) ? choices
                        : choices.stream()
                                .filter(c -> c.toLowerCase().startsWith(v.toLowerCase()))
                                .toList();
                combo.setItems(FXCollections.observableArrayList(filtered));
                if (!combo.isShowing() && !filtered.isEmpty()) combo.show();
            });
            // Keyboard model: Enter opens the dropdown (first value
            // pre-selected); Enter again picks it; ↑/↓ walk the choices.
            // With the popup closed and a typed/custom value present, Enter
            // commits and moves down (legacy spreadsheet flow). Consumed at
            // capture so the editor never also fires the combo action —
            // exactly ONE commit and ONE navigation per Enter press.
            combo.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.DOWN && !combo.isShowing()
                        && !choices.isEmpty()) {
                    combo.show();
                    e.consume();
                    return;
                }
                if (e.getCode() == KeyCode.ENTER) {
                    if (combo.isShowing()) {
                        // Pick the highlighted choice (defaults to the first).
                        int idx = combo.getSelectionModel().getSelectedIndex();
                        if (idx < 0) idx = 0;
                        List<String> options = combo.getItems();
                        if (options != null && !options.isEmpty()) {
                            setting = true;
                            combo.getEditor().setText(options.get(Math.min(idx, options.size() - 1)));
                            setting = false;
                        }
                        combo.hide();
                    }
                    writeValue();
                    moveDownFrom(VarCell.this);
                    e.consume();
                }
            });
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        private void writeValue() {
            if (!(getTableRow() != null && getTableRow().getItem() instanceof PrintRow row)) return;
            Object ud = getTableColumn() != null ? getTableColumn().getUserData() : null;
            if (!(ud instanceof String key)) return;
            String text = combo.getEditor().getText();
            String clean = text != null ? text.trim() : "";
            String current = row.get(key);
            if (!Objects.equals(current, clean)) {
                row.prop(key).set(clean);
                updateTotals();
            }
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setGraphic(null);
            } else {
                setting = true;
                combo.getEditor().setText(item != null ? item : "");
                combo.setValue(item);
                setting = false;
                setGraphic(combo);
            }
        }

        @Override
        public void startEdit() {
            super.startEdit();
            Platform.runLater(() -> {
                combo.getEditor().requestFocus();
                combo.getEditor().selectAll();
            });
        }
    }

    /** Copies cell: numeric TextField committed on Enter / focus loss. */
    private class CopiesCell extends TableCell<PrintRow, Number> {
        private final TextField field = new TextField();

        CopiesCell() {
            field.setPrefWidth(70);
            field.getStyleClass().add("fs-11");
            field.focusedProperty().addListener((obs, o, n) -> {
                if (!n) writeCopies();
            });
            field.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    writeCopies();
                    moveDownFrom(CopiesCell.this);
                    e.consume();
                }
            });
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        private void writeCopies() {
            if (!(getTableRow() != null && getTableRow().getItem() instanceof PrintRow row)) return;
            int v;
            try {
                v = Integer.parseInt(field.getText().trim());
            } catch (NumberFormatException ignored) {
                v = 1;
            }
            v = Math.max(1, v);
            if (row.copies.get() != v) {
                row.copies.set(v);
                updateTotals();
            }
        }

        @Override
        protected void updateItem(Number item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                field.setText(String.valueOf(item.intValue()));
                setGraphic(field);
            }
        }

        @Override
        public void startEdit() {
            super.startEdit();
            Platform.runLater(() -> {
                field.requestFocus();
                field.selectAll();
            });
        }
    }

    // ─── Last-session memory (per template) ───────────────────────────────

    /**
     * Restores the previous session's rows (values + copies) and printer so a
     * repeat print run starts exactly where the last one ended. No trailing
     * empty row is auto-added — a blank line looks like it will print (it is
     * skipped by the spooler, but the user should never have to delete it);
     * Enter on the last row or + Add Row appends one on demand. Silently
     * skips restore when nothing is stored, the shape changed, or it fails.
     */
    private void restoreLastState() {
        try {
            BulkPrintStateStore.TemplateState st = BulkPrintStateStore.load(template.getId());
            if (st == null || st.rows().isEmpty()) return;
            List<BulkPrintStateStore.Row> remembered = st.rows();
            // Sanity: every remembered key must still be a column of this grid.
            List<String> keys = variableOrder();
            for (BulkPrintStateStore.Row r : remembered) {
                for (String k : r.values().keySet()) {
                    if (!keys.contains(k)) return; // template variables changed → start fresh
                }
            }
            rows.clear();
            for (BulkPrintStateStore.Row r : remembered) {
                PrintRow row = new PrintRow();
                for (Map.Entry<String, String> en : r.values().entrySet()) {
                    if (en.getValue() != null && !en.getValue().isBlank()) row.prop(en.getKey()).set(en.getValue());
                }
                row.copies.set(Math.max(1, r.copies()));
                rows.add(row);
            }
            // Sync the fill-all spinner when every row agrees (the common
            // "all same copies" case); otherwise leave it untouched.
            if (!rows.isEmpty()) {
                int first = rows.get(0).copies.get();
                boolean uniform = rows.stream().allMatch(r -> r.copies.get() == first);
                if (uniform && fillAllSpinner != null) {
                    fillSyncing = true;
                    fillAllSpinner.getValueFactory().setValue(first);
                    fillSyncing = false;
                }
            }
            if (st.printer() != null && !st.printer().isBlank()) {
                for (Printer p : printerBox.getItems()) {
                    if (p.getName() != null && p.getName().equals(st.printer())) {
                        printerBox.setValue(p);
                        break;
                    }
                }
            }
            // No trailing empty row — the grid ends on the last REAL line.
            // Enter / + Add Row creates a fresh row the moment it's needed.
            updateTotals();
            refreshPreview();
        } catch (Throwable t) {
            AppLog.debug(t); // restore must never block opening the dialog
        }
    }

    /** Snapshot of the current rows + printer, written on dialog close. */
    private void persistState() {
        try {
            List<BulkPrintStateStore.Row> out = new ArrayList<>();
            for (PrintRow r : rows) {
                if (!r.hasAnyValue()) continue; // an empty tail row is not a session
                Map<String, String> vals = new LinkedHashMap<>();
                for (String k : variableOrder()) {
                    String v = r.get(k);
                    if (v != null && !v.isBlank()) vals.put(k, v);
                }
                out.add(new BulkPrintStateStore.Row(vals, r.getCopies()));
            }
            if (out.isEmpty()) return; // nothing meaningful typed — keep the previous memory
            Printer p = printerBox.getValue();
            BulkPrintStateStore.save(template.getId(),
                    new BulkPrintStateStore.TemplateState(out, p != null ? p.getName() : ""));
        } catch (Throwable t) {
            AppLog.debug(t);
        }
    }

    // ─── Row helpers ──────────────────────────────────────────────────────

    private void addRow() {
        PrintRow row = new PrintRow();
        // Cells start EMPTY — no silent pre-fill of the first possible value
        // (a pre-filled value could quietly end up on printed barcodes).
        // The combo's prompt text shows the possible values instead.
        // Copies inherit the fill-all header value, so a "10 copies" session
        // never silently resets to 1 on new rows.
        if (fillAllSpinner != null && !fillSyncing) {
            Integer v = fillAllSpinner.getValue();
            if (v != null && v > 1) row.copies.set(v);
        }
        rows.add(row);
    }

    private void focusLastRow() {
        if (rows.isEmpty()) return;
        table.getSelectionModel().clearAndSelect(rows.size() - 1,
                table.getColumns().isEmpty() ? null : table.getColumns().get(0));
        table.requestFocus();
        table.scrollTo(rows.size() - 1);
    }

    private void deleteSelectedRow() {
        PrintRow sel = table.getSelectionModel().getSelectedItem();
        if (sel == null && table.getFocusModel().getFocusedCell().getRow() >= 0) {
            int idx = table.getFocusModel().getFocusedCell().getRow();
            if (idx >= 0 && idx < rows.size()) sel = rows.get(idx);
        }
        if (sel != null) {
            rows.remove(sel);
        }
    }

    /**
     * Enter navigation from a cell: commit happened already; move to the row
     * below. A new row is appended ONLY when we are on the last row AND it
     * already carries at least one value — an empty tail row can never breed
     * more empty rows.
     */
    private void moveDownFrom(TableCell<PrintRow, ?> cell) {
        int idx = cell.getIndex();
        if (idx < 0 || idx >= rows.size()) return;
        moveDownFrom(rows.get(idx), cell.getTableColumn());
    }

    private void moveDownFrom(PrintRow current, TableColumn<PrintRow, ?> col) {
        int idx = rows.indexOf(current);
        if (idx < 0 || col == null) return;
        int next = idx + 1;
        boolean toFirstColumn = false;
        if (next >= rows.size()) {
            if (!current.hasAnyValue()) {
                // Last row is still empty — no new row; park on its first column.
                next = idx;
                toFirstColumn = true;
            } else {
                addRow();
                toFirstColumn = true; // a new row always starts at the first column
            }
        } else if (isLastColumn(col)) {
            // Enter in the Copies column: continue on the NEXT row's first
            // column — never straight down into an empty Copies box.
            toFirstColumn = true;
        }
        final int target = Math.min(next, rows.size() - 1);
        final TableColumn<PrintRow, ?> targetCol = toFirstColumn
                ? table.getColumns().get(0) : col;
        Platform.runLater(() -> {
            table.getSelectionModel().clearAndSelect(target, targetCol);
            table.edit(target, targetCol);
            table.scrollTo(target);
        });
    }

    private boolean isLastColumn(TableColumn<PrintRow, ?> col) {
        int n = table.getColumns().size();
        return n > 0 && table.getColumns().get(n - 1) == col;
    }

    private void updateTotals() {
        List<LabelGeometryService.PrintLine> lines = collectLines();
        int total = LabelGeometryService.totalLabels(lines);
        int pages = LabelGeometryService.totalPages(lines, template.labelOrNew());
        int copies = rows.stream().mapToInt(PrintRow::getCopies).sum();
        totalLbl.setText(total + (total == 1 ? " label · " : " labels · ")
                + pages + (pages == 1 ? " strip row · " : " strip rows · ")
                + copies + (copies == 1 ? " copy queued" : " copies queued"));
    }

    private List<LabelGeometryService.PrintLine> collectLines() {
        List<LabelGeometryService.PrintLine> lines = new ArrayList<>();
        List<String> order = variableOrder();
        for (PrintRow row : rows) {
            Map<String, String> values = new LinkedHashMap<>();
            boolean any = false;
            for (String key : order) {
                String v = row.get(key);
                if (v != null && !v.isBlank()) {
                    values.put(key, v.trim());
                    any = true;
                }
            }
            if (!any) continue; // skip fully empty rows
            lines.add(new LabelGeometryService.PrintLine(values, row.getCopies()));
        }
        return lines;
    }

    /** Ordered variable keys — same order as the table columns. */
    public List<String> variableOrder() {
        List<String> order = new ArrayList<>();
        for (VariableDef var : barcodeVars) order.add(var.getKey());
        return order;
    }

    // ─── Live preview (WYSIWYG: physical orientation + real values) ───────

    /** Points the preview at the CURRENT first row and re-renders once. */
    private void attachPreviewRow() {
        PrintRow first = rows.isEmpty() ? null : rows.get(0);
        if (first == previewRow) return; // same source row — property listeners already wired
        for (PropListener pl : previewListeners) {
            pl.prop().removeListener(pl.listener());
        }
        previewListeners.clear();
        previewRow = first;
        if (first != null) {
            for (String key : variableOrder()) {
                StringProperty p = first.prop(key);
                ChangeListener<String> l = (obs, o, n) -> schedulePreviewRefresh();
                p.addListener(l);
                previewListeners.add(new PropListener(p, l));
            }
        }
        refreshPreview();
    }

    /** Coalesces bursts of property changes into one re-render per pulse. */
    private void schedulePreviewRefresh() {
        if (previewScheduled) return;
        previewScheduled = true;
        Platform.runLater(() -> {
            previewScheduled = false;
            refreshPreview();
        });
    }

    /** Values for the preview: row-1's typed values, else first possible value. */
    private Map<String, String> previewValues() {
        Map<String, String> vals = new LinkedHashMap<>();
        for (VariableDef var : barcodeVars) {
            String key = var.getKey();
            String v = previewRow != null ? previewRow.get(key) : "";
            if (v == null || v.isBlank()) {
                List<String> choices = var.choicesList();
                v = !choices.isEmpty() ? choices.get(0) : key;
            }
            vals.put(key, v.trim());
        }
        return vals;
    }

    /**
     * Rebuilds the preview card: label artwork at design size, placed in the
     * PHYSICAL cell (rotated when cfg still carries a 90/270 orientation) —
     * the same geometry the strip preview and the print path use, so this
     * card is pixel-faithful to what comes out of the printer.
     */
    private void refreshPreview() {
        double designW = cfg != null ? cfg.getLabelWidth() : template.labelOrNew().getLabelWidth();
        double designH = cfg != null ? cfg.getLabelHeight() : template.labelOrNew().getLabelHeight();
        double cellW = cfg != null ? LabelGeometryService.physicalCellWidth(cfg) : designW;
        double cellH = cfg != null ? LabelGeometryService.physicalCellHeight(cfg) : designH;
        double angle = 0;
        if (cfg != null) {
            try { angle = Double.parseDouble(cfg.getOrientation()); } catch (Exception ignored) {
            AppLog.debug(ignored); }
        }

        Pane art = LabelRenderUtil.renderLabelNode(template, previewValues(), designW, designH, settings);
        Pane holder = LabelRenderUtil.physicalCellHolder(art, designW, designH, cellW, cellH, angle, false, 0);

        // Scale the physical cell to fit the 146px card.
        double cellWpx = Math.max(1, cellW * LabelRenderUtil.MM_PX);
        double cellHpx = Math.max(1, cellH * LabelRenderUtil.MM_PX);
        double scale = Math.min(1.0, Math.min(146.0 / cellWpx, 146.0 / cellHpx));
        holder.setScaleX(scale);
        holder.setScaleY(scale);

        previewHolder.getChildren().setAll(holder);
    }

    // ─── Printing ─────────────────────────────────────────────────────────

    /**
     * The RAW spooler can take several seconds (the transport waits for the
     * spooler to accept/finish the job) — it therefore runs on a background
     * thread via {@link LabelPrintService#printLabelsQueued}. While spooling,
     * the print buttons are disabled so a queue can never be printed twice;
     * the final toast fires from the background thread's FX callback.
     */
    private void printTest() {
        if (spooling) return;
        List<LabelGeometryService.PrintLine> lines = collectLines();
        LabelGeometryService.PrintLine one;
        if (!lines.isEmpty()) {
            one = new LabelGeometryService.PrintLine(lines.get(0).values, 1);
        } else {
            one = new LabelGeometryService.PrintLine(new LinkedHashMap<>(), 1);
        }
        Printer p = printerBox.getValue();
        setSpooling(true);
        Toast.show(this.getScene() != null ? this.getScene().getRoot() : null,
                "Test Label", "Rendering and spooling the test label…", false);
        LabelPrintService.printLabelsQueued(template, settings, List.of(one), variableOrder(), p, true,
                res -> {
                    setSpooling(false);
                    Toast.show(this.getScene() != null ? this.getScene().getRoot() : null,
                            res.success() ? "Test Label Sent" : "Test Print Failed",
                            res.message(), !res.success());
                });
    }

    private void printAll() {
        if (spooling) return;
        List<LabelGeometryService.PrintLine> lines = collectLines();
        if (lines.isEmpty()) {
            Toast.show(this.getScene().getRoot(), "Nothing To Print", "Add at least one print line with values.", true);
            return;
        }
        Printer p = printerBox.getValue();
        // Capture the toast surface before closing (scene detaches on close).
        javafx.scene.layout.Pane toastRoot = this.getScene() != null && this.getScene().getRoot() instanceof javafx.scene.layout.Pane pr
                ? pr : null;
        // Close immediately on Print — the job spools in the background; the
        // toast confirms delivery so the user is never left watching the dialog.
        close();
        LabelPrintService.printLabelsQueued(template, settings, lines, variableOrder(), p, false,
                res -> {
                    Toast.show(toastRoot,
                            res.success() ? "Labels Sent" : "Print Failed",
                            res.message(), !res.success());
                });
    }

    /** Disables the print actions while a job is spooling in the background. */
    private void setSpooling(boolean on) {
        spooling = on;
        if (testBtn != null) {
            testBtn.setDisable(on);
            testBtn.setText(on ? "Spooling…" : "Test Print (1)");
        }
        if (printBtn != null) {
            printBtn.setDisable(on);
            printBtn.setText(on ? "Sending…" : "Print All  (Ctrl+Enter)");
        }
        if (calibrateBtn != null) {
            calibrateBtn.setDisable(on);
        }
    }

    // ─── Stock-sensor calibration ──────────────────────────────────────

    /**
     * Sends the TSC AUTODETECT calibration job (its own RAW spool job): the
     * printer feeds a few labels while it measures the die-cut pitch, then
     * stores the result. This is the documented cure for printers that feed
     * extra blank labels around the printed ones — their learned pitch no
     * longer matches the loaded roll.
     */
    private void calibrateSensor() {
        if (spooling) return;
        Printer p = printerBox.getValue();
        if (!com.invoicestudio.service.TsplPrintService.enabledFor(p)) {
            Toast.show(this.getScene() != null ? this.getScene().getRoot() : null,
                    "TSC / TSPL Printers Only",
                    "Stock-sensor calibration speaks the TSC TSPL language (AUTODETECT). "
                            + "Select your TSC printer and try again.", true);
            return;
        }
        setSpooling(true);
        Toast.show(this.getScene() != null ? this.getScene().getRoot() : null,
                "Calibrating Stock Sensor",
                "Sending AUTODETECT — the printer will feed a few labels while it measures the die-cut pitch…",
                false);
        com.invoicestudio.service.TsplPrintService.calibrateSensorQueued(p, res -> {
            setSpooling(false);
            Toast.show(this.getScene() != null ? this.getScene().getRoot() : null,
                    res.success() ? "Calibration Sent" : "Calibration Failed",
                    res.message(), !res.success());
        });
    }

    // ─── Keyboard ─────────────────────────────────────────────────────────

    private void installKeyboardShortcuts(Scene scene) {
        scene.getAccelerators().put(
                KeyCombination.keyCombination("Ctrl+Enter"), this::printAll);
        scene.getAccelerators().put(
                KeyCombination.keyCombination("Insert"), this::addRowAndFocus);
        scene.getAccelerators().put(
                KeyCombination.keyCombination("Alt+N"), this::addRowAndFocus);
        scene.getAccelerators().put(
                KeyCombination.keyCombination("Ctrl+Delete"), this::deleteSelectedRow);

        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F4) { close(); return; }
            if (e.getCode() == KeyCode.DELETE && e.isControlDown()) { deleteSelectedRow(); e.consume(); return; }
            if (e.getCode() == KeyCode.INSERT || (e.getCode() == KeyCode.N && e.isAltDown())) { addRowAndFocus(); e.consume(); return; }

            // Enter with focus on the CELL itself (keyboard navigation, no
            // editor open) = move down. When an editor has focus this event
            // targets the editor's TextField, never the cell — the cell's
            // own filter handles commit+navigation there, so there is
            // exactly ONE navigation path per Enter press.
            if (e.getCode() == KeyCode.ENTER && e.getTarget() instanceof javafx.scene.Node tNode
                    && (tNode instanceof TableCell || tNode instanceof TableRow)
                    && isDescendant(table, tNode)) {
                javafx.scene.control.TablePosition<?, ?> pos = table.getFocusModel().getFocusedCell();
                if (pos != null && pos.getTableColumn() != null
                        && pos.getRow() >= 0 && pos.getRow() < rows.size()) {
                    @SuppressWarnings("unchecked")
                    TableColumn<PrintRow, ?> col = (TableColumn<PrintRow, ?>) pos.getTableColumn();
                    moveDownFrom(rows.get(pos.getRow()), col);
                    e.consume();
                }
            }
        });
    }

    private void addRowAndFocus() {
        addRow();
        focusLastRow();
    }

    private static boolean isDescendant(javafx.scene.Parent parent, javafx.scene.Node node) {
        if (node == null) return false;
        javafx.scene.Node n = node;
        while (n != null) {
            if (n == parent) return true;
            n = n.getParent();
        }
        return false;
    }
}
