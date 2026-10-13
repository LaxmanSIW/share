package com.invoicestudio.ui.views;

import com.invoicestudio.model.ElementType;
import com.invoicestudio.model.TemplateElement;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI-logic verification for the TEXT frame policy (user spec):
 * - the selection frame shrink-wraps the measured text (no clipping),
 * - horizontal gap text-to-frame is exactly 1 mm per side,
 * - vertical gap is driven by the line height,
 * - the invariant holds at any font size (i.e. during/after scaling).
 */
class TextFramePaddingTest {

    private static final double MM_PX = 3.7795275591;
    private static final double EPS = 0.02; // mm tolerance for font metric rounding

    @BeforeAll
    static void initFx() {
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            latch.await();
        } catch (IllegalStateException alreadyRunning) {
            // toolkit already initialized by another test
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // Note: never call Platform.exit() here — the JavaFX toolkit is shared by
    // every test class in the surefire JVM, and shutting it down makes later
    // classes crash the forked VM ("terminated without properly saying goodbye").

    private static TemplateElement textEl(String text, double fontPt, double lh, boolean bold) {
        TemplateElement el = new TemplateElement();
        el.setType(ElementType.TEXT);
        el.setText(text);
        el.setFontFamily("Segoe UI");
        el.setFontSize(fontPt);
        el.setLineHeight(lh);
        el.setBold(bold);
        return el;
    }

    /** Runs one shrink-wrap case on the FX thread and returns the element. */
    private static TemplateElement wrap(final String text, final double fontPt,
                                        final double lh, final boolean bold) throws Exception {
        final AtomicReference<TemplateElement> out = new AtomicReference<>();
        final CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            TemplateElement el = textEl(text, fontPt, lh, bold);
            TemplateDesigner.ensureTextFrame(el);
            out.set(el);
            latch.countDown();
        });
        latch.await();
        return out.get();
    }

    private static void assertPolicy(TemplateElement el) {
        double padW = TemplateDesigner.textVisualPadW();
        double padH = TemplateDesigner.textVisualPadH(el);
        double textWmm = TemplateDesigner.estimateTextWidth(el) / MM_PX;
        double textHmm = TemplateDesigner.estimateTextHeight(el) / MM_PX;

        // Horizontal: frame = text + exactly 1 mm each side.
        assertEquals(2.0 * padW, el.getW() - textWmm, EPS,
                "horizontal gap must be 1mm per side for " + el.getText());
        // Vertical: frame = text + line-height-driven padding per side.
        assertEquals(2.0 * padH, el.getH() - textHmm, EPS,
                "vertical gap must follow line height for " + el.getText());
        // No clipping: inner area strictly contains the text.
        assertTrue(el.getW() > textWmm && el.getH() > textHmm,
                "text must fit inside the frame with room to spare");
    }

    @Test
    void singleLineShrinkWrapsWith1mmHorizontalGap() throws Exception {
        assertPolicy(wrap("NERROW", 10, 1.25, false));
    }

    @Test
    void boldTextStillFits() throws Exception {
        assertPolicy(wrap("NERROW", 24, 1.25, true));
    }

    @Test
    void multiLineHeightFollowsLineHeight() throws Exception {
        TemplateElement el = wrap("LINE1\nLINE2\nLINE3", 8, 1.5, false);
        double padH = TemplateDesigner.textVisualPadH(el);
        assertEquals(0.25, padH, 1e-9, "(1.5-1)*0.5 = 0.25mm per side");
        assertPolicy(el);
    }

    @Test
    void tightLineHeightDoesNotNegativePad() throws Exception {
        TemplateElement el = wrap("Tight", 14, 0.8, false);
        assertTrue(TemplateDesigner.textVisualPadH(el) >= 0.0);
        assertPolicy(el);
    }

    /** Simulates a scale-down: a stale oversized frame must shrink back. */
    @Test
    void scaleDownShrinksTheFrame() throws Exception {
        TemplateElement el = wrap("NERROW", 10, 1.25, false);
        el.setW(el.getW() * 3.0); // stale slack from an earlier large scale
        el.setH(el.getH() * 2.0);
        TemplateDesigner.ensureTextFrame(el);
        assertPolicy(el);
    }

    /** Scale sync: padding gap must stay CONSTANT across font sizes. */
    @Test
    void horizontalGapConstantAcrossFontSizes() throws Exception {
        for (double fs : new double[]{6, 10, 18, 36}) {
            TemplateElement el = wrap("NERROW", fs, 1.25, false);
            double gap = el.getW()
                    - TemplateDesigner.estimateTextWidth(el) / MM_PX;
            assertEquals(2.0, gap, EPS, "gap constant at fontSize " + fs);
        }
    }

    @Test
    void padHFormulaMatchesSpec() {
        TemplateElement el = textEl("x", 10, 1.25, false);
        assertEquals(0.125, TemplateDesigner.textVisualPadH(el), 1e-9);
        el.setLineHeight(2.0);
        assertEquals(0.5, TemplateDesigner.textVisualPadH(el), 1e-9);
        el.setLineHeight(0.5);
        assertEquals(0.05, TemplateDesigner.textVisualPadH(el), 1e-9);
        assertEquals(1.0, TemplateDesigner.textVisualPadW(), 1e-9);
    }
}
