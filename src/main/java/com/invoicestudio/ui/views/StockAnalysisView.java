package com.invoicestudio.ui.views;

import com.invoicestudio.model.Bill;
import com.invoicestudio.model.ItemRecord;
import com.invoicestudio.model.PurchaseBill;
import com.invoicestudio.service.FinancialService;
import com.invoicestudio.service.PurchaseService;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.UiTheme;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Combined purchase + sales + item analytics (Tally "Stock Summary" and
 * "Stock Item-wise Profitability"), plus a low-stock watchlist.
 *
 * All math lives in {@link FinancialService} so the scenario tests and the
 * UI share one source of truth.
 */
public class StockAnalysisView extends BorderPane {

    private final StudioApp app;
    private final FinancialService service = new FinancialService();

    private final DatePicker fromPicker = new DatePicker();
    private final DatePicker toPicker = new DatePicker();
    private final TextField searchField = new TextField();

    private TableView<FinancialService.StockSummaryRow> stockTable;
    private TableView<FinancialService.ItemProfitRow> profitTable;
    private VBox lowStockBox;

    private final Label statStockValue = UiTheme.kpiValue("₹0.00");
    private final Label statSkus = UiTheme.kpiValue("0");
    private final Label statLow = UiTheme.kpiValue("0");
    private final Label statGp = UiTheme.kpiValue("₹0.00");

    public StockAnalysisView(StudioApp app) {
        this.app = app;

        setPadding(new Insets(24));
        getStyleClass().add("bg-app");

        setTop(createTopBar());
        setCenter(buildTabs());
        refresh();
    }

    public void refresh() {
        rebuild();
    }

