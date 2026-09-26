package com.invoicestudio.ui;

import com.invoicestudio.service.AppLog;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

import java.util.List;
import java.util.function.Consumer;

/**
 * Settings tab panel hosting the Theme Vault.
 * Allows users to browse built-in themes, copy .root CSS blocks,
 * create/edit custom themes, validate required tokens, and live-switch palettes.
 */
public class ThemeVaultPanel extends VBox {

    private final StudioApp app;
    private final ThemeManager themeManager = ThemeManager.getInstance();

    private final VBox cardsContainer = new VBox(12);
    private final TextField searchField = new TextField();
    private final Label countPill = UiTheme.pill("0 Themes");
    private final Consumer<Theme> themeListener;

    public ThemeVaultPanel(StudioApp app) {
        this.app = app;
        setSpacing(16);
        setPadding(new Insets(4, 6, 20, 4));

        getChildren().addAll(
            UiTheme.headerBlock("🎨", "Theme Vault",
                "Customize and switch application color themes. Define or edit the .root CSS token block."),
            buildToolbar(),
            new Separator(),
            cardsContainer
        );

        themeListener = theme -> Platform.runLater(this::rebuildCards);
        themeManager.addThemeChangeListener(themeListener);

        searchField.textProperty().addListener((obs, oldV, newV) -> rebuildCards());

        rebuildCards();
    }

