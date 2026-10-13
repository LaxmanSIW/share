package com.invoicestudio.ui;

import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the laptop-clipping fix (docs/vault/10 §7): window geometry saved on a
 * large desktop monitor used to be restored as-is on the laptop, opening the
 * window with its top/bottom edges (and the bottom-right chatbot button)
 * outside the visible screen. The restored rectangle must now always fit
 * entirely inside the screen it lands on.
 *
 * Pure geometry — no JavaFX toolkit is started (Rectangle2D is a value type).
 */
class WindowStateManagerGeometryTest {

    /** Desktop monitor, 100 % scaling. */
    private static final Rectangle2D DESKTOP = new Rectangle2D(0, 0, 1920, 1040);
    /** Laptop panel at 150 % Windows scaling — logical desktop is small. */
    private static final Rectangle2D LAPTOP = new Rectangle2D(0, 0, 1280, 664);

    private static final double MIN_W = 1024, MIN_H = 640;

    @Test
    void geometrySavedOnDesktopFitsTheLaptopScreen() {
        // User maximises on the 1920×1040 desktop, drags to windowed 1800×1000
        // at (60, 20), then opens the app on the 1280×664 laptop.
        double[] fit = WindowStateManager.fitToScreen(60, 20, 1800, 1000,
                List.of(LAPTOP), MIN_W, MIN_H);

        assertNotNull(fit);
        assertEquals(1280, fit[2], 1e-9, "width must be capped to the laptop screen");
        assertEquals(664, fit[3], 1e-9, "height must be capped to the laptop screen (was clipped before)");
        assertEquals(0, fit[0], 1e-9, "left edge must stay on-screen");
        assertEquals(0, fit[1], 1e-9, "top edge must stay reachable (was clipped before)");
    }

    @Test
    void savedSizeBetweenMinAndScreenIsKept() {
        // 1500×900 saved on the desktop: bigger than the laptop minimum but
        // bigger than the laptop screen too — cap, don't stretch to the minimum.
        double[] fit = WindowStateManager.fitToScreen(100, 100, 1500, 900,
                List.of(LAPTOP), MIN_W, MIN_H);
        assertEquals(1280, fit[2], 1e-9);
        assertEquals(664, fit[3], 1e-9);
    }

    @Test
    void validGeometryInsideTheScreenIsUntouched() {
        double[] fit = WindowStateManager.fitToScreen(200, 150, 1300, 800,
                List.of(DESKTOP), MIN_W, MIN_H);
        assertArrayEquals(new double[]{200, 150, 1300, 800}, fit, 1e-9,
                "a window that already fits (and respects the app minimum) must be restored exactly");
    }

    @Test
    void windowSmallerThanTheAppMinimumIsRaisedToIt() {
        // The stage enforces minWidth/minHeight at runtime, so restoring a
        // 900×500 saved window verbatim would get silently bumped by JavaFX —
        // AFTER our position pinning. Grow it here so pinning sees the final
        // size and the bottom/right edge can't overflow after the bump.
        double[] fit = WindowStateManager.fitToScreen(200, 150, 900, 500,
                List.of(DESKTOP), MIN_W, MIN_H);
        assertArrayEquals(new double[]{200, 150, 1024, 640}, fit, 1e-9);
    }

    @Test
    void bottomEdgeIsPinnedBackOntoTheScreen() {
        // Window dragged mostly below the visible area (dock drift, remote
        // desktop, laptop screen change) — bottom (chatbot) was unreachable.
        double[] fit = WindowStateManager.fitToScreen(300, 800, 900, 500,
                List.of(DESKTOP), MIN_W, MIN_H);
        // 500 grows to the 640 app minimum first; then y pins so the bottom
        // edge sits exactly on the screen's bottom: 1040 - 640 = 400.
        assertEquals(400, fit[1], 1e-9, "y must be pinned so the bottom edge is visible");
        assertEquals(1040, fit[1] + fit[3], 1e-9, "bottom edge must land on the screen edge, not past it");
    }

    @Test
    void negativeCoordinatesFromLeftMonitorStillWork() {
        // Multi-monitor: laptop left of the desktop → x can be negative.
        Rectangle2D left = new Rectangle2D(-1920, 0, 1920, 1040);
        double[] fit = WindowStateManager.fitToScreen(-1800, 40, 1100, 700,
                List.of(left, DESKTOP), MIN_W, MIN_H);
        assertEquals(-1800, fit[0], 1e-9, "legit negative x must be preserved");
        assertEquals(1100, fit[2], 1e-9);
        assertEquals(700, fit[3], 1e-9);
    }

    @Test
    void windowLandsOnTheScreenItOverlapsMost() {
        // Fully on the 1920-wide desktop, no overlap with the 1280 laptop to
        // its right → must NOT be clamped to the laptop's smaller size.
        Rectangle2D laptopRight = new Rectangle2D(1920, 0, 1280, 664);
        double[] fit = WindowStateManager.fitToScreen(300, 100, 1500, 700,
                List.of(laptopRight, DESKTOP), MIN_W, MIN_H);
        assertEquals(1500, fit[2], 1e-9, "must not be shrunk to the far screen's size");
        assertEquals(300, fit[0], 1e-9, "already-fitting x must be untouched");
    }

    @Test
    void corruptValuesFallBackToDefaults() {
        assertNull(WindowStateManager.fitToScreen(10, 10, -5, 500, List.of(DESKTOP), MIN_W, MIN_H),
                "non-positive size is corrupt → caller falls back to defaults");
        assertNull(WindowStateManager.fitToScreen(Double.NaN, 10, 900, 500, List.of(DESKTOP), MIN_W, MIN_H),
                "NaN position is corrupt → caller falls back to defaults");
        double[] noScreens = WindowStateManager.fitToScreen(10, 20, 800, 600, List.of(), MIN_W, MIN_H);
        assertArrayEquals(new double[]{10, 20, 800, 600}, noScreens, 1e-9,
                "no screen info → keep saved values (old behaviour)");
    }

    @Test
    void tinyScreenStillAllowsUsableMinimums() {
        Rectangle2D mini = new Rectangle2D(0, 0, 800, 500);
        double[] fit = WindowStateManager.fitToScreen(0, 0, 1600, 900, List.of(mini), MIN_W, MIN_H);
        assertEquals(800, fit[2], 1e-9, "screen smaller than the minimum → screen wins");
        assertEquals(500, fit[3], 1e-9);
    }
}
