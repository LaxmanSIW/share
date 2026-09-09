package com.invoicestudio.ui;

import com.invoicestudio.db.*;
import com.invoicestudio.model.*;
import com.invoicestudio.service.*;
import com.invoicestudio.ui.views.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.*;

public class StudioApp extends Application {

    private Stage primaryStage;
    private StackPane rootPane;
    private BorderPane mainLayout;
    private StackPane mainContentPane;

    private DatabaseManager db;
    private BillDao billDao;
    private TemplateDao templateDao;
    private SettingsDao settingsDao;
    private BuyerDao buyerDao;
    private ItemDao itemDao;
    private VariableDao variableDao;
    private BackupRestoreService backupService;
    private PrintingService printingService;

    private String currentView = "dashboard";
    private final Map<String, Button> navButtons = new HashMap<>();

    @Override
    public void start(Stage stage) {
        this.primaryStage = stage;

        // 1. Initialize Database & DAOs
        initDatabase();

        // 2. Build Root Layout
        rootPane = new StackPane();
        rootPane.getStyleClass().add("root-container");
        rootPane.setStyle("-fx-background-color: #0B0E13;");

        mainLayout = new BorderPane();
        mainLayout.getStyleClass().add("main-layout");

        mainContentPane = new StackPane();
        mainContentPane.getStyleClass().add("content-area");
        mainLayout.setCenter(mainContentPane);

        // Header Navigation Bar
        HBox header = buildHeader();
        mainLayout.setTop(header);

        rootPane.getChildren().add(mainLayout);

        // 3. Create Scene & Attach Stylesheet
        Scene scene = new Scene(rootPane, 1280, 800);
        String css = getClass().getResource("/css/globalfile.css") != null
                ? getClass().getResource("/css/globalfile.css").toExternalForm()
                : null;
        if (css != null) {
            scene.getStylesheets().add(css);
        }

        stage.setScene(scene);
        stage.setTitle("InvoiceStudio — Bill Design & Print");
        stage.setMinWidth(1050);
        stage.setMinHeight(650);

        // Window Icon
        try {
            InputStream iconStream = getClass().getResourceAsStream("/icons/Invoicewhitebackground.png");
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            }
        } catch (Exception ignored) {}

        stage.show();

        // 4. Initial Navigation
        showDashboard();

