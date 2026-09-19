package com.invoicestudio.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shared copy button: renders a copy glyph, copies the FULL value
 * (masked text in rows is display-only), and flips to a gold check with a
 * "Copied!" tooltip after the click. Clipboard access is FX-thread-only, so
 * every interaction runs on the real toolkit thread (JFXPanel bootstrap,
 * same as the other UI harnesses).
 */
class CopyButtonFactoryTest {

    @BeforeAll
    static void initFx() {
        try {
            new javafx.embed.swing.JFXPanel();
        } catch (IllegalStateException ignored) {}
    }

    /** Runs r on the FX thread and rethrows any failure there. */
    private static void fx(javafx.util.Duration wait, Runnable r) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();
        javafx.application.Platform.runLater(() -> {
            try {
                r.run();
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        });
        assertTrue(latch.await(30, TimeUnit.SECONDS), "FX task timed out");
        if (err.get() != null) {
            if (err.get() instanceof RuntimeException re) throw re;
            if (err.get() instanceof Error e) throw e;
            throw new RuntimeException(err.get());
        }
    }

    @Test
    void clickCopiesFullValueAndFlipsToCheck() throws Exception {
        AtomicReference<String> clip = new AtomicReference<>();
        AtomicReference<String> tip = new AtomicReference<>();
        AtomicReference<Boolean> glyphIsVector = new AtomicReference<>();
        fx(javafx.util.Duration.seconds(10), () -> {
            Clipboard.getSystemClipboard().setContent(new ClipboardContent());
            assertNull(Clipboard.getSystemClipboard().getString(), "precondition: clipboard empty");

            Button b = CopyButtonFactory.create("AIzaSyTESTKEY1234567890", "Copy Gemini free tier key");
            b.fire();

            clip.set(Clipboard.getSystemClipboard().getString());
            Tooltip t = b.getTooltip();
            tip.set(t == null ? null : t.getText());
            glyphIsVector.set(b.getGraphic() instanceof SVGPath);
        });
        assertEquals("AIzaSyTESTKEY1234567890", clip.get(),
                "the FULL key must land on the clipboard, never the masked form");
        assertEquals("Copied!", tip.get(), "tooltip flips to Copied! after the click");
        assertTrue(glyphIsVector.get(), "graphic swaps to a vector glyph");
    }

    @Test
    void nullOrEmptyTextCopiesEmptyStringNotCrash() throws Exception {
        fx(javafx.util.Duration.seconds(10), () -> {
            Button b = CopyButtonFactory.create(null);
            assertDoesNotThrow(b::fire);
        });
    }

    @Test
    void defaultTooltipUsedWhenNoneGiven() {
        Button b = CopyButtonFactory.create("x");
        assertEquals("Copy", b.getTooltip().getText());
    }
}
