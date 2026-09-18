package com.invoicestudio.ui.views;

import com.invoicestudio.service.AppLog;
import com.invoicestudio.model.*;
import com.invoicestudio.service.AppExecutors;
import com.invoicestudio.service.AuthSessionManager;
import com.invoicestudio.service.BackupRestoreService;
import com.invoicestudio.service.FirebaseAuthService;
import com.invoicestudio.ui.DialogHelper;
import com.invoicestudio.ui.IconHelper;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.Toast;
import com.invoicestudio.ui.UiTheme;
import com.invoicestudio.ui.auth.PasswordFieldWithToggle;
import com.invoicestudio.ui.auth.PasswordStrengthMeter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import javafx.scene.text.Font;

/**
 * Settings — v3 redesign.
 * All sections preserved: business profile + logo, bank details, billing
 * preferences, buyer custom fields, custom fonts (Google / local files),
 * print calibration, backup & restore, storage info.
 * FIX: expandable textareas no longer render with a white background inside
 * the dark theme.
 */
public class SettingsView extends VBox {

    private final StudioApp app;

    private Settings currentSettings;

    // Firebase details section
    private final VBox firebaseAccountSection = new VBox();

    // Business inputs
    private final TextField busName = new TextField();
    private final TextField busGstin = new TextField();
    private final TextArea busAddress = new TextArea();
    private final TextField busPhone = new TextField();
    private final TextField busEmail = new TextField();
    private final TextField busState = new TextField();
    private final TextField busStateCode = new TextField();
    private final TextArea busTerms = new TextArea();

    // Logo
    private String currentLogoBase64 = "";
    private final ImageView logoImageView = new ImageView();
    private final StackPane logoContainer = new StackPane();
    private final Button removeLogoBtn = new Button("✕");

    // Bank inputs
    private final TextField bankName = new TextField();
    private final TextField bankAccount = new TextField();
    private final TextField bankIfsc = new TextField();
    private final TextField bankUpi = new TextField();

    // Billing Prefs
    private final TextField currencyField = new TextField();
    private final TextField prefixField = new TextField();
    private final TextField nextNoField = new TextField();
    private final Spinner<Integer> digitsSpinner = new Spinner<>(1, 8, 4);
    private final CheckBox interStateBox = new CheckBox("Inter-state supplies (IGST default)");
    private final CheckBox autoRecurringBox = new CheckBox("Auto-create recurring invoices on startup");
    private final CheckBox monochromePrintBox = new CheckBox("Monochrome / B&W Xerox Print Mode (Optimized for photocopiers & laser printers)");

    // Print calibration
    private final TextField offsetXField = new TextField();
    private final TextField offsetYField = new TextField();
    private final CheckBox statusStampBox = new CheckBox("Status stamp on print (PAID / CANCELLED)");

    // Thermal label (TSC/TSPL) brightness threshold — sharp black & white cut
    private final javafx.scene.control.Slider barcodeThresholdSlider = new javafx.scene.control.Slider(0, 255, 150);
    private final Label barcodeThresholdValue = new Label("150");
    private final Label barcodeThresholdHint = new Label();
    private final javafx.scene.canvas.Canvas thresholdRamp = new javafx.scene.canvas.Canvas(256, 22);

    // Buyer custom fields
    private final VBox buyerFieldsList = new VBox(6);
    private final TextField newBuyerFieldLabel = new TextField();
    private final ComboBox<String> newBuyerFieldType = new ComboBox<>();
    private final List<BuyerFieldDef> editableBuyerFields = new ArrayList<>();

    // Custom fonts
    private final VBox customFontsList = new VBox(6);
    private final TextField googleFontInput = new TextField();
    private final ComboBox<String> googleFontCat = new ComboBox<>();
    private final List<CustomFontDef> editableFonts = new ArrayList<>();

    private final TabPane tabPane = new TabPane();

    public SettingsView(StudioApp app) {
        this.app = app;

        setSpacing(16);
        setPadding(new Insets(20, 24, 20, 24));
        getStyleClass().add("view-page");

        Node header = buildHeader();

        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.getStyleClass().add("settings-tab-pane");
        VBox.setVgrow(tabPane, Priority.ALWAYS);

        buildTabs();

        getChildren().addAll(header, tabPane);

        AuthSessionManager.addSessionChangeListener(s -> Platform.runLater(this::refreshFirebaseAccountSection));

        reload();
    }

    private void buildTabs() {
        tabPane.getTabs().clear();
        tabPane.getTabs().addAll(
            createTab("Profile", IconHelper.ICON_BUSINESS, buildProfileTabContent()),
            createTab("Bank", IconHelper.ICON_BANK, buildBankSection()),
            createTab("Billing", IconHelper.ICON_BILLING, buildBillingPrefsSection()),
            createTab("Fields", IconHelper.ICON_FIELDS, buildBuyerFieldsSection()),
            createTab("Fonts", IconHelper.ICON_FONT, buildCustomFontsSection()),
            createTab("Print", IconHelper.ICON_PRINT, buildPrintSection()),
            createTab("Knowledge", IconHelper.ICON_HELP, new com.invoicestudio.ui.KnowledgeHubPanel()),
            createTab("Backup", IconHelper.ICON_BACKUP, buildBackupStorageSection()),
            createTab("Shortcuts", IconHelper.ICON_CODE, new com.invoicestudio.ui.ShortcutsPanel()),
            createTab("Chatbot", IconHelper.ICON_CHAT, new com.invoicestudio.ui.ChatbotSettingsPanel(
                    app, app.chatbotConfig())),
            createTab("MCP Server", IconHelper.ICON_MCP, new com.invoicestudio.mcp.McpSettingsPanel(
                    com.invoicestudio.mcp.McpConfig.load()))
        );
    }

    private Tab createTab(String title, String iconName, Node content) {
        Tab tab = new Tab(title);
        tab.setGraphic(IconHelper.createTabGraphic(iconName, tab.selectedProperty()));

        // Full-height multi-pane views (like Knowledge Hub) manage their own internal
        // scrolling and must occupy 100% of the tab viewport height.
        if (content instanceof com.invoicestudio.ui.KnowledgeHubPanel) {
            tab.setContent(content);
            return tab;
        }

        VBox wrapper = new VBox(content);
        wrapper.setPadding(new Insets(4, 6, 20, 4));
        VBox.setVgrow(content, Priority.NEVER);

        ScrollPane sp = new ScrollPane(wrapper);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("settings-scroll-pane");
        VBox.setVgrow(sp, Priority.ALWAYS);

        tab.setContent(sp);
        return tab;
    }

    private Node buildHeader() {
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label titleIcon = new Label("⚙");
        titleIcon.getStyleClass().addAll("icon-accent", "icon-lg");
        Label title = new Label("Settings");
        title.getStyleClass().add("view-title");
        titleRow.getChildren().addAll(titleIcon, title);

        Label sub = new Label("Business profile, tax mode, billing numbering, printing calibration & backups.");
        sub.getStyleClass().add("view-subtitle");
        titleBox.getChildren().addAll(titleRow, sub);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Button saveBtn = UiTheme.goldBtn("Save Settings");
        saveBtn.setGraphic(new Label("✓"));
        saveBtn.setTooltip(new Tooltip("Save all business profile and system preferences"));
        saveBtn.setOnAction(e -> saveSettings());

        header.getChildren().addAll(titleBox, saveBtn);
        return header;
    }

