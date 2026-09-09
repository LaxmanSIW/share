package com.invoicestudio.ui;

import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;

public class DialogHelper {

    public static void styleDialog(Dialog<?> dialog) {
        if (dialog == null) return;
        DialogPane pane = dialog.getDialogPane();
        String css = DialogHelper.class.getResource("/css/globalfile.css").toExternalForm();
        if (!pane.getStylesheets().contains(css)) {
            pane.getStylesheets().add(css);
        }
        if (!pane.getStyleClass().contains("custom-dialog-pane")) {
            pane.getStyleClass().add("custom-dialog-pane");
        }
    }

    public static void styleScene(javafx.scene.Scene scene) {
        if (scene == null) return;
        String css = DialogHelper.class.getResource("/css/globalfile.css").toExternalForm();
        if (!scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
    }
}
