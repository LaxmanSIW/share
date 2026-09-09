package com.invoicestudio.ui.views;

import com.invoicestudio.db.BillDao;
import com.invoicestudio.db.BuyerDao;
import com.invoicestudio.db.SettingsDao;
import com.invoicestudio.model.Bill;
import com.invoicestudio.model.BillPayment;
import com.invoicestudio.model.BillStatus;
import com.invoicestudio.model.Buyer;
import com.invoicestudio.model.BuyerFieldDef;
import com.invoicestudio.model.Settings;
import com.invoicestudio.service.CsvService;
import com.invoicestudio.ui.DialogHelper;
import com.invoicestudio.ui.IconHelper;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.Toast;
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
import java.util.stream.Collectors;

public class BuyersView extends BorderPane {

    private final StudioApp app;
    private final BuyerDao buyerDao;
    private final BillDao billDao;
    private final SettingsDao settingsDao;

    private final TableView<Buyer> table = new TableView<>();
    private FilteredList<Buyer> filteredBuyers;
    private final TextField searchField = new TextField();
    private final Label resultCountLbl = new Label("0 customers");

    // KPI Summary Header Cards
    private final Label statTotalBuyers = new Label("0");
    private final Label statTotalDue = new Label("₹0.00");
    private final Label statGstRegistered = new Label("0");

    public BuyersView(StudioApp app) {
        this.app = app;
        this.buyerDao = new BuyerDao(app.getDb());
        this.billDao = new BillDao(app.getDb());
        this.settingsDao = new SettingsDao(app.getDb());

        setPadding(new Insets(24));
        getStyleClass().add("bg-app");

        setTop(createTopBar());
        setCenter(createTableArea());

        refresh();
    }

    public void refresh() {
        rebuildTableColumns();
        List<Buyer> list = buyerDao.getAllBuyers();
        filteredBuyers = new FilteredList<>(FXCollections.observableArrayList(list), b -> true);
        table.setItems(filteredBuyers);
        applyFilter();
        updateSummaryStats(list);
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
        subtitle.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
        titleBox.getChildren().addAll(title, subtitle);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button sampleCsvBtn = new Button("Sample CSV");
        sampleCsvBtn.getStyleClass().addAll("button-sm", "button-secondary");
        sampleCsvBtn.setTooltip(new Tooltip("Download sample CSV spreadsheet structure for buyers"));
        sampleCsvBtn.setOnAction(e -> downloadSampleCsv());

        Button importCsvBtn = new Button("Import CSV");
        importCsvBtn.getStyleClass().addAll("button-sm", "button-secondary");
        importCsvBtn.setTooltip(new Tooltip("Bulk import buyers from CSV spreadsheet"));
        importCsvBtn.setOnAction(e -> showImportDialog());

        Button exportCsvBtn = new Button("Export CSV");
        exportCsvBtn.getStyleClass().addAll("button-sm", "button-secondary");
        exportCsvBtn.setTooltip(new Tooltip("Export customer directory including all custom fields to CSV"));
        exportCsvBtn.setOnAction(e -> exportBuyersCsv());

        Button addBtn = new Button("+ Add Buyer");
        addBtn.getStyleClass().addAll("gold-btn");
        addBtn.setTooltip(new Tooltip("Create a new customer profile"));
        addBtn.setOnAction(e -> showBuyerFormDialog(null));

        bar1.getChildren().addAll(titleBox, sp, sampleCsvBtn, importCsvBtn, exportCsvBtn, addBtn);

        // 2. Modern 3-Card KPI Summary Banner
        HBox statsGrid = new HBox(16);
        statsGrid.getChildren().addAll(
                buildMetricCard("TOTAL CUSTOMERS", statTotalBuyers, "Active directory profiles", "#D9A13B"),
                buildMetricCard("OUTSTANDING DUE", statTotalDue, "Total pending receivables", "#F87171"),
                buildMetricCard("GST REGISTERED", statGstRegistered, "Profiles with verified GSTIN", "#34D399")
        );

        // 3. Search and Quick Filter Bar
        HBox filterRow = new HBox(12);
        filterRow.setAlignment(Pos.CENTER_LEFT);

        searchField.setPromptText("Search by Customer Name, Phone, GSTIN, State or Custom Fields...");
        searchField.setPrefWidth(420);
        searchField.getStyleClass().add("text-field");
        searchField.textProperty().addListener((obs, o, v) -> applyFilter());

        Button clearSearchBtn = new Button("✕");
        clearSearchBtn.getStyleClass().addAll("button-sm", "button-secondary");
        clearSearchBtn.setTooltip(new Tooltip("Clear search filter"));
        clearSearchBtn.setOnAction(e -> searchField.clear());

        Region filterSp = new Region();
        HBox.setHgrow(filterSp, Priority.ALWAYS);

        resultCountLbl.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");

        filterRow.getChildren().addAll(searchField, clearSearchBtn, filterSp, resultCountLbl);

        box.getChildren().addAll(bar1, statsGrid, filterRow);
        return box;
    }