    private VBox buildProfileTabContent() {
        VBox container = new VBox(20);
        container.getChildren().addAll(
            buildFirebaseAccountSection(),
            buildBusinessSection()
        );
        return container;
    }

    private VBox buildFirebaseAccountSection() {
        refreshFirebaseAccountSection();
        return firebaseAccountSection;
    }

    private void refreshFirebaseAccountSection() {
        firebaseAccountSection.getChildren().clear();
        VBox card = UiTheme.card(16);

        UserSession session = AuthSessionManager.getActiveSession();
        if (session == null || !AuthSessionManager.isLoggedIn()) {
            Label secTitle = new Label("FIREBASE ACCOUNT & AUTHENTICATION");
            secTitle.getStyleClass().add("card-title");

            HBox guestBox = new HBox(12);
            guestBox.setAlignment(Pos.CENTER_LEFT);
            guestBox.getStyleClass().add("card-pane-subtle");
            guestBox.setPadding(new Insets(14));

            Label guestIcon = new Label("🔒");
            guestIcon.getStyleClass().add("icon-lg");

            VBox guestInfo = new VBox(4);
            Label guestTitle = new Label("Local Offline Guest Mode");
            guestTitle.getStyleClass().add("table-cell-title");
            Label guestSub = new Label("No authenticated Firebase account is currently active. Sign in to link your invoices and account profile.");
            guestSub.getStyleClass().add("muted-label");
            guestInfo.getChildren().addAll(guestTitle, guestSub);

            guestBox.getChildren().addAll(guestIcon, guestInfo);
            card.getChildren().addAll(secTitle, guestBox);
            firebaseAccountSection.getChildren().add(card);
            return;
        }

        // Header / title
        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label secIcon = new Label("👤");
        secIcon.getStyleClass().add("icon-accent");
        Label secTitle = new Label("FIREBASE ACCOUNT & AUTHENTICATION");
        secTitle.getStyleClass().add("card-title");
        titleRow.getChildren().addAll(secIcon, secTitle);

        // Profile banner row
        HBox banner = new HBox(16);
        banner.setAlignment(Pos.CENTER_LEFT);
        banner.getStyleClass().add("card-pane-subtle");
        banner.setPadding(new Insets(16));

        // Avatar circle
        StackPane avatar = new StackPane();
        avatar.setPrefSize(50, 50);
        avatar.setMinSize(50, 50);
        avatar.setMaxSize(50, 50);
        avatar.setStyle("-fx-background-color: #F2CA6B; -fx-background-radius: 25px;");

        String dName = session.getDisplayName();
        if (dName == null || dName.isBlank()) {
            dName = session.getEmail() != null && session.getEmail().contains("@")
                    ? session.getEmail().substring(0, session.getEmail().indexOf("@"))
                    : "User";
        }
        String initial = !dName.isEmpty() ? dName.substring(0, 1).toUpperCase() : "U";
        Label initialLbl = new Label(initial);
        initialLbl.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #0D1117;");
        avatar.getChildren().add(initialLbl);

        // User info
        VBox userInfo = new VBox(4);
        HBox nameAndStatus = new HBox(10);
        nameAndStatus.setAlignment(Pos.CENTER_LEFT);

        Label nameLbl = new Label(dName);
        nameLbl.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #F1F5F9;");

        Label statusPill = UiTheme.statusPill("Active Session", "success");
        nameAndStatus.getChildren().addAll(nameLbl, statusPill);

        Label emailLbl = new Label(session.getEmail() != null ? session.getEmail() : "No email registered");
        emailLbl.getStyleClass().add("muted-label");

        userInfo.getChildren().addAll(nameAndStatus, emailLbl);
        HBox.setHgrow(userInfo, Priority.ALWAYS);

        // Change password button
        Button changePassBtn = UiTheme.secondaryBtn("Change Password");
        changePassBtn.setGraphic(new Label("🔑"));
        changePassBtn.setTooltip(new Tooltip("Update your Firebase account login password"));
        changePassBtn.setOnAction(e -> openChangePasswordDialog(session));

        banner.getChildren().addAll(avatar, userInfo, changePassBtn);

        // Details grid
        GridPane detailsGrid = new GridPane();
        detailsGrid.setHgap(20);
        detailsGrid.setVgap(12);

        ColumnConstraints c1 = new ColumnConstraints();
        c1.setPercentWidth(50);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setPercentWidth(50);
        detailsGrid.getColumnConstraints().addAll(c1, c2);

        // Email address field (read only)
        TextField emailDisplay = new TextField(session.getEmail() != null ? session.getEmail() : "—");
        emailDisplay.setEditable(false);
        emailDisplay.setFocusTraversable(false);
        emailDisplay.setStyle("-fx-opacity: 0.9;");
        detailsGrid.add(UiTheme.labeled("Registered Firebase Email", emailDisplay), 0, 0);

        // User ID (UID) with Copy button
        HBox uidBox = new HBox(8);
        uidBox.setAlignment(Pos.CENTER_LEFT);
        TextField uidDisplay = new TextField(session.getUserId() != null ? session.getUserId() : "—");
        uidDisplay.setEditable(false);
        uidDisplay.setFocusTraversable(false);
        uidDisplay.setStyle("-fx-font-family: 'Consolas', monospace; -fx-opacity: 0.9;");
        HBox.setHgrow(uidDisplay, Priority.ALWAYS);

        Button copyUidBtn = UiTheme.iconBtn("📋", "Copy Firebase UID");
        copyUidBtn.setOnAction(e -> {
            if (session.getUserId() != null && !session.getUserId().isBlank()) {
                ClipboardContent content = new ClipboardContent();
                content.putString(session.getUserId());
                Clipboard.getSystemClipboard().setContent(content);
                Toast.show(this, "Copied", "Firebase UID copied to clipboard", false);
            }
        });
        uidBox.getChildren().addAll(uidDisplay, copyUidBtn);
        detailsGrid.add(UiTheme.labeled("Firebase User UID", uidBox), 1, 0);

        // Auth Provider
        TextField providerDisplay = new TextField("Firebase Identity Toolkit (Google Cloud)");
        providerDisplay.setEditable(false);
        providerDisplay.setFocusTraversable(false);
        providerDisplay.setStyle("-fx-opacity: 0.9;");
        detailsGrid.add(UiTheme.labeled("Authentication Provider", providerDisplay), 0, 1);

        // Session Persistence
        String mode = session.isRememberMe() ? "Persistent (Remember Me enabled)" : "Session Only";
        TextField sessionModeDisplay = new TextField(mode);
        sessionModeDisplay.setEditable(false);
        sessionModeDisplay.setFocusTraversable(false);
        sessionModeDisplay.setStyle("-fx-opacity: 0.9;");
        detailsGrid.add(UiTheme.labeled("Session Mode", sessionModeDisplay), 1, 1);

        card.getChildren().addAll(titleRow, banner, detailsGrid);
        firebaseAccountSection.getChildren().add(card);
    }

