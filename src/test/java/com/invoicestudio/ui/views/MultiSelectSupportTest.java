package com.invoicestudio.ui.views;

import com.invoicestudio.model.TemplateElement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pins the pure geometry/logic of multi-selection (F3) and the spacing
 * layer (F5): marquee hit-testing, union bounds, equal/nudge/distribute
 * gap math, and the multi-element clipboard contract in DesignerState.
 * Positions are plain mm coordinates — exactly what the database stores.
 */
class MultiSelectSupportTest {

    private static TemplateElement el(double x, double y, double w, double h) {
        TemplateElement e = new TemplateElement();
        e.setId("el_" + x + "_" + y);
        e.setX(x);
        e.setY(y);
        e.setW(w);
        e.setH(h);
        return e;
    }

    // ── union bounds ──────────────────────────────────────────────────────

    @Test
    void unionBoundsSpansAllElements() {
        List<TemplateElement> els = List.of(el(10, 5, 20, 10), el(5, 20, 30, 8));
        double[] b = MultiSelectSupport.unionBounds(els);
        assertEquals(5, b[0], 1e-9);
        assertEquals(5, b[1], 1e-9);
        assertEquals(30, b[2], 1e-9, "width = maxX - minX");
        assertEquals(23, b[3], 1e-9, "height = maxY - minY");
    }

    @Test
    void unionBoundsOfEmptySelectionIsZero() {
        assertArrayEquals(new double[]{0, 0, 0, 0}, MultiSelectSupport.unionBounds(List.of()));
    }

    // ── marquee hit test ─────────────────────────────────────────────────

    @Test
    void marqueeCatchesElementsItIntersects_andSkipsHiddenLocked() {
        TemplateElement inside = el(10, 10, 10, 10);
        TemplateElement straddling = el(25, 25, 10, 10);
        TemplateElement outside = el(60, 60, 10, 10);
        TemplateElement hidden = el(12, 12, 5, 5);
        hidden.setHidden(true);
        TemplateElement locked = el(12, 40, 5, 5);
        locked.setLocked(true);

        List<TemplateElement> hit = MultiSelectSupport.elementsInMarquee(
                List.of(inside, straddling, outside, hidden, locked), 8, 8, 20, 20);

        assertTrue(hit.contains(inside));
        assertTrue(hit.contains(straddling), "partial overlap must count");
        assertFalse(hit.contains(outside));
        assertFalse(hit.contains(hidden), "hidden elements are never selectable");
        assertFalse(hit.contains(locked), "locked elements are never selectable");
    }

    // ── vertical gaps ────────────────────────────────────────────────────

    @Test
    void equalVerticalGapAlignsEveryRowAtTargetGap() {
        List<TemplateElement> els = new ArrayList<>(List.of(
                el(0, 0, 20, 10), el(0, 18, 20, 10), el(0, 40, 20, 10)));
        List<TemplateElement> changed = MultiSelectSupport.applyEqualVerticalGap(els, 5.0);

        assertEquals(0, els.get(0).getY(), 1e-9, "first element never moves");
        assertEquals(15, els.get(1).getY(), 1e-9);
        assertEquals(30, els.get(2).getY(), 1e-9);
        assertEquals(2, changed.size(), "both lower elements actually moved");
    }

    @Test
    void equalVerticalGapKeepsAlreadyAlignedRowsUnchanged() {
        List<TemplateElement> els = new ArrayList<>(List.of(
                el(0, 0, 20, 10), el(0, 15, 20, 10), el(0, 40, 20, 10)));
        List<TemplateElement> changed = MultiSelectSupport.applyEqualVerticalGap(els, 5.0);

        assertEquals(15, els.get(1).getY(), 1e-9, "row already at target gap stays put");
        assertEquals(30, els.get(2).getY(), 1e-9);
        assertEquals(1, changed.size(), "only the actually-moved element is reported");
    }

    @Test
    void equalVerticalGapWithTwoElementsMovesOnlyTheLower() {
        List<TemplateElement> els = new ArrayList<>(List.of(
                el(0, 0, 20, 10), el(0, 40, 20, 10)));
        MultiSelectSupport.applyEqualVerticalGap(els, 7.5);
        assertEquals(17.5, els.get(1).getY(), 1e-9);
    }

