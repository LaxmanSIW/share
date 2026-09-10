package com.invoicestudio.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.function.Consumer;

/**
 * Modern, dark-themed custom color chooser dialog with visual sliders,
 * direct hex input, preset swatches, and rock-solid window focus retention
 * on Windows OS.
 */
public class CustomColorChooserDialog extends Stage {

    private Color currentColor;
    private final Consumer<String> onColorChosen;
    private final Window ownerWindow;

    private final Rectangle previewRect = new Rectangle(120, 36);
    private final TextField hexField = new TextField();

    private final Slider redSlider = new Slider(0, 255, 0);
    private final Slider greenSlider = new Slider(0, 255, 0);
    private final Slider blueSlider = new Slider(0, 255, 0);
    private final Slider alphaSlider = new Slider(0, 100, 100);

    private final Label redVal = new Label("0");
    private final Label greenVal = new Label("0");
    private final Label blueVal = new Label("0");
    private final Label alphaVal = new Label("100%");

    private boolean updatingInternally = false;

    public static void show(Window owner, String initialHex, Consumer<String> onColorChosen) {
        show(owner, "Choose Color", initialHex, onColorChosen);
    }

    public static void show(Window owner, String title, String initialHex, Consumer<String> onColorChosen) {
        CustomColorChooserDialog dialog = new CustomColorChooserDialog(owner, initialHex, onColorChosen);
        if (title != null && !title.isBlank()) dialog.setTitle(title);
        dialog.showAndWait();
    }

    public static Button createColorButton(String initialHex, Window owner, Consumer<String> onColorChosen) {
        Button btn = new Button();
        btn.getStyleClass().add("custom-color-btn");
        updateButtonVisual(btn, initialHex);

        btn.setOnAction(e -> {
            String current = (String) btn.getUserData();
            show(owner, current != null ? current : initialHex, newHex -> {
                btn.setUserData(newHex);
                updateButtonVisual(btn, newHex);
                if (onColorChosen != null) onColorChosen.accept(newHex);
            });
        });
        return btn;
    }

    public static void updateButtonVisual(Button btn, String hex) {
        String clean = (hex != null && !hex.isBlank()) ? hex : "#1A1A1A";
        btn.setUserData(clean);
        btn.setStyle(String.format(
                "-fx-background-color: %s; -fx-border-color: #475569; -fx-border-width: 1.5; "
                + "-fx-border-radius: 6; -fx-background-radius: 6; -fx-pref-width: 32; -fx-pref-height: 28; "
                + "-fx-min-width: 32; -fx-min-height: 28; -fx-cursor: hand;", clean));
        btn.setTooltip(new Tooltip("Color: " + clean + " (Click to change)"));
    }

    public CustomColorChooserDialog(Window owner, String initialHex, Consumer<String> onColorChosen) {
        this.ownerWindow = owner;
        this.onColorChosen = onColorChosen;

        initModality(Modality.APPLICATION_MODAL);
        if (owner != null) initOwner(owner);
        initStyle(StageStyle.DECORATED);
        setTitle("Color Chooser");
        setResizable(false);

        this.currentColor = parseColor(initialHex);

        VBox root = new VBox(16);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("custom-color-dialog");
        root.setStyle("-fx-background-color: #0E131B; -fx-text-fill: #E2E8F0;");

        // 1. Top Preview & Hex Row
        HBox previewRow = new HBox(16);
        previewRow.setAlignment(Pos.CENTER_LEFT);

        previewRect.setArcWidth(8);
        previewRect.setArcHeight(8);
        previewRect.setStroke(Color.web("#475569"));
        previewRect.setStrokeWidth(1.5);

        VBox hexBox = new VBox(4);
        Label hexLabel = new Label("Hex Color Code:");
        hexLabel.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 11px; -fx-font-weight: bold;");
        hexField.setPrefWidth(120);
        hexField.setStyle("-fx-background-color: #1A2433; -fx-text-fill: #F8FAFC; -fx-border-color: #334155; -fx-border-radius: 6; -fx-padding: 6 10; -fx-font-family: monospace; -fx-font-weight: bold;");
        hexField.textProperty().addListener((obs, old, val) -> {
            if (updatingInternally) return;
            try {
                if (val != null && (val.startsWith("#") && (val.length() == 7 || val.length() == 9))) {
                    Color c = Color.web(val);
                    setColor(c, false);
                }
            } catch (Exception ignored) {}
        });

        hexBox.getChildren().addAll(hexLabel, hexField);
        previewRow.getChildren().addAll(previewRect, hexBox);
        root.getChildren().add(previewRow);

        // 2. Sliders Grid
        GridPane slidersGrid = new GridPane();
        slidersGrid.setHgap(12);
        slidersGrid.setVgap(10);
        slidersGrid.setAlignment(Pos.CENTER_LEFT);

        addSliderRow(slidersGrid, 0, "Red:", redSlider, redVal, "#EF4444");
        addSliderRow(slidersGrid, 1, "Green:", greenSlider, greenVal, "#22C55E");
        addSliderRow(slidersGrid, 2, "Blue:", blueSlider, blueVal, "#3B82F6");
        addSliderRow(slidersGrid, 3, "Opacity:", alphaSlider, alphaVal, "#CBD5E1");

        root.getChildren().add(slidersGrid);

        // 3. Preset Palette
        VBox presetBox = new VBox(6);
        Label presetLbl = new Label("Quick Palette:");
        presetLbl.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 11px; -fx-font-weight: bold;");

        FlowPane presets = new FlowPane(6, 6);
        presets.setPrefWrapLength(320);

        String[] palette = {
                "#000000", "#1A1A1A", "#334155", "#64748B", "#94A3B8", "#E2E8F0", "#FFFFFF",
                "#D9A13B", "#F2CA6B", "#B45309", "#2563EB", "#38BDF8", "#16A34A", "#4ADE80",
                "#DC2626", "#F87171", "#7C3AED", "#C084FC", "#EA580C", "#0D9488"
        };

        for (String hex : palette) {
            Button chip = new Button();
            chip.setPrefSize(24, 24);
            chip.setMinSize(24, 24);
            chip.setMaxSize(24, 24);
            chip.setStyle("-fx-background-color: " + hex + "; -fx-border-color: #475569; -fx-border-width: 1; -fx-border-radius: 4; -fx-background-radius: 4; -fx-cursor: hand;");
            chip.setOnAction(e -> setColor(Color.web(hex), true));
            chip.setTooltip(new Tooltip(hex));
            presets.getChildren().add(chip);
        }

        presetBox.getChildren().addAll(presetLbl, presets);
        root.getChildren().add(presetBox);

        // 4. Action Buttons Row
        HBox actions = new HBox(12);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(10, 0, 0, 0));

