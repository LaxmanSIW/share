package com.invoicestudio.ui.views;

import com.invoicestudio.db.SettingsDao;
import com.invoicestudio.model.*;
import com.invoicestudio.service.BackupRestoreService;
import com.invoicestudio.service.PrintingService;
import com.invoicestudio.ui.DialogHelper;
import com.invoicestudio.ui.IconHelper;
import com.invoicestudio.ui.Toast;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Consumer;
import javafx.scene.text.Font;

public class SettingsView extends VBox {

    private final SettingsDao settingsDao;
    private final BackupRestoreService backupService;
    private final PrintingService printingService;
    private final Consumer<Settings> onSaved;
    private final Runnable onReloadApp;

    private Settings currentSettings;

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
    private final CheckBox interStateBox = new CheckBox("Inter-state supplies (IGST default)");
    private final CheckBox autoRecurringBox = new CheckBox("Auto-create recurring invoices on startup");

    // Print calibration
    private final TextField offsetXField = new TextField();
    private final TextField offsetYField = new TextField();
    private final CheckBox statusStampBox = new CheckBox("Status stamp on print (PAID / CANCELLED)");

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

    public SettingsView(SettingsDao settingsDao, BackupRestoreService backupService, PrintingService printingService,
                        Consumer<Settings> onSaved, Runnable onReloadApp) {
        this.settingsDao = settingsDao;
        this.backupService = backupService;
        this.printingService = printingService;
        this.onSaved = onSaved;
        this.onReloadApp = onReloadApp;

        setSpacing(24);
        setPadding(new Insets(24));
        getStyleClass().add("root-pane");
        setStyle("-fx-background-color: #0B0E13;");

        buildHeader();
        buildBusinessSection();
        buildBankSection();
        buildBillingPrefsSection();
        buildBuyerFieldsSection();
        buildCustomFontsSection();
        buildPrintCalibrationSection();
        buildBackupRestoreSection();
        buildStorageInfoSection();

        reload();
    }

    private void buildHeader() {
        HBox header = new HBox(16);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(4);
        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label titleIcon = IconHelper.createIconLabel(IconHelper.ICON_SETTINGS, 22, "#d9a13b");
        Label title = new Label("Settings");
        title.getStyleClass().add("view-title");
        titleRow.getChildren().addAll(titleIcon, title);

        Label sub = new Label("Business profile, tax mode, billing numbering, printing calibration & backups.");
        sub.getStyleClass().add("view-subtitle");
        titleBox.getChildren().addAll(titleRow, sub);
        HBox.setHgrow(titleBox, Priority.ALWAYS);

        Button saveBtn = new Button("Save Settings");
        saveBtn.setGraphic(IconHelper.createIconLabel(IconHelper.ICON_CHECK, 14, "#000000"));
        saveBtn.getStyleClass().add("button-primary");
        saveBtn.setTooltip(new Tooltip("Save all business profile and system preferences"));
        saveBtn.setOnAction(e -> saveSettings());

        header.getChildren().addAll(titleBox, saveBtn);
        getChildren().add(header);
    }

    private void buildBusinessSection() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card-pane");
        card.setPadding(new Insets(18));

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

        grid.add(labeledNode("Business Name *", busName), 0, 0);
        busGstin.textProperty().addListener((obs, o, v) -> {
            if (v != null && v.trim().length() >= 2 && busStateCode.getText().isBlank()) {
                String code = v.trim().substring(0, 2);
                if (code.matches("\\d{2}")) {
                    busStateCode.setText(code);
                }
            }
        });
        grid.add(labeledNode("GSTIN", busGstin), 1, 0);

        grid.add(createExpandableField("Registered Address", busAddress, "Street, building, area, city, pin code...", 3, 8), 0, 1, 2, 1);

        grid.add(labeledNode("Phone Number", busPhone), 0, 2);
        grid.add(labeledNode("Email Address", busEmail), 1, 2);

        grid.add(labeledNode("State (e.g. Maharashtra)", busState), 0, 3);
        grid.add(labeledNode("State Code (e.g. 27)", busStateCode), 1, 3);

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