    private void openChangePasswordDialog(UserSession session) {
        if (session == null || session.getIdToken() == null || session.getIdToken().isBlank()) {
            Toast.show(this, "Authentication Error", "You must be logged in with a valid session to change password.", true);
            return;
        }

        Dialog<Boolean> dlg = new Dialog<>();
        dlg.setTitle("Change Password");
        dlg.setHeaderText("UPDATE ACCOUNT PASSWORD");

        VBox content = new VBox(14);
        content.setPrefWidth(380);

        Label desc = new Label("Enter your new password below. It will be securely updated in Firebase.");
        desc.getStyleClass().add("muted-label");
        desc.setWrapText(true);

        Label emailLabel = new Label("Account: " + (session.getEmail() != null ? session.getEmail() : "Current User"));
        emailLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #F2CA6B; -fx-font-size: 13px;");

        PasswordFieldWithToggle newPassField = new PasswordFieldWithToggle("Enter new password (min 6 chars)");
        PasswordStrengthMeter meter = new PasswordStrengthMeter();
        meter.bindToPassword(newPassField.textProperty());

        PasswordFieldWithToggle confirmPassField = new PasswordFieldWithToggle("Confirm new password");

        Label errorLbl = new Label();
        errorLbl.setStyle("-fx-text-fill: #EF4444; -fx-font-size: 12px;");
        errorLbl.setWrapText(true);
        errorLbl.setVisible(false);
        errorLbl.setManaged(false);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(20, 20);
        spinner.setVisible(false);
        spinner.setManaged(false);

        HBox statusRow = new HBox(8, spinner, errorLbl);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        content.getChildren().addAll(
            desc,
            emailLabel,
            UiTheme.labeled("New Password *", newPassField),
            meter,
            UiTheme.labeled("Confirm Password *", confirmPassField),
            statusRow
        );

        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Button okBtn = (Button) dlg.getDialogPane().lookupButton(ButtonType.OK);
        okBtn.setText("Update Password");
        okBtn.getStyleClass().add("gold-btn");

        Button cancelBtn = (Button) dlg.getDialogPane().lookupButton(ButtonType.CANCEL);
        cancelBtn.getStyleClass().add("button-secondary");

        okBtn.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();

            String np = newPassField.getText();
            String cp = confirmPassField.getText();

            if (np == null || np.trim().length() < 6) {
                errorLbl.setText("Password must be at least 6 characters long.");
                errorLbl.setVisible(true);
                errorLbl.setManaged(true);
                return;
            }

            if (!np.equals(cp)) {
                errorLbl.setText("Passwords do not match.");
                errorLbl.setVisible(true);
                errorLbl.setManaged(true);
                return;
            }

            okBtn.setDisable(true);
            cancelBtn.setDisable(true);
            spinner.setVisible(true);
            spinner.setManaged(true);
            errorLbl.setVisible(false);
            errorLbl.setManaged(false);

            AppExecutors.io().submit(() -> {
                try {
                    UserSession updated = FirebaseAuthService.getInstance().updatePassword(session.getIdToken(), np.trim());
                    if (updated != null) {
                        AuthSessionManager.setActiveSession(updated);
                    }
                    Platform.runLater(() -> {
                        dlg.setResult(true);
                        dlg.close();
                        Toast.show(this, "Success", "Password updated successfully!", false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        okBtn.setDisable(false);
                        cancelBtn.setDisable(false);
                        spinner.setVisible(false);
                        spinner.setManaged(false);
                        String msg = ex.getMessage() != null ? ex.getMessage() : "Failed to update password.";
                        errorLbl.setText(msg);
                        errorLbl.setVisible(true);
                        errorLbl.setManaged(true);
                    });
                }
            });
        });