    private Node createTopBar() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(0, 0, 16, 0));

        HBox bar1 = new HBox(16);
        bar1.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label title = new Label("Stock & Profitability");
        title.getStyleClass().add("heading-l");
        Label subtitle = new Label("Combined purchase + sales analytics — stock summary, item-wise gross profit, low-stock alerts.");
        subtitle.getStyleClass().add("view-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        LocalDate today = LocalDate.now();
        LocalDate fyStart = today.getMonthValue() >= 4
                ? LocalDate.of(today.getYear(), 4, 1)
                : LocalDate.of(today.getYear() - 1, 4, 1);
        fromPicker.setValue(fyStart);
        toPicker.setValue(today);
        fromPicker.setPrefWidth(140);
        toPicker.setPrefWidth(140);
        fromPicker.setOnAction(e -> rebuild());
        toPicker.setOnAction(e -> rebuild());

        Button refreshBtn = UiTheme.secondaryBtn("Refresh");
        refreshBtn.setOnAction(e -> rebuild());

        bar1.getChildren().addAll(titleBox, sp, new Label("From:"), fromPicker, new Label("To:"), toPicker, refreshBtn);
        box.getChildren().addAll(bar1);
        return box;
    }

    private Node buildTabs() {
        TabPane tabs = new TabPane();
        tabs.getStyleClass().add("floating-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        stockTable = new TableView<>();
        profitTable = new TableView<>();
        lowStockBox = new VBox(8);
        lowStockBox.setPadding(new Insets(4, 0, 0, 0));

        searchField.setPromptText("Filter items...");
        searchField.setPrefWidth(240);
        searchField.getStyleClass().add("search-field");

        Tab stockTab = new Tab("Stock Summary", wrapScroll(buildStockTab()));
        Tab profitTab = new Tab("Item Profitability", wrapScroll(buildProfitTab()));
        Tab lowTab = new Tab("Low Stock", wrapScroll(lowStockBox));

        tabs.getTabs().addAll(stockTab, profitTab, lowTab);
        return tabs;
    }

    private VBox buildStockTab() {
        VBox box = new VBox(10);

        HBox stats = new HBox(16);
        stats.getChildren().addAll(
                UiTheme.kpiCard("CLOSING STOCK VALUE", statStockValue, "At cost (purchase rate)", "accent-gold"),
                UiTheme.kpiCard("TRACKED SKUs", statSkus, "Items in catalog", "accent-blue"),
                UiTheme.kpiCard("LOW STOCK", statLow, "At or below reorder point", "accent-red"),
                UiTheme.kpiCard("ITEM GROSS PROFIT", statGp, "Sales − cost of goods sold", "accent-emerald")
        );
        box.getChildren().addAll(stats, new Label("Filter:"), searchField, stockTable);
        VBox.setVgrow(stockTable, Priority.ALWAYS);
        return box;
    }

    private VBox buildProfitTab() {
        VBox box = new VBox(10);
        Label hint = new Label("Sales value vs average purchase cost per item — sort by Gross Profit to spot loss-makers.");
        hint.getStyleClass().add("text-muted");
        box.getChildren().addAll(hint, profitTable);
        VBox.setVgrow(profitTable, Priority.ALWAYS);
        return box;
    }

    private Node wrapScroll(VBox content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("scroll-pane");
        sp.setFitToHeight(true);
        return sp;
    }

    private void rebuild() {
        String from = fromPicker.getValue() != null ? fromPicker.getValue().toString() : "";
        String to = toPicker.getValue() != null ? toPicker.getValue().toString() : "";

        List<ItemRecord> items = app.getData().getAllItems();
        List<Bill> bills = app.getData().getAllBills();
        List<PurchaseBill> purchases = app.getData().getAllPurchases();
        var stock = app.getData().getStockBalances();
        String cur = app.getData().getSettings().getCurrency();

        List<FinancialService.StockSummaryRow> summary = service.stockSummary(items, stock, purchases, bills, from, to);
        List<FinancialService.ItemProfitRow> profit = service.itemProfitability(items, purchases, bills, from, to);

        rebuildStockTable(summary, cur);
        rebuildProfitTable(profit, cur);
        rebuildLowStock(items, stock, cur);

        double totalValue = summary.stream().mapToDouble(FinancialService.StockSummaryRow::closingValue).sum();
        double totalGp = profit.stream().mapToDouble(FinancialService.ItemProfitRow::grossProfit).sum();
        long lowCount = items.stream().filter(i -> isLow(i, stock)).count();

        statStockValue.setText(fmt(cur, totalValue));
        statSkus.setText(String.valueOf(items.size()));
        statLow.setText(String.valueOf(lowCount));
        statGp.setText(fmt(cur, totalGp));
    }

    private boolean isLow(ItemRecord i, java.util.Map<String, Double> stock) {
        Double bal = stock.get(i.getId());
        double qty = bal != null ? bal : i.getCurrentStock();
        return i.getReorderLevel() > 0 && qty <= i.getReorderLevel();
    }

    private void rebuildStockTable(List<FinancialService.StockSummaryRow> rows, String cur) {
        TableColumn<FinancialService.StockSummaryRow, String> cName = new TableColumn<>("Item");
        cName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name()));
        cName.setPrefWidth(200);

        TableColumn<FinancialService.StockSummaryRow, String> cUnit = new TableColumn<>("Unit");
        cUnit.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().unit()));
        cUnit.setPrefWidth(60);

        TableColumn<FinancialService.StockSummaryRow, String> cOpen = new TableColumn<>("Opening");
        cOpen.setCellValueFactory(d -> new SimpleStringProperty(fmtQty(d.getValue().openingQty())));
        cOpen.setPrefWidth(90);
        cOpen.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<FinancialService.StockSummaryRow, String> cIn = new TableColumn<>("In (Purchases)");
        cIn.setCellValueFactory(d -> new SimpleStringProperty(fmtQty(d.getValue().inQty())));
        cIn.setPrefWidth(110);
        cIn.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<FinancialService.StockSummaryRow, String> cOut = new TableColumn<>("Out (Sales)");
        cOut.setCellValueFactory(d -> new SimpleStringProperty(fmtQty(d.getValue().outQty())));
        cOut.setPrefWidth(100);
        cOut.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<FinancialService.StockSummaryRow, String> cClose = new TableColumn<>("Closing");
        cClose.setCellValueFactory(d -> new SimpleStringProperty(fmtQty(d.getValue().closingQty())));
        cClose.setPrefWidth(90);
        cClose.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<FinancialService.StockSummaryRow, String> cRate = new TableColumn<>("Cost Rate");
        cRate.setCellValueFactory(d -> new SimpleStringProperty(fmt(cur, d.getValue().costRate())));
        cRate.setPrefWidth(100);
        cRate.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<FinancialService.StockSummaryRow, String> cVal = new TableColumn<>("Closing Value");
        cVal.setCellValueFactory(d -> new SimpleStringProperty(fmt(cur, d.getValue().closingValue())));
        cVal.setPrefWidth(120);
        cVal.setStyle("-fx-alignment: CENTER-RIGHT;");

        stockTable.getColumns().setAll(cName, cUnit, cOpen, cIn, cOut, cClose, cRate, cVal);
        stockTable.setItems(FXCollections.observableArrayList(rows));
        stockTable.getStyleClass().add("table-view");
        stockTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        stockTable.setPlaceholder(UiTheme.emptyState("📦", "No Items in Catalog",
                "Add items under Items to track stock, purchases and sales."));
        applySearchFilter();
    }

    private void applySearchFilter() {
        String q = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";
        var src = stockTable.getItems();
        if (src instanceof javafx.collections.transformation.FilteredList<?> fl) return; // already wrapped
        javafx.collections.transformation.FilteredList<FinancialService.StockSummaryRow> filtered =
                new javafx.collections.transformation.FilteredList<>(FXCollections.observableArrayList(src),
                        r -> q.isEmpty() || r.name().toLowerCase().contains(q));
        searchField.textProperty().addListener((o, a, b) -> {
            String qq = b == null ? "" : b.trim().toLowerCase();
            filtered.setPredicate(r -> qq.isEmpty() || r.name().toLowerCase().contains(qq));
        });
        stockTable.setItems(filtered);
    }

    private void rebuildProfitTable(List<FinancialService.ItemProfitRow> rows, String cur) {
        TableColumn<FinancialService.ItemProfitRow, String> cName = new TableColumn<>("Item");
        cName.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().name()));
        cName.setPrefWidth(200);

        TableColumn<FinancialService.ItemProfitRow, String> cQty = new TableColumn<>("Qty Sold");
        cQty.setCellValueFactory(d -> new SimpleStringProperty(fmtQty(d.getValue().qtySold())));
        cQty.setPrefWidth(90);
        cQty.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<FinancialService.ItemProfitRow, String> cSales = new TableColumn<>("Sales Value");
        cSales.setCellValueFactory(d -> new SimpleStringProperty(fmt(cur, d.getValue().salesValue())));
        cSales.setPrefWidth(120);
        cSales.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<FinancialService.ItemProfitRow, String> cCost = new TableColumn<>("Avg Cost");
        cCost.setCellValueFactory(d -> new SimpleStringProperty(fmt(cur, d.getValue().avgCost())));
        cCost.setPrefWidth(100);
        cCost.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<FinancialService.ItemProfitRow, String> cCogs = new TableColumn<>("COGS");
        cCogs.setCellValueFactory(d -> new SimpleStringProperty(fmt(cur, d.getValue().cogs())));
        cCogs.setPrefWidth(110);
        cCogs.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<FinancialService.ItemProfitRow, String> cGp = new TableColumn<>("Gross Profit");
        cGp.setCellValueFactory(d -> new SimpleStringProperty(fmt(cur, d.getValue().grossProfit())));
        cGp.setPrefWidth(120);
        cGp.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<FinancialService.ItemProfitRow, String> cPct = new TableColumn<>("GP %");
        cPct.setCellValueFactory(d -> new SimpleStringProperty(String.format("%.1f%%", d.getValue().gpPercent())));
        cPct.setPrefWidth(80);
        cPct.setStyle("-fx-alignment: CENTER-RIGHT;");

        profitTable.getColumns().setAll(cName, cQty, cSales, cCost, cCogs, cGp, cPct);
        profitTable.setItems(FXCollections.observableArrayList(rows));
        profitTable.getStyleClass().add("table-view");
        profitTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        profitTable.setPlaceholder(UiTheme.emptyState("📈", "No Sales in Range",
                "Item profitability appears once sales are recorded for the selected period."));
    }

    private void rebuildLowStock(List<ItemRecord> items, java.util.Map<String, Double> stock, String cur) {
        lowStockBox.getChildren().clear();
        Label head = new Label("ITEMS AT OR BELOW REORDER LEVEL");
        head.getStyleClass().add("section-eyebrow");
        lowStockBox.getChildren().add(head);

        boolean any = false;
        for (ItemRecord i : items) {
            if (!isLow(i, stock)) continue;
            any = true;
            Double bal = stock.get(i.getId());
            double qty = bal != null ? bal : i.getCurrentStock();
            HBox row = new HBox();
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("card-pane-subtle");
            row.setPadding(new Insets(8, 12, 8, 12));
            Label name = new Label(i.getName());
            name.getStyleClass().add("table-cell-title");
            Label detail = new Label(String.format("balance %.0f %s · reorder at %.0f · cost %s",
                    qty, i.getUnit(), i.getReorderLevel(), fmt(cur, i.getPurchaseRate() > 0 ? i.getPurchaseRate() : i.getRate())));
            detail.getStyleClass().add("text-muted");
            Region sp = new Region();
            HBox.setHgrow(sp, Priority.ALWAYS);
            Label pill = UiTheme.statusPill("Reorder Now", "danger");
            row.getChildren().addAll(name, sp, detail, pill);
            lowStockBox.getChildren().add(row);
        }
        if (!any) {
            Label ok = new Label("✓ All items above reorder levels.");
            ok.getStyleClass().add("accent-emerald");
            lowStockBox.getChildren().add(ok);
        }
    }

    private static String fmt(String cur, double v) {
        return String.format("%s%.2f", cur, v);
    }

    private static String fmtQty(double q) {
        return q == Math.floor(q) ? String.format("%.0f", q) : String.format("%.2f", q);
    }
}
