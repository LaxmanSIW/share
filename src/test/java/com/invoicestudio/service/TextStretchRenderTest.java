package com.invoicestudio.service;

import com.invoicestudio.model.ElementType;
import com.invoicestudio.model.TemplateElement;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the free-mode TEXT stretch (Bind W/H off): the label is laid out at
 * its unscaled size and stretched from the top-left, so the glyphs fill the
 * element frame exactly and the selection box keeps hugging the text.
 */
class TextStretchRenderTest {

    @BeforeAll
    static void initFx() {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            latch.await();
        } catch (IllegalStateException alreadyRunning) {
            // toolkit already up
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static TemplateElement textEl(double sx, double sy) {
        TemplateElement el = new TemplateElement();
        el.setType(ElementType.TEXT);
        el.setText("NERROW");
        el.setFontFamily("Segoe UI");
        el.setFontSize(12);
        el.setLineHeight(1.25);
        el.setScaleX(sx);
        el.setScaleY(sy);
        return el;
    }

    /** Renders the element into a laid-out pane and returns the visual bounds. */
    private static Bounds renderBounds(double sx, double sy, double w, double h) throws Exception {
        AtomicReference<Bounds> out = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            Pane pane = new Pane();
            pane.setPrefSize(w, h);
            Node visual = DesignObjectRenderer.render(textEl(sx, sy), null, w, h);
            pane.getChildren().add(visual);
            Scene scene = new Scene(pane, w, h);
            pane.applyCss();
            pane.layout();
            out.set(visual.getBoundsInParent());
            // keep a strong ref to the scene so it is not collected mid-layout
            assertTrue(scene.getWidth() > 0);
            latch.countDown();
        });
        latch.await();
        return out.get();
    }

    @Test
    void unstretchedTextFillsTheFrame() throws Exception {
        Bounds b = renderBounds(1.0, 1.0, 120, 40);
        assertEquals(0.0, b.getMinX(), 1.5);
        assertEquals(0.0, b.getMinY(), 1.5);
        assertEquals(120.0, b.getWidth(), 2.5);
        assertEquals(40.0, b.getHeight(), 2.5);
    }

    @Test
    void horizontallyStretchedTextStillFillsTheFrame() throws Exception {
        Bounds b = renderBounds(2.0, 1.0, 120, 40);
        assertEquals(0.0, b.getMinX(), 1.5, "stretch must start at the frame's left edge");
        assertEquals(120.0, b.getWidth(), 2.5, "stretched glyphs must fill the frame width");
        assertEquals(40.0, b.getHeight(), 2.5);
    }

    @Test
    void verticallyStretchedTextStillFillsTheFrame() throws Exception {
        Bounds b = renderBounds(1.0, 2.0, 120, 40);
        assertEquals(0.0, b.getMinY(), 1.5);
        assertEquals(40.0, b.getHeight(), 2.5, "stretched glyphs must fill the frame height");
        assertEquals(120.0, b.getWidth(), 2.5);
    }

    @Test
    void bothAxesStretchedFillsTheFrame() throws Exception {
        Bounds b = renderBounds(1.6, 2.4, 120, 40);
        assertEquals(0.0, b.getMinX(), 1.5);
        assertEquals(0.0, b.getMinY(), 1.5);
        assertEquals(120.0, b.getWidth(), 2.5);
        assertEquals(40.0, b.getHeight(), 2.5);
    }
}
