package com.invoicestudio.ui.auth;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/**
 * Premium Logout confirmation modal dialog matching reference Screen 5.
 */
public class LogoutDialog extends StackPane {

    private static final String LOGOUT_ICON_PATH = "M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z";

    public LogoutDialog(Runnable onConfirm, Runnable onCancel) {
        // Semi-transparent backdrop scrim
        setStyle("-fx-background-color: rgba(5, 7, 11, 0.75);");
        setAlignment(Pos.CENTER);

        // Dialog Card
        VBox card = new VBox(16);
        card.getStyleClass().add("auth-card");
        card.setMaxWidth(380);
        card.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        card.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        card.setAlignment(Pos.CENTER);
        StackPane.setAlignment(card, Pos.CENTER);
        card.setStyle(card.getStyle() + "; -fx-padding: 26px 30px;");

        // Icon badge
        StackPane iconBadge = new StackPane();
        iconBadge.setStyle("-fx-background-color: rgba(239, 68, 68, 0.12); -fx-background-radius: 50%; -fx-pref-width: 52px; -fx-pref-height: 52px; -fx-max-width: 52px; -fx-max-height: 52px;");
        SVGPath icon = new SVGPath();
        icon.setContent(LOGOUT_ICON_PATH);
        icon.setFill(Color.web("#EF4444"));
        icon.setScaleX(1.1);
        icon.setScaleY(1.1);
        iconBadge.getChildren().add(icon);

        // Titles
        Label title = new Label("Log Out");
        title.getStyleClass().add("auth-title");
        title.setStyle("-fx-font-size: 19px;");

        Label subtitle = new Label("Are you sure you want to log out of InvoiceStudio?\nYour local invoices remain safely stored on this computer.");
        subtitle.getStyleClass().add("auth-subtitle");
        subtitle.setWrapText(true);
        subtitle.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        // Buttons row
        HBox btnRow = new HBox(12);
        btnRow.setAlignment(Pos.CENTER);

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("auth-btn-secondary");
        HBox.setHgrow(cancelBtn, Priority.ALWAYS);
        cancelBtn.setMaxWidth(Double.MAX_VALUE);
        cancelBtn.setOnAction(e -> {
            if (onCancel != null) onCancel.run();
        });

        Button logoutBtn = new Button("Log Out");
        logoutBtn.getStyleClass().add("auth-btn-danger");
        HBox.setHgrow(logoutBtn, Priority.ALWAYS);
        logoutBtn.setMaxWidth(Double.MAX_VALUE);
        logoutBtn.setOnAction(e -> {
            if (onConfirm != null) onConfirm.run();
        });

        btnRow.getChildren().addAll(cancelBtn, logoutBtn);

        card.getChildren().addAll(iconBadge, title, subtitle, btnRow);
        getChildren().add(card);

        // Clicking scrim closes dialog
        setOnMouseClicked(e -> {
            if (e.getTarget() == this && onCancel != null) {
                onCancel.run();
            }
        });
    }
}
