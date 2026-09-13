package com.invoicestudio.ui.views;

import com.invoicestudio.model.Bill;
import com.invoicestudio.model.Expense;
import com.invoicestudio.model.ItemRecord;
import com.invoicestudio.model.PurchaseBill;
import com.invoicestudio.model.Supplier;
import com.invoicestudio.service.FinancialService;
import com.invoicestudio.service.PurchaseService;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.UiTheme;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TableView;
import javafx.scene.control.TableColumn;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Financial Statements (Tally "Balance Sheet / P&L / GST reports").
 *
 * Four tabs: Profit & Loss (trading + net), Balance Sheet (liabilities vs
 * assets with difference line), GST Summary (output − ITC = net payable)
 * and a unified Daybook. All values computed by {@link FinancialService}
 * from the live data caches for the selected date range.
 */
public class FinancialsView extends BorderPane {

    private final StudioApp app;
    private final FinancialService service = new FinancialService();

    private final javafx.scene.control.DatePicker fromPicker = new javafx.scene.control.DatePicker();
    private final javafx.scene.control.DatePicker toPicker = new javafx.scene.control.DatePicker();

    private TabPane tabs;
    private VBox pnlBox;
    private VBox bsBox;
    private VBox gstBox;
    private TableView<FinancialService.DaybookEntry> daybookTable;

    public FinancialsView(StudioApp app) {
        this.app = app;

        setPadding(new Insets(24));
        getStyleClass().add("bg-app");

        setTop(createTopBar());
        setCenter(buildTabs());
        refresh();
    }

    public void refresh() {
        rebuildAll();
    }

    private Node createTopBar() {
        VBox box = new VBox(14);
        box.setPadding(new Insets(0, 0, 16, 0));

        HBox bar1 = new HBox(16);
        bar1.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label title = new Label("Financial Statements");
        title.getStyleClass().add("heading-l");
        Label subtitle = new Label("Trading & P&L account, balance sheet, GST summary and unified daybook.");
        subtitle.getStyleClass().add("view-subtitle");
        titleBox.getChildren().addAll(title, subtitle);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        // Default range: current financial year start (Apr 1) to today
        LocalDate today = LocalDate.now();
        LocalDate fyStart = today.getMonthValue() >= 4
                ? LocalDate.of(today.getYear(), 4, 1)
                : LocalDate.of(today.getYear() - 1, 4, 1);
        fromPicker.setValue(fyStart);
        toPicker.setValue(today);
        fromPicker.setPrefWidth(140);
        toPicker.setPrefWidth(140);
        fromPicker.setOnAction(e -> rebuildAll());
        toPicker.setOnAction(e -> rebuildAll());

        Button refreshBtn = UiTheme.secondaryBtn("Refresh");
        refreshBtn.setOnAction(e -> rebuildAll());

        bar1.getChildren().addAll(titleBox, sp, new Label("From:"), fromPicker, new Label("To:"), toPicker, refreshBtn);

        box.getChildren().addAll(bar1);
        return box;
    }

    private Node buildTabs() {
        tabs = new TabPane();
        tabs.getStyleClass().add("floating-tabs");
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        pnlBox = new VBox(8);
        bsBox = new VBox(8);
        gstBox = new VBox(8);
        daybookTable = new TableView<>();

        Tab pnlTab = new Tab("Profit & Loss", wrapScroll(pnlBox));
        Tab bsTab = new Tab("Balance Sheet", wrapScroll(bsBox));
        Tab gstTab = new Tab("GST Summary", wrapScroll(gstBox));
        Tab dayTab = new Tab("Daybook", daybookTable);

        tabs.getTabs().addAll(pnlTab, bsTab, gstTab, dayTab);
        return tabs;
    }

    private Node wrapScroll(VBox content) {
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("scroll-pane");
        return sp;
    }

    private void rebuildAll() {
        String from = fromPicker.getValue() != null ? fromPicker.getValue().toString() : "";
        String to = toPicker.getValue() != null ? toPicker.getValue().toString() : "";

        List<Bill> bills = app.getData().getAllBills();
        List<PurchaseBill> purchases = app.getData().getAllPurchases();
        List<Expense> expenses = app.getData().getAllExpenses();
        List<Supplier> suppliers = app.getData().getAllSuppliers();
        List<ItemRecord> items = app.getData().getAllItems();
        var stock = app.getData().getStockBalances();

        FinancialService.Financials f = service.compute(bills, purchases, expenses, suppliers, items, stock, from, to);
        String cur = app.getData().getSettings().getCurrency();

        rebuildPnl(f, cur);
        rebuildBalanceSheet(f, cur);
        rebuildGst(f, cur);
        rebuildDaybook(f);
    }