        // 5. Run Auto-Recurring Sweep if enabled
        checkRecurringSweep();
    }

    private void initDatabase() {
        try {
            db = DatabaseManager.getInstance();
            billDao = new BillDao(db);
            templateDao = new TemplateDao(db);
            settingsDao = new SettingsDao(db);
            buyerDao = new BuyerDao(db);
            itemDao = new ItemDao(db);
            variableDao = new VariableDao(db);
            backupService = new BackupRestoreService(db);
            printingService = new PrintingService();

            // Seed default settings if empty
            if (settingsDao.getSettings() == null) {
                settingsDao.saveSettings(new Settings());
            }

            // Seed default preset templates if empty
            List<Template> existing = templateDao.getAllTemplates();
            if (existing.isEmpty()) {
                for (Template t : PresetTemplates.getAllPresets()) {
                    templateDao.saveTemplate(t);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private HBox buildHeader() {
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(10, 20, 10, 20));
        header.getStyleClass().add("app-header");
        header.setStyle("-fx-background-color: #0B0E13; -fx-border-color: #232B38; -fx-border-width: 0 0 1 0;");

        // Brand Logo & Title (Clicking returns to Dashboard)
        HBox brand = new HBox(10);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setStyle("-fx-cursor: hand;");
        brand.setOnMouseClicked(e -> showDashboard());

        StackPane iconBox = new StackPane();
        iconBox.setPrefSize(32, 32);
        iconBox.setMaxSize(32, 32);
        iconBox.setStyle("-fx-background-color: #D9A13B; -fx-background-radius: 6;");
        Label iconLbl = IconHelper.createIconLabel(IconHelper.ICON_RECEIPT, 16, "#0B0E13");
        iconBox.getChildren().add(iconLbl);

        VBox titleBox = new VBox(0);
        HBox titleRow = new HBox(0);
        Label title1 = new Label("Invoice");
        title1.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #F4F4F5;");
        Label title2 = new Label("Studio");
        title2.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #D9A13B;");
        titleRow.getChildren().addAll(title1, title2);

        Label subtitle = new Label("BILL DESIGN & PRINT");
        subtitle.setStyle("-fx-font-size: 9px; -fx-text-fill: #94A3B8; -fx-letter-spacing: 1.5; -fx-font-weight: bold;");
        titleBox.getChildren().addAll(titleRow, subtitle);

        brand.getChildren().addAll(iconBox, titleBox);

        // Center Navigation Tabs
        HBox navBar = new HBox(4);
        navBar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(navBar, Priority.ALWAYS);

        addNavButton(navBar, "dashboard", "Dashboard", IconHelper.ICON_DASHBOARD, this::showDashboard);
        addNavButton(navBar, "templates", "Templates", IconHelper.ICON_TEMPLATES, this::showTemplates);
        addNavButton(navBar, "new", "Create Bill", IconHelper.ICON_RECEIPT, this::showCreateBill);
        addNavButton(navBar, "history", "History", IconHelper.ICON_HISTORY, this::showHistory);
        addNavButton(navBar, "buyers", "Buyers", IconHelper.ICON_USERS, this::showBuyers);
        addNavButton(navBar, "items", "Items", IconHelper.ICON_PACKAGE, this::showItems);
        addNavButton(navBar, "variables", "Variables", IconHelper.ICON_VARIABLE, this::showVariables);
        addNavButton(navBar, "settings", "Settings", IconHelper.ICON_SETTINGS, this::showSettings);

        // Right Action: "+ New Bill"
        Button newBillBtn = new Button("+ New Bill");
        newBillBtn.getStyleClass().addAll("gold-btn");
        newBillBtn.setStyle("-fx-background-color: #D9A13B; -fx-text-fill: #0B0E13; -fx-font-weight: bold; " +
                "-fx-font-size: 12px; -fx-background-radius: 6; -fx-padding: 6 14; -fx-cursor: hand;");
        newBillBtn.setOnAction(e -> showCreateBill());

        header.getChildren().addAll(brand, navBar, newBillBtn);
        return header;
    }

    private void addNavButton(HBox container, String id, String label, String iconName, Runnable action) {
        Button btn = new Button(label);
        btn.setGraphic(IconHelper.getIcon(iconName, 14, "#94A3B8"));
        btn.getStyleClass().add("nav-button");
        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94A3B8; -fx-font-size: 12px; " +
                "-fx-font-weight: 600; -fx-padding: 6 12; -fx-background-radius: 6; -fx-cursor: hand;");

        btn.setOnAction(e -> action.run());

        navButtons.put(id, btn);
        container.getChildren().add(btn);
    }

    private void updateNavActive(String activeId) {
        this.currentView = activeId;
        for (Map.Entry<String, Button> entry : navButtons.entrySet()) {
            Button btn = entry.getValue();
            boolean isActive = entry.getKey().equalsIgnoreCase(activeId) ||
                    ("designer".equalsIgnoreCase(activeId) && "templates".equalsIgnoreCase(entry.getKey()));

            if (isActive) {
                btn.setStyle("-fx-background-color: #1E2738; -fx-text-fill: #F2CA6B; -fx-font-size: 12px; " +
                        "-fx-font-weight: bold; -fx-padding: 6 12; -fx-background-radius: 6; -fx-cursor: hand;");
                btn.getStyleClass().add("active");
            } else {
                btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #94A3B8; -fx-font-size: 12px; " +
                        "-fx-font-weight: 600; -fx-padding: 6 12; -fx-background-radius: 6; -fx-cursor: hand;");
                btn.getStyleClass().remove("active");
            }
        }
    }

    private void setView(String id, Node viewNode) {
        updateNavActive(id);
        if (viewNode instanceof VBox) {
            ScrollPane scroll = new ScrollPane(viewNode);
            scroll.setFitToWidth(true);
            scroll.setFitToHeight(false);
            scroll.getStyleClass().add("scroll-pane");
            scroll.setStyle("-fx-background-color: transparent; -fx-background: #0B0E13; -fx-border-color: transparent; -fx-padding: 0;");
            mainContentPane.getChildren().setAll(scroll);
        } else {
            mainContentPane.getChildren().setAll(viewNode);
        }
    }

    public void showDashboard() {
        setView("dashboard", new DashboardView(this));
    }

    public void showTemplates() {
        setView("templates", new TemplatesView(this));
    }

    public void showTemplateDesigner(Template template) {
        setView("designer", new TemplateDesigner(this, template));
    }

    public void showCreateBill() {
        showCreateBill(null, null);
    }

    public void showCreateBill(String initialTemplateId) {
        showCreateBill(initialTemplateId, null);
    }

    public void showCreateBill(String initialTemplateId, ItemRecord initialItem) {
        setView("new", new CreateBillView(this, null, initialTemplateId, initialItem));
    }

    public void showHistory() {
        setView("history", new HistoryView(this));
    }

    public void showBuyers() {
        setView("buyers", new BuyersView(this));
    }

    public void showItems() {
        String currency = settingsDao.getSettings() != null ? settingsDao.getSettings().getCurrency() : "₹";
        setView("items", new ItemsView(itemDao, billDao, currency, this::reloadAllData, item -> showCreateBill(null, item)));
    }

    public void showVariables() {
        setView("variables", new VariablesView(variableDao, settingsDao, this::reloadAllData));
    }

    public void showSettings() {
        setView("settings", new SettingsView(settingsDao, backupService, printingService, s -> {
            settingsDao.saveSettings(s);
            reloadAllData();
        }, this::reloadAllData));
    }

    public void editBill(Bill bill) {
        setView("new", new CreateBillView(this, bill, bill != null ? bill.getTemplateId() : null, null));
    }

    public void duplicateBill(Bill bill) {
        if (bill == null) {
            showCreateBill();
            return;
        }
        Settings settings = settingsDao.getSettings();
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
        Settings settings = settingsDao.getSettings();
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
        Settings settings = settingsDao.getSettings();
        Bill next = BillingService.repeatBill(bill, settings);
        next.setBillNo(BillingService.nextBillNo(settings));

        setView("new", new CreateBillView(this, next, bill.getTemplateId(), null));
    }

    public void reloadAllData() {
        switch (currentView) {
            case "dashboard" -> showDashboard();
            case "templates" -> showTemplates();
            case "history" -> showHistory();
            case "buyers" -> showBuyers();
            case "items" -> showItems();
            case "variables" -> showVariables();
            case "settings" -> showSettings();
            default -> {}
        }
    }

    private void checkRecurringSweep() {
        Platform.runLater(() -> {
            try {
                RecurringEngine engine = new RecurringEngine(db);
                RecurringEngine.SweepResult res = engine.runSweep(false);
                if (res != null && res.ran && res.created != null && !res.created.isEmpty()) {
                    Toast.show(rootPane, "Recurring Invoices",
                            "Auto-created " + res.created.size() + " due recurring invoice(s).", false);
                }
            } catch (Exception ignored) {}
        });
    }

    public DatabaseManager getDb() {
        return db;
    }

    public RecurringEngine getRecurringEngine() {
        return new RecurringEngine(db);
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public Pane getRootPane() {
        return rootPane;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
