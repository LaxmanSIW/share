package com.invoicestudio.ui.views;

import com.invoicestudio.model.Supplier;
import com.invoicestudio.ui.DialogHelper;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.Toast;
import com.invoicestudio.ui.UiTheme;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Seller / Supplier Directory (Sundry Creditors) — v4.3.
 *
 * Purchase-side counterpart of {@link BuyersView}, built with the same
 * "Obsidian & Gold" design system: UiTheme KPI cards, search toolbar,
 * table with row actions, and a DialogHelper-styled form dialog.
 *
 * Balance semantics: positive opening balance = payable to the seller (Cr);
 * negative = advance paid to the seller (Dr). Purchase bills will extend
 * this running balance in a later phase.
 */
public class SuppliersView extends BorderPane {

    private final StudioApp app;

    private final TableView<Supplier> table = new TableView<>();
    private FilteredList<Supplier> filteredSuppliers;
    private final TextField searchField = new TextField();
    private final Label resultCountLbl = new Label("0 sellers");

    // KPI summary values
    private final Label statTotalSellers = UiTheme.kpiValue("0");
    private final Label statTotalPayable = UiTheme.kpiValue("₹0.00");
    private final Label statGstRegistered = UiTheme.kpiValue("0");

    private List<Supplier> allSuppliers = List.of();

    public SuppliersView(StudioApp app) {
        this.app = app;

        setPadding(new Insets(24));
        getStyleClass().add("bg-app");

        setTop(createTopBar());
        setCenter(createTableArea());

        refresh();
    }

    /** Re-reads suppliers from the DB and rebuilds KPIs + filter. Called on every view show. */
    public void refresh() {
        allSuppliers = app.getData().suppliers().getAllSuppliers();
        filteredSuppliers = new FilteredList<>(FXCollections.observableArrayList(allSuppliers), s -> true);
        table.setItems(filteredSuppliers);
        applyFilter();
        updateSummaryStats();
    }

    private Node createTopBar() {
        VBox box = new VBox(16);
        box.setPadding(new Insets(0, 0, 16, 0));

        // 1. Title + primary actions
        HBox bar1 = new HBox(16);
        bar1.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label title = new Label("Sellers / Suppliers Directory");
        title.getStyleClass().add("heading-l");
        Label subtitle = new Label("Manage sundry creditors, GST & banking details, credit periods and payables.");
        subtitle.getStyleClass().add("view-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button exportCsvBtn = UiTheme.smallBtn("Export CSV");
        exportCsvBtn.setTooltip(new Tooltip("Export seller directory to CSV"));
        exportCsvBtn.setOnAction(e -> exportSuppliersCsv());

        Button addBtn = UiTheme.goldBtn("+ New Seller");
        addBtn.setTooltip(new Tooltip("Create a new seller / supplier profile"));
        addBtn.setOnAction(e -> showSupplierDialog(null));

        bar1.getChildren().addAll(titleBox, sp, exportCsvBtn, addBtn);

        // 2. KPI summary banner
        HBox statsGrid = new HBox(16);
        statsGrid.getChildren().addAll(
                UiTheme.kpiCard("TOTAL SELLERS", statTotalSellers, "Active creditor profiles", "accent-gold"),
                UiTheme.kpiCard("TOTAL PAYABLE", statTotalPayable, "Outstanding balance due to sellers", "accent-red"),
                UiTheme.kpiCard("GST REGISTERED", statGstRegistered, "Profiles with valid GSTIN", "accent-emerald")
        );

        // 3. Search / filter bar
        HBox filterRow = new HBox(12);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        searchField.setPromptText("Search by Firm Name, Phone, GSTIN or City...");
        searchField.setPrefWidth(420);
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, o, v) -> applyFilter());

        Button clearSearchBtn = UiTheme.smallBtn("✕");
        clearSearchBtn.setTooltip(new Tooltip("Clear search filter"));
        clearSearchBtn.setOnAction(e -> searchField.clear());

        Region filterSp = new Region();
        HBox.setHgrow(filterSp, Priority.ALWAYS);

        resultCountLbl.getStyleClass().add("result-count");

        filterRow.getChildren().addAll(searchField, clearSearchBtn, filterSp, resultCountLbl);

