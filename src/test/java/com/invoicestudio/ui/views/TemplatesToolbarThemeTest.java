package com.invoicestudio.ui.views;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Templates Gallery top bar mixes a plain {@code Button} ("Import") with a
 * {@code MenuButton} ("Export") that must look and measure identically. Modena
 * gives the menu's inner {@code .label} its own dark text fill and gives the
 * arrow-button a taller em padding, so both are asserted here against the real
 * {@code globalfile.css} — with the app's own style classes, no inline styles.
 */
class TemplatesToolbarThemeTest {

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException alreadyRunning) {
            // Toolkit already initialized by another test class
        }
    }

    private static void fx(Runnable body) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                body.run();
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            assertTrue(latch.await(30, TimeUnit.SECONDS), "FX task timed out");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            fail("interrupted");
        }
        if (err.get() != null) {
            if (err.get() instanceof RuntimeException re) throw re;
            if (err.get() instanceof Error e) throw e;
            throw new RuntimeException(err.get());
        }
    }

    private static Button importButton() {
        Button b = new Button("Import");
        b.getStyleClass().addAll("button-sm", "button-secondary");
        return b;
    }

    /** Applies the shipped stylesheet, lays the row out and returns both nodes. */
    private static void withToolbarRow(java.util.function.BiConsumer<Button, MenuButton> assertions) {
        fx(() -> {
            Button importBtn = importButton();
            MenuButton exportMenu = TemplatesView.buildExportMenu(java.util.List.of(), list -> {});
            HBox row = new HBox(16, importBtn, exportMenu);

            Scene scene = new Scene(row, 800, 200);
            java.net.URL css = TemplatesView.class.getResource("/css/globalfile.css");
            assertNotNull(css, "the app stylesheet must be on the test classpath");
            scene.getStylesheets().add(css.toExternalForm());

            row.applyCss();
            row.layout();

            assertions.accept(importBtn, exportMenu);
        });
    }

    /**
     * A Button paints its caption itself; a MenuButton wraps it in a Label —
     * exactly the node that Modena recolours to near-black.
     */
    private static Label menuCaption(MenuButton menu) {
        javafx.scene.Node node = menu.lookup(".label");
        assertNotNull(node, "expected the menu to expose an inner .label");
        return (Label) node;
    }

    @Test
    void exportDropdownTextIsInheritedFromTheThemeNotModenasDarkDefault() {
        withToolbarRow((importBtn, exportMenu) -> {
            Label exportText = menuCaption(exportMenu);

            assertTrue(((Color) importBtn.getTextFill()).getBrightness() > 0.5,
                    "sanity: the theme's secondary button text is light");
            assertEquals(importBtn.getTextFill(), exportText.getTextFill(),
                    "the Export caption must inherit the theme text color, not Modena's dark default");
        });
    }

    @Test
    void exportDropdownIsExactlyTheHeightOfTheImportButton() {
        withToolbarRow((importBtn, exportMenu) -> {
            assertEquals(importBtn.getPadding(), exportMenu.getPadding(),
                    "the dropdown must share the .button-sm padding");
            assertEquals(importBtn.getFont(), exportMenu.getFont(),
                    "the dropdown must share the .button-sm font");
            assertEquals(importBtn.getHeight(), exportMenu.getHeight(), 0.5,
                    "Export must be exactly the Import box, not the taller Modena menu");
        });
    }
}
