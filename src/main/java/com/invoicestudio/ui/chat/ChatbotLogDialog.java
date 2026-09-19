package com.invoicestudio.ui.chat;

import com.invoicestudio.service.ChatbotLogManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Real-time CLI Execution Log Dialog for the AI Chatbot.
 * Provides live visibility into query routing, provider dispatches, tool executions,
 * timing diagnostics, and confirmation events in a modern dark terminal layout.
 *
 * <p>Organised for reading top-down: every user message opens a gold
 * {@code NEW MESSAGE} turn divider, so each ROUTER/HTTP/TOOL step can be
 * attributed to the request it belongs to. Filter chips (routing / http /
 * tools / issues) collapse the stream to one concern. Auto-scroll only
 * follows the tail while the view is already pinned to the bottom —
 * scrolling up to read history is never yanked away.</p>
 *
 * <p>Lifecycle note: the live listener is (re-)registered on every
 * {@link #show()} and removed when the stage hides. The dialog instance is
 * cached by the chat panel, so without the re-register the SECOND open of
 * the window stayed silent forever — the reported "logs stop working after
 * a while".</p>
 */
public final class ChatbotLogDialog {

    private static final String MONO_FONT = "Consolas, 'Courier New', monospace";

    private final Stage stage = new Stage();
    private final VBox logRows = new VBox(3);
    private final ScrollPane scroll = new ScrollPane(logRows);
    private final Label countLbl = new Label("0 events");
    private final Consumer<ChatbotLogManager.LogEntry> listener = this::appendEntry;

    /** Rendered blocks with their source entry — lets filters restyle live. */
    private final List<Block> blocks = new ArrayList<>();
    private String activeFilter = "ALL";
    private int turnCounter = 0;
    /** True while the viewport hugs the bottom; auto-scroll only then. */
    private boolean pinned = true;

    private record Block(ChatbotLogManager.LogEntry entry, javafx.scene.Node node) {}

    public ChatbotLogDialog(Window owner) {
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.NONE);
        stage.setTitle("Assistant Live Execution Logs");
        stage.setWidth(720);
        stage.setHeight(520);
        stage.setMinWidth(500);
        stage.setMinHeight(340);

        // ── Header bar ──
        Label title = new Label(">_ Assistant Execution Logs");
        title.setStyle("-fx-font-family: " + MONO_FONT + "; -fx-font-weight: bold; "
                + "-fx-font-size: 13px; -fx-text-fill: #38BDF8;");

        countLbl.setStyle("-fx-font-family: " + MONO_FONT + "; -fx-font-size: 11px; -fx-text-fill: #64748B;");

        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);

        Button copyBtn = smallButton("Copy All");
        copyBtn.setOnAction(e -> {
            StringBuilder sb = new StringBuilder();
            for (ChatbotLogManager.LogEntry entry : ChatbotLogManager.getEntries()) {
                sb.append(entry.toCliString()).append("\n");
            }
            Clipboard cb = Clipboard.getSystemClipboard();
            ClipboardContent cc = new ClipboardContent();
            cc.putString(sb.toString());
            cb.setContent(cc);
            copyBtn.setText("Copied!");
            javafx.animation.PauseTransition pt = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1.5));
            pt.setOnFinished(ev -> copyBtn.setText("Copy All"));
            pt.play();
        });

        Button clearBtn = smallButton("Clear");
        clearBtn.setOnAction(e -> {
            ChatbotLogManager.clear();
            rebuildFromBuffer();
        });

        HBox topBar = new HBox(8, title, countLbl, spring, copyBtn, clearBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: #0F172A; -fx-padding: 10 14 8 14; -fx-border-color: #1E293B; -fx-border-width: 0 0 1 0;");

        // ── Filter chips: one concern at a time ──
        HBox filterBar = new HBox(6);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        filterBar.setPadding(new Insets(8, 14, 8, 14));
        filterBar.setStyle("-fx-background-color: #0F172A; -fx-border-color: #1E293B; -fx-border-width: 0 0 1 0;");

        ToggleGroup group = new ToggleGroup();
        for (String chip : List.of("ALL", "ROUTING", "HTTP", "TOOLS", "RESULTS", "ISSUES")) {
            ToggleButton b = new ToggleButton(chip);
            b.setToggleGroup(group);
            b.getStyleClass().add("log-filter-chip");
            styleChip(b, chip.equals(activeFilter));
            String tip = switch (chip) {
                case "ROUTING" -> "Router decisions & provider dispatches";
                case "HTTP" -> "Provider requests, latency & token usage";
                case "TOOLS" -> "Tool calls & local MCP executions";
                case "RESULTS" -> "Completions and system notes";
                case "ISSUES" -> "Warnings and errors only";
                default -> "Everything";
            };
            b.setTooltip(new Tooltip(tip));
            b.setOnAction(e -> {
                activeFilter = chip;
                for (javafx.scene.Node n : filterBar.getChildren()) {
                    styleChip((ToggleButton) n, ((ToggleButton) n).getText().equals(activeFilter));
                }
                applyFilter();
            });
            filterBar.getChildren().add(b);
        }
        Label filterHint = new Label("message dividers stay visible in every filter");
        filterHint.setStyle("-fx-font-size: 10px; -fx-text-fill: #475569;");
        Region fSpring = new Region();
        HBox.setHgrow(fSpring, Priority.ALWAYS);
        filterBar.getChildren().addAll(fSpring, filterHint);

        // ── Log rows area ──
        logRows.setStyle("-fx-background-color: #070B11; -fx-padding: 10 12;");
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #070B11; -fx-background: #070B11; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        // Pin tracking: auto-scroll only follows the tail when the user is
        // already at (or near) the bottom — reading history is never disturbed.
        scroll.vvalueProperty().addListener((o, a, v) ->
                pinned = v.doubleValue() >= scroll.getVmax() - 0.02 || scroll.getVmax() <= 0.02);

        VBox root = new VBox(topBar, filterBar, scroll);
        root.setStyle("-fx-background-color: #070B11;");

        Scene scene = new Scene(root);
        stage.setScene(scene);

        rebuildFromBuffer();

        stage.setOnHidden(e -> ChatbotLogManager.removeListener(listener));
    }

    public void show() {
        if (!stage.isShowing()) {
            // THE FIX: (re)attach before showing. The listener used to be
            // registered once in the constructor and removed on close, so
            // every reopen after the first was permanently silent.
            ChatbotLogManager.removeListener(listener);
            ChatbotLogManager.addListener(listener);
            rebuildFromBuffer(); // catch up on entries logged while hidden
            stage.show();
        } else {
            stage.toFront();
        }
        pinned = true;
        scrollToBottom();
    }

    /** Rebuilds all rows from the manager buffer (used on open and clear). */
    private void rebuildFromBuffer() {
        turnCounter = 0;
        blocks.clear();
        logRows.getChildren().clear();
        for (ChatbotLogManager.LogEntry entry : ChatbotLogManager.getEntries()) {
            appendRow(entry);
        }
        applyFilter();
        updateCount();
    }

    private void appendEntry(ChatbotLogManager.LogEntry entry) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> appendEntry(entry));
            return;
        }
        boolean wasPinned = pinned;
        appendRow(entry);
        applyFilterTo(blocks.get(blocks.size() - 1));
        updateCount();
        if (wasPinned) {
            scrollToBottom();
        }
    }

    private void appendRow(ChatbotLogManager.LogEntry entry) {
        if (entry.level() == ChatbotLogManager.LogLevel.USER) {
            turnCounter++;
            blocks.add(new Block(entry, turnDivider(entry, turnCounter)));
            return;
        }

        HBox row = new HBox(6);
        row.setAlignment(Pos.TOP_LEFT);

        // Color bar — one glance tells the kind of step
        Region bar = new Region();
        bar.setStyle("-fx-background-color: " + barColor(entry.level())
                + "; -fx-background-radius: 2; -fx-min-width: 3; -fx-max-width: 3;");
        bar.setMinHeight(18);

        Label timeLbl = new Label(entry.timestamp());
        timeLbl.setStyle("-fx-font-family: " + MONO_FONT + "; -fx-font-size: 11px; -fx-text-fill: #475569;");
        timeLbl.setMinWidth(76);

        Label tagLbl = new Label("[" + entry.tag() + "]");
        tagLbl.setStyle("-fx-font-family: " + MONO_FONT + "; -fx-font-size: 11px; -fx-font-weight: bold; "
                + badgeColor(entry.level()) + " -fx-background-radius: 4; -fx-padding: 1 5;");

        boolean isError = entry.level() == ChatbotLogManager.LogLevel.ERROR
                || entry.level() == ChatbotLogManager.LogLevel.WARN;
        Label msgLbl = new Label(entry.message());
        msgLbl.setStyle("-fx-font-family: " + MONO_FONT + "; -fx-font-size: 12px; -fx-text-fill: "
                + (isError ? "#FCA5A5;" : "#F1F5F9;"));
        msgLbl.setWrapText(true);
        HBox.setHgrow(msgLbl, Priority.ALWAYS);

        row.getChildren().addAll(bar, timeLbl, tagLbl, msgLbl);

        if (entry.details() != null && !entry.details().isBlank()) {
            Label detLbl = new Label(entry.details());
            detLbl.setStyle("-fx-font-family: " + MONO_FONT + "; -fx-font-size: 11px; -fx-text-fill: #94A3B8; -fx-padding: 0 0 2 97;");
            detLbl.setWrapText(true);
            VBox block = new VBox(2, row, detLbl);
            blocks.add(new Block(entry, block));
        } else {
            blocks.add(new Block(entry, row));
        }
    }

    /** Gold full-width divider opening a new message turn. */
    private javafx.scene.Node turnDivider(ChatbotLogManager.LogEntry entry, int turn) {
        Label marker = new Label("◆  MESSAGE #" + turn);
        marker.setStyle("-fx-font-family: " + MONO_FONT + "; -fx-font-size: 11px; -fx-font-weight: bold; "
                + "-fx-text-fill: #D9A13B;");

        Label timeLbl = new Label(entry.timestamp());
        timeLbl.setStyle("-fx-font-family: " + MONO_FONT + "; -fx-font-size: 10.5px; -fx-text-fill: #6B7280;");

        Region line = new Region();
        line.setStyle("-fx-background-color: rgba(217,161,59,0.35); -fx-min-height: 1; -fx-max-height: 1;");
        HBox.setHgrow(line, Priority.ALWAYS);

        HBox head = new HBox(8, marker, timeLbl, line);
        head.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(2);
        box.setStyle("-fx-padding: 8 0 2 0;");
        box.getChildren().add(head);
        if (entry.message() != null && !entry.message().isBlank()) {
            Label quote = new Label(entry.message());
            quote.setStyle("-fx-font-family: " + MONO_FONT + "; -fx-font-size: 11px; -fx-text-fill: #B9A26B;");
            quote.setWrapText(true);
            box.getChildren().add(quote);
        }
        return box;
    }

    /** Shows/hides each rendered block according to the active chip. */
    private void applyFilter() {
        for (Block b : blocks) {
            applyFilterTo(b);
        }
        updateCount();
    }

    private void applyFilterTo(Block b) {
        boolean show = showsEntry(activeFilter, b.entry());
        b.node().setManaged(show);
        b.node().setVisible(show);
    }

    /**
     * Filter predicate — package-private static so it is unit-testable
     * without the JavaFX toolkit. USER turn dividers are always shown so a
     * filtered view still anchors every step to its message.
     */
    static boolean showsEntry(String chip, ChatbotLogManager.LogEntry e) {
        if (e == null) return false;
        if (e.level() == ChatbotLogManager.LogLevel.USER) return true;
        return switch (chip == null ? "ALL" : chip) {
            case "ROUTING" -> e.level() == ChatbotLogManager.LogLevel.ROUTER
                    || e.level() == ChatbotLogManager.LogLevel.DISPATCH;
            case "HTTP" -> e.level() == ChatbotLogManager.LogLevel.HTTP
                    || e.level() == ChatbotLogManager.LogLevel.TOKENS;
            case "TOOLS" -> e.level() == ChatbotLogManager.LogLevel.TOOL
                    || e.level() == ChatbotLogManager.LogLevel.MCP;
            case "RESULTS" -> e.level() == ChatbotLogManager.LogLevel.SUCCESS
                    || e.level() == ChatbotLogManager.LogLevel.INFO;
            case "ISSUES" -> e.level() == ChatbotLogManager.LogLevel.WARN
                    || e.level() == ChatbotLogManager.LogLevel.ERROR;
            default -> true; // ALL
        };
    }

    private void updateCount() {
        int shown = 0;
        int issues = 0;
        for (Block b : blocks) {
            if (b.node().isManaged()) {
                shown++;
            }
            ChatbotLogManager.LogLevel lv = b.entry().level();
            if (lv == ChatbotLogManager.LogLevel.WARN || lv == ChatbotLogManager.LogLevel.ERROR) {
                issues++;
            }
        }
        countLbl.setText(shown + " events · " + issues + " issues");
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scroll.setVvalue(1.0));
    }

    private static Button smallButton(String text) {
        Button b = new Button(text);
        b.setStyle("-fx-background-color: #1E293B; -fx-text-fill: #CBD5E1; -fx-font-size: 11px; "
                + "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 4 10;");
        return b;
    }

    private static void styleChip(ToggleButton b, boolean selected) {
        b.setStyle("-fx-font-size: 10.5px; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 3 10 3 10;"
                + "-fx-background-radius: 999; -fx-border-radius: 999; -fx-border-width: 1;"
                + (selected
                    ? "-fx-background-color: rgba(217,161,59,0.18); -fx-text-fill: #D9A13B; -fx-border-color: rgba(217,161,59,0.55);"
                    : "-fx-background-color: #1E293B; -fx-text-fill: #94A3B8; -fx-border-color: #273245;"));
    }

    private static String barColor(ChatbotLogManager.LogLevel level) {
        return switch (level) {
            case USER -> "#D9A13B";
            case ROUTER -> "#38BDF8";
            case DISPATCH -> "#818CF8";
            case TOOL -> "#F59E0B";
            case MCP -> "#10B981";
            case TOKENS -> "#A78BFA";
            case SUCCESS -> "#22C55E";
            case WARN -> "#FB923C";
            case ERROR -> "#EF4444";
            default -> "#334155";
        };
    }

    private static String badgeColor(ChatbotLogManager.LogLevel level) {
        return switch (level) {
            case ROUTER -> "-fx-text-fill: #38BDF8; -fx-background-color: rgba(56, 189, 248, 0.12);";
            case DISPATCH -> "-fx-text-fill: #818CF8; -fx-background-color: rgba(129, 140, 248, 0.12);";
            case TOOL -> "-fx-text-fill: #F59E0B; -fx-background-color: rgba(245, 158, 11, 0.12);";
            case MCP -> "-fx-text-fill: #10B981; -fx-background-color: rgba(16, 185, 129, 0.12);";
            case TOKENS -> "-fx-text-fill: #A78BFA; -fx-background-color: rgba(167, 139, 250, 0.12);";
            case SUCCESS -> "-fx-text-fill: #22C55E; -fx-background-color: rgba(34, 197, 94, 0.15);";
            case WARN -> "-fx-text-fill: #FB923C; -fx-background-color: rgba(251, 146, 60, 0.12);";
            case ERROR -> "-fx-text-fill: #EF4444; -fx-background-color: rgba(239, 68, 68, 0.15);";
            default -> "-fx-text-fill: #94A3B8; -fx-background-color: rgba(148, 163, 184, 0.10);";
        };
    }
}
