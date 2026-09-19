package com.invoicestudio.ui;

import com.invoicestudio.service.ModelStatusStore;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Circle;

/**
 * Tiny colored dot marking the learned usability of a model:
 * <ul>
 *   <li><b>Green</b> — last real request through this model succeeded.</li>
 *   <li><b>Red</b> — last real request failed with a balance/quota/access
 *       wall (stored reason shown in the tooltip).</li>
 *   <li><b>Grey (nothing rendered)</b> — never used / status unknown;
 *       the list stays clean for models the app has no opinion about.</li>
 * </ul>
 * Pure presentation — the state comes from {@link ModelStatusStore}.
 */
public final class ModelStatusDot {

    private ModelStatusDot() {}

    /**
     * Dot for a provider+model pair. Returns null for "unknown" so callers
     * can simply skip adding it (keeps rows tidy for untouched models).
     */
    public static Node forModel(String provider, String model) {
        ModelStatusStore.Entry e = ModelStatusStore.get(provider, model);
        if (e == null) return null;

        boolean blocked = e.state() == ModelStatusStore.State.BLOCKED;
        String color = blocked ? "#EF4444" : "#22C55E";
        String tooltipText = blocked
                ? "Not usable now: " + (e.reason() == null ? "quota/balance error" : e.reason())
                : "Working — last request through this model succeeded";

        StackPane dot = new StackPane();
        Circle c = new Circle(4, javafx.scene.paint.Color.web(color));
        dot.getChildren().add(c);
        dot.setStyle("-fx-cursor: hand;");
        Tooltip.install(dot, new Tooltip(tooltipText));
        return dot;
    }
}
