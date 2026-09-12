package com.invoicestudio.ui.auth;

import com.invoicestudio.model.UserSession;
import com.invoicestudio.service.AuthSessionManager;
import com.invoicestudio.service.FirebaseAuthService;
import com.invoicestudio.ui.DataManager;
import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

import java.awt.Desktop;
import java.net.URI;
import java.util.concurrent.Executors;

/**
 * Complete Firebase Authentication view supporting 6 reference states:
 * SIGN_IN, SIGN_UP, FORGOT_PASSWORD, CHECK_EMAIL, SET_NEW_PASSWORD, LOGGED_OUT.
 */
public class AuthView extends StackPane {

    public enum AuthState {
        SIGN_IN,
        SIGN_UP,
        FORGOT_PASSWORD,
        CHECK_EMAIL,
        SET_NEW_PASSWORD,
        LOGGED_OUT
    }

    private final FirebaseAuthService authService = FirebaseAuthService.getInstance();
    private final Runnable onAuthSuccess;
    private final VBox cardContainer = new VBox(20);

    private AuthState currentState = AuthState.SIGN_IN;
    private String lastResetEmail = "";

    // Icons
    private static final String ICON_ENVELOPE = "M20 4H4c-1.1 0-1.99.9-1.99 2L2 18c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2zm0 4l-8 5-8-5V6l8 5 8-5v2z";
    private static final String ICON_USER = "M12 12c2.21 0 4-1.79 4-4s-1.79-4-4-4-4 1.79-4 4 1.79 4 4 4zm0 2c-2.67 0-8 1.34-8 4v2h16v-2c0-2.66-5.33-4-8-4z";
    private static final String ICON_KEY = "M12.65 10C11.83 7.67 9.61 6 7 6c-3.31 0-6 2.69-6 6s2.69 6 6 6c2.61 0 4.83-1.67 5.65-4H17v4h4v-4h2v-4H12.65zM7 14c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2z";
    private static final String ICON_CHECK = "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z";

    public AuthView(Runnable onAuthSuccess) {
        this(AuthState.SIGN_IN, onAuthSuccess);
    }

    public AuthView(AuthState initialState, Runnable onAuthSuccess) {
        this.onAuthSuccess = onAuthSuccess;
        this.currentState = initialState;

        getStyleClass().add("auth-root-bg");
        setAlignment(Pos.CENTER);

        cardContainer.getStyleClass().add("auth-card");
        cardContainer.setMaxWidth(440);
        cardContainer.setMaxHeight(Region.USE_PREF_SIZE);
        cardContainer.setAlignment(Pos.CENTER);

        // Centering wrapper inside ScrollPane to ensure the card is dead-center on all resolutions
        StackPane centeringWrapper = new StackPane(cardContainer);
        centeringWrapper.setAlignment(Pos.CENTER);
        centeringWrapper.setPadding(new javafx.geometry.Insets(30, 20, 30, 20));
        StackPane.setAlignment(cardContainer, Pos.CENTER);

        ScrollPane scroll = new ScrollPane(centeringWrapper);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent; -fx-border-color: transparent;");
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        getChildren().add(scroll);

        renderState(initialState);
    }

    public void setState(AuthState state) {
        this.currentState = state;
        FadeTransition ft = new FadeTransition(Duration.millis(180), cardContainer);
        ft.setFromValue(1.0);
        ft.setToValue(0.2);
        ft.setOnFinished(e -> {
            renderState(state);
            FadeTransition fadeIn = new FadeTransition(Duration.millis(220), cardContainer);
            fadeIn.setFromValue(0.2);
            fadeIn.setToValue(1.0);
            fadeIn.play();
        });
        ft.play();
    }

    private void renderState(AuthState state) {
        cardContainer.getChildren().clear();

        switch (state) {
            case SIGN_IN:
                renderSignIn();
                break;
            case SIGN_UP:
                renderSignUp();
                break;
            case FORGOT_PASSWORD:
                renderForgotPassword();
                break;
            case CHECK_EMAIL:
                renderCheckEmail();
                break;
            case SET_NEW_PASSWORD:
                renderSetNewPassword();
                break;
            case LOGGED_OUT:
                renderLoggedOut();
                break;
        }
    }

