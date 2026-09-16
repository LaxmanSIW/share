package com.invoicestudio.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Header Help (F1) — themed overlay dialog listing every keyboard shortcut
 * in the application, grouped by area. Closes on ✕ button or Escape.
 *
 * Rendered as a full-root overlay (same convention as {@link com.invoicestudio.ui.auth.LogoutDialog}).
 */
public class ShortcutsDialog extends StackPane {

    private final Runnable onClose;

    public ShortcutsDialog(Runnable onClose) {
        this.onClose = onClose;
        getStyleClass().add("overlay-dim");
        setStyle("-fx-background-color: rgba(4, 7, 12, 0.72);");
        setAlignment(Pos.CENTER);

        VBox card = new VBox(12);
        card.getStyleClass().add("card-pane");
        card.setPadding(new Insets(22));
        card.setMaxWidth(760);
        card.setMaxHeight(Double.MAX_VALUE);

        // Header row
        HBox head = new HBox(12);
        head.setAlignment(Pos.CENTER_LEFT);
        VBox titleBox = new VBox(2);
        Label title = new Label("Keyboard Shortcuts");
        title.getStyleClass().add("heading-m");
        Label sub = new Label("Press F1 anywhere to open this help · Esc to close");
        sub.getStyleClass().add("text-muted");
        titleBox.getChildren().addAll(title, sub);
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Button closeBtn = new Button("✕");
        closeBtn.getStyleClass().addAll("button-sm", "button-secondary");
        closeBtn.setOnAction(e -> close());
        head.getChildren().addAll(titleBox, sp, closeBtn);
        card.getChildren().add(head);
        card.getChildren().add(new Separator());

        // Live registry bindings first (always reflects user rebindings),
        // then contextual catalog sections (designer tool keys etc.).
        Map<String, String[][]> groups = new LinkedHashMap<>();
        for (var e : ShortcutManager.grouped().entrySet()) {
            String[][] rows = e.getValue().stream()
                    .map(a -> new String[]{
                            ShortcutManager.comboOf(a.id()) == null ? "—" : ShortcutManager.comboOf(a.id()),
                            a.label()})
                    .toArray(String[][]::new);
            groups.put(e.getKey(), rows);
        }
        groups.putAll(com.invoicestudio.ui.ShortcutCatalog.contextGroups());

        HBox columns = new HBox(24);
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

        ScrollPane sc = new ScrollPane(columns);
        sc.setFitToWidth(true);
        sc.getStyleClass().add("scroll-pane");
        sc.setMaxHeight(520);
        card.getChildren().add(sc);

        getChildren().add(card);

        // Escape closes (when this overlay has focus)
        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                close();
                e.consume();
            }
        });
        // Click on dim background closes
        setOnMouseClicked(e -> {
            if (e.getTarget() == this) close();
        });
    }

    private HBox shortcutRow(String keys, String description) {
        HBox h = new HBox(12);
        h.setAlignment(Pos.CENTER_LEFT);
        Label k = new Label(keys);
        k.getStyleClass().addAll("gstin-badge", "table-cell-mono");
        k.setMinWidth(150);
        Label d = new Label(description);
        d.getStyleClass().add("table-cell-secondary");
        h.getChildren().addAll(k, d);
        return h;
    }

    private void close() {
        if (onClose != null) onClose.run();
    }
}