        box.getChildren().addAll(bar1, statsGrid, filterRow);
        return box;
    }

    private void updateSummaryStats() {
        statTotalSellers.setText(String.valueOf(allSuppliers.size()));

        long gstCount = allSuppliers.stream()
                .filter(s -> s.getGst() != null && s.getGst().trim().length() == 15)
                .count();
        statGstRegistered.setText(gstCount + " of " + allSuppliers.size());

        String cur = app.getData().getSettings().getCurrency();
        // Payable = sum of positive (Cr) opening balances. Negative (Dr) are advances, not payables.
        double totalPayable = allSuppliers.stream()
                .mapToDouble(Supplier::getOpeningBalance)
                .filter(v -> v > 0)
                .sum();
        statTotalPayable.setText(String.format("%s%.2f", cur, totalPayable));
    }

    private Node createTableArea() {
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        VBox emptyBox = UiTheme.emptyState("🏭", "No Sellers Found",
                "Click '+ New Seller' or adjust your search filter to find records.");
        table.setPlaceholder(emptyBox);

        buildTableColumns();
        return table;
    }

    private void buildTableColumns() {
        table.getColumns().clear();

        // 1. Seller / Firm name (avatar + bold) with city subtitle
        TableColumn<Supplier, Supplier> colName = new TableColumn<>("Seller / Firm Name");
        colName.setPrefWidth(220);
        colName.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colName.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Supplier s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    HBox box = new HBox(10);
                    box.setAlignment(Pos.CENTER_LEFT);

                    String initial = s.getName() != null && !s.getName().isBlank()
                            ? s.getName().substring(0, 1).toUpperCase() : "S";
                    Label avatar = new Label(initial);
                    avatar.getStyleClass().add("avatar-circle");

                    VBox textBox = new VBox(2);
                    Label nameLbl = new Label(s.getName());
                    nameLbl.getStyleClass().add("table-cell-title");

                    String subInfo = !s.getContactPerson().isBlank() ? "Contact: " + s.getContactPerson()
                            : !s.getCity().isBlank() ? s.getCity()
                            : !s.getAddress().isBlank() ? (s.getAddress().length() > 32 ? s.getAddress().substring(0, 30) + "…" : s.getAddress())
                            : "No contact details";
                    Label subLbl = new Label(subInfo);
                    subLbl.getStyleClass().add("kpi-subtext");

                    textBox.getChildren().addAll(nameLbl, subLbl);
                    box.getChildren().addAll(avatar, textBox);
                    setGraphic(box);
                }
            }
        });

        // 2. Contact person & phone
        TableColumn<Supplier, String> colContact = new TableColumn<>("Contact & Phone");
        colContact.setPrefWidth(170);
        colContact.setCellValueFactory(d -> {
            Supplier s = d.getValue();
            String person = s.getContactPerson() != null ? s.getContactPerson() : "";
            String phone = s.getPhone() != null ? s.getPhone() : "";
            String val = !person.isBlank() && !phone.isBlank() ? person + " · " + phone
                    : !person.isBlank() ? person
                    : !phone.isBlank() ? phone : "—";
            return new SimpleStringProperty(val);
        });

        // 3. GSTIN badge (monospace)
        TableColumn<Supplier, String> colGst = new TableColumn<>("GSTIN");
        colGst.setPrefWidth(155);
        colGst.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getGst()));
        colGst.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String gst, boolean empty) {
                super.updateItem(gst, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setGraphic(null);
                    setText(null);
                } else if (gst == null || gst.isBlank()) {
                    setGraphic(UiTheme.pill("Unregistered"));
                } else {
                    Label badge = new Label(gst);
                    badge.getStyleClass().add("gstin-badge");
                    setGraphic(badge);
                }
            }
        });

        // 4. State / Place of supply
        TableColumn<Supplier, String> colState = new TableColumn<>("State / Place of Supply");
        colState.setPrefWidth(170);
        colState.setCellValueFactory(d -> {
            Supplier s = d.getValue();
            String st = s.getState() != null ? s.getState() : "";
            String code = s.getEffectiveStateCode();
            if (!st.isEmpty() && !code.isEmpty()) return new SimpleStringProperty(st + " (" + code + ")");
            if (!code.isEmpty()) return new SimpleStringProperty("Code: " + code);
            return new SimpleStringProperty(!st.isEmpty() ? st : "—");
        });

        // 5. Credit period
        TableColumn<Supplier, String> colCredit = new TableColumn<>("Credit Period");
        colCredit.setPrefWidth(110);
        colCredit.setCellValueFactory(d -> {
            int days = d.getValue().getCreditPeriodDays();
            return new SimpleStringProperty(days > 0 ? days + " Days" : "—");
        });

        // 6. Balance / payable (red pill when payable, settled when zero/negative)
        TableColumn<Supplier, Double> colBalance = new TableColumn<>("Balance / Payable");
        colBalance.setPrefWidth(150);
        colBalance.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue().getOpeningBalance()));
        colBalance.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double bal, boolean empty) {
                super.updateItem(bal, empty);
                if (empty || bal == null) {
                    setGraphic(null);
                    setText(null);
                } else if (bal > 0.001) {
                    setGraphic(UiTheme.statusPill(
                            String.format("%s%.2f Due", app.getData().getSettings().getCurrency(), bal), "danger"));
                } else if (bal < -0.001) {
                    setGraphic(UiTheme.statusPill(
                            String.format("%s%.2f Advance", app.getData().getSettings().getCurrency(), -bal), "success"));
                } else {
                    setGraphic(UiTheme.statusPill("✓ Settled", "success"));
                }
            }
        });

        // 7. Row actions: Edit / Details / Delete
        TableColumn<Supplier, Void> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(220);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button viewBtn = new Button("Details");
            private final Button delBtn = new Button("✕");
            private final HBox box = new HBox(6, editBtn, viewBtn, delBtn);

            {
                editBtn.getStyleClass().addAll("button-sm", "button-secondary");
                editBtn.setTooltip(new Tooltip("Edit seller details"));
                editBtn.setOnAction(e -> {
                    Supplier s = getTableRow().getItem();
                    if (s != null) showSupplierDialog(s);
                });

                viewBtn.getStyleClass().addAll("button-sm", "button-secondary");
                viewBtn.setTooltip(new Tooltip("View full seller profile incl. banking details"));
                viewBtn.setOnAction(e -> {
                    Supplier s = getTableRow().getItem();
                    if (s != null) showDetailsDialog(s);
                });

                delBtn.getStyleClass().addAll("button-sm", "button-danger");
                delBtn.setTooltip(new Tooltip("Delete seller profile"));
                delBtn.setOnAction(e -> {
                    Supplier s = getTableRow().getItem();
                    if (s != null) {
                        confirmAndDelete(s);
                    }
                });
                box.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow().getItem() == null) setGraphic(null);
                else setGraphic(box);
            }
        });

        table.getColumns().addAll(colName, colContact, colGst, colState, colCredit, colBalance, colActions);
    }

    private void applyFilter() {
        if (filteredSuppliers == null) return;
        String q = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        filteredSuppliers.setPredicate(s -> {
            if (q.isEmpty()) return true;
            return (s.getName() != null && s.getName().toLowerCase().contains(q))
                    || (s.getPhone() != null && s.getPhone().toLowerCase().contains(q))
                    || (s.getGst() != null && s.getGst().toLowerCase().contains(q))
                    || (s.getCity() != null && s.getCity().toLowerCase().contains(q))
                    || (s.getState() != null && s.getState().toLowerCase().contains(q))
                    || (s.getContactPerson() != null && s.getContactPerson().toLowerCase().contains(q));
        });

        resultCountLbl.setText("Showing " + filteredSuppliers.size() + " of " + allSuppliers.size() + " sellers");
    }

    // ------------------------------------------------------------------
    // Seller form dialog (Basic / Tax & Location / Financial & Banking)
    // ------------------------------------------------------------------

    private void showSupplierDialog(Supplier existing) {
        Dialog<Supplier> dlg = new Dialog<>();
        dlg.setTitle(existing != null ? "Edit Seller" : "Add New Seller");
        dlg.setHeaderText(existing != null ? "Update details for " + existing.getName()
                : "Create a new seller (Sundry Creditor) in directory");

        GridPane g = new GridPane();
        g.setHgap(10);
        g.setVgap(10);
        g.setPadding(new Insets(16));

        ColumnConstraints col0 = new ColumnConstraints();
        col0.setPrefWidth(150);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPrefWidth(300);
        col1.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(col0, col1);

        // --- Section 1: Basic Information ---
        Label basicHeader = new Label("Basic Information");
        basicHeader.getStyleClass().add("section-eyebrow");
        g.add(basicHeader, 0, 0, 2, 1);

        TextField nameF = new TextField(existing != null ? existing.getName() : "");
        nameF.setPromptText("Firm / Business Name");
        g.add(new Label("Firm Name:*"), 0, 1);
        g.add(nameF, 1, 1);

        TextField contactF = new TextField(existing != null ? existing.getContactPerson() : "");
        contactF.setPromptText("Contact person name");
        g.add(new Label("Contact Person:"), 0, 2);
        g.add(contactF, 1, 2);

        TextField phoneF = new TextField(existing != null ? existing.getPhone() : "");
        phoneF.setPromptText("Mobile / Phone number");
        g.add(new Label("Phone:"), 0, 3);
        g.add(phoneF, 1, 3);

        TextField emailF = new TextField(existing != null ? existing.getEmail() : "");
        emailF.setPromptText("name@example.com");
        g.add(new Label("Email:"), 0, 4);
        g.add(emailF, 1, 4);

        // --- Section 2: Tax & Location ---
        Label taxHeader = new Label("Tax & Location");
        taxHeader.getStyleClass().add("section-eyebrow");
        g.add(taxHeader, 0, 5, 2, 1);

        TextField gstF = new TextField(existing != null ? existing.getGst() : "");
        gstF.setPromptText("15-digit GSTIN (e.g. 27AAPFU0939F1ZV)");

        TextField stateCodeF = new TextField(existing != null ? existing.getStateCode() : "");
        stateCodeF.setPrefWidth(100);

        // GSTIN normalization: uppercase, strip non-alphanumerics, max 15 chars,
        // with instant state-code auto-detection from the first two digits.
        gstF.textProperty().addListener((obs, o, v) -> {
            if (v == null) return;
            String normalized = v.toUpperCase().replaceAll("[^A-Z0-9]", "");
            if (normalized.length() > 15) normalized = normalized.substring(0, 15);
            if (!normalized.equals(v)) {
                gstF.setText(normalized); // re-triggers this listener with the clean value
                return;
            }
            if (normalized.length() >= 2 && stateCodeF.getText().isBlank()) {
                String code = normalized.substring(0, 2);
                if (code.matches("\\d{2}")) {
                    stateCodeF.setText(code);
                }
            }
        });
        g.add(new Label("GSTIN:"), 0, 6);
        g.add(gstF, 1, 6);

        HBox stateCodeBox = new HBox(8);
        stateCodeBox.setAlignment(Pos.CENTER_LEFT);
        Label scHint = new Label("(2-digit GST state code)");
        scHint.getStyleClass().add("kpi-subtext");
        stateCodeBox.getChildren().addAll(stateCodeF, scHint);
        g.add(new Label("State Code:"), 0, 7);
        g.add(stateCodeBox, 1, 7);

        TextField stateF = new TextField(existing != null ? existing.getState() : "");
        stateF.setPromptText("e.g. Maharashtra (Place of Supply)");
        g.add(new Label("State:"), 0, 8);
        g.add(stateF, 1, 8);

        TextField cityF = new TextField(existing != null ? existing.getCity() : "");
        cityF.setPromptText("City / Town");
        g.add(new Label("City:"), 0, 9);
        g.add(cityF, 1, 9);

        TextArea addrF = new TextArea(existing != null ? existing.getAddress() : "");
        addrF.setPrefRowCount(2);
        addrF.setPromptText("Full address (street, area, pincode)");
        g.add(new Label("Address:"), 0, 10);
        g.add(addrF, 1, 10);

        // --- Section 3: Financial & Banking ---
        Label finHeader = new Label("Financial & Banking");
        finHeader.getStyleClass().add("section-eyebrow");
        g.add(finHeader, 0, 11, 2, 1);

        TextField openingBalF = new TextField(
                existing != null && existing.getOpeningBalance() != 0 ? String.valueOf(existing.getOpeningBalance()) : "");
        openingBalF.setPromptText("0.00 (+ = payable to seller, − = advance paid)");
        g.add(new Label("Opening Balance (₹):"), 0, 12);
        g.add(openingBalF, 1, 12);

        TextField creditDaysF = new TextField(
                existing != null && existing.getCreditPeriodDays() > 0 ? String.valueOf(existing.getCreditPeriodDays()) : "");
        creditDaysF.setPromptText("e.g. 30, 45, 60");
        g.add(new Label("Credit Period (Days):"), 0, 13);
        g.add(creditDaysF, 1, 13);

        TextField bankNameF = new TextField(existing != null ? existing.getBankName() : "");
        bankNameF.setPromptText("Bank name & branch");
        g.add(new Label("Bank Name:"), 0, 14);
        g.add(bankNameF, 1, 14);

        TextField bankAccF = new TextField(existing != null ? existing.getBankAccountNo() : "");
        bankAccF.setPromptText("Account number (for NEFT / RTGS)");
        g.add(new Label("Account No:"), 0, 15);
        g.add(bankAccF, 1, 15);

        TextField ifscF = new TextField(existing != null ? existing.getBankIfsc() : "");
        ifscF.setPromptText("e.g. SBIN0001234");
        g.add(new Label("IFSC Code:"), 0, 16);
        g.add(ifscF, 1, 16);

        dlg.getDialogPane().setContent(g);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogHelper.styleDialog(dlg);

        dlg.setResultConverter(btn -> {
            if (btn != ButtonType.OK) return null;

            // --- Validation (accountant-grade) ---
            String name = nameF.getText() == null ? "" : nameF.getText().trim();
            if (name.isEmpty()) {
                Alert err = new Alert(Alert.AlertType.WARNING,
                        "Firm Name is required.", ButtonType.OK);
                err.setHeaderText("Validation Error");
                DialogHelper.styleDialog(err);
                err.showAndWait();
                return null;
            }

            String gst = gstF.getText() == null ? "" : gstF.getText().trim().toUpperCase();
            if (!gst.isEmpty() && gst.length() != 15) {
                Alert err = new Alert(Alert.AlertType.WARNING,
                        "GSTIN must be exactly 15 characters (got " + gst.length() + ").", ButtonType.OK);
                err.setHeaderText("Validation Error");
                DialogHelper.styleDialog(err);
                err.showAndWait();
                return null;
            }

            String phone = phoneF.getText() == null ? "" : phoneF.getText().trim();
            if (!phone.isEmpty() && !phone.replaceAll("[\\s\\-()+]", "").matches("\\d{7,15}")) {
                Alert err = new Alert(Alert.AlertType.WARNING,
                        "Phone number must be 7–15 digits.", ButtonType.OK);
                err.setHeaderText("Validation Error");
                DialogHelper.styleDialog(err);
                err.showAndWait();
                return null;
            }

            String ifsc = ifscF.getText() == null ? "" : ifscF.getText().trim().toUpperCase();
            if (!ifsc.isEmpty() && !ifsc.matches("[A-Z]{4}0[A-Z0-9]{6}")) {
                Alert err = new Alert(Alert.AlertType.WARNING,
                        "IFSC format: 4 letters, '0', then 6 alphanumeric (e.g. SBIN0001234).", ButtonType.OK);
                err.setHeaderText("Validation Error");
                DialogHelper.styleDialog(err);
                err.showAndWait();
                return null;
            }

            double openingBal = 0;
            try {
                String obStr = openingBalF.getText() == null ? "" : openingBalF.getText().trim();
                if (!obStr.isEmpty()) openingBal = Double.parseDouble(obStr);
                if (openingBal < 0 && openingBal > -0.005) openingBal = 0;
            } catch (NumberFormatException ex) {
                Alert err = new Alert(Alert.AlertType.WARNING,
                        "Opening Balance must be a number (use − for advance paid).", ButtonType.OK);
                err.setHeaderText("Validation Error");
                DialogHelper.styleDialog(err);
                err.showAndWait();
                return null;
            }

            int creditDays = 0;
            try {
                String cdStr = creditDaysF.getText() == null ? "" : creditDaysF.getText().trim();
                if (!cdStr.isEmpty()) creditDays = Integer.parseInt(cdStr);
                if (creditDays < 0) creditDays = 0;
            } catch (NumberFormatException ex) {
                Alert err = new Alert(Alert.AlertType.WARNING,
                        "Credit Period must be a whole number of days.", ButtonType.OK);
                err.setHeaderText("Validation Error");
                DialogHelper.styleDialog(err);
                err.showAndWait();
                return null;
            }

            // Duplicate-name confirmation (same convention as CSV import for buyers)
            Supplier dup = app.getData().suppliers().findByName(name);
            if (dup != null && (existing == null || !dup.getId().equals(existing.getId()))) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "A seller named \"" + name + "\" already exists. Save anyway as a separate profile?",
                        ButtonType.YES, ButtonType.NO);
                confirm.setHeaderText("Possible Duplicate");
                DialogHelper.styleDialog(confirm);
                if (confirm.showAndWait().filter(ButtonType.YES::equals).orElse(null) == null) {
                    return null;
                }
            }

            String id = existing != null ? existing.getId()
                    : "sup_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            Supplier s = new Supplier(id, name);
            s.setContactPerson(contactF.getText() == null ? "" : contactF.getText().trim());
            s.setPhone(phone);
            s.setEmail(emailF.getText() == null ? "" : emailF.getText().trim());
            s.setGst(gst);
            s.setState(stateF.getText() == null ? "" : stateF.getText().trim());
            s.setStateCode(stateCodeF.getText() == null ? "" : stateCodeF.getText().trim());
            s.setCity(cityF.getText() == null ? "" : cityF.getText().trim());
            s.setAddress(addrF.getText() == null ? "" : addrF.getText().trim());
            s.setPan(s.getEffectivePan());
            s.setOpeningBalance(openingBal);
            s.setCreditPeriodDays(creditDays);
            s.setBankName(bankNameF.getText() == null ? "" : bankNameF.getText().trim());
            s.setBankAccountNo(bankAccF.getText() == null ? "" : bankAccF.getText().trim());
            s.setBankIfsc(ifsc);
            if (existing != null) {
                s.setCustom(existing.getCustom());
                s.setCreatedAt(existing.getCreatedAt());
            }
            return s;
        });

        dlg.showAndWait().ifPresent(s -> {
            app.getData().suppliers().saveSupplier(s);
            refresh();
            Toast.show(app.getRootPane(), "Seller Saved", s.getName() + " saved.", false);
        });
    }

    // ------------------------------------------------------------------
    // Details dialog (view-only profile incl. banking)
    // ------------------------------------------------------------------

    private void showDetailsDialog(Supplier s) {
        if (s == null) return;
        String cur = app.getData().getSettings().getCurrency();

        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Seller Details — " + s.getName());
        dlg.setHeaderText("Profile, tax & banking details");

        GridPane g = new GridPane();
        g.setHgap(12);
        g.setVgap(8);
        g.setPadding(new Insets(16));
        g.setPrefWidth(520);

        int r = 0;
        g.add(new Label("Firm Name:"), 0, r);
        g.add(new Label(s.getName()), 1, r++);
        if (!s.getContactPerson().isBlank()) {
            g.add(new Label("Contact Person:"), 0, r);
            g.add(new Label(s.getContactPerson()), 1, r++);
        }
        if (!s.getPhone().isBlank()) {
            g.add(new Label("Phone:"), 0, r);
            g.add(new Label(s.getPhone()), 1, r++);
        }
        if (!s.getEmail().isBlank()) {
            g.add(new Label("Email:"), 0, r);
            g.add(new Label(s.getEmail()), 1, r++);
        }
        if (!s.getGst().isBlank()) {
            g.add(new Label("GSTIN:"), 0, r);
            Label gstLbl = new Label(s.getGst());
            gstLbl.getStyleClass().add("gstin-badge");
            g.add(gstLbl, 1, r++);
        }
        if (!s.getEffectivePan().isBlank()) {
            g.add(new Label("PAN:"), 0, r);
            g.add(new Label(s.getEffectivePan()), 1, r++);
        }
        if (!s.getState().isBlank() || !s.getCity().isBlank()) {
            g.add(new Label("Location:"), 0, r);
            g.add(new Label((!s.getCity().isBlank() ? s.getCity() + ", " : "") + s.getState()
                    + (!s.getEffectiveStateCode().isBlank() ? " (" + s.getEffectiveStateCode() + ")" : "")), 1, r++);
        }
        if (!s.getAddress().isBlank()) {
            g.add(new Label("Address:"), 0, r);
            g.add(new Label(s.getAddress()), 1, r++);
        }
        g.add(new Label("Opening Balance:"), 0, r);
        Label balLbl = new Label(s.getOpeningBalance() > 0
                ? String.format("%s%.2f payable (Cr)", cur, s.getOpeningBalance())
                : s.getOpeningBalance() < 0
                ? String.format("%s%.2f advance (Dr)", cur, -s.getOpeningBalance())
                : "Settled");
        balLbl.getStyleClass().add(s.getOpeningBalance() > 0 ? "accent-red" : "accent-emerald");
        g.add(balLbl, 1, r++);
        if (s.getCreditPeriodDays() > 0) {
            g.add(new Label("Credit Period:"), 0, r);
            g.add(new Label(s.getCreditPeriodDays() + " Days"), 1, r++);
        }
        if (!s.getBankName().isBlank()) {
            g.add(new Label("Bank:"), 0, r);
            g.add(new Label(s.getBankName()), 1, r++);
        }
        if (!s.getBankAccountNo().isBlank()) {
            g.add(new Label("Account No:"), 0, r);
            g.add(new Label(s.getBankAccountNo()), 1, r++);
        }
        if (!s.getBankIfsc().isBlank()) {
            g.add(new Label("IFSC:"), 0, r);
            g.add(new Label(s.getBankIfsc()), 1, r++);
        }

        dlg.getDialogPane().setContent(g);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        DialogHelper.styleDialog(dlg, 560, 420);
        dlg.showAndWait();
    }

    // ------------------------------------------------------------------
    // Delete confirmation
    // ------------------------------------------------------------------

    private void confirmAndDelete(Supplier s) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete seller \"" + s.getName() + "\"? This cannot be undone.",
                ButtonType.YES, ButtonType.CANCEL);
        confirm.setHeaderText("Delete Seller");
        DialogHelper.styleDialog(confirm);
        Optional<ButtonType> res = confirm.showAndWait();
        if (res.isEmpty() || res.get() != ButtonType.YES) return;

        app.getData().suppliers().deleteSupplier(s.getId());
        refresh();
        Toast.show(app.getRootPane(), "Seller Deleted", s.getName() + " removed.", false);
    }

    // ------------------------------------------------------------------
    // CSV export
    // ------------------------------------------------------------------

    private void exportSuppliersCsv() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Sellers CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Spreadsheet (*.csv)", "*.csv"));
        fc.setInitialFileName("sellers-export-" + LocalDate.now() + ".csv");
        File dest = fc.showSaveDialog(app.getPrimaryStage());
        if (dest == null) return;
        try (FileWriter fw = new FileWriter(dest)) {
            List<Supplier> rows = filteredSuppliers != null ? new ArrayList<>(filteredSuppliers) : allSuppliers;
            fw.write("Firm Name,Contact Person,Phone,Email,GSTIN,PAN,State,State Code,City,Address,");
            fw.write("Opening Balance,Credit Period Days,Bank Name,Account No,IFSC\n");
            StringBuilder sb = new StringBuilder();
            for (Supplier s : rows) {
                sb.setLength(0);
                sb.append(csv(s.getName())).append(',');
                sb.append(csv(s.getContactPerson())).append(',');
                sb.append(csv(s.getPhone())).append(',');
                sb.append(csv(s.getEmail())).append(',');
                sb.append(csv(s.getGst())).append(',');
                sb.append(csv(s.getEffectivePan())).append(',');
                sb.append(csv(s.getState())).append(',');
                sb.append(csv(s.getStateCode())).append(',');
                sb.append(csv(s.getCity())).append(',');
                sb.append(csv(s.getAddress())).append(',');
                sb.append(s.getOpeningBalance()).append(',');
                sb.append(s.getCreditPeriodDays()).append(',');
                sb.append(csv(s.getBankName())).append(',');
                sb.append(csv(s.getBankAccountNo())).append(',');
                sb.append(csv(s.getBankIfsc())).append('\n');
                fw.write(sb.toString());
            }
            Toast.show(app.getRootPane(), "Export Successful", "Saved " + rows.size() + " sellers.", false);
        } catch (Exception ex) {
            Toast.show(app.getRootPane(), "Export Failed", ex.getMessage(), true);
        }
    }

    private static String csv(String v) {
        String s = v == null ? "" : v;
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
