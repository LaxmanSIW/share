package com.invoicestudio.ui.chat;

import com.invoicestudio.service.ChatbotLogManager;
import com.invoicestudio.ui.IconHelper;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Duration;

/**
 * Tiny animated "assistant at work" strip: a character avatar + a stage
 * caption in plain words (received → routing → API → MCP → done), a coin
 * counter that ticks up with the tokens each round reports, and a slim
 * live progress bar underneath. It is streamed from the SAME
 * {@link ChatbotLogManager} entries that already drive the logs — no extra
 * work is generated, it only renders what the send pipeline reports.
 *
 * <p><b>Performance contract (this is why it exists at all):</b></p>
 * <ul>
 *   <li>hidden and UNMANAGED whenever no request is in flight (zero layout
 *       cost while idle),</li>
 *   <li>exactly two cheap animations while visible — one {@link ScaleTransition}
 *       pulse on a 22px avatar and one 300ms {@link Timeline} advancing the bar
 *       width — both STOPPED the moment the reply lands,</li>
 *   <li>no CSS effects, no shadows, no images to decode, no binding chains.</li>
 * </ul>
 */
public final class ChatPipelineBar extends VBox {

    private static final String GOLD = "#D9A13B";

    // 24×24 material glyphs — one per pipeline stage (no colour-emoji risk).
    private static final String G_BUBBLE = "M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z";
    private static final String G_COMPASS =
            "M12 10.9c-.61 0-1.1.49-1.1 1.1s.49 1.1 1.1 1.1c.61 0 1.1-.49 1.1-1.1s-.49-1.1-1.1-1.1zM12 2C6.48 2 2 6.48 2 12"
                    + "s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm2.19 12.19L6 18l3.81-8.19L18 6l-3.81 8.19z";
    private static final String G_CLOUD =
            "M19.35 10.04C18.67 6.59 15.64 4 12 4 9.11 4 6.6 5.64 5.35 8.04 2.34 8.36 0 10.91 0 14c0 3.31 2.69 6 6 6h13"
                    + "c2.76 0 5-2.24 5-5 0-2.64-2.05-4.78-4.65-4.96z";
    private static final String G_WRENCH =
            "M22.7 19l-9.1-9.1c.9-2.3.4-5-1.5-6.9-2-2-5-2.4-7.4-1.3L9 6 6 9 1.6 4.7C.4 7.1.9 10.1 2.9 12.1"
                    + "c1.9 1.9 4.6 2.4 6.9 1.5l9.1 9.1c.4.4 1 .4 1.4 0l2.3-2.3c.5-.4.5-1.1.1-1.4z";
    private static final String G_DB =
            "M12 2C6.48 2 2 3.9 2 6.3v11.4C2 20.1 6.48 22 12 22s10-1.9 10-4.3V6.3C22 3.9 17.52 2 12 2z";
    private static final String G_CHECK = "M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z";
    private static final String G_ALERT = "M1 21h22L12 2 1 21zm12-3h-2v-2h2v2zm0-4h-2v-4h2v4z";
    private static final String G_COIN =
            "M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm0 18c-4.41 0-8-3.59-8-8s3.59-8 8-8 "
                    + "8 3.59 8 8-3.59 8-8 8zm.5-13c1.74 0 3.15.86 3.43 2.5h-1.72c-.14-.69-.79-1.15-1.71-1.15-.97 0-1.65.5-1.65 1.2 "
                    + "0 .61.47.94 1.68 1.22 1.79.4 2.87 1.02 2.87 2.47 0 1.55-1.34 2.5-2.9 2.72V17h-1.5v-1.03c-1.7-.26-2.86-1.2-3.05-2.72 "
                    + "h1.75c.16.83.94 1.3 2.05 1.3 1.06 0 1.74-.47 1.74-1.12 0-.62-.43-.96-1.83-1.28-1.65-.37-2.83-.96-2.83-2.35 "
                    + "0-1.3 1.09-2.24 2.64-2.55V6h1.5v1z";

    /** Stage: glyph + caption shown in plain words. */
    private record Stage(String glyph, String caption) {}
    private static final Stage ST_RECEIVED  = new Stage(G_BUBBLE,  "Received — reading your request");
    private static final Stage ST_ROUTING   = new Stage(G_COMPASS, "Routing — picking the right tools");
    private static final Stage ST_API       = new Stage(G_CLOUD,   "Asking the AI (API)");
    private static final Stage ST_TOOL      = new Stage(G_WRENCH,  "Assistant calls a tool");
    private static final Stage ST_MCP       = new Stage(G_DB,      "Working in your app (MCP)");
    private static final Stage ST_DONE      = new Stage(G_CHECK,   "Done — answer ready");
    private static final Stage ST_ADJUST    = new Stage(G_ALERT,   "Adjusting…");

    private final StackPane avatar;
    private final Label stageIcon = new Label();
    private final Label stageLbl = new Label();
    private final Label coinLbl = new Label();
    private final Region fill = new Region();
    private final HBox track = new HBox(fill);

    private final ScaleTransition pulse;
    private final Timeline creep;
    private Stage last;
    private long tokensIn;
    private long tokensOut;
    private boolean tokenSeen;