    // =========================================================================
    // SCREEN 1: SIGN IN
    // =========================================================================
    private void renderSignIn() {
        Label logoBadge = createLogoBadge();
        Label title = new Label("Welcome Back");
        title.getStyleClass().add("auth-title");
        Label subtitle = new Label("Please enter your details to sign in");
        subtitle.getStyleClass().add("auth-subtitle");

        VBox header = new VBox(6, logoBadge, title, subtitle);
        header.setAlignment(Pos.CENTER);

        // Form fields
        Label emailLabel = new Label("Email Address");
        emailLabel.getStyleClass().add("auth-field-label");
        HBox emailBox = createInputBox(ICON_ENVELOPE, "name@company.com");
        TextField emailField = (TextField) emailBox.getUserData();

        Label passLabel = new Label("Password");
        passLabel.getStyleClass().add("auth-field-label");
        PasswordFieldWithToggle passField = new PasswordFieldWithToggle("••••••••");

        // Options Row (Remember me + Forgot password)
        CheckBox rememberMe = new CheckBox("Remember me");
        rememberMe.setSelected(true);
        rememberMe.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12.5px; -fx-cursor: hand;");

        Label forgotLink = new Label("Forgot password?");
        forgotLink.getStyleClass().add("auth-link");
        forgotLink.setOnMouseClicked(e -> setState(AuthState.FORGOT_PASSWORD));

        BorderPane optionsPane = new BorderPane();
        optionsPane.setLeft(rememberMe);
        optionsPane.setRight(forgotLink);

        // Sign In Button
        Button signInBtn = new Button("Sign In");
        signInBtn.getStyleClass().add("auth-btn-primary");
        signInBtn.setMaxWidth(Double.MAX_VALUE);

        // Error message banner
        VBox bannerBox = new VBox();
        bannerBox.setVisible(false);
        bannerBox.setManaged(false);

        signInBtn.setOnAction(e -> {
            String email = emailField.getText().trim();
            String password = passField.getText();

            if (email.isEmpty() || password.isEmpty()) {
                showBanner(bannerBox, "Please enter both email and password.", true);
                return;
            }

            signInBtn.setDisable(true);
            signInBtn.setText("Signing In...");
            bannerBox.setVisible(false);
            bannerBox.setManaged(false);

            Executors.newSingleThreadExecutor().submit(() -> {
                try {
                    UserSession session = authService.signInWithEmail(email, password, rememberMe.isSelected());
                    handleSuccessfulLogin(session, rememberMe.isSelected());
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        signInBtn.setDisable(false);
                        signInBtn.setText("Sign In");
                        showBanner(bannerBox, ex.getMessage(), true);
                    });
                }
            });
        });

        // Google Sign In
        GoogleSignInButton googleBtn = new GoogleSignInButton();
        googleBtn.setOnAction(e -> {
            googleBtn.setDisable(true);
            authService.signInWithGoogle(session -> {
                googleBtn.setDisable(false);
                handleSuccessfulLogin(session, true);
            }, err -> {
                googleBtn.setDisable(false);
                showBanner(bannerBox, err, true);
            });
        });

        // Sign Up Footer
        HBox footer = new HBox(5);
        footer.setAlignment(Pos.CENTER);
        Label noAccLabel = new Label("Don't have an account?");
        noAccLabel.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12.5px;");
        Label signUpLink = new Label("Sign Up");
        signUpLink.getStyleClass().add("auth-link");
        signUpLink.setOnMouseClicked(e -> setState(AuthState.SIGN_UP));
        footer.getChildren().addAll(noAccLabel, signUpLink);

        VBox form = new VBox(12,
                emailLabel, emailBox,
                passLabel, passField,
                optionsPane,
                signInBtn,
                createDivider(),
                googleBtn
        );

        cardContainer.getChildren().addAll(header, bannerBox, form, footer);
    }

    // =========================================================================
    // SCREEN: SIGN UP
    // =========================================================================
    private void renderSignUp() {
        Label logoBadge = createLogoBadge();
        Label title = new Label("Create an Account");
        title.getStyleClass().add("auth-title");
        Label subtitle = new Label("Start managing your invoices securely");
        subtitle.getStyleClass().add("auth-subtitle");

        VBox header = new VBox(6, logoBadge, title, subtitle);
        header.setAlignment(Pos.CENTER);

        // Fields
        Label nameLabel = new Label("Full Name");
        nameLabel.getStyleClass().add("auth-field-label");
        HBox nameBox = createInputBox(ICON_USER, "John Doe");
        TextField nameField = (TextField) nameBox.getUserData();

        Label emailLabel = new Label("Email Address");
        emailLabel.getStyleClass().add("auth-field-label");
        HBox emailBox = createInputBox(ICON_ENVELOPE, "name@company.com");
        TextField emailField = (TextField) emailBox.getUserData();

        Label passLabel = new Label("Password");
        passLabel.getStyleClass().add("auth-field-label");
        PasswordFieldWithToggle passField = new PasswordFieldWithToggle("Create password");

        PasswordStrengthMeter strengthMeter = new PasswordStrengthMeter();
        strengthMeter.bindToPassword(passField.textProperty());

        Label confirmLabel = new Label("Confirm Password");
        confirmLabel.getStyleClass().add("auth-field-label");
        PasswordFieldWithToggle confirmField = new PasswordFieldWithToggle("Confirm password");

        CheckBox rememberMe = new CheckBox("Remember me on this device");
        rememberMe.setSelected(true);
        rememberMe.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12.5px; -fx-cursor: hand;");

        Button signUpBtn = new Button("Create Account");
        signUpBtn.getStyleClass().add("auth-btn-primary");
        signUpBtn.setMaxWidth(Double.MAX_VALUE);

        VBox bannerBox = new VBox();
        bannerBox.setVisible(false);
        bannerBox.setManaged(false);

        signUpBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            String email = emailField.getText().trim();
            String password = passField.getText();
            String confirm = confirmField.getText();

            if (email.isEmpty() || password.isEmpty()) {
                showBanner(bannerBox, "Please provide email and password.", true);
                return;
            }
            if (!password.equals(confirm)) {
                showBanner(bannerBox, "Passwords do not match.", true);
                return;
            }
            if (password.length() < 6) {
                showBanner(bannerBox, "Password must be at least 6 characters long.", true);
                return;
            }

            signUpBtn.setDisable(true);
            signUpBtn.setText("Creating Account...");
            bannerBox.setVisible(false);
            bannerBox.setManaged(false);

            Executors.newSingleThreadExecutor().submit(() -> {
                try {
                    UserSession session = authService.signUpWithEmail(email, password, name, rememberMe.isSelected());
                    handleSuccessfulLogin(session, rememberMe.isSelected());
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        signUpBtn.setDisable(false);
                        signUpBtn.setText("Create Account");
                        showBanner(bannerBox, ex.getMessage(), true);
                    });
                }
            });
        });

        GoogleSignInButton googleBtn = new GoogleSignInButton();
        googleBtn.setOnAction(e -> {
            googleBtn.setDisable(true);
            authService.signInWithGoogle(session -> {
                googleBtn.setDisable(false);
                handleSuccessfulLogin(session, true);
            }, err -> {
                googleBtn.setDisable(false);
                showBanner(bannerBox, err, true);
            });
        });

        HBox footer = new HBox(5);
        footer.setAlignment(Pos.CENTER);
        Label hasAccLabel = new Label("Already have an account?");
        hasAccLabel.setStyle("-fx-text-fill: #94A3B8; -fx-font-size: 12.5px;");
        Label signInLink = new Label("Sign In");
        signInLink.getStyleClass().add("auth-link");
        signInLink.setOnMouseClicked(e -> setState(AuthState.SIGN_IN));
        footer.getChildren().addAll(hasAccLabel, signInLink);

        VBox form = new VBox(10,
                nameLabel, nameBox,
                emailLabel, emailBox,
                passLabel, passField,
                strengthMeter,
                confirmLabel, confirmField,
                rememberMe,
                signUpBtn,
                createDivider(),
                googleBtn
        );

        cardContainer.getChildren().addAll(header, bannerBox, form, footer);
    }

    // =========================================================================
    // SCREEN 2: FORGOT PASSWORD
    // =========================================================================
    private void renderForgotPassword() {
        StackPane iconBadge = createCircularBadge(ICON_KEY, "#D4AF37", "rgba(212, 175, 55, 0.12)");
        Label title = new Label("Forgot Password");
        title.getStyleClass().add("auth-title");
        Label subtitle = new Label("No worries, we'll send you reset instructions.");
        subtitle.getStyleClass().add("auth-subtitle");

        VBox header = new VBox(6, iconBadge, title, subtitle);
        header.setAlignment(Pos.CENTER);

        Label emailLabel = new Label("Email Address");
        emailLabel.getStyleClass().add("auth-field-label");
        HBox emailBox = createInputBox(ICON_ENVELOPE, "name@company.com");
        TextField emailField = (TextField) emailBox.getUserData();

        Button resetBtn = new Button("Reset Password");
        resetBtn.getStyleClass().add("auth-btn-primary");
        resetBtn.setMaxWidth(Double.MAX_VALUE);

        VBox bannerBox = new VBox();
        bannerBox.setVisible(false);
        bannerBox.setManaged(false);

        resetBtn.setOnAction(e -> {
            String email = emailField.getText().trim();
            if (email.isEmpty()) {
                showBanner(bannerBox, "Please enter your email address.", true);
                return;
            }

            resetBtn.setDisable(true);
            resetBtn.setText("Sending Instructions...");
            bannerBox.setVisible(false);
            bannerBox.setManaged(false);

            Executors.newSingleThreadExecutor().submit(() -> {
                try {
                    authService.sendPasswordReset(email);
                    lastResetEmail = email;
                    Platform.runLater(() -> setState(AuthState.CHECK_EMAIL));
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        resetBtn.setDisable(false);
                        resetBtn.setText("Reset Password");
                        showBanner(bannerBox, ex.getMessage(), true);
                    });
                }
            });
        });

        Label backLink = new Label("← Back to Sign In");
        backLink.getStyleClass().add("auth-link");
        backLink.setOnMouseClicked(e -> setState(AuthState.SIGN_IN));

        VBox form = new VBox(12, emailLabel, emailBox, resetBtn, backLink);
        form.setAlignment(Pos.CENTER);

        cardContainer.getChildren().addAll(header, bannerBox, form);
    }

    // =========================================================================
    // SCREEN 3: CHECK YOUR EMAIL
    // =========================================================================
    private void renderCheckEmail() {
        StackPane iconBadge = createCircularBadge(ICON_ENVELOPE, "#10B981", "rgba(16, 185, 129, 0.12)");
        Label title = new Label("Check your email");
        title.getStyleClass().add("auth-title");

        String emailMsg = lastResetEmail.isBlank()
                ? "We have sent password recovery instructions to your email."
                : "We have sent password recovery instructions to " + lastResetEmail;
        Label subtitle = new Label(emailMsg);
        subtitle.getStyleClass().add("auth-subtitle");
        subtitle.setWrapText(true);
        subtitle.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        VBox header = new VBox(8, iconBadge, title, subtitle);
        header.setAlignment(Pos.CENTER);

        Button openEmailBtn = new Button("Open Email App");
        openEmailBtn.getStyleClass().add("auth-btn-primary");
        openEmailBtn.setMaxWidth(Double.MAX_VALUE);
        openEmailBtn.setOnAction(e -> {
            try {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) {
                    Desktop.getDesktop().mail();
                } else if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    Desktop.getDesktop().browse(new URI("https://mail.google.com"));
                }
            } catch (Exception ex) {
                setState(AuthState.SIGN_IN);
            }
        });

        Label hintLabel = new Label("Did not receive the email? Check your spam filter, or try another email address.");
        hintLabel.setStyle("-fx-font-size: 11.5px; -fx-text-fill: #64748B;");
        hintLabel.setWrapText(true);
        hintLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        Label backLink = new Label("← Back to Sign In");
        backLink.getStyleClass().add("auth-link");
        backLink.setOnMouseClicked(e -> setState(AuthState.SIGN_IN));

        VBox content = new VBox(14, openEmailBtn, hintLabel, backLink);
        content.setAlignment(Pos.CENTER);

        cardContainer.getChildren().addAll(header, content);
    }

    // =========================================================================
    // SCREEN 4: SET NEW PASSWORD
    // =========================================================================
    private void renderSetNewPassword() {
        StackPane iconBadge = createCircularBadge(ICON_KEY, "#D4AF37", "rgba(212, 175, 55, 0.12)");
        Label title = new Label("Set new password");
        title.getStyleClass().add("auth-title");
        Label subtitle = new Label("Must be at least 8 characters.");
        subtitle.getStyleClass().add("auth-subtitle");

        VBox header = new VBox(6, iconBadge, title, subtitle);
        header.setAlignment(Pos.CENTER);

        Label passLabel = new Label("Password");
        passLabel.getStyleClass().add("auth-field-label");
        PasswordFieldWithToggle passField = new PasswordFieldWithToggle("Enter new password");

        PasswordStrengthMeter meter = new PasswordStrengthMeter();
        meter.bindToPassword(passField.textProperty());

        Label confirmLabel = new Label("Confirm Password");
        confirmLabel.getStyleClass().add("auth-field-label");
        PasswordFieldWithToggle confirmField = new PasswordFieldWithToggle("Confirm new password");

        Button submitBtn = new Button("Reset Password");
        submitBtn.getStyleClass().add("auth-btn-primary");
        submitBtn.setMaxWidth(Double.MAX_VALUE);

        VBox bannerBox = new VBox();
        bannerBox.setVisible(false);
        bannerBox.setManaged(false);

        submitBtn.setOnAction(e -> {
            String p1 = passField.getText();
            String p2 = confirmField.getText();
            if (p1.isEmpty() || p2.isEmpty()) {
                showBanner(bannerBox, "Please fill in both password fields.", true);
                return;
            }
            if (!p1.equals(p2)) {
                showBanner(bannerBox, "Passwords do not match.", true);
                return;
            }
            if (p1.length() < 8) {
                showBanner(bannerBox, "Password must be at least 8 characters.", true);
                return;
            }

            submitBtn.setDisable(true);
            submitBtn.setText("Updating Password...");

            // If active session exists, update via idToken
            UserSession current = AuthSessionManager.getActiveSession();
            if (current != null && current.getIdToken() != null) {
                Executors.newSingleThreadExecutor().submit(() -> {
                    try {
                        UserSession updated = authService.updatePassword(current.getIdToken(), p1);
                        handleSuccessfulLogin(updated, true);
                    } catch (Exception ex) {
                        Platform.runLater(() -> {
                            submitBtn.setDisable(false);
                            submitBtn.setText("Reset Password");
                            showBanner(bannerBox, ex.getMessage(), true);
                        });
                    }
                });
            } else {
                // If not logged in, direct back to sign in
                setState(AuthState.SIGN_IN);
            }
        });

        Label backLink = new Label("← Back to Sign In");
        backLink.getStyleClass().add("auth-link");
        backLink.setOnMouseClicked(e -> setState(AuthState.SIGN_IN));

        VBox form = new VBox(10, passLabel, passField, meter, confirmLabel, confirmField, submitBtn, backLink);
        form.setAlignment(Pos.CENTER);

        cardContainer.getChildren().addAll(header, bannerBox, form);
    }

    // =========================================================================
    // SCREEN 6: LOGGED OUT
    // =========================================================================
    private void renderLoggedOut() {
        StackPane iconBadge = createCircularBadge(ICON_CHECK, "#10B981", "rgba(16, 185, 129, 0.12)");
        Label title = new Label("You've been logged out");
        title.getStyleClass().add("auth-title");
        Label subtitle = new Label("Thank you for using InvoiceStudio.\nAll your invoice data is safely saved on this computer.");
        subtitle.getStyleClass().add("auth-subtitle");
        subtitle.setWrapText(true);
        subtitle.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        VBox header = new VBox(8, iconBadge, title, subtitle);
        header.setAlignment(Pos.CENTER);

        Button signInAgainBtn = new Button("Sign In Again");
        signInAgainBtn.getStyleClass().add("auth-btn-primary");
        signInAgainBtn.setMaxWidth(Double.MAX_VALUE);
        signInAgainBtn.setOnAction(e -> setState(AuthState.SIGN_IN));

        cardContainer.getChildren().addAll(header, signInAgainBtn);
    }

    // =========================================================================
    // HELPERS & WIDGET BUILDERS
    // =========================================================================

    private void handleSuccessfulLogin(UserSession session, boolean rememberMe) {
        Platform.runLater(() -> {
            session.setRememberMe(rememberMe);
            if (rememberMe) {
                try {
                    DataManager.get().auth().saveSession(session);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    DataManager.get().auth().clearSession();
                } catch (Exception ignored) {}
            }

            AuthSessionManager.setActiveSession(session);

            if (onAuthSuccess != null) {
                onAuthSuccess.run();
            }
        });
    }

    private Label createLogoBadge() {
        Label badge = new Label("IS");
        badge.getStyleClass().add("auth-logo-badge");
        badge.setPrefSize(48, 48);
        return badge;
    }

    private StackPane createCircularBadge(String svgPath, String fillColor, String bgColor) {
        StackPane circle = new StackPane();
        circle.setStyle("-fx-background-color: " + bgColor + "; -fx-background-radius: 50%; -fx-pref-width: 54px; -fx-pref-height: 54px; -fx-max-width: 54px; -fx-max-height: 54px;");
        SVGPath icon = new SVGPath();
        icon.setContent(svgPath);
        icon.setFill(Color.web(fillColor));
        icon.setScaleX(1.1);
        icon.setScaleY(1.1);
        circle.getChildren().add(icon);
        return circle;
    }

    private HBox createInputBox(String iconSvg, String placeholder) {
        HBox box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("auth-input-box");

        SVGPath icon = new SVGPath();
        icon.setContent(iconSvg);
        icon.setFill(Color.web("#64748B"));
        icon.setScaleX(0.7);
        icon.setScaleY(0.7);

        TextField tf = new TextField();
        tf.setPromptText(placeholder);
        tf.getStyleClass().add("auth-transparent-field");
        HBox.setHgrow(tf, Priority.ALWAYS);

        tf.focusedProperty().addListener((obs, oldV, isFocused) -> {
            if (isFocused) {
                if (!box.getStyleClass().contains("auth-input-box-focused")) {
                    box.getStyleClass().add("auth-input-box-focused");
                }
            } else {
                box.getStyleClass().remove("auth-input-box-focused");
            }
        });

        box.getChildren().addAll(icon, tf);
        box.setUserData(tf);
        return box;
    }

    private Node createDivider() {
        HBox divider = new HBox(8);
        divider.setAlignment(Pos.CENTER);

        Region line1 = new Region();
        line1.getStyleClass().add("auth-divider-line");
        HBox.setHgrow(line1, Priority.ALWAYS);

        Label label = new Label("Or continue with");
        label.getStyleClass().add("auth-divider-text");

        Region line2 = new Region();
        line2.getStyleClass().add("auth-divider-line");
        HBox.setHgrow(line2, Priority.ALWAYS);

        divider.getChildren().addAll(line1, label, line2);
        return divider;
    }

    private void showBanner(VBox container, String text, boolean isError) {
        container.getChildren().clear();
        container.getStyleClass().removeAll("auth-error-banner", "auth-success-banner");

        Label label = new Label(text);
        label.setWrapText(true);

        if (isError) {
            container.getStyleClass().add("auth-error-banner");
            label.getStyleClass().add("auth-error-text");
        } else {
            container.getStyleClass().add("auth-success-banner");
            label.getStyleClass().add("auth-success-text");
        }

        container.getChildren().add(label);
        container.setVisible(true);
        container.setManaged(true);
    }
}
