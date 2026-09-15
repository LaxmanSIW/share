package com.invoicestudio.service;

import com.invoicestudio.model.Settings;
import com.invoicestudio.model.Template;
import com.invoicestudio.model.TemplateElement;
import javafx.scene.Node;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;

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

    /**
     * Places one label artwork node into its PHYSICAL die-cut cell — the
     * single geometry every viewer (strip preview, bulk popup preview and
     * the real print path) must share so all three are pixel-identical.
     * <p>
     * The returned holder is sized {@code cellWmm × cellHmm} (physical cell
     * on the liner); the artwork (authored at {@code designWmm × designHmm})
     * is centered inside it and spun by {@code angleDeg}° about its own
     * centre when the label uses a legacy 90/270 print orientation. The clip
     * lives on the UNROTATED holder, so even rotated artwork is clipped to
     * the physical cell shape exactly as the printer's die-cut would.
     *
     * @param artwork   node built by {@link #renderLabelNode} (design-space size)
     * @param designWmm design width of the artwork in mm
     * @param designHmm design height of the artwork in mm
     * @param cellWmm   physical cell width on the strip (mm)
     * @param cellHmm   physical cell height on the strip (mm)
     * @param angleDeg  artwork rotation in degrees (0 / 90 / 180 / 270)
     * @param clip      true to clip the holder to a rounded rectangle
     * @param radiusPx  corner radius of the clip in px (used when clip=true)
     */
    public static Pane physicalCellHolder(Pane artwork, double designWmm, double designHmm,
                                          double cellWmm, double cellHmm,
                                          double angleDeg, boolean clip, double radiusPx) {
        double cellWpx = cellWmm * MM_PX;
        double cellHpx = cellHmm * MM_PX;
        double artWpx = designWmm * MM_PX;
        double artHpx = designHmm * MM_PX;

        Pane holder = new Pane();
        holder.setPrefSize(cellWpx, cellHpx);
        holder.setMinSize(cellWpx, cellHpx);
        holder.setMaxSize(cellWpx, cellHpx);

        if (angleDeg != 0) {
            artwork.getTransforms().add(new Rotate(angleDeg, artWpx / 2.0, artHpx / 2.0));
        }
        artwork.setLayoutX((cellWpx - artWpx) / 2.0);
        artwork.setLayoutY((cellHpx - artHpx) / 2.0);
        holder.getChildren().add(artwork);

        if (clip) {
            Rectangle r = new Rectangle(cellWpx, cellHpx);
            r.setArcWidth(radiusPx);
            r.setArcHeight(radiusPx);
            holder.setClip(r);
        }
        return holder;
    }
}