    private Node buildToolbar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);

        Button newThemeBtn = UiTheme.goldBtn("+ New Theme");
        newThemeBtn.setTooltip(new Tooltip("Create a new custom theme by pasting a .root CSS block"));
        newThemeBtn.setOnAction(e -> showThemeEditorDialog(null, false));

        Button copyTemplateBtn = UiTheme.secondaryBtn("Copy .root Template");
        copyTemplateBtn.setTooltip(new Tooltip("Copy the canonical .root CSS variables template to clipboard"));
        copyTemplateBtn.setOnAction(e -> copyDefaultTemplate());

        Button resetBtn = UiTheme.secondaryBtn("Reset to Default");
        resetBtn.setTooltip(new Tooltip("Revert to the signature Obsidian & Gold default theme"));
        resetBtn.setOnAction(e -> {
            themeManager.applyTheme(ThemeManager.DEFAULT_THEME_ID);
            showToast("Theme Reset", "Reverted to Obsidian & Gold (Default)", false);
        });

        Region spacer = UiTheme.spacer();

        searchField.setPromptText("Filter themes...");
        searchField.setPrefWidth(200);

        bar.getChildren().addAll(newThemeBtn, copyTemplateBtn, resetBtn, spacer, searchField, countPill);
        return bar;
    }

    private void rebuildCards() {
        cardsContainer.getChildren().clear();

        List<Theme> allThemes = themeManager.getAllThemes();
        String query = searchField.getText() != null ? searchField.getText().trim().toLowerCase() : "";

        int visibleCount = 0;
        Theme activeTheme = themeManager.getActiveTheme();

        for (Theme theme : allThemes) {
            if (!query.isEmpty()) {
                boolean matchName = theme.getName() != null && theme.getName().toLowerCase().contains(query);
                boolean matchDesc = theme.getDescription() != null && theme.getDescription().toLowerCase().contains(query);
                if (!matchName && !matchDesc) {
                    continue;
                }
            }

            visibleCount++;
            boolean isActive = activeTheme != null && theme.getId().equals(activeTheme.getId());
            cardsContainer.getChildren().add(buildThemeCard(theme, isActive));
        }

        countPill.setText(visibleCount + " Themes");

        if (visibleCount == 0) {
            VBox empty = new VBox(10);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(30));
            empty.getStyleClass().add("card");
            Label emptyLbl = new Label("No themes match your search query.");
            emptyLbl.getStyleClass().add("text-muted");
            empty.getChildren().add(emptyLbl);
            cardsContainer.getChildren().add(empty);
        }
    }

    private Node buildThemeCard(Theme theme, boolean isActive) {
        VBox card = UiTheme.card(12);
        card.getStyleClass().add("theme-card");
        if (isActive) {
            card.getStyleClass().add("theme-card-active");
        }

        // Top Row: Title + Badges + Spacer + Action Buttons
        HBox topRow = new HBox(10);
        topRow.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label(theme.getName());
        title.getStyleClass().add("card-title");

        topRow.getChildren().add(title);

        if (isActive) {
            topRow.getChildren().add(UiTheme.statusPill("ACTIVE", "success"));
        }

        if (theme.isBuiltIn()) {
            topRow.getChildren().add(UiTheme.pill("BUILT-IN"));
        } else {
            topRow.getChildren().add(UiTheme.statusPill("CUSTOM", "neutral"));
        }

        topRow.getChildren().add(UiTheme.spacer());

        // Actions
        if (!isActive) {
            Button applyBtn = UiTheme.goldBtn("Apply Theme");
            applyBtn.setOnAction(e -> {
                themeManager.applyTheme(theme);
                showToast("Theme Applied", "Switched to " + theme.getName(), false);
            });
            topRow.getChildren().add(applyBtn);
        } else {
            Button appliedBtn = UiTheme.secondaryBtn("✓ Applied");
            appliedBtn.setDisable(true);
            topRow.getChildren().add(appliedBtn);
        }

        Button viewCssBtn = UiTheme.smallBtn("View CSS");
        viewCssBtn.setOnAction(e -> showViewCssDialog(theme));
        topRow.getChildren().add(viewCssBtn);

        if (!theme.isBuiltIn()) {
            Button editBtn = UiTheme.smallBtn("Edit");
            editBtn.setOnAction(e -> showThemeEditorDialog(theme, false));

            Button deleteBtn = UiTheme.dangerBtn("Delete");
            deleteBtn.setOnAction(e -> confirmAndDelete(theme));

            topRow.getChildren().addAll(editBtn, deleteBtn);
        } else {
            Button duplicateBtn = UiTheme.smallBtn("Duplicate & Edit");
            duplicateBtn.setTooltip(new Tooltip("Create a customizable copy of this built-in theme"));
            duplicateBtn.setOnAction(e -> showThemeEditorDialog(theme, true));
            topRow.getChildren().add(duplicateBtn);
        }

        // Middle: Description
        Label desc = new Label(theme.getDescription() != null ? theme.getDescription() : "");
        desc.getStyleClass().add("text-muted");
        desc.setWrapText(true);

        // Bottom: Color Preview Swatches
        HBox swatchesRow = buildSwatchesRow(theme);

        card.getChildren().addAll(topRow, desc, swatchesRow);
        return card;
    }

    private HBox buildSwatchesRow(Theme theme) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label("Palette Swatches:");
        label.getStyleClass().add("micro-label");

        row.getChildren().add(label);

        row.getChildren().add(createSwatch("Background (-color-bg)", theme.getBgColor()));
        row.getChildren().add(createSwatch("Surface (-color-surface)", theme.getSurfaceColor()));
        row.getChildren().add(createSwatch("Accent (-color-accent)", theme.getAccentColor()));
        row.getChildren().add(createSwatch("Accent Text (-color-accent-text)", theme.getAccentTextColor()));
        row.getChildren().add(createSwatch("Border (-color-border)", theme.getBorderColor()));
        row.getChildren().add(createSwatch("Success (-color-success)", theme.getSuccessColor()));
        row.getChildren().add(createSwatch("Text (-color-text)", theme.getTextColor()));

        return row;
    }

    private Node createSwatch(String name, String colorValue) {
        StackPane swatch = new StackPane();
        swatch.getStyleClass().add("theme-swatch");
        swatch.setPrefSize(38, 20);
        swatch.setMinSize(38, 20);
        swatch.setMaxSize(38, 20);

        String safeColor = colorValue != null && !colorValue.isBlank() ? colorValue : "#151B25";
        swatch.setStyle("-fx-background-color: " + safeColor + ";");

        Tooltip.install(swatch, new Tooltip(name + ": " + safeColor));
        return swatch;
    }

    private void copyDefaultTemplate() {
        String template = ThemeManager.readDefaultRootCss();
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(template);
        clipboard.setContent(content);
        showToast("Template Copied", "Canonical .root { ... } CSS copied to clipboard.", false);
    }

    private void showViewCssDialog(Theme theme) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Theme CSS — " + theme.getName());
        dialog.setHeaderText("Complete .root CSS block for " + theme.getName());

        TextArea area = new TextArea(theme.getCssContent());
        area.setEditable(false);
        area.setWrapText(false);
        area.setStyle("-fx-font-family: 'JetBrains Mono', 'Consolas', monospace; -fx-font-size: 12px;");
        area.setPrefSize(720, 420);

        Button copyBtn = UiTheme.secondaryBtn("Copy to Clipboard");
        copyBtn.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent c = new ClipboardContent();
            c.putString(theme.getCssContent());
            clipboard.setContent(c);
            showToast("Copied", "Theme CSS copied to clipboard.", false);
        });

        VBox content = new VBox(10, copyBtn, area);
        content.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        DialogHelper.styleDialog(dialog);
        dialog.showAndWait();
    }

    private void showThemeEditorDialog(Theme existingTheme, boolean isDuplicate) {
        Dialog<ButtonType> dialog = new Dialog<>();
        String actionTitle = existingTheme == null ? "Create New Theme"
                : (isDuplicate ? "Duplicate Theme" : "Edit Theme: " + existingTheme.getName());
        dialog.setTitle(actionTitle);
        dialog.setHeaderText("Define or paste a complete .root { ... } CSS token block.");

        TextField nameF = new TextField();
        nameF.setPromptText("Theme Name (e.g. Midnight Sapphire)");

        TextField descF = new TextField();
        descF.setPromptText("Short Description (e.g. Dark navy background with electric cyan accents)");

        String initialCss;
        if (existingTheme != null) {
            nameF.setText(isDuplicate ? existingTheme.getName() + " (Copy)" : existingTheme.getName());
            descF.setText(existingTheme.getDescription() != null ? existingTheme.getDescription() : "");
            initialCss = existingTheme.getCssContent();
        } else {
            nameF.setText("New Custom Theme");
            descF.setText("Custom user-defined theme palette.");
            initialCss = ThemeManager.readDefaultRootCss();
        }

        TextArea cssArea = new TextArea(initialCss);
        cssArea.setWrapText(false);
        cssArea.setStyle("-fx-font-family: 'JetBrains Mono', 'Consolas', monospace; -fx-font-size: 12px;");
        cssArea.setPrefSize(750, 360);

        // Validation Banner
        VBox validationBanner = new VBox(6);
        validationBanner.setPadding(new Insets(10, 12, 10, 12));
        validationBanner.setMaxWidth(Double.MAX_VALUE);

        Label validationTitle = new Label();
        validationTitle.setStyle("-fx-font-weight: bold;");
        Label validationDetails = new Label();
        validationDetails.setWrapText(true);
        validationDetails.setStyle("-fx-font-size: 11px;");

        validationBanner.getChildren().addAll(validationTitle, validationDetails);

        // Live validator update method
        Runnable runValidation = () -> {
            String currentText = cssArea.getText();
            ThemeValidator.ValidationResult res = ThemeValidator.validate(currentText);

            if (res.isValid()) {
                validationBanner.setStyle("-fx-background-color: rgba(16, 185, 129, 0.12); " +
                                          "-fx-border-color: rgba(16, 185, 129, 0.40); " +
                                          "-fx-border-radius: 6px; -fx-background-radius: 6px;");
                validationTitle.setText("✓ Valid Theme — All " + res.getTotalRequired() + " required .root tokens defined.");
                validationTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: -color-success-text;");
                validationDetails.setText("This CSS block is complete and ready to apply across InvoiceStudio.");
                validationDetails.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-success-text;");
            } else {
                validationBanner.setStyle("-fx-background-color: rgba(239, 68, 68, 0.12); " +
                                          "-fx-border-color: rgba(239, 68, 68, 0.40); " +
                                          "-fx-border-radius: 6px; -fx-background-radius: 6px;");
                validationTitle.setText("⚠ Validation Error: " + res.getMissingVariables().size() + " required variable(s) missing from .root");
                validationTitle.setStyle("-fx-font-weight: bold; -fx-text-fill: -color-error-text;");

                String missingSample = String.join(", ", res.getMissingVariables());
                if (missingSample.length() > 220) {
                    missingSample = missingSample.substring(0, 217) + "...";
                }
                validationDetails.setText("Missing variables:\n" + missingSample);
                validationDetails.setStyle("-fx-font-size: 11px; -fx-text-fill: -color-error-text;");
            }
        };

        cssArea.textProperty().addListener((obs, oldV, newV) -> runValidation.run());
        runValidation.run();

        // Helper buttons row above textarea
        HBox helperBar = new HBox(8);
        helperBar.setAlignment(Pos.CENTER_LEFT);

        Label codeLbl = new Label(".root CSS Block:");
        codeLbl.setStyle("-fx-font-weight: bold;");

        Region hSpacer = UiTheme.spacer();

        Button insertTemplateBtn = UiTheme.smallBtn("Insert Default Template");
        insertTemplateBtn.setOnAction(e -> {
            cssArea.setText(ThemeManager.readDefaultRootCss());
            runValidation.run();
        });

        Button pasteBtn = UiTheme.smallBtn("Paste from Clipboard");
        pasteBtn.setOnAction(e -> {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            if (clipboard.hasString()) {
                cssArea.setText(clipboard.getString());
                runValidation.run();
            }
        });

        helperBar.getChildren().addAll(codeLbl, hSpacer, insertTemplateBtn, pasteBtn);

        // Live preview button
        Button testPreviewBtn = UiTheme.secondaryBtn("Test Live Preview");
        testPreviewBtn.setOnAction(e -> {
            ThemeValidator.ValidationResult res = ThemeValidator.validate(cssArea.getText());
            if (!res.isValid()) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Cannot Preview Theme");
                alert.setHeaderText("The CSS block has missing variables.");
                alert.setContentText(res.getErrorMessage());
                DialogHelper.styleDialog(alert);
                alert.showAndWait();
            } else {
                Theme previewTheme = new Theme("preview-temp", nameF.getText().trim(),
                        descF.getText().trim(), ThemeValidator.normalizeCss(cssArea.getText()), false);
                themeManager.applyTheme(previewTheme);
                showToast("Live Preview Applied", "Testing " + previewTheme.getName(), false);
            }
        });

        HBox formBox = new HBox(12,
            new VBox(4, new Label("Theme Name"), nameF),
            new VBox(4, new Label("Description"), descF)
        );
        HBox.setHgrow(nameF, Priority.ALWAYS);
        HBox.setHgrow(descF, Priority.ALWAYS);

        VBox layout = new VBox(12, formBox, helperBar, cssArea, validationBanner, testPreviewBtn);
        layout.setPadding(new Insets(10));
        dialog.getDialogPane().setContent(layout);

        ButtonType saveBtnType = new ButtonType("Save & Apply Theme", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveBtnType, ButtonType.CANCEL);
        DialogHelper.styleDialog(dialog);

        dialog.setResultConverter(buttonType -> {
            if (buttonType == saveBtnType) {
                String name = nameF.getText().trim();
                if (name.isBlank()) {
                    showToast("Validation Error", "Theme name cannot be empty.", true);
                    return null;
                }

                String css = cssArea.getText();
                ThemeValidator.ValidationResult res = ThemeValidator.validate(css);
                if (!res.isValid()) {
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Validation Failed");
                    alert.setHeaderText("Cannot save theme. Missing required CSS variables.");
                    alert.setContentText(res.getErrorMessage());
                    DialogHelper.styleDialog(alert);
                    alert.showAndWait();
                    return null;
                }

                try {
                    String targetId = (existingTheme != null && !isDuplicate) ? existingTheme.getId() : null;
                    Theme saved = themeManager.saveCustomTheme(targetId, name, descF.getText().trim(), css);
                    themeManager.applyTheme(saved);
                    showToast("Theme Saved & Applied", "Saved '" + saved.getName() + "' successfully.", false);
                    rebuildCards();
                } catch (Exception ex) {
                    AppLog.debug(ex);
                    showToast("Save Error", ex.getMessage(), true);
                }
            }
            return buttonType;
        });

        dialog.showAndWait();
    }

    private void confirmAndDelete(Theme theme) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Delete Custom Theme");
        confirm.setHeaderText("Delete theme '" + theme.getName() + "'?");
        confirm.setContentText("This will permanently remove the theme from your vault. This cannot be undone.");
        DialogHelper.styleDialog(confirm);

        confirm.showAndWait().ifPresent(ans -> {
            if (ans == ButtonType.OK) {
                boolean deleted = themeManager.deleteCustomTheme(theme.getId());
                if (deleted) {
                    showToast("Theme Deleted", "Theme '" + theme.getName() + "' removed.", false);
                    rebuildCards();
                } else {
                    showToast("Delete Failed", "Could not delete this theme.", true);
                }
            }
        });
    }

    private void showToast(String title, String msg, boolean isError) {
        if (app != null && app.getRootPane() != null) {
            Toast.show(app.getRootPane(), title, msg, isError);
        }
    }
}
