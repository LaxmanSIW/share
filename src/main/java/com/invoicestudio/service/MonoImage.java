package com.invoicestudio.service;

/**
 * Pure grayscale → 1-bit conversion for TSPL BITMAP payloads (no JavaFX —
 * the snapshot that produces the gray buffer lives in
 * {@link TsplPrintService}; this class is the testable core).
 * <p>
 * Thermal 203-dpi heads look ragged when anti-aliased edges are thresholded
 * directly, so the pipeline snapshots at 2× the dot density and this class
 * box-downsamples 2×2 pixels into one dot before the black cut.
 */
public final class MonoImage {

    /** Default black cut on the 0–255 downsampled gray. Slightly above the
     *  mid-grey so hairlines and small text survive the thermal print. */
    public static final int DEFAULT_THRESHOLD = 150;

    private MonoImage() {}

    /**
     * Downsamples a 2× supersampled gray image into dot resolution.
     *
     * @param gray    row-major luminance 0–255, width×height pixels
     * @param pxW     supersampled width (even)
     * @param pxH     supersampled height (even)
     * @return row-major gray 0–255 at half resolution, w=pxW/2, h=pxH/2
     */
    public static int[] downsample2x(int[] gray, int pxW, int pxH) {
        int w = Math.max(1, pxW / 2), h = Math.max(1, pxH / 2);
        int[] out = new int[w * h];
        if (gray == null) return out;
        for (int y = 0; y < h; y++) {
            int y0 = y * 2, y1 = Math.min(y0 + 1, pxH - 1);
            for (int x = 0; x < w; x++) {
                int x0 = x * 2, x1 = Math.min(x0 + 1, pxW - 1);
                int sum = gray[y0 * pxW + x0] + gray[y0 * pxW + x1]
                        + gray[y1 * pxW + x0] + gray[y1 * pxW + x1];
                out[y * w + x] = sum / 4;
            }
        }
        return out;
    }

    /**
     * Luminance of one ARGB pixel (integer Rec. 601 — matches what the eye
     * expects to burn on a monochrome thermal head).
     */
    public static int luminance(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000;
    }

    /**
     * Thresholds dot gray values into the black grid consumed by
     * {@link TsplCommandBuilder#packBits}.
     *
     * @param dots       row-major gray 0–255, widthDots×heightDots
     * @param widthDots  bitmap width in dots
     * @param heightDots bitmap height in dots
     * @param threshold  0–255, ≥ burns black
     */
    public static boolean[] threshold(int[] dots, int widthDots, int heightDots, int threshold) {
        boolean[] black = new boolean[Math.max(0, widthDots) * Math.max(0, heightDots)];
        if (dots == null) return black;
        for (int i = 0; i < black.length && i < dots.length; i++) {
            black[i] = dots[i] <= threshold;
        }
        return black;
    }
}
