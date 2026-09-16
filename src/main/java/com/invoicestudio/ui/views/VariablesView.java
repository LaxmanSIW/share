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
 * Variables — v4 redesign.
 * Custom placeholder management with built-in variable reference,
 * buyer custom fields overview, and the custom variables table.
 *
 * Custom variables now have three scopes:
 *  - "fixed"   → one value per bill (input shown in CreateBillView)
 *  - "table"   → one value per line item (add as a table column via TemplateDesigner)
 *  - "barcode" → one value per label in Bulk Label Print (with possible values)
 */
public class VariablesView extends VBox {

    private final StudioApp app;

    // Add form controls
    private final TextField labelInput  = new TextField();
    private final TextField keyInput    = new TextField();
    private final ComboBox<String> typeSelect = new ComboBox<>();
    private final TextField defaultValueInput = new TextField();
    private final TextField choicesInput = new TextField();

    // Scope selection — uses btn-filter-pill pattern (matches app filter tabs)
    private String currentScope = "fixed";  // fixed | table | barcode
    private Button scopeFixedBtn;
    private Button scopeTableBtn;
    private Button scopeBarcodeBtn;
    private VBox defaultValueRow;
    private VBox choicesRow;

    // Containers rebuilt on reload
    private final VBox customVarsContainer  = new VBox();
    private final VBox buyerFieldsContainer = new VBox();