        removeLogoBtn.getStyleClass().add("button-icon-subtle");
        removeLogoBtn.setStyle("-fx-text-fill: #ef4444; -fx-font-weight: bold;");
        removeLogoBtn.setOnAction(e -> clearLogo());

        logoContainer.getChildren().addAll(logoImageView);

        VBox logoInfo = new VBox(4);
        Label logoTitle = new Label("Business Logo");
        logoTitle.getStyleClass().add("table-cell-title");
        Label logoSub = new Label("Upload once here to appear on all invoices and template designs.");
        logoSub.getStyleClass().add("muted-label");

        HBox btnBox = new HBox(8);
        Button uploadLogoBtn = new Button("Upload Logo");
        uploadLogoBtn.setGraphic(IconHelper.createIconLabel(IconHelper.ICON_UPLOAD, 13, "#d9a13b"));
        uploadLogoBtn.getStyleClass().add("button-secondary");
        uploadLogoBtn.setOnAction(e -> pickLogoFile());

        Button appLogoBtn = new Button("Use App Logo");
        appLogoBtn.getStyleClass().add("button-secondary");
        appLogoBtn.setTooltip(new Tooltip("Set bundled InvoiceStudio logo as business logo"));
        appLogoBtn.setOnAction(e -> setAppLogo());

        btnBox.getChildren().addAll(uploadLogoBtn, appLogoBtn, removeLogoBtn);
        logoInfo.getChildren().addAll(logoTitle, logoSub, btnBox);

        logoRow.getChildren().addAll(logoContainer, logoInfo);