    public ChatPipelineBar() {
        setSpacing(4);
        setPadding(new Insets(2, 2, 0, 2));
        setManaged(false);
        setVisible(false);

        // Character: the assistant's gold sparkle avatar (same face as the chat).
        avatar = new StackPane(IconHelper.getIcon(IconHelper.ICON_SPARKLES, 11, "#1A1408"));
        avatar.setStyle("-fx-background-color: " + GOLD + "; -fx-background-radius: 999;");
        avatar.setMinSize(22, 22);
        avatar.setMaxSize(22, 22);

        stageIcon.setGraphic(glyph(G_BUBBLE, 13, "#97A3B6"));
        stageLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #AAB6C8;");
        HBox stageRow = new HBox(7, avatar, stageIcon, stageLbl);
        stageRow.setAlignment(Pos.CENTER_LEFT);

        Label coinIcon = new Label();
        coinIcon.setGraphic(glyph(G_COIN, 12, GOLD));
        coinLbl.setStyle("-fx-font-size: 10.5px; -fx-text-fill: " + GOLD + ";");
        coinLbl.setVisible(false);
        coinLbl.setManaged(false);
        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);
        HBox metaRow = new HBox(5, spring, coinIcon, coinLbl);
        metaRow.setAlignment(Pos.CENTER_RIGHT);
        stageRow.getChildren().add(metaRow);
        HBox.setHgrow(stageRow, Priority.ALWAYS);

        // Slim live progress bar (asymptotic fill — never fakes completion).
        track.setStyle("-fx-background-color: #1B2534; -fx-background-radius: 3;");
        track.setPrefHeight(4);
        track.setMaxHeight(4);
        HBox.setHgrow(track, Priority.ALWAYS);
        fill.setStyle("-fx-background-color: " + GOLD + "; -fx-background-radius: 3;");
        fill.setPrefWidth(6);
        fill.setMinWidth(USE_PREF_SIZE);

        getChildren().addAll(stageRow, track);

        // Animation 1: gentle avatar pulse (single property, 22px node).
        pulse = new ScaleTransition(Duration.millis(650), avatar);
        pulse.setFromX(1.0); pulse.setFromY(1.0);
        pulse.setToX(1.14);  pulse.setToY(1.14);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setAutoReverse(true);

        // Animation 2: bar creep — moves 7% of the remaining distance every
        // 300ms, so it slows as it approaches the end and never "finishes".
        creep = new Timeline(new KeyFrame(Duration.millis(300), e -> {
            double max = Math.max(track.getWidth(), 120.0);
            double cur = fill.getPrefWidth();
            double next = cur + (max * 0.92 - cur) * 0.07;
            fill.setPrefWidth(Math.min(next, max * 0.92));
        }));
        creep.setCycleCount(Animation.INDEFINITE);
    }

    /** Resets counters, shows the strip and starts both animations. */
    public void begin() {
        tokensIn = 0;
        tokensOut = 0;
        tokenSeen = false;
        coinLbl.setText("");
        coinLbl.setVisible(false);
        coinLbl.setManaged(false);
        setStage(ST_RECEIVED);
        fill.setPrefWidth(6);
        setManaged(true);
        setVisible(true);
        if (pulse.getStatus() != Animation.Status.RUNNING) pulse.playFrom(Duration.ZERO);
        if (creep.getStatus() != Animation.Status.RUNNING) creep.playFrom(Duration.ZERO);
    }

    /** Stops both animations and shows the final state briefly, then hides. */
    public void end(boolean success) {
        pulse.stop();
        creep.stop();
        setStage(success ? ST_DONE : ST_ADJUST);
        PauseTransition hide = new PauseTransition(Duration.millis(1400));
        hide.setOnFinished(e -> {
            setManaged(false);
            setVisible(false);
        });
        hide.play();
    }

    /** Maps one log entry to a stage / coin update (called on the FX thread). */
    public void onLog(ChatbotLogManager.LogEntry e) {
        if (e == null || e.tag() == null || !isVisible()) return;
        switch (e.tag()) {
            case "ROUTER" -> setStage(ST_ROUTING);
            case "DISPATCH" -> setStage(ST_API);
            case "TOOL-CALL" -> setStage(ST_TOOL);
            case "MCP-EXEC" -> setStage(ST_MCP);
            case "TOKENS" -> {
                java.util.regex.Matcher mi = java.util.regex.Pattern.compile("↑(\\d+)")
                        .matcher(e.message() == null ? "" : e.message());
                java.util.regex.Matcher mo = java.util.regex.Pattern.compile("↓(\\d+)")
                        .matcher(e.message() == null ? "" : e.message());
                if (mi.find()) tokensIn += Long.parseLong(mi.group(1));
                if (mo.find()) tokensOut += Long.parseLong(mo.group(1));
                if (!tokenSeen && (tokensIn > 0 || tokensOut > 0)) {
                    tokenSeen = true;
                    coinLbl.setVisible(true);
                    coinLbl.setManaged(true);
                }
                if (tokenSeen) {
                    coinLbl.setText("+" + format(tokensIn + tokensOut) + " tok");
                }
            }
            case "WARN", "ERROR" -> setStage(ST_ADJUST);
            default -> { /* INFO/SUCCESS: keep the current stage */ }
        }
    }

    private void setStage(Stage s) {
        if (s == last) return; // no node churn when the stage repeats
        last = s;
        stageIcon.setGraphic(glyph(s.glyph(), 13, "#97A3B6"));
        stageLbl.setText(s.caption());
    }

    private static SVGPath glyph(String path, double size, String color) {
        SVGPath p = new SVGPath();
        p.setContent(path);
        p.setFill(Color.web(color));
        p.setStyle("-fx-scale-x: " + (size / 24.0) + "; -fx-scale-y: " + (size / 24.0) + ";");
        return p;
    }

    private static String format(long n) {
        return String.format("%,d", n);
    }
}