    private Node buildMetricCard(String labelText, Label valLbl, String subText, String accentColor) {
        VBox card = new VBox(4);
        card.setPadding(new Insets(12, 16, 12, 16));
        card.setStyle("-fx-background-color: #12161E; -fx-border-color: #1E2738; -fx-border-width: 1; -fx-border-radius: 8; -fx-background-radius: 8;");
        HBox.setHgrow(card, Priority.ALWAYS);

        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #8E9EB5; -fx-letter-spacing: 1.2;");

        valLbl.setStyle("-fx-font-size: 18px; -fx-font-weight: bold; -fx-text-fill: " + accentColor + ";");

        Label sub = new Label(subText);
        sub.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748B;");

        card.getChildren().addAll(lbl, valLbl, sub);
        return card;
    }

    private void updateSummaryStats(List<Buyer> buyers) {
        if (buyers == null) buyers = List.of();
        statTotalBuyers.setText(String.valueOf(buyers.size()));

        long gstCount = buyers.stream().filter(b -> b.getGst() != null && !b.getGst().isBlank()).count();
        statGstRegistered.setText(gstCount + " of " + buyers.size());

        List<Bill> allBills = billDao.getAllBills();
        double totalDue = 0;
        for (Bill bill : allBills) {
            if (bill.getStatus() == BillStatus.CANCELLED) continue;
            double paid = bill.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
            if (paid == 0 && bill.getStatus() == BillStatus.PAID) continue;
            totalDue += Math.max(0, bill.getTotals().getGrandTotal() - paid);
        }
        statTotalDue.setText(String.format("₹%.2f", totalDue));
    }

