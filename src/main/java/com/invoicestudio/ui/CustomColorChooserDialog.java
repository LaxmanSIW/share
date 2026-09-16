package com.invoicestudio.ui;

import com.invoicestudio.service.AppLog;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Robot;
import java.util.function.Consumer;

/**
 * Modern, dark-themed custom color chooser dialog with visual sliders,
 * direct hex input, preset swatches, desktop screen eyedropper, and rock-solid
 * window focus retention on Windows OS.
 */
public class CustomColorChooserDialog extends Stage {

    public static final String DROPPER_ICON_PATH = "M20.71 5.63l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-3.12 3.12-1.93-1.91-1.41 1.41 1.42 1.42L3 16.25V21h4.75l8.92-8.92 1.42 1.42 1.41-1.41-1.92-1.92 3.12-3.12c.4-.4.4-1.03.01-1.42zM6.92 19L5 17.08l8.06-8.06 1.92 1.92L6.92 19z";

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
        DialogHelper.applyAppIcon(this); // logo in title bar from the very first frame
        initStyle(StageStyle.DECORATED);
        setTitle("Color Chooser");
        setResizable(false);

        this.currentColor = parseColor(initialHex);

        VBox root = new VBox(16);
        root.setPadding(new Insets(20));
        root.getStyleClass().add("custom-color-dialog");
        root.setStyle("-fx-background-color: #0E131B; -fx-text-fill: #E2E8F0;");

        // 1. Top Preview & Hex Row with Dropper
        HBox previewRow = new HBox(14);
        previewRow.setAlignment(Pos.CENTER_LEFT);

        previewRect.setArcWidth(8);
        previewRect.setArcHeight(8);
        previewRect.setStroke(Color.web("#475569"));
        previewRect.setStrokeWidth(1.5);

        VBox hexBox = new VBox(4);
        Label hexLabel = new Label("Hex Color Code:");
        hexLabel.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 11px; -fx-font-weight: bold;");

        HBox hexInputRow = new HBox(8);
        hexInputRow.setAlignment(Pos.CENTER_LEFT);

        hexField.setPrefWidth(105);
        hexField.setStyle("-fx-background-color: #1A2433; -fx-text-fill: #F8FAFC; -fx-border-color: #334155; -fx-border-radius: 6; -fx-padding: 6 10; -fx-font-family: monospace; -fx-font-weight: bold;");
        hexField.textProperty().addListener((obs, old, val) -> {
            if (updatingInternally) return;
            try {
                if (val != null && (val.startsWith("#") && (val.length() == 7 || val.length() == 9))) {
                    Color c = Color.web(val);
                    setColor(c, false);
                }
            } catch (Exception ignored) {
            AppLog.debug(ignored); }
        });

        Button dropperBtn = new Button();
        dropperBtn.getStyleClass().add("button-icon-subtle");
        dropperBtn.setStyle("-fx-background-color: #1A2433; -fx-border-color: #334155; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 6 9; -fx-cursor: hand;");
        dropperBtn.setTooltip(new Tooltip("Pick Color from Screen / Desktop / Image (Eyedropper)"));

        SVGPath dropperIcon = new SVGPath();
        dropperIcon.setContent(DROPPER_ICON_PATH);
        dropperIcon.setFill(Color.web("#F2CA6B"));
        dropperIcon.setScaleX(0.7);
        dropperIcon.setScaleY(0.7);
        dropperBtn.setGraphic(dropperIcon);

        dropperBtn.setOnMouseEntered(e -> {
            dropperBtn.setStyle("-fx-background-color: #243042; -fx-border-color: #F2CA6B; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 6 9; -fx-cursor: hand;");
            dropperIcon.setFill(Color.web("#FFD700"));
        });
        dropperBtn.setOnMouseExited(e -> {
            dropperBtn.setStyle("-fx-background-color: #1A2433; -fx-border-color: #334155; -fx-border-radius: 6; -fx-background-radius: 6; -fx-padding: 6 9; -fx-cursor: hand;");
            dropperIcon.setFill(Color.web("#F2CA6B"));
        });

        dropperBtn.setOnAction(e -> {
            pickColorFromScreen(this, pickedColor -> {
                setColor(pickedColor, true);
            });
        });

        hexInputRow.getChildren().addAll(hexField, dropperBtn);
        hexBox.getChildren().addAll(hexLabel, hexInputRow);
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