        Button cancelBtn = new Button("Cancel");
        cancelBtn.setStyle("-fx-background-color: #1E293B; -fx-text-fill: #E2E8F0; -fx-border-color: #334155; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 8 18; -fx-cursor: hand;");
        cancelBtn.setOnAction(e -> closeAndRestoreFocus());

        Button useBtn = new Button("Use Color");
        useBtn.setStyle("-fx-background-color: #D9A13B; -fx-text-fill: #0B0E13; -fx-font-weight: bold; -fx-background-radius: 6; -fx-padding: 8 22; -fx-cursor: hand;");
        useBtn.setOnAction(e -> {
            if (onColorChosen != null) {
                onColorChosen.accept(colorToHex(currentColor));
            }
            closeAndRestoreFocus();
        });

        actions.getChildren().addAll(cancelBtn, useBtn);
        root.getChildren().add(actions);

        Scene scene = new Scene(root, 360, 420);
        setScene(scene);

        setColor(this.currentColor, true);

        setOnCloseRequest(e -> closeAndRestoreFocus());
    }

    private void addSliderRow(GridPane grid, int row, String label, Slider slider, Label valLbl, String accent) {
        Label lbl = new Label(label);
        lbl.setPrefWidth(55);
        lbl.setStyle("-fx-text-fill: #CBD5E1; -fx-font-size: 12px;");

        slider.setPrefWidth(180);
        slider.valueProperty().addListener((obs, old, val) -> {
            if (updatingInternally) return;
            onSliderChanged();
        });

        valLbl.setPrefWidth(45);
        valLbl.setAlignment(Pos.CENTER_RIGHT);
        valLbl.setStyle("-fx-text-fill: " + accent + "; -fx-font-family: monospace; -fx-font-weight: bold;");

        grid.add(lbl, 0, row);
        grid.add(slider, 1, row);
        grid.add(valLbl, 2, row);
    }

    private void onSliderChanged() {
        int r = (int) Math.round(redSlider.getValue());
        int g = (int) Math.round(greenSlider.getValue());
        int b = (int) Math.round(blueSlider.getValue());
        double a = alphaSlider.getValue() / 100.0;

        Color c = Color.rgb(r, g, b, a);
        setColor(c, false);
    }

    private void setColor(Color c, boolean updateSliders) {
        if (c == null) c = Color.BLACK;
        this.currentColor = c;

        previewRect.setFill(c);

        updatingInternally = true;
        try {
            hexField.setText(colorToHex(c));

            if (updateSliders) {
                redSlider.setValue(Math.round(c.getRed() * 255));
                greenSlider.setValue(Math.round(c.getGreen() * 255));
                blueSlider.setValue(Math.round(c.getBlue() * 255));
                alphaSlider.setValue(Math.round(c.getOpacity() * 100));
            }

            redVal.setText(String.valueOf((int) Math.round(c.getRed() * 255)));
            greenVal.setText(String.valueOf((int) Math.round(c.getGreen() * 255)));
            blueVal.setText(String.valueOf((int) Math.round(c.getBlue() * 255)));
            alphaVal.setText((int) Math.round(c.getOpacity() * 100) + "%");
        } finally {
            updatingInternally = false;
        }
    }

    private void closeAndRestoreFocus() {
        close();
        if (ownerWindow != null) {
            ownerWindow.requestFocus();
            if (ownerWindow instanceof Stage s) {
                s.toFront();
            }
        }
    }

    private static Color parseColor(String hex) {
        if (hex == null || hex.isBlank() || "transparent".equalsIgnoreCase(hex)) {
            return Color.BLACK;
        }
        try {
            return Color.web(hex);
        } catch (Exception e) {
            return Color.BLACK;
        }
    }

    private static String colorToHex(Color c) {
        if (c == null) return "#000000";
        int r = (int) Math.round(c.getRed() * 255);
        int g = (int) Math.round(c.getGreen() * 255);
        int b = (int) Math.round(c.getBlue() * 255);
        if (c.getOpacity() < 0.999) {
            int a = (int) Math.round(c.getOpacity() * 255);
            return String.format("#%02X%02X%02X%02X", r, g, b, a);
        }
        return String.format("#%02X%02X%02X", r, g, b);
    }
}
