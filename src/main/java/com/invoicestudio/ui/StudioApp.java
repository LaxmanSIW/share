package com.invoicestudio.ui;

import com.invoicestudio.db.DatabaseManager;
import com.invoicestudio.model.*;
import com.invoicestudio.service.*;
import com.invoicestudio.ui.auth.AuthView;
import com.invoicestudio.ui.auth.LogoutDialog;
import com.invoicestudio.ui.views.*;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * InvoiceStudio — application shell (v3, "Obsidian & Gold").
 *
 * Production-hardened shell:
 * - Sidebar navigation (VS Code style) with active gold indicator + hover states.
 * - View caching: heavy views are built once and refreshed on show, navigation
 *   is instant instead of rebuilding the whole scene graph per click.
 * - Fade transition between views.
 * - Window geometry persisted between runs (WindowStateManager).
 * - All shared DAO access via DataManager (single connection surface, bill cache).
 * - Startup work that touches the DB runs on a background executor.
 */
public class StudioApp extends Application {

    private Stage primaryStage;
    private StackPane rootPane;
    private BorderPane mainLayout;
    private StackPane mainContentPane;
    private VBox sidebar;

    private DataManager data;
    private BackupRestoreService backupService;
    private PrintingService printingService;

    private String currentView = "dashboard";
    private final Map<String, Button> navButtons = new HashMap<>();
    private final Map<String, Node> viewCache = new HashMap<>();
    private boolean sidebarCollapsed = false;

    private HBox userProfilePill;
    private Label userAvatarLabel;
    private Label userNameLabel;
    private Label userEmailLabel;

