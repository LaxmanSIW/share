package com.invoicestudio.ui.views;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Shared control factories for the Settings tabs (skill rule 5.2 role 3:
 * stateless builders). Extracted verbatim from {@link SettingsView}.
 */
final class SettingsFieldSupport {

    private SettingsFieldSupport() {}

    /**
     * Auto-expanding labeled TextArea with a manual expand/collapse toggle.
     * Extracted verbatim; behavior unchanged.
     */
    static VBox createExpandableField(String labelText, TextArea ta, String prompt, int minRows, int maxRows) {
        ta.setPromptText(prompt);
        ta.setWrapText(true);
        ta.setPrefRowCount(minRows);
        ta.getStyleClass().add("setting-expandable-textbox");

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
        toggleBtn.getStyleClass().add("link-toggle");
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

    /** Slug used for {{buyer_<key>}} variable keys. */
    static String slugify(String s) {
        if (s == null) return "";
        return s.toLowerCase().replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
