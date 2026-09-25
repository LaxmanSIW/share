package com.invoicestudio.ui;

import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the TitleBarTheme contract — including a real OS round trip:
 * show a stage, apply the theme, then READ the caption color back from
 * DwmGetWindowAttribute and assert it equals the brand background.
 * This is the test that would have caught both shipped bugs (reflection
 * no-op, apply-before-show), because it proves the OS actually accepted
 * the value instead of just proving the code didn't throw.
 */
class TitleBarThemeTest {

    @BeforeAll
    static void initJfx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {
            // Toolkit already initialized (another suite bootstrapped it)
        }
        // MUST run even when startup threw: this suite shows + hides a real
        // probe stage. With the default implicit exit, hiding the last visible
        // window shuts the FX toolkit down for the WHOLE surefire fork — and
        // every later Platform.runLater-based suite (Templates export/toolbar)
        // starves. (First placement was inside the try and never executed when
        // another suite had already bootstrapped FX — exactly that bug.)
        Platform.setImplicitExit(false);
    }

    @Test
    void packsHexIntoWin32ColorrefLayout() {
        // COLORREF is 0x00BBGGRR — the channel order is REVERSED vs #RRGGBB.
        assertEquals(0x000000, TitleBarTheme.packColorRef("#000000"));
        assertEquals(0x0000FF, TitleBarTheme.packColorRef("#FF0000")); // red -> 0x0000FF
        assertEquals(0x00FF00, TitleBarTheme.packColorRef("#00FF00")); // green stays middle
        assertEquals(0xFF0000, TitleBarTheme.packColorRef("#0000FF")); // blue -> 0xFF0000
        assertEquals(0x382B23, TitleBarTheme.packColorRef("#232B38")); // app border
        // Leading '#' optional
        assertEquals(0x382B23, TitleBarTheme.packColorRef("232B38"));
    }

    @Test
    void malformedHexYieldsBlackInsteadOfThrowing() {
        assertEquals(0x000000, TitleBarTheme.packColorRef(null));
        assertEquals(0x000000, TitleBarTheme.packColorRef(""));
        assertEquals(0x000000, TitleBarTheme.packColorRef("#12345"));
        assertEquals(0x000000, TitleBarTheme.packColorRef("#GGHHII"));
        assertEquals(0x000000, TitleBarTheme.packColorRef("not-a-color"));
    }

    @Test
    void appPaletteConstantsMatchGlobalfileCss() {
        // Sync contract with globalfile.css .root — keep both sides honest.
        assertEquals("#0B0E13", TitleBarTheme.CAPTION_BG);
        assertEquals("#F4F4F5", TitleBarTheme.TEXT);
        assertEquals("#232B38", TitleBarTheme.BORDER);
    }

    @Test
    void applyNeverThrows_onNullOrFreshStage() {
        // Null stage: silent no-op, no FX thread needed.
        assertDoesNotThrow(() -> TitleBarTheme.apply(null));
        // Process sweep is idempotent and must never throw either.
        assertDoesNotThrow(TitleBarTheme::applyToAllProcessWindows);
    }

    @Test
    void unknownTitleFindsNothingInsteadOfThrowing() throws Exception {
        runOnFx(() -> {
            Stage ghost = new Stage();
            ghost.setTitle("NoWindowShouldHaveThisTitle_0xFEE1DEAD");
            // Not shown → no native window → lookup must yield 0 (no-op), never throw.
            assertEquals(0L, TitleBarTheme.findWindowHandle(ghost));
        });
    }

    /**
     * THE round-trip proof: a shown stage, themed, then read back from the OS.
     * On Windows 11 the read-back must equal the brand caption color. On
     * Windows 10 the attribute is unsupported (read-back -1) but the window
     * must still have been FOUND — the plumbing assertions hold everywhere.
     */
    @Test
    void appliedThemeIsReadableBackFromTheOS() throws Exception {
        runOnFx(() -> {
            Stage probe = new Stage();
            String uniqueTitle = "TitleBarThemeProbe_" + System.nanoTime();
            probe.setTitle(uniqueTitle);
            probe.setScene(new javafx.scene.Scene(new javafx.scene.layout.Region(), 10, 10));
            probe.show();
            try {
                long hwnd = TitleBarTheme.findWindowHandle(probe);
                assertNotEquals(0L, hwnd, "a shown stage must resolve to a native window handle");

                assertDoesNotThrow(() -> TitleBarTheme.apply(probe));

                int readBack = TitleBarTheme.readCaptionColor(hwnd);
                int darkFlag = TitleBarTheme.readDarkFlag(hwnd);
                // Evidence trail lands in the surefire report — makes the
                // supported-vs-unsupported branch visible in CI output.
                System.out.println("[TitleBarThemeTest] hwnd=0x" + Long.toHexString(hwnd)
                        + " captionReadBack=0x" + Integer.toHexString(readBack)
                        + " darkFlag=" + darkFlag
                        + " expected=0x" + Integer.toHexString(TitleBarTheme.packColorRef(TitleBarTheme.CAPTION_BG)));
                if (readBack == -1) {
                    // Pre-Win11 build: exact-color attribute unsupported —
                    // but the dark-caption flag IS provable there.
                    assertTrue(darkFlag == 1 || darkFlag == -1,
                            "dark caption flag must read back as 1 (applied) or -1 (build predates 19041), was " + darkFlag);
                } else {
                    assertEquals(TitleBarTheme.packColorRef(TitleBarTheme.CAPTION_BG), readBack,
                            "OS must report the themed caption color after apply()");
                    assertEquals(1, darkFlag, "dark flag must be set alongside the exact color");
                }

                assertDoesNotThrow(TitleBarTheme::applyToAllProcessWindows);
            } finally {
                javafx.application.Platform.runLater(probe::hide);
            }
        });
    }

    @Test
    void dialogStyleAppliesThemeWithoutThrowing() throws Exception {
        runOnFx(() -> {
            javafx.scene.control.Dialog<String> dlg = new javafx.scene.control.Dialog<>();
            dlg.setTitle("Test Dialog Title");
            DialogHelper.styleDialog(dlg, 300, 200);
            assertDoesNotThrow(TitleBarTheme::applyToAllProcessWindows);
        });
    }

    /** Runs the body on the FX application thread and waits for it. */
    private static void runOnFx(Runnable body) throws Exception {
        if (Platform.isFxApplicationThread()) {
            body.run();
            return;
        }
        Throwable[] failure = new Throwable[1];
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                failure[0] = t;
            } finally {
                done.countDown();
            }
        });
        assertTrue(done.await(15, java.util.concurrent.TimeUnit.SECONDS), "FX thread did not run the body in time");
        if (failure[0] != null) {
            throw new AssertionError("Body failed on FX thread", failure[0]);
        }
    }
}
