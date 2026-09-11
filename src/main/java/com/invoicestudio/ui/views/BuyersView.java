package com.invoicestudio.ui.views;

import com.invoicestudio.model.Bill;
import com.invoicestudio.model.BillPayment;
import com.invoicestudio.model.BillStatus;
import com.invoicestudio.model.Buyer;
import com.invoicestudio.model.BuyerFieldDef;
import com.invoicestudio.model.Settings;
import com.invoicestudio.service.CsvService;
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
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.*;

/**
 * Buyer & Customer Directory — v3 redesign.
 *
 * PERFORMANCE FIX (this was the app's worst bottleneck):
 * The old build computed "bills of this buyer" with a full {@code getAllBills()}
 * DB scan INSIDE every table-cell renderer — hundreds of scans per frame.
 * Now: a single buyer→bills map is built once per refresh from the DataManager
 * cache and every cell / KPI reads from it.
 */
public class BuyersView extends BorderPane {

    private final StudioApp app;

    private final TableView<Buyer> table = new TableView<>();
    private FilteredList<Buyer> filteredBuyers;
    private final TextField searchField = new TextField();
    private final Label resultCountLbl = new Label("0 customers");

    // KPI Summary values
    private final Label statTotalBuyers = UiTheme.kpiValue("0");
    private final Label statTotalDue = UiTheme.kpiValue("₹0.00");
    private final Label statGstRegistered = UiTheme.kpiValue("0");

    /** Built once per refresh: buyer name (lower-case) → their bills. */
    private Map<String, List<Bill>> billsByBuyer = Map.of();
    private List<Buyer> allBuyers = List.of();

    public BuyersView(StudioApp app) {
        this.app = app;

        setPadding(new Insets(24));
        getStyleClass().add("bg-app");

        setTop(createTopBar());
        setCenter(createTableArea());

        refresh();
    }

    public void refresh() {
        rebuildTableColumns();
        allBuyers = app.getData().buyers().getAllBuyers();

        // ONE pass over cached bills → buyer→bills map (was per-cell before!)
        List<Bill> allBills = app.getData().getAllBills();
        Map<String, List<Bill>> map = new HashMap<>();
        for (Bill bill : allBills) {
            String buyer = bill.getVariables().getOrDefault("buyer_name", "");
            if (!buyer.isBlank()) {
                map.computeIfAbsent(buyer.toLowerCase(), k -> new ArrayList<>()).add(bill);
            }
        }
        billsByBuyer = map;

        filteredBuyers = new FilteredList<>(FXCollections.observableArrayList(allBuyers), b -> true);
        table.setItems(filteredBuyers);
        applyFilter();
        updateSummaryStats(allBuyers, allBills);
    }

