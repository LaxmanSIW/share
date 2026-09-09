package com.invoicestudio.ui.views;

import com.invoicestudio.model.BuyerFieldDef;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.VariableDef;
import com.invoicestudio.ui.DialogHelper;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.Toast;
import com.invoicestudio.ui.UiTheme;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.List;

/**
 * Variables — v3 redesign.
 * Custom placeholder management with built-in variable reference,
 * buyer custom fields overview and the custom variables table.
 * Row hover is now pure CSS (previously two inline-styled mouse listeners per row).
 */
public class VariablesView extends VBox {

    private final StudioApp app;

    private final TextField labelInput = new TextField();
    private final TextField keyInput = new TextField();
    private final ComboBox<String> typeSelect = new ComboBox<>();
    private final VBox customVarsContainer = new VBox();
    private final VBox buyerFieldsContainer = new VBox();

    // Default built-in vars list
    private static final List<VarPair> BUILTIN_VARS = List.of(
            new VarPair("Invoice Number", "invoice_no"),
            new VarPair("Invoice Date", "invoice_date"),
            new VarPair("Due Date", "due_date"),
            new VarPair("PO Number", "po_no"),
            new VarPair("Vehicle Number", "vehicle_no"),
            new VarPair("Buyer Name", "buyer_name"),
            new VarPair("Buyer Trade Name", "buyer_trade_name"),
            new VarPair("Buyer GSTIN", "buyer_gstin"),
            new VarPair("Buyer Address", "buyer_address"),
            new VarPair("Buyer Phone", "buyer_phone"),
            new VarPair("Buyer Email", "buyer_email"),
            new VarPair("Buyer State", "buyer_state"),
            new VarPair("Buyer State Code", "buyer_state_code"),
            new VarPair("My Business Name", "business_name"),
            new VarPair("My Business GSTIN", "business_gstin"),
            new VarPair("My Business Phone", "business_phone"),
            new VarPair("My Business State", "business_state"),
            new VarPair("My Business State Code", "business_state_code"),
            new VarPair("My Bank Name", "business_bank_name"),
            new VarPair("My Account No", "business_account_no"),
            new VarPair("My IFSC Code", "business_ifsc"),
            new VarPair("My UPI ID", "business_upi")
    );

    private record VarPair(String label, String key) {}

    public VariablesView(StudioApp app) {
        this.app = app;

        setSpacing(20);
        setPadding(new Insets(24));
        getStyleClass().add("view-page");

        buildHeader();
        buildAddForm();
        buildBuiltinSection();
        buildBuyerFieldsSection();
        buildCustomListSection();

        reload();
    }

    private void buildHeader() {
        VBox titleBox = new VBox(4);
        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label titleIcon = new Label("χ");
        titleIcon.getStyleClass().addAll("icon-accent", "icon-lg");
        Label title = new Label("Variables");
        title.getStyleClass().add("view-title");
        titleRow.getChildren().addAll(titleIcon, title);

        Label sub = new Label("Variables are placeholders you drop into templates — they get filled with real values when a bill is generated.");
        sub.getStyleClass().add("view-subtitle");
        titleBox.getChildren().addAll(titleRow, sub);
        getChildren().add(titleBox);
    }

