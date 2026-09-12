package com.invoicestudio.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.oned.Code128Writer;
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

public class BarcodeService {

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
        String clean = (payload != null ? payload : "INV-0001").replaceAll("[^\\x20-\\x7e]", "").trim();
        if (clean.isBlank()) clean = "INV";
        try {
            Code128Writer writer = new Code128Writer();
            int barHeight = showText ? Math.max(10, height - 16) : height;
            BitMatrix bitMatrix = writer.encode(clean, BarcodeFormat.CODE_128, width, barHeight);
            BufferedImage barImg = MatrixToImageWriter.toBufferedImage(bitMatrix);

            if (!showText) return barImg;

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
        String clean = (payload != null ? payload : "INV-0001").replaceAll("[^\\x20-\\x7e]", "").trim();
        if (clean.isBlank()) clean = "INV";
        String key = width + "x" + height + ":" + showText + ":" + clean;
        synchronized (BARCODE_FX_CACHE) {
            Image cached = BARCODE_FX_CACHE.get(key);
            if (cached != null) return cached;
        }
        BufferedImage bi = generateBarcodeBufferedImage(payload, width, height, showText);
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
