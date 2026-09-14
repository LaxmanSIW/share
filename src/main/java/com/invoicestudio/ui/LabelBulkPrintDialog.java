package com.invoicestudio.ui;

import com.invoicestudio.model.LabelConfig;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;
import com.invoicestudio.model.VariableDef;
import com.invoicestudio.service.LabelGeometryService;
import com.invoicestudio.service.LabelPrintService;
import com.invoicestudio.service.LabelRenderUtil;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.print.Printer;
import javafx.scene.Scene;
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

/**
 * Bulk Label Print popup (Barcode Mode).
 * <p>
 * One row per print line: one editable cell per barcode variable
 * (quick-picks from the variable's possible values, free typing allowed)
 * plus a copies cell. Pressing Enter commits and jumps to the next row —
 * adding a fresh row automatically at the end — so a whole queue like
 * "A/19/S ×20, A/20/M ×10, B/19/S ×11" can be typed without touching the
 * mouse. Arrow keys / Tab / Shift+Tab / Left/Right all navigate cells.
 * <p>
 * Keyboard map:
 * <ul>
 *   <li>Enter — commit cell, move DOWN (adds a new row on the last one)</li>
 *   <li>Tab / Shift+Tab — next / previous cell</li>
 *   <li>↑ ↓ ← → — move between rows / cells</li>
 *   <li>Insert or Alt+N — add row · Ctrl+Delete — remove selected row</li>
 *   <li>Ctrl+Enter — Print All · F4 — close</li>
 * </ul>
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
        public int getCopies() { return Math.max(1, copies.get()); }
    }

    private final Template template;
    private final Settings settings;
    private final List<VariableDef> barcodeVars;
    private final TableView<PrintRow> table = new TableView<>();
    private final ObservableList<PrintRow> rows = FXCollections.observableArrayList();
    private final Label totalLbl = new Label("0 labels");
    private final ComboBox<Printer> printerBox = new ComboBox<>();

    public LabelBulkPrintDialog(javafx.stage.Window owner, Template template, Settings settings,
                                List<VariableDef> barcodeVars, LabelConfig cfg) {
        this.template = template;
        this.settings = settings;
        this.barcodeVars = barcodeVars != null ? barcodeVars : List.of();

        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Bulk Label Print — " + template.getName());
        setMinWidth(680);
        setMinHeight(460);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));
        root.getStyleClass().add("bg-app");

        // ── Top: info + preview of the label design ──
        HBox top = new HBox(14);
        top.setAlignment(Pos.CENTER_LEFT);

        double designW = cfg != null ? cfg.getLabelWidth() : template.labelOrNew().getLabelWidth();
        double designH = cfg != null ? cfg.getLabelHeight() : template.labelOrNew().getLabelHeight();
        double previewScale = Math.min(1.0, Math.min(150.0 / Math.max(1, designW), 150.0 / Math.max(1, designH)));
        Pane labelPreview = LabelRenderUtil.renderLabelNode(template, null, designW, designH, settings);
        labelPreview.setScaleX(previewScale);
        labelPreview.setScaleY(previewScale);
        StackPane previewHolder = new StackPane(labelPreview);
        previewHolder.setPrefSize(160, 160);
        previewHolder.setMinSize(160, 160);
        previewHolder.setStyle("-fx-background-color: #0F172A; -fx-background-radius: 6;");
        previewHolder.setPadding(new Insets(6));

        VBox info = new VBox(6);
        Label title = new Label("Print many labels with different values");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: -color-fg;");
        Label sub = new Label("One row = one print line. Type values into the variable columns, set copies, then Print All.\n"
                + "Enter moves to the next row; the last Enter adds a new row. Use ↑↓←→ to navigate.");
        sub.setWrapText(true);
        sub.getStyleClass().add("text-muted");
        info.getChildren().addAll(title, sub);

        top.getChildren().addAll(previewHolder, info);
        VBox.setVgrow(info, Priority.ALWAYS);
        root.setTop(top);

        // ── Center: the print-line table ──
        buildTable();
        table.setItems(rows);
        addRow();
        root.setCenter(table);

        // ── Bottom: buttons ──
        HBox bottom = new HBox(10);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(10, 0, 0, 0));

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

        printerBox.getItems().addAll(Printer.getAllPrinters());
        Printer def = Printer.getDefaultPrinter();
        printerBox.setValue(def);
        printerBox.setPrefWidth(210);
        printerBox.setTooltip(new Tooltip("Target printer (e.g. TSC TA210)"));

        Button testBtn = new Button("Test Print (1)");
        testBtn.getStyleClass().addAll("button-sm", "button-secondary");
        testBtn.setTooltip(new Tooltip("Print one label with the first row's values to verify alignment"));
        testBtn.setOnAction(e -> printTest());

        Button printBtn = new Button("Print All  (Ctrl+Enter)");
        printBtn.getStyleClass().addAll("gold-btn");
        printBtn.setOnAction(e -> printAll());

        Button closeBtn = new Button("Close");
        closeBtn.getStyleClass().addAll("button-sm", "button-secondary");
        closeBtn.setOnAction(e -> close());

        bottom.getChildren().addAll(addBtn, delBtn, spacer, totalLbl, new Separator(Orientation.VERTICAL), printerBox, testBtn, printBtn, closeBtn);
        root.setBottom(bottom);

        Scene scene = new Scene(root, 760, 540);
        com.invoicestudio.ui.DialogHelper.styleScene(scene);
        setScene(scene);
        installKeyboardShortcuts(scene);
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
        table.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        table.getSelectionModel().setCellSelectionEnabled(true);
        table.setPlaceholder(new Label("No print lines — press Insert to add one."));
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        VBox.setVgrow(table, Priority.ALWAYS);

        int colIdx = 0;
        for (VariableDef var : barcodeVars) {
            final String key = var.getKey();
            final String header = (var.getLabel() != null && !var.getLabel().isBlank())
                    ? var.getLabel() : key;
            final List<String> choices = var.choicesList();

            TableColumn<PrintRow, String> col = new TableColumn<>(header + "  {{" + key + "}}");
            final int cIdx = colIdx;
            col.setMinWidth(120);
            col.setCellValueFactory(param -> param.getValue().prop(key));

            col.setCellFactory(tc -> new VarCell(choices));

            col.setOnEditCommit(ev -> {
                PrintRow row = ev.getRowValue();
                row.prop(key).set(ev.getNewValue() != null ? ev.getNewValue() : "");
                updateTotals();
                Platform.runLater(() -> moveDown(row, col));
            });
            col.setUserData(cIdx);
            table.getColumns().add(col);
            colIdx++;
        }

        TableColumn<PrintRow, Number> copiesCol = new TableColumn<>("Copies");
        copiesCol.setMinWidth(90);
        copiesCol.setCellValueFactory(param -> param.getValue().copies);
        copiesCol.setCellFactory(tc -> new CopiesCell());
        copiesCol.setOnEditCommit(ev -> {
            ev.getRowValue().copies.set(Math.max(1, ev.getNewValue().intValue()));
            updateTotals();
        });
        table.getColumns().add(copiesCol);

        rows.addListener((javafx.collections.ListChangeListener<PrintRow>) c -> updateTotals());
    }

    /** Editable combo cell: quick-pick from possible values + free typing. */
    private class VarCell extends TableCell<PrintRow, String> {
        private final ComboBox<String> combo = new ComboBox<>();
        private final List<String> choices;

        VarCell(List<String> choices) {
            this.choices = choices;
            combo.setEditable(true);
            combo.getStyleClass().add("fs-11");
            combo.setItems(FXCollections.observableArrayList(choices));
            combo.setPrefWidth(150);
            combo.setMaxWidth(Double.MAX_VALUE);
            combo.setOnAction(e -> {
                if (isEditing()) commitEdit(combo.getValue());
            });
            combo.focusedProperty().addListener((obs, o, n) -> {
                if (!n && isEditing()) commitEdit(combo.getValue());
            });
            // Type-to-filter: narrow the quick-picks as the user types.
            combo.getEditor().textProperty().addListener((obs, o, v) -> {
                if (!combo.isShowing() && v != null && !v.isEmpty()) {
                    List<String> filtered = choices.stream()
                            .filter(c -> c.toLowerCase().startsWith(v.toLowerCase())).toList();
                    combo.setItems(FXCollections.observableArrayList(filtered));
                } else if (v != null && v.isEmpty()) {
                    combo.setItems(FXCollections.observableArrayList(choices));
                }
            });
            combo.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    commitEdit(combo.getValue());
                    e.consume();
                }
            });
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setGraphic(null);
            } else {
                combo.getEditor().setText(item != null ? item : "");
                combo.setValue(item);
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
    private static class CopiesCell extends TableCell<PrintRow, Number> {
        private final TextField field = new TextField();

        CopiesCell() {
            field.setPrefWidth(70);
            field.getStyleClass().add("fs-11");
            field.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
                if (e.getCode() == KeyCode.ENTER) {
                    commitFromField();
                    e.consume();
                }
            });
            field.focusedProperty().addListener((obs, o, n) -> {
                if (!n) commitFromField();
            });
            setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        }

        private void commitFromField() {
            try {
                int v = Integer.parseInt(field.getText().trim());
                commitEdit(Math.max(1, v));
            } catch (NumberFormatException ignored) {
                commitEdit(1);
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

    // ─── Row helpers ──────────────────────────────────────────────────────

    private void addRow() {
        PrintRow row = new PrintRow();
        for (VariableDef var : barcodeVars) {
            List<String> choices = var.choicesList();
            if (!choices.isEmpty()) {
                row.prop(var.getKey()).set(choices.get(0));
            }
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

    private void moveDown(PrintRow current, TableColumn<PrintRow, ?> col) {
        int idx = rows.indexOf(current);
        if (idx < 0) return;
        int next = idx + 1;
        if (next >= rows.size()) {
            addRow();
        }
        final int target = Math.min(next, rows.size() - 1);
        Platform.runLater(() -> {
            table.getSelectionModel().clearAndSelect(target, col);
            table.edit(target, col);
            table.scrollTo(target);
        });
    }

    private void updateTotals() {
        List<LabelGeometryService.PrintLine> lines = collectLines();
        int total = LabelGeometryService.totalLabels(lines);
        int pages = LabelGeometryService.totalPages(lines, template.labelOrNew());
        int copies = rows.stream().mapToInt(PrintRow::getCopies).sum();
        totalLbl.setText(total + " labels · " + pages + " strip rows · " + copies + " copies queued");
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

    // ─── Printing ─────────────────────────────────────────────────────────

    private void printTest() {
        List<LabelGeometryService.PrintLine> lines = collectLines();
        LabelGeometryService.PrintLine one;
        if (!lines.isEmpty()) {
            one = new LabelGeometryService.PrintLine(lines.get(0).values, 1);
        } else {
            one = new LabelGeometryService.PrintLine(new LinkedHashMap<>(), 1);
        }
        Printer p = printerBox.getValue();
        LabelPrintService.PrintResult res = LabelPrintService.printLabels(
                template, settings, List.of(one), variableOrder(), p, true);
        Toast.show(this.getScene() != null ? this.getScene().getRoot() : null,
                res.success() ? "Test Label Sent" : "Test Print Failed",
                res.message(), !res.success());
    }

    private void printAll() {
        List<LabelGeometryService.PrintLine> lines = collectLines();
        if (lines.isEmpty()) {
            Toast.show(this.getScene().getRoot(), "Nothing To Print", "Add at least one print line with values.", true);
            return;
        }
        Printer p = printerBox.getValue();
        LabelPrintService.PrintResult res = LabelPrintService.printLabels(
                template, settings, lines, variableOrder(), p, false);
        Toast.show(this.getScene().getRoot(),
                res.success() ? "Labels Sent" : "Print Failed",
                res.message(), !res.success());
        if (res.success()) close();
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

            // Enter inside the table = commit + down (last row → add row)
            if (e.getCode() == KeyCode.ENTER && e.getTarget() instanceof javafx.scene.Node tNode && isDescendant(table, tNode)) {
                javafx.scene.control.TablePosition<?, ?> pos = table.getFocusModel().getFocusedCell();
                if (pos != null && pos.getTableColumn() != null) {
                    @SuppressWarnings("unchecked")
                    TableColumn<PrintRow, ?> col = (TableColumn<PrintRow, ?>) pos.getTableColumn();
                    int rowIdx = pos.getRow();
                    if (rowIdx >= 0 && rowIdx < rows.size()) {
                        moveDown(rows.get(rowIdx), col);
                        e.consume();
                    }
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
