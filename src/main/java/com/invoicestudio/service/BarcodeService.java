package com.invoicestudio.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
import com.google.zxing.oned.Code39Writer;
import com.google.zxing.oned.EAN13Writer;
import com.google.zxing.oned.EAN8Writer;
import com.google.zxing.oned.ITFWriter;
import com.google.zxing.oned.UPCAWriter;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ZXing-powered 1D/2D code generator with an LRU cache.
 * <p>Symbologies offered in the template designer (Barcode element properties):
 * CODE_128 (legacy default), EAN_13, EAN_8, CODE_39, ITF, UPC_A and QR_CODE.
 * Any unrecognised / failing format safely falls back to Code 128 with the
 * payload sanitized, exactly like the pre-multi-format behaviour.</p>
 */
public class BarcodeService {

    /** Symbology list shown in the designer combo. */
    public static final String[] FORMATS = {
            "CODE_128", "EAN_13", "EAN_8", "CODE_39", "ITF", "UPC_A", "QR_CODE"
    };

    private static final int MAX_CACHE_SIZE = 150;

    private static final Map<String, Image> QR_FX_CACHE = new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    private static final Map<String, Image> BARCODE_FX_CACHE = new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };

    public static BufferedImage generateQrBufferedImage(String payload, int size) {
        if (payload == null || payload.isBlank()) payload = "InvoiceStudio";
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");

            BitMatrix bitMatrix = qrCodeWriter.encode(payload, BarcodeFormat.QR_CODE, size, size, hints);
            return MatrixToImageWriter.toBufferedImage(bitMatrix);
        } catch (Exception e) {
            e.printStackTrace();
            return createFallbackImage(size, size, "QR Error");
        }
    }

    public static Image generateQrFxImage(String payload, int size) {
        String key = size + ":" + (payload != null ? payload : "InvoiceStudio");
        synchronized (QR_FX_CACHE) {
            Image cached = QR_FX_CACHE.get(key);
            if (cached != null) return cached;
        }
        BufferedImage bi = generateQrBufferedImage(payload, size);
        Image img = SwingFXUtils.toFXImage(bi, null);
        synchronized (QR_FX_CACHE) {
            QR_FX_CACHE.put(key, img);
        }
        return img;
    }

    public static BufferedImage generateBarcodeBufferedImage(String payload, int width, int height, boolean showText) {
        return generateBarcodeBufferedImage(payload, width, height, showText, "CODE_128");
    }

    /**
     * Multi-symbology render. Numeric-only formats (EAN/ITF/UPC) validate the
     * payload and fall back to Code 128 when it does not fit the spec — a
     * mis-typed size code must never block a 500-label print run.
     */
    public static BufferedImage generateBarcodeBufferedImage(String payload, int width, int height,
                                                             boolean showText, String format) {
        String clean = (payload != null ? payload : "INV-0001").replaceAll("[^\\x20-\\x7e]", "").trim();
        if (clean.isBlank()) clean = "INV";
        String fmt = format != null ? format.trim().toUpperCase() : "CODE_128";
        try {
            BitMatrix bitMatrix;
            int barHeight = showText ? Math.max(10, height - 16) : height;
            switch (fmt) {
                case "EAN_13" -> {
                    String digits = clean.replaceAll("[^0-9]", "");
                    bitMatrix = digits.length() == 13
                            ? new EAN13Writer().encode(digits, BarcodeFormat.EAN_13, width, barHeight)
                            : new Code128Writer().encode(clean, BarcodeFormat.CODE_128, width, barHeight);
                }
                case "EAN_8" -> {
                    String digits = clean.replaceAll("[^0-9]", "");
                    bitMatrix = digits.length() == 8
                            ? new EAN8Writer().encode(digits, BarcodeFormat.EAN_8, width, barHeight)
                            : new Code128Writer().encode(clean, BarcodeFormat.CODE_128, width, barHeight);
                }
                case "UPC_A" -> {
                    String digits = clean.replaceAll("[^0-9]", "");
                    bitMatrix = digits.length() == 12
                            ? new UPCAWriter().encode(digits, BarcodeFormat.UPC_A, width, barHeight)
                            : new Code128Writer().encode(clean, BarcodeFormat.CODE_128, width, barHeight);
                }
                case "ITF" -> {
                    String digits = clean.replaceAll("[^0-9]", "");
                    if (digits.length() % 2 != 0) digits = digits + "0";
                    bitMatrix = digits.isEmpty()
                            ? new Code128Writer().encode(clean, BarcodeFormat.CODE_128, width, barHeight)
                            : new ITFWriter().encode(digits, BarcodeFormat.ITF, width, barHeight);
                }
                case "CODE_39" -> {
                    bitMatrix = new Code39Writer().encode(clean, BarcodeFormat.CODE_39, width, barHeight);
                }
                case "QR_CODE", "QRCODE", "QR" -> {
                    int side = Math.min(width, height);
                    bitMatrix = new QRCodeWriter().encode(clean, BarcodeFormat.QR_CODE, side, side,
                            Map.of(EncodeHintType.MARGIN, 1, EncodeHintType.CHARACTER_SET, "UTF-8"));
                }
                default -> {
                    bitMatrix = new Code128Writer().encode(clean, BarcodeFormat.CODE_128, width, barHeight);
                }
            }
            BufferedImage barImg = MatrixToImageWriter.toBufferedImage(bitMatrix);

            if ("QR_CODE".equals(fmt) || "QRCODE".equals(fmt) || "QR".equals(fmt) || !showText) {
                return barImg;
            }

            // Render text underneath
            BufferedImage composite = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = composite.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(barImg, 0, 0, null);

            g.setColor(Color.BLACK);
            g.setFont(new Font("Monospaced", Font.PLAIN, 11));
            FontMetrics fm = g.getFontMetrics();
            int strW = fm.stringWidth(clean);
            int textX = Math.max(0, (width - strW) / 2);
            g.drawString(clean, textX, height - 2);
            g.dispose();
            return composite;
        } catch (Exception e) {
            e.printStackTrace();
            return createFallbackImage(width, height, clean);
        }
    }

    public static Image generateBarcodeFxImage(String payload, int width, int height, boolean showText) {
        return generateBarcodeFxImage(payload, width, height, showText, "CODE_128");
    }

    public static Image generateBarcodeFxImage(String payload, int width, int height, boolean showText, String format) {
        String clean = (payload != null ? payload : "INV-0001").replaceAll("[^\\x20-\\x7e]", "").trim();
        if (clean.isBlank()) clean = "INV";
        String fmt = format != null ? format.trim().toUpperCase() : "CODE_128";
        String key = width + "x" + height + ":" + showText + ":" + fmt + ":" + clean;
        synchronized (BARCODE_FX_CACHE) {
            Image cached = BARCODE_FX_CACHE.get(key);
            if (cached != null) return cached;
        }
        BufferedImage bi = generateBarcodeBufferedImage(payload, width, height, showText, fmt);
        Image img = SwingFXUtils.toFXImage(bi, null);
        synchronized (BARCODE_FX_CACHE) {
            BARCODE_FX_CACHE.put(key, img);
        }
        return img;
    }

    public static String buildUpiPayload(String upiId, String merchantName, double amount, String invoiceNo) {
        if (upiId == null || upiId.isBlank()) return "";
        try {
            StringBuilder sb = new StringBuilder("upi://pay?");
            sb.append("pa=").append(URLEncoder.encode(upiId.trim(), StandardCharsets.UTF_8));
            if (merchantName != null && !merchantName.isBlank()) {
                sb.append("&pn=").append(URLEncoder.encode(merchantName.trim(), StandardCharsets.UTF_8));
            }
            if (amount > 0) {
                sb.append(String.format(java.util.Locale.US, "&am=%.2f", amount));
            }
            sb.append("&cu=INR");
            if (invoiceNo != null && !invoiceNo.isBlank()) {
                sb.append("&tn=").append(URLEncoder.encode(invoiceNo.trim(), StandardCharsets.UTF_8));
            }
            return sb.toString();
        } catch (Exception e) {
            return "upi://pay?pa=" + upiId;
        }
    }

    private static BufferedImage createFallbackImage(int w, int h, String text) {
        BufferedImage img = new BufferedImage(Math.max(w, 10), Math.max(h, 10), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.setColor(Color.LIGHT_GRAY);
        g.drawRect(0, 0, w - 1, h - 1);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g.drawString(text, 5, h / 2);
        g.dispose();
        return img;
    }
}
