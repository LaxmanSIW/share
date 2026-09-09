package com.invoicestudio.ui;

import javafx.application.Platform;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;

public class DialogHelper {

    public static void styleDialog(Dialog<?> dialog) {
        if (dialog == null) return;
        DialogPane pane = dialog.getDialogPane();
        String css = DialogHelper.class.getResource("/css/globalfile.css") != null
                ? DialogHelper.class.getResource("/css/globalfile.css").toExternalForm()
                : null;
        if (css != null && !pane.getStylesheets().contains(css)) {
            pane.getStylesheets().add(css);
        }
        if (!pane.getStyleClass().contains("custom-dialog-pane")) {
            pane.getStyleClass().add("custom-dialog-pane");
        }

        // Set application icon and scene stylesheet on dialog stage
        Platform.runLater(() -> {
            try {
                if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage stage) {
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
