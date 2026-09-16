package com.invoicestudio.ui.views;

import com.invoicestudio.model.Buyer;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.service.AppFormatters;
import com.invoicestudio.ui.UiTheme;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

import java.text.DecimalFormat;

/**
 * Reports &amp; Analytics View — Version 4.0.0 (Redesigned)
 * Cohesive Obsidian &amp; Gold executive aesthetic.
 * Fully unified accounting engine integrating all Bills and Transactions.
 *
 * 1. Outstanding &amp; Aging Report (with KPIs, search, risk indicators, statement drilldown)
 * 2. Buyer Statement &amp; Ledger (Opening balance, Debits, Credits, Running balance, CSV &amp; Print)
 * 3. Trouser Movement (Daily, weekly, and monthly velocity across CC and CS books)
 * 4. Sales Trends (Monthly billed sales vs collection rate efficiency)
 * 5. Item Movement (Catalog item sales velocity across all invoices)
 * 6. Item Performance (Top revenue and volume items)
 * 7. Category Breakdown (Category revenue &amp; pieces share)
 * 8. GST / Tax Summary (Taxable, CGST, SGST, IGST, and GSTR-1 CSV export)
 * 9. Transport Performance (Freight and parcel analysis per transporter)
 *
 * Shell role (skill rule 5.2): owns header, tab navigation and cross-view
 * selection; all tab content is built by {@link ReportsBuilders}.
 */
public class ReportsView extends BorderPane {

    private final StudioApp app;
    private final TabPane tabPane = new TabPane();
    /** Shared cached Indian-grouped money format (skill 2.1), handed to the tab builders. */
    private final DecimalFormat currencyFmt = AppFormatters.inrFormat();
    private final ReportsBuilders builders;

    public ReportsView(StudioApp app) {
        this.app = app;
        this.builders = new ReportsBuilders(this, app, currencyFmt);
        setPadding(new Insets(18, 24, 20, 24));
        getStyleClass().add("bg-app");

        setTop(createHeader());
        setCenter(createTabPane());
    }

    public void refresh() {
        int selIdx = tabPane.getSelectionModel().getSelectedIndex();
        buildTabs();
        tabPane.getSelectionModel().select(Math.max(0, selIdx));
    }

    public void selectBuyerStatement(String buyerId) {
        tabPane.getSelectionModel().select(1); // tab 1 is Buyer Statement
        ComboBox<Buyer> combo = builders.statementBuyerCombo();
        if (combo != null && buyerId != null) {
            for (Buyer b : combo.getItems()) {
                if (buyerId.equalsIgnoreCase(b.getId())) {
                    combo.setValue(b);
                    break;
                }
            }
        }
    }

    private Node createHeader() {
        HBox box = new HBox(16);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(0, 0, 16, 0));

        VBox titleBox = new VBox(2);
        Label title = new Label("Reports & Financial Ledger");
        title.getStyleClass().add("heading-l");
        Label subtitle = new Label("Unified accounting analytics, buyer statements, aging analysis, and logistics performance.");
        subtitle.getStyleClass().add("kpi-subtext");
        titleBox.getChildren().addAll(title, subtitle);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button refreshBtn = UiTheme.secondaryBtn("↻ Refresh Data");
        refreshBtn.setTooltip(new Tooltip("Reload latest bills and transactions"));
        refreshBtn.setOnAction(e -> refresh());

        Button newTxBtn = UiTheme.goldBtn("+ New Transaction");
        newTxBtn.setOnAction(e -> app.showTransactions());

        box.getChildren().addAll(titleBox, sp, refreshBtn, newTxBtn);
        return box;
    }

    private Node createTabPane() {
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add("clean-tab-pane");
        buildTabs();
        return tabPane;
    }

    private void buildTabs() {
        tabPane.getTabs().clear();
        tabPane.getTabs().add(new Tab("Outstanding Report", buildOutstandingReport()));
        tabPane.getTabs().add(new Tab("Buyer Statement & Ledger", buildBuyerStatementReport()));
        tabPane.getTabs().add(new Tab("Trouser Movement", buildTrouserMovementReport()));
        tabPane.getTabs().add(new Tab("Sales Trends", buildSalesTrendsReport()));
        tabPane.getTabs().add(new Tab("Item Sales & Movement", buildItemMovementReport()));
        tabPane.getTabs().add(new Tab("Category Breakdown", buildCategoryBreakdownReport()));
        tabPane.getTabs().add(new Tab("GST / Tax Summary", buildGstTaxReport()));
        tabPane.getTabs().add(new Tab("Transport Performance", buildTransportPerformanceReport()));
    }

    // --- Delegations to ReportsBuilders (behavior preserved) ---
    Node buildOutstandingReport() { return builders.buildOutstandingReport(); }
    Node buildBuyerStatementReport() { return builders.buildBuyerStatementReport(); }
    Node buildTrouserMovementReport() { return builders.buildTrouserMovementReport(); }
    Node buildSalesTrendsReport() { return builders.buildSalesTrendsReport(); }
    Node buildItemMovementReport() { return builders.buildItemMovementReport(); }
    Node buildCategoryBreakdownReport() { return builders.buildCategoryBreakdownReport(); }
    Node buildGstTaxReport() { return builders.buildGstTaxReport(); }
    Node buildTransportPerformanceReport() { return builders.buildTransportPerformanceReport(); }
}