    // Default built-in vars list
    private static final List<VarPair> BUILTIN_VARS = List.of(
            new VarPair("Invoice Number",       "invoice_no"),
            new VarPair("Invoice Date",         "invoice_date"),
            new VarPair("Due Date",             "due_date"),
            new VarPair("PO Number",            "po_no"),
            new VarPair("Vehicle Number",       "vehicle_no"),
            new VarPair("Buyer Name",           "buyer_name"),
            new VarPair("Buyer Trade Name",     "buyer_trade_name"),
            new VarPair("Buyer GSTIN",          "buyer_gstin"),
            new VarPair("Buyer Address",        "buyer_address"),
            new VarPair("Buyer Phone",          "buyer_phone"),
            new VarPair("Buyer Email",          "buyer_email"),
            new VarPair("Buyer State",          "buyer_state"),
            new VarPair("Buyer State Code",     "buyer_state_code"),
            new VarPair("My Business Name",     "business_name"),
            new VarPair("My Business GSTIN",    "business_gstin"),
            new VarPair("My Business Phone",    "business_phone"),
            new VarPair("My Business State",    "business_state"),
            new VarPair("My Business State Code","business_state_code"),
            new VarPair("My Bank Name",         "business_bank_name"),
            new VarPair("My Account No",        "business_account_no"),
            new VarPair("My IFSC Code",         "business_ifsc"),
            new VarPair("My UPI ID",            "business_upi")
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

    // ─── Header ────────────────────────────────────────────────────────────────

    private void buildHeader() {
        VBox titleBox = new VBox(4);
        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label titleIcon = new Label("χ");
        titleIcon.getStyleClass().addAll("icon-accent", "icon-lg");
        Label title = new Label("Variables");
        title.getStyleClass().add("view-title");
        titleRow.getChildren().addAll(titleIcon, title);

        Label sub = new Label("Variables are placeholders you drop into templates — they get filled with real values when a bill or label run is generated.\n" +
                "• Bill Field (fixed) → one value per bill   • Table Column (per item) → one value per line   • Barcode Label → one value per printed label");
        sub.getStyleClass().add("view-subtitle");
        sub.setWrapText(true);
        titleBox.getChildren().addAll(titleRow, sub);
        getChildren().add(titleBox);
    }

    // ─── Add Form ──────────────────────────────────────────────────────────────

    private void buildAddForm() {
        VBox card = UiTheme.card(14);

        // ── Section header ──
        HBox head = new HBox(8);
        head.setAlignment(Pos.CENTER_LEFT);
        Label secTitle = new Label("ADD CUSTOM VARIABLE");
        secTitle.getStyleClass().add("card-title");
        Label subHint  = new Label("· Create custom placeholders for your bill templates");
        subHint.getStyleClass().add("kpi-subtext");
        head.getChildren().addAll(secTitle, subHint);

        // ── Scope selector — segmented pill bar (btn-filter-pill pattern) ──
        HBox scopeBar = new HBox(4);
        scopeBar.getStyleClass().add("toggle-group-container");
        scopeBar.setAlignment(Pos.CENTER_LEFT);

        scopeFixedBtn = new Button("🔖  Bill Field (fixed)");
        scopeFixedBtn.getStyleClass().addAll("btn-filter-pill", "active");
        scopeFixedBtn.setOnAction(e -> setScope("fixed"));

        scopeTableBtn = new Button("📋  Table Column (per item)");
        scopeTableBtn.getStyleClass().add("btn-filter-pill");
        scopeTableBtn.setOnAction(e -> setScope("table"));

        scopeBarcodeBtn = new Button("🏷  Barcode Label");
        scopeBarcodeBtn.getStyleClass().add("btn-filter-pill");
        scopeBarcodeBtn.setOnAction(e -> setScope("barcode"));

        scopeBar.getChildren().addAll(scopeFixedBtn, scopeTableBtn, scopeBarcodeBtn);

        HBox scopeRow = new HBox(10);
        scopeRow.setAlignment(Pos.CENTER_LEFT);
        Label scopeLbl = new Label("SCOPE");
        scopeLbl.getStyleClass().add("field-label");
        scopeRow.getChildren().addAll(scopeLbl, scopeBar);

        // Show / hide default value row when scope changes
        // (handled in setScope)

        // ── Main form row: Label | Key | Type ──
        HBox formRow = new HBox(14);
        formRow.setAlignment(Pos.BOTTOM_LEFT);

        // Label field
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

        // Key field
        VBox keyBox = new VBox(6);
        Label klbl = new Label("KEY (PLACEHOLDER)");
        klbl.getStyleClass().add("field-label");
        HBox keyGroup = new HBox(6);
        keyGroup.setAlignment(Pos.CENTER_LEFT);
        Label bra1 = new Label("{{");
        bra1.getStyleClass().add("code-pill");
        keyInput.setPromptText("delivery_date");
        keyInput.getStyleClass().add("code-input");
        HBox.setHgrow(keyInput, Priority.ALWAYS);
        keyInput.setMaxWidth(Double.MAX_VALUE);
        Label bra2 = new Label("}}");
        bra2.getStyleClass().add("code-pill");
        keyGroup.getChildren().addAll(bra1, keyInput, bra2);
        HBox.setHgrow(keyGroup, Priority.ALWAYS);
        keyBox.getChildren().addAll(klbl, keyGroup);
        HBox.setHgrow(keyBox, Priority.ALWAYS);

        // Type field
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

        // ── Default Value row (only visible for fixed scope) ──
        defaultValueRow = new VBox(6);
        Label defLbl = new Label("DEFAULT VALUE (optional)");
        defLbl.getStyleClass().add("field-label");
        defaultValueInput.setPromptText("Pre-filled when creating a new bill (e.g. Maharashtra)");
        defaultValueInput.setMaxWidth(Double.MAX_VALUE);
        defaultValueRow.getChildren().addAll(defLbl, defaultValueInput);

        // ── Possible Values row (only visible for barcode scope) ──
        choicesRow = new VBox(6);
        Label chLbl = new Label("POSSIBLE VALUES (comma separated)");
        chLbl.getStyleClass().add("field-label");
        choicesInput.setPromptText("e.g. S, M, L, XL, XXL  — or  Item A, Item B");
        choicesInput.setMaxWidth(Double.MAX_VALUE);
        choicesRow.getChildren().addAll(chLbl, choicesInput);

        card.getChildren().addAll(head, scopeRow, formRow, defaultValueRow, choicesRow);
        getChildren().add(card);
    }

    private void setScope(String scope) {
        currentScope = scope;
        scopeFixedBtn.getStyleClass().remove("active");
        scopeTableBtn.getStyleClass().remove("active");
        scopeBarcodeBtn.getStyleClass().remove("active");
        switch (scope) {
            case "table" -> scopeTableBtn.getStyleClass().add("active");
            case "barcode" -> scopeBarcodeBtn.getStyleClass().add("active");
            default -> scopeFixedBtn.getStyleClass().add("active");
        }
        boolean fixed = "fixed".equals(scope);
        boolean barcode = "barcode".equals(scope);
        if (defaultValueRow != null) { defaultValueRow.setVisible(fixed); defaultValueRow.setManaged(fixed); }
        if (choicesRow != null) { choicesRow.setVisible(barcode); choicesRow.setManaged(barcode); }
    }

    private void handleAdd() {
        String label = labelInput.getText().trim();
        String key   = keyInput.getText().trim();
        if (key.isEmpty()) key = slugify(label);
        if (label.isEmpty() || key.isEmpty()) {
            Toast.show(this, "Validation", "Please enter both a label and key.", true);
            return;
        }

        String scope = currentScope;

        try {
            VariableDef v = new VariableDef();
            v.setKey(key);
            v.setLabel(label);
            v.setType(typeSelect.getValue() != null ? typeSelect.getValue() : "text");
            v.setBuiltin(false);
            v.setScope(scope);
            v.setDefaultValue("fixed".equals(scope) ? defaultValueInput.getText().trim() : "");
            v.setChoices("barcode".equals(scope) ? choicesInput.getText().trim() : "");
            app.getData().variables().saveVariable(v);

            String scopeDesc = switch (scope) {
                case "table" -> "table column";
                case "barcode" -> "barcode label variable";
                default -> "bill field";
            };
            Toast.show(this, "Variable Added",
                    "Variable {{" + key + "}} created as " + scopeDesc + ".", false);
            labelInput.clear();
            keyInput.clear();
            defaultValueInput.clear();
            choicesInput.clear();
            reload();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
            Toast.show(this, "Error", "Failed to add variable: " + e.getMessage(), true);
        }
    }

    // ─── Built-in Section ──────────────────────────────────────────────────────

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

        int col = 0, row = 0;
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
            if (col >= 3) { col = 0; row++; }
        }

