package com.invoicestudio.ui.views;

import com.invoicestudio.model.ElementType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the resize/selection-handle semantics the Template Designer now
 * follows (researched against Figma/Canva/Office conventions — see
 * docs/vault/10 Designer Selection & Resize.md):
 *
 *  - Corner handle on content objects (image/barcode/QR/icon/svg):
 *    proportional by default, Shift = temporarily free.
 *  - Corner handle on everything else (text, rect, table, …): free by
 *    default, Shift = constrain to proportions.
 *  - Side handles: always free stretch, Shift never binds them.
 *  - Aspect lock ("Bind W/H"): always binds, overrides Shift-inversion.
 */
class DesignerResizeSemanticsTest {

    private static final double ORIG_W = 40, ORIG_H = 20; // 2:1

    @Test
    void cornerDragOnImageIsProportionalByDefault() {
        double[] wh = TemplateDesigner.constrainSize(false, ElementType.IMAGE, ORIG_W, ORIG_H, 80, 25, true, false, "w");
        assertEquals(80, wh[0], 1e-9);
        assertEquals(40, wh[1], 1e-9, "height must follow the 2:1 ratio when width dominates");
    }

    @Test
    void cornerDragOnImageWithShiftBecomesFree() {
        double[] wh = TemplateDesigner.constrainSize(false, ElementType.IMAGE, ORIG_W, ORIG_H, 80, 25, true, true, "w");
        assertEquals(80, wh[0], 1e-9);
        assertEquals(25, wh[1], 1e-9, "Shift frees the corner drag for content objects");
    }

    @Test
    void cornerDragOnTextIsFreeByDefault_andShiftConstrains() {
        double[] free = TemplateDesigner.constrainSize(false, ElementType.TEXT, ORIG_W, ORIG_H, 80, 25, true, false, "w");
        assertEquals(80, free[0], 1e-9);
        assertEquals(25, free[1], 1e-9, "text boxes resize freely (industry convention)");

        double[] constrained = TemplateDesigner.constrainSize(false, ElementType.TEXT, ORIG_W, ORIG_H, 80, 25, true, true, "w");
        assertEquals(40, constrained[1], 1e-9, "Shift constrains a plain corner drag to the original ratio");
    }

    @Test
    void sideHandlesAlwaysStretchRegardlessOfTypeOrShift() {
        for (ElementType t : new ElementType[]{ElementType.IMAGE, ElementType.TEXT, ElementType.BARCODE}) {
            double[] wh = TemplateDesigner.constrainSize(false, t, ORIG_W, ORIG_H, 80, 25, false, true, "w");
            assertEquals(25, wh[1], 1e-9, "side handle must never bind height, even with Shift, for " + t);
        }
    }

    @Test
    void aspectLockAlwaysBinds_andOverridesShiftInversion() {
        // Lock + corner + content type + Shift (which would normally free it).
        double[] wh = TemplateDesigner.constrainSize(true, ElementType.IMAGE, ORIG_W, ORIG_H, 80, 25, true, true, "h");
        assertEquals(50, wh[0], 1e-9, "lock binds even when Shift asks for free");
        assertEquals(25, wh[1], 1e-9);
    }

    @Test
    void ratioComesFromDragStart_notFromCurrentSize() {
        // Simulates toggling Shift mid-drag: every call recomputes from the
        // SAME origin, so results are stable instead of compounding drift.
        double[] a = TemplateDesigner.constrainSize(true, ElementType.RECT, ORIG_W, ORIG_H, 80, 999, true, false, "w");
        double[] b = TemplateDesigner.constrainSize(true, ElementType.RECT, ORIG_W, ORIG_H, 80, 999, true, false, "w");
        assertArrayEquals(a, b, 1e-12);
        assertEquals(40, a[1], 1e-9);
    }

    @Test
    void zeroHeightOriginDoesNotDivideByZero() {
        assertDoesNotThrow(() ->
                TemplateDesigner.constrainSize(true, ElementType.RECT, 40, 0, 80, 10, true, false, "w"));
    }
}
