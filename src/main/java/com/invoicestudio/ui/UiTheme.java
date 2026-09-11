package com.invoicestudio.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * UI factory helpers — the SINGLE source of reusable visual building blocks.
 *
 * GOLDEN RULE (fixes the broken hover app-wide):
 *   NEVER call node.setStyle(...). Inline styles in JavaFX have higher
 *   precedence than stylesheet :hover rules, which is why hover states died
 *   in the original app. Everything here attaches CSS classes from
 *   globalfile.css; dynamic variation is expressed with extra style classes
 *   (e.g. "accent-gold", "accent-emerald"), never inline styles.
 */
public final class UiTheme {

    private UiTheme() {}

    // ---------- Layout ----------

    /** Standard page container with consistent padding — every view root uses this. */
    public static VBox page() {
        VBox page = new VBox(20);
        page.setPadding(new Insets(24));
        page.getStyleClass().add("view-page");
        return page;
    }

    /** Card container (surface + border + hover lift via CSS). */
    public static VBox card(double spacing) {
        VBox card = new VBox(spacing);
        card.getStyleClass().add("card");
        return card;
    }

    public static VBox card() {
        return card(12);
    }

    /** Horizontal row inside a card. */
    public static HBox row(double spacing) {
        HBox row = new HBox(spacing);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    /** Flexible horizontal spacer. */
    public static Region spacer() {
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        VBox.setVgrow(sp, Priority.NEVER);
        return sp;
    }

    // ---------- Typography ----------

    /** Large page title. */
    public static Label pageTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("heading-l");
        return l;
    }

    /** Small subtitle under a page title. */
    public static Label pageSubtitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("view-subtitle");
        l.setWrapText(true);
        return l;
    }

    /** Card/section title. */
    public static Label sectionTitle(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("card-title");
        return l;
    }

    /** Uppercase micro-label (tracking headers). */
    public static Label microLabel(String text, String accentClass) {
        Label l = new Label(text);
        l.getStyleClass().addAll("micro-label");
        if (accentClass != null) l.getStyleClass().add(accentClass);
        return l;
    }

    /** Title+subtitle stacked header block used at the top of views. */
    public static VBox headerBlock(String iconGlyph, String title, String subtitle) {
        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        if (iconGlyph != null) {
            Label ic = new Label(iconGlyph);
            ic.getStyleClass().addAll("icon-accent", "icon-lg");
            titleRow.getChildren().add(ic);
        }
        titleRow.getChildren().add(pageTitle(title));

        VBox box = new VBox(3);
        box.getChildren().addAll(titleRow, pageSubtitle(subtitle));
        return box;
    }

    // ---------- KPI metric card ----------

    /**
     * Metric card: micro title, big value, sub description.
     * accentClass: accent-gold / accent-emerald / accent-red / accent-sky / accent-amber —
     * applied to the SUB line (e.g. "+12.5% vs last month") so a delta reads
     * green for plus and red for minus; the big value stays neutral.
     */
    public static VBox kpiCard(String title, Label valueLabel, String subText, String accentClass) {
        VBox card = new VBox(5);
        card.getStyleClass().add("kpi-card");
        HBox.setHgrow(card, Priority.ALWAYS);
        Label sub = subText != null ? subLabel(subText) : new Label();
        card.getChildren().addAll(microLabel(title, null), valueLabel, sub);
        if (accentClass != null && subText != null) {
            sub.getStyleClass().add(accentClass);
        }
        return card;
    }

    public static Label kpiValue(String initial) {
        Label l = new Label(initial);
        l.getStyleClass().add("kpi-value");
        return l;
    }

    public static Label subLabel(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("kpi-subtext");
        return l;
    }

    // ---------- Badges & pills ----------

    /** Neutral information pill (e.g. HSN, unit, size). */
    public static Label pill(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("badge-neutral");
        return l;
    }

    /** Gold code pill for {{placeholders}} and IDs. */
    public static Label codePill(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("code-pill");
        return l;
    }

    /** Status pill: success / warning / danger / neutral. */
    public static Label statusPill(String text, String semantic) {
        Label l = new Label(text);
        l.getStyleClass().addAll("status-pill", semantic);
        return l;
    }

    // ---------- Buttons (classes only → hover works) ----------

    public static Button primaryBtn(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll("button-primary");
        return b;
    }

    public static Button goldBtn(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll("gold-btn");
        return b;
    }

    public static Button secondaryBtn(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll("button-secondary");
        return b;
    }

    public static Button smallBtn(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll("button-sm", "button-secondary");
        return b;
    }

    public static Button dangerBtn(String text) {
        Button b = new Button(text);
        b.getStyleClass().addAll("button-sm", "button-danger");
        return b;
    }

    public static Button iconBtn(String glyph, String tooltip) {
        Button b = new Button(glyph);
        b.getStyleClass().addAll("button-icon-subtle");
        if (tooltip != null && !tooltip.isBlank()) {
            b.setTooltip(new javafx.scene.control.Tooltip(tooltip));
        }
        return b;
    }

    // ---------- Data rows ----------

    /** Label — spacer — value row used across stats and totals. */
    public static HBox statRow(String label, String value) {
        HBox row = new HBox();
        row.setAlignment(Pos.CENTER_LEFT);
        Label l = new Label(label);
        l.getStyleClass().add("stat-label");
        Region sp = new Region();
        HBox.setHgrow(sp, Priority.ALWAYS);
        Label v = new Label(value);
        v.getStyleClass().add("stat-value");
        row.getChildren().addAll(l, sp, v);
        return row;
    }

    /** Form label above an input. */
    public static VBox labeled(String text, Node input) {
        VBox box = new VBox(4);
        Label lbl = new Label(text);
        lbl.getStyleClass().add("field-label");
        box.getChildren().addAll(lbl, input);
        return box;
    }

    // ---------- Empty states ----------

    public static VBox emptyState(String glyph, String message, String hint) {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        box.getStyleClass().add("empty-state");

        Label g = new Label(glyph);
        g.getStyleClass().add("empty-glyph");
        Label m = new Label(message);
        m.getStyleClass().add("empty-message");
        box.getChildren().addAll(g, m);
        if (hint != null && !hint.isBlank()) {
            Label h = new Label(hint);
            h.getStyleClass().add("kpi-subtext");
            box.getChildren().add(h);
        }
        return box;
    }

    /** Toast helper passthrough so views don't reach for raw pane styling. */
    public static void toast(Pane root, String title, String msg, boolean error) {
        Toast.show(root, title, msg, error);
    }
}
