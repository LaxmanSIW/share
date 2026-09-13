package com.invoicestudio.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Keyboard-shortcuts reference panel, embedded as a Settings tab.
 * The same content also opens as an F1 overlay ({@link ShortcutsDialog}).
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

        Map<String, String[][]> groups = new LinkedHashMap<>();
        groups.put("Global (works on every screen)", new String[][]{
                {"F1", "Toggle shortcuts help popup"},
                {"Ctrl + N", "New Bill (invoice entry)"},
                {"Ctrl + P", "Purchases — record a purchase bill"},
                {"Ctrl + E", "Expenses — new expense voucher"},
                {"Ctrl + B", "Buyers directory"},
                {"Ctrl + D", "Dashboard"},
                {"Esc", "Close dialogs / cancel entry (contextual)"}
        });
        groups.put("Billing & Entry Forms", new String[][]{
                {"Ctrl + S", "Save the bill / purchase / expense being edited"},
                {"Enter", "Confirm focused dialog (OK)"},
                {"Tab / Shift + Tab", "Move between fields in entry forms"}
        });
        groups.put("Template Designer", new String[][]{
                {"Ctrl + S", "Save template"},
                {"Ctrl + Z / Ctrl + Y", "Undo / Redo"},
                {"Ctrl + C / Ctrl + V", "Copy / Paste element"},
                {"Ctrl + D", "Duplicate selected element"},
                {"Ctrl + G", "Toggle grid snap"},
                {"Delete / Backspace", "Delete selected element"},
                {"Arrow keys", "Nudge selected element (Shift = fine move)"},
                {"Ctrl + 0 / Ctrl + +/-", "Zoom fit / zoom in / out"},
                {"Space", "Toggle pan mode"},
                {"V / H / P", "Pen tool: vertical / horizontal / free draw"},
                {"Esc", "Deselect / exit tool"}
        });

        HBox columns = new HBox(40);
        VBox left = new VBox(10);
        VBox right = new VBox(10);
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);

        int i = 0;
        for (Map.Entry<String, String[][]> g : groups.entrySet()) {
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
