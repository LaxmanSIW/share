package com.invoicestudio.ui;

import com.invoicestudio.service.AppLog;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.prefs.Preferences;

/**
 * Persists window geometry (size, position, maximised state) between app runs
 * using the Java Preferences API — production apps must never lose the user's
 * preferred window layout.
 *
 * Behaviour:
 * - First launch: opens maximised at a sane default (user asked for responsive sizing).
 * - Later launches: restores the exact size + position the user left.
 * - Guards against corrupt/off-screen geometry (e.g. monitor unplugged).
 */
public final class WindowStateManager {

    private static final String KEY_X        = "win.x";
    private static final String KEY_Y        = "win.y";
    private static final String KEY_W        = "win.w";
    private static final String KEY_H        = "win.h";
    private static final String KEY_MAXIMISED = "win.maximised";

    private final Preferences prefs = Preferences.userNodeForPackage(WindowStateManager.class);

    public void applyAndTrack(Stage stage, double defaultW, double defaultH, double minW, double minH) {
        stage.setMinWidth(minW);
        stage.setMinHeight(minH);

        boolean restored = restore(stage);

        if (!restored) {
            // First run: maximised for a professional out-of-box experience.
            stage.setWidth(defaultW);
            stage.setHeight(defaultH);
            stage.setMaximized(true);
        }

        // Save on close (also catches maximise/unmaximise toggles).
        stage.setOnHidden(e -> save(stage));

        // Persist live (debounced) so an OS-crash doesn't lose state either.
        Runnable debouncedSave = new Runnable() {
            private boolean scheduled = false;
            @Override
            public void run() {
                if (scheduled) return;
                scheduled = true;
                Platform.runLater(() -> {
                    scheduled = false;
                    save(stage);
                });
            }
        };
        stage.widthProperty().addListener((o, ov, nv) -> debouncedSave.run());
        stage.heightProperty().addListener((o, ov, nv) -> debouncedSave.run());
        stage.xProperty().addListener((o, ov, nv) -> debouncedSave.run());
        stage.yProperty().addListener((o, ov, nv) -> debouncedSave.run());
        stage.maximizedProperty().addListener((o, ov, nv) -> debouncedSave.run());
    }

    private boolean restore(Stage stage) {
        try {
            if (!prefs.getBoolean(KEY_MAXIMISED, false)) {
                double w = prefs.getDouble(KEY_W, -1);
                double h = prefs.getDouble(KEY_H, -1);
                double x = prefs.getDouble(KEY_X, -1);
                double y = prefs.getDouble(KEY_Y, -1);
                if (w > 0 && h > 0 && isVisibleOnAnyScreen(x, y, w, h)) {
                    stage.setWidth(w);
                    stage.setHeight(h);
                    stage.setX(x);
                    stage.setY(y);
                    stage.centerOnScreen();
                    stage.setX(x); // re-apply after centering adjusted position
                    stage.setY(y);
                    return true;
                }
            } else {
                stage.setMaximized(true);
                return true;
            }
        } catch (Exception ignored) {
            AppLog.debug(ignored); }
        return false;
    }

    private boolean isVisibleOnAnyScreen(double x, double y, double w, double h) {
        if (x < 0 && y < 0) return false;
        for (Screen s : Screen.getScreens()) {
            Rectangle2D b = s.getVisualBounds();
            // At least 100px of the window must overlap a connected screen.
            boolean overlap = x + w > b.getMinX() + 100
                    && x < b.getMaxX() - 100
                    && y + h > b.getMinY() + 40
                    && y < b.getMaxY() - 40;
            if (overlap) return true;
        }
        return false;
    }

    private void save(Stage stage) {
        try {
            prefs.putBoolean(KEY_MAXIMISED, stage.isMaximized());
            if (!stage.isMaximized()) {
                prefs.putDouble(KEY_W, stage.getWidth());
                prefs.putDouble(KEY_H, stage.getHeight());
                prefs.putDouble(KEY_X, stage.getX());
                prefs.putDouble(KEY_Y, stage.getY());
            }
            prefs.flush();
        } catch (Exception ignored) {
            AppLog.debug(ignored); }
    }
}
