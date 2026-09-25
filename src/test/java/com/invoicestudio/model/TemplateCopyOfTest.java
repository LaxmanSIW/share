package com.invoicestudio.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Duplicate Template deep-copy contract (bug reported 2026-09: duplicating a
 * barcode template produced a bill template and lost print settings, because
 * the copy was rebuilt from a bill preset and only page+elements were taken).
 */
class TemplateCopyOfTest {

    private Template labelTemplate() {
        Template t = new Template("tpl_src", "Shop Barcode", new PageConfig(), new ArrayList<>());
        t.setMode("label");
        t.setPrintOffsetX(3.5);
        t.setPrintOffsetY(1.25);
        t.setCreatedAt("2026-01-01T00:00:00Z");
        t.setUpdatedAt("2026-02-02T00:00:00Z");

        LabelConfig lc = new LabelConfig();
        t.setLabelConfig(lc);

        TemplateElement text = new TemplateElement();
        text.setType(ElementType.TEXT);
        text.setText("hello");
        text.setX(10); text.setY(20); text.setW(30); text.setH(5);

        TemplateElement bar = new TemplateElement();
        bar.setType(ElementType.BARCODE);
        bar.setBarcodeData("{{invoice_no}}");
        bar.setX(1); bar.setY(2); bar.setW(40); bar.setH(12);

        List<TemplateElement> els = new ArrayList<>();
        els.add(text); els.add(bar);
        t.setElements(els);
        return t;
    }

    @Test
    void copyPreservesEverythingThatDuplicateUsedToDrop() {
        Template src = labelTemplate();
        Template copy = Template.copyOf(src);

        assertEquals("label", copy.getMode(), "mode must survive the copy (barcode stays barcode)");
        assertNotNull(copy.getLabelConfig(), "labelConfig must survive the copy");
        assertEquals(3.5, copy.getPrintOffsetX(), 1e-9, "print offsets must survive the copy");
        assertEquals(1.25, copy.getPrintOffsetY(), 1e-9);
        assertEquals("Shop Barcode", copy.getName());
        assertEquals("2026-01-01T00:00:00Z", copy.getCreatedAt());
        assertEquals("2026-02-02T00:00:00Z", copy.getUpdatedAt());
        assertEquals(2, copy.getElements().size());
    }

    @Test
    void copyIsDeep_elementsAreClonedNotShared() {
        Template src = labelTemplate();
        Template copy = Template.copyOf(src);

        assertNotSame(src.getElements().get(0), copy.getElements().get(0), "element instances must be cloned");
        assertNotSame(src.getElements().get(1), copy.getElements().get(1));
        assertNotSame(src.getLabelConfig(), copy.getLabelConfig(), "labelConfig must be cloned");

        // Editing the copy must never mutate the source.
        copy.getElements().get(0).setText("changed");
        copy.setPrintOffsetX(99);
        assertEquals("hello", src.getElements().get(0).getText());
        assertEquals(3.5, src.getPrintOffsetX(), 1e-9);
    }

    @Test
    void copyHasNoId_soDuplicateGetsAFreshRow() {
        Template copy = Template.copyOf(labelTemplate());
        assertNull(copy.getId(), "id must be cleared so the caller assigns a fresh one and the DAO upsert can never overwrite the source row");
    }

    @Test
    void copyOfNullIsNull_andNullLabelConfigStaysNull() {
        assertNull(Template.copyOf(null));
        Template plain = new Template("tpl_x", "Plain", new PageConfig(), new ArrayList<>());
        Template copy = Template.copyOf(plain);
        assertNotNull(copy);
        assertEquals("bill", copy.getMode());
        assertNull(copy.getLabelConfig());
    }
}