    private Node createTopBar() {
        VBox box = new VBox(16);
        box.setPadding(new Insets(0, 0, 16, 0));

        // 1. Title and Primary Action Buttons
        HBox bar1 = new HBox(16);
        bar1.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label title = new Label("Buyer & Customer Directory");
        title.getStyleClass().add("heading-l");
        Label subtitle = new Label("Manage customer profiles, place of supply, billing statements & custom fields.");
        subtitle.getStyleClass().add("view-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button sampleCsvBtn = UiTheme.smallBtn("Sample CSV");
        sampleCsvBtn.setTooltip(new Tooltip("Download sample CSV spreadsheet structure for buyers"));
        sampleCsvBtn.setOnAction(e -> downloadSampleCsv());

        Button importCsvBtn = UiTheme.smallBtn("Import CSV");
        importCsvBtn.setTooltip(new Tooltip("Bulk import buyers from CSV spreadsheet"));
        importCsvBtn.setOnAction(e -> showImportDialog());

        Button exportCsvBtn = UiTheme.smallBtn("Export CSV");
        exportCsvBtn.setTooltip(new Tooltip("Export customer directory including all custom fields to CSV"));
        exportCsvBtn.setOnAction(e -> exportBuyersCsv());

        Button addBtn = UiTheme.goldBtn("+ Add Buyer");
        addBtn.setTooltip(new Tooltip("Create a new customer profile"));
        addBtn.setOnAction(e -> showBuyerFormDialog(null));

        bar1.getChildren().addAll(titleBox, sp, sampleCsvBtn, importCsvBtn, exportCsvBtn, addBtn);

        // 2. KPI Summary Banner
        HBox statsGrid = new HBox(16);
        statsGrid.getChildren().addAll(
                UiTheme.kpiCard("TOTAL CUSTOMERS", statTotalBuyers, "Active directory profiles", "accent-gold"),
                UiTheme.kpiCard("OUTSTANDING DUE", statTotalDue, "Total pending receivables", "accent-red"),
                UiTheme.kpiCard("GST REGISTERED", statGstRegistered, "Profiles with verified GSTIN", "accent-emerald")
        );

        // 3. Search and Quick Filter Bar
        HBox filterRow = new HBox(12);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        searchField.setPromptText("Search by Customer Name, Phone, GSTIN, State or Custom Fields...");
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

    private void updateSummaryStats(List<Buyer> buyers, List<Bill> allBills) {
        if (buyers == null) buyers = List.of();
        statTotalBuyers.setText(String.valueOf(buyers.size()));

        long gstCount = buyers.stream().filter(b -> b.getGst() != null && !b.getGst().isBlank()).count();
        statGstRegistered.setText(gstCount + " of " + buyers.size());

        String cur = app.getData().getSettings().getCurrency();
        double totalDue = 0;
        for (Bill bill : allBills) {
            if (bill.getStatus() == BillStatus.CANCELLED) continue;
            double paid = bill.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
            if (paid == 0 && bill.getStatus() == BillStatus.PAID) continue;
            totalDue += Math.max(0, bill.getTotals().getGrandTotal() - paid);
        }
        statTotalDue.setText(String.format("%s%.2f", cur, totalDue));
    }

    private Node createTableArea() {
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        // Empty state placeholder
        VBox emptyBox = UiTheme.emptyState("👥", "No Customer Profiles Found",
                "Click '+ Add Buyer' or adjust your search filter to find records.");
        table.setPlaceholder(emptyBox);

        rebuildTableColumns();
        return table;
    }

    /**
     * Dynamically constructs table columns, ensuring all custom buyer fields configured
     * in Settings are loaded and displayed as individual columns in the list view.
     */
    private void rebuildTableColumns() {
        table.getColumns().clear();

        // 1. Name Column (with initials avatar and address subtitle)
        TableColumn<Buyer, Buyer> colName = new TableColumn<>("Customer Name");
        colName.setPrefWidth(210);
        colName.setCellValueFactory(d -> new SimpleObjectProperty<>(d.getValue()));
        colName.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Buyer b, boolean empty) {
                super.updateItem(b, empty);
                if (empty || b == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    HBox box = new HBox(10);
                    box.setAlignment(Pos.CENTER_LEFT);

                    String initial = !b.getName().isBlank() ? b.getName().substring(0, 1).toUpperCase() : "C";
                    Label avatar = new Label(initial);
                    avatar.getStyleClass().add("avatar-circle");

                    VBox textBox = new VBox(2);
                    Label nameLbl = new Label(b.getName());
                    nameLbl.getStyleClass().add("table-cell-title");

                    String sub = b.getAddress() != null && !b.getAddress().isBlank()
                            ? (b.getAddress().length() > 32 ? b.getAddress().substring(0, 30) + "…" : b.getAddress())
                            : (b.getPhone() != null && !b.getPhone().isBlank() ? b.getPhone() : "No address specified");
                    Label subLbl = new Label(sub);
                    subLbl.getStyleClass().add("kpi-subtext");

                    textBox.getChildren().addAll(nameLbl, subLbl);
                    box.getChildren().addAll(avatar, textBox);
                    setGraphic(box);
                }
            }
        });

