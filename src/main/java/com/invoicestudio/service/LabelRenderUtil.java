package com.invoicestudio.service;

import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;
import com.invoicestudio.model.TemplateElement;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Renders ONE label design (Barcode Mode canvas) into a JavaFX node at
 * 96-DPI screen pixels, with a given variable value set.
 * Shared by the strip preview, the Bulk Print popup and the real print path,
 * so all three show pixel-identical output.
 */
public final class LabelRenderUtil {

    public static final double MM_PX = 3.7795275591; // 96 DPI

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private LabelRenderUtil() {}

    /**
     * Builds the label node for the given values.
     *
     * @param template  the label-mode template (elements positioned in mm on the label cell)
     * @param values    variable values used for {{placeholders}} (may be null)
     * @param widthMm   design width of the label cell
     * @param heightMm  design height of the label cell
     * @param settings  live settings for business-name/logo variables (null-safe)
     */
    public static Pane renderLabelNode(Template template, Map<String, String> values,
                                       double widthMm, double heightMm, Settings settings) {
        double w = Math.max(1, widthMm) * MM_PX;
        double h = Math.max(1, heightMm) * MM_PX;

        Pane cell = new Pane();
        cell.setPrefSize(w, h);
        cell.setMinSize(w, h);
        cell.setMaxSize(w, h);
        cell.setStyle("-fx-background-color: white;");

        if (template == null || template.getElements() == null) return cell;

        RenderContext ctx = new RenderContext(null, settings != null ? settings : new Settings(), 0, 1, 1);
        if (values != null && !values.isEmpty()) {
            ctx.getValues().putAll(values);
        }
        // Label conveniences — handy for "Packed on" / batch labels.
        LocalDateTime now = LocalDateTime.now();
        ctx.getValues().putIfAbsent("label_date", now.format(DATE_FMT));
        ctx.getValues().putIfAbsent("label_time", now.format(TIME_FMT));
        ctx.getValues().putIfAbsent("invoice_date", now.format(DATE_FMT));

        List<TemplateElement> els = template.getElements();
        // Paint in list order (list order == z order in the designer).
        for (int i = 0; i < els.size(); i++) {
            TemplateElement el = els.get(i);
            if (el == null || el.isHidden()) continue;
            Node node = DesignObjectRenderer.render(el, ctx,
                    el.getW() * MM_PX, el.getH() * MM_PX);
            if (node == null) continue;
            node.setLayoutX(el.getX() * MM_PX);
            node.setLayoutY(el.getY() * MM_PX);
            node.setRotate(el.getRotation());
            node.setMouseTransparent(true);
            cell.getChildren().add(node);
        }
        return cell;
    }
}
