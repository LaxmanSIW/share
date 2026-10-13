package com.invoicestudio.ui;

import com.invoicestudio.service.AppLog;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
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
 *
 * Laptop-clipping fix (docs/vault/10 §7): geometry saved on a large desktop
 * monitor used to be restored as-is on a smaller laptop screen — the window
 * then opened with its top/bottom edges outside the visible area (the floating
 * chatbot button vanished with the bottom edge). The old
 * {@code isVisibleOnAnyScreen} guard only demanded ~100 px of overlap, so a
 * mostly-off-screen window still passed. Restore now clamps the saved
 * rectangle to fit ENTIRELY inside the screen it lands on:
 * - size is capped at that screen's visual bounds (never wider/taller),
 * - position is pinned so the top edge stays reachable and the bottom edge
 *   stays visible,
 * - stage minimums are clamped to the smallest connected screen, because a
 *   laptop at 125–150 % Windows scaling exposes a much smaller LOGICAL
 *   desktop (e.g. 1280×720 at 150 %) than its panel suggests.
 */
public final class WindowStateManager {

    private static final String KEY_X        = "win.x";
    private static final String KEY_Y        = "win.y";
    private static final String KEY_W        = "win.w";
    private static final String KEY_H        = "win.h";
    private static final String KEY_MAXIMISED = "win.maximised";

    /** Absolute floor so an absurdly tiny display can't shrink the stage to nothing. */
    private static final double ABS_MIN_W = 320;
    private static final double ABS_MIN_H = 240;

    /** Source of connected-screen bounds; injectable so tests run headless. */
    @FunctionalInterface
    interface ScreenBoundsSource {
        List<Rectangle2D> visualBounds();
    }

    private final Preferences prefs = Preferences.userNodeForPackage(WindowStateManager.class);
    private final ScreenBoundsSource screens;

    public WindowStateManager() {
        this(WindowStateManager::javaFxScreenBounds);
    }

    WindowStateManager(ScreenBoundsSource screens) {
        this.screens = screens;
    }

    private static List<Rectangle2D> javaFxScreenBounds() {
        List<Rectangle2D> out = new ArrayList<>();
        for (Screen s : Screen.getScreens()) {
            out.add(s.getVisualBounds());
        }
        if (out.isEmpty() && Screen.getPrimary() != null) {
            out.add(Screen.getPrimary().getVisualBounds());
        }
        return out;
    }

    public void applyAndTrack(Stage stage, double defaultW, double defaultH, double minW, double minH) {
        // A 1024×640 minimum that fits a desktop monitor can exceed the whole
        // logical desktop of a scaled laptop panel — the stage would open
        // already clipped no matter what we restore. Cap the minimum at what
        // the smallest connected screen can actually show; views scroll.
        Rectangle2D smallest = smallestScreen(screens.visualBounds());
        if (smallest != null) {
            minW = Math.min(minW, Math.max(ABS_MIN_W, smallest.getWidth()));
            minH = Math.min(minH, Math.max(ABS_MIN_H, smallest.getHeight()));
        }
        stage.setMinWidth(minW);
        stage.setMinHeight(minH);

        boolean restored = restore(stage);

        if (!restored) {
            // First run (or unusable geometry): maximised for a professional
            // out-of-box experience — maximising also adapts to ANY screen size.
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
            if (prefs.getBoolean(KEY_MAXIMISED, false)) {
                stage.setMaximized(true);
                return true;
            }
            double w = prefs.getDouble(KEY_W, -1);
            double h = prefs.getDouble(KEY_H, -1);
            double x = prefs.getDouble(KEY_X, -1);
            double y = prefs.getDouble(KEY_Y, -1);
            if (w <= 0 || h <= 0) return false;
            // Clamp onto the screen this window will actually land on — the
            // laptop that clipped its top/bottom edges before this fix.
            double[] fit = fitToScreen(x, y, w, h, screens.visualBounds(),
                    stage.getMinWidth(), stage.getMinHeight());
            if (fit == null) return false;
            stage.setWidth(fit[2]);
            stage.setHeight(fit[3]);
            stage.setX(fit[0]);
            stage.setY(fit[1]);
            return true;
        } catch (Exception e) {
            AppLog.debug(e);
            return false;
        }
    }

    /**
     * Clamps a saved window rectangle so it fits entirely inside the screen it
     * most overlaps (the primary screen when there is no overlap at all).
     * Returns {@code null} when width/height are unusable (caller falls back
     * to defaults). Pure geometry — unit-testable without a running toolkit.
     *
     * @return array of {x, y, w, h} or {@code null}
     */
    static double[] fitToScreen(double x, double y, double w, double h,
                                List<Rectangle2D> screens, double minW, double minH) {
        if (!isFinite(w) || !isFinite(h) || w <= 0 || h <= 0) return null;
        if (!isFinite(x) || !isFinite(y)) return null;
        if (screens == null || screens.isEmpty()) return new double[]{x, y, w, h};

        // Target = the screen the window overlaps most (negative multi-monitor
        // coordinates are legit; only NaN/∞ and non-positive sizes are corrupt).
        Rectangle2D target = screens.get(0);
        double bestOverlap = -1;
        for (Rectangle2D b : screens) {
            double overlap = overlapArea(b, x, y, w, h);
            if (overlap > bestOverlap) {
                bestOverlap = overlap;
                target = b;
            }
        }

        // Size: never bigger than the target screen (THE clipping bug), but
        // keep the app's usable minimum when the screen is large enough for it.
        double fitW = Math.max(Math.min(w, target.getWidth()), Math.min(minW, target.getWidth()));
        double fitH = Math.max(Math.min(h, target.getHeight()), Math.min(minH, target.getHeight()));

        // Position: keep the saved spot when it already fits; otherwise pin
        // the window inside the screen (top edge on-screen → always draggable,
        // bottom edge visible → chatbot button reachable again).
        double fitX = clamp(x, target.getMinX(), target.getMaxX() - fitW);
        double fitY = clamp(y, target.getMinY(), target.getMaxY() - fitH);
        return new double[]{fitX, fitY, fitW, fitH};
    }

    private static Rectangle2D smallestScreen(List<Rectangle2D> bounds) {
        Rectangle2D smallest = null;
        for (Rectangle2D b : bounds) {
            if (smallest == null || area(b) < area(smallest)) smallest = b;
        }
        return smallest;
    }

    private static double area(Rectangle2D b) {
        return b.getWidth() * b.getHeight();
    }

    private static double overlapArea(Rectangle2D b, double x, double y, double w, double h) {
        double ix = Math.max(0, Math.min(b.getMaxX(), x + w) - Math.max(b.getMinX(), x));
        double iy = Math.max(0, Math.min(b.getMaxY(), y + h) - Math.max(b.getMinY(), y));
        return ix * iy;
    }

    private static double clamp(double v, double lo, double hi) {
        if (hi < lo) return lo;
        return Math.max(lo, Math.min(hi, v));
    }

    private static boolean isFinite(double v) {
        return !Double.isNaN(v) && !Double.isInfinite(v);
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
