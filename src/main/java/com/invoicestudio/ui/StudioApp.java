package com.invoicestudio.ui;

import com.invoicestudio.service.AppLog;
import com.invoicestudio.db.DatabaseManager;
import com.invoicestudio.model.*;
import com.invoicestudio.service.*;
import com.invoicestudio.ui.auth.AuthView;
import com.invoicestudio.ui.auth.LogoutDialog;
import com.invoicestudio.ui.views.*;
import javafx.collections.ListChangeListener;
import javafx.animation.FadeTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.Window;
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
 * Production-hardened shell (skill rule 5.2 role 1 — coordination only):
 * - Sidebar navigation (VS Code style) with active gold indicator + hover states
 *   (built by {@link SidebarController}, user pill by {@link UserProfilePill}).
 * - Shortcuts live in {@link AppShortcuts}.
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

    private DataManager data;
    private BackupRestoreService backupService;
    private PrintingService printingService;

    private final Map<String, Node> viewCache = new HashMap<>();

    /**
     * Per-view freshness (stale-while-revalidate): each view remembers the
     * data epoch it last read at, so a refresh of view A can never mark
     * view B fresh — the stale-after-edit-elsewhere bug the single global
     * lastDataEpoch used to have.
     */
    private final ViewEpochTracker viewEpochs = new ViewEpochTracker();
    private Label refreshPill;
    private boolean refreshInProgress;
    /** Refreshes queued while another was mid-flight: view id → its refresher. */
    private final java.util.Map<String, Runnable> pendingRefreshers = new java.util.LinkedHashMap<>();

    private final SidebarController sidebarController = new SidebarController(this);
    private final AppShortcuts shortcuts = new AppShortcuts(this);
    private UserProfilePill userProfilePill;

    /** Single background worker for DB-touching tasks (SQLite is single-writer anyway). */
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "invoicestudio-db");
        t.setDaemon(true);
        return t;
    });

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

        mainLayout.setLeft(sidebarController.buildSidebar());

        rootPane.getChildren().add(mainLayout);
        installChatbot();

        Scene scene = new Scene(rootPane, 1440, 900);
        shortcuts.installGlobalShortcuts(scene);
        String css = getClass().getResource("/css/globalfile.css") != null
                ? getClass().getResource("/css/globalfile.css").toExternalForm()
                : null;
        if (css != null) {
            scene.getStylesheets().add(css);
        }
        ThemeManager.getInstance().applyToScene(scene);

        stage.setScene(scene);
        stage.setTitle("InvoiceStudio — Bill Design & Print");
        stage.setMinWidth(1024);
        stage.setMinHeight(640);

        try {
            // Modern icon-only mark (redesigned from the square wordmark —
            // readable at taskbar/title-bar sizes). Wordmark PNGs stay for
            // label-artwork "Use App Logo" features.
            InputStream iconStream = getClass().getResourceAsStream("/icons/invoice-mark.png");
            if (iconStream == null) {
                iconStream = getClass().getResourceAsStream("/icons/Invoicewhitebackground.png");
            }
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception ignored) {
            AppLog.debug(ignored); }

        TitleBarTheme.init();
        new WindowStateManager().applyAndTrack(stage, 1440, 900, 1024, 640);
        stage.show();
        // OS title bar (icon + title + min/max/close strip) matches the dark
        // theme — OS-drawn chrome, so styled natively, not via CSS. Must run
        // AFTER show(): the native window exists only once the JavaFX peer is
        // created inside show(). The process-wide sweep also catches any
        // window already open; it is idempotent.
        TitleBarTheme.apply(stage);
        // Belt-and-braces: the native HWND can land a pulse after show() —
        // re-sweep once more (covers this window and every other one).
        Platform.runLater(TitleBarTheme::applyToAllProcessWindows);

        // Global icon safety net: EVERY window this app ever opens — Dialogs,
        // raw Stages, file pickers with title bars — inherits the InvoiceStudio
        // logo and dark title bar automatically.
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                for (Window w : change.getAddedSubList()) {
                    if (w instanceof Stage s) {
                        DialogHelper.styleStage(s);
                    }
                }
            }
        });

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
                        if (userProfilePill != null) userProfilePill.refresh();
                        // Seeding only runs once the user is authenticated with their userId context
                        dbExecutor.execute(() -> {
                            try {
                                data.seedIfEmpty();
                            } catch (Exception ignored) {
            AppLog.debug(ignored); }
                        });
                        showDashboardInternal();
                        checkRecurringSweepAsync();
                        // One-time: seed the expense-account registry from
                        // existing voucher payees (no-op after first run).
                        com.invoicestudio.service.ExpenseAccountService.backfillFromHistoryAsync(null);
                        // Pre-load every cached collection in the background so
                        // the first navigation to each view paints instantly.
                        // (No epoch bookkeeping needed here: each view records
                        // its own epoch when it is built, and cache warming is
                        // read-only so it never bumps the epoch.)
                        data.warmCachesAsync(dbExecutor, () -> { });
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
        // The MCP server must never outlive the app (localhost port + pending ops die with it)
        com.invoicestudio.mcp.McpServer.shutdown();
        com.invoicestudio.service.AppExecutors.shutdownAll();
        dbExecutor.shutdownNow();
    }

    // ------------------------------------------------------------------
    // Chatbot — floating icon + right-side overlay panel
    // ------------------------------------------------------------------

    private com.invoicestudio.service.ChatbotConfig chatbotCfg;
    private StackPane chatbotIcon;
    private ChatbotPanel chatbotPanel;

    /** The single chatbot-config instance the shell reacts to (Settings →
     *  Chatbot edits and saves THIS instance, so the icon/panel follow). */
    public com.invoicestudio.service.ChatbotConfig chatbotConfig() {
        if (chatbotCfg == null) chatbotCfg = com.invoicestudio.service.ChatbotConfig.load();
        return chatbotCfg;
    }

    /** Replaces the active chatbot config (Settings save / support hooks). */
    public void setChatbotConfig(com.invoicestudio.service.ChatbotConfig cfg) {
        this.chatbotCfg = cfg;
        refreshChatbotIcon();
        if (chatbotPanel != null) {
            chatbotPanel.refreshConfig();
        }
    }

    /** Adds the floating round chat icon (bottom-right), honoring the
     *  Settings → Chatbot show/hide switch. Safe to call again. */
    private void installChatbot() {
        chatbotCfg = com.invoicestudio.service.ChatbotConfig.load();
        if (chatbotIcon == null) {
            chatbotIcon = new StackPane(IconHelper.getIcon(IconHelper.ICON_CHAT, 24, "#F2EBDD"));
            chatbotIcon.setStyle("-fx-background-color: #D9A13B; -fx-background-radius: 26;"
                    + "-fx-border-color: #8a671f; -fx-border-radius: 26; -fx-border-width: 1;"
                    + "-fx-cursor: hand;");
            chatbotIcon.setMinSize(52, 52);
            chatbotIcon.setPrefSize(52, 52);
            chatbotIcon.setMaxSize(52, 52);
            chatbotIcon.setId("chatbot-fab");
            javafx.scene.control.Tooltip.install(chatbotIcon,
                    new javafx.scene.control.Tooltip("Chat with your business data"));
            StackPane.setAlignment(chatbotIcon, javafx.geometry.Pos.BOTTOM_RIGHT);
            StackPane.setMargin(chatbotIcon, new Insets(0, 18, 18, 0));
            chatbotIcon.setOnMouseClicked(e -> toggleChatbot());
            chatbotIcon.setOnMouseEntered(e -> chatbotIcon.setStyle(chatbotIcon.getStyle()
                    .replace("#D9A13B", "#E4B25B")));
            chatbotIcon.setOnMouseExited(e -> chatbotIcon.setStyle(chatbotIcon.getStyle()
                    .replace("#E4B25B", "#D9A13B")));
        }
        refreshChatbotIcon();
    }

    /** Shows/hides the floating icon per Settings → Chatbot (called on save). */
    public void refreshChatbotIcon() {
        if (rootPane == null) return;
        boolean show = chatbotCfg.isShowIcon();
        if (show && chatbotIcon.getParent() == null) {
            rootPane.getChildren().add(chatbotIcon);
        } else if (!show && chatbotIcon.getParent() != null) {
            rootPane.getChildren().remove(chatbotIcon);
        }
    }

    /** Opens (or closes) the chatbot overlay panel. */
    public void toggleChatbot() {
        if (chatbotPanel != null && chatbotPanel.getParent() != null) {
            rootPane.getChildren().remove(chatbotPanel);
            return;
        }
        if (chatbotCfg.getApiKey().isBlank()) {
            Toast.show(rootPane, "Chatbot not configured",
                    "Add your AI provider API key in Settings → Chatbot first.", true);
            showSettings();
            return;
        }
        chatbotPanel = new ChatbotPanel(this, chatbotConfig(),
                () -> rootPane.getChildren().remove(chatbotPanel));
        // Pin to the right edge and fill the shell height (tracks resizes).
        StackPane.setAlignment(chatbotPanel, javafx.geometry.Pos.CENTER_RIGHT);
        chatbotPanel.maxHeightProperty().bind(rootPane.heightProperty());
        chatbotPanel.setMaxWidth(Region.USE_PREF_SIZE);
        rootPane.getChildren().add(chatbotPanel);
    }

    // ------------------------------------------------------------------
    // Help overlay (F1) — overlay chrome, kept in the shell
    // ------------------------------------------------------------------

    void toggleShortcutsHelp() {
        // Remove existing overlay if present (toggle behavior)
        rootPane.getChildren().removeIf(n -> n instanceof ShortcutsDialog);
        ShortcutsDialog dlg = new ShortcutsDialog(() -> rootPane.getChildren().removeIf(n -> n instanceof ShortcutsDialog));
        rootPane.getChildren().add(dlg);
    }

    /** Create the shared data layer + long-lived services. Kept cheap: heavy DB work is deferred. */
    private void initServices() {
        DatabaseManager db = DatabaseManager.getInstance();
        data = DataManager.init(db);
        backupService = new BackupRestoreService(db);
        printingService = new PrintingService();
        startMcpIfConfigured();
    }

    /** Auto-start the MCP (AI access) server when the user enabled it in Settings. */
    private void startMcpIfConfigured() {
        try {
            com.invoicestudio.mcp.McpConfig cfg = com.invoicestudio.mcp.McpConfig.load();
            if (cfg.isAutoStart()) {
                String err = com.invoicestudio.mcp.McpServer.start(cfg);
                if (err != null) System.err.println("MCP auto-start failed: " + err);
            }
        } catch (Exception e) {
            System.err.println("MCP auto-start error: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // View switching with cache + fade transition
    // ------------------------------------------------------------------

    private void setView(String id, Node viewNode) {
        setView(id, viewNode, true);
    }

    private void setView(String id, Node viewNode, boolean animate) {
        sidebarController.updateNavActive(id);

        Node content = viewNode;
        if (content instanceof VBox && !(content instanceof SettingsView)) {
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

    /**
     * Cached views are refreshed (data re-read) but NOT rebuilt → instant nav.
     * Stale-while-revalidate: the cached UI is returned immediately; if the
     * data epoch moved since it was built, caches are re-warmed on the
     * background executor and only the refresher (data re-read) runs on the
     * FX thread afterwards — the click never waits on disk or a rebuild.
     */
    private Node cached(String id, java.util.function.Supplier<Node> factory, Runnable refresher) {
        Node view = viewCache.get(id);
        if (view == null) {
            view = factory.get();
            viewCache.put(id, view);
            // Freshly built = freshly read.
            viewEpochs.markRefreshed(id, data != null ? data.dataEpoch() : Long.MIN_VALUE);
        } else if (refresher != null && data != null
                && viewEpochs.needsRefresh(id, data.dataEpoch())) {
            refreshViewAsync(id, refresher);
        }
        return view;
    }

    /** Test/harness access to a cached view instance (null if not built yet). */
    public Node cachedView(String id) {
        return viewCache.get(id);
    }

    /** Warms caches off the FX thread, then re-reads data into the live view with a small pill indicator. */
    private void refreshViewAsync(String viewId, Runnable refresher) {
        if (refreshInProgress) {
            // Another refresh is mid-flight; queue this view so it is never
            // left stale (fast A→B navigation before A's refresh lands).
            pendingRefreshers.put(viewId, refresher);
            return;
        }
        refreshInProgress = true;
        showRefreshPill();
        dbExecutor.execute(() -> {
            try {
                data.warmCachesNow();
            } catch (Exception e) {
                AppLog.error("Cache re-warm failed", e);
            }
            Platform.runLater(() -> {
                try {
                    refresher.run();
                    viewEpochs.markRefreshed(viewId, data.dataEpoch());
                    for (var entry : pendingRefreshers.entrySet()) {
                        try {
                            entry.getValue().run();
                            viewEpochs.markRefreshed(entry.getKey(), data.dataEpoch());
                        } catch (Exception e) {
                            AppLog.error("Queued view refresh failed", e);
                        }
                    }
                    pendingRefreshers.clear();
                } catch (Exception e) {
                    AppLog.error("View refresh failed", e);
                } finally {
                    hideRefreshPill();
                    refreshInProgress = false;
                }
            });
        });
    }

    private void showRefreshPill() {
        if (refreshPill == null) {
            refreshPill = new Label("⟳  Refreshing…");
            refreshPill.getStyleClass().add("refresh-pill");
            refreshPill.setMouseTransparent(true);
            StackPane.setAlignment(refreshPill, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(refreshPill, new Insets(0, 18, 18, 0));
        }
        if (refreshPill.getParent() == null && rootPane != null) {
            rootPane.getChildren().add(refreshPill);
        }
        refreshPill.setVisible(true);
    }

    private void hideRefreshPill() {
        if (refreshPill != null) refreshPill.setVisible(false);
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

    public void showLabelHistory() {
        setView("labelhistory", cached("labelhistory",
                () -> new LabelHistoryView(this),
                () -> ((LabelHistoryView) viewCache.get("labelhistory")).reload()));
    }

    public void showTransactions() {
        setView("transactions", cached("transactions",
                () -> new TransactionsView(this),
                () -> ((TransactionsView) viewCache.get("transactions")).refresh()));
    }

    public void showPurchases() {
        setView("purchases", cached("purchases",
                () -> new PurchasesView(this),
                () -> ((PurchasesView) viewCache.get("purchases")).refresh()));
    }

    /** Record a new purchase bill (Tally F9: Purchase equivalent). */
    public void showCreatePurchase() {
        // Entry form holds unsaved state → always fresh.
        setView("purchases", new CreatePurchaseView(this));
    }

    /** Edit an existing purchase bill (stock ledger rows are rewritten on save). */
    public void showEditPurchase(com.invoicestudio.model.PurchaseBill bill) {
        setView("purchases", new CreatePurchaseView(this, bill));
    }

    public void showExpenses() {
        setView("expenses", cached("expenses",
                () -> new ExpensesView(this),
                () -> ((ExpensesView) viewCache.get("expenses")).refresh()));
    }

    public void showFinancials() {
        setView("financials", cached("financials",
                () -> new FinancialsView(this),
                () -> ((FinancialsView) viewCache.get("financials")).refresh()));
    }

    public void showStockAnalysis() {
        setView("stockanalysis", cached("stockanalysis",
                () -> new StockAnalysisView(this),
                () -> ((StockAnalysisView) viewCache.get("stockanalysis")).refresh()));
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
        switch (sidebarController.currentView()) {
            case "dashboard" -> showDashboard();
            case "dashboard2" -> showDashboard2();
            case "templates" -> showTemplates();
            case "history" -> showHistory();
            case "transactions" -> showTransactions();
            case "purchases" -> showPurchases();
            case "expenses" -> showExpenses();
            case "financials" -> showFinancials();
            case "reports" -> showReports();
            case "buyers" -> showBuyers();
            case "sellers" -> showSuppliers();
            case "items" -> showItems();
            case "stockanalysis" -> showStockAnalysis();
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
                        if ("dashboard".equals(sidebarController.currentView())) {
                            showDashboard();
                        }
                    });
                }
            } catch (Exception ignored) {
            AppLog.debug(ignored); }
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

    // --- Package-private surface for collaborators (skill rule 5.2) ---

    boolean hasData() { return data != null; }

    SidebarController sidebar() { return sidebarController; }

    StackPane mainContentPane() { return mainContentPane; }

    HBox newUserProfilePill() {
        userProfilePill = new UserProfilePill(this);
        return userProfilePill.node();
    }

    void promptLogout() {
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
            } catch (Exception ignored) {
            AppLog.debug(ignored); }
            AuthSessionManager.clear();
            Platform.runLater(() -> {
                viewCache.clear();
                viewEpochs.clear();
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
        if (userProfilePill != null) userProfilePill.refresh();
        rootPane.getChildren().clear();
        rootPane.getChildren().add(mainLayout);
        dbExecutor.execute(() -> {
            try {
                data.seedIfEmpty();
            } catch (Exception ignored) {
            AppLog.debug(ignored); }
            Platform.runLater(this::showDashboard);
        });
    }

    public static void main(String[] args) {
        System.setProperty("invoicestudio.init", "1");
        launch(args);
    }
}