        // 2. Phone Column
        TableColumn<Buyer, String> colPhone = new TableColumn<>("Phone");
        colPhone.setPrefWidth(120);
        colPhone.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getPhone() != null && !d.getValue().getPhone().isBlank() ? d.getValue().getPhone() : "—"));
        colPhone.setCellFactory(col -> new TableCell<>() {
            {
                getStyleClass().add("table-cell-mono");
            }
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                setText(empty || s == null ? null : s);
            }
        });

        // 3. GSTIN Column (Badge style)
        TableColumn<Buyer, String> colGst = new TableColumn<>("GSTIN");
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

        // 4. State / Place of Supply Column
        TableColumn<Buyer, String> colState = new TableColumn<>("Place of Supply");
        colState.setPrefWidth(150);
        colState.setCellValueFactory(d -> {
            String st = d.getValue().getState() != null ? d.getValue().getState() : "";
            String code = d.getValue().getEffectiveStateCode();
            if (!st.isEmpty() && !code.isEmpty()) return new SimpleStringProperty(st + " (" + code + ")");
            if (!code.isEmpty()) return new SimpleStringProperty("Code: " + code);
            return new SimpleStringProperty(!st.isEmpty() ? st : "—");
        });

        // Base columns
        table.getColumns().addAll(colName, colPhone, colGst, colState);

        // 5. Dynamic Custom Buyer Fields (Configured in Settings!)
        Settings settings = app.getData().getSettings();
        List<BuyerFieldDef> customDefs = settings != null && settings.getBuyerFields() != null ? settings.getBuyerFields() : List.of();
        for (BuyerFieldDef def : customDefs) {
            TableColumn<Buyer, String> colCust = new TableColumn<>(def.getLabel());
            colCust.setPrefWidth(130);
            colCust.setCellValueFactory(d -> {
                Buyer b = d.getValue();
                String val = null;
                if (b != null && b.getCustom() != null) {
                    val = b.getCustom().get(def.getKey());
                    if (val == null || val.isBlank()) {
                        val = b.getCustom().get(def.getLabel());
                    }
                }
                return new SimpleStringProperty(val != null && !val.isBlank() ? val : "—");
            });
            table.getColumns().add(colCust);
        }

        // 6. Total Billed Column
        TableColumn<Buyer, Double> colBilled = new TableColumn<>("Total Billed");
        colBilled.setPrefWidth(120);
        colBilled.setCellValueFactory(d -> new SimpleObjectProperty<>(getBuyerBilled(d.getValue())));
        colBilled.setCellFactory(col -> new TableCell<>() {
            {
                getStyleClass().add("table-cell-mono");
            }
            @Override
            protected void updateItem(Double amt, boolean empty) {
                super.updateItem(amt, empty);
                setText(empty || amt == null ? null : String.format("%s%.2f", app.getData().getSettings().getCurrency(), amt));
            }
        });

        // 7. Balance Due Column (with settled / due pill badges)
        TableColumn<Buyer, Double> colDue = new TableColumn<>("Balance Due");
        colDue.setPrefWidth(130);
        colDue.setCellValueFactory(d -> new SimpleObjectProperty<>(getBuyerDue(d.getValue())));
        colDue.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double due, boolean empty) {
                super.updateItem(due, empty);
                if (empty || due == null) {
                    setGraphic(null);
                    setText(null);
                } else if (due <= 0.001) {
                    setGraphic(UiTheme.statusPill("✓ Settled", "success"));
                } else {
                    setGraphic(UiTheme.statusPill(String.format("%s%.2f Due", app.getData().getSettings().getCurrency(), due), "danger"));
                }
            }
        });

        // 8. Actions Column (Edit, Statement Ledger, Delete)
        TableColumn<Buyer, Void> colActions = new TableColumn<>("Actions");
        colActions.setPrefWidth(210);
        colActions.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            private final Button stBtn = new Button("Ledger");
            private final Button delBtn = new Button("✕");
            private final HBox box = new HBox(6, editBtn, stBtn, delBtn);

            {
                editBtn.getStyleClass().addAll("button-sm", "button-secondary");
                editBtn.setTooltip(new Tooltip("Edit customer details & custom fields"));
                editBtn.setOnAction(e -> {
                    Buyer b = getTableRow().getItem();
                    if (b != null) showBuyerFormDialog(b);
                });

                stBtn.getStyleClass().addAll("button-sm", "button-secondary");
                stBtn.setTooltip(new Tooltip("View billing statement & payment ledger"));
                stBtn.setOnAction(e -> {
                    Buyer b = getTableRow().getItem();
                    if (b != null) showStatementDialog(b);
                });

                delBtn.getStyleClass().addAll("button-sm", "button-danger");
                delBtn.setTooltip(new Tooltip("Delete customer profile"));
                delBtn.setOnAction(e -> {
                    Buyer b = getTableRow().getItem();
                    if (b != null) {
                        app.getData().buyers().deleteBuyer(b.getId());
                        refresh();
                        Toast.show(app.getRootPane(), "Buyer Deleted", b.getName() + " removed.", false);
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

        table.getColumns().addAll(colBilled, colDue, colActions);
    }

    private void applyFilter() {
        if (filteredBuyers == null) return;
        String q = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        filteredBuyers.setPredicate(b -> {
            if (q.isEmpty()) return true;
            boolean matchCore = (b.getName() != null && b.getName().toLowerCase().contains(q)) ||
                    (b.getPhone() != null && b.getPhone().toLowerCase().contains(q)) ||
                    (b.getGst() != null && b.getGst().toLowerCase().contains(q)) ||
                    (b.getState() != null && b.getState().toLowerCase().contains(q)) ||
                    (b.getStateCode() != null && b.getStateCode().toLowerCase().contains(q)) ||
                    (b.getAddress() != null && b.getAddress().toLowerCase().contains(q));
            if (matchCore) return true;

            // Search across custom fields
            if (b.getCustom() != null) {
                for (String cv : b.getCustom().values()) {
                    if (cv != null && cv.toLowerCase().contains(q)) return true;
                }
            }
            return false;
        });

        int visible = filteredBuyers.size();
        int total = allBuyers.size();
        resultCountLbl.setText("Showing " + visible + " of " + total + " customers");
    }

    private List<Bill> getBuyerBills(Buyer b) {
        if (b == null || b.getName() == null) return List.of();
        return billsByBuyer.getOrDefault(b.getName().toLowerCase(), List.of());
    }

    private double getBuyerBilled(Buyer b) {
        return getBuyerBills(b).stream().mapToDouble(bi -> bi.getTotals().getGrandTotal()).sum();
    }

    private double getBuyerDue(Buyer b) {
        return getBuyerBills(b).stream().mapToDouble(bi -> {
            double paid = bi.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
            if (paid == 0 && bi.getStatus() == BillStatus.PAID) return 0;
            if (bi.getStatus() == BillStatus.CANCELLED) return 0;
            return Math.max(0, bi.getTotals().getGrandTotal() - paid);
        }).sum();
    }

    // ------------------------------------------------------------------
    // Buyer form dialog
    // ------------------------------------------------------------------

    private void showBuyerFormDialog(Buyer existing) {
        Dialog<Buyer> dlg = new Dialog<>();
        dlg.setTitle(existing != null ? "Edit Customer Profile" : "Add New Customer");
        dlg.setHeaderText(existing != null ? "Update details for " + existing.getName() : "Create a new buyer in directory");

        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(16));

        ColumnConstraints col0 = new ColumnConstraints();
        col0.setPrefWidth(140);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPrefWidth(300);
        col1.setHgrow(Priority.ALWAYS);
        g.getColumnConstraints().addAll(col0, col1);

        TextField nameF = new TextField(existing != null ? existing.getName() : "");
        nameF.setPromptText("Customer / Business Name");
        TextArea addrF = new TextArea(existing != null ? existing.getAddress() : "");
        addrF.setPrefRowCount(2);
        addrF.setPromptText("Address, City, Pincode");
        TextField gstF = new TextField(existing != null ? existing.getGst() : "");
        gstF.setPromptText("15-digit GSTIN (e.g. 27AAPFU0939F1ZV)");
        TextField phoneF = new TextField(existing != null ? existing.getPhone() : "");
        phoneF.setPromptText("Mobile / Phone number");
        TextField stateF = new TextField(existing != null ? existing.getState() : "");
        stateF.setPromptText("e.g. Maharashtra");
        TextField stateCodeF = new TextField(existing != null ? existing.getStateCode() : "");
        stateCodeF.setPromptText("e.g. 27");
        stateCodeF.setPrefWidth(100);

        // Auto-extract 2-digit state code from GSTIN if empty
        gstF.textProperty().addListener((obs, o, v) -> {
            if (v != null && v.trim().length() >= 2 && stateCodeF.getText().isBlank()) {
                String code = v.trim().substring(0, 2);
                if (code.matches("\\d{2}")) {
                    stateCodeF.setText(code);
                }
            }
        });

        HBox stateCodeBox = new HBox(8);
        stateCodeBox.setAlignment(Pos.CENTER_LEFT);
        Label scHint = new Label("(2-digit GST state code)");
        scHint.getStyleClass().add("kpi-subtext");
        stateCodeBox.getChildren().addAll(stateCodeF, scHint);

        g.add(new Label("Customer Name:"), 0, 0); g.add(nameF, 1, 0);
        g.add(new Label("Billing Address:"), 0, 1); g.add(addrF, 1, 1);
        g.add(new Label("GSTIN:"), 0, 2); g.add(gstF, 1, 2);
        g.add(new Label("Phone:"), 0, 3); g.add(phoneF, 1, 3);
        g.add(new Label("State Name:"), 0, 4); g.add(stateF, 1, 4);
        g.add(new Label("State Code:"), 0, 5); g.add(stateCodeBox, 1, 5);

        // Query custom buyer fields defined under Settings
        Settings settings = app.getData().getSettings();
        List<BuyerFieldDef> defs = settings != null && settings.getBuyerFields() != null ? settings.getBuyerFields() : List.of();
        Map<String, TextField> customInputs = new LinkedHashMap<>();

        int rowIdx = 6;
        if (!defs.isEmpty()) {
            Separator sep = new Separator();
            sep.setPadding(new Insets(4, 0, 4, 0));
            g.add(sep, 0, rowIdx++, 2, 1);

            Label customSecHeader = new Label("Custom Fields (from Settings):");
            customSecHeader.getStyleClass().add("section-eyebrow");
            g.add(customSecHeader, 0, rowIdx++, 2, 1);

            Map<String, String> existingCustom = existing != null && existing.getCustom() != null ? existing.getCustom() : Map.of();
            for (BuyerFieldDef def : defs) {
                String val = existingCustom.getOrDefault(def.getKey(), "");
                TextField cf = new TextField(val);
                cf.setPromptText(def.getLabel() + " (" + (def.getType() != null ? def.getType() : "text") + ")");
                customInputs.put(def.getKey(), cf);

                g.add(new Label(def.getLabel() + ":"), 0, rowIdx);
                g.add(cf, 1, rowIdx);
                rowIdx++;
            }
        }

        dlg.getDialogPane().setContent(g);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        DialogHelper.styleDialog(dlg);

        dlg.setResultConverter(btn -> {
            if (btn == ButtonType.OK) {
                String id = existing != null ? existing.getId() : "byr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                Buyer b = new Buyer(id, nameF.getText().trim(), addrF.getText().trim(), gstF.getText().trim(), phoneF.getText().trim(), stateF.getText().trim(), stateCodeF.getText().trim());
                Map<String, String> customMap = new HashMap<>();
                if (existing != null && existing.getCustom() != null) {
                    customMap.putAll(existing.getCustom());
                }
                for (Map.Entry<String, TextField> entry : customInputs.entrySet()) {
                    customMap.put(entry.getKey(), entry.getValue().getText().trim());
                }
                b.setCustom(customMap);
                return b;
            }
            return null;
        });

        dlg.showAndWait().ifPresent(b -> {
            if (b.getName().isBlank()) {
                Toast.show(app.getRootPane(), "Validation Error", "Name is required.", true);
                return;
            }
            app.getData().buyers().saveBuyer(b);
            refresh();
            Toast.show(app.getRootPane(), "Buyer Saved", b.getName() + " saved.", false);
        });
    }

    // ------------------------------------------------------------------
    // Statement / ledger dialog
    // ------------------------------------------------------------------

    private void showStatementDialog(Buyer b) {
        if (b == null) return;
        String cur = app.getData().getSettings().getCurrency();
        List<Bill> bills = getBuyerBills(b);

        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Account Statement — " + b.getName());
        dlg.setHeaderText("Statement of account for " + b.getName());

        VBox content = new VBox(12);
        content.setPrefWidth(740);
        content.setPrefHeight(480);
        content.setMaxWidth(Double.MAX_VALUE);
        content.setMaxHeight(Double.MAX_VALUE);

        TableView<Bill> stTable = new TableView<>();
        stTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        stTable.getStyleClass().add("table-view");

        TableColumn<Bill, String> cDate = new TableColumn<>("Date");
        cDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDate()));
        cDate.setPrefWidth(95);

        TableColumn<Bill, String> cNo = new TableColumn<>("Invoice #");
        cNo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBillNo()));
        cNo.setPrefWidth(110);

        TableColumn<Bill, String> cTotal = new TableColumn<>("Grand Total");
        cTotal.setCellValueFactory(d -> new SimpleStringProperty(String.format("%s%.2f", cur, d.getValue().getTotals().getGrandTotal())));
        cTotal.setPrefWidth(120);

        TableColumn<Bill, String> cPaid = new TableColumn<>("Paid");
        cPaid.setCellValueFactory(d -> {
            double p = d.getValue().getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
            if (p == 0 && d.getValue().getStatus() == BillStatus.PAID) p = d.getValue().getTotals().getGrandTotal();
            return new SimpleStringProperty(String.format("%s%.2f", cur, p));
        });
        cPaid.setPrefWidth(110);

        TableColumn<Bill, String> cBal = new TableColumn<>("Balance Due");
        cBal.setCellValueFactory(d -> {
            double p = d.getValue().getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
            if (p == 0 && d.getValue().getStatus() == BillStatus.PAID) return new SimpleStringProperty(String.format("%s%.2f", cur, 0.0));
            double bal = Math.max(0, d.getValue().getTotals().getGrandTotal() - p);
            return new SimpleStringProperty(String.format("%s%.2f", cur, bal));
        });
        cBal.setPrefWidth(120);

        TableColumn<Bill, String> cStatus = new TableColumn<>("Status");
        cStatus.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus().getLabel()));
        cStatus.setPrefWidth(100);

        stTable.getColumns().addAll(cDate, cNo, cTotal, cPaid, cBal, cStatus);
        stTable.setItems(FXCollections.observableArrayList(bills));
        VBox.setVgrow(stTable, Priority.ALWAYS);

        // Summary bar at bottom
        double sumBilled = bills.stream().mapToDouble(bi -> bi.getTotals().getGrandTotal()).sum();
        double sumPaid = bills.stream().mapToDouble(bi -> {
            double p = bi.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
            return (p == 0 && bi.getStatus() == BillStatus.PAID) ? bi.getTotals().getGrandTotal() : p;
        }).sum();
        double sumDue = Math.max(0, sumBilled - sumPaid);

        HBox summary = new HBox(20);
        summary.setAlignment(Pos.CENTER_RIGHT);
        summary.getStyleClass().add("card-pane-subtle");
        summary.setPadding(new Insets(10, 14, 10, 14));

        Label lBilled = new Label(String.format("Total Billed: %s%.2f", cur, sumBilled));
        lBilled.getStyleClass().add("table-cell-title");
        Label lPaid = new Label(String.format("Paid: %s%.2f", cur, sumPaid));
        lPaid.getStyleClass().addAll("table-cell-title", "accent-emerald");
        Label lDue = new Label(String.format("Due: %s%.2f", cur, sumDue));
        lDue.getStyleClass().addAll("table-cell-title", "accent-red");
        summary.getChildren().addAll(lBilled, lPaid, lDue);

        content.getChildren().addAll(stTable, summary);
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        DialogHelper.styleDialog(dlg, 600, 420);
        dlg.showAndWait();
    }

    // ------------------------------------------------------------------
    // CSV import / export
    // ------------------------------------------------------------------

    private void showImportDialog() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Customer CSV Spreadsheet");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Spreadsheets (*.csv)", "*.csv"));
        File f = fc.showOpenDialog(app.getPrimaryStage());
        if (f == null) return;

        try {
            String text = Files.readString(f.toPath());
            List<List<String>> rows = CsvService.parseCsv(text);
            if (rows.isEmpty()) {
                Toast.show(app.getRootPane(), "Empty File", "No rows found in CSV.", true);
                return;
            }

            Dialog<ButtonType> dlg = new Dialog<>();
            dlg.setTitle("CSV Import Preview");
            dlg.setHeaderText("Importing " + (rows.size() - 1) + " rows from " + f.getName());

            VBox box = new VBox(10);
            box.setPadding(new Insets(14));
            box.setPrefWidth(550);

            CheckBox updateExistingCb = new CheckBox("Update existing buyers if name matches");
            updateExistingCb.setSelected(true);

            Label info = new Label("Detected " + (rows.size() - 1) + " records. Headers: " + String.join(", ", rows.get(0)));
            info.getStyleClass().add("kpi-subtext");
            info.setWrapText(true);

            Label fieldsNotice = new Label("Supported columns: Name, Address, GSTIN, Phone, State, State Code + Custom Fields");
            fieldsNotice.getStyleClass().add("section-eyebrow");

            box.getChildren().addAll(fieldsNotice, info, updateExistingCb);
            dlg.getDialogPane().setContent(box);
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            DialogHelper.styleDialog(dlg);

            dlg.showAndWait().ifPresent(ans -> {
                if (ans == ButtonType.OK) {
                    List<String> headers = rows.get(0);
                    List<BuyerFieldDef> defs = app.getData().getSettings().getBuyerFields();

                    // Detect column mappings by header name
                    int nameCol = -1;
                    int addrCol = -1;
                    int gstCol = -1;
                    int phoneCol = -1;
                    int stateCol = -1;
                    int stateCodeCol = -1;

                    for (int c = 0; c < headers.size(); c++) {
                        String h = headers.get(c).trim().toLowerCase().replaceAll("[_\\-\\s]+", "");
                        if (nameCol == -1 && (h.equals("name") || h.equals("customername") || h.equals("buyername"))) {
                            nameCol = c;
                        } else if (addrCol == -1 && (h.equals("address") || h.equals("billingaddress") || h.equals("addr"))) {
                            addrCol = c;
                        } else if (gstCol == -1 && (h.equals("gstin") || h.equals("gst") || h.equals("gstno") || h.equals("gstnumber"))) {
                            gstCol = c;
                        } else if (phoneCol == -1 && (h.equals("phone") || h.equals("mobile") || h.equals("contact") || h.equals("phoneno"))) {
                            phoneCol = c;
                        } else if (stateCodeCol == -1 && (h.equals("statecode") || h.equals("poscode") || h.equals("gststatecode") || h.equals("code"))) {
                            stateCodeCol = c;
                        } else if (stateCol == -1 && (h.equals("state") || h.equals("placeofsupply") || h.equals("statename") || h.equals("pos"))) {
                            stateCol = c;
                        }
                    }

                    // Fallbacks for positional standard columns if not detected by header name
                    if (nameCol == -1 && headers.size() > 0) nameCol = 0;
                    if (addrCol == -1 && headers.size() > 1) addrCol = 1;
                    if (gstCol == -1 && headers.size() > 2) gstCol = 2;
                    if (phoneCol == -1 && headers.size() > 3) phoneCol = 3;
                    if (stateCol == -1 && headers.size() > 4) stateCol = 4;
                    if (stateCodeCol == -1 && headers.size() > 5) {
                        String h5 = headers.get(5).trim().toLowerCase();
                        if (h5.contains("code") || h5.contains("state")) {
                            stateCodeCol = 5;
                        }
                    }

                    Set<Integer> standardCols = new HashSet<>();
                    if (nameCol >= 0) standardCols.add(nameCol);
                    if (addrCol >= 0) standardCols.add(addrCol);
                    if (gstCol >= 0) standardCols.add(gstCol);
                    if (phoneCol >= 0) standardCols.add(phoneCol);
                    if (stateCol >= 0) standardCols.add(stateCol);
                    if (stateCodeCol >= 0) standardCols.add(stateCodeCol);

                    int imported = 0;
                    for (int i = 1; i < rows.size(); i++) {
                        List<String> r = rows.get(i);
                        if (r.isEmpty()) continue;
                        String name = (nameCol >= 0 && nameCol < r.size()) ? r.get(nameCol).trim() : "";
                        if (name.isBlank()) continue;
                        String addr = (addrCol >= 0 && addrCol < r.size()) ? r.get(addrCol).trim() : "";
                        String gst = (gstCol >= 0 && gstCol < r.size()) ? r.get(gstCol).trim() : "";
                        String phone = (phoneCol >= 0 && phoneCol < r.size()) ? r.get(phoneCol).trim() : "";
                        String state = (stateCol >= 0 && stateCol < r.size()) ? r.get(stateCol).trim() : "";
                        String stateCode = (stateCodeCol >= 0 && stateCodeCol < r.size()) ? r.get(stateCodeCol).trim() : "";

                        // Auto-derive 2-digit state code from GSTIN if empty
                        if (stateCode.isBlank() && gst.length() >= 2) {
                            String prefix = gst.substring(0, 2);
                            if (prefix.matches("\\d{2}")) {
                                stateCode = prefix;
                            }
                        }

                        Buyer existing = app.getData().buyers().findByName(name);
                        if (existing != null && !updateExistingCb.isSelected()) continue;

                        String id = existing != null ? existing.getId() : "byr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                        if (existing != null) {
                            if (addr.isBlank() && existing.getAddress() != null) addr = existing.getAddress();
                            if (gst.isBlank() && existing.getGst() != null) gst = existing.getGst();
                            if (phone.isBlank() && existing.getPhone() != null) phone = existing.getPhone();
                            if (state.isBlank() && existing.getState() != null) state = existing.getState();
                            if (stateCode.isBlank() && existing.getStateCode() != null) stateCode = existing.getStateCode();
                        }

                        Buyer b = new Buyer(id, name, addr, gst, phone, state, stateCode);

                        // Populate custom columns if present in CSV
                        Map<String, String> customMap = new HashMap<>();
                        if (existing != null && existing.getCustom() != null) {
                            customMap.putAll(existing.getCustom());
                        }
                        if (defs != null) {
                            for (BuyerFieldDef def : defs) {
                                for (int colIdx = 0; colIdx < headers.size() && colIdx < r.size(); colIdx++) {
                                    if (standardCols.contains(colIdx)) continue;
                                    String h = headers.get(colIdx).trim();
                                    if (h.equalsIgnoreCase(def.getLabel()) || h.equalsIgnoreCase(def.getKey())) {
                                        customMap.put(def.getKey(), r.get(colIdx).trim());
                                    }
                                }
                            }
                        }
                        b.setCustom(customMap);

                        app.getData().buyers().saveBuyer(b);
                        imported++;
                    }
                    refresh();
                    Toast.show(app.getRootPane(), "Import Complete", "Successfully imported " + imported + " customers.", false);
                }
            });
        } catch (Exception ex) {
            Toast.show(app.getRootPane(), "Import Error", ex.getMessage(), true);
        }
    }

    private void downloadSampleCsv() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Sample Buyer CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Spreadsheet (*.csv)", "*.csv"));
        fc.setInitialFileName("buyers-sample.csv");
        File dest = fc.showSaveDialog(app.getPrimaryStage());
        if (dest != null) {
            try (FileWriter fw = new FileWriter(dest)) {
                fw.write(CsvService.getSampleBuyerCsv(app.getData().getSettings().getBuyerFields()));
                Toast.show(app.getRootPane(), "Sample CSV Saved", "Saved to " + dest.getName(), false);
            } catch (Exception ex) {
                Toast.show(app.getRootPane(), "Save Failed", ex.getMessage(), true);
            }
        }
    }

    private void exportBuyersCsv() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Export Buyers CSV");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Spreadsheet (*.csv)", "*.csv"));
        fc.setInitialFileName("buyers-export-" + LocalDate.now() + ".csv");
        File dest = fc.showSaveDialog(app.getPrimaryStage());
        if (dest != null) {
            try (FileWriter fw = new FileWriter(dest)) {
                fw.write(CsvService.exportBuyers(new ArrayList<>(filteredBuyers), app.getData().getSettings().getBuyerFields()));
                Toast.show(app.getRootPane(), "Export Successful", "Saved " + filteredBuyers.size() + " buyers.", false);
            } catch (Exception ex) {
                Toast.show(app.getRootPane(), "Export Failed", ex.getMessage(), true);
            }
        }
    }
}
