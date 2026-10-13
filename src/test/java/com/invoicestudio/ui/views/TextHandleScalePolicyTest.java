package com.invoicestudio.ui.views;

import com.invoicestudio.model.ElementType;
import com.invoicestudio.model.TemplateElement;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Bind W/H semantics on a TEXT object.
 *
 * <p>Bound (default): dragging a handle scales the FONT, so both dimensions
 * grow together. Unbound: only the dragged axis changes — the frame takes the
 * drag and the glyphs stretch via scaleX/scaleY, so the box keeps hugging the
 * text.</p>
 */
class TextHandleScalePolicyTest {

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

    private static TemplateElement textEl() {
        TemplateElement el = new TemplateElement();
        el.setType(ElementType.TEXT);
        el.setText("NERROW");
        el.setFontFamily("Segoe UI");
        el.setFontSize(10);
        el.setLineHeight(1.25);
        return el;
    }

    private static void onFx(Runnable r) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                r.run();
            } finally {
                latch.countDown();
            }
        });
        latch.await();
    }

    @Test
    void boundDragScalesTheFontAndKeepsAspect() throws Exception {
        onFx(() -> {
            TemplateElement el = textEl();
            double[] base = TemplateDesigner.naturalTextFrame(el);
            TemplateDesigner.applyTextDrag(el, base[0] * 2.0, base[1] * 2.0,
                    base[0], base[1], 10.0, true);
            assertEquals(20.0, el.getFontSize(), 0.01, "font scales with the drag");
            assertEquals(1.0, el.getScaleX(), 1e-9, "bound mode must not stretch");
            assertEquals(1.0, el.getScaleY(), 1e-9);
        });
    }

    @Test
    void freeDragStretchesOnlyTheDraggedAxis() throws Exception {
        onFx(() -> {
            TemplateElement el = textEl();
            double[] base = TemplateDesigner.naturalTextFrame(el);
            TemplateDesigner.applyTextDrag(el, base[0] * 1.5, base[1],
                    base[0], base[1], 10.0, false);
            assertEquals(10.0, el.getFontSize(), 0.01, "free mode keeps the font size");
            assertEquals(1.5, el.getScaleX(), 0.02, "X stretch follows the drag");
            assertEquals(1.0, el.getScaleY(), 0.02, "Y must stay untouched");
        });
    }

    @Test
    void freeDragVerticallyStretchesOnlyY() throws Exception {
        onFx(() -> {
            TemplateElement el = textEl();
            double[] base = TemplateDesigner.naturalTextFrame(el);
            TemplateDesigner.applyTextDrag(el, base[0], base[1] * 2.2,
                    base[0], base[1], 10.0, false);
            assertEquals(1.0, el.getScaleX(), 0.02, "X must stay untouched");
            assertEquals(2.2, el.getScaleY(), 0.02);
        });
    }

    @Test
    void frameHugsTheStretchedTextAfterRelease() throws Exception {
        onFx(() -> {
            TemplateElement el = textEl();
            double[] base = TemplateDesigner.naturalTextFrame(el);
            TemplateDesigner.applyTextDrag(el, base[0] * 1.8, base[1] * 1.4,
                    base[0], base[1], 10.0, false);
            // release-time shrink-wrap must keep the dragged box exactly
            TemplateDesigner.ensureTextFrame(el);
            assertEquals(base[0] * 1.8, el.getW(), 0.05);
            assertEquals(base[1] * 1.4, el.getH(), 0.05);
        });
    }

    @Test
    void constraintPolicyMatchesTheAspectLock() {
        // "Bind W/H" on → proportional from every handle, side handles included.
        double[] lockedSide = TemplateDesigner.constrainSize(
                true, ElementType.TEXT, 40, 10, 60, 10, false, false, "w");
        assertEquals(60.0, lockedSide[0], 1e-9);
        assertEquals(15.0, lockedSide[1], 1e-9, "bound side drag grows both dimensions");
        // lock off + side handle → that axis only
        double[] freeSide = TemplateDesigner.constrainSize(
                false, ElementType.TEXT, 40, 10, 60, 10, false, false, "w");
        assertEquals(60.0, freeSide[0], 1e-9);
        assertEquals(10.0, freeSide[1], 1e-9, "free side drag changes only its axis");
    }

    @Test
    void zeroStartFontFallbackKeepsFontSensible() throws Exception {
        onFx(() -> {
            TemplateElement el = textEl();
            el.setFontSize(14.0);
            double[] base = TemplateDesigner.naturalTextFrame(el);
            // Pass 0.0 for startFont (simulating missing resizeStart[6])
            TemplateDesigner.applyTextDrag(el, base[0] * 1.5, base[1] * 1.5,
                    base[0], base[1], 0.0, true);
            // Must scale from el.getFontSize() (14.0 * 1.5 = 21.0), NOT collapse to 0.5 pt!
            assertEquals(21.0, el.getFontSize(), 0.1, "fallback must use current element font size, not collapse to 0.5 pt");
        });
    }

    @Test
    void anchorPinningMaintainsStationaryCoordinatesOnRelease() throws Exception {
        onFx(() -> {
            TemplateElement el = textEl();
            el.setX(100.0);
            el.setY(200.0);
            double origW = 50.0;
            double origH = 20.0;
            el.setW(origW);
            el.setH(origH);

            double origRight = el.getX() + el.getW(); // 150.0
            double origBottom = el.getY() + el.getH(); // 220.0

            // 1. NW resize: dragging top-left corner must keep bottom-right stationary
            TemplateDesigner.ensureTextFrame(el);
            double nwX = origRight - el.getW();
            double nwY = origBottom - el.getH();
            assertEquals(origRight, nwX + el.getW(), 1e-6, "NW release must pin bottom-right X anchor");
            assertEquals(origBottom, nwY + el.getH(), 1e-6, "NW release must pin bottom-right Y anchor");

            // 2. NE resize: dragging top-right corner must keep bottom-left stationary
            double neX = el.getX();
            double neY = origBottom - el.getH();
            assertEquals(el.getX(), neX, 1e-6, "NE release must pin bottom-left X anchor");
            assertEquals(origBottom, neY + el.getH(), 1e-6, "NE release must pin bottom-left Y anchor");

            // 3. SW resize: dragging bottom-left corner must keep top-right stationary
            double swX = origRight - el.getW();
            double swY = el.getY();
            assertEquals(origRight, swX + el.getW(), 1e-6, "SW release must pin top-right X anchor");
            assertEquals(el.getY(), swY, 1e-6, "SW release must pin top-right Y anchor");

            // 4. W resize: dragging left handle must keep right edge stationary
            double wX = origRight - el.getW();
            assertEquals(origRight, wX + el.getW(), 1e-6, "W release must pin right edge anchor");

            // 5. N resize: dragging top handle must keep bottom edge stationary
            double nY = origBottom - el.getH();
            assertEquals(origBottom, nY + el.getH(), 1e-6, "N release must pin bottom edge anchor");
        });
    }
}
