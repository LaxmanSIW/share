package com.invoicestudio.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Toast notifications. Styling lives entirely in globalfile.css
 * (.toast-box / .info / .error) so future theme changes don't touch code.
 */
public class Toast {

    public static void show(Pane rootPane, String title, String message, boolean isError) {
        if (rootPane == null) return;

        VBox toast = new VBox(2);
        toast.getStyleClass().addAll("toast-box", isError ? "error" : "info");
        toast.setMaxWidth(360);
        toast.setMaxHeight(80);

        Label titleLbl = new Label(title);
        titleLbl.getStyleClass().add("toast-title");
        toast.getChildren().add(titleLbl);

        if (message != null && !message.isBlank()) {
            Label msgLbl = new Label(message);
            msgLbl.setWrapText(true);
            msgLbl.getStyleClass().add("toast-message");
            toast.getChildren().add(msgLbl);
        }

        if (rootPane instanceof StackPane) {
            StackPane.setAlignment(toast, Pos.BOTTOM_RIGHT);
            StackPane.setMargin(toast, new javafx.geometry.Insets(0, 24, 24, 0));
        }

        toast.setOpacity(0);
        rootPane.getChildren().add(toast);

        FadeTransition fadeIn = new FadeTransition(Duration.millis(200), toast);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);

        PauseTransition stay = new PauseTransition(Duration.seconds(3.5));

        FadeTransition fadeOut = new FadeTransition(Duration.millis(300), toast);
        fadeOut.setFromValue(1);
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> rootPane.getChildren().remove(toast));

        SequentialTransition seq = new SequentialTransition(fadeIn, stay, fadeOut);
        seq.play();
    }

    public static void show(Node node, String message) {
        show(node, "Notice", message, false);
    }

    /** Convenience: message + severity without an explicit title. */
    public static void show(Node node, String message, boolean isError) {
        show(node, isError ? "Error" : "Success", message, isError);
    }

    public static void show(Node node, String title, String message, boolean isError) {
        if (node == null) return;
        Pane root = null;
        if (node.getScene() != null && node.getScene().getRoot() instanceof Pane p) {
            root = p;
        } else if (node instanceof StackPane sp) {
            root = sp;
        } else if (node instanceof Pane p) {
            root = p;
        }
        if (root != null) {
            show(root, title, message, isError);
        }
    }
}
