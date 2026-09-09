package com.invoicestudio.ui;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.io.InputStream;

public class DialogHelper {

    public static void styleDialog(Dialog<?> dialog) {
        styleDialog(dialog, 460, 320);
    }

    public static void styleDialog(Dialog<?> dialog, double minWidth, double minHeight) {
        if (dialog == null) return;

        // Enable maximize, minimize, and free resizing on the dialog stage
        dialog.setResizable(true);

        DialogPane pane = dialog.getDialogPane();
        pane.setMaxWidth(Double.MAX_VALUE);
        pane.setMaxHeight(Double.MAX_VALUE);

        // Ensure inner content can expand fully on maximize/resize
        if (pane.getContent() instanceof Region r) {
            r.setMaxWidth(Double.MAX_VALUE);
            r.setMaxHeight(Double.MAX_VALUE);
        }
        pane.contentProperty().addListener((obs, oldV, newV) -> {
            if (newV instanceof Region r) {
                r.setMaxWidth(Double.MAX_VALUE);
                r.setMaxHeight(Double.MAX_VALUE);
            }
        });

        String css = DialogHelper.class.getResource("/css/globalfile.css") != null
                ? DialogHelper.class.getResource("/css/globalfile.css").toExternalForm()
                : null;
        if (css != null && !pane.getStylesheets().contains(css)) {
            pane.getStylesheets().add(css);
        }
        if (!pane.getStyleClass().contains("custom-dialog-pane")) {
            pane.getStyleClass().add("custom-dialog-pane");
        }

        // Set application icon, min dimensions and scene stylesheet on dialog stage
        Platform.runLater(() -> {
            try {
                if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage stage) {
                    if (minWidth > 0) stage.setMinWidth(minWidth);
                    if (minHeight > 0) stage.setMinHeight(minHeight);
                    if (stage.getIcons().isEmpty()) {
                        InputStream iconStream = DialogHelper.class.getResourceAsStream("/icons/Invoicewhitebackground.png");
                        if (iconStream != null) {
                            stage.getIcons().add(new Image(iconStream));
                        }
                    }
                    if (css != null && !pane.getScene().getStylesheets().contains(css)) {
                        pane.getScene().getStylesheets().add(css);
                    }
                }
            } catch (Exception ignored) {}
        });
    }

    public static void styleScene(javafx.scene.Scene scene) {
        if (scene == null) return;
        String css = DialogHelper.class.getResource("/css/globalfile.css") != null
                ? DialogHelper.class.getResource("/css/globalfile.css").toExternalForm()
                : null;
        if (css != null && !scene.getStylesheets().contains(css)) {
            scene.getStylesheets().add(css);
        }
    }
}