        card.getChildren().addAll(secTitle, grid, logoRow);
        getChildren().add(card);
    }

    private void buildBankSection() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card-pane");
        card.setPadding(new Insets(18));

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

        grid.add(labeledNode("Bank Name", bankName), 0, 0);
        grid.add(labeledNode("Account Number", bankAccount), 1, 0);
        grid.add(labeledNode("IFSC Code", bankIfsc), 0, 1);
        grid.add(labeledNode("UPI ID (e.g. business@okaxis)", bankUpi), 1, 1);

        card.getChildren().addAll(secTitle, grid);
        getChildren().add(card);
    }

    private void buildBillingPrefsSection() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card-pane");
        card.setPadding(new Insets(18));

        Label secTitle = new Label("BILLING PREFERENCES");
        secTitle.getStyleClass().add("card-title");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(33.3);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(33.3);
        ColumnConstraints col3 = new ColumnConstraints();
        col3.setPercentWidth(33.3);
        grid.getColumnConstraints().addAll(col1, col2, col3);

        grid.add(labeledNode("Currency Symbol", currencyField), 0, 0);
        grid.add(labeledNode("Bill No Prefix", prefixField), 1, 0);
        grid.add(labeledNode("Next Bill Number", nextNoField), 2, 0);

        HBox toggles = new HBox(24);
        toggles.setAlignment(Pos.CENTER_LEFT);
        interStateBox.getStyleClass().add("check-box");
        autoRecurringBox.getStyleClass().add("check-box");
        toggles.getChildren().addAll(interStateBox, autoRecurringBox);

        card.getChildren().addAll(secTitle, grid, toggles);
        getChildren().add(card);
    }

    private void buildBuyerFieldsSection() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card-pane");
        card.setPadding(new Insets(18));

        HBox head = new HBox(8);
        head.setAlignment(Pos.CENTER_LEFT);
        Label ic = IconHelper.createIconLabel(IconHelper.ICON_USERS, 14, "#d9a13b");
        Label title = new Label("BUYER CUSTOM FIELDS");
        title.getStyleClass().add("card-title");
        head.getChildren().addAll(ic, title);

        Label sub = new Label("Add custom fields to your customers (e.g. Credit Limit, Payment Terms, Region, Sales Agent). They become variables ({{buyer_<key>}}) and extra CSV columns.");
        sub.getStyleClass().add("muted-label");

        // Add row
        HBox addRow = new HBox(12);
        addRow.setAlignment(Pos.CENTER_LEFT);

        newBuyerFieldLabel.setPromptText("New field name (e.g. Region)");
        newBuyerFieldLabel.getStyleClass().add("text-input");
        newBuyerFieldLabel.setPrefWidth(220);

        newBuyerFieldType.setItems(FXCollections.observableArrayList("text", "number", "date"));
        newBuyerFieldType.setValue("text");
        newBuyerFieldType.setPrefWidth(110);

        Button addBtn = new Button("Add Field");
        addBtn.setGraphic(IconHelper.createIconLabel(IconHelper.ICON_PLUS, 13, "#d9a13b"));
        addBtn.getStyleClass().add("button-secondary");
        addBtn.setOnAction(e -> addBuyerField());

        addRow.getChildren().addAll(newBuyerFieldLabel, newBuyerFieldType, addBtn);

        card.getChildren().addAll(head, sub, buyerFieldsList, addRow);
        getChildren().add(card);
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
            row.getStyleClass().add("table-data-row");
            row.setPadding(new Insets(6, 12, 6, 12));

            TextField labelEdit = new TextField(def.getLabel());
            labelEdit.getStyleClass().add("text-input");
            labelEdit.setPrefWidth(180);
            labelEdit.textProperty().addListener((obs, oldV, newV) -> def.setLabel(newV.trim()));

            Label keyPill = new Label("{{buyer_" + def.getKey() + "}}");
            keyPill.getStyleClass().add("code-pill");
            keyPill.setPrefWidth(160);

            ComboBox<String> typeCb = new ComboBox<>(FXCollections.observableArrayList("text", "number", "date"));
            typeCb.setValue(def.getType() != null ? def.getType() : "text");
            typeCb.valueProperty().addListener((obs, oldV, newV) -> def.setType(newV));
            typeCb.setPrefWidth(100);

            HBox grow = new HBox();
            HBox.setHgrow(grow, Priority.ALWAYS);

            Button del = new Button();
            del.setGraphic(IconHelper.createIconLabel(IconHelper.ICON_TRASH, 13, "#ef4444"));
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
            Toast.show(this, "Maximum 12 custom buyer fields allowed.");
            return;
        }
        String key = slugify(label);
        boolean exists = editableBuyerFields.stream().anyMatch(f -> f.getKey().equalsIgnoreCase(key));
        if (exists) {
            Toast.show(this, "A field with this name already exists.");
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

    private void buildCustomFontsSection() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card-pane");
        card.setPadding(new Insets(18));

        HBox head = new HBox(8);
        head.setAlignment(Pos.CENTER_LEFT);
        Label ic = IconHelper.createIconLabel(IconHelper.ICON_VARIABLE, 14, "#d9a13b");
        Label title = new Label("CUSTOM FONTS & TYPOGRAPHY");
        title.getStyleClass().add("card-title");
        head.getChildren().addAll(ic, title);

        Label sub = new Label("Add Google Web Fonts or load local .ttf / .otf font files from disk to use on your invoices and templates.");
        sub.getStyleClass().add("muted-label");

        // Clear Informational Guide Box
        VBox guideBox = new VBox(8);
        guideBox.getStyleClass().add("card-pane-subtle");
        guideBox.setPadding(new Insets(12));

        Label guideTitle = new Label("HOW TO ADD & USE CUSTOM FONTS");
        guideTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 11px; -fx-text-fill: #D9A13B;");

        Label gInfo1 = new Label("1. Google Fonts / Web Links: Type any font name (e.g. Outfit, Cinzel, Inter, Fira Code, DM Sans) or paste a Google Fonts URL (e.g. https://fonts.google.com/specimen/Outfit). InvoiceStudio will parse the family name and register it.");
        gInfo1.setStyle("-fx-font-size: 11px; -fx-text-fill: #CBD5E1;");
        gInfo1.setWrapText(true);

        Label gInfo2 = new Label("2. Local Font Files (.ttf / .otf): If you have downloaded font files on your computer, click 'Browse Local Font (.ttf/.otf)'. The desktop application loads and registers the font directly into JavaFX memory so you can preview and print with it instantly.");
        gInfo2.setStyle("-fx-font-size: 11px; -fx-text-fill: #CBD5E1;");
        gInfo2.setWrapText(true);

        guideBox.getChildren().addAll(guideTitle, gInfo1, gInfo2);

        // Action Row
        HBox addRow = new HBox(10);
        addRow.setAlignment(Pos.CENTER_LEFT);

        googleFontInput.setPromptText("Font name or Google Fonts URL (e.g. Outfit)");
        googleFontInput.getStyleClass().add("text-input");
        googleFontInput.setPrefWidth(260);

        googleFontCat.setItems(FXCollections.observableArrayList("sans", "serif", "mono", "display", "handwriting"));
        googleFontCat.setValue("sans");
        googleFontCat.setPrefWidth(110);

        Button addBtn = new Button("Add Font Name / URL");
        addBtn.setGraphic(IconHelper.createIconLabel(IconHelper.ICON_PLUS, 13, "#d9a13b"));
        addBtn.getStyleClass().add("button-secondary");
        addBtn.setOnAction(e -> addFont());

        Separator sep = new Separator(javafx.geometry.Orientation.VERTICAL);

        Button browseFileBtn = new Button("Browse Local Font (.ttf, .otf)...");
        browseFileBtn.setGraphic(IconHelper.createIconLabel(IconHelper.ICON_UPLOAD, 13, "#10b981"));
        browseFileBtn.getStyleClass().add("button-secondary");
        browseFileBtn.setOnAction(e -> pickLocalFontFile());

        addRow.getChildren().addAll(googleFontInput, googleFontCat, addBtn, sep, browseFileBtn);

        card.getChildren().addAll(head, sub, guideBox, customFontsList, addRow);
        getChildren().add(card);
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
            row.getStyleClass().add("table-data-row");
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
                pathLbl.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748B;");
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
            preview.setStyle("-fx-font-family: '" + font.getName() + "'; -fx-text-fill: #d9a13b; -fx-font-size: 13px;");
            HBox.setHgrow(preview, Priority.ALWAYS);

            Button del = new Button();
            del.setGraphic(IconHelper.createIconLabel(IconHelper.ICON_TRASH, 13, "#ef4444"));
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
            Toast.show(this, "Font already added: " + finalName);
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
        Toast.show(this, "Added font: " + finalName);
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
                        Toast.show(this, "Font \"" + finalName + "\" is already in your font list.");
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
                    Toast.show(this, "Loaded local font: " + finalName);
                } else {
                    Toast.show(this, "Could not load font file. Please verify it is a valid .ttf or .otf file.");
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                Toast.show(this, "Error reading font file: " + ex.getMessage());
            }
        }
    }

    private void buildPrintCalibrationSection() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card-pane");
        card.setPadding(new Insets(18));

        Label secTitle = new Label("PRINT CALIBRATION & HARDWARE OFFSETS");
        secTitle.getStyleClass().add("card-title");

        Label sub = new Label("Adjust physical print placement (in millimeters) to align precisely with pre-printed stationary, letterhead, and thermal margins.");
        sub.getStyleClass().add("muted-label");

        GridPane grid = new GridPane();
        grid.setHgap(16);
        grid.setVgap(12);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        grid.getColumnConstraints().addAll(col1, col2);

        grid.add(labeledNode("Horizontal Offset (mm, + moves right)", offsetXField), 0, 0);
        grid.add(labeledNode("Vertical Offset (mm, + moves down)", offsetYField), 1, 0);

        HBox bottomRow = new HBox(16);
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        statusStampBox.getStyleClass().add("check-box");
        HBox.setHgrow(statusStampBox, Priority.ALWAYS);

        Button calibSheetBtn = new Button("Print Calibration Sheet");
        calibSheetBtn.setGraphic(IconHelper.createIconLabel(IconHelper.ICON_CROSSHAIR, 13, "#d9a13b"));
        calibSheetBtn.getStyleClass().add("button-secondary");
        calibSheetBtn.setOnAction(e -> runCalibrationPrint());

        bottomRow.getChildren().addAll(statusStampBox, calibSheetBtn);

        card.getChildren().addAll(secTitle, sub, grid, bottomRow);
        getChildren().add(card);
    }

    private void runCalibrationPrint() {
        try {
            double ox = 0;
            double oy = 0;
            try {
                ox = Double.parseDouble(offsetXField.getText().trim());
                oy = Double.parseDouble(offsetYField.getText().trim());
            } catch (Exception ignore) {}
            boolean ok = printingService.printCalibrationSheet(ox, oy);
            if (ok) {
                Toast.show(this, "Calibration sheet sent to printer.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.show(this, "Print failed: " + e.getMessage());
        }
    }

    private void buildBackupRestoreSection() {
        VBox card = new VBox(14);
        card.getStyleClass().add("card-pane");
        card.setPadding(new Insets(18));

        Label secTitle = new Label("DATABASE BACKUP & RESTORE");
        secTitle.getStyleClass().add("card-title");

        Label sub = new Label("Export your entire business database (bills, items, buyers, templates, settings) into a single portable JSON file, or restore from a previous backup.");
        sub.getStyleClass().add("muted-label");

        HBox btnRow = new HBox(16);
        btnRow.setAlignment(Pos.CENTER_LEFT);

        Button exportBtn = new Button("Export Backup JSON");
        exportBtn.setGraphic(IconHelper.createIconLabel(IconHelper.ICON_DOWNLOAD, 13, "#10b981"));
        exportBtn.getStyleClass().add("button-secondary");
        exportBtn.setTooltip(new Tooltip("Export complete database backup to a JSON file"));
        exportBtn.setOnAction(e -> handleExportBackup());

        Button importBtn = new Button("Restore from Backup JSON...");
        importBtn.setGraphic(IconHelper.createIconLabel(IconHelper.ICON_UPLOAD, 13, "#d9a13b"));
        importBtn.getStyleClass().add("button-secondary");
        importBtn.setTooltip(new Tooltip("Restore database from previously exported JSON backup"));
        importBtn.setOnAction(e -> handleRestoreBackup());

        btnRow.getChildren().addAll(exportBtn, importBtn);

        card.getChildren().addAll(secTitle, sub, btnRow);
        getChildren().add(card);
    }

    private void handleExportBackup() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Database Backup");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
        fc.setInitialFileName("InvoiceStudio_backup_" + System.currentTimeMillis() + ".json");
        File file = fc.showSaveDialog(getScene().getWindow());
        if (file != null) {
            try {
                backupService.exportToFile(file);
                Toast.show(this, "Backup saved: " + file.getName());
            } catch (Exception e) {
                e.printStackTrace();
                Toast.show(this, "Backup failed: " + e.getMessage());
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
                    BackupRestoreService.RestoreResult r = backupService.restoreFromFile(file);
                    Toast.show(this, String.format("Restored: %d bills, %d buyers, %d items, %d templates",
                            r.billCount, r.buyerCount, r.itemCount, r.templateCount));
                    reload();
                    if (onReloadApp != null) onReloadApp.run();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.show(this, "Restore error: " + e.getMessage());
                }
            }
        }
    }

    private void buildStorageInfoSection() {
        VBox card = new VBox(8);
        card.getStyleClass().add("card-pane-dashed");
        card.setPadding(new Insets(16));

        Label title = new Label("LOCAL EMBEDDED DATA STORAGE");
        title.getStyleClass().add("card-title");

        Label desc = new Label("InvoiceStudio runs entirely offline using a local SQLite database (share.db) with WAL mode enabled. No cloud connection or subscription required. Data is always private and stored locally on this machine.");
        desc.getStyleClass().add("muted-label");
        desc.setWrapText(true);

        card.getChildren().addAll(title, desc);
        getChildren().add(card);
    }

    public void reload() {
        try {
            currentSettings = settingsDao.get();
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
            prefixField.setText(currentSettings.getBillNoPrefix() != null ? currentSettings.getBillNoPrefix() : "INV-");
            nextNoField.setText(String.valueOf(currentSettings.getBillNoNext()));
            interStateBox.setSelected(currentSettings.isInterState());
            autoRecurringBox.setSelected(currentSettings.isAutoRecurring());

            offsetXField.setText(String.valueOf(currentSettings.getPrintOffsetX()));
            offsetYField.setText(String.valueOf(currentSettings.getPrintOffsetY()));
            statusStampBox.setSelected(currentSettings.isStatusStamp());

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
                            } catch (Exception ignored) {}
                        }
                    }
                }
            }
            renderCustomFontsList();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.show(this, "Failed to load settings: " + e.getMessage());
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
            } catch (Exception ignore) {}
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
                Toast.show(this, "Logo loaded. Remember to click Save Settings.");
            } catch (Exception e) {
                Toast.show(this, "Failed to read image: " + e.getMessage());
            }
        }
    }

    private void clearLogo() {
        currentLogoBase64 = "";
        updateLogoPreview();
        Toast.show(this, "Logo removed. Remember to click Save Settings.");
    }

    private void setAppLogo() {
        try (var in = getClass().getResourceAsStream("/icons/Invoicewhitebackground.png")) {
            if (in != null) {
                byte[] bytes = in.readAllBytes();
                currentLogoBase64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
                updateLogoPreview();
                Toast.show(this, "InvoiceStudio logo loaded. Remember to click Save Settings.");
            }
        } catch (Exception e) {
            Toast.show(this, "Failed to load logo: " + e.getMessage());
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
            currentSettings.setBillNoPrefix(prefixField.getText().trim().isEmpty() ? "INV-" : prefixField.getText().trim());
            try {
                currentSettings.setBillNoNext(Math.max(1, Integer.parseInt(nextNoField.getText().trim())));
            } catch (Exception ignore) {}
            currentSettings.setInterState(interStateBox.isSelected());
            currentSettings.setAutoRecurring(autoRecurringBox.isSelected());

            try {
                currentSettings.setPrintOffsetX(Double.parseDouble(offsetXField.getText().trim()));
            } catch (Exception ignore) {}
            try {
                currentSettings.setPrintOffsetY(Double.parseDouble(offsetYField.getText().trim()));
            } catch (Exception ignore) {}
            currentSettings.setStatusStamp(statusStampBox.isSelected());

            currentSettings.setBuyerFields(new ArrayList<>(editableBuyerFields));
            currentSettings.setCustomFonts(new ArrayList<>(editableFonts));

            settingsDao.save(currentSettings);
            Toast.show(this, "Settings saved successfully!");

            if (onSaved != null) onSaved.accept(currentSettings);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.show(this, "Failed to save settings: " + e.getMessage());
        }
    }

    private VBox createExpandableField(String labelText, TextArea ta, String prompt, int minRows, int maxRows) {
        ta.setPromptText(prompt);
        ta.setWrapText(true);
        ta.setPrefRowCount(minRows);
        ta.getStyleClass().add("setting-expandable-textbox");
        ta.setStyle("-fx-control-inner-background: #8E9EB5; -fx-background-color: #FFFFFF; -fx-text-fill: #000000; -fx-prompt-text-fill: #64748B; -fx-font-family: 'Segoe UI', sans-serif; -fx-font-size: 13px;");

        // Auto-expand dynamically as content is typed/pasted
        ta.textProperty().addListener((obs, o, v) -> {
            int lines = 1;
            if (v != null && !v.isEmpty()) {
                lines = v.split("\r\n|\r|\n", -1).length;
                int wrapLines = (int) Math.ceil((double) v.length() / 50.0);
                lines = Math.max(lines, wrapLines);
            }
            ta.setPrefRowCount(Math.min(maxRows, Math.max(minRows, lines)));
        });

        // Header with title and manual expand/collapse toggle button
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("field-label");

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button toggleBtn = new Button("⤢ Expand");
        toggleBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #8E9EB5; -fx-font-size: 10px; -fx-padding: 1 4; -fx-cursor: hand;");
        toggleBtn.setOnAction(e -> {
            if (ta.getPrefRowCount() <= minRows + 1) {
                ta.setPrefRowCount(maxRows);
                toggleBtn.setText("⤡ Collapse");
            } else {
                ta.setPrefRowCount(minRows);
                toggleBtn.setText("⤢ Expand");
            }
        });

        header.getChildren().addAll(lbl, sp, toggleBtn);

        VBox box = new VBox(4);
        box.getChildren().addAll(header, ta);
        return box;
    }

    private VBox labeledNode(String labelText, javafx.scene.Node node) {
        VBox box = new VBox(4);
        Label lbl = new Label(labelText);
        lbl.getStyleClass().add("field-label");
        box.getChildren().addAll(lbl, node);
        return box;
    }

    private static String slugify(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