    /** Single background worker for DB-touching tasks (SQLite is single-writer anyway). */
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "invoicestudio-db");
        t.setDaemon(true);
        return t;
    });

    private record NavItem(String id, String label, String icon, Runnable action) {}

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        initServices();

        rootPane = new StackPane();
        rootPane.getStyleClass().add("root-container");

        mainLayout = new BorderPane();
        mainLayout.getStyleClass().add("main-layout");

        mainContentPane = new StackPane();
        mainContentPane.getStyleClass().add("content-area");
        mainLayout.setCenter(mainContentPane);

        sidebar = buildSidebar();
        mainLayout.setLeft(sidebar);

        rootPane.getChildren().add(mainLayout);

        Scene scene = new Scene(rootPane, 1440, 900);
        String css = getClass().getResource("/css/globalfile.css") != null
                ? getClass().getResource("/css/globalfile.css").toExternalForm()
                : null;
        if (css != null) {
            scene.getStylesheets().add(css);
        }

        stage.setScene(scene);
        stage.setTitle("InvoiceStudio — Bill Design & Print");
        stage.setMinWidth(1024);
        stage.setMinHeight(640);

        try {
            InputStream iconStream = getClass().getResourceAsStream("/icons/Invoicewhitebackground.png");
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception ignored) {}

        new WindowStateManager().applyAndTrack(stage, 1440, 900, 1024, 640);
        stage.show();

        // Build an empty shell immediately, then verify session & hydrate data in background:
        showLoading();
        dbExecutor.execute(() -> {
            try {
                UserSession session = data.auth().getActiveSession();
                if (session != null && session.isExpired()) {
                    try {
                        session = FirebaseAuthService.getInstance().refreshSession(session);
                        data.auth().updateTokens(session.getUserId(), session.getIdToken(), session.getRefreshToken(), session.getExpiresAtMillis());
                    } catch (Exception e) {
                        if (!session.isRememberMe()) {
                            session = null;
                        }
                    }
                }

                final UserSession finalSession = session;
                Platform.runLater(() -> {
                    if (finalSession != null) {
                        AuthSessionManager.setActiveSession(finalSession);
                        updateUserProfilePill();
                        // Seeding only runs once the user is authenticated with their userId context
                        dbExecutor.execute(() -> {
                            try {
                                data.seedIfEmpty();
                            } catch (Exception ignored) {}
                        });
                        showDashboardInternal();
                        checkRecurringSweepAsync();
                    } else {
                        showAuthScreen(AuthView.AuthState.SIGN_IN);
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showAuthScreen(AuthView.AuthState.SIGN_IN));
            }
        });
    }

    @Override
    public void stop() {
        dbExecutor.shutdownNow();
    }

    /** Create the shared data layer + long-lived services. Kept cheap: heavy DB work is deferred. */
    private void initServices() {
        DatabaseManager db = DatabaseManager.getInstance();
        data = DataManager.init(db);
        backupService = new BackupRestoreService(db);
        printingService = new PrintingService();
    }

    // ------------------------------------------------------------------
    // Sidebar
    // ------------------------------------------------------------------

    private VBox buildSidebar() {
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
        brand.setOnMouseClicked(e -> showDashboard());

        // Navigation
        VBox nav = new VBox(2);
        nav.getStyleClass().add("sidebar-nav");
        VBox.setVgrow(nav, Priority.NEVER);

        Label sectionMain = new Label("WORKSPACE");
        sectionMain.getStyleClass().add("sidebar-section-label");

        Label sectionFinance = new Label("FINANCE & LEDGER");
        sectionFinance.getStyleClass().add("sidebar-section-label");

        Label sectionManage = new Label("DIRECTORY & CATALOG");
        sectionManage.getStyleClass().add("sidebar-section-label");

        nav.getChildren().add(sectionMain);
        addNavButton(nav, "dashboard", "Dashboard", IconHelper.ICON_DASHBOARD, this::showDashboard);
        addNavButton(nav, "history", "History", IconHelper.ICON_HISTORY, this::showHistory);

        nav.getChildren().add(sectionFinance);
        addNavButton(nav, "transactions", "Transactions", IconHelper.ICON_TRANSACTIONS, this::showTransactions);
        addNavButton(nav, "reports", "Reports & Ledger", IconHelper.ICON_REPORTS, this::showReports);

        nav.getChildren().add(sectionManage);
        addNavButton(nav, "templates", "Templates", IconHelper.ICON_TEMPLATES, this::showTemplates);
        addNavButton(nav, "buyers", "Buyers", IconHelper.ICON_USERS, this::showBuyers);
        addNavButton(nav, "sellers", "Sellers", IconHelper.ICON_BUSINESS, this::showSuppliers);
        addNavButton(nav, "items", "Items", IconHelper.ICON_PACKAGE, this::showItems);
        addNavButton(nav, "categories", "Categories", IconHelper.ICON_CATEGORIES, this::showCategories);
        addNavButton(nav, "transports", "Transports", IconHelper.ICON_TRANSPORT, this::showTransports);
        addNavButton(nav, "variables", "Variables", IconHelper.ICON_VARIABLE, this::showVariables);
        addNavButton(nav, "settings", "Settings", IconHelper.ICON_SETTINGS, this::showSettings);

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
        newBillBtn.setOnAction(e -> showCreateBill());

        HBox userPill = buildUserProfilePill();

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

    private void updateNavActive(String activeId) {
        this.currentView = activeId;
        for (Map.Entry<String, Button> entry : navButtons.entrySet()) {
            Button btn = entry.getValue();
            boolean isActive = entry.getKey().equalsIgnoreCase(activeId) ||
                    ("designer".equalsIgnoreCase(activeId) && "templates".equalsIgnoreCase(entry.getKey())) ||
                    ("dashboard2".equalsIgnoreCase(activeId) && "dashboard".equalsIgnoreCase(entry.getKey()));
            btn.getStyleClass().remove("active");
            if (isActive) {
                btn.getStyleClass().add("active");
                btn.setGraphic(IconHelper.getIcon(navIconFor(entry.getKey()), 15, "#F2CA6B"));
            } else {
                btn.setGraphic(IconHelper.getIcon(navIconFor(entry.getKey()), 15, "#94A3B8"));
            }
        }
    }

    private String navIconFor(String id) {
        return switch (id) {
            case "dashboard" -> IconHelper.ICON_DASHBOARD;
            case "dashboard2" -> IconHelper.ICON_DASHBOARD2;
            case "templates" -> IconHelper.ICON_TEMPLATES;
            case "new" -> IconHelper.ICON_RECEIPT;
            case "history" -> IconHelper.ICON_HISTORY;
            case "transactions" -> IconHelper.ICON_TRANSACTIONS;
            case "reports" -> IconHelper.ICON_REPORTS;
            case "buyers" -> IconHelper.ICON_USERS;
            case "sellers" -> IconHelper.ICON_BUSINESS;
            case "items" -> IconHelper.ICON_PACKAGE;
            case "categories" -> IconHelper.ICON_CATEGORIES;
            case "transports" -> IconHelper.ICON_TRANSPORT;
            case "variables" -> IconHelper.ICON_VARIABLE;
            case "settings" -> IconHelper.ICON_SETTINGS;
            default -> IconHelper.ICON_RECEIPT;
        };
    }

    // ------------------------------------------------------------------
    // View switching with cache + fade transition
    // ------------------------------------------------------------------

    private void setView(String id, Node viewNode) {
        setView(id, viewNode, true);
    }

    private void setView(String id, Node viewNode, boolean animate) {
        updateNavActive(id);

        Node content = viewNode;
        if (content instanceof VBox) {
            // Wrap bare vertical lists in a scroll container so long content stays reachable.
            ScrollPane scroll = new ScrollPane(content);
            scroll.setFitToWidth(true);
            scroll.setFitToHeight(false);
            scroll.getStyleClass().add("scroll-pane");
            content = scroll;
        }

        mainContentPane.getChildren().setAll(content);

        if (animate) {
            content.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(150), content);
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.play();
        }
    }

    /** Cached views are refreshed (data re-read) but NOT rebuilt → instant nav. */
    private Node cached(String id, java.util.function.Supplier<Node> factory, Runnable refresher) {
        Node view = viewCache.get(id);
        if (view == null) {
            view = factory.get();
            viewCache.put(id, view);
        } else if (refresher != null) {
            try {
                refresher.run();
            } catch (Exception ignored) {}
        }
        return view;
    }

    private void showLoading() {
        VBox loading = new VBox(12);
        loading.setAlignment(Pos.CENTER);
        loading.getStyleClass().add("loading-pane");
        Label glyph = new Label("⌛");
        glyph.getStyleClass().add("loading-glyph");
        Label text = new Label("Preparing your workspace…");
        text.getStyleClass().add("loading-text");
        loading.getChildren().addAll(glyph, text);
        mainContentPane.getChildren().setAll(loading);
    }

    // ------------------------------------------------------------------
    // Public navigation API (used by all views)
    // ------------------------------------------------------------------

    public void showDashboard() {
        if (data == null) return;
        setView("dashboard", cached("dashboard",
                () -> new DashboardView(this),
                () -> ((DashboardView) viewCache.get("dashboard")).refresh()));
    }

    public void showDashboard2() {
        if (data == null) return;
        setView("dashboard2", cached("dashboard2",
                () -> new Dashboard2View(this),
                () -> ((Dashboard2View) viewCache.get("dashboard2")).refresh()));
    }

    private void showDashboardInternal() {
        setView("dashboard", cached("dashboard", () -> new DashboardView(this), null), false);
    }

    public void showTemplates() {
        setView("templates", cached("templates",
                () -> new TemplatesView(this),
                () -> ((TemplatesView) viewCache.get("templates")).refresh()));
    }

    public void showTemplateDesigner(Template template) {
        // Designer is stateful per template → always a fresh instance.
        setView("designer", new TemplateDesigner(this, template));
    }

    public void showCreateBill() {
        showCreateBill(null, null);
    }

    public void showCreateBill(String initialTemplateId) {
        showCreateBill(initialTemplateId, null);
    }

    public void showCreateBill(String initialTemplateId, ItemRecord initialItem) {
        // CreateBill holds unsaved form state → always fresh.
        setView("new", new CreateBillView(this, null, initialTemplateId, initialItem));
    }

    public void showHistory() {
        setView("history", cached("history",
                () -> new HistoryView(this),
                () -> ((HistoryView) viewCache.get("history")).refresh()));
    }

    public void showBuyers() {
        setView("buyers", cached("buyers",
                () -> new BuyersView(this),
                () -> ((BuyersView) viewCache.get("buyers")).refresh()));
    }

    public void showSuppliers() {
        setView("sellers", cached("sellers",
                () -> new SuppliersView(this),
                () -> ((SuppliersView) viewCache.get("sellers")).refresh()));
    }

    public void showItems() {
        setView("items", cached("items",
                () -> new ItemsView(this),
                () -> ((ItemsView) viewCache.get("items")).reload()));
    }

    public void showVariables() {
        setView("variables", cached("variables",
                () -> new VariablesView(this),
                () -> ((VariablesView) viewCache.get("variables")).reload()));
    }

    public void showTransactions() {
        setView("transactions", cached("transactions",
                () -> new TransactionsView(this),
                () -> ((TransactionsView) viewCache.get("transactions")).refresh()));
    }

    public void showReports() {
        showReportsForBuyer(null);
    }

    public void showReportsForBuyer(String buyerId) {
        setView("reports", cached("reports",
                () -> {
                    ReportsView rv = new ReportsView(this);
                    if (buyerId != null) rv.selectBuyerStatement(buyerId);
                    return rv;
                },
                () -> {
                    ReportsView rv = (ReportsView) viewCache.get("reports");
                    rv.refresh();
                    if (buyerId != null) rv.selectBuyerStatement(buyerId);
                }));
    }

    public void showTransports() {
        setView("transports", cached("transports",
                () -> new TransportsView(this),
                () -> ((TransportsView) viewCache.get("transports")).refresh()));
    }

    public void showCategories() {
        setView("categories", cached("categories",
                () -> new CategoriesView(this),
                () -> ((CategoriesView) viewCache.get("categories")).refresh()));
    }

    public void showSettings() {
        setView("settings", cached("settings",
                () -> new SettingsView(this),
                () -> ((SettingsView) viewCache.get("settings")).reload()));
    }

    public void editBill(Bill bill) {
        setView("new", new CreateBillView(this, bill, bill != null ? bill.getTemplateId() : null, null));
    }

    public void duplicateBill(Bill bill) {
        if (bill == null) {
            showCreateBill();
            return;
        }
        Settings settings = data.getSettings();
        Bill copy = new Bill();
        copy.setBillNo(BillingService.nextBillNo(settings));
        copy.setDate(BillingService.todayISO());
        copy.setDocType(bill.getDocType());
        copy.setStatus(BillStatus.UNPAID);
        copy.setTemplateId(bill.getTemplateId());
        copy.setNotes(bill.getNotes());
        copy.setBuyerName(bill.getBuyerName());
        copy.setVariables(new HashMap<>(bill.getVariables()));
        copy.setItems(new ArrayList<>(bill.getItems()));
        copy.setTotals(bill.getTotals());

        setView("new", new CreateBillView(this, copy, bill.getTemplateId(), null));
    }

    public void convertBill(Bill bill) {
        if (bill == null) {
            showCreateBill();
            return;
        }
        Settings settings = data.getSettings();
        Bill converted = new Bill();
        converted.setBillNo(BillingService.nextBillNo(settings));
        converted.setDate(BillingService.todayISO());
        converted.setDocType(DocType.INVOICE);
        converted.setStatus(BillStatus.UNPAID);
        converted.setTemplateId(bill.getTemplateId());
        converted.setNotes(bill.getNotes());
        converted.setBuyerName(bill.getBuyerName());
        converted.setVariables(new HashMap<>(bill.getVariables()));
        converted.setItems(new ArrayList<>(bill.getItems()));
        converted.setTotals(bill.getTotals());

        setView("new", new CreateBillView(this, converted, bill.getTemplateId(), null));
    }

    public void repeatBill(Bill bill) {
        if (bill == null) {
            showCreateBill();
            return;
        }
        Settings settings = data.getSettings();
        Bill next = BillingService.repeatBill(bill, settings);
        next.setBillNo(BillingService.nextBillNo(settings));

        setView("new", new CreateBillView(this, next, bill.getTemplateId(), null));
    }

    public void reloadAllData() {
        // Views refresh themselves on show now; just refresh the active one.
        switch (currentView) {
            case "dashboard" -> showDashboard();
            case "dashboard2" -> showDashboard2();
            case "templates" -> showTemplates();
            case "history" -> showHistory();
            case "transactions" -> showTransactions();
            case "reports" -> showReports();
            case "buyers" -> showBuyers();
            case "sellers" -> showSuppliers();
            case "items" -> showItems();
            case "categories" -> showCategories();
            case "transports" -> showTransports();
            case "variables" -> showVariables();
            case "settings" -> showSettings();
            default -> {}
        }
    }

    private void checkRecurringSweepAsync() {
        dbExecutor.execute(() -> {
            try {
                RecurringEngine engine = new RecurringEngine(data.getDb());
                RecurringEngine.SweepResult res = engine.runSweep(false);
                if (res != null && res.ran && res.created != null && !res.created.isEmpty()) {
                    Platform.runLater(() -> {
                        Toast.show(rootPane, "Recurring Invoices",
                                "Auto-created " + res.created.size() + " due recurring invoice(s).", false);
                        if ("dashboard".equals(currentView)) {
                            showDashboard();
                        }
                    });
                }
            } catch (Exception ignored) {}
        });
    }

    // ------------------------------------------------------------------
    // Accessors used by views
    // ------------------------------------------------------------------

    public DatabaseManager getDb() {
        return data != null ? data.getDb() : null;
    }

    public DataManager getData() {
        return data;
    }

    public BackupRestoreService getBackupService() {
        return backupService;
    }

    public PrintingService getPrintingService() {
        return printingService;
    }

    public RecurringEngine getRecurringEngine() {
        return new RecurringEngine(data.getDb());
    }

    public ExecutorService getDbExecutor() {
        return dbExecutor;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public Pane getRootPane() {
        return rootPane;
    }

    private HBox buildUserProfilePill() {
        HBox pill = new HBox(10);
        pill.setAlignment(Pos.CENTER_LEFT);
        pill.setStyle("-fx-background-color: #121721; -fx-background-radius: 8px; -fx-padding: 8px 10px; -fx-border-color: #1E2738; -fx-border-radius: 8px; -fx-border-width: 1px;");

        userAvatarLabel = new Label("IS");
        userAvatarLabel.setStyle("-fx-background-color: linear-gradient(to bottom right, #D4AF37, #AA820A); -fx-background-radius: 50%; -fx-min-width: 30px; -fx-min-height: 30px; -fx-max-width: 30px; -fx-max-height: 30px; -fx-alignment: center; -fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #0B0E13;");

        VBox textBox = new VBox(1);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        userNameLabel = new Label("Account");
        userNameLabel.setStyle("-fx-text-fill: #F8FAFC; -fx-font-size: 11.5px; -fx-font-weight: 600;");

        userEmailLabel = new Label("");
        userEmailLabel.setStyle("-fx-text-fill: #64748B; -fx-font-size: 10px;");

        textBox.getChildren().addAll(userNameLabel, userEmailLabel);

        Button logoutBtn = new Button();
        logoutBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 4px;");
        SVGPath logoutIcon = new SVGPath();
        logoutIcon.setContent("M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z");
        logoutIcon.setFill(Color.web("#94A3B8"));
        logoutIcon.setScaleX(0.7);
        logoutIcon.setScaleY(0.7);
        logoutBtn.setGraphic(logoutIcon);
        logoutBtn.setTooltip(new Tooltip("Log Out"));

        logoutBtn.setOnMouseEntered(e -> logoutIcon.setFill(Color.web("#EF4444")));
        logoutBtn.setOnMouseExited(e -> logoutIcon.setFill(Color.web("#94A3B8")));
        logoutBtn.setOnAction(e -> promptLogout());

        pill.getChildren().addAll(userAvatarLabel, textBox, logoutBtn);
        userProfilePill = pill;
        updateUserProfilePill();
        return pill;
    }

    private void updateUserProfilePill() {
        if (userNameLabel == null || userEmailLabel == null) return;
        String name = AuthSessionManager.getCurrentUserDisplayName();
        String email = AuthSessionManager.getCurrentUserEmail();

        userNameLabel.setText(name.isBlank() ? "Account" : name);
        userEmailLabel.setText(email);

        String initials = "IS";
        if (!name.isBlank() && !name.equalsIgnoreCase("User")) {
            String[] parts = name.trim().split("\\s+");
            if (parts.length >= 2) {
                initials = ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
            } else if (!parts[0].isEmpty()) {
                initials = parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
            }
        } else if (!email.isBlank()) {
            initials = email.substring(0, Math.min(2, email.length())).toUpperCase();
        }
        userAvatarLabel.setText(initials);
    }

    private void promptLogout() {
        LogoutDialog dialog = new LogoutDialog(
                () -> {
                    rootPane.getChildren().removeIf(node -> node instanceof LogoutDialog);
                    performLogout();
                },
                () -> rootPane.getChildren().removeIf(node -> node instanceof LogoutDialog)
        );
        rootPane.getChildren().add(dialog);
    }

    private void performLogout() {
        dbExecutor.execute(() -> {
            try {
                data.auth().clearSession();
            } catch (Exception ignored) {}
            AuthSessionManager.clear();
            Platform.runLater(() -> {
                viewCache.clear();
                showAuthScreen(AuthView.AuthState.LOGGED_OUT);
            });
        });
    }

    private void showAuthScreen(AuthView.AuthState state) {
        rootPane.getChildren().clear();
        AuthView authView = new AuthView(state, this::onAuthenticationSuccess);
        rootPane.getChildren().add(authView);
    }

    private void onAuthenticationSuccess() {
        updateUserProfilePill();
        rootPane.getChildren().clear();
        rootPane.getChildren().add(mainLayout);
        dbExecutor.execute(() -> {
            try {
                data.seedIfEmpty();
            } catch (Exception ignored) {}
            Platform.runLater(this::showDashboard);
        });
    }

    public static void main(String[] args) {
        System.setProperty("invoicestudio.init", "1");
        launch(args);
    }
}
