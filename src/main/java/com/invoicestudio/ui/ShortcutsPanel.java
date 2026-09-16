package com.invoicestudio.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

/**
 * Settings → Shortcuts tab: every registered action with its effective
 * binding, editable via a key-capture dialog with live validation
 * (conflict / reserved / too-simple checks from {@link ShortcutManager}).
 */
public class ShortcutsPanel extends VBox {

    public ShortcutsPanel() {
        setSpacing(14);
        setPadding(new Insets(18));
        getStyleClass().add("view-page");

        Label title = new Label("Keyboard Shortcuts");
        title.getStyleClass().add("heading-m");
        Label sub = new Label("Click a shortcut to rebind it · F1 anywhere opens the help popup");
        sub.getStyleClass().add("text-muted");
        getChildren().addAll(title, sub, buildToolbar(), new Separator(), buildGroups());
    }

    private Node buildToolbar() {
        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_LEFT);

        Button resetAll = UiTheme.secondaryBtn("Restore All Defaults");
        resetAll.setTooltip(new Tooltip("Reset every shortcut to its original binding"));
        resetAll.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Reset ALL shortcuts to their defaults?", ButtonType.YES, ButtonType.NO);
            DialogHelper.styleDialog(confirm);
            confirm.showAndWait().ifPresent(ans -> {
                if (ans == ButtonType.YES) {
                    ShortcutManager.resetAll();
                    refreshScene();
                    rebuild();
                }
            });
        });

        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label hint = new Label("Bindings persist in shortcuts.json");
        hint.getStyleClass().add("text-dim");
        bar.getChildren().addAll(resetAll, sp, hint);
        return bar;
    }

    /**
     * Groups laid out flat — no nested ScrollPane: SettingsView.createTab
     * already wraps every tab in an outer ScrollPane, and a nested one pins
     * the list to its preferred height (clipping the rest). Full content
     * height = the outer settings scroll scrolls naturally.
     */
    private Node buildGroups() {
        VBox all = new VBox(18);
        for (var entry : ShortcutManager.grouped().entrySet()) {
            Label gl = new Label(entry.getKey().toUpperCase());
            gl.getStyleClass().add("section-eyebrow");
            all.getChildren().add(gl);

            VBox rows = new VBox(6);
            for (ShortcutManager.ShortcutAction a : entry.getValue()) {
                rows.getChildren().add(actionRow(a));
            }
            all.getChildren().add(rows);
        }
        return all;
    }

    private HBox actionRow(ShortcutManager.ShortcutAction a) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);

        Label keys = new Label(ShortcutManager.comboOf(a.id()) == null ? "—" : ShortcutManager.comboOf(a.id()));
        keys.getStyleClass().addAll("gstin-badge", "table-cell-mono");
        keys.setMinWidth(170);

        Label desc = new Label(a.label());
        desc.getStyleClass().add("table-cell-secondary");
        HBox.setHgrow(desc, Priority.ALWAYS);

        Button editBtn = UiTheme.smallBtn("Change");
        editBtn.setTooltip(new Tooltip("Rebind this shortcut"));
        editBtn.setOnAction(e -> openCaptureDialog(a, () -> rebuild()));

        Button resetBtn = UiTheme.smallBtn("↺");
        resetBtn.setTooltip(new Tooltip("Restore default (" + a.defaultCombo() + ")"));
        resetBtn.setOnAction(e -> {
            ShortcutManager.resetToDefault(a.id());
            refreshScene();
            rebuild();
        });

        row.getChildren().addAll(keys, desc, resetBtn, editBtn);
        return row;
    }

    /** Key-capture dialog with live validation. */
    private void openCaptureDialog(ShortcutManager.ShortcutAction a, Runnable onApplied) {
        Stage dlg = new Stage();
        dlg.initOwner(getScene() != null ? getScene().getWindow() : null);
        dlg.initModality(Modality.WINDOW_MODAL);
        dlg.setTitle("Set Shortcut — " + a.label());

        VBox box = new VBox(14);
        box.setPadding(new Insets(20));
        box.getStyleClass().add("card-pane");

        Label head = new Label("Press the new key combination");
        head.getStyleClass().add("heading-s");

        Label current = new Label("Current: " + ShortcutManager.comboOf(a.id())
                + "   ·   Default: " + a.defaultCombo());
        current.getStyleClass().add("text-dim");

        Label captured = new Label("—");
        captured.getStyleClass().addAll("gstin-badge", "table-cell-mono");
        captured.setMinWidth(220);
        captured.setStyle("-fx-font-size: 14px; -fx-alignment: center;");

        Label status = new Label(" ");
        status.getStyleClass().add("text-muted");
        status.setWrapText(true);

        Button saveBtn = UiTheme.goldBtn("Save");
        saveBtn.setDisable(true);
        Button clearBtn = UiTheme.secondaryBtn("Unbind");
        Button cancelBtn = UiTheme.secondaryBtn("Cancel");

        final String[] pending = {null};

        Runnable evaluate = () -> {
            if (pending[0] == null) {
                saveBtn.setDisable(true);
                status.setText(" ");
                return;
            }
            var v = ShortcutManager.validate(a.id(), pending[0]);
            switch (v) {
                case OK -> {
                    saveBtn.setDisable(false);
                    status.setStyle("-fx-text-fill: #34D399;");
                    status.setText("✓ Available");
                }
                case TAKEN -> {
                    saveBtn.setDisable(true);
                    status.setStyle("-fx-text-fill: #F87171;");
                    status.setText(ShortcutManager.validationMessage(v, pending[0]));
                }
                case RESERVED -> {
                    saveBtn.setDisable(true);
                    status.setStyle("-fx-text-fill: #F87171;");
                    status.setText(ShortcutManager.validationMessage(v, pending[0]));
                }
                case TOO_SIMPLE -> {
                    saveBtn.setDisable(true);
                    status.setStyle("-fx-text-fill: #F2CA6B;");
                    status.setText(ShortcutManager.validationMessage(v, pending[0]));
                }
                case INVALID -> {
                    saveBtn.setDisable(true);
                    status.setStyle("-fx-text-fill: #F87171;");
                    status.setText(ShortcutManager.validationMessage(v, pending[0]));
                }
            }
        };

        String[] captureMods = {"Ctrl", "Shift", "Alt"};
        box.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            KeyCode code = e.getCode();
            if (code == KeyCode.ESCAPE) { dlg.close(); e.consume(); return; }
            if (code.isModifierKey()) return; // wait for the real key
            StringBuilder sb = new StringBuilder();
            if (e.isControlDown()) sb.append("Ctrl+");
            if (e.isShiftDown()) sb.append("Shift+");
            if (e.isAltDown()) sb.append("Alt+");
            sb.append(code.getName());
            pending[0] = sb.toString();
            captured.setText(pending[0]);
            evaluate.run();
            e.consume();
        });

        saveBtn.setOnAction(e -> {
            ShortcutManager.bind(a.id(), pending[0]);
            refreshScene();
            dlg.close();
            onApplied.run();
        });

        clearBtn.setOnAction(e -> {
            ShortcutManager.bind(a.id(), null);
            refreshScene();
            dlg.close();
            onApplied.run();
        });

        cancelBtn.setOnAction(e -> dlg.close());

        HBox actions = new HBox(10, saveBtn, clearBtn, cancelBtn);
        actions.setAlignment(Pos.CENTER_RIGHT);

        // make the modifiers reachable via click too
        HBox modRow = new HBox(8);
        for (String m : captureMods) {
            Label chip = new Label(m);
            chip.getStyleClass().addAll("gstin-badge", "table-cell-mono");
            modRow.getChildren().add(chip);
        }
        modRow.setAlignment(Pos.CENTER_LEFT);

        box.getChildren().addAll(head, current, captured, modRow, status, actions);
        Scene sc = new Scene(box, 440, 300);
        sc.getStylesheets().add(getClass().getResource("/css/globalfile.css") != null
                ? getClass().getResource("/css/globalfile.css").toExternalForm() : null);
        dlg.setScene(sc);
        Platform.runLater(box::requestFocus);
        dlg.show();
    }

    private void refreshScene() {
        if (getScene() != null && getScene().getWindow() instanceof Stage st) {
            Scene primary = st.getScene();
            ShortcutManager.installAll(primary);
        }
    }

    private void rebuild() {
        getChildren().set(4, buildGroups());
    }
}
