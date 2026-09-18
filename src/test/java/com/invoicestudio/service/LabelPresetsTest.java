package com.invoicestudio.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the TSC TA210 preset catalogue: hardware-envelope conformance of
 * every built-in preset, the exact display format contract, and the custom
 * dimension validator.
 */
class LabelPresetsTest {

    @Test
    void seventeenPresetsExist() {
        assertEquals(17, LabelPresets.TA210.size(), "preset count");
    }

    /** Every preset must sit inside the TA210's official media envelope. */
    @Test
    void allPresetsWithinHardwareEnvelope() {
        for (LabelPresets.Preset p : LabelPresets.TA210) {
            assertNull(LabelPresets.validate(p.w(), p.h()),
                    "preset " + p.name() + " violates the TA210 envelope");
            assertTrue(p.w() >= LabelPresets.MEDIA_WIDTH_MIN, p.name() + " width min");
            assertTrue(p.w() <= LabelPresets.MEDIA_WIDTH_MAX, p.name() + " width max");
            assertTrue(p.h() >= LabelPresets.LENGTH_MIN, p.name() + " length min");
            assertTrue(p.h() <= LabelPresets.LENGTH_MAX, p.name() + " length max");
            assertTrue(p.w() <= LabelPresets.PRINT_WIDTH_MAX, p.name() + " print head width");
        }
    }

    /** Display contract: [W×H]  |  L/R: xmm  |  Row Gap: ymm  |  Col Gap: zmm */
    @Test
    void specFormatMatchesContract() {
        LabelPresets.Preset p = LabelPresets.TA210.stream()
                .filter(x -> x.w() == 50 && x.h() == 25).findFirst().orElseThrow();
        assertEquals("[50×25]  |  L/R: 1mm  |  Row Gap: 1.5mm  |  Col Gap: 2mm", p.spec());

        // A preset without a column gap shows an em dash.
        LabelPresets.Preset max = LabelPresets.TA210.get(0);
        assertTrue(max.spec().endsWith("Col Gap: —"), "no col gap renders as —");
    }

    @Test
    void validatorRejectsOutOfBoundsDimensions() {
        assertNotNull(LabelPresets.validate(120, 50), "width over 118 must be rejected");
        assertNotNull(LabelPresets.validate(20, 50), "width under 25.4 must be rejected");
        assertNotNull(LabelPresets.validate(50, 5), "length under 10 must be rejected");
        assertNotNull(LabelPresets.validate(50, 3000), "length over 2794 must be rejected");
        assertNull(LabelPresets.validate(50, 25), "in-range custom size is fine");
    }

    /** Widest media (118) still can't exceed the 108 mm print head — the
     *  validator must explain the clip instead of the envelope. */
    @Test
    void validatorExplainsPrintHeadClip() {
        String err = LabelPresets.validate(118, 50);
        assertNotNull(err);
        assertTrue(err.contains("108"), "clip message mentions the print-head width: " + err);
    }
}
