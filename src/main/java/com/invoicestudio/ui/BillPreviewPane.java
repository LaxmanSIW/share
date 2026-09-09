package com.invoicestudio.ui;

import com.invoicestudio.model.*;
import com.invoicestudio.service.BarcodeService;
import com.invoicestudio.service.RenderContext;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class BillPreviewPane extends StackPane {

    private static final double MM_PX = 3.7795275591; // ~96 DPI screen pixels per mm

    private double zoom = 1.0;
    private Template currentTemplate;
    private Bill currentBill;
    private Settings currentSettings;
    private int currentCopyIndex = 0;
    private int currentPageCount = 1;

    private final Pane pagePane = new Pane();
    private final Group scaleGroup = new Group(pagePane);

    public BillPreviewPane() {
        setAlignment(Pos.TOP_CENTER);
        setPadding(new Insets(16));
        getStyleClass().add("preview-surface");

        pagePane.getStyleClass().add("preview-paper");
        getChildren().add(scaleGroup);
    }

    public void setZoom(double zoom) {
        this.zoom = Math.max(0.2, Math.min(3.0, zoom));
        scaleGroup.setScaleX(this.zoom);
        scaleGroup.setScaleY(this.zoom);
        if (currentTemplate != null) {
            updatePrefSize();
        }
    }

    public double getZoom() {
        return zoom;
    }

    public void render(Template template, Bill bill, Settings settings) {
        render(template, bill, settings, 0, 1);
    }

    public void render(Template template, Bill bill, Settings settings, int copyIndex, int pageCount) {
        this.currentTemplate = template != null ? template : PresetTemplates.buildClassic();
        this.currentBill = bill;
        this.currentSettings = settings != null ? settings : new Settings();
        this.currentCopyIndex = copyIndex;
        this.currentPageCount = Math.max(1, pageCount);

        rebuild();
    }

    private void rebuild() {
        pagePane.getChildren().clear();

        if (currentTemplate == null) return;

        double widthMm = currentTemplate.getPage() != null ? currentTemplate.getPage().getWidth() : 210.0;
        double heightMm = currentTemplate.getPage() != null ? currentTemplate.getPage().getHeight() : 297.0;

        boolean isRoll = currentTemplate.getPage() != null && currentTemplate.getPage().isAutoHeight();
        int itemCount = currentBill != null && currentBill.getItems() != null ? currentBill.getItems().size() : 1;

        if (isRoll) {
            heightMm = calculateEffectiveHeight(currentTemplate, itemCount);
        }

        double pageWPx = widthMm * MM_PX;
        double pageHPx = heightMm * MM_PX;

        pagePane.setPrefSize(pageWPx, pageHPx);
        pagePane.setMinSize(pageWPx, pageHPx);
        pagePane.setMaxSize(pageWPx, pageHPx);

        updatePrefSize();

        RenderContext ctx = new RenderContext(currentBill, currentSettings, currentCopyIndex, 1, currentPageCount);

        List<TemplateElement> elements = new ArrayList<>(currentTemplate.getElements());
        elements.sort((a, b) -> Integer.compare(a.getZIndex(), b.getZIndex()));

        double offXMm = currentSettings != null ? currentSettings.getPrintOffsetX() : 0;
        double offYMm = currentSettings != null ? currentSettings.getPrintOffsetY() : 0;

        double tableY = 0;
        double shift = 0;
        if (isRoll) {
            for (TemplateElement el : elements) {
                if (el.getType() == ElementType.TABLE) {
                    tableY = el.getY();
                    double rh = el.getRowHeight() > 0 ? el.getRowHeight() : 7.0;
                    shift = Math.max(0, (itemCount - 1) * rh);
                    break;
                }
            }
        }

        for (TemplateElement el : elements) {
            if (el.isHidden()) continue;
            if (el.isHideWhenBlank() && ctx.isTextBlank(el)) continue;

            double elY = el.getY();
            if (isRoll && shift > 0 && el.getType() != ElementType.TABLE && elY > tableY + 2) {
                elY += shift;
            }

            double x = (el.getX() + offXMm) * MM_PX;
            double y = (elY + offYMm) * MM_PX;
            double w = el.getW() * MM_PX;
            double h = el.getH() * MM_PX;

            if (el.getType() == ElementType.TABLE && isRoll) {
                double rh = el.getRowHeight() > 0 ? el.getRowHeight() : 7.0;
                h = (7.0 + (Math.max(1, itemCount) * rh)) * MM_PX;
            }

            Node node = createVisualElementNode(el, ctx, w, h);
            if (node != null) {
                node.setLayoutX(x);
                node.setLayoutY(y);
                if (el.getRotation() != 0) {
                    node.setRotate(el.getRotation());
                }
                pagePane.getChildren().add(node);
            }
        }

        // Status Watermark Stamp
        if (currentBill != null && (currentBill.getStatus() == BillStatus.PAID || currentBill.getStatus() == BillStatus.CANCELLED)) {
            Node stampNode = createStatusStampNode(currentBill.getStatus(), pageWPx, pageHPx);
            if (stampNode != null) {
                pagePane.getChildren().add(stampNode);
            }
        }
    }

    private void updatePrefSize() {
        if (currentTemplate == null) return;
        double widthMm = currentTemplate.getPage() != null ? currentTemplate.getPage().getWidth() : 210.0;
        double heightMm = currentTemplate.getPage() != null ? currentTemplate.getPage().getHeight() : 297.0;

        boolean isRoll = currentTemplate.getPage() != null && currentTemplate.getPage().isAutoHeight();
        if (isRoll) {
            int itemCount = currentBill != null && currentBill.getItems() != null ? currentBill.getItems().size() : 1;
            heightMm = calculateEffectiveHeight(currentTemplate, itemCount);
        }

        double finalW = (widthMm * MM_PX * zoom) + 32;
        double finalH = (heightMm * MM_PX * zoom) + 32;

        setPrefSize(finalW, finalH);
        setMinSize(finalW, finalH);
    }

    private double calculateEffectiveHeight(Template template, int itemCount) {
        double maxBottom = 0;
        double tableY = 0;
        double rowHeight = 7.0;
        boolean hasTable = false;

        for (TemplateElement el : template.getElements()) {
            if (el.getType() == ElementType.TABLE) {
                hasTable = true;
                tableY = el.getY();
                rowHeight = el.getRowHeight() > 0 ? el.getRowHeight() : 7.0;
                double h = 7.0 + (Math.max(1, itemCount) * rowHeight);
                maxBottom = Math.max(maxBottom, tableY + h);
            } else {
                maxBottom = Math.max(maxBottom, el.getY() + el.getH());
            }
        }
        if (hasTable) {
            double shift = Math.max(0, (itemCount - 1) * rowHeight);
            for (TemplateElement el : template.getElements()) {
                if (el.getType() != ElementType.TABLE && el.getY() > tableY + 2) {
                    maxBottom = Math.max(maxBottom, el.getY() + shift + el.getH());
                }
            }
        }
        return Math.max(80.0, maxBottom + 12.0);
    }

    private Node createVisualElementNode(TemplateElement el, RenderContext ctx, double w, double h) {
        switch (el.getType()) {
            case RECT -> {
                Rectangle r = new Rectangle(w, h);
                if (el.getBg() != null && !el.getBg().isBlank() && !"transparent".equalsIgnoreCase(el.getBg())) {
                    r.setFill(Color.web(el.getBg()));
                } else {
                    r.setFill(Color.TRANSPARENT);
                }
                if (el.getBorderWidth() > 0 && el.getBorderColor() != null) {
                    r.setStroke(Color.web(el.getBorderColor()));
                    r.setStrokeWidth(el.getBorderWidth() * MM_PX);
                }
                if (el.getBorderRadius() > 0) {
                    r.setArcWidth(el.getBorderRadius() * MM_PX * 2);
                    r.setArcHeight(el.getBorderRadius() * MM_PX * 2);
                }
                return r;
            }
            case LINE -> {
                Line l = new Line();
                if ("v".equalsIgnoreCase(el.getDirection())) {
                    l.setStartX(w / 2.0); l.setStartY(0);
                    l.setEndX(w / 2.0); l.setEndY(h);
                } else {
                    l.setStartX(0); l.setStartY(h / 2.0);
                    l.setEndX(w); l.setEndY(h / 2.0);
                }
                l.setStroke(Color.web(el.getBorderColor() != null ? el.getBorderColor() : "#1a1a1a"));
                l.setStrokeWidth(Math.max(1, (el.getBorderWidth() > 0 ? el.getBorderWidth() : 0.4) * MM_PX));
                return l;
            }
            case TEXT, PAGENO -> {
                String resolved = ctx.resolveText(el.getText());
                if (el.isUppercase()) resolved = resolved.toUpperCase();

                Label lbl = new Label(resolved);
                lbl.setPrefSize(w, h);
                lbl.setMinSize(w, h);
                lbl.setWrapText(true);

                String colorHex = el.getColor() != null && !el.getColor().isBlank() ? el.getColor() : "#1a1a1a";
                String bgStyle = "";
                if (el.getBg() != null && !el.getBg().isBlank() && !"transparent".equalsIgnoreCase(el.getBg())) {
                    bgStyle = "-fx-background-color: " + el.getBg() + ";";
                }
                String borderStyle = "";
                if (el.getBorderWidth() > 0 && el.getBorderColor() != null) {
                    borderStyle = "-fx-border-color: " + el.getBorderColor() + "; -fx-border-width: " + (el.getBorderWidth() * MM_PX) + ";";
                }
                if (el.getBorderRadius() > 0) {
                    borderStyle += "-fx-background-radius: " + (el.getBorderRadius() * MM_PX) + "; -fx-border-radius: " + (el.getBorderRadius() * MM_PX) + ";";
                }

                String fw = el.getFontWeight() >= 700 ? "bold" : "normal";
                String fs = el.isItalic() ? "italic" : "normal";
                String family = el.getFontFamily() != null ? el.getFontFamily() : "Segoe UI";

                lbl.setStyle("-fx-text-fill: " + colorHex + "; -fx-fill: " + colorHex +
                        "; -fx-font-size: " + (el.getFontSize() * 1.3) + "px; -fx-font-family: '" + family +
                        "'; -fx-font-weight: " + fw + "; -fx-font-style: " + fs + "; " + bgStyle + " " + borderStyle);

                Pos alignment = Pos.TOP_LEFT;
                if ("center".equalsIgnoreCase(el.getAlign())) alignment = Pos.TOP_CENTER;
                else if ("right".equalsIgnoreCase(el.getAlign())) alignment = Pos.TOP_RIGHT;

                if ("middle".equalsIgnoreCase(el.getVAlign())) {
                    if ("center".equalsIgnoreCase(el.getAlign())) alignment = Pos.CENTER;
                    else if ("right".equalsIgnoreCase(el.getAlign())) alignment = Pos.CENTER_RIGHT;
                    else alignment = Pos.CENTER_LEFT;
                } else if ("bottom".equalsIgnoreCase(el.getVAlign())) {
                    if ("center".equalsIgnoreCase(el.getAlign())) alignment = Pos.BOTTOM_CENTER;
                    else if ("right".equalsIgnoreCase(el.getAlign())) alignment = Pos.BOTTOM_RIGHT;
                    else alignment = Pos.BOTTOM_LEFT;
                }
                lbl.setAlignment(alignment);
                return lbl;
            }
            case IMAGE -> {
                ImageView iv = new ImageView();
                iv.setFitWidth(w);
                iv.setFitHeight(h);
                iv.setPreserveRatio(!"fill".equalsIgnoreCase(el.getObjectFit()));
                iv.setSmooth(true);

                Image img = null;
                if (el.isUseBusinessLogo()) {
                    String logo = currentSettings != null && currentSettings.getBusiness() != null ? currentSettings.getBusiness().getLogo() : null;
                    if (logo != null && !logo.isBlank()) {
                        img = decodeFxImage(logo);
                    }
                    if (img == null) {
                        try {
                            img = new Image(getClass().getResourceAsStream("/icons/Invoicewhitebackground.png"));
                        } catch (Exception ignored) {}
                    }
                } else if (el.getSrc() != null && !el.getSrc().isBlank()) {
                    img = decodeFxImage(el.getSrc());
                }

                if (img != null) {
                    iv.setImage(img);
                    StackPane sp = new StackPane(iv);
                    sp.setPrefSize(w, h);
                    sp.setAlignment(Pos.CENTER);
                    return sp;
                }
                return null;
            }
            case QRCODE -> {
                String payload = ctx.getQrPayload(el);
                int size = (int) Math.min(w, h);
                Image qrImg = BarcodeService.generateQrFxImage(payload, Math.max(20, size * 2));
                if (qrImg != null) {
                    ImageView iv = new ImageView(qrImg);
                    iv.setFitWidth(size);
                    iv.setFitHeight(size);
                    iv.setPreserveRatio(true);
                    iv.setSmooth(true);
                    StackPane sp = new StackPane(iv);
                    sp.setPrefSize(w, h);
                    sp.setAlignment(Pos.CENTER);
                    return sp;
                }
                return null;
            }
            case BARCODE -> {
                String payload = ctx.getBarcodePayload(el);
                Image barImg = BarcodeService.generateBarcodeFxImage(payload, (int) (w * 2), (int) (h * 2), el.isBarcodeShowText());
                if (barImg != null) {
                    ImageView iv = new ImageView(barImg);
                    iv.setFitWidth(w);
                    iv.setFitHeight(h);
                    iv.setPreserveRatio(true);
                    iv.setSmooth(true);
                    StackPane sp = new StackPane(iv);
                    sp.setPrefSize(w, h);
                    sp.setAlignment(Pos.CENTER);
                    return sp;
                }
                return null;
            }
            case TABLE -> {
                return createTableNode(el, w, h);
            }
        }
        return null;
    }

    private Node createTableNode(TemplateElement el, double w, double h) {
        // Shared table skin values (kept identical to designer canvas + PdfExportService)
        String bc = el.getTableBorderColor();
        double bwPx = Math.max(0.5, el.getTableBorderWidth() > 0 ? el.getTableBorderWidth() * MM_PX : 1.0);
        String bw = String.format(java.util.Locale.US, "%.2f", bwPx);
        String bStyle = el.getBorderStyle(); // grid, rows, outline, none
        boolean drawOuter = !"none".equals(bStyle);
        boolean innerLines = "grid".equals(bStyle) || "rows".equals(bStyle);

        VBox box = new VBox(0);
        box.setPrefSize(w, h);
        if (drawOuter) {
            box.setStyle("-fx-background-color: #ffffff; -fx-border-color: "
                    + (el.isBorderTop() ? bc : "transparent") + " "
                    + (el.isBorderRight() ? bc : "transparent") + " "
                    + (el.isBorderBottom() ? bc : "transparent") + " "
                    + (el.isBorderLeft() ? bc : "transparent") + ";"
                    + " -fx-border-width: " + bw + ";");
        } else {
            box.setStyle("-fx-background-color: #ffffff;");
        }

        List<TableColumn> cols = el.getColumns();
        if (cols == null || cols.isEmpty()) cols = PresetTemplates.defaultItemColumns();

        HBox hRow = new HBox(0);
        double rowHeightPx = (el.getRowHeight() > 0 ? el.getRowHeight() : 7.0) * MM_PX;
        double headerHeightPx = Math.max(22, rowHeightPx);
        hRow.setPrefHeight(headerHeightPx);
        String fontPx = String.format(java.util.Locale.US, "%.1f", 10.0 * el.tableFontScale());

        String hBg = el.getHeaderBg() != null && !el.getHeaderBg().isBlank() ? el.getHeaderBg() : "#efe9db";
        String hCol = el.getHeaderColor() != null && !el.getHeaderColor().isBlank() ? el.getHeaderColor() : "#1a1a1a";
        hRow.setStyle("-fx-background-color: " + hBg + ";"
                + (innerLines ? " -fx-border-color: transparent transparent " + bc + " transparent; -fx-border-width: 0 0 " + bw + " 0;" : ""));

        for (int ci = 0; ci < cols.size(); ci++) {
            TableColumn c = cols.get(ci);
            double cW = (c.getWidth() / 100.0) * w;
            boolean vSep = "grid".equals(bStyle) && ci < cols.size() - 1;
            Label lbl = new Label(c.getLabel());
            lbl.setPrefWidth(cW);
            lbl.setPrefHeight(headerHeightPx);
            lbl.setStyle("-fx-font-weight: bold; -fx-font-size: " + fontPx + "px; -fx-text-fill: " + hCol + "; -fx-padding: 0 4;"
                    + (vSep ? " -fx-border-color: transparent " + bc + " transparent transparent; -fx-border-width: 0 " + bw + " 0 0;" : ""));
            lbl.setAlignment("right".equalsIgnoreCase(c.getAlign()) ? Pos.CENTER_RIGHT : ("center".equalsIgnoreCase(c.getAlign()) ? Pos.CENTER : Pos.CENTER_LEFT));
            hRow.getChildren().add(lbl);
        }
        box.getChildren().add(hRow);

        List<BillItem> items = currentBill != null && currentBill.getItems() != null ? currentBill.getItems() : new ArrayList<>();
        if (items.isEmpty()) {
            BillItem sample = new BillItem();
            sample.setDesc("Sample Item");
            sample.setQty(1);
            sample.setRate(100);
            sample.setGst(18);
            items = List.of(sample);
        }

        String cur = currentSettings != null ? currentSettings.getCurrency() : "₹";
        String rowBgCol = el.getRowBg();
        String rowTextCol = el.getRowColor();
        String zebraCol = el.getZebraColor();

        for (int i = 0; i < items.size(); i++) {
            BillItem item = items.get(i);
            HBox row = new HBox(0);
            row.setPrefHeight(rowHeightPx);
            String rBg = el.isShowZebra() && (i % 2 == 1) ? zebraCol : rowBgCol;
            row.setStyle("-fx-background-color: " + rBg + ";"
                    + (innerLines ? " -fx-border-color: transparent transparent " + bc + " transparent; -fx-border-width: 0 0 " + bw + " 0;" : ""));

            for (int ci = 0; ci < cols.size(); ci++) {
                TableColumn c = cols.get(ci);
                double cW = (c.getWidth() / 100.0) * w;
                boolean vSep = "grid".equals(bStyle) && ci < cols.size() - 1;
                String val = getItemColumnValue(c.getKey(), item, i + 1, cur);
                Label lbl = new Label(val);
                lbl.setPrefWidth(cW);
                lbl.setPrefHeight(rowHeightPx);
                lbl.setStyle("-fx-font-size: " + fontPx + "px; -fx-text-fill: " + rowTextCol + "; -fx-padding: 0 4;"
                        + (vSep ? " -fx-border-color: transparent " + bc + " transparent transparent; -fx-border-width: 0 " + bw + " 0 0;" : ""));
                lbl.setAlignment("right".equalsIgnoreCase(c.getAlign()) ? Pos.CENTER_RIGHT : ("center".equalsIgnoreCase(c.getAlign()) ? Pos.CENTER : Pos.CENTER_LEFT));
                row.getChildren().add(lbl);
            }
            box.getChildren().add(row);
        }

        return box;
    }

    private String getItemColumnValue(String key, BillItem item, int index, String cur) {
        if (key == null) return "";
        double qty = item.getQty();
        double rate = item.getRate();
        double disc = item.getDiscPct();
        double gst = item.getGst();
        double taxable = (qty * rate) * (1.0 - disc / 100.0);
        double total = taxable * (1.0 + gst / 100.0);

        return switch (key.toLowerCase().trim()) {
            case "sr", "index", "#", "s_no", "sno" -> String.valueOf(index);
            case "desc", "name", "description", "item_name" -> item.getDesc() != null ? item.getDesc() : "";
            case "hsn", "sac", "hsn_sac" -> item.getHsn() != null ? item.getHsn() : "";
            case "qty", "quantity" -> String.format("%.2f", qty);
            case "unit" -> item.getUnit() != null ? item.getUnit() : "PCS";
            case "rate", "price", "unit_price" -> String.format("%.2f", rate);
            case "disc", "discount", "disc_pct" -> disc > 0 ? String.format("%.1f%%", disc) : "0%";
            case "taxable", "taxable_value" -> String.format("%.2f", taxable);
            case "gst", "gst_rate", "tax" -> String.format("%.0f%%", gst);
            case "amount", "total", "total_amount" -> String.format("%.2f", total);
            default -> item.getCustomField(key);
        };
    }

    private Node createStatusStampNode(BillStatus status, double pageWPx, double pageHPx) {
        boolean isPaid = (status == BillStatus.PAID);
        String label = isPaid ? "PAID" : "CANCELLED";
        String colorHex = isPaid ? "#0E7A4F" : "#C02626";

        StackPane stamp = new StackPane();
        stamp.setMouseTransparent(true);

        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-family: 'Arial Black', Arial, sans-serif; -fx-font-size: " + (pageWPx * 0.11) + "px; " +
                "-fx-font-weight: 900; -fx-text-fill: " + colorHex + "; -fx-border-color: " + colorHex + "; " +
                "-fx-border-width: " + (pageWPx * 0.015) + "px; -fx-border-radius: " + (pageWPx * 0.02) + "px; " +
                "-fx-padding: " + (pageWPx * 0.01) + "px " + (pageWPx * 0.04) + "px; -fx-opacity: 0.22;");

        stamp.getChildren().add(lbl);
        stamp.setLayoutX(pageWPx / 2.0 - (pageWPx * 0.25));
        stamp.setLayoutY(pageHPx * 0.44 - (pageHPx * 0.08));
        stamp.setRotate(-22);

        return stamp;
    }

    private Image decodeFxImage(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            String b64 = raw;
            if (b64.contains(",")) {
                b64 = b64.substring(b64.indexOf(",") + 1);
            }
            byte[] bytes = Base64.getDecoder().decode(b64.trim());
            return new Image(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null;
        }
    }
}