        HBox footerBox = new HBox(8);
        footerBox.setAlignment(Pos.CENTER_LEFT);
        footerBox.setPadding(new Insets(4, 4, 0, 4));
        Label footer = new Label("Plus automatic computed totals: {{subtotal}} {{cgst}} {{sgst}} {{igst}} {{grand_total}} {{amount_in_words}} {{balance_due}} {{paid_amount}} {{amount_paid_words}}...");
        footer.getStyleClass().add("kpi-subtext");
        footerBox.getChildren().add(footer);

        card.getChildren().addAll(head, grid, footerBox);
        getChildren().add(card);
    }

    // ─── Buyer Fields Section ─────────────────────────────────────────────────

    private void buildBuyerFieldsSection() {
        buyerFieldsContainer.setSpacing(14);
        getChildren().add(buyerFieldsContainer);
    }

    // ─── Custom List Section ──────────────────────────────────────────────────

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

    // ─── Reload ───────────────────────────────────────────────────────────────

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
            grid.setHgap(14); grid.setVgap(8);
            for (int i = 0; i < 3; i++) {
                ColumnConstraints cc = new ColumnConstraints();
                cc.setPercentWidth(33.333);
                grid.getColumnConstraints().add(cc);
            }

            int col = 0, row = 0;
            for (BuyerFieldDef bf : fields) {
                HBox item = new HBox(10);
                item.setAlignment(Pos.CENTER_LEFT);
                item.setPadding(new Insets(7, 12, 7, 12));
                item.getStyleClass().add("card-pane-subtle");

                HBox left = new HBox(6);
                left.setAlignment(Pos.CENTER_LEFT);
                Label lbl      = new Label(bf.getLabel());
                Label typeBadge = UiTheme.pill(bf.getType() != null ? bf.getType().toUpperCase() : "TEXT");
                left.getChildren().addAll(lbl, typeBadge);
                left.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(left, Priority.ALWAYS);

                Label keyPill = UiTheme.codePill("{{buyer_" + bf.getKey() + "}}");
                item.getChildren().addAll(left, keyPill);
                grid.add(item, col, row);

                col++;
                if (col >= 3) { col = 0; row++; }
            }

            card.getChildren().addAll(head, grid);
            buyerFieldsContainer.getChildren().add(card);
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
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
            hLabel.setMinWidth(180); hLabel.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(hLabel, Priority.ALWAYS);

            Label hKey = new Label("PLACEHOLDER KEY");
            hKey.getStyleClass().add("table-th");
            hKey.setPrefWidth(220); hKey.setMinWidth(220); hKey.setMaxWidth(220);

            Label hScope = new Label("SCOPE");
            hScope.getStyleClass().add("table-th");
            hScope.setPrefWidth(110); hScope.setMinWidth(110); hScope.setMaxWidth(110);

            Label hType = new Label("DATA TYPE");
            hType.getStyleClass().add("table-th");
            hType.setPrefWidth(100); hType.setMinWidth(100); hType.setMaxWidth(100);

            Label hAct = new Label("ACTION");
            hAct.getStyleClass().add("table-th");
            hAct.setPrefWidth(80); hAct.setMinWidth(80); hAct.setMaxWidth(80);
            hAct.setAlignment(Pos.CENTER_RIGHT);

            th.getChildren().addAll(hLabel, hKey, hScope, hType, hAct);
            customVarsContainer.getChildren().add(th);

            for (VariableDef vd : list) {
                HBox rowBox = new HBox(16);
                rowBox.setAlignment(Pos.CENTER_LEFT);
                rowBox.getStyleClass().add("table-data-row");
                /* padding via .table-data-row CSS */

                // Column 1: Label
                Label lbl = new Label(vd.getLabel());
                lbl.getStyleClass().add("table-cell-title");
                lbl.setMinWidth(180); lbl.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(lbl, Priority.ALWAYS);

                // Column 2: Key
                HBox keyCol = new HBox();
                keyCol.setAlignment(Pos.CENTER_LEFT);
                keyCol.setPrefWidth(220); keyCol.setMinWidth(220); keyCol.setMaxWidth(220);
                keyCol.getChildren().add(UiTheme.codePill("{{" + vd.getKey() + "}}"));

                // Column 3: Scope badge
                HBox scopeCol = new HBox();
                scopeCol.setAlignment(Pos.CENTER_LEFT);
                scopeCol.setPrefWidth(110); scopeCol.setMinWidth(110); scopeCol.setMaxWidth(110);
                String scope = vd.getScope() != null ? vd.getScope() : "fixed";
                Label scopeBadge = UiTheme.pill(switch (scope) {
                    case "table" -> "TABLE";
                    case "barcode" -> "BARCODE";
                    default -> "FIXED";
                });
                // Subtle colour hint: barcode = green, table = blue, fixed = default gold
                scopeBadge.getStyleClass().add(switch (scope) {
                    case "barcode" -> "pill-success";
                    case "table" -> "pill-info";
                    default -> "pill-accent";
                });
                scopeCol.getChildren().add(scopeBadge);

                // Column 4: Type
                HBox typeCol = new HBox();
                typeCol.setAlignment(Pos.CENTER_LEFT);
                typeCol.setPrefWidth(100); typeCol.setMinWidth(100); typeCol.setMaxWidth(100);
                typeCol.getChildren().add(UiTheme.pill(vd.getType() != null ? vd.getType().toUpperCase() : "TEXT"));

                // Column 5: Delete action
                HBox actCol = new HBox();
                actCol.setAlignment(Pos.CENTER_RIGHT);
                actCol.setPrefWidth(80); actCol.setMinWidth(80); actCol.setMaxWidth(80);

                Button del = new Button("🗑");
                del.getStyleClass().add("button-icon-subtle");
                del.setTooltip(new Tooltip("Delete variable"));
                del.setOnAction(e -> {
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Delete variable {{" + vd.getKey() + "}}?", ButtonType.YES, ButtonType.NO);
                    confirm.setTitle("Delete Variable");
                    confirm.setHeaderText("Delete Variable: " + vd.getLabel());
                    confirm.setContentText("Are you sure you want to delete placeholder {{" + vd.getKey() + "}}?");
                    DialogHelper.styleDialog(confirm, 420, 200);
                    confirm.showAndWait().ifPresent(res -> {
                        if (res == ButtonType.YES) {
                            try {
                                app.getData().variables().deleteVariable(vd.getKey());
                                Toast.show(this, "Variable Deleted",
                                        "Variable {{" + vd.getKey() + "}} was removed.", false);
                                reload();
                            } catch (Exception ex) {
                                Toast.show(this, "Delete Failed", ex.getMessage(), true);
                            }
                        }
                    });
                });
                actCol.getChildren().add(del);

                rowBox.getChildren().addAll(lbl, keyCol, scopeCol, typeCol, actCol);
                customVarsContainer.getChildren().add(rowBox);
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    // ─── Utility ─────────────────────────────────────────────────────────────

    private static String slugify(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
