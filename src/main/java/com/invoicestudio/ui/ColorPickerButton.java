package com.invoicestudio.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Themed, fully in-app replacement for {@code javafx.scene.control.ColorPicker}.
 *
 * <p>Why not the built-in ColorPicker? Its "Custom Color…" sub-dialog is a
 * separate native-styled stage whose <em>Use</em> / <em>Save</em> buttons stole
 * focus from (and visually dropped the user out of) the application, and it
 * could not be themed to match the Obsidian &amp; Gold design. This control
 * opens a normal themed Dialog instead: preset swatches, previously saved
 * custom colors, RGB sliders, a hex field and a live preview.</p>
 *
 * <p>Usage:
 * <pre>{@code
 * ColorPickerButton colorBtn = new ColorPickerButton(el.getColor(), false, hex -> {
 *     el.setColor(hex);
 *     refreshCanvas();
 * });
 * }</pre>
 * The callback fires once, when the user presses <b>Use Color</b> (or clicks a
 * preset swatch).</p>
 */
public class ColorPickerButton extends Button {

    /** Quick palette shown in every picker (row 1 = neutrals, row 2 = accents, row 3 = print colors). */
    private static final String[] PRESETS = {
            "#FFFFFF", "#F4F1EA", "#E2E8F0", "#94A3B8", "#475569", "#1E293B", "#1A1A1A", "#000000",
            "#D9A13B", "#F59E0B", "#DC2626", "#EC4899", "#7C3AED", "#2563EB", "#0EA5E9", "#10B981",
            "#16A34A", "#065F46", "#92400E", "#7C2D12", "#334155", "#64748B", "#CBD5E1", "#F8FAFC"
    };

    private static final String PREF_KEY = "savedCustomColors";

    /** Custom colors the user pressed “Save” on — persisted across sessions. */
    private static final List<String> SAVED_COLORS = new ArrayList<>();

    static {
        try {
            String raw = Preferences.userNodeForPackage(ColorPickerButton.class).get(PREF_KEY, "");
            for (String s : raw.split(",")) {
                if (s != null && s.startsWith("#") && s.length() >= 7) SAVED_COLORS.add(s.substring(0, 7));
            }
        } catch (Exception ignored) {
        }
    }

    private String hex;                 // "#RRGGBB" or "transparent"
    private final boolean allowTransparent;
    private final Consumer<String> onColor;
    private final Region swatch = new Region();
    private final Label hexLbl = new Label();

    public ColorPickerButton(String initialHex, boolean allowTransparent, Consumer<String> onColor) {
        this.allowTransparent = allowTransparent;
        this.onColor = onColor;
        getStyleClass().addAll("color-btn");
        setTooltip(new Tooltip("Pick a color (presets, saved colors, RGB sliders & hex)"));

        swatch.getStyleClass().add("color-btn-swatch");
        swatch.setMinSize(22, 22);
        swatch.setPrefSize(22, 22);
        swatch.setMaxSize(22, 22);
        hexLbl.getStyleClass().add("color-btn-hex");
        HBox graphic = new HBox(6, swatch, hexLbl);
        graphic.setAlignment(Pos.CENTER_LEFT);
        setGraphic(graphic);

        setHex(initialHex);
        setOnAction(e -> showPickerDialog());
    }

    /* ------------------------------------------------------------ state --- */

    public String getHex() {
        return hex;
    }

    /** Sets the current value programmatically (does NOT fire the callback). */
    public void setHex(String value) {
        this.hex = normalizeInstance(value);
        refreshSwatch();
    }