    public static void pickColorFromScreen(Window owner, Consumer<Color> onColorPicked) {
        try {
            Robot awtRobot = new Robot();

            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            for (Screen screen : Screen.getScreens()) {
                Rectangle2D b = screen.getBounds();
                minX = Math.min(minX, b.getMinX());
                minY = Math.min(minY, b.getMinY());
                maxX = Math.max(maxX, b.getMaxX());
                maxY = Math.max(maxY, b.getMaxY());
            }
            double totalW = maxX - minX;
            double totalH = maxY - minY;

            Stage pickerStage = new Stage(StageStyle.TRANSPARENT);
            if (owner != null) {
                pickerStage.initOwner(owner);
            }
            pickerStage.initModality(Modality.APPLICATION_MODAL);
            pickerStage.setAlwaysOnTop(true);
            pickerStage.setX(minX);
            pickerStage.setY(minY);
            pickerStage.setWidth(totalW);
            pickerStage.setHeight(totalH);

            Pane overlay = new Pane();
            overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.005);");
            overlay.setCursor(Cursor.CROSSHAIR);

            // Floating Magnifier / Color Inspector HUD
            VBox loupe = new VBox(4);
            loupe.setStyle(
                "-fx-background-color: #0E131B; " +
                "-fx-border-color: #F2CA6B; " +
                "-fx-border-width: 1.5; " +
                "-fx-border-radius: 8; " +
                "-fx-background-radius: 8; " +
                "-fx-padding: 8 12; " +
                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.75), 14, 0, 0, 4);"
            );
            loupe.setMouseTransparent(true);

            HBox topRow = new HBox(8);
            topRow.setAlignment(Pos.CENTER_LEFT);

            Region swatch = new Region();
            swatch.setPrefSize(24, 24);
            swatch.setMinSize(24, 24);
            swatch.setMaxSize(24, 24);
            swatch.setStyle("-fx-border-radius: 4; -fx-background-radius: 4; -fx-border-color: #475569; -fx-border-width: 1;");

            VBox labels = new VBox(1);
            Label hexLbl = new Label("#FFFFFF");
            hexLbl.setStyle("-fx-font-family: monospace; -fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #F8FAFC;");

            Label rgbLbl = new Label("RGB(255, 255, 255)");
            rgbLbl.setStyle("-fx-font-family: monospace; -fx-font-size: 10.5px; -fx-text-fill: #94A3B8;");

            labels.getChildren().addAll(hexLbl, rgbLbl);
            topRow.getChildren().addAll(swatch, labels);

            Label hintLbl = new Label("Click to pick • ESC to cancel");
            hintLbl.setStyle("-fx-font-size: 9.5px; -fx-text-fill: #F2CA6B; -fx-font-weight: bold;");

            loupe.getChildren().addAll(topRow, hintLbl);
            overlay.getChildren().add(loupe);

            Scene scene = new Scene(overlay, totalW, totalH, Color.TRANSPARENT);

            scene.setOnMouseMoved(e -> {
                try {
                    Point pt = MouseInfo.getPointerInfo().getLocation();
                    java.awt.Color awtCol = awtRobot.getPixelColor(pt.x, pt.y);
                    Color fxCol = Color.rgb(awtCol.getRed(), awtCol.getGreen(), awtCol.getBlue());
                    String hex = colorToHex(fxCol);

                    swatch.setStyle("-fx-background-color: " + hex + "; -fx-border-radius: 4; -fx-background-radius: 4; -fx-border-color: #475569; -fx-border-width: 1;");
                    hexLbl.setText(hex);
                    rgbLbl.setText(String.format("RGB(%d, %d, %d)", awtCol.getRed(), awtCol.getGreen(), awtCol.getBlue()));

                    double lx = e.getSceneX() + 22;
                    double ly = e.getSceneY() + 22;
                    if (lx + 170 > totalW) lx = e.getSceneX() - 180;
                    if (ly + 70 > totalH) ly = e.getSceneY() - 80;
                    loupe.setLayoutX(lx);
                    loupe.setLayoutY(ly);
                } catch (Exception ignored) {
            AppLog.debug(ignored); }
            });

            scene.setOnMouseClicked(e -> {
                try {
                    Point pt = MouseInfo.getPointerInfo().getLocation();
                    java.awt.Color awtCol = awtRobot.getPixelColor(pt.x, pt.y);
                    Color fxCol = Color.rgb(awtCol.getRed(), awtCol.getGreen(), awtCol.getBlue());
                    pickerStage.close();
                    if (onColorPicked != null) {
                        onColorPicked.accept(fxCol);
                    }
                } catch (Exception ex) {
                    pickerStage.close();
                }
            });

            scene.setOnKeyPressed(e -> {
                if (e.getCode() == KeyCode.ESCAPE) {
                    pickerStage.close();
                }
            });

            pickerStage.setScene(scene);
            pickerStage.show();
        } catch (Exception e) {
            com.invoicestudio.service.AppLog.error(e);
        }
    }

    public static Color parseColor(String hex) {
        if (hex == null || hex.isBlank() || "transparent".equalsIgnoreCase(hex)) {
            return Color.BLACK;
        }
        try {
            return Color.web(hex);
        } catch (Exception e) {
            return Color.BLACK;
        }
    }

    public static String colorToHex(Color c) {
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
