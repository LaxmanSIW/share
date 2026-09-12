package com.invoicestudio.ui.auth;

import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * 4-segment visual password strength meter with criteria checklist.
 */
public class PasswordStrengthMeter extends VBox {

    private final Region bar1 = new Region();
    private final Region bar2 = new Region();
    private final Region bar3 = new Region();
    private final Region bar4 = new Region();

    private final Label statusLabel = new Label("Strength");
    private final Label criteriaLength = new Label("○  At least 8 characters");
    private final Label criteriaNumber = new Label("○  At least 1 number (0-9)");
    private final Label criteriaCase = new Label("○  Both uppercase & lowercase letters");

    public PasswordStrengthMeter() {
        setSpacing(6);

        // Segment bars row
        HBox barsRow = new HBox(5);
        barsRow.setAlignment(Pos.CENTER);

        bar1.getStyleClass().addAll("strength-bar", "strength-empty");
        bar2.getStyleClass().addAll("strength-bar", "strength-empty");
        bar3.getStyleClass().addAll("strength-bar", "strength-empty");
        bar4.getStyleClass().addAll("strength-bar", "strength-empty");

        HBox.setHgrow(bar1, Priority.ALWAYS);
        HBox.setHgrow(bar2, Priority.ALWAYS);
        HBox.setHgrow(bar3, Priority.ALWAYS);
        HBox.setHgrow(bar4, Priority.ALWAYS);

        barsRow.getChildren().addAll(bar1, bar2, bar3, bar4);

        // Status row
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");

        // Criteria checklist
        criteriaLength.getStyleClass().add("criteria-label");
        criteriaNumber.getStyleClass().add("criteria-label");
        criteriaCase.getStyleClass().add("criteria-label");

        VBox checklist = new VBox(3, criteriaLength, criteriaNumber, criteriaCase);
        checklist.setStyle("-fx-padding: 4px 0 0 0;");

        getChildren().addAll(barsRow, statusLabel, checklist);
    }

    public void bindToPassword(StringProperty passwordProp) {
        passwordProp.addListener((obs, oldV, newV) -> updateStrength(newV != null ? newV : ""));
    }

    public void updateStrength(String password) {
        boolean hasLength = password.length() >= 8;
        boolean hasNumber = password.matches(".*\\d.*");
        boolean hasUpper = password.matches(".*[A-Z].*");
        boolean hasLower = password.matches(".*[a-z].*");
        boolean hasSpecial = password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*");
        boolean hasBothCases = hasUpper && hasLower;

        // Update criteria checklist
        updateCriteria(criteriaLength, hasLength, "At least 8 characters");
        updateCriteria(criteriaNumber, hasNumber, "At least 1 number (0-9)");
        updateCriteria(criteriaCase, hasBothCases, "Both uppercase & lowercase letters");

        if (password.isEmpty()) {
            setBars(0, "strength-empty");
            statusLabel.setText("Password strength");
            statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #94A3B8;");
            return;
        }

        int score = 0;
        if (hasLength) score++;
        if (hasNumber) score++;
        if (hasBothCases) score++;
        if (hasSpecial || password.length() >= 12) score++;

        switch (score) {
            case 1:
                setBars(1, "strength-weak");
                statusLabel.setText("Strength: Weak");
                statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #EF4444; -fx-font-weight: bold;");
                break;
            case 2:
                setBars(2, "strength-fair");
                statusLabel.setText("Strength: Fair");
                statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #F59E0B; -fx-font-weight: bold;");
                break;
            case 3:
                setBars(3, "strength-good");
                statusLabel.setText("Strength: Good");
                statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #3B82F6; -fx-font-weight: bold;");
                break;
            case 4:
                setBars(4, "strength-strong");
                statusLabel.setText("Strength: Strong");
                statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #10B981; -fx-font-weight: bold;");
                break;
            default:
                setBars(1, "strength-weak");
                statusLabel.setText("Strength: Too short");
                statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #EF4444;");
                break;
        }
    }

    private void updateCriteria(Label label, boolean met, String text) {
        if (met) {
            label.setText("✓  " + text);
            label.getStyleClass().remove("criteria-label");
            if (!label.getStyleClass().contains("criteria-met")) {
                label.getStyleClass().add("criteria-met");
            }
        } else {
            label.setText("○  " + text);
            label.getStyleClass().remove("criteria-met");
            if (!label.getStyleClass().contains("criteria-label")) {
                label.getStyleClass().add("criteria-label");
            }
        }
    }

    private void setBars(int activeCount, String activeClass) {
        Region[] bars = {bar1, bar2, bar3, bar4};
        String[] allTypes = {"strength-empty", "strength-weak", "strength-fair", "strength-good", "strength-strong"};

        for (int i = 0; i < 4; i++) {
            bars[i].getStyleClass().removeAll(allTypes);
            if (i < activeCount) {
                bars[i].getStyleClass().add(activeClass);
            } else {
                bars[i].getStyleClass().add("strength-empty");
            }
        }
    }
}
