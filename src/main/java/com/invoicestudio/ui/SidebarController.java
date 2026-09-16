package com.invoicestudio.ui;

import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.HashMap;
import java.util.Map;

/**
 * Sidebar navigation for {@link StudioApp} (skill rule 5.2 role 2).
 * Owns nav buttons, the collapsed Catalog popup, and active-view highlighting.
 * View construction is delegated back to the shell's public navigation API.
 */
class SidebarController {

    private final StudioApp app;
    private final Map<String, Button> navButtons = new HashMap<>();
    private String currentView = "dashboard";

    SidebarController(StudioApp app) {
        this.app = app;
    }

    /** Id of the currently displayed top-level view. */
    String currentView() { return currentView; }

    boolean isDesignerActive() { return "designer".equals(currentView); }

    VBox buildSidebar() {
        VBox side = new VBox();
        side.getStyleClass().add("app-sidebar");

        // Brand block (click = dashboard)
        VBox brand = new VBox(2);
        brand.getStyleClass().add("sidebar-brand");
        brand.setAlignment(Pos.CENTER_LEFT);

        HBox brandRow = new HBox(10);
        brandRow.setAlignment(Pos.CENTER_LEFT);

        StackPane iconBox = new StackPane();
        iconBox.getStyleClass().add("brand-icon");
        iconBox.setPrefSize(34, 34);
        iconBox.setMaxSize(34, 34);
        Label iconLbl = IconHelper.createIconLabel(IconHelper.ICON_RECEIPT, 17, "#0B0E13");
        iconLbl.getStyleClass().add("brand-icon-glyph");
        iconBox.getChildren().add(iconLbl);

        VBox titleBox = new VBox(1);
        Label title = new Label("InvoiceStudio");
        title.getStyleClass().add("sidebar-brand-text");
        Label subtitle = new Label("BILL DESIGN & PRINT");
        subtitle.getStyleClass().add("sidebar-brand-sub");
        titleBox.getChildren().addAll(title, subtitle);

        brandRow.getChildren().addAll(iconBox, titleBox);
        brand.getChildren().add(brandRow);
        brand.setOnMouseClicked(e -> app.showDashboard());

        // Navigation
        VBox nav = new VBox(2);
        nav.getStyleClass().add("sidebar-nav");
        VBox.setVgrow(nav, Priority.NEVER);

        Label sectionMain = new Label("WORKSPACE");
        sectionMain.getStyleClass().add("sidebar-section-label");

        Label sectionSales = new Label("SALES");
        sectionSales.getStyleClass().add("sidebar-section-label");

        Label sectionPurchase = new Label("PURCHASE & EXPENSES");
        sectionPurchase.getStyleClass().add("sidebar-section-label");

        Label sectionFinance = new Label("INSIGHTS");
        sectionFinance.getStyleClass().add("sidebar-section-label");

        Label sectionManage = new Label("DIRECTORY & CATALOG");
        sectionManage.getStyleClass().add("sidebar-section-label");

        Label sectionSystem = new Label("SYSTEM");
        sectionSystem.getStyleClass().add("sidebar-section-label");

        nav.getChildren().add(sectionMain);
        addNavButton(nav, "dashboard", "Dashboard", IconHelper.ICON_DASHBOARD, app::showDashboard);

        nav.getChildren().add(sectionSales);
        addNavButton(nav, "history", "Invoices", IconHelper.ICON_HISTORY, app::showHistory);
        addNavButton(nav, "transactions", "Transactions", IconHelper.ICON_TRANSACTIONS, app::showTransactions);
        addNavButton(nav, "reports", "Reports & Ledger", IconHelper.ICON_REPORTS, app::showReports);

        nav.getChildren().add(sectionPurchase);
        addNavButton(nav, "purchases", "Purchases", IconHelper.ICON_BILLING, app::showPurchases);
        addNavButton(nav, "expenses", "Expenses", IconHelper.ICON_TAG, app::showExpenses);

        nav.getChildren().add(sectionFinance);
        addNavButton(nav, "financials", "Financials", IconHelper.ICON_BAR_CHART, app::showFinancials);
        addNavButton(nav, "stockanalysis", "Stock & Profit", IconHelper.ICON_TRENDING_UP, app::showStockAnalysis);

        nav.getChildren().add(sectionManage);
        // Directory & Catalog collapsed into ONE button to keep the sidebar short;
        // the 7 catalog destinations live in a themed popup (same icons/design).
        Button catalogBtn = new Button("Catalog");
        catalogBtn.setGraphic(IconHelper.getIcon(IconHelper.ICON_CATEGORIES, 15, "#94A3B8"));
        catalogBtn.getStyleClass().add("sidebar-nav-btn");
        catalogBtn.setMaxWidth(Double.MAX_VALUE);
        catalogBtn.setAlignment(Pos.CENTER_LEFT);
        catalogBtn.setTooltip(new Tooltip("Buyers, Sellers, Items, Categories, Templates, Transports, Variables & Label History"));
        catalogBtn.setOnAction(e -> showCatalogPopup(catalogBtn));
        navButtons.put("catalog", catalogBtn);
        nav.getChildren().add(catalogBtn);

        nav.getChildren().add(sectionSystem);
        addNavButton(nav, "settings", "Settings", IconHelper.ICON_SETTINGS, app::showSettings);

        // Push footer down
        Region filler = new Region();
        VBox.setVgrow(filler, Priority.ALWAYS);

        // Footer: + New Bill button + user profile pill (sidebar-footer border provides single divider above)
        VBox footer = new VBox(10);
        footer.getStyleClass().add("sidebar-footer");

        Button newBillBtn = new Button("+  New Bill");
        newBillBtn.getStyleClass().addAll("gold-btn", "sidebar-cta");
        newBillBtn.setMaxWidth(Double.MAX_VALUE);
        newBillBtn.setTooltip(new Tooltip("Create a new invoice (Ctrl+N)"));
        newBillBtn.setOnAction(e -> app.showCreateBill());

        HBox userPill = app.newUserProfilePill();

        Region midDivider = new Region();
        midDivider.setStyle("-fx-background-color: -color-border; -fx-pref-height: 1px; -fx-max-height: 1px;");
        VBox.setMargin(midDivider, new Insets(14, 0, 14, 0));

        footer.getChildren().addAll(newBillBtn, midDivider, userPill);

        side.getChildren().addAll(brand, nav, filler, footer);
        return side;
    }

