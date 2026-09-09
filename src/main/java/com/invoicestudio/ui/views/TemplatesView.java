package com.invoicestudio.ui.views;

import com.invoicestudio.db.SettingsDao;
import com.invoicestudio.db.TemplateDao;
import com.invoicestudio.model.PageSizeName;
import com.invoicestudio.model.PresetTemplates;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;
import com.invoicestudio.service.PrintingService;
import com.invoicestudio.ui.BillPreviewPane;
import com.invoicestudio.ui.DialogHelper;
import com.invoicestudio.ui.StudioApp;
import com.invoicestudio.ui.Toast;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class TemplatesView extends BorderPane {

    private final StudioApp app;
    private final TemplateDao templateDao;
    private final SettingsDao settingsDao;
    private final VBox contentBox = new VBox(24);

    public TemplatesView(StudioApp app) {
        this.app = app;
        this.templateDao = new TemplateDao(app.getDb());
        this.settingsDao = new SettingsDao(app.getDb());

        setPadding(new Insets(24));
        getStyleClass().add("bg-app");

        ScrollPane scroll = new ScrollPane(contentBox);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-pane");
        setCenter(scroll);

        refresh();
    }

    public void refresh() {
        contentBox.getChildren().clear();

        List<Template> templates = templateDao.getAllTemplates();
        Settings settings = settingsDao.getSettings();

        // 1. Top Bar
        HBox topBar = new HBox(16);
        topBar.setAlignment(Pos.CENTER_LEFT);

        VBox titleBox = new VBox(2);
        Label title = new Label("Templates Gallery");
        title.getStyleClass().add("heading-l");
        Label sub = new Label("Design and manage layout templates for standard printers and thermal rolls.");
        sub.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
        titleBox.getChildren().addAll(title, sub);

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);

        Button calibBtn = new Button("Print Calibration Sheet");
        calibBtn.getStyleClass().addAll("button-sm", "button-secondary");
        calibBtn.setTooltip(new Tooltip("Print printer alignment & margin test sheet"));
        calibBtn.setOnAction(e -> {
            Pane sheet = PrintingService.createCalibrationSheetNode(settings);
            boolean printed = PrintingService.printNode(sheet, app.getPrimaryStage(), 1, "Printer Calibration Sheet");
            if (printed) {
                Toast.show(app.getRootPane(), "Calibration Sheet Sent", "Sent calibration sheet to printer.", false);
            }
        });

        Button newBtn = new Button("+ New Template");
        newBtn.getStyleClass().addAll("gold-btn");
        newBtn.setTooltip(new Tooltip("Design a custom template in the visual designer"));
        newBtn.setOnAction(e -> {
            Template blank = PresetTemplates.buildClassic();
            blank.setId("tpl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            blank.setName("New Custom Template");
            templateDao.saveTemplate(blank);
            app.showTemplateDesigner(blank);
        });

        topBar.getChildren().addAll(titleBox, sp, calibBtn, newBtn);
        contentBox.getChildren().add(topBar);

        // 2. Built-in Preset Library
        VBox presetSec = new VBox(12);
        Label pTitle = new Label("PRESET STARTERS (CLICK TO ADD TO YOUR TEMPLATES)");
        pTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #D9A13B; -fx-letter-spacing: 1.5;");
        presetSec.getChildren().add(pTitle);

        FlowPane presetGrid = new FlowPane(16, 16);
        presetGrid.getChildren().addAll(
                buildPresetCard("GST Tax Invoice — Classic", "A4", "Cream letterhead, dark title band, full GST split & bank details.", "#D9A13B", () -> loadPreset(PresetTemplates.buildClassic())),
                buildPresetCard("GST Tax Invoice — Modern", "A4", "Bold charcoal header with gold accents, crisp contemporary look.", "#181818", () -> loadPreset(PresetTemplates.buildModern())),
                buildPresetCard("Compact GST Invoice", "A5", "Half-size A5 docket — great for counters, deliveries and short bills.", "#0E7A5F", () -> loadPreset(PresetTemplates.buildCompactA5())),
                buildPresetCard("Minimal Invoice", "A4", "Clean borderless layout with hairline rules — plain and modern.", "#52525B", () -> loadPreset(PresetTemplates.buildMinimal())),
                buildPresetCard("Thermal POS Receipt", "80mm Roll", "Continuous 80 mm roll with a scan-to-pay UPI QR & barcode.", "#B45309", () -> loadPreset(PresetTemplates.buildThermal80())),
                buildPresetCard("Thermal Mini Receipt", "58mm Roll", "Ultra-compact 58 mm roll for pocket portable POS printers.", "#0D9488", () -> loadPreset(PresetTemplates.buildThermal58()))
        );
        presetSec.getChildren().add(presetGrid);
        contentBox.getChildren().add(presetSec);

        // 3. User Saved Templates
        VBox userSec = new VBox(12);
        Label uTitle = new Label("YOUR SAVED TEMPLATES (" + templates.size() + ")");
        uTitle.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #94A3B8; -fx-letter-spacing: 1.5;");
        userSec.getChildren().add(uTitle);

        FlowPane userGrid = new FlowPane(16, 16);
        for (Template t : templates) {
            userGrid.getChildren().add(buildUserTemplateCard(t, settings));
        }
        userSec.getChildren().add(userGrid);
        contentBox.getChildren().add(userSec);
    }

    private Node buildPresetCard(String name, String size, String desc, String accentColor, Runnable onAdd) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        card.setPrefWidth(260);
        card.setStyle("-fx-background-color: #151B25; -fx-padding: 14; -fx-background-radius: 8; -fx-border-color: #232B38; -fx-border-radius: 8;");

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label nameLbl = new Label(name);
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: #F4F4F5;");
        HBox.setHgrow(nameLbl, Priority.ALWAYS);

        Label badge = new Label(size);
        badge.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-background-color: " + accentColor + "; -fx-text-fill: white; -fx-padding: 2 6; -fx-background-radius: 4;");
        header.getChildren().addAll(nameLbl, badge);

        Label descLbl = new Label(desc);
        descLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
        descLbl.setWrapText(true);
        descLbl.setPrefHeight(36);

        Button addBtn = new Button("Load Template");
        addBtn.getStyleClass().addAll("button-sm", "button-secondary");
        addBtn.setMaxWidth(Double.MAX_VALUE);
        addBtn.setOnAction(e -> onAdd.run());

        card.getChildren().addAll(header, descLbl, addBtn);
        return card;
    }

    private Node buildUserTemplateCard(Template t, Settings settings) {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPrefWidth(280);
        card.setStyle("-fx-background-color: #151B25; -fx-padding: 16; -fx-background-radius: 8; -fx-border-color: #232B38; -fx-border-radius: 8;");

        HBox top = new HBox(8);
        top.setAlignment(Pos.CENTER_LEFT);
        Label nameLbl = new Label(t.getName());
        nameLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 13px; -fx-text-fill: #F4F4F5;");
        HBox.setHgrow(nameLbl, Priority.ALWAYS);

        Label badge = new Label(t.getPage().getSizeName().getLabel());
        badge.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-background-color: #232B38; -fx-text-fill: #D9A13B; -fx-padding: 2 6; -fx-background-radius: 4;");
        top.getChildren().addAll(nameLbl, badge);

        Label meta = new Label(t.getElements().size() + " layout elements • " + (t.getPage().isAutoHeight() ? "Continuous roll" : (int) t.getPage().getWidth() + "x" + (int) t.getPage().getHeight() + " mm"));
        meta.setStyle("-fx-font-size: 10px; -fx-text-fill: #64748B;");

        // Action buttons
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);

        Button useBtn = new Button("Create Bill");
        useBtn.getStyleClass().addAll("button-sm", "gold-btn");
        useBtn.setTooltip(new Tooltip("Create invoice with this layout"));
        useBtn.setOnAction(e -> app.showCreateBill(t.getId(), null));

        Button editBtn = new Button("Designer");
        editBtn.getStyleClass().addAll("button-sm", "button-secondary");
        editBtn.setTooltip(new Tooltip("Open visual drag-and-drop template designer"));
        editBtn.setOnAction(e -> app.showTemplateDesigner(t));

        Button dupBtn = new Button("⎘");
        dupBtn.getStyleClass().addAll("button-sm", "button-secondary");
        dupBtn.setTooltip(new Tooltip("Duplicate Template"));
        dupBtn.setOnAction(e -> {
            Template copy = PresetTemplates.buildClassic();
            copy.setId("tpl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
            copy.setName(t.getName() + " (Copy)");
            copy.setPage(t.getPage());
            copy.setElements(t.getElements());
            templateDao.saveTemplate(copy);
            refresh();
            Toast.show(app.getRootPane(), "Template Duplicated", "Created copy of " + t.getName(), false);
        });

        Button delBtn = new Button("🗑");
        delBtn.getStyleClass().addAll("button-sm", "button-danger");
        delBtn.setTooltip(new Tooltip("Delete Template"));
        delBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Delete template \"" + t.getName() + "\"?", ButtonType.YES, ButtonType.NO);
            DialogHelper.styleDialog(confirm);
            confirm.showAndWait().ifPresent(ans -> {
                if (ans == ButtonType.YES) {
                    templateDao.deleteTemplate(t.getId());
                    refresh();
                    Toast.show(app.getRootPane(), "Template Deleted", t.getName() + " removed.", false);
                }
            });
        });

        actions.getChildren().addAll(useBtn, editBtn, dupBtn, delBtn);

        card.getChildren().addAll(top, meta, actions);
        return card;
    }

    private void loadPreset(Template preset) {
        preset.setId("tpl_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        templateDao.saveTemplate(preset);
        refresh();
        Toast.show(app.getRootPane(), "Preset Added", "Added " + preset.getName() + " to your templates.", false);
    }
}
