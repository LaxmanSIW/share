package com.invoicestudio.ui;

import com.invoicestudio.service.AppLog;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

public class DialogHelper {

    private static final AtomicReference<Image> cachedIcon = new AtomicReference<>(null);

    /** The application logo, lazily loaded and cached (never throws).
     *  Uses the modern icon-only mark (crisp at window-icon sizes);
     *  falls back to the classic wordmark if the mark is unavailable. */
    public static Image getAppIcon() {
        Image ic = cachedIcon.get();
        if (ic == null) {
            try (InputStream is = DialogHelper.class.getResourceAsStream("/icons/invoice-mark.png")) {
                if (is != null) {
                    ic = new Image(is);
                    cachedIcon.set(ic);
                }
            } catch (Exception ignored) {
            AppLog.debug(ignored);
            }
            if (ic == null) {
                try (InputStream is = DialogHelper.class.getResourceAsStream("/icons/Invoicewhitebackground.png")) {
                    if (is != null) {
                        ic = new Image(is);
                        cachedIcon.set(ic);
                    }
                } catch (Exception ignored) {
            AppLog.debug(ignored);
                }
            }
        }
        return cachedIcon.get();
    }

    /**
     * Gives a stage the application logo if it does not already have one.
     * Safe to call before OR after {@code show()} — call before show when you
     * own the Stage so the title bar never flashes the default Java icon.
     */
    public static void applyAppIcon(Stage stage) {
        if (stage == null) return;
        try {
            if (!stage.getIcons().isEmpty()) return;
            Image ic = getAppIcon();
            if (ic != null) stage.getIcons().add(ic);
        } catch (Exception ignored) {
            AppLog.debug(ignored);
        }
    }

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

        // Anti-flicker: clamp the pane's MIN size BEFORE the dialog is shown.
        // JavaFX sizes the dialog stage from the pane's constrained preferred
        // size at show() time, so the very first frame already honours the
        // floor — the old approach raised the stage minimum in a
        // Platform.runLater AFTER show, which made small dialogs visibly
        // "jump" from their content size up to the minimum.
        if (minWidth > 0) pane.setMinWidth(minWidth);
        if (minHeight > 0) pane.setMinHeight(minHeight);

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

        // Ensure the dark OS title bar is applied whenever the dialog is shown
        dialog.addEventHandler(javafx.scene.control.DialogEvent.DIALOG_SHOWN, e -> {
            try {
                if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage stage) {
                    styleStage(stage);
                }
                TitleBarTheme.applyToAllProcessWindows();
                Platform.runLater(TitleBarTheme::applyToAllProcessWindows);
            } catch (Exception ignored) {
                AppLog.debug(ignored);
            }
        });

        // Set application icon + stage-level min dimensions once the dialog's
        // own stage exists (the pane min above already guarantees the size,
        // this only adds the resize floor for user-driven resizing).
        Platform.runLater(() -> {
            try {
                if (pane.getScene() != null && pane.getScene().getWindow() instanceof Stage stage) {
                    if (minWidth > 0) stage.setMinWidth(minWidth);
                    if (minHeight > 0) stage.setMinHeight(minHeight);
                    styleStage(stage);
                    if (css != null && !pane.getScene().getStylesheets().contains(css)) {
                        pane.getScene().getStylesheets().add(css);
                    }
                    ThemeManager.getInstance().applyToScene(pane.getScene());
                }
            } catch (Exception ignored) {
            AppLog.debug(ignored); }
        });
    }

    public static void styleStage(Stage stage) {
        if (stage == null) return;
        applyAppIcon(stage);
        if (stage.isShowing()) {
            TitleBarTheme.apply(stage);
            TitleBarTheme.applyToAllProcessWindows();
        }
        stage.addEventHandler(javafx.stage.WindowEvent.WINDOW_SHOWN, e -> {
            TitleBarTheme.apply(stage);
            TitleBarTheme.applyToAllProcessWindows();
            Platform.runLater(TitleBarTheme::applyToAllProcessWindows);
        });
        stage.showingProperty().addListener((obs, was, is) -> {
            if (is) {
                TitleBarTheme.apply(stage);
                TitleBarTheme.applyToAllProcessWindows();
                Platform.runLater(TitleBarTheme::applyToAllProcessWindows);
            }
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
        ThemeManager.getInstance().applyToScene(scene);
        if (scene.getWindow() instanceof Stage stage) {
            styleStage(stage);
        }
        scene.windowProperty().addListener((obs, oldW, newW) -> {
            if (newW instanceof Stage s) {
                styleStage(s);
            }
        });
    }
}
