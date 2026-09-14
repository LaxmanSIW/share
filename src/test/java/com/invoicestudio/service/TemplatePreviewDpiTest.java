package com.invoicestudio.service;

import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;
import com.invoicestudio.model.TemplateElement;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import javax.imageio.ImageIO;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Task 16 regression test for the preview DPI scaling fix: the shared
 * renderer draws at a 300-dpi reference scale, so previews at ANY requested
 * dpi must scale the graphics context to the canvas — previously a 150-dpi
 * A4 canvas was cropped at ~60% because elements were drawn at 300 dpi.
 */
class TemplatePreviewDpiTest {

    private static Template bottomMarkerTemplate() {
        Template t = new Template();
        com.invoicestudio.model.PageConfig page = new com.invoicestudio.model.PageConfig();
        page.setSizeName(com.invoicestudio.model.PageSizeName.A4);
        t.setPage(page);

        // Solid band at the very bottom of the page: mm y 280..290
        TemplateElement band = new TemplateElement();
        band.setType(com.invoicestudio.model.ElementType.RECT);
        band.setName("Bottom Marker");
        band.setX(10); band.setY(280); band.setW(190); band.setH(10);
        band.setBg("#101010");
        t.getElements().add(band);
        return t;
    }

    private static BufferedImage render(Template t, double dpi) throws Exception {
        byte[] png = TemplatePreviewService.renderPng(t, new Settings(), dpi);
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    private static boolean isDark(BufferedImage img, int x, int y) {
        int rgb = img.getRGB(x, y);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        return r + g + b < 200;
    }

    @Test
    void testCanvasDimensionsMatchRequestedDpi() throws Exception {
        Template t = bottomMarkerTemplate();
        BufferedImage p150 = render(t, 150);
        assertEquals(Math.round(210.0 * 150 / 25.4), p150.getWidth(), "width must follow requested dpi");
        assertEquals(Math.round(297.0 * 150 / 25.4), p150.getHeight(), "height must follow requested dpi");
        BufferedImage p300 = render(t, 300);
        assertEquals(Math.round(210.0 * 300 / 25.4), p300.getWidth(), "300 dpi stays the reference size");
    }

    @Test
    void test150DpiPreviewIsNotCropped() throws Exception {
        Template t = bottomMarkerTemplate();
        BufferedImage img = render(t, 150);

        // Probe the CENTER of the bottom band (y ≈ 285 mm) — before the fix
        // this region was blank white because the 300-dpi drawing overflowed
        // the 150-dpi canvas.
        int probeY = (int) Math.round(285.0 * 150 / 25.4);
        assertTrue(isDark(img, img.getWidth() / 2, probeY),
                "bottom-of-page content must be visible at 150 dpi (was cropped before the DPI fix)");
        // Band interior, not just the center line
        assertTrue(isDark(img, img.getWidth() / 4, (int) Math.round(283.0 * 150 / 25.4)),
                "band body must render at 150 dpi");
    }

    @Test
    void test300DpiPreviewStillComplete() throws Exception {
        Template t = bottomMarkerTemplate();
        BufferedImage img = render(t, 300);
        assertTrue(isDark(img, img.getWidth() / 2, (int) Math.round(285.0 * 300 / 25.4)),
                "reference dpi must remain complete");
    }

    @Test
    void testAllDpisRenderSameContentCoverage() throws Exception {
        Template t = bottomMarkerTemplate();
        // The band's top edge (y=280mm) must appear at the same FRACTION of
        // page height at any dpi — proving uniform scaling, not cropping.
        for (double dpi : new double[]{72, 100, 150, 200, 300}) {
            BufferedImage img = render(t, dpi);
            int probe = (int) Math.round(284.0 * dpi / 25.4);
            assertTrue(isDark(img, img.getWidth() / 2, probe),
                    "band must render at " + dpi + " dpi too");
        }
    }
}