    // ------------------------------------------------------------------
    // P&L
    // ------------------------------------------------------------------

    private void rebuildPnl(FinancialService.Financials f, String cur) {
        pnlBox.getChildren().clear();

        VBox tradingCard = UiTheme.card(0);
        tradingCard.getChildren().addAll(
                section("TRADING ACCOUNT"),
                stmtRow("Sales Revenue", fmt(cur, f.salesRevenue()), false),
                stmtRow("Opening Stock", fmt(cur, f.openingStockValue()), true),
                stmtRow("Purchases (taxable)", fmt(cur, f.purchasesValue()), true),
                stmtRow("Direct Expenses", fmt(cur, f.directExpenses()), true),
                stmtRow("Closing Stock", fmt(cur, f.closingStockValue()), false),
                totalRow("GROSS PROFIT", fmt(cur, f.grossProfit()), f.grossProfit() >= 0 ? "accent-emerald" : "accent-red"),
                section("PROFIT & LOSS ACCOUNT"),
                stmtRow("Gross Profit b/f", fmt(cur, f.grossProfit()), false),
                stmtRow("Indirect Expenses", fmt(cur, f.indirectExpenses()), true),
                totalRow("NET PROFIT", fmt(cur, f.netProfit()), f.netProfit() >= 0 ? "accent-emerald" : "accent-red"),
                hint("GP = (Sales + Closing Stock) − (Opening Stock + Purchases + Direct Expenses)   ·   NP = GP − Indirect Expenses"));
        pnlBox.getChildren().add(tradingCard);
    }

    // ------------------------------------------------------------------
    // Balance Sheet
    // ------------------------------------------------------------------

    private void rebuildBalanceSheet(FinancialService.Financials f, String cur) {
        bsBox.getChildren().clear();

        HBox cols = new HBox(24);
        cols.setAlignment(Pos.TOP_CENTER);

        VBox liab = UiTheme.card(0);
        HBox.setHgrow(liab, Priority.ALWAYS);
        liab.getChildren().add(section("LIABILITIES (Sources of Funds)"));
        liab.getChildren().add(stmtRow("Sundry Creditors (Sellers)", fmt(cur, f.sundryCreditors()), false));
        liab.getChildren().add(stmtRow("Duties & Taxes (Net GST Payable)", fmt(cur, f.gstPayable()), false));
        liab.getChildren().add(stmtRow("Capital / Net Profit (accumulated)", fmt(cur, f.netProfit()), false));
        liab.getChildren().add(totalRow("TOTAL LIABILITIES", fmt(cur, f.totalLiabilities()), "accent-gold"));

        VBox assets = UiTheme.card(0);
        HBox.setHgrow(assets, Priority.ALWAYS);
        assets.getChildren().add(section("ASSETS (Application of Funds)"));
        assets.getChildren().add(stmtRow("Sundry Debtors (Buyers)", fmt(cur, f.sundryDebtors()), false));
        assets.getChildren().add(stmtRow("Closing Stock (Inventory)", fmt(cur, f.inventoryValue()), false));
        assets.getChildren().add(stmtRow("Cash in Hand (net position)", fmt(cur, f.cashInHand()), false));
        assets.getChildren().add(stmtRow("GST Credit in Hand (ITC > Output)", fmt(cur, Math.max(0, f.inputCredit() - f.outputGst())), false));
        assets.getChildren().add(totalRow("TOTAL ASSETS", fmt(cur, f.totalAssets()), "accent-gold"));

        cols.getChildren().addAll(liab, assets);
        bsBox.getChildren().add(cols);

        double diff = PurchaseService.round2(f.totalAssets() - f.totalLiabilities());
        Label note = diff == 0
                ? new Label("✓ Balanced — Assets equal Liabilities")
                : new Label(String.format("Difference: %s%.2f (capital account is derived from current-period profit; add opening capital in a future release to reconcile)", cur, diff));
        note.getStyleClass().add(diff == 0 ? "accent-emerald" : "text-muted");
        note.setWrapText(true);
        bsBox.getChildren().add(note);
    }

    // ------------------------------------------------------------------
    // GST
    // ------------------------------------------------------------------

