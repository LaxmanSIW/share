package com.invoicestudio.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;

/**
 * Keyboard-shortcuts reference panel, embedded as a Settings tab.
 * Content comes from {@link ShortcutCatalog} — the same data that powers
 * the F1 overlay ({@link ShortcutsDialog}), so both always stay in sync.
 */
public class ShortcutsPanel extends VBox {

    public ShortcutsPanel() {
        setSpacing(14);
        setPadding(new Insets(18));
        getStyleClass().add("view-page");

        Label title = new Label("Keyboard Shortcuts");
        title.getStyleClass().add("heading-m");
        Label sub = new Label("Press F1 anywhere to toggle this help as a popup · Esc closes dialogs");
        sub.getStyleClass().add("text-muted");
        getChildren().addAll(title, sub, new Separator());

        var groups = ShortcutCatalog.groups();

        HBox columns = new HBox(40);
        VBox left = new VBox(10);
        VBox right = new VBox(10);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        int i = 0;
        for (var g : groups.entrySet()) {
            VBox target = (i++ % 2 == 0) ? left : right;
            Label gl = new Label(g.getKey());
            gl.getStyleClass().add("section-eyebrow");
            target.getChildren().add(gl);
            for (String[] row : g.getValue()) {
                target.getChildren().add(shortcutRow(row[0], row[1]));
            }
        }
        columns.getChildren().addAll(left, right);
        getChildren().add(columns);
    }

    private HBox shortcutRow(String keys, String description) {
        HBox h = new HBox(12);
        h.setAlignment(Pos.CENTER_LEFT);
        Label k = new Label(keys);
        k.getStyleClass().addAll("gstin-badge", "table-cell-mono");
        k.setMinWidth(160);
        Label d = new Label(description);
        d.getStyleClass().add("table-cell-secondary");
        h.getChildren().addAll(k, d);
        return h;
    }
}
