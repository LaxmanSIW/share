package com.invoicestudio.ui;

import com.invoicestudio.model.LabelConfig;
import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;
import com.invoicestudio.service.LabelGeometryService;
import com.invoicestudio.service.LabelRenderUtil;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "How the strip looks" preview for Barcode Mode: renders the full liner
 * width with the configured columns and 4-5 rows of labels, including the
 * die-cut gaps and rounded corners. Each tile shows the label design with
 * sample / different variable values, so shops see a believable strip before
 * running a bulk job.
 */
public class LabelStripPreviewDialog extends Stage {

    private static final int PREVIEW_ROWS = 5;

    public LabelStripPreviewDialog(javafx.stage.Window owner, Template template,
                                   Settings settings, List<Map<String, String>> sampleValues) {
        initOwner(owner);
        initModality(Modality.APPLICATION_MODAL);
        setTitle("Strip Preview — " + template.getName());

        LabelConfig cfg = template.labelOrNew();
        cfg.sanitize();

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(14));
        root.getStyleClass().add("bg-app");

        // ── Header info ──
        VBox header = new VBox(4);
        Label title = new Label("Strip Preview (first " + PREVIEW_ROWS + " rows)");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        Label info = new Label(String.format(java.util.Locale.US,
                "Strip %.0f mm wide · %d across · label %.0f × %.0f mm · gaps %.1f / %.1f mm · corners r=%.1f mm · print orientation %s°%s",
                cfg.getStripWidth(), cfg.getColumns(),
                cfg.getLabelWidth(), cfg.getLabelHeight(),
                cfg.getGapX(), cfg.getGapY(), cfg.getCornerRadius(),
                cfg.getOrientation(),
                LabelGeometryService.fitsStrip(cfg) ? "" : "   ⚠ labels overflow the strip!"));
        info.getStyleClass().add("text-muted");
        info.setWrapText(true);
        header.getChildren().addAll(title, info);
        root.setTop(header);

        // ── The strip itself, on a dark "printer bed" ──
        double pageWmm = cfg.getStripWidth();
        double cellHmm = LabelGeometryService.physicalCellHeight(cfg);
        double rowPitch = cellHmm + cfg.getGapY();
        double stripHmm = rowPitch * PREVIEW_ROWS + cfg.getGapY();
        double pad = 12; // mm of dark border around the liner

        Pane strip = new Pane();
        double stripWpx = (pageWmm + pad * 2) * LabelRenderUtil.MM_PX;
        double stripHpx = (stripHmm + pad * 2) * LabelRenderUtil.MM_PX;
        strip.setPrefSize(stripWpx, stripHpx);
        strip.setMinSize(stripWpx, stripHpx);
        strip.setMaxSize(stripWpx, stripHpx);
        strip.setStyle("-fx-background-color: #2A3342; -fx-background-radius: 4;");

        // Liner sheet
        Rectangle liner = new Rectangle(pad * LabelRenderUtil.MM_PX, pad * LabelRenderUtil.MM_PX,
                pageWmm * LabelRenderUtil.MM_PX, stripHmm * LabelRenderUtil.MM_PX);
        liner.setFill(Color.web("#e9e5db"));
        liner.setArcWidth(3);
        liner.setArcHeight(3);
        strip.getChildren().add(liner);

        double[] offsets = LabelGeometryService.columnOffsets(cfg);
        double cellWmm = LabelGeometryService.physicalCellWidth(cfg);
        boolean rotate = !"0".equals(cfg.getOrientation());
        double angle;
        try { angle = Double.parseDouble(cfg.getOrientation()); } catch (Exception e) { angle = 0; }

        int tileIndex = 0;
        for (int row = 0; row < PREVIEW_ROWS; row++) {
            for (int col = 0; col < cfg.getColumns(); col++) {
                double xMm = pad + offsets[col];
                double yMm = pad + cfg.getGapY() + row * rowPitch;

                // Rounded die-cut outline
                Rectangle cut = new Rectangle(
                        xMm * LabelRenderUtil.MM_PX, yMm * LabelRenderUtil.MM_PX,
                        cellWmm * LabelRenderUtil.MM_PX, cellHmm * LabelRenderUtil.MM_PX);
                cut.setFill(Color.WHITE);
                cut.setStroke(Color.web("#b9b2a2"));
                cut.setStrokeWidth(0.8);
                double r = cfg.getCornerRadius() * LabelRenderUtil.MM_PX * 2;
                cut.setArcWidth(r);
                cut.setArcHeight(r);
                strip.getChildren().add(cut);

                // Label artwork with a per-slot sample value set. The design
                // node is always authored at cfg design dims; rotation spins
                // it into the physical die-cut cell.
                Map<String, String> values = sampleValues != null && !sampleValues.isEmpty()
                        ? sampleValues.get(tileIndex % sampleValues.size())
                        : new LinkedHashMap<>();
                Pane cell = LabelRenderUtil.renderLabelNode(template, values,
                        cfg.getLabelWidth(), cfg.getLabelHeight(), settings);
                if (rotate) {
                    double w = cfg.getLabelWidth() * LabelRenderUtil.MM_PX;
                    double h = cfg.getLabelHeight() * LabelRenderUtil.MM_PX;
                    cell.getTransforms().add(new javafx.scene.transform.Rotate(angle, w / 2.0, h / 2.0));
                }
                // Clip the artwork to the rounded die-cut
                Rectangle clip = new Rectangle(
                        cellWmm * LabelRenderUtil.MM_PX, cellHmm * LabelRenderUtil.MM_PX);
                clip.setArcWidth(r);
                clip.setArcHeight(r);
                cell.setClip(clip);

                double designWpx = cfg.getLabelWidth() * LabelRenderUtil.MM_PX;
                double designHpx = cfg.getLabelHeight() * LabelRenderUtil.MM_PX;
                cell.setLayoutX(xMm * LabelRenderUtil.MM_PX + (cellWmm * LabelRenderUtil.MM_PX - designWpx) / 2.0);
                cell.setLayoutY(yMm * LabelRenderUtil.MM_PX + (cellHmm * LabelRenderUtil.MM_PX - designHpx) / 2.0);
                strip.getChildren().add(cell);
                tileIndex++;
            }
        }

        // Scale to fit the dialog comfortably
        double fit = Math.min(1.0, Math.min(860.0 / stripWpx, 520.0 / stripHpx));
        Group scaled = new Group(strip);
        scaled.getTransforms().add(new javafx.scene.transform.Scale(fit, fit, 0, 0));

        ScrollPane scroll = new ScrollPane(scaled);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-pane");
        scroll.setPadding(new Insets(10, 0, 10, 0));
        root.setCenter(scroll);

        // ── Footer ──
        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_RIGHT);
        Label hint = new Label("Gaps between labels are die-cut waste — the printer's gap sensor advances by " +
                String.format(java.util.Locale.US, "%.1f mm per row.", LabelGeometryService.feedPitchMm(cfg)));
        hint.setFont(Font.font("Segoe UI", 11));
        hint.getStyleClass().add("text-muted");
        footer.getChildren().add(hint);
        root.setBottom(footer);

        Scene scene = new Scene(root, 900, 660);
        DialogHelper.styleScene(scene);
        setScene(scene);
    }
}
