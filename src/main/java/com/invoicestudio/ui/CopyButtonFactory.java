package com.invoicestudio.ui;

import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.shape.SVGPath;
import javafx.scene.paint.Color;

/**
 * Small icon-only "copy to clipboard" button shared by every surface that
 * shows a secret or text worth copying (API-key vault rows, chat bubbles,
 * log rows). Copies the full, unmasked value to the system clipboard —
 * masked text is display-only — then flips to a gold check for a moment so
 * the user can see the copy landed. Self-contained: no per-surface styling
 * knowledge, callers only supply text and an optional tooltip.
 */
public final class CopyButtonFactory {

    private static final String SVG_COPY =
            "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z";
    private static final String SVG_CHECK =
            "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z";

    private CopyButtonFactory() {}

    /** Copy button for the given text (tooltip "Copy secret text"). */
    public static Button create(String text) {
        return create(text, null);
    }

    /**
     * Copy button with a custom tooltip. Never renders the text itself —
     * the clipboard gets the raw value, the tooltip shows only what it is.
     */
    public static Button create(String text, String tooltipText) {
        Button b = new Button();
        b.getStyleClass().add("button-icon-subtle");
        b.setGraphic(glyph(SVG_COPY, 12, "#7C8AA0"));
        b.setTooltip(new Tooltip(tooltipText == null || tooltipText.isBlank()
                ? "Copy" : tooltipText));
        b.setMinSize(22, 22);
        b.setPrefSize(22, 22);
        b.setMaxSize(22, 22);
        b.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-padding: 0;");
        // When the button sits inside a selectable row (vault popup rows), a
        // click must copy ONLY — not bubble up and trigger the row's own
        // select/assign handler. Consuming the click at the button stops it;
        // ButtonBehavior uses press/release, not clicked, so firing is safe.
        b.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, javafx.event.Event::consume);
        b.setOnAction(e -> {
            javafx.scene.input.Clipboard cb = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
            cc.putString(text == null ? "" : text);
            cb.setContent(cc);
            b.setGraphic(glyph(SVG_CHECK, 12, "#D9A13B"));
            b.setTooltip(new Tooltip("Copied!"));
            javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(
                    javafx.util.Duration.seconds(1.5));
            pt.setOnFinished(ev -> {
                b.setGraphic(glyph(SVG_COPY, 12, "#7C8AA0"));
                b.setTooltip(new Tooltip(tooltipText == null || tooltipText.isBlank()
                        ? "Copy" : tooltipText));
            });
            pt.play();
        });
        return b;
    }

    private static SVGPath glyph(String content, double size, String color) {
        SVGPath p = new SVGPath();
        p.setContent(content);
        p.setFill(Color.web(color));
        p.setStyle("-fx-scale-x: " + (size / 24.0) + "; -fx-scale-y: " + (size / 24.0) + ";");
        return p;
    }
}