    private void addNavButton(VBox container, String id, String label, String iconName, Runnable action) {
        Button btn = new Button(label);
        btn.setGraphic(IconHelper.getIcon(iconName, 15, "#94A3B8"));
        btn.getStyleClass().add("sidebar-nav-btn");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setAlignment(Pos.CENTER_LEFT);
        btn.setOnAction(e -> action.run());
        navButtons.put(id, btn);
        container.getChildren().add(btn);
    }

    void updateNavActive(String activeId) {
        this.currentView = activeId;
        // Views that live inside the collapsed Catalog popup light up the Catalog button.
        boolean catalogGroup = switch (activeId) {
            case "buyers", "sellers", "items", "categories", "templates", "transports", "variables", "labelhistory", "designer" -> true;
            default -> false;
        };
        for (Map.Entry<String, Button> entry : navButtons.entrySet()) {
            Button btn = entry.getValue();
            boolean isActive = entry.getKey().equalsIgnoreCase(activeId) ||
                    ("designer".equalsIgnoreCase(activeId) && "templates".equalsIgnoreCase(entry.getKey())) ||
                    ("dashboard2".equalsIgnoreCase(activeId) && "dashboard".equalsIgnoreCase(entry.getKey())) ||
                    (catalogGroup && "catalog".equalsIgnoreCase(entry.getKey()));
            btn.getStyleClass().remove("active");
            if (isActive) {
                btn.getStyleClass().add("active");
                btn.setGraphic(IconHelper.getIcon(navIconFor(entry.getKey()), 15, "#F2CA6B"));
            } else {
                btn.setGraphic(IconHelper.getIcon(navIconFor(entry.getKey()), 15, "#94A3B8"));
            }
        }
    }

