package com.invoicestudio.ui.auth;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/**
 * Premium Obsidian & Gold styled "Continue with Google" button with official multi-colored G logo.
 */
public class GoogleSignInButton extends Button {

    public GoogleSignInButton() {
        getStyleClass().add("auth-btn-google");
        setMaxWidth(Double.MAX_VALUE);

        // Multi-color Google "G" SVG paths
        SVGPath bluePath = new SVGPath();
        bluePath.setContent("M17.64 9.2c0-.637-.057-1.251-.164-1.84H9v3.481h4.844c-.209 1.125-.843 2.078-1.796 2.717v2.258h2.908c1.702-1.567 2.684-3.874 2.684-6.616z");
        bluePath.setFill(Color.web("#4285F4"));

        SVGPath greenPath = new SVGPath();
        greenPath.setContent("M9 18c2.43 0 4.467-.806 5.956-2.184l-2.908-2.258c-.806.54-1.837.86-3.048.86-2.344 0-4.328-1.584-5.036-3.711H.957v2.332C2.438 15.983 5.482 18 9 18z");
        greenPath.setFill(Color.web("#34A853"));

        SVGPath yellowPath = new SVGPath();
        yellowPath.setContent("M3.964 10.707c-.18-.54-.282-1.117-.282-1.707s.102-1.167.282-1.707V4.961H.957C.347 6.173 0 7.548 0 9s.347 2.827.957 4.039l3.007-2.332z");
        yellowPath.setFill(Color.web("#FBBC05"));

        SVGPath redPath = new SVGPath();
        redPath.setContent("M9 3.58c1.321 0 2.508.454 3.44 1.345l2.582-2.58C13.463.891 11.426 0 9 0 5.482 0 2.438 2.017.957 4.961L3.964 7.293C4.672 5.166 6.656 3.58 9 3.58z");
        redPath.setFill(Color.web("#EA4335"));

        Group googleLogo = new Group(bluePath, greenPath, yellowPath, redPath);
        googleLogo.setScaleX(0.85);
        googleLogo.setScaleY(0.85);

        Label textLabel = new Label("Continue with Google");
        textLabel.setStyle("-fx-text-fill: #F8FAFC; -fx-font-size: 13.5px; -fx-font-weight: 600;");

        HBox content = new HBox(12, googleLogo, textLabel);
        content.setAlignment(Pos.CENTER);

        setGraphic(content);
    }
}