    private Node createTableArea() {
        table.getStyleClass().add("table-view");
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        // Empty state placeholder
        VBox emptyBox = new VBox(10);
        emptyBox.setAlignment(Pos.CENTER);
        emptyBox.setPadding(new Insets(30));
        Label emptyIcon = new Label("👥");
        emptyIcon.setStyle("-fx-font-size: 32px;");
        Label emptyTitle = new Label("No Customer Profiles Found");
        emptyTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: #94A3B8;");
        Label emptySub = new Label("Click '+ Add Buyer' or adjust your search filter to find records.");
        emptySub.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");
        emptyBox.getChildren().addAll(emptyIcon, emptyTitle, emptySub);
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
                    avatar.setStyle("-fx-background-color: #1A222D; -fx-text-fill: #D9A13B; -fx-font-weight: bold; -fx-font-size: 11px; -fx-min-width: 26; -fx-min-height: 26; -fx-alignment: CENTER; -fx-background-radius: 13; -fx-border-color: rgba(217,161,59,0.3); -fx-border-radius: 13;");

                    VBox textBox = new VBox(2);
                    Label nameLbl = new Label(b.getName());
                    nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #F4F4F5;");

                    String sub = b.getAddress() != null && !b.getAddress().isBlank()
                            ? (b.getAddress().length() > 32 ? b.getAddress().substring(0, 30) + "…" : b.getAddress())
                            : (b.getPhone() != null && !b.getPhone().isBlank() ? b.getPhone() : "No address specified");
                    Label subLbl = new Label(sub);
                    subLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #8E9EB5;");

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
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) setText(null);
                else {
                    setText(s);
                    setStyle("-fx-text-fill: #CBD5E1; -fx-font-size: 12px; -fx-alignment: CENTER_LEFT;");
                }
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
                    Label unreg = new Label("Unregistered");
                    unreg.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748B; -fx-padding: 2 6; -fx-background-color: #151B25; -fx-background-radius: 4;");
                    setGraphic(unreg);
                } else {
                    Label badge = new Label(gst);
                    badge.setStyle("-fx-font-family: monospace; -fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #D9A13B; -fx-background-color: #1A222D; -fx-border-color: rgba(217,161,59,0.3); -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 2 6;");
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
        colState.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String s, boolean empty) {
                super.updateItem(s, empty);
                if (empty || s == null) setText(null);
                else {
                    setText(s);
                    setStyle("-fx-text-fill: #CBD5E1; -fx-font-size: 12px; -fx-alignment: CENTER_LEFT;");
                }
            }
        });

        // Add base columns
        table.getColumns().addAll(colName, colPhone, colGst, colState);

        // 5. Dynamic Custom Buyer Fields (Configured in Settings!)
        Settings settings = settingsDao.getSettings();
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
            colCust.setCellFactory(col -> new TableCell<>() {
                @Override
                protected void updateItem(String val, boolean empty) {
                    super.updateItem(val, empty);
                    if (empty || val == null) {
                        setText(null);
                        setGraphic(null);
                    } else if ("—".equals(val)) {
                        setText("—");
                        setStyle("-fx-text-fill: #64748B; -fx-font-size: 11px; -fx-alignment: CENTER_LEFT;");
                    } else {
                        Label tag = new Label(val);
                        tag.setStyle("-fx-font-size: 11px; -fx-text-fill: #E2E8F0; -fx-background-color: #171F2C; -fx-border-color: #28354A; -fx-border-radius: 4; -fx-background-radius: 4; -fx-padding: 2 6;");
                        setGraphic(tag);
                        setText(null);
                    }
                }
            });
            table.getColumns().add(colCust);
        }

        // 6. Total Billed Column
        TableColumn<Buyer, Double> colBilled = new TableColumn<>("Total Billed");
        colBilled.setPrefWidth(120);
        colBilled.setCellValueFactory(d -> {
            double billed = getBuyerBills(d.getValue()).stream().mapToDouble(b -> b.getTotals().getGrandTotal()).sum();
            return new SimpleObjectProperty<>(billed);
        });
        colBilled.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double amt, boolean empty) {
                super.updateItem(amt, empty);
                if (empty || amt == null) setText(null);
                else {
                    setText(String.format("₹%.2f", amt));
                    setStyle("-fx-text-fill: #E2E8F0; -fx-font-weight: bold; -fx-font-size: 12px; -fx-alignment: CENTER_RIGHT;");
                }
            }
        });

        // 7. Balance Due Column (with settled / due pill badges)
        TableColumn<Buyer, Double> colDue = new TableColumn<>("Balance Due");
        colDue.setPrefWidth(130);
        colDue.setCellValueFactory(d -> {
            List<Bill> bills = getBuyerBills(d.getValue());
            double totalDue = bills.stream().mapToDouble(b -> {
                double paid = b.getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
                if (paid == 0 && b.getStatus() == BillStatus.PAID) return 0;
                return Math.max(0, b.getTotals().getGrandTotal() - paid);
            }).sum();
            return new SimpleObjectProperty<>(totalDue);
        });
        colDue.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(Double due, boolean empty) {
                super.updateItem(due, empty);
                if (empty || due == null) {
                    setGraphic(null);
                    setText(null);
                } else if (due <= 0.001) {
                    Label settled = new Label("✓ Settled");
                    settled.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #34D399; -fx-background-color: rgba(16, 185, 129, 0.15); -fx-border-color: rgba(16, 185, 129, 0.35); -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 2 8;");
                    setGraphic(settled);
                } else {
                    Label dueBadge = new Label(String.format("₹%.2f Due", due));
                    dueBadge.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-text-fill: #F87171; -fx-background-color: rgba(239, 68, 68, 0.15); -fx-border-color: rgba(239, 68, 68, 0.35); -fx-border-radius: 10; -fx-background-radius: 10; -fx-padding: 2 8;");
                    setGraphic(dueBadge);
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
                        buyerDao.deleteBuyer(b.getId());
                        refresh();
                        app.reloadAllData();
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
        int total = buyerDao.getAllBuyers().size();
        resultCountLbl.setText("Showing " + visible + " of " + total + " customers");
    }

    private List<Bill> getBuyerBills(Buyer b) {
        if (b == null) return List.of();
        return billDao.getAllBills().stream()
                .filter(bill -> b.getName().equalsIgnoreCase(bill.getVariables().getOrDefault("buyer_name", "")))
                .collect(Collectors.toList());
    }

    private void showBuyerFormDialog(Buyer existing) {
        Dialog<Buyer> dlg = new Dialog<>();
        dlg.setTitle(existing != null ? "Edit Customer Profile" : "Add New Customer");
        dlg.setHeaderText(existing != null ? "Update details for " + existing.getName() : "Create a new buyer in directory");

        GridPane g = new GridPane();
        g.setHgap(10); g.setVgap(10); g.setPadding(new Insets(16));

        ColumnConstraints col0 = new ColumnConstraints();
        col0.setPrefWidth(130);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPrefWidth(280);
        g.getColumnConstraints().addAll(col0, col1);

        TextField nameF = new TextField(existing != null ? existing.getName() : "");
        TextArea addrF = new TextArea(existing != null ? existing.getAddress() : "");
        addrF.setPrefRowCount(2);
        TextField gstF = new TextField(existing != null ? existing.getGst() : "");
        TextField phoneF = new TextField(existing != null ? existing.getPhone() : "");
        TextField stateF = new TextField(existing != null ? existing.getState() : "");
        TextField stateCodeF = new TextField(existing != null ? existing.getStateCode() : "");
        stateCodeF.setPromptText("e.g. 27");
        stateCodeF.setPrefWidth(90);

        // Auto-extract 2-digit state code from GSTIN if empty
        gstF.textProperty().addListener((obs, o, v) -> {
            if (v != null && v.trim().length() >= 2 && stateCodeF.getText().isBlank()) {
                String code = v.trim().substring(0, 2);
                if (code.matches("\\d{2}")) {
                    stateCodeF.setText(code);
                }
            }
        });

        HBox stateBox = new HBox(8);
        stateBox.setAlignment(Pos.CENTER_LEFT);
        stateF.setPromptText("e.g. Maharashtra");
        HBox.setHgrow(stateF, Priority.ALWAYS);
        Label scLbl = new Label("Code:");
        scLbl.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 11px;");
        stateBox.getChildren().addAll(stateF, scLbl, stateCodeF);

        g.add(new Label("Customer Name:"), 0, 0); g.add(nameF, 1, 0);
        g.add(new Label("Billing Address:"), 0, 1); g.add(addrF, 1, 1);
        g.add(new Label("GSTIN:"), 0, 2); g.add(gstF, 1, 2);
        g.add(new Label("Phone:"), 0, 3); g.add(phoneF, 1, 3);
        g.add(new Label("Place of Supply:"), 0, 4); g.add(stateBox, 1, 4);

        // Query custom buyer fields defined under Settings
        Settings settings = settingsDao.getSettings();
        List<BuyerFieldDef> defs = settings != null && settings.getBuyerFields() != null ? settings.getBuyerFields() : List.of();
        Map<String, TextField> customInputs = new LinkedHashMap<>();

        int rowIdx = 5;
        if (!defs.isEmpty()) {
            Separator sep = new Separator();
            sep.setPadding(new Insets(4, 0, 4, 0));
            g.add(sep, 0, rowIdx++, 2, 1);

            Label customSecHeader = new Label("Custom Fields (from Settings):");
            customSecHeader.setStyle("-fx-font-weight: bold; -fx-text-fill: #D9A13B; -fx-font-size: 11px;");
            g.add(customSecHeader, 0, rowIdx++, 2, 1);

            Map<String, String> existingCustom = existing != null && existing.getCustom() != null ? existing.getCustom() : Map.of();
            for (BuyerFieldDef def : defs) {
                String val = existingCustom.getOrDefault(def.getKey(), "");
                TextField cf = new TextField(val);
                cf.setPromptText(def.getLabel() + " (" + (def.getType() != null ? def.getType() : "text") + ")");
                customInputs.put(def.getKey(), cf);

                Label fLbl = new Label(def.getLabel() + ":");
                fLbl.setStyle("-fx-text-fill: #E2E8F0;");
                g.add(fLbl, 0, rowIdx);
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
            buyerDao.saveBuyer(b);
            refresh();
            app.reloadAllData();
            Toast.show(app.getRootPane(), "Buyer Saved", b.getName() + " saved.", false);
        });
    }

    private void showStatementDialog(Buyer b) {
        if (b == null) return;
        List<Bill> bills = getBuyerBills(b);
        Settings settings = settingsDao.getSettings();

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
        TableColumn<Bill, String> cDate = new TableColumn<>("Date");
        cDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getDate()));
        cDate.setPrefWidth(95);

        TableColumn<Bill, String> cNo = new TableColumn<>("Invoice #");
        cNo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getBillNo()));
        cNo.setPrefWidth(110);

        TableColumn<Bill, String> cTotal = new TableColumn<>("Grand Total");
        cTotal.setCellValueFactory(d -> new SimpleStringProperty(String.format("₹%.2f", d.getValue().getTotals().getGrandTotal())));
        cTotal.setPrefWidth(120);

        TableColumn<Bill, String> cPaid = new TableColumn<>("Paid");
        cPaid.setCellValueFactory(d -> {
            double p = d.getValue().getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
            if (p == 0 && d.getValue().getStatus() == BillStatus.PAID) p = d.getValue().getTotals().getGrandTotal();
            return new SimpleStringProperty(String.format("₹%.2f", p));
        });
        cPaid.setPrefWidth(110);

        TableColumn<Bill, String> cBal = new TableColumn<>("Balance Due");
        cBal.setCellValueFactory(d -> {
            double p = d.getValue().getPayments().stream().mapToDouble(BillPayment::getAmount).sum();
            if (p == 0 && d.getValue().getStatus() == BillStatus.PAID) return new SimpleStringProperty("₹0.00");
            double bal = Math.max(0, d.getValue().getTotals().getGrandTotal() - p);
            return new SimpleStringProperty(String.format("₹%.2f", bal));
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
        summary.setStyle("-fx-background-color: #12161E; -fx-padding: 10 14; -fx-border-color: #1E2738; -fx-border-width: 1; -fx-border-radius: 6; -fx-background-radius: 6;");
        Label lBilled = new Label(String.format("Total Billed: ₹%.2f", sumBilled));
        lBilled.setStyle("-fx-font-weight: bold; -fx-text-fill: #E2E8F0;");
        Label lPaid = new Label(String.format("Paid: ₹%.2f", sumPaid));
        lPaid.setStyle("-fx-font-weight: bold; -fx-text-fill: #34D399;");
        Label lDue = new Label(String.format("Due: ₹%.2f", sumDue));
        lDue.setStyle("-fx-font-weight: bold; -fx-text-fill: #F87171;");
        summary.getChildren().addAll(lBilled, lPaid, lDue);

        content.getChildren().addAll(stTable, summary);
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        DialogHelper.styleDialog(dlg, 600, 420);
        dlg.showAndWait();
    }

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

            Label info = new Label("Detected " + rows.size() + " total rows. Headers: " + String.join(", ", rows.get(0)));
            info.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

            box.getChildren().addAll(info, updateExistingCb);
            dlg.getDialogPane().setContent(box);
            dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            DialogHelper.styleDialog(dlg);

            dlg.showAndWait().ifPresent(ans -> {
                if (ans == ButtonType.OK) {
                    List<String> headers = rows.get(0);
                    List<BuyerFieldDef> defs = settingsDao.getSettings().getBuyerFields();
                    int imported = 0;
                    for (int i = 1; i < rows.size(); i++) {
                        List<String> r = rows.get(i);
                        if (r.isEmpty()) continue;
                        String name = r.size() > 0 ? r.get(0).trim() : "";
                        if (name.isBlank()) continue;
                        String addr = r.size() > 1 ? r.get(1).trim() : "";
                        String gst = r.size() > 2 ? r.get(2).trim() : "";
                        String phone = r.size() > 3 ? r.get(3).trim() : "";
                        String state = r.size() > 4 ? r.get(4).trim() : "";

                        Buyer existing = buyerDao.findByName(name);
                        if (existing != null && !updateExistingCb.isSelected()) continue;

                        String id = existing != null ? existing.getId() : "byr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
                        Buyer b = new Buyer(id, name, addr, gst, phone, state);

                        // Populate custom columns if present in CSV
                        Map<String, String> customMap = new HashMap<>();
                        if (existing != null && existing.getCustom() != null) {
                            customMap.putAll(existing.getCustom());
                        }
                        if (defs != null) {
                            for (BuyerFieldDef def : defs) {
                                for (int colIdx = 5; colIdx < headers.size() && colIdx < r.size(); colIdx++) {
                                    String h = headers.get(colIdx).trim();
                                    if (h.equalsIgnoreCase(def.getLabel()) || h.equalsIgnoreCase(def.getKey())) {
                                        customMap.put(def.getKey(), r.get(colIdx).trim());
                                    }
                                }
                            }
                        }
                        b.setCustom(customMap);

                        buyerDao.saveBuyer(b);
                        imported++;
                    }
                    refresh();
                    app.reloadAllData();
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
                fw.write(CsvService.getSampleBuyerCsv(settingsDao.getSettings().getBuyerFields()));
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
                fw.write(CsvService.exportBuyers(new ArrayList<>(filteredBuyers), settingsDao.getSettings().getBuyerFields()));
                Toast.show(app.getRootPane(), "Export Successful", "Saved " + filteredBuyers.size() + " buyers.", false);
            } catch (Exception ex) {
                Toast.show(app.getRootPane(), "Export Failed", ex.getMessage(), true);
            }
        }
    }
}
