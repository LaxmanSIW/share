package com.invoicestudio.ui.auth;

import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/**
 * Modern password field with prefix lock icon and interactive eye toggle.
 */
public class PasswordFieldWithToggle extends HBox {

    private final PasswordField passwordField;
    private final TextField textField;
    private final Button toggleButton;
    private boolean showingPassword = false;

    // SVG Icons
    private static final String LOCK_ICON_PATH = "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z";
    private static final String EYE_OPEN_PATH = "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z";
    private static final String EYE_CLOSED_PATH = "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.44-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27zM7.53 9.8l1.55 1.55c-.05.21-.08.43-.08.65 0 1.66 1.34 3 3 3 .22 0 .44-.03.65-.08l1.55 1.55c-.67.33-1.41.53-2.2.53-2.76 0-5-2.24-5-5 0-.79.2-1.53.53-2.2zm4.31-.78l3.15 3.15.02-.16c0-1.66-1.34-3-3-3l-.17.01z";

    public PasswordFieldWithToggle(String promptText) {
        setAlignment(Pos.CENTER_LEFT);
        getStyleClass().add("auth-input-box");

        // Lock prefix icon
        SVGPath lockIcon = new SVGPath();
        lockIcon.setContent(LOCK_ICON_PATH);
        lockIcon.setFill(Color.web("#64748B"));
        lockIcon.setScaleX(0.7);
        lockIcon.setScaleY(0.7);

        passwordField = new PasswordField();
        passwordField.setPromptText(promptText);
        passwordField.getStyleClass().add("auth-transparent-field");
        HBox.setHgrow(passwordField, Priority.ALWAYS);

        textField = new TextField();
        textField.setPromptText(promptText);
        textField.getStyleClass().add("auth-transparent-field");
        textField.setManaged(false);
        textField.setVisible(false);
        HBox.setHgrow(textField, Priority.ALWAYS);

        // Synchronize text between passwordField and textField
        passwordField.textProperty().bindBidirectional(textField.textProperty());

        // Eye toggle button
        toggleButton = new Button();
        toggleButton.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 4px;");
        SVGPath eyeIcon = new SVGPath();
        eyeIcon.setContent(EYE_OPEN_PATH);
        eyeIcon.setFill(Color.web("#64748B"));
        eyeIcon.setScaleX(0.7);
        eyeIcon.setScaleY(0.7);
        toggleButton.setGraphic(eyeIcon);

        toggleButton.setOnAction(e -> {
            showingPassword = !showingPassword;
            if (showingPassword) {
                textField.setText(passwordField.getText());
                passwordField.setManaged(false);
                passwordField.setVisible(false);
                textField.setManaged(true);
                textField.setVisible(true);
                textField.requestFocus();
                textField.positionCaret(textField.getText().length());
                eyeIcon.setContent(EYE_CLOSED_PATH);
                eyeIcon.setFill(Color.web("#D4AF37"));
            } else {
                passwordField.setText(textField.getText());
                textField.setManaged(false);
                textField.setVisible(false);
                passwordField.setManaged(true);
                passwordField.setVisible(true);
                passwordField.requestFocus();
                passwordField.positionCaret(passwordField.getText().length());
                eyeIcon.setContent(EYE_OPEN_PATH);
                eyeIcon.setFill(Color.web("#64748B"));
            }
        });

        // Focus styling
        passwordField.focusedProperty().addListener((obs, oldV, isFocused) -> updateFocusStyle());
        textField.focusedProperty().addListener((obs, oldV, isFocused) -> updateFocusStyle());

        getChildren().addAll(lockIcon, passwordField, textField, toggleButton);
    }

    private void updateFocusStyle() {
        if (passwordField.isFocused() || textField.isFocused()) {
            if (!getStyleClass().contains("auth-input-box-focused")) {
                getStyleClass().add("auth-input-box-focused");
            }
        } else {
            getStyleClass().remove("auth-input-box-focused");
        }
    }

    public String getText() {
        return showingPassword ? textField.getText() : passwordField.getText();
    }

    public void setText(String text) {
        passwordField.setText(text);
        textField.setText(text);
    }

    public StringProperty textProperty() {
        return passwordField.textProperty();
    }

    public void clear() {
        passwordField.clear();
        textField.clear();
    }
}