    /** Catalog destinations shown inside the popup (id, label, icon). */
    private static final String[][] CATALOG_ITEMS = {
            {"buyers", "Buyers", "buyers"},
            {"sellers", "Sellers", "business"},
            {"items", "Items", "items"},
            {"categories", "Categories", "categories"},
            {"templates", "Templates", "templates"},
            {"transports", "Transports", "transport"},
            {"variables", "Variables", "variables"},
            {"labelhistory", "Label Print History", "history"}
    };

    /** Themed popup listing the Directory & Catalog destinations (same icons/design). */
    private void showCatalogPopup(Button anchor) {
        javafx.stage.Popup popup = new javafx.stage.Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);

        VBox panel = new VBox(4);
        panel.getStyleClass().add("catalog-popup");

        Label head = new Label("DIRECTORY & CATALOG");
        head.getStyleClass().add("sidebar-section-label");
        head.setStyle("-fx-padding: 2 8 8 8;");
        panel.getChildren().add(head);

        for (String[] item : CATALOG_ITEMS) {
            String id = item[0];
            String label = item[1];
            String iconKey = navIconFor(id);
            Button b = new Button(label);
            boolean active = id.equalsIgnoreCase(currentView)
                    || ("designer".equalsIgnoreCase(currentView) && "templates".equals(id));
            b.setGraphic(IconHelper.getIcon(iconKey, 15, active ? "#F2CA6B" : "#94A3B8"));
            b.getStyleClass().add("sidebar-nav-btn");
            if (active) b.getStyleClass().add("catalog-popup-item-active");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setAlignment(Pos.CENTER_LEFT);
            b.setOnAction(ev -> {
                popup.hide();
                switch (id) {
                    case "buyers" -> app.showBuyers();
                    case "sellers" -> app.showSuppliers();
                    case "items" -> app.showItems();
                    case "categories" -> app.showCategories();
                    case "templates" -> app.showTemplates();
                    case "transports" -> app.showTransports();
                    case "variables" -> app.showVariables();
                    case "labelhistory" -> app.showLabelHistory();
                }
            });
            panel.getChildren().add(b);
        }

        popup.getContent().add(panel);
        // Position below the anchor, aligned to its left edge
        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        popup.show(anchor, bounds.getMinX(), bounds.getMaxY() + 6);
    }

    private String navIconFor(String id) {
        return switch (id) {
            case "dashboard" -> IconHelper.ICON_DASHBOARD;
            case "dashboard2" -> IconHelper.ICON_DASHBOARD2;
            case "templates" -> IconHelper.ICON_TEMPLATES;
            case "new" -> IconHelper.ICON_RECEIPT;
            case "history" -> IconHelper.ICON_HISTORY;
            case "transactions" -> IconHelper.ICON_TRANSACTIONS;
            case "purchases" -> IconHelper.ICON_BILLING;
            case "expenses" -> IconHelper.ICON_TAG;
            case "financials" -> IconHelper.ICON_BAR_CHART;
            case "reports" -> IconHelper.ICON_REPORTS;
            case "buyers" -> IconHelper.ICON_USERS;
            case "sellers" -> IconHelper.ICON_BUSINESS;
            case "items" -> IconHelper.ICON_PACKAGE;
            case "stockanalysis" -> IconHelper.ICON_TRENDING_UP;
            case "categories" -> IconHelper.ICON_CATEGORIES;
            case "transports" -> IconHelper.ICON_TRANSPORT;
            case "variables" -> IconHelper.ICON_VARIABLE;
            case "labelhistory" -> IconHelper.ICON_HISTORY;
            case "settings" -> IconHelper.ICON_SETTINGS;
            default -> IconHelper.ICON_RECEIPT;
        };
    }
}