    private void buildAddForm() {
        VBox card = UiTheme.card(14);

        HBox head = new HBox(8);
        head.setAlignment(Pos.CENTER_LEFT);
        Label secTitle = new Label("ADD CUSTOM VARIABLE");
        secTitle.getStyleClass().add("card-title");
        Label subHint = new Label("· Create custom placeholders for your bill templates");
        subHint.getStyleClass().add("kpi-subtext");
        head.getChildren().addAll(secTitle, subHint);

        HBox formRow = new HBox(14);
        formRow.setAlignment(Pos.BOTTOM_LEFT);

        // Label
        VBox labelBox = new VBox(6);
        Label lbl = new Label("LABEL *");
        lbl.getStyleClass().add("field-label");
        labelInput.setPromptText("e.g. Delivery Date");
        labelInput.textProperty().addListener((obs, oldV, newV) -> {
            if (keyInput.getText().trim().isEmpty() || slugify(oldV).equals(keyInput.getText().trim())) {
                keyInput.setText(slugify(newV));
            }
        });
        labelBox.getChildren().addAll(lbl, labelInput);
        HBox.setHgrow(labelBox, Priority.ALWAYS);

        // Key
        VBox keyBox = new VBox(6);
        Label klbl = new Label("KEY (PLACEHOLDER)");
        klbl.getStyleClass().add("field-label");

        HBox keyGroup = new HBox(6);
        keyGroup.setAlignment(Pos.CENTER_LEFT);

        Label bra1 = new Label("{{");
        bra1.getStyleClass().addAll("code-pill");
        keyInput.setPromptText("delivery_date");
        keyInput.getStyleClass().add("code-input");
        HBox.setHgrow(keyInput, Priority.ALWAYS);
        keyInput.setMaxWidth(Double.MAX_VALUE);

        Label bra2 = new Label("}}");
        bra2.getStyleClass().addAll("code-pill");
        keyGroup.getChildren().addAll(bra1, keyInput, bra2);
        HBox.setHgrow(keyGroup, Priority.ALWAYS);

        keyBox.getChildren().addAll(klbl, keyGroup);
        HBox.setHgrow(keyBox, Priority.ALWAYS);

        // Type
        VBox typeBox = new VBox(6);
        Label tlbl = new Label("TYPE");
        tlbl.getStyleClass().add("field-label");
        typeSelect.setItems(FXCollections.observableArrayList("text", "number", "date"));
        typeSelect.setValue("text");
        typeSelect.setPrefWidth(120);
        typeBox.getChildren().addAll(tlbl, typeSelect);

        // Add button
        Button addBtn = UiTheme.goldBtn("Add Variable");
        addBtn.setGraphic(new Label("＋"));
        addBtn.setPrefWidth(140);
        addBtn.setOnAction(e -> handleAdd());

        formRow.getChildren().addAll(labelBox, keyBox, typeBox, addBtn);
        card.getChildren().addAll(head, formRow);
        getChildren().add(card);
    }

    private void handleAdd() {
        String label = labelInput.getText().trim();
        String key = keyInput.getText().trim();
        if (key.isEmpty()) {
            key = slugify(label);
        }
        if (label.isEmpty() || key.isEmpty()) {
            Toast.show(this, "Validation", "Please enter both a label and key.", true);
            return;
        }

        try {
            VariableDef v = new VariableDef();
            v.setKey(key);
            v.setLabel(label);
            v.setType(typeSelect.getValue() != null ? typeSelect.getValue() : "text");
            v.setBuiltin(false);
            app.getData().variables().saveVariable(v);
            Toast.show(this, "Variable Added", "Variable {{" + key + "}} created successfully.", false);
            labelInput.clear();
            keyInput.clear();
            reload();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.show(this, "Error", "Failed to add variable: " + e.getMessage(), true);
        }
    }

    private void buildBuiltinSection() {
        VBox card = UiTheme.card(14);

        HBox head = new HBox(8);
        head.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("BUILT-IN BILL VARIABLES");
        title.getStyleClass().add("card-title");
        Label count = new Label("· " + BUILTIN_VARS.size() + " SYSTEM FIELDS");
        count.getStyleClass().add("kpi-subtext");
        head.getChildren().addAll(title, count);

        GridPane grid = new GridPane();
        grid.setHgap(14);
        grid.setVgap(8);
        for (int i = 0; i < 3; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(33.333);
            grid.getColumnConstraints().add(cc);
        }

        int col = 0;
        int row = 0;
        for (VarPair vp : BUILTIN_VARS) {
            HBox item = new HBox(10);
            item.setAlignment(Pos.CENTER_LEFT);
            item.setPadding(new Insets(7, 12, 7, 12));
            item.getStyleClass().add("card-pane-subtle");

            Label lbl = new Label(vp.label());
            lbl.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(lbl, Priority.ALWAYS);

            Label keyPill = UiTheme.codePill("{{" + vp.key() + "}}");

            item.getChildren().addAll(lbl, keyPill);
            grid.add(item, col, row);

            col++;
            if (col >= 3) {
                col = 0;
                row++;
            }
        }

        HBox footerBox = new HBox(8);
        footerBox.setAlignment(Pos.CENTER_LEFT);
        footerBox.setPadding(new Insets(4, 4, 0, 4));
        Label footer = new Label("Plus automatic computed totals: {{subtotal}} {{cgst}} {{sgst}} {{igst}} {{grand_total}} {{amount_in_words}} {{balance_due}} {{paid_amount}} {{amount_paid_words}}...");
        footer.getStyleClass().add("kpi-subtext");
        footerBox.getChildren().addAll(footer);

        card.getChildren().addAll(head, grid, footerBox);
        getChildren().add(card);
    }