    @Test
    void verticalGapDeltaNudgesEachRowByItsIndex() {
        List<TemplateElement> els = new ArrayList<>(List.of(
                el(0, 10, 10, 5), el(0, 20, 10, 5), el(0, 30, 10, 5)));
        MultiSelectSupport.applyVerticalGapDelta(els, 2.0);
        assertEquals(10, els.get(0).getY(), 1e-9, "first row anchors");
        assertEquals(22, els.get(1).getY(), 1e-9, "2nd row shifts by 1x delta");
        assertEquals(34, els.get(2).getY(), 1e-9, "3rd row shifts by 2x delta");
    }

    @Test
    void verticalGapDeltaCanTightenGaps() {
        List<TemplateElement> els = new ArrayList<>(List.of(
                el(0, 0, 10, 5), el(0, 20, 10, 5)));
        MultiSelectSupport.applyVerticalGapDelta(els, -3.0);
        assertEquals(17, els.get(1).getY(), 1e-9, "negative delta decreases the gap");
    }

    // ── horizontal gaps ──────────────────────────────────────────────────

    @Test
    void equalHorizontalGapAlignsEveryColumn() {
        List<TemplateElement> els = new ArrayList<>(List.of(
                el(0, 0, 10, 10), el(30, 0, 10, 10), el(70, 0, 10, 10)));
        List<TemplateElement> changed = MultiSelectSupport.applyEqualHorizontalGap(els, 4.0);
        assertEquals(0, els.get(0).getX(), 1e-9);
        assertEquals(14, els.get(1).getX(), 1e-9);
        assertEquals(28, els.get(2).getX(), 1e-9);
        assertEquals(2, changed.size());
    }

    @Test
    void horizontalGapDeltaNudgesEachColumnByItsIndex() {
        List<TemplateElement> els = new ArrayList<>(List.of(
                el(5, 0, 10, 10), el(15, 0, 10, 10), el(25, 0, 10, 10)));
        MultiSelectSupport.applyHorizontalGapDelta(els, 1.5);
        assertEquals(5, els.get(0).getX(), 1e-9);
        assertEquals(16.5, els.get(1).getX(), 1e-9);
        assertEquals(28, els.get(2).getX(), 1e-9);
    }

    @Test
    void gapHelpersIgnoreSingleElementSelections() {
        List<TemplateElement> one = List.of(el(0, 0, 10, 10));
        assertTrue(MultiSelectSupport.applyEqualVerticalGap(one, 5).isEmpty());
        assertTrue(MultiSelectSupport.applyVerticalGapDelta(one, 5).isEmpty());
        assertTrue(MultiSelectSupport.applyEqualHorizontalGap(one, 5).isEmpty());
        assertTrue(MultiSelectSupport.applyHorizontalGapDelta(one, 5).isEmpty());
    }

    // ── gap read-out (drives the properties panel label) ─────────────────

    @Test
    void gapReadoutsReportCurrentSpacing() {
        List<TemplateElement> col = List.of(
                el(0, 0, 10, 10), el(0, 14, 10, 6), el(0, 24, 10, 6));
        assertEquals(List.of(4.0, 4.0), MultiSelectSupport.verticalGaps(col));

        List<TemplateElement> row = List.of(
                el(0, 0, 10, 10), el(14, 0, 10, 10));
        assertEquals(List.of(4.0), MultiSelectSupport.horizontalGaps(row));
    }

    // ── multi-element clipboard (DesignerState) ──────────────────────────

    @Test
    void designerStateClipboardRoundTripsMultipleElements() {
        DesignerState state = new DesignerState();
        List<TemplateElement> sel = List.of(el(1, 2, 10, 5), el(20, 30, 15, 8));
        sel.forEach(e -> e.setText("row-" + e.getId()));

        state.copyAll(sel);
        List<TemplateElement> pasted = state.pasteAllSource();
        assertNotNull(pasted);
        assertEquals(2, pasted.size());
        assertEquals("row-el_1.0_2.0", pasted.get(0).getText());
        assertEquals(30, pasted.get(1).getY(), 1e-9, "deep copy keeps relative positions");

        pasted.get(0).setY(999);
        assertEquals(2, state.pasteAllSource().get(0).getY(), 1e-9,
                "paste must hand out fresh copies, not the clipboard");
    }

    @Test
    void designerStatePasteAllIsNullWhenClipboardEmpty() {
        assertNull(new DesignerState().pasteAllSource());
    }
}