    private String normalizeInstance(String value) {
        if (value == null || value.isBlank() || "transparent".equalsIgnoreCase(value) || "none".equalsIgnoreCase(value)) {
            return allowTransparent ? "transparent" : "#FFFFFF";
        }
        value = value.trim();
        if (!value.startsWith("#")) value = "#" + value;
        try {
            Color c = Color.web(value);
            return String.format("#%02X%02X%02X",
                    Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
        } catch (Exception e) {
            return "#000000";
        }
    }

    private void refreshSwatch() {
        if ("transparent".equals(hex)) {
            swatch.setStyle("-fx-background-color: linear-gradient(to bottom right, #d8dce6 49%, #ffffff 50%);"
                    + "-fx-background-radius: 4; -fx-border-color: #39445A; -fx-border-radius: 4; -fx-border-width: 1;");
            hexLbl.setText("transparent");
        } else {
            swatch.setStyle("-fx-background-color: " + hex + ";"
                    + "-fx-background-radius: 4; -fx-border-color: #39445A; -fx-border-radius: 4; -fx-border-width: 1;");
            hexLbl.setText(hex);
        }
    }

    /* ------------------------------------------------------------ dialog --- */

    private void showPickerDialog() {
        Dialog<String> dlg = new Dialog<>();
        dlg.setTitle("Choose Color");
        dlg.setHeaderText("Choose Color");
        DialogHelper.styleDialog(dlg, 430, 540);

        String initial = "transparent".equals(hex) && !allowTransparent ? "#FFFFFF" : hex;
        Color initialColor = "transparent".equals(initial) ? Color.WHITE : Color.web(initial);

        final String[] working = {initial};   // current in-dialog selection
        final boolean[] syncing = {false};    // slider <-> hex update guard

        Slider rS = new Slider(0, 255, initialColor.getRed() * 255);
        Slider gS = new Slider(0, 255, initialColor.getGreen() * 255);
        Slider bS = new Slider(0, 255, initialColor.getBlue() * 255);
        TextField hexField = new TextField(initial);
        hexField.setPromptText("#RRGGBB");
        hexField.setPrefWidth(120);

        Region previewSwatch = new Region();
        previewSwatch.getStyleClass().add("color-preview-swatch");
        previewSwatch.setPrefSize(48, 48);
        previewSwatch.setMinSize(48, 48);
        previewSwatch.setMaxSize(48, 48);
        Label previewLbl = new Label(initial);
        previewLbl.getStyleClass().add("color-btn-hex");

        Runnable slidersToHex = () -> {
            if (syncing[0]) return;
            syncing[0] = true;
            working[0] = String.format("#%02X%02X%02X",
                    (int) Math.round(rS.getValue()), (int) Math.round(gS.getValue()), (int) Math.round(bS.getValue()));
            hexField.setText(working[0]);
            updatePreview(previewSwatch, previewLbl, working[0]);
            syncing[0] = false;
        };
        rS.valueProperty().addListener((o, a, b) -> slidersToHex.run());
        gS.valueProperty().addListener((o, a, b) -> slidersToHex.run());
        bS.valueProperty().addListener((o, a, b) -> slidersToHex.run());

        hexField.textProperty().addListener((o, oldV, v) -> {
            if (syncing[0]) return;
            String v2 = v == null ? "" : v.trim();
            if (!v2.startsWith("#")) v2 = "#" + v2;
            try {
                Color c = Color.web(v2);
                syncing[0] = true;
                rS.setValue(Math.round(c.getRed() * 255));
                gS.setValue(Math.round(c.getGreen() * 255));
                bS.setValue(Math.round(c.getBlue() * 255));
                working[0] = String.format("#%02X%02X%02X",
                        Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
                updatePreview(previewSwatch, previewLbl, working[0]);
                syncing[0] = false;
            } catch (Exception ignored) {
                // incomplete typing — wait for a valid value
            }
        });

        VBox content = new VBox(10);
        content.setPadding(new Insets(4, 2, 2, 2));

        // --- transparent chip (only where the target property supports it) ---
        if (allowTransparent) {
            Button transBtn = new Button("Transparent (no fill)");
            transBtn.getStyleClass().addAll("color-swatch-chip");
            transBtn.setMaxWidth(Double.MAX_VALUE);
            transBtn.setTooltip(new Tooltip("Use no color at all"));
            transBtn.setOnAction(e -> {
                working[0] = "transparent";
                updatePreview(previewSwatch, previewLbl, "transparent");
                dlg.setResult("transparent"); // applies & closes
            });
            content.getChildren().add(transBtn);
        }

        // --- preset swatches ---
        Label presetTitle = new Label("PRESET COLORS");
        presetTitle.getStyleClass().add("overline-accent");
        FlowPane presets = new FlowPane(6, 6);
        presets.setPrefWrapLength(390);
        for (String p : PRESETS) {
            presets.getChildren().add(makeSwatchButton(p, working, rS, gS, bS, hexField, previewSwatch, previewLbl, syncing, dlg));
        }

        // --- previously saved custom colors ---
        Label savedTitle = new Label("SAVED CUSTOM COLORS");
        savedTitle.getStyleClass().add("overline-accent");
        FlowPane savedRow = new FlowPane(6, 6);
        savedRow.setPrefWrapLength(390);
        for (String s : SAVED_COLORS) {
            savedRow.getChildren().add(makeSwatchButton(s, working, rS, gS, bS, hexField, previewSwatch, previewLbl, syncing, dlg));
        }

        // --- custom area ---
        Label customTitle = new Label("CUSTOM COLOR");
        customTitle.getStyleClass().add("overline-accent");

        GridPane rgbGrid = new GridPane();
        rgbGrid.setHgap(8);
        rgbGrid.setVgap(6);
        rgbGrid.add(new Label("R"), 0, 0);
        rgbGrid.add(rS, 1, 0);
        rgbGrid.add(new Label("G"), 0, 1);
        rgbGrid.add(gS, 1, 1);
        rgbGrid.add(new Label("B"), 0, 2);
        rgbGrid.add(bS, 1, 2);
        rgbGrid.add(new Label("Hex"), 0, 3);
        rgbGrid.add(hexField, 1, 3);
        GridPane.setHgrow(rS, Priority.ALWAYS);
        GridPane.setHgrow(gS, Priority.ALWAYS);
        GridPane.setHgrow(bS, Priority.ALWAYS);

        VBox previewBox = new VBox(6, previewSwatch, previewLbl);
        previewBox.setAlignment(Pos.CENTER);
        HBox customRow = new HBox(16, rgbGrid, previewBox);
        customRow.setAlignment(Pos.TOP_LEFT);

        content.getChildren().addAll(presetTitle, presets);
        if (!SAVED_COLORS.isEmpty()) content.getChildren().addAll(savedTitle, savedRow);
        content.getChildren().addAll(customTitle, customRow);
        updatePreview(previewSwatch, previewLbl, working[0]);
        dlg.getDialogPane().setContent(content);

        // Buttons: Save (remember this color) / Use (apply) / Cancel
        ButtonType saveType = new ButtonType("Save Color", ButtonBar.ButtonData.OTHER);
        ButtonType useType = new ButtonType("Use Color", ButtonBar.ButtonData.APPLY);
        dlg.getDialogPane().getButtonTypes().addAll(saveType, useType, ButtonType.CANCEL);

        dlg.getDialogPane().lookupButton(saveType).addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            String current = working[0];
            if (!"transparent".equals(current) && !SAVED_COLORS.contains(current)) {
                SAVED_COLORS.add(0, current);
                while (SAVED_COLORS.size() > 16) SAVED_COLORS.remove(SAVED_COLORS.size() - 1);
                try {
                    Preferences.userNodeForPackage(ColorPickerButton.class)
                            .put(PREF_KEY, String.join(",", SAVED_COLORS));
                } catch (Exception ignored) {
                }
                // live-update the saved row so the user sees the new swatch
                savedRow.getChildren().clear();
                for (String s : SAVED_COLORS) {
                    savedRow.getChildren().add(makeSwatchButton(s, working, rS, gS, bS, hexField,
                            previewSwatch, previewLbl, syncing, dlg));
                }
                if (content.getChildren().contains(customTitle) && !content.getChildren().contains(savedTitle)) {
                    content.getChildren().add(2, savedTitle);
                    content.getChildren().add(3, savedRow);
                }
            }
            ev.consume(); // Save does not close the dialog — keep picking
        });

        dlg.setResultConverter(bt -> bt != null && bt.getButtonData() == ButtonBar.ButtonData.APPLY ? working[0] : null);
        dlg.showAndWait().ifPresent(result -> {
            setHex(result);
            if (onColor != null) onColor.accept(hex);
        });
    }