    private void buildBuyerFieldsSection() {
        buyerFieldsContainer.setSpacing(14);
        getChildren().add(buyerFieldsContainer);
    }

    private void buildCustomListSection() {
        VBox card = UiTheme.card(14);

        HBox head = new HBox(8);
        head.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("YOUR CUSTOM VARIABLES");
        title.getStyleClass().add("card-title");
        Label subHint = new Label("· User-defined placeholders available across templates");
        subHint.getStyleClass().add("kpi-subtext");
        head.getChildren().addAll(title, subHint);

        customVarsContainer.setSpacing(6);

        card.getChildren().addAll(head, customVarsContainer);
        getChildren().add(card);
    }

    public void reload() {
        renderBuyerFields();
        renderCustomList();
    }

    private void renderBuyerFields() {
        buyerFieldsContainer.getChildren().clear();
        try {
            Settings s = app.getData().getSettings();
            List<BuyerFieldDef> fields = s != null && s.getBuyerFields() != null ? s.getBuyerFields() : List.of();
            if (fields.isEmpty()) return;

            VBox card = UiTheme.card(14);

            HBox head = new HBox(8);
            head.setAlignment(Pos.CENTER_LEFT);
            Label title = new Label("BUYER CUSTOM FIELDS");
            title.getStyleClass().add("card-title");
            Label count = new Label("· " + fields.size() + " CONFIGURED (Defined in Settings)");
            count.getStyleClass().add("kpi-subtext");
            head.getChildren().addAll(title, count);

            GridPane grid = new GridPane();
            grid.setHgap(14);
            grid.setVgap(8);
            for (int i = 0; i < 3; i++) {
                ColumnConstraints cc = new ColumnConstraints();
                cc.setPercentWidth(33.333);
                grid.getColumnConstraints().add(cc);
            }

            int col = 0;
            int row = 0;
            for (BuyerFieldDef bf : fields) {
                HBox item = new HBox(10);
                item.setAlignment(Pos.CENTER_LEFT);
                item.setPadding(new Insets(7, 12, 7, 12));
                item.getStyleClass().add("card-pane-subtle");

                HBox left = new HBox(6);
                left.setAlignment(Pos.CENTER_LEFT);
                Label lbl = new Label(bf.getLabel());
                Label typeBadge = UiTheme.pill(bf.getType() != null ? bf.getType().toUpperCase() : "TEXT");
                left.getChildren().addAll(lbl, typeBadge);
                left.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(left, Priority.ALWAYS);

                Label keyPill = UiTheme.codePill("{{buyer_" + bf.getKey() + "}}");

                item.getChildren().addAll(left, keyPill);
                grid.add(item, col, row);

                col++;
                if (col >= 3) {
                    col = 0;
                    row++;
                }
            }

            card.getChildren().addAll(head, grid);
            buyerFieldsContainer.getChildren().add(card);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void renderCustomList() {
        customVarsContainer.getChildren().clear();
        try {
            List<VariableDef> list = app.getData().variables().getAllVariables();
            if (list.isEmpty()) {
                customVarsContainer.getChildren().add(UiTheme.emptyState("χ",
                        "No custom variables created yet",
                        "Use the form above to add custom placeholders for your invoices"));
                return;
            }

            // Header row
            HBox th = new HBox(16);
            th.setAlignment(Pos.CENTER_LEFT);
            th.getStyleClass().add("table-header-row");
            th.setPadding(new Insets(10, 16, 10, 16));

            Label hLabel = new Label("VARIABLE LABEL");
            hLabel.getStyleClass().add("table-th");
            hLabel.setMinWidth(180);
            hLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(hLabel, Priority.ALWAYS);

            Label hKey = new Label("PLACEHOLDER KEY");
            hKey.getStyleClass().add("table-th");
            hKey.setPrefWidth(260);
            hKey.setMinWidth(260);
            hKey.setMaxWidth(260);

            Label hType = new Label("DATA TYPE");
            hType.getStyleClass().add("table-th");
            hType.setPrefWidth(110);
            hType.setMinWidth(110);
            hType.setMaxWidth(110);

            Label hAct = new Label("ACTION");
            hAct.getStyleClass().add("table-th");
            hAct.setPrefWidth(80);
            hAct.setMinWidth(80);
            hAct.setMaxWidth(80);
            hAct.setAlignment(Pos.CENTER_RIGHT);

            th.getChildren().addAll(hLabel, hKey, hType, hAct);
            customVarsContainer.getChildren().add(th);

            for (VariableDef vd : list) {
                HBox row = new HBox(16);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("table-data-row");
                row.setPadding(new Insets(10, 16, 10, 16));

                // Column 1: Label
                Label lbl = new Label(vd.getLabel());
                lbl.getStyleClass().add("table-cell-title");
                lbl.setMinWidth(180);
                lbl.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(lbl, Priority.ALWAYS);

                // Column 2: Placeholder Key (fixed width)
                HBox keyCol = new HBox();
                keyCol.setAlignment(Pos.CENTER_LEFT);
                keyCol.setPrefWidth(260);
                keyCol.setMinWidth(260);
                keyCol.setMaxWidth(260);
                keyCol.getChildren().add(UiTheme.codePill("{{" + vd.getKey() + "}}"));

                // Column 3: Type (fixed width)
                HBox typeCol = new HBox();
                typeCol.setAlignment(Pos.CENTER_LEFT);
                typeCol.setPrefWidth(110);
                typeCol.setMinWidth(110);
                typeCol.setMaxWidth(110);
                typeCol.getChildren().add(UiTheme.pill(vd.getType() != null ? vd.getType().toUpperCase() : "TEXT"));

                // Column 4: Action (fixed width)
                HBox actCol = new HBox();
                actCol.setAlignment(Pos.CENTER_RIGHT);
                actCol.setPrefWidth(80);
                actCol.setMinWidth(80);
                actCol.setMaxWidth(80);

                Button del = new Button("🗑");
                del.getStyleClass().add("button-icon-subtle");
                del.setTooltip(new Tooltip("Delete variable"));
                del.setOnAction(e -> {
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete variable {{" + vd.getKey() + "}}?", ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Delete Variable");
                    confirm.setHeaderText("Delete Variable: " + vd.getLabel());
                    confirm.setContentText("Are you sure you want to delete placeholder {{" + vd.getKey() + "}}?");
                    DialogHelper.styleDialog(confirm, 420, 200);
                    confirm.showAndWait().ifPresent(res -> {
                        if (res == ButtonType.YES) {
                            try {
                                app.getData().variables().deleteVariable(vd.getKey());
                                Toast.show(this, "Variable Deleted", "Variable {{" + vd.getKey() + "}} was removed.", false);
                                reload();
                            } catch (Exception ex) {
                                Toast.show(this, "Delete Failed", ex.getMessage(), true);
                            }
                        }
                    });
                });
                actCol.getChildren().add(del);

                row.getChildren().addAll(lbl, keyCol, typeCol, actCol);
                customVarsContainer.getChildren().add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String slugify(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