        DialogHelper.styleDialog(dlg, 420, 380);
        dlg.showAndWait();
    }

    private VBox buildBusinessSection() {
        VBox card = UiTheme.card(14);

        Label secTitle = new Label("MY BUSINESS PROFILE");
        secTitle.getStyleClass().add("card-title");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(col1, col2);

        grid.add(UiTheme.labeled("Business Name *", busName), 0, 0);
        busGstin.textProperty().addListener((obs, o, v) -> {
            if (v != null && v.trim().length() >= 2 && busStateCode.getText().isBlank()) {
                String code = v.trim().substring(0, 2);
                if (code.matches("\\d{2}")) {
                    busStateCode.setText(code);
                }
            }
        });
        grid.add(UiTheme.labeled("GSTIN", busGstin), 1, 0);

        grid.add(createExpandableField("Registered Address", busAddress, "Street, building, area, city, pin code...", 3, 8), 0, 1, 2, 1);

        grid.add(UiTheme.labeled("Phone Number", busPhone), 0, 2);
        grid.add(UiTheme.labeled("Email Address", busEmail), 1, 2);

        grid.add(UiTheme.labeled("State (e.g. Maharashtra)", busState), 0, 3);
        grid.add(UiTheme.labeled("State Code (e.g. 27)", busStateCode), 1, 3);

        grid.add(createExpandableField("Default Terms & Conditions", busTerms, "1. Goods once sold will not be taken back...\n2. Interest @18% p.a. will be charged...", 4, 10), 0, 4, 2, 1);

        // Business Logo Box
        HBox logoRow = new HBox(16);
        logoRow.setAlignment(Pos.CENTER_LEFT);
        logoRow.getStyleClass().add("card-pane-subtle");
        logoRow.setPadding(new Insets(12));

        logoContainer.setPrefSize(100, 70);
        logoContainer.getStyleClass().add("logo-preview-box");
        logoImageView.setFitWidth(90);
        logoImageView.setFitHeight(60);
        logoImageView.setPreserveRatio(true);

        removeLogoBtn.getStyleClass().addAll("button-icon-subtle", "accent-red");
        removeLogoBtn.setOnAction(e -> clearLogo());

        logoContainer.getChildren().addAll(logoImageView);

        VBox logoInfo = new VBox(4);
        Label logoTitle = new Label("Business Logo");
        logoTitle.getStyleClass().add("table-cell-title");
        Label logoSub = new Label("Upload once here to appear on all invoices and template designs.");
        logoSub.getStyleClass().add("muted-label");

        HBox btnBox = new HBox(8);
        Button uploadLogoBtn = UiTheme.smallBtn("Upload Logo");
        uploadLogoBtn.setOnAction(e -> pickLogoFile());

        Button appLogoBtn = UiTheme.smallBtn("Use App Logo");
        appLogoBtn.setTooltip(new Tooltip("Set bundled InvoiceStudio logo as business logo"));
        appLogoBtn.setOnAction(e -> setAppLogo());

        btnBox.getChildren().addAll(uploadLogoBtn, appLogoBtn, removeLogoBtn);
        logoInfo.getChildren().addAll(logoTitle, logoSub, btnBox);

        logoRow.getChildren().addAll(logoContainer, logoInfo);

        card.getChildren().addAll(secTitle, grid, logoRow);
        return card;
    }

    private VBox buildBankSection() {
        VBox card = UiTheme.card(14);

        Label secTitle = new Label("BANK & PAYMENT DETAILS");
        secTitle.getStyleClass().add("card-title");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(col1, col2);

        grid.add(UiTheme.labeled("Bank Name", bankName), 0, 0);
        grid.add(UiTheme.labeled("Account Number", bankAccount), 1, 0);
        grid.add(UiTheme.labeled("IFSC Code", bankIfsc), 0, 1);
        grid.add(UiTheme.labeled("UPI ID (e.g. business@okaxis)", bankUpi), 1, 1);

        card.getChildren().addAll(secTitle, grid);
        return card;
    }

    private VBox buildBillingPrefsSection() {
        VBox card = UiTheme.card(14);

        HBox secTitleRow = new HBox(8);
        secTitleRow.setAlignment(Pos.CENTER_LEFT);
        Label secTitle = new Label("BILLING PREFERENCES & NUMBERING");
        secTitle.getStyleClass().add("card-title");
        secTitleRow.getChildren().add(secTitle);

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(25);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(25);
        ColumnConstraints col3 = new ColumnConstraints();
        col3.setPercentWidth(25);
        ColumnConstraints col4 = new ColumnConstraints();
        col4.setPercentWidth(25);
        grid.getColumnConstraints().addAll(col1, col2, col3, col4);

        prefixField.setPromptText("e.g. INV- or leave empty");
        digitsSpinner.setPrefWidth(120);

        Button helpBtn = new Button("?");
        helpBtn.getStyleClass().addAll("button-sm", "button-secondary");
        helpBtn.setTooltip(new Tooltip("How Bill Prefix and Zero-Padding Digits work"));
        helpBtn.setOnAction(e -> showBillNumberingHelp());

        HBox digitsLabelBox = new HBox(6);
        digitsLabelBox.setAlignment(Pos.CENTER_LEFT);
        Label lblDigits = new Label("Padding Digits");
        lblDigits.getStyleClass().add("field-label");
        digitsLabelBox.getChildren().addAll(lblDigits, helpBtn);

        VBox digitsBox = new VBox(4);
        digitsBox.getChildren().addAll(digitsLabelBox, digitsSpinner);

        grid.add(UiTheme.labeled("Currency Symbol", currencyField), 0, 0);
        grid.add(UiTheme.labeled("Bill No Prefix", prefixField), 1, 0);
        grid.add(UiTheme.labeled("Next Bill Number", nextNoField), 2, 0);
        grid.add(digitsBox, 3, 0);

        // Live Preview Row
        HBox previewRow = new HBox(10);
        previewRow.setAlignment(Pos.CENTER_LEFT);
        previewRow.getStyleClass().add("card-pane-subtle");
        previewRow.setPadding(new Insets(8, 12, 8, 12));

        Label previewTitle = new Label("Live Bill # Format Preview:");
        previewTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: #94A3B8; -fx-font-size: 11px;");
        Label previewBadge = new Label();
        previewBadge.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #F2CA6B;");

        Runnable updateLivePreview = () -> {
            String p = prefixField.getText() != null ? prefixField.getText().trim() : "";
            int num = 1;
            try { num = Integer.parseInt(nextNoField.getText().trim()); } catch (Exception ignored) {
            AppLog.debug(ignored); }
            int dig = digitsSpinner.getValue() != null ? digitsSpinner.getValue() : 4;
            String f = dig <= 1 ? p + num : String.format("%s%0" + dig + "d", p, num);
            previewBadge.setText(f);
        };
        prefixField.textProperty().addListener((o, ov, nv) -> updateLivePreview.run());
        nextNoField.textProperty().addListener((o, ov, nv) -> updateLivePreview.run());
        digitsSpinner.valueProperty().addListener((o, ov, nv) -> updateLivePreview.run());
        updateLivePreview.run();

        previewRow.getChildren().addAll(previewTitle, previewBadge);

        HBox toggles = new HBox(24);
        toggles.setAlignment(Pos.CENTER_LEFT);
        toggles.getChildren().addAll(interStateBox, autoRecurringBox, monochromePrintBox);

        card.getChildren().addAll(secTitleRow, grid, previewRow, toggles);
        return card;
    }

    private void showBillNumberingHelp() {
        Dialog<Void> dlg = new Dialog<>();
        dlg.setTitle("Invoice Numbering & Zero-Padding Help");
        dlg.setHeaderText("Configuring Bill Prefix and Leading Zeros");

        VBox content = new VBox(12);
        content.setPadding(new Insets(16));
        content.setPrefWidth(460);

        Label desc = new Label(
            "InvoiceStudio generates invoice numbers by combining the Prefix with the formatted Next Number.\n\n" +
            "• Prefix: Text placed before the number. Leave completely empty for raw numbers (e.g. 101), or use custom codes like 'INV-', 'BILL/', 'GST/26/'.\n\n" +
            "• Next Number: The starting or sequential counter for your next invoice.\n\n" +
            "• Padding Digits: Minimum number of digits for the numeric portion:\n" +
            "   - 1 digit (no padding): 1, 2, ..., 15, 100\n" +
            "   - 3 digits: 001, 002, ..., 015, 100\n" +
            "   - 4 digits: 0001, 0002, ..., 0015, 0100"
        );
        desc.setWrapText(true);

        String p = prefixField.getText() != null ? prefixField.getText().trim() : "";
        int num = 1;
        try { num = Integer.parseInt(nextNoField.getText().trim()); } catch (Exception ignored) {
            AppLog.debug(ignored); }
        int dig = digitsSpinner.getValue() != null ? digitsSpinner.getValue() : 4;
        String formatted = dig <= 1 ? p + num : String.format("%s%0" + dig + "d", p, num);

        Label previewLbl = new Label("Current Configuration Preview: " + formatted);
        previewLbl.setStyle("-fx-font-family: 'Consolas', monospace; -fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #F2CA6B; -fx-background-color: #151B26; -fx-padding: 8 12; -fx-background-radius: 6; -fx-border-color: #222F3E; -fx-border-radius: 6;");

        content.getChildren().addAll(desc, previewLbl);
        dlg.getDialogPane().setContent(content);
        dlg.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        DialogHelper.styleDialog(dlg);
        dlg.showAndWait();
    }

    private VBox buildBuyerFieldsSection() {
        VBox card = UiTheme.card(14);

        HBox head = new HBox(8);
        head.setAlignment(Pos.CENTER_LEFT);
        Label ic = new Label("👥");
        Label title = new Label("BUYER CUSTOM FIELDS");
        title.getStyleClass().add("card-title");
        head.getChildren().addAll(ic, title);

        Label sub = new Label("Add custom fields to your customers (e.g. Credit Limit, Payment Terms, Region, Sales Agent). They become variables ({{buyer_<key>}}) and extra CSV columns.");
        sub.getStyleClass().add("muted-label");
        sub.setWrapText(true);

        // Add row
        HBox addRow = new HBox(12);
        addRow.setAlignment(Pos.CENTER_LEFT);

        newBuyerFieldLabel.setPromptText("New field name (e.g. Region)");
        newBuyerFieldLabel.setPrefWidth(220);

        newBuyerFieldType.setItems(FXCollections.observableArrayList("text", "number", "date"));
        newBuyerFieldType.setValue("text");
        newBuyerFieldType.setPrefWidth(110);

        Button addBtn = UiTheme.smallBtn("Add Field");
        addBtn.setOnAction(e -> addBuyerField());

        addRow.getChildren().addAll(newBuyerFieldLabel, newBuyerFieldType, addBtn);

        card.getChildren().addAll(head, sub, buyerFieldsList, addRow);
        return card;
    }

    private void renderBuyerFieldsList() {
        buyerFieldsList.getChildren().clear();
        if (editableBuyerFields.isEmpty()) {
            Label empty = new Label("No custom buyer fields defined yet.");
            empty.getStyleClass().add("muted-label");
            buyerFieldsList.getChildren().add(empty);
            return;
        }

        for (BuyerFieldDef def : editableBuyerFields) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("card-pane-subtle");
            row.setPadding(new Insets(6, 12, 6, 12));

            TextField labelEdit = new TextField(def.getLabel());
            labelEdit.setPrefWidth(180);
            labelEdit.textProperty().addListener((obs, oldV, newV) -> def.setLabel(newV.trim()));

            Label keyPill = UiTheme.codePill("{{buyer_" + def.getKey() + "}}");
            keyPill.setPrefWidth(160);

            ComboBox<String> typeCb = new ComboBox<>(FXCollections.observableArrayList("text", "number", "date"));
            typeCb.setValue(def.getType() != null ? def.getType() : "text");
            typeCb.valueProperty().addListener((obs, oldV, newV) -> def.setType(newV));
            typeCb.setPrefWidth(100);

            HBox grow = new HBox();
            HBox.setHgrow(grow, Priority.ALWAYS);

            Button del = new Button("🗑");
            del.getStyleClass().add("button-icon-subtle");
            del.setOnAction(e -> {
                editableBuyerFields.remove(def);
                renderBuyerFieldsList();
            });

            row.getChildren().addAll(labelEdit, keyPill, typeCb, grow, del);
            buyerFieldsList.getChildren().add(row);
        }
    }

    private void addBuyerField() {
        String label = newBuyerFieldLabel.getText().trim();
        if (label.isEmpty()) return;
        if (editableBuyerFields.size() >= 12) {
            Toast.show(this, "Maximum 12 custom buyer fields allowed.", true);
            return;
        }
        String key = slugify(label);
        boolean exists = editableBuyerFields.stream().anyMatch(f -> f.getKey().equalsIgnoreCase(key));
        if (exists) {
            Toast.show(this, "A field with this name already exists.", true);
            return;
        }
        BuyerFieldDef bf = new BuyerFieldDef();
        bf.setKey(key);
        bf.setLabel(label);
        bf.setType(newBuyerFieldType.getValue() != null ? newBuyerFieldType.getValue() : "text");
        editableBuyerFields.add(bf);
        newBuyerFieldLabel.clear();
        renderBuyerFieldsList();
    }

    private VBox buildCustomFontsSection() {
        VBox card = UiTheme.card(14);

        HBox head = new HBox(8);
        head.setAlignment(Pos.CENTER_LEFT);
        Label ic = new Label("🔤");
        Label title = new Label("CUSTOM FONTS & TYPOGRAPHY");
        title.getStyleClass().add("card-title");
        head.getChildren().addAll(ic, title);

        Label sub = new Label("Add Google Web Fonts or load local .ttf / .otf font files from disk to use on your invoices and templates.");
        sub.getStyleClass().add("muted-label");
        sub.setWrapText(true);

        // Guide Box
        VBox guideBox = new VBox(8);
        guideBox.getStyleClass().add("card-pane-subtle");
        guideBox.setPadding(new Insets(12));

        Label guideTitle = new Label("HOW TO ADD & USE CUSTOM FONTS");
        guideTitle.getStyleClass().add("section-eyebrow");

        Label gInfo1 = new Label("1. Google Fonts / Web Links: Type any font name (e.g. Outfit, Cinzel, Inter, Fira Code, DM Sans) or paste a Google Fonts URL (e.g. https://fonts.google.com/specimen/Outfit). InvoiceStudio will parse the family name and register it.");
        gInfo1.getStyleClass().add("storage-note");
        gInfo1.setWrapText(true);

        Label gInfo2 = new Label("2. Local Font Files (.ttf / .otf): If you have downloaded font files on your computer, click 'Browse Local Font (.ttf/.otf)'. The desktop application loads and registers the font directly into JavaFX memory so you can preview and print with it instantly.");
        gInfo2.getStyleClass().add("storage-note");
        gInfo2.setWrapText(true);

        guideBox.getChildren().addAll(guideTitle, gInfo1, gInfo2);

        // Action Row
        HBox addRow = new HBox(10);
        addRow.setAlignment(Pos.CENTER_LEFT);

        googleFontInput.setPromptText("Font name or Google Fonts URL (e.g. Outfit)");
        googleFontInput.setPrefWidth(260);

        googleFontCat.setItems(FXCollections.observableArrayList("sans", "serif", "mono", "display", "handwriting"));
        googleFontCat.setValue("sans");
        googleFontCat.setPrefWidth(110);

        Button addBtn = UiTheme.smallBtn("Add Font Name / URL");
        addBtn.setOnAction(e -> addFont());

        Separator sep = new Separator(javafx.geometry.Orientation.VERTICAL);

        Button browseFileBtn = UiTheme.smallBtn("Browse Local Font (.ttf, .otf)...");
        browseFileBtn.setOnAction(e -> pickLocalFontFile());

        addRow.getChildren().addAll(googleFontInput, googleFontCat, addBtn, sep, browseFileBtn);

        card.getChildren().addAll(head, sub, guideBox, customFontsList, addRow);
        return card;
    }

    private void renderCustomFontsList() {
        customFontsList.getChildren().clear();
        if (editableFonts.isEmpty()) {
            Label empty = new Label("No custom fonts added yet. Standard system fonts (Inter, Roboto, Arial, Times, Segoe UI) are built-in.");
            empty.getStyleClass().add("muted-label");
            customFontsList.getChildren().add(empty);
            return;
        }

        for (CustomFontDef font : editableFonts) {
            HBox row = new HBox(12);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("card-pane-subtle");
            row.setPadding(new Insets(8, 12, 8, 12));

            VBox nameBox = new VBox(2);
            nameBox.setPrefWidth(170);
            Label name = new Label(font.getName());
            name.getStyleClass().add("table-cell-title");
            nameBox.getChildren().add(name);

            boolean isFile = "file".equalsIgnoreCase(font.getSource());
            if (isFile && font.getFileUrl() != null && !font.getFileUrl().isEmpty()) {
                File fl = new File(font.getFileUrl());
                Label pathLbl = new Label(fl.getName());
                pathLbl.getStyleClass().add("kpi-subtext");
                pathLbl.setTooltip(new Tooltip(font.getFileUrl()));
                nameBox.getChildren().add(pathLbl);
            }

            Label srcBadge = new Label(isFile ? "LOCAL FILE" : "GOOGLE FONT");
            srcBadge.getStyleClass().add(isFile ? "badge-source-file" : "badge-source-google");
            srcBadge.setPrefWidth(isFile ? 85 : 100);

            Label cat = new Label(font.getCategory() != null ? font.getCategory().toUpperCase() : "SANS");
            cat.getStyleClass().add("badge-neutral");
            cat.setPrefWidth(70);

            Label preview = new Label("ABCDEFGHIJKLM 1234567890");
            preview.setStyle("-fx-font-family: '" + font.getName() + "';"); // font family is data-driven, allowed
            preview.getStyleClass().addAll("table-cell-mono", "accent-gold");
            HBox.setHgrow(preview, Priority.ALWAYS);

            Button del = new Button("🗑");
            del.getStyleClass().add("button-icon-subtle");
            del.setTooltip(new Tooltip("Remove this font"));
            del.setOnAction(e -> {
                editableFonts.remove(font);
                renderCustomFontsList();
            });

            row.getChildren().addAll(nameBox, srcBadge, cat, preview, del);
            customFontsList.getChildren().add(row);
        }
    }

    private void addFont() {
        String input = googleFontInput.getText().trim();
        if (input.isEmpty()) return;

        String fontName = input;
        String fontUrl = "";
        // Auto-extract font name if user pasted a Google Fonts URL
        if (input.contains("fonts.google.com/specimen/")) {
            int idx = input.indexOf("fonts.google.com/specimen/");
            String rest = input.substring(idx + "fonts.google.com/specimen/".length());
            if (rest.contains("?")) rest = rest.substring(0, rest.indexOf("?"));
            if (rest.contains("/")) rest = rest.substring(0, rest.indexOf("/"));
            fontName = rest.replace("+", " ").trim();
            fontUrl = input;
        } else if (input.contains("family=")) {
            int idx = input.indexOf("family=");
            String rest = input.substring(idx + 7);
            if (rest.contains("&")) rest = rest.substring(0, rest.indexOf("&"));
            if (rest.contains(":")) rest = rest.substring(0, rest.indexOf(":"));
            fontName = rest.replace("+", " ").trim();
            fontUrl = input;
        }

        final String finalName = fontName;
        boolean exists = editableFonts.stream().anyMatch(f -> f.getName().equalsIgnoreCase(finalName));
        if (exists) {
            Toast.show(this, "Font already added: " + finalName, true);
            return;
        }

        CustomFontDef cf = new CustomFontDef();
        cf.setId("font_" + System.currentTimeMillis());
        cf.setName(finalName);
        cf.setFamily(finalName);
        cf.setCategory(googleFontCat.getValue() != null ? googleFontCat.getValue() : "sans");
        cf.setSource("google");
        if (!fontUrl.isEmpty()) cf.setUrl(fontUrl);

        editableFonts.add(cf);
        googleFontInput.clear();
        renderCustomFontsList();
        Toast.show(this, "Added font: " + finalName, false);
    }

    private void pickLocalFontFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Local Font File (.ttf or .otf)");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Font Files (*.ttf, *.otf)", "*.ttf", "*.otf", "*.TTF", "*.OTF"),
                new FileChooser.ExtensionFilter("TrueType Font (*.ttf)", "*.ttf", "*.TTF"),
                new FileChooser.ExtensionFilter("OpenType Font (*.otf)", "*.otf", "*.OTF")
        );
        File file = fc.showOpenDialog(getScene().getWindow());
        if (file != null) {
            try (FileInputStream fis = new FileInputStream(file)) {
                Font loadedFont = Font.loadFont(fis, 14.0);
                if (loadedFont != null) {
                    String fontName = loadedFont.getFamily();
                    if (fontName == null || fontName.isBlank()) fontName = loadedFont.getName();
                    if (fontName == null || fontName.isBlank()) {
                        fontName = file.getName().replaceAll("(?i)\\.(ttf|otf)$", "");
                    }

                    final String finalName = fontName;
                    boolean exists = editableFonts.stream().anyMatch(f -> f.getName().equalsIgnoreCase(finalName));
                    if (exists) {
                        Toast.show(this, "Font \"" + finalName + "\" is already in your font list.", true);
                        return;
                    }

                    CustomFontDef cf = new CustomFontDef();
                    cf.setId("font_" + System.currentTimeMillis());
                    cf.setName(finalName);
                    cf.setFamily(finalName);
                    cf.setSource("file");
                    cf.setFileUrl(file.getAbsolutePath());
                    cf.setFormat(file.getName().toLowerCase().endsWith(".otf") ? "otf" : "ttf");
                    cf.setCategory(googleFontCat.getValue() != null ? googleFontCat.getValue() : "sans");
                    editableFonts.add(cf);
                    renderCustomFontsList();
                    Toast.show(this, "Loaded local font: " + finalName, false);
                } else {
                    Toast.show(this, "Could not load font file. Please verify it is a valid .ttf or .otf file.", true);
                }
            } catch (Exception ex) {
                com.invoicestudio.service.AppLog.error(ex);
                Toast.show(this, "Error reading font file: " + ex.getMessage(), true);
            }
        }
    }

    /** The Print tab: calibration card + thermal (TSC/TSPL) quality card. */
    private Node buildPrintSection() {
        VBox container = new VBox(16);
        container.getChildren().addAll(buildPrintCalibrationSection(), buildBarcodeThresholdSection());
        return container;
    }

    /**
     * Brightness Threshold for the native TSC/TSPL label pipeline: the single
     * cut that turns every printed dot sharp black or sharp white. Persisted
     * in Settings; a live gray ramp shows exactly where the cut lands.
     */
    private VBox buildBarcodeThresholdSection() {
        VBox card = UiTheme.card(14);

        Label secTitle = new Label("THERMAL LABEL QUALITY — BRIGHTNESS THRESHOLD (TSC / TSPL)");
        secTitle.getStyleClass().add("card-title");

        Label sub = new Label("A thermal head has only two states: burn black or leave white. The threshold decides "
                + "which printed pixels become sharp black and which stay sharp white — there is never any gray in "
                + "between. Applies to every label sent to a TSC printer (TA210 etc.).");
        sub.getStyleClass().add("muted-label");
        sub.setWrapText(true);

        barcodeThresholdSlider.setShowTickMarks(true);
        barcodeThresholdSlider.setShowTickLabels(true);
        barcodeThresholdSlider.setMajorTickUnit(51);
        barcodeThresholdSlider.setMinorTickCount(4);
        barcodeThresholdSlider.setBlockIncrement(5);
        barcodeThresholdSlider.setSnapToTicks(false);
        barcodeThresholdSlider.setMaxWidth(340);
        barcodeThresholdSlider.valueProperty().addListener((obs, o, v) -> refreshThresholdPreview());

        barcodeThresholdValue.setStyle("-fx-text-fill: #d9a13b; -fx-font-weight: bold; -fx-font-size: 15px;");
        barcodeThresholdHint.getStyleClass().add("muted-label");
        barcodeThresholdHint.setWrapText(true);

        HBox sliderRow = new HBox(14);
        sliderRow.setAlignment(Pos.CENTER_LEFT);
        VBox sliderBox = new VBox(2, thresholdRamp, barcodeThresholdSlider);
        sliderRow.getChildren().addAll(sliderBox, barcodeThresholdValue);

        HBox legend = new HBox(20);
        legend.setAlignment(Pos.CENTER_LEFT);
        Label low = new Label("← lower: only true darks burn (crisper barcodes, lighter print)");
        low.getStyleClass().add("muted-label");
        Label high = new Label("higher: more pixels burn black (bolder, heavier) →");
        high.getStyleClass().add("muted-label");
        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);
        legend.getChildren().addAll(low, spring, high);

        card.getChildren().addAll(secTitle, sub, sliderRow, legend, barcodeThresholdHint);
        refreshThresholdPreview();
        return card;
    }

    /** Repaints the gray ramp + cut marker and the live hint text. */
    private void refreshThresholdPreview() {
        int t = (int) Math.round(barcodeThresholdSlider.getValue());
        barcodeThresholdValue.setText(String.valueOf(t));
        barcodeThresholdHint.setText("gray values ≤ " + t + " burn sharp black — everything above stays sharp white.");

        var gc = thresholdRamp.getGraphicsContext2D();
        double w = thresholdRamp.getWidth(), h = thresholdRamp.getHeight();
        gc.clearRect(0, 0, w, h);
        for (int x = 0; x < (int) w; x++) {
            int gray = x * 255 / Math.max(1, (int) w - 1); // black → white ramp
            gc.setFill(javafx.scene.paint.Color.rgb(gray, gray, gray));
            gc.fillRect(x, 0, 1, h);
        }
        double mx = t * (w - 1) / 255.0; // cut marker at the threshold position
        gc.setStroke(javafx.scene.paint.Color.web("#d9a13b"));
        gc.setLineWidth(2);
        gc.strokeLine(mx, 0, mx, h);
    }

    private VBox buildPrintCalibrationSection() {
        VBox card = UiTheme.card(14);

        Label secTitle = new Label("PRINT CALIBRATION & HARDWARE OFFSETS");
        secTitle.getStyleClass().add("card-title");

        Label sub = new Label("Adjust physical print placement (in millimeters) to align precisely with pre-printed stationary, letterhead, and thermal margins.");
        sub.getStyleClass().add("muted-label");
        sub.setWrapText(true);

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(col1, col2);

        grid.add(UiTheme.labeled("Horizontal Offset (mm, + moves right)", offsetXField), 0, 0);
        grid.add(UiTheme.labeled("Vertical Offset (mm, + moves down)", offsetYField), 1, 0);

        HBox bottomRow = new HBox(16);
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(statusStampBox, Priority.ALWAYS);

        Button calibSheetBtn = UiTheme.smallBtn("Print Calibration Sheet");
        calibSheetBtn.setOnAction(e -> runCalibrationPrint());

        bottomRow.getChildren().addAll(statusStampBox, calibSheetBtn);

        card.getChildren().addAll(secTitle, sub, grid, bottomRow);
        return card;
    }

    private void runCalibrationPrint() {
        try {
            double ox = 0;
            double oy = 0;
            try {
                ox = Double.parseDouble(offsetXField.getText().trim());
                oy = Double.parseDouble(offsetYField.getText().trim());
            } catch (Exception ignore) { AppLog.debug(ignore); }
            boolean ok = app.getPrintingService().printCalibrationSheet(ox, oy);
            if (ok) {
                Toast.show(this, "Calibration sheet sent to printer.", false);
            }
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
            Toast.show(this, "Print failed: " + e.getMessage(), true);
        }
    }

    private Node buildBackupStorageSection() {
        VBox container = new VBox(16);
        container.getChildren().addAll(
            buildBackupRestoreSection(),
            buildStorageInfoSection()
        );
        return container;
    }

    private VBox buildBackupRestoreSection() {
        VBox card = UiTheme.card(14);

        Label secTitle = new Label("DATABASE BACKUP & RESTORE");
        secTitle.getStyleClass().add("card-title");

        Label sub = new Label("Export your entire business database (bills, items, buyers, templates, settings) into a single portable JSON file, or restore from a previous backup.");
        sub.getStyleClass().add("muted-label");
        sub.setWrapText(true);

        HBox btnRow = new HBox(16);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        Button exportBtn = UiTheme.secondaryBtn("Export Backup JSON");
        exportBtn.setTooltip(new Tooltip("Export complete database backup to a JSON file"));
        exportBtn.setOnAction(e -> handleExportBackup());

        Button importBtn = UiTheme.secondaryBtn("Restore from Backup JSON...");
        importBtn.setTooltip(new Tooltip("Restore database from previously exported JSON backup"));
        importBtn.setOnAction(e -> handleRestoreBackup());

        btnRow.getChildren().addAll(exportBtn, importBtn);

        card.getChildren().addAll(secTitle, sub, btnRow);
        return card;
    }

    private void handleExportBackup() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Database Backup");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
        fc.setInitialFileName("InvoiceStudio_backup_" + System.currentTimeMillis() + ".json");
        File file = fc.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try {
                app.getBackupService().exportToFile(file);
                Toast.show(this, "Backup saved: " + file.getName(), false);
            } catch (Exception e) {
                com.invoicestudio.service.AppLog.error(e);
                Toast.show(this, "Backup failed: " + e.getMessage(), true);
            }
        }
    }

    private void handleRestoreBackup() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Backup File to Restore");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
        File file = fc.showOpenDialog(getScene().getWindow());
        if (file != null) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Confirm Restore");
            confirm.setHeaderText("OVERWRITE ALL EXISTING DATA?");
            confirm.setContentText("Restoring from " + file.getName() + " will replace all your current bills, templates, buyers, items, and settings. This cannot be undone!");
            DialogHelper.styleDialog(confirm);

            Optional<ButtonType> res = confirm.showAndWait();
            if (res.isPresent() && res.get() == ButtonType.OK) {
                try {
                    BackupRestoreService.RestoreResult r = app.getBackupService().restoreFromFile(file);
                    Toast.show(this, String.format("Restored: %d bills, %d buyers, %d items, %d templates",
                            r.billCount, r.buyerCount, r.itemCount, r.templateCount), false);
                    app.getData().invalidateBills();
                    app.getData().invalidateSettings();
                    reload();
                } catch (Exception e) {
                    com.invoicestudio.service.AppLog.error(e);
                    Toast.show(this, "Restore error: " + e.getMessage(), true);
                }
            }
        }
    }

    private VBox buildStorageInfoSection() {
        VBox card = new VBox(8);
        card.getStyleClass().add("card-pane-dashed");
        card.setPadding(new Insets(16));

        Label title = new Label("LOCAL EMBEDDED DATA STORAGE");
        title.getStyleClass().add("card-title");

        Label desc = new Label("InvoiceStudio runs entirely offline using a local SQLite database (share.db) with WAL mode enabled. No cloud connection or subscription required. Data is always private and stored locally on this machine.");
        desc.getStyleClass().add("storage-note");
        desc.setWrapText(true);

        card.getChildren().addAll(title, desc);
        return card;
    }

    public void reload() {
        try {
            refreshFirebaseAccountSection();
            currentSettings = app.getData().getSettings();
            if (currentSettings == null) currentSettings = new Settings();

            BusinessProfile b = currentSettings.getBusiness();
            if (b == null) b = new BusinessProfile();

            busName.setText(b.getName() != null ? b.getName() : "");
            busGstin.setText(b.getGstin() != null ? b.getGstin() : "");
            busAddress.setText(b.getAddress() != null ? b.getAddress() : "");
            busPhone.setText(b.getPhone() != null ? b.getPhone() : "");
            busEmail.setText(b.getEmail() != null ? b.getEmail() : "");
            busState.setText(b.getState() != null ? b.getState() : "");
            busStateCode.setText(b.getStateCode() != null ? b.getStateCode() : "");
            busTerms.setText(b.getTerms() != null ? b.getTerms() : "");

            currentLogoBase64 = b.getLogo() != null ? b.getLogo() : "";
            updateLogoPreview();

            bankName.setText(b.getBankName() != null ? b.getBankName() : "");
            bankAccount.setText(b.getAccountNo() != null ? b.getAccountNo() : "");
            bankIfsc.setText(b.getIfsc() != null ? b.getIfsc() : "");
            bankUpi.setText(b.getUpi() != null ? b.getUpi() : "");

            currencyField.setText(currentSettings.getCurrency() != null ? currentSettings.getCurrency() : "₹");
            prefixField.setText(currentSettings.getBillNoPrefix() != null ? currentSettings.getBillNoPrefix() : "");
            nextNoField.setText(String.valueOf(currentSettings.getBillNoNext()));
            digitsSpinner.getValueFactory().setValue(currentSettings.getBillNoDigits());
            interStateBox.setSelected(currentSettings.isInterState());
            autoRecurringBox.setSelected(currentSettings.isAutoRecurring());
            monochromePrintBox.setSelected(currentSettings.isMonochromePrint());

            offsetXField.setText(String.valueOf(currentSettings.getPrintOffsetX()));
            offsetYField.setText(String.valueOf(currentSettings.getPrintOffsetY()));
            statusStampBox.setSelected(currentSettings.isStatusStamp());

            barcodeThresholdSlider.setValue(currentSettings.getBarcodeThreshold());
            refreshThresholdPreview();

            editableBuyerFields.clear();
            if (currentSettings.getBuyerFields() != null) {
                editableBuyerFields.addAll(currentSettings.getBuyerFields());
            }
            renderBuyerFieldsList();

            editableFonts.clear();
            if (currentSettings.getCustomFonts() != null) {
                editableFonts.addAll(currentSettings.getCustomFonts());
                for (CustomFontDef f : editableFonts) {
                    if ("file".equalsIgnoreCase(f.getSource()) && f.getFileUrl() != null) {
                        File fl = new File(f.getFileUrl());
                        if (fl.exists()) {
                            try (FileInputStream fis = new FileInputStream(fl)) {
                                Font.loadFont(fis, 14.0);
                            } catch (Exception ignored) {
            AppLog.debug(ignored); }
                        }
                    }
                }
            }
            renderCustomFontsList();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
            Toast.show(this, "Failed to load settings: " + e.getMessage(), true);
        }
    }

    private void updateLogoPreview() {
        if (currentLogoBase64 != null && !currentLogoBase64.isEmpty()) {
            try {
                String clean = currentLogoBase64;
                if (clean.contains(",")) clean = clean.substring(clean.indexOf(",") + 1);
                byte[] bytes = Base64.getDecoder().decode(clean);
                Image img = new Image(new ByteArrayInputStream(bytes));
                logoImageView.setImage(img);
                removeLogoBtn.setVisible(true);
                return;
            } catch (Exception ignore) { AppLog.debug(ignore); }
        }
        logoImageView.setImage(null);
        removeLogoBtn.setVisible(false);
    }

    private void pickLogoFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Business Logo");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.webp", "*.bmp"));
        File f = fc.showOpenDialog(getScene().getWindow());
        if (f != null) {
            try {
                byte[] bytes = Files.readAllBytes(f.toPath());
                String base64 = Base64.getEncoder().encodeToString(bytes);
                String mime = f.getName().toLowerCase().endsWith(".png") ? "image/png" : "image/jpeg";
                currentLogoBase64 = "data:" + mime + ";base64," + base64;
                updateLogoPreview();
                Toast.show(this, "Logo loaded. Remember to click Save Settings.", false);
            } catch (Exception e) {
                Toast.show(this, "Failed to read image: " + e.getMessage(), true);
            }
        }
    }

    private void clearLogo() {
        currentLogoBase64 = "";
        updateLogoPreview();
        Toast.show(this, "Logo removed. Remember to click Save Settings.", false);
    }

    private void setAppLogo() {
        try (var in = getClass().getResourceAsStream("/icons/Invoicewhitebackground.png")) {
            if (in != null) {
                byte[] bytes = in.readAllBytes();
                currentLogoBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
                updateLogoPreview();
                Toast.show(this, "InvoiceStudio logo loaded. Remember to click Save Settings.", false);
            }
        } catch (Exception e) {
            Toast.show(this, "Failed to load logo: " + e.getMessage(), true);
        }
    }

    private void saveSettings() {
        try {
            if (currentSettings == null) currentSettings = new Settings();
            BusinessProfile b = currentSettings.getBusiness();
            if (b == null) {
                b = new BusinessProfile();
                currentSettings.setBusiness(b);
            }

            b.setName(busName.getText().trim());
            b.setGstin(busGstin.getText().trim());
            b.setAddress(busAddress.getText().trim());
            b.setPhone(busPhone.getText().trim());
            b.setEmail(busEmail.getText().trim());
            b.setState(busState.getText().trim());
            b.setStateCode(busStateCode.getText().trim());
            b.setTerms(busTerms.getText().trim());
            b.setLogo(currentLogoBase64);

            b.setBankName(bankName.getText().trim());
            b.setAccountNo(bankAccount.getText().trim());
            b.setIfsc(bankIfsc.getText().trim());
            b.setUpi(bankUpi.getText().trim());

            currentSettings.setCurrency(currencyField.getText().trim().isEmpty() ? "₹" : currencyField.getText().trim());
            currentSettings.setBillNoPrefix(prefixField.getText() != null ? prefixField.getText().trim() : "");
            try {
                currentSettings.setBillNoNext(Math.max(1, Integer.parseInt(nextNoField.getText().trim())));
            } catch (Exception ignore) { AppLog.debug(ignore); }
            currentSettings.setBillNoDigits(digitsSpinner.getValue() != null ? digitsSpinner.getValue() : 4);
            currentSettings.setInterState(interStateBox.isSelected());
            currentSettings.setAutoRecurring(autoRecurringBox.isSelected());
            currentSettings.setMonochromePrint(monochromePrintBox.isSelected());

            try {
                currentSettings.setPrintOffsetX(Double.parseDouble(offsetXField.getText().trim()));
            } catch (Exception ignore) { AppLog.debug(ignore); }
            try {
                currentSettings.setPrintOffsetY(Double.parseDouble(offsetYField.getText().trim()));
            } catch (Exception ignore) { AppLog.debug(ignore); }
            currentSettings.setStatusStamp(statusStampBox.isSelected());
            currentSettings.setBarcodeThreshold((int) Math.round(barcodeThresholdSlider.getValue()));

            currentSettings.setBuyerFields(new ArrayList<>(editableBuyerFields));
            currentSettings.setCustomFonts(new ArrayList<>(editableFonts));

            app.getData().saveSettings(currentSettings);
            Toast.show(this, "Settings saved successfully!", false);
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
            Toast.show(this, "Failed to save settings: " + e.getMessage(), true);
        }
    }

    private VBox createExpandableField(String labelText, TextArea ta, String prompt, int minRows, int maxRows) {
        return SettingsFieldSupport.createExpandableField(labelText, ta, prompt, minRows, maxRows);
    }

    private static String slugify(String s) {
        return SettingsFieldSupport.slugify(s);
    }
}
