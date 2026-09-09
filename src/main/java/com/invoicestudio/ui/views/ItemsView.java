package com.invoicestudio.ui.views;

import com.invoicestudio.db.BillDao;
import com.invoicestudio.db.ItemDao;
import com.invoicestudio.model.Bill;
import com.invoicestudio.model.BillItem;
import com.invoicestudio.model.BillStatus;
import com.invoicestudio.model.ItemRecord;
import com.invoicestudio.service.BillingService;
import com.invoicestudio.ui.DialogHelper;
import com.invoicestudio.ui.IconHelper;
import com.invoicestudio.ui.Toast;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ItemsView extends VBox {

    private final ItemDao itemDao;
    private final BillDao billDao;
    private final String currency;
    private final Runnable onChanged;
    private final Consumer<ItemRecord> onNewBillWithItem;

    private List<ItemRecord> allItems = new ArrayList<>();
    private List<Bill> allBills = new ArrayList<>();

    // Aggregates
    private final Map<String, ItemStat> statsMap = new HashMap<>();

    // Controls
    private final TextField searchField = new TextField();
    private final VBox tableContainer = new VBox();
    private final VBox analyticsContainer = new VBox(16);
    private final VBox dynamicBody = new VBox(16);

    private String activeTab = "catalog"; // "catalog" or "analytics"
    private Button catalogTabBtn;
    private Button analyticsTabBtn;

    // KPI labels
    private Label totalItemsVal;
    private Label totalUnitsVal;
    private Label totalRevenueVal;
    private Label topSellerNameVal;
    private Label topSellerQtyVal;

    private static final String[] COMMON_UNITS = {"PCS", "NOS", "BOX", "KG", "MTR", "HRS", "SET", "PKT", "LTR", "BAG", "DOZ"};
    private static final Integer[] COMMON_GST = {0, 5, 12, 18, 28};

    public static class ItemStat {
        public double totalQty = 0;
        public double totalAmount = 0;
        public int billCount = 0;
    }

    public ItemsView(ItemDao itemDao, BillDao billDao, String currency, Runnable onChanged, Consumer<ItemRecord> onNewBillWithItem) {
        this.itemDao = itemDao;
        this.billDao = billDao;
        this.currency = currency != null ? currency : "₹";
        this.onChanged = onChanged;
        this.onNewBillWithItem = onNewBillWithItem;

        setSpacing(20);
        setPadding(new Insets(24));
        getStyleClass().add("root-pane");
        setStyle("-fx-background-color: #0B0E13;");

        buildHeader();
        buildKpis();
        buildContentTabs();

        reload();
    }

    private void buildHeader() {
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label titleIcon = IconHelper.createIconLabel(IconHelper.ICON_PACKAGE, 22, "#d9a13b");
        Label title = new Label("Item Catalog & Reports");
        title.getStyleClass().add("view-title");
        titleRow.getChildren().addAll(titleIcon, title);

        Label sub = new Label("Manage product and service items, set default rates & GST, and track sales performance across bills.");
        sub.getStyleClass().add("view-subtitle");
        titleBox.getChildren().addAll(titleRow, sub);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        // Tab switcher
        HBox tabSwitch = new HBox(2);
        tabSwitch.getStyleClass().add("subtab-bar");
        tabSwitch.setAlignment(Pos.CENTER);

        catalogTabBtn = new Button("Catalog (0)");
        catalogTabBtn.getStyleClass().addAll("subtab-button", "active");
        catalogTabBtn.setTooltip(new Tooltip("View all catalog items"));
        catalogTabBtn.setOnAction(e -> switchTab("catalog"));

        analyticsTabBtn = new Button("Sales Reports");
        analyticsTabBtn.setGraphic(IconHelper.createIconLabel(IconHelper.ICON_BAR_CHART, 14, "#8a99ad"));
        analyticsTabBtn.getStyleClass().add("subtab-button");
        analyticsTabBtn.setTooltip(new Tooltip("View item sales reports and analytics"));
        analyticsTabBtn.setOnAction(e -> switchTab("analytics"));

        tabSwitch.getChildren().addAll(catalogTabBtn, analyticsTabBtn);

        Button newItemBtn = new Button("New Item");
        newItemBtn.setGraphic(IconHelper.createIconLabel(IconHelper.ICON_PLUS, 14, "#000000"));
        newItemBtn.getStyleClass().add("button-primary");
        newItemBtn.setTooltip(new Tooltip("Add a new product or service to catalog"));
        newItemBtn.setOnAction(e -> openItemDialog(null));

        header.getChildren().addAll(titleBox, tabSwitch, newItemBtn);
        getChildren().add(header);
    }

    private void buildKpis() {
        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(16);

        for (int i = 0; i < 4; i++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(25);
            grid.getColumnConstraints().add(cc);
        }

        // 1. Catalog Items
        VBox c1 = createKpiCard(IconHelper.ICON_TAG, "CATALOG ITEMS", "#8a99ad");
        totalItemsVal = new Label("0");
        totalItemsVal.getStyleClass().add("kpi-value");
        Label sub1 = new Label("Reusable item masters");
        sub1.getStyleClass().add("kpi-subtext");
        c1.getChildren().addAll(totalItemsVal, sub1);

        // 2. Units Sold
        VBox c2 = createKpiCard(IconHelper.ICON_TRENDING_UP, "TOTAL UNITS SOLD", "#d9a13b");
        totalUnitsVal = new Label("0");
        totalUnitsVal.getStyleClass().addAll("kpi-value", "accent-gold");
        Label sub2 = new Label("Across active bills");
        sub2.getStyleClass().add("kpi-subtext");
        c2.getChildren().addAll(totalUnitsVal, sub2);

        // 3. Revenue
        VBox c3 = createKpiCard(IconHelper.ICON_BAR_CHART, "TOTAL REVENUE", "#10b981");
        totalRevenueVal = new Label(currency + "0.00");
        totalRevenueVal.getStyleClass().addAll("kpi-value", "accent-emerald");
        Label sub3 = new Label("Item sales generated");
        sub3.getStyleClass().add("kpi-subtext");
        c3.getChildren().addAll(totalRevenueVal, sub3);

        // 4. Top Seller
        VBox c4 = createKpiCard(IconHelper.ICON_SPARKLES, "TOP SELLER", "#f59e0b");
        topSellerNameVal = new Label("—");
        topSellerNameVal.getStyleClass().add("kpi-value-small");
        topSellerQtyVal = new Label("No sales yet");
        topSellerQtyVal.getStyleClass().add("kpi-subtext");
        c4.getChildren().addAll(topSellerNameVal, topSellerQtyVal);

        grid.add(c1, 0, 0);
        grid.add(c2, 1, 0);
        grid.add(c3, 2, 0);
        grid.add(c4, 3, 0);

        getChildren().add(grid);
    }

    private VBox createKpiCard(String icon, String title, String iconColor) {
        VBox card = new VBox(6);
        card.getStyleClass().add("kpi-card");
        HBox top = new HBox(6);
        top.setAlignment(Pos.CENTER_LEFT);
        Label ic = IconHelper.createIconLabel(icon, 14, iconColor);
        Label lbl = new Label(title);
        lbl.getStyleClass().add("kpi-title");
        top.getChildren().addAll(ic, lbl);
        card.getChildren().add(top);
        return card;
    }

    private void buildContentTabs() {
        // Search & Filter Toolbar
        HBox toolbar = new HBox(12);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.getStyleClass().add("card-pane");
        toolbar.setPadding(new Insets(12, 16, 12, 16));

        Label sIcon = IconHelper.createIconLabel(IconHelper.ICON_SEARCH, 14, "#8a99ad");
        searchField.setPromptText("Search items by name, HSN/SAC, or unit...");
        searchField.getStyleClass().add("search-field");
        searchField.textProperty().addListener((obs, oldV, newV) -> renderCatalogList());
        HBox.setHgrow(searchField, Priority.ALWAYS);

        toolbar.getChildren().addAll(sIcon, searchField);

        // Table container
        tableContainer.setSpacing(8);

        // Analytics container
        analyticsContainer.setSpacing(16);

        dynamicBody.getChildren().setAll(toolbar, tableContainer);
        VBox.setVgrow(dynamicBody, Priority.ALWAYS);
        getChildren().add(dynamicBody);
    }

    private void switchTab(String tab) {
        this.activeTab = tab;
        if ("catalog".equals(tab)) {
            catalogTabBtn.getStyleClass().add("active");
            analyticsTabBtn.getStyleClass().remove("active");
            analyticsTabBtn.setGraphic(IconHelper.createIconLabel(IconHelper.ICON_BAR_CHART, 14, "#8a99ad"));
            dynamicBody.getChildren().clear();
            HBox toolbar = new HBox(12);
            toolbar.setAlignment(Pos.CENTER_LEFT);
            toolbar.getStyleClass().add("card-pane");
            toolbar.setPadding(new Insets(12, 16, 12, 16));
            Label sIcon = IconHelper.createIconLabel(IconHelper.ICON_SEARCH, 14, "#8a99ad");
            toolbar.getChildren().addAll(sIcon, searchField);
            dynamicBody.getChildren().addAll(toolbar, tableContainer);
            renderCatalogList();
        } else {
            analyticsTabBtn.getStyleClass().add("active");
            analyticsTabBtn.setGraphic(IconHelper.createIconLabel(IconHelper.ICON_BAR_CHART, 14, "#e8c064"));
            catalogTabBtn.getStyleClass().remove("active");
            dynamicBody.getChildren().clear();
            renderAnalytics();
            dynamicBody.getChildren().add(analyticsContainer);
        }
    }

    public void reload() {
        try {
            allItems = itemDao.findAll();
            allBills = billDao.findAll();
            computeStats();
            updateKpis();
            catalogTabBtn.setText("Catalog (" + allItems.size() + ")");
            if ("catalog".equals(activeTab)) {
                renderCatalogList();
            } else {
                renderAnalytics();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.show(this, "Failed to load catalog: " + e.getMessage());
        }
    }

    private void computeStats() {
        statsMap.clear();
        for (Bill b : allBills) {
            if (b.getStatus() == BillStatus.CANCELLED) continue;
            if (b.getItems() == null) continue;
            for (BillItem it : b.getItems()) {
                if (it.getDesc() == null || it.getDesc().trim().isEmpty()) continue;
                String key = it.getDesc().trim().toLowerCase();
                ItemStat st = statsMap.computeIfAbsent(key, k -> new ItemStat());
                st.totalQty += it.getQty();
                double itemTot = it.getQty() * it.getRate();
                if (it.getDiscPct() > 0) {
                    itemTot -= itemTot * (it.getDiscPct() / 100.0);
                }
                st.totalAmount += itemTot;
                st.billCount += 1;
            }
        }
    }

    private void updateKpis() {
        int totalCatalog = allItems.size();
        double totalUnits = 0;
        double totalRev = 0;
        String topItem = "—";
        double topQty = 0;

        for (ItemRecord it : allItems) {
            ItemStat st = statsMap.get(it.getName().trim().toLowerCase());
            if (st != null) {
                totalUnits += st.totalQty;
                totalRev += st.totalAmount;
                if (st.totalQty > topQty) {
                    topQty = st.totalQty;
                    topItem = it.getName();
                }
            }
        }

        // Also check uncataloged items from bills
        for (Map.Entry<String, ItemStat> entry : statsMap.entrySet()) {
            if (entry.getValue().totalQty > topQty) {
                boolean existsInCatalog = allItems.stream().anyMatch(i -> i.getName().equalsIgnoreCase(entry.getKey()));
                if (!existsInCatalog) {
                    topQty = entry.getValue().totalQty;
                    topItem = entry.getKey();
                }
            }
        }

        totalItemsVal.setText(String.valueOf(totalCatalog));
        totalUnitsVal.setText(String.format("%,.0f", totalUnits));
        totalRevenueVal.setText(BillingService.formatMoney(totalRev, currency));
        topSellerNameVal.setText(topItem);
        topSellerQtyVal.setText(topQty > 0 ? String.format("%,.0f units sold", topQty) : "No sales yet");
    }

    private void renderCatalogList() {
        tableContainer.getChildren().clear();

        String q = searchField.getText().trim().toLowerCase();
        List<ItemRecord> filtered = allItems.stream().filter(it -> {
            if (q.isEmpty()) return true;
            return it.getName().toLowerCase().contains(q)
                    || (it.getHsn() != null && it.getHsn().toLowerCase().contains(q))
                    || (it.getUnit() != null && it.getUnit().toLowerCase().contains(q));
        }).collect(Collectors.toList());

        if (filtered.isEmpty()) {
            VBox empty = new VBox(12);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(48));
            empty.getStyleClass().add("card-pane");
            Label emptyIcon = IconHelper.createIconLabel(IconHelper.ICON_PACKAGE, 36, "#3a4456");
            Label emptyText = new Label(allItems.isEmpty() ? "No items in catalog yet. Click 'New Item' above." : "No matching items found.");
            emptyText.getStyleClass().add("muted-label");
            empty.getChildren().addAll(emptyIcon, emptyText);
            tableContainer.getChildren().add(empty);
            return;
        }

        // Header Row
        HBox th = new HBox(12);
        th.getStyleClass().add("table-header-row");
        th.setPadding(new Insets(10, 16, 10, 16));

        Label hName = new Label("ITEM / SERVICE");
        hName.getStyleClass().add("table-th");
        HBox.setHgrow(hName, Priority.ALWAYS);
        hName.setMaxWidth(Double.MAX_VALUE);

        Label hRate = new Label("DEFAULT RATE");
        hRate.getStyleClass().add("table-th");
        hRate.setPrefWidth(120);

        Label hGst = new Label("GST %");
        hGst.getStyleClass().add("table-th");
        hGst.setPrefWidth(80);

        Label hUnits = new Label("UNITS SOLD");
        hUnits.getStyleClass().add("table-th");
        hUnits.setPrefWidth(100);

        Label hRev = new Label("TOTAL REVENUE");
        hRev.getStyleClass().add("table-th");
        hRev.setPrefWidth(130);

        Label hActions = new Label("ACTIONS");
        hActions.getStyleClass().add("table-th");
        hActions.setPrefWidth(180);
        hActions.setAlignment(Pos.CENTER_RIGHT);

        th.getChildren().addAll(hName, hRate, hGst, hUnits, hRev, hActions);
        tableContainer.getChildren().add(th);

        for (ItemRecord it : filtered) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("table-data-row");
            row.setPadding(new Insets(12, 16, 12, 16));

            // Name + HSN
            VBox nameBox = new VBox(3);
            Label nameLbl = new Label(it.getName());
            nameLbl.getStyleClass().add("table-cell-title");
            HBox badgeRow = new HBox(6);
            badgeRow.setAlignment(Pos.CENTER_LEFT);
            if (it.getHsn() != null && !it.getHsn().trim().isEmpty()) {
                Label hsn = new Label("HSN: " + it.getHsn());
                hsn.getStyleClass().add("badge-hsn");
                badgeRow.getChildren().add(hsn);
            }
            Label unitLbl = new Label(it.getUnit() != null ? it.getUnit() : "PCS");
            unitLbl.getStyleClass().add("badge-neutral");
            badgeRow.getChildren().add(unitLbl);
            nameBox.getChildren().addAll(nameLbl, badgeRow);
            HBox.setHgrow(nameBox, Priority.ALWAYS);

            // Rate
            Label rateLbl = new Label(BillingService.formatMoney(it.getRate(), currency));
            rateLbl.getStyleClass().add("table-cell-mono");
            rateLbl.setPrefWidth(120);

            // GST
            Label gstLbl = new Label(it.getGst() > 0 ? it.getGst() + "%" : "0%");
            gstLbl.getStyleClass().add("table-cell-mono");
            gstLbl.setPrefWidth(80);

            // Stats
            ItemStat st = statsMap.get(it.getName().trim().toLowerCase());
            double soldQty = st != null ? st.totalQty : 0;
            double soldRev = st != null ? st.totalAmount : 0;

            Label soldQtyLbl = new Label(String.format("%,.0f", soldQty));
            soldQtyLbl.getStyleClass().add("table-cell-mono");
            soldQtyLbl.setPrefWidth(100);

            Label soldRevLbl = new Label(BillingService.formatMoney(soldRev, currency));
            soldRevLbl.getStyleClass().addAll("table-cell-mono", "accent-emerald");
            soldRevLbl.setPrefWidth(130);

            // Actions
            HBox actions = new HBox(8);
            actions.setAlignment(Pos.CENTER_RIGHT);
            actions.setPrefWidth(180);

            Button billBtn = new Button("Bill Item");
            billBtn.getStyleClass().add("button-secondary-small");
            billBtn.setTooltip(new Tooltip("Create a new bill with this item"));
            billBtn.setOnAction(e -> {
                if (onNewBillWithItem != null) {
                    onNewBillWithItem.accept(it);
                }
            });

            Button editBtn = new Button();
            editBtn.setGraphic(IconHelper.createIconLabel(IconHelper.ICON_EDIT, 14, "#8a99ad"));
            editBtn.getStyleClass().add("button-icon-subtle");
            editBtn.setTooltip(new Tooltip("Edit item"));
            editBtn.setOnAction(e -> openItemDialog(it));

            Button delBtn = new Button();
            delBtn.setGraphic(IconHelper.createIconLabel(IconHelper.ICON_TRASH, 14, "#ef4444"));
            delBtn.getStyleClass().add("button-icon-subtle");
            delBtn.setTooltip(new Tooltip("Delete item"));
            delBtn.setOnAction(e -> confirmDelete(it));

            actions.getChildren().addAll(billBtn, editBtn, delBtn);

            row.getChildren().addAll(nameBox, rateLbl, gstLbl, soldQtyLbl, soldRevLbl, actions);
            tableContainer.getChildren().add(row);
        }
    }

    private void renderAnalytics() {
        analyticsContainer.getChildren().clear();

        // Sort items by units sold
        List<Map.Entry<String, ItemStat>> byQty = new ArrayList<>(statsMap.entrySet());
        byQty.sort((a, b) -> Double.compare(b.getValue().totalQty, a.getValue().totalQty));

        // Sort items by revenue
        List<Map.Entry<String, ItemStat>> byRev = new ArrayList<>(statsMap.entrySet());
        byRev.sort((a, b) -> Double.compare(b.getValue().totalAmount, a.getValue().totalAmount));

        HBox chartsRow = new HBox(16);
        chartsRow.setAlignment(Pos.TOP_LEFT);

        // Chart 1: Units Sold
        VBox c1 = new VBox(14);
        c1.getStyleClass().add("card-pane");
        c1.setPadding(new Insets(16));
        HBox.setHgrow(c1, Priority.ALWAYS);

        HBox c1Head = new HBox(8);
        c1Head.setAlignment(Pos.CENTER_LEFT);
        Label ic1 = IconHelper.createIconLabel(IconHelper.ICON_BAR_CHART, 14, "#d9a13b");
        Label c1Title = new Label("TOP ITEMS BY UNITS SOLD");
        c1Title.getStyleClass().add("card-title");
        HBox.setHgrow(c1Title, Priority.ALWAYS);
        Label c1Sub = new Label("Quantity");
        c1Sub.getStyleClass().add("muted-label");
        c1Head.getChildren().addAll(ic1, c1Title, c1Sub);
        c1.getChildren().add(c1Head);

        double maxQty = byQty.isEmpty() ? 1 : Math.max(1, byQty.get(0).getValue().totalQty);

        if (byQty.isEmpty() || maxQty <= 0) {
            VBox empty = new VBox(8);
            empty.setAlignment(Pos.CENTER);
            empty.setPrefHeight(180);
            Label emptyLbl = new Label("No sales data recorded yet.");
            emptyLbl.getStyleClass().add("muted-label");
            empty.getChildren().add(emptyLbl);
            c1.getChildren().add(empty);
        } else {
            VBox barsList = new VBox(10);
            int count = Math.min(8, byQty.size());
            for (int i = 0; i < count; i++) {
                Map.Entry<String, ItemStat> entry = byQty.get(i);
                double qty = entry.getValue().totalQty;
                double pct = Math.max(0.04, qty / maxQty);

                VBox barGroup = new VBox(4);
                HBox info = new HBox();
                Label name = new Label(entry.getKey());
                name.getStyleClass().add("table-cell-title");
                HBox.setHgrow(name, Priority.ALWAYS);
                Label val = new Label(String.format("%,.0f units", qty));
                val.getStyleClass().add("table-cell-mono");
                info.getChildren().addAll(name, val);

                ProgressBar pb = new ProgressBar(pct);
                pb.setMaxWidth(Double.MAX_VALUE);
                pb.getStyleClass().add("gold-progress-bar");
                barGroup.getChildren().addAll(info, pb);
                barsList.getChildren().add(barGroup);
            }
            c1.getChildren().add(barsList);
        }

        // Chart 2: Revenue
        VBox c2 = new VBox(14);
        c2.getStyleClass().add("card-pane");
        c2.setPadding(new Insets(16));
        HBox.setHgrow(c2, Priority.ALWAYS);

        HBox c2Head = new HBox(8);
        c2Head.setAlignment(Pos.CENTER_LEFT);
        Label ic2 = IconHelper.createIconLabel(IconHelper.ICON_TRENDING_UP, 14, "#10b981");
        Label c2Title = new Label("TOP ITEMS BY REVENUE");
        c2Title.getStyleClass().add("card-title");
        HBox.setHgrow(c2Title, Priority.ALWAYS);
        Label c2Sub = new Label(currency);
        c2Sub.getStyleClass().add("muted-label");
        c2Head.getChildren().addAll(ic2, c2Title, c2Sub);
        c2.getChildren().add(c2Head);

        double maxRev = byRev.isEmpty() ? 1 : Math.max(1, byRev.get(0).getValue().totalAmount);

        if (byRev.isEmpty() || maxRev <= 0) {
            VBox empty = new VBox(8);
            empty.setAlignment(Pos.CENTER);
            empty.setPrefHeight(180);
            Label emptyLbl = new Label("No revenue data recorded yet.");
            emptyLbl.getStyleClass().add("muted-label");
            empty.getChildren().add(emptyLbl);
            c2.getChildren().add(empty);
        } else {
            VBox barsList = new VBox(10);
            int count = Math.min(8, byRev.size());
            for (int i = 0; i < count; i++) {
                Map.Entry<String, ItemStat> entry = byRev.get(i);
                double amt = entry.getValue().totalAmount;
                double pct = Math.max(0.04, amt / maxRev);

                VBox barGroup = new VBox(4);
                HBox info = new HBox();
                Label name = new Label(entry.getKey());
                name.getStyleClass().add("table-cell-title");
                HBox.setHgrow(name, Priority.ALWAYS);
                Label val = new Label(BillingService.formatMoney(amt, currency));
                val.getStyleClass().addAll("table-cell-mono", "accent-emerald");
                info.getChildren().addAll(name, val);

                ProgressBar pb = new ProgressBar(pct);
                pb.setMaxWidth(Double.MAX_VALUE);
                pb.getStyleClass().add("emerald-progress-bar");
                barGroup.getChildren().addAll(info, pb);
                barsList.getChildren().add(barGroup);
            }
            c2.getChildren().add(barsList);
        }

        chartsRow.getChildren().addAll(c1, c2);
        analyticsContainer.getChildren().add(chartsRow);
    }

    private void openItemDialog(ItemRecord editing) {
        Stage dlg = new Stage();
        dlg.initModality(Modality.APPLICATION_MODAL);
        dlg.setTitle(editing == null ? "New Catalog Item" : "Edit Item: " + editing.getName());

        VBox root = new VBox(16);
        root.setPadding(new Insets(24));
        root.getStyleClass().add("dialog-root");
        root.setPrefWidth(460);

        Label title = new Label(editing == null ? "Create Catalog Item" : "Edit Item Record");
        title.getStyleClass().add("view-title");
        Label sub = new Label("Save frequently billed items for instant autocomplete and uniform pricing.");
        sub.getStyleClass().add("view-subtitle");

        TextField nameField = new TextField(editing != null ? editing.getName() : "");
        nameField.setPromptText("e.g. Website Design & Maintenance");
        nameField.getStyleClass().add("text-input");

        TextField hsnField = new TextField(editing != null && editing.getHsn() != null ? editing.getHsn() : "");
        hsnField.setPromptText("e.g. 998311");
        hsnField.getStyleClass().add("text-input");

        ComboBox<String> unitBox = new ComboBox<>(FXCollections.observableArrayList(COMMON_UNITS));
        unitBox.setEditable(true);
        unitBox.setValue(editing != null && editing.getUnit() != null ? editing.getUnit() : "PCS");
        unitBox.setMaxWidth(Double.MAX_VALUE);

        TextField rateField = new TextField(editing != null ? String.valueOf(editing.getRate()) : "0");
        rateField.getStyleClass().add("text-input");

        ComboBox<Integer> gstBox = new ComboBox<>(FXCollections.observableArrayList(COMMON_GST));
        gstBox.setValue(editing != null ? (int) editing.getGst() : 18);
        gstBox.setMaxWidth(Double.MAX_VALUE);

        VBox form = new VBox(12);
        form.getChildren().addAll(
                labeledNode("Item Name / Description *", nameField),
                labeledNode("HSN / SAC Code", hsnField),
                labeledNode("Unit of Measurement", unitBox),
                labeledNode("Default Unit Rate (" + currency + ")", rateField),
                labeledNode("Default GST Rate (%)", gstBox)
        );

        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER_RIGHT);
        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("button-secondary");
        cancelBtn.setOnAction(e -> dlg.close());

        Button saveBtn = new Button(editing == null ? "Add Item" : "Save Changes");
        saveBtn.getStyleClass().add("button-primary");
        saveBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                Toast.show(this, "Item name is required.");
                return;
            }
            double rate = 0;
            try {
                rate = Double.parseDouble(rateField.getText().trim());
            } catch (Exception ex) {
                rate = 0;
            }
            int gst = gstBox.getValue() != null ? gstBox.getValue() : 18;
            String unit = unitBox.getValue() != null ? unitBox.getValue().trim().toUpperCase() : "PCS";
            String hsn = hsnField.getText().trim();

            try {
                if (editing == null) {
                    ItemRecord it = new ItemRecord();
                    it.setName(name);
                    it.setHsn(hsn);
                    it.setUnit(unit);
                    it.setRate(rate);
                    it.setGst(gst);
                    itemDao.insert(it);
                    Toast.show(this, "Item added: " + name);
                } else {
                    editing.setName(name);
                    editing.setHsn(hsn);
                    editing.setUnit(unit);
                    editing.setRate(rate);
                    editing.setGst(gst);
                    itemDao.update(editing);
                    Toast.show(this, "Item updated: " + name);
                }
                dlg.close();
                reload();
                if (onChanged != null) onChanged.run();
            } catch (Exception ex) {
                ex.printStackTrace();
                Toast.show(this, "Failed to save item: " + ex.getMessage());
            }
        });

        btnRow.getChildren().addAll(cancelBtn, saveBtn);
        root.getChildren().addAll(title, sub, form, btnRow);

        dlg.setScene(new javafx.scene.Scene(root));
        DialogHelper.styleScene(dlg.getScene());
        dlg.showAndWait();
    }

    private void confirmDelete(ItemRecord it) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Catalog Item");
        alert.setHeaderText("Delete '" + it.getName() + "'?");
        alert.setContentText("This item will be removed from your catalog. Past bills containing this item will not be affected.");
        DialogHelper.styleDialog(alert);

        Optional<ButtonType> res = alert.showAndWait();
        if (res.isPresent() && res.get() == ButtonType.OK) {
            try {
                itemDao.delete(it.getId());
                Toast.show(this, "Item deleted.");
                reload();
                if (onChanged != null) onChanged.run();
            } catch (Exception e) {
                Toast.show(this, "Could not delete item: " + e.getMessage());
            }
        }
    }

    private VBox labeledNode(String text, javafx.scene.Node node) {
        VBox box = new VBox(4);
        Label lbl = new Label(text);
        lbl.getStyleClass().add("field-label");
        box.getChildren().addAll(lbl, node);
        return box;
    }
}
