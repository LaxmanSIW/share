package com.invoicestudio.ui;

import com.invoicestudio.service.AuthSessionManager;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/**
 * Sidebar user-profile pill for {@link StudioApp} (skill rule 5.2 role 2).
 * Builds the avatar/initials/email pill and refreshes it on auth changes.
 */
class UserProfilePill {

    private final StudioApp app;
    private final Label userAvatarLabel;
    private final Label userNameLabel;
    private final Label userEmailLabel;
    private final HBox pill;

    UserProfilePill(StudioApp app) {
        this.app = app;
        pill = new HBox(10);
        pill.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        pill.setStyle("-fx-background-color: #121721; -fx-background-radius: 8px; -fx-padding: 8px 10px; -fx-border-color: #1E2738; -fx-border-radius: 8px; -fx-border-width: 1px;");

        userAvatarLabel = new Label("IS");
        userAvatarLabel.setStyle("-fx-background-color: linear-gradient(to bottom right, #D4AF37, #AA820A); -fx-background-radius: 50%; -fx-min-width: 30px; -fx-min-height: 30px; -fx-max-width: 30px; -fx-max-height: 30px; -fx-alignment: center; -fx-font-size: 11px; -fx-font-weight: 800; -fx-text-fill: #0B0E13;");

        VBox textBox = new VBox(1);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        userNameLabel = new Label("Account");
        userNameLabel.setStyle("-fx-text-fill: #F8FAFC; -fx-font-size: 11.5px; -fx-font-weight: 600;");

        userEmailLabel = new Label("");
        userEmailLabel.setStyle("-fx-text-fill: #64748B; -fx-font-size: 10px;");

        textBox.getChildren().addAll(userNameLabel, userEmailLabel);

        Button logoutBtn = new Button();
        logoutBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 4px;");
        SVGPath logoutIcon = new SVGPath();
        logoutIcon.setContent("M17 7l-1.41 1.41L18.17 11H8v2h10.17l-2.58 2.58L17 17l5-5zM4 5h8V3H4c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h8v-2H4V5z");
        logoutIcon.setFill(Color.web("#94A3B8"));
        logoutIcon.setScaleX(0.7);
        logoutIcon.setScaleY(0.7);
        logoutBtn.setGraphic(logoutIcon);
        logoutBtn.setTooltip(new Tooltip("Log Out"));

        logoutBtn.setOnMouseEntered(e -> logoutIcon.setFill(Color.web("#EF4444")));
        logoutBtn.setOnMouseExited(e -> logoutIcon.setFill(Color.web("#94A3B8")));
        logoutBtn.setOnAction(e -> app.promptLogout());

        pill.getChildren().addAll(userAvatarLabel, textBox, logoutBtn);
        refresh();
    }

    HBox node() { return pill; }

    void refresh() {
        String name = AuthSessionManager.getCurrentUserDisplayName();
        String email = AuthSessionManager.getCurrentUserEmail();

        userNameLabel.setText(name.isBlank() ? "Account" : name);
        userEmailLabel.setText(email);

        String initials = "IS";
        if (!name.isBlank() && !name.equalsIgnoreCase("User")) {
            String[] parts = name.trim().split("\\s+");
            if (parts.length >= 2) {
                initials = ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
            } else if (!parts[0].isEmpty()) {
                initials = parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
            }
        } else if (!email.isBlank()) {
            initials = email.substring(0, Math.min(2, email.length())).toUpperCase();
        }
        userAvatarLabel.setText(initials);
    }
}
