package com.invoicestudio.service;

import com.invoicestudio.model.BillStatus;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

/**
 * Low-level text drawing and status-stamp rendering for PDF export
 * (skill rule 5.2 role 5: pure renderers). Extracted verbatim from
 * {@link PdfExportService}; behavior unchanged.
 */
final class PdfTextDraw {

    private PdfTextDraw() {}

    /** DPI shared with {@link PdfExportService} (kept identical by contract). */
    static final double DPI = 300.0;

    static void drawCellText(Graphics2D g2, String text, double x, double y, double w, double h, String align) {
        if (text == null || text.isBlank()) return;
        FontMetrics fm = g2.getFontMetrics();
        int strW = fm.stringWidth(text);
        int textY = (int) Math.round(y + ((h - fm.getHeight()) / 2.0) + fm.getAscent());

        int textX = (int) Math.round(x);
        if ("right".equalsIgnoreCase(align)) {
            textX = (int) Math.round(x + w - strW);
        } else if ("center".equalsIgnoreCase(align)) {
            textX = (int) Math.round(x + (w - strW) / 2.0);
        }
        g2.drawString(text, textX, textY);
    }

    static void drawWrappedText(Graphics2D g2, String text, double x, double y, double w, double h,
                                String align, String vAlign, double lineHeightMult) {
        drawWrappedText(g2, text, x, y, w, h, align, vAlign, lineHeightMult, 0, 0);
    }

    static void drawWrappedText(Graphics2D g2, String text, double x, double y, double w, double h,
                                String align, String vAlign, double lineHeightMult, double lineSpacingPt, double wordSpacingPt) {
        if (text == null || text.isEmpty()) return;
        FontMetrics fm = g2.getFontMetrics();
        double multSpacing = fm.getHeight() * (lineHeightMult > 0 ? lineHeightMult : 1.25);
        double ptSpacingPx = lineSpacingPt * (DPI / 72.0);
        double lineSpacing = Math.max(fm.getHeight() * 0.75, multSpacing + ptSpacingPx);
        double wordSpacingPx = wordSpacingPt > 0 ? wordSpacingPt * (DPI / 72.0) : 0;

        String[] rawLines = text.split("\n");
        List<String> lines = new ArrayList<>();

        for (String raw : rawLines) {
            String[] words = raw.split(" ");
            StringBuilder sb = new StringBuilder();
            for (String word : words) {
                if (sb.length() == 0) {
                    sb.append(word);
                } else if (fm.stringWidth(sb + " " + word) + (wordSpacingPx > 0 ? wordSpacingPx : 0) <= w) {
                    sb.append(" ").append(word);
                } else {
                    lines.add(sb.toString());
                    sb = new StringBuilder(word);
                }
            }
            if (sb.length() > 0) lines.add(sb.toString());
        }

        double totalTextHeight = lines.size() * lineSpacing;
        double startY = y + fm.getAscent();

        if ("middle".equalsIgnoreCase(vAlign)) {
            startY = y + Math.max(0, (h - totalTextHeight) / 2.0) + fm.getAscent();
        } else if ("bottom".equalsIgnoreCase(vAlign)) {
            startY = y + Math.max(0, h - totalTextHeight) + fm.getAscent();
        }

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (wordSpacingPx <= 0) {
                int strW = fm.stringWidth(line);
                double lineX = x;
                if ("right".equalsIgnoreCase(align)) {
                    lineX = x + w - strW;
                } else if ("center".equalsIgnoreCase(align)) {
                    lineX = x + (w - strW) / 2.0;
                }
                g2.drawString(line, (int) Math.round(lineX), (int) Math.round(startY + (i * lineSpacing)));
            } else {
                String[] words = line.split(" ");
                int baseW = fm.stringWidth(line);
                double totalW = baseW + ((words.length - 1) * wordSpacingPx);
                double curX = x;
                if ("right".equalsIgnoreCase(align)) {
                    curX = x + w - totalW;
                } else if ("center".equalsIgnoreCase(align)) {
                    curX = x + (w - totalW) / 2.0;
                }
                double currentY = startY + (i * lineSpacing);
                for (int wi = 0; wi < words.length; wi++) {
                    g2.drawString(words[wi], (int) Math.round(curX), (int) Math.round(currentY));
                    curX += fm.stringWidth(words[wi] + " ") + wordSpacingPx;
                }
            }
        }
    }

    /** Big translucent PAID / CANCELLED stamp across the rendered page image. */
    static void renderStatusStamp(Graphics2D g2, BillStatus status, int imgW, int imgH) {
        boolean isPaid = (status == BillStatus.PAID);
        String label = isPaid ? "PAID" : "CANCELLED";
        Color stampColor = isPaid ? new Color(14, 122, 79, 70) : new Color(192, 38, 38, 70);

        AffineTransform orig = g2.getTransform();
        g2.translate(imgW / 2.0, imgH * 0.44);
        g2.rotate(Math.toRadians(-22));

        Font font = new Font("Arial", Font.BOLD, (int) (imgW * 0.09));
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();

        int padX = (int) (imgW * 0.04);
        int padY = (int) (imgW * 0.015);
        int strW = fm.stringWidth(label);
        int boxW = strW + padX * 2;
        int boxH = fm.getHeight() + padY * 2;

        g2.setColor(stampColor);
        g2.setStroke(new BasicStroke((float) (imgW * 0.008)));
        g2.drawRoundRect(-boxW / 2, -boxH / 2, boxW, boxH, (int) (imgW * 0.015), (int) (imgW * 0.015));

        g2.drawString(label, -strW / 2, fm.getAscent() - boxH / 2 + padY);

        g2.setTransform(orig);
    }
}
