package com.invoicestudio.ui;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

public class Toast {

    public static void show(Pane rootPane, String title, String message, boolean isError) {
        if (rootPane == null) return;

        VBox toast = new VBox(2);
        toast.getStyleClass().add("toast-box");
        toast.setMaxWidth(360);
        toast.setMaxHeight(80);
        toast.setStyle("-fx-background-color: #171F2C; -fx-border-color: " + (isError ? "#EF4444" : "#D9A13B") +
                "; -fx-border-width: 1; -fx-background-radius: 8; -fx-border-radius: 8; -fx-padding: 10 14; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.6), 16, 0, 0, 4);");

        Label titleLbl = new Label(title);
        titleLbl.setStyle("-fx-font-weight: bold; -fx-font-size: 12px; -fx-text-fill: " + (isError ? "#F87171" : "#E5B055") + ";");
        toast.getChildren().add(titleLbl);

        if (message != null && !message.isBlank()) {
            Label msgLbl = new Label(message);
            msgLbl.setWrapText(true);
            msgLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #CBD5E1;");
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