    private void rebuildGst(FinancialService.Financials f, String cur) {
        gstBox.getChildren().clear();

        VBox gstCard = UiTheme.card(0);
        gstCard.getChildren().addAll(
                section("GSTR-1 (OUTWARD SUPPLIES — SALES)"),
                stmtRow("Taxable Sales Value", fmt(cur, f.salesRevenue()), false),
                stmtRow("Output GST (CGST+SGST+IGST)", fmt(cur, f.outputGst()), false),
                section("PURCHASE REGISTER / ITC (INWARD SUPPLIES)"),
                stmtRow("Taxable Purchase Value", fmt(cur, f.purchasesValue()), false),
                stmtRow("Eligible Input Tax Credit", fmt(cur, f.inputCredit()), false),
                section("GSTR-3B STYLE COMPUTATION"),
                stmtRow("Output GST", fmt(cur, f.outputGst()), false),
                stmtRow("Less: ITC", fmt(cur, f.inputCredit()), true),
                totalRow("NET TAX PAYABLE TO GOVT", fmt(cur, f.netTaxPayable()),
                        f.netTaxPayable() > 0 ? "accent-red" : "accent-emerald"));
        gstBox.getChildren().add(gstCard);
    }

    // ------------------------------------------------------------------
    // Daybook
    // ------------------------------------------------------------------

    private void rebuildDaybook(FinancialService.Financials f) {
        TableColumn<FinancialService.DaybookEntry, String> cDate = new TableColumn<>("Date");
        cDate.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().date()));
        cDate.setPrefWidth(95);

        TableColumn<FinancialService.DaybookEntry, String> cType = new TableColumn<>("Type");
        cType.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().type()));
        cType.setPrefWidth(90);

        TableColumn<FinancialService.DaybookEntry, String> cPart = new TableColumn<>("Particulars");
        cPart.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().particulars()));

        TableColumn<FinancialService.DaybookEntry, String> cIn = new TableColumn<>("Inflow (₹)");
        cIn.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().inflow() > 0 ? String.format("%.2f", d.getValue().inflow()) : ""));
        cIn.setPrefWidth(110);
        cIn.setStyle("-fx-alignment: CENTER-RIGHT;");

        TableColumn<FinancialService.DaybookEntry, String> cOut = new TableColumn<>("Outflow (₹)");
        cOut.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().outflow() > 0 ? String.format("%.2f", d.getValue().outflow()) : ""));
        cOut.setPrefWidth(110);
        cOut.setStyle("-fx-alignment: CENTER-RIGHT;");

        daybookTable.getColumns().setAll(cDate, cType, cPart, cIn, cOut);
        daybookTable.setItems(FXCollections.observableArrayList(f.daybook()));
        daybookTable.getStyleClass().add("table-view");
        daybookTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        daybookTable.setPlaceholder(UiTheme.emptyState("📔", "No Transactions in Range",
                "Sales, purchases, payments and expenses for the selected period appear here."));
    }

    // ------------------------------------------------------------------
    // Small styled builders
    // ------------------------------------------------------------------

    private Label section(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("section-eyebrow");
        l.setPadding(new Insets(10, 0, 2, 0));
        return l;
    }

    private Label hint(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("text-muted");
        l.setWrapText(true);
        l.setPadding(new Insets(6, 0, 0, 0));
        return l;
    }

    /** Ledger-style row: hairline separator below, no box/border. */
    private HBox stmtRow(String label, String value, boolean isDebit) {
        HBox h = new HBox();
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("ledger-row");
        h.setPadding(new Insets(8, 16, 8, 16));
        Label l = new Label((isDebit ? "Dr" : "Cr") + " · " + label);
        l.getStyleClass().add("table-cell-secondary");
        Label v = new Label(value);
        v.getStyleClass().add("table-cell-title");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        h.getChildren().addAll(l, sp, v);
        return h;
    }

    /** Emphasised total row with a top rule, staying inside the same card. */
    private HBox totalRow(String label, String value, String accent) {
        HBox h = new HBox();
        h.setAlignment(Pos.CENTER_LEFT);
        h.getStyleClass().add("ledger-total-row");
        h.setPadding(new Insets(9, 16, 9, 16));
        Label l = new Label(label);
        l.getStyleClass().add("table-cell-title");
        Label v = new Label(value);
        v.getStyleClass().addAll("table-cell-title", accent);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        h.getChildren().addAll(l, sp, v);
        return h;
    }

    private static String fmt(String cur, double v) {
        return String.format("%s%.2f", cur, v);
    }
}