    /** Small swatch button: click = apply & close (with sliders synced). */
    private Button makeSwatchButton(String color, String[] working,
                                    Slider rS, Slider gS, Slider bS, TextField hexField,
                                    Region previewSwatch, Label previewLbl, boolean[] syncing,
                                    Dialog<String> dlg) {
        Button b = new Button();
        b.getStyleClass().add("color-swatch");
        b.setMinSize(24, 24);
        b.setMaxSize(24, 24);
        b.setStyle("-fx-background-color: " + color + "; -fx-border-color: #39445A; -fx-border-width: 1;"
                + " -fx-background-radius: 5; -fx-border-radius: 5; -fx-cursor: hand;");
        b.setTooltip(new Tooltip(color));
        b.setOnAction(e -> {
            working[0] = color;
            syncing[0] = true;
            try {
                Color c = Color.web(color);
                rS.setValue(Math.round(c.getRed() * 255));
                gS.setValue(Math.round(c.getGreen() * 255));
                bS.setValue(Math.round(c.getBlue() * 255));
            } catch (Exception ignored) {
            }
            hexField.setText(color);
            syncing[0] = false;
            updatePreview(previewSwatch, previewLbl, color);
            dlg.setResult(color); // apply & close
        });
        return b;
    }

    private static void updatePreview(Region swatchNode, Label lbl, String value) {
        if ("transparent".equals(value)) {
            swatchNode.setStyle("-fx-background-color: linear-gradient(to bottom right, #d8dce6 49%, #ffffff 50%);"
                    + "-fx-background-radius: 6; -fx-border-color: #39445A; -fx-border-radius: 6; -fx-border-width: 1;");
            lbl.setText("transparent");
        } else {
            swatchNode.setStyle("-fx-background-color: " + value + ";"
                    + "-fx-background-radius: 6; -fx-border-color: #39445A; -fx-border-radius: 6; -fx-border-width: 1;");
            lbl.setText(value);
        }
    }

    /* --------------------------------------------------------- utilities --- */

    /** Converts a JavaFX Color to #RRGGBB (alpha dropped; TRANSPARENT → "transparent"). */
    public static String toHex(Color c) {
        if (c == null) return "#000000";
        if (c.equals(Color.TRANSPARENT)) return "transparent";
        return String.format("#%02X%02X%02X",
                Math.round(c.getRed() * 255), Math.round(c.getGreen() * 255), Math.round(c.getBlue() * 255));
    }

    /** Parses "#RRGGBB" (or "transparent") to a JavaFX Color with fallback. */
    public static Color parseColor(String hex, Color fallback) {
        if (hex == null || hex.isBlank() || "transparent".equalsIgnoreCase(hex)) return fallback;
        try {
            return Color.web(hex);
        } catch (Exception e) {
            return fallback;
        }
    }
}
