package com.invoicestudio.ui.chat;

import com.invoicestudio.service.ChatbotLogManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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

import java.util.function.Consumer;

/**
 * Real-time CLI Execution Log Dialog for the AI Chatbot.
 * Provides live visibility into query routing, provider dispatches, tool executions,
 * timing diagnostics, and confirmation events in a modern dark terminal layout.
 */
public final class ChatbotLogDialog {

    private static final String MONO_FONT = "Consolas, 'Courier New', monospace";

    private final Stage stage = new Stage();
    private final VBox logRows = new VBox(4);
    private final ScrollPane scroll = new ScrollPane(logRows);
    private final Label countLbl = new Label("0 events");
    private final Consumer<ChatbotLogManager.LogEntry> listener = this::appendEntry;

    public ChatbotLogDialog(Window owner) {
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.NONE);
        stage.setTitle("Assistant Live Execution Logs");
        stage.setWidth(680);
        stage.setHeight(480);
        stage.setMinWidth(480);
        stage.setMinHeight(320);

        // Header bar
        Label title = new Label(">_ Assistant Execution Logs");
        title.setStyle("-fx-font-family: " + MONO_FONT + "; -fx-font-weight: bold; "
                + "-fx-font-size: 13px; -fx-text-fill: #38BDF8;");

        countLbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #64748B;");

        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);

        Button copyBtn = new Button("Copy All");
        copyBtn.setStyle("-fx-background-color: #1E293B; -fx-text-fill: #CBD5E1; -fx-font-size: 11px; "
                + "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 4 10;");
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

        Button clearBtn = new Button("Clear");
        clearBtn.setStyle("-fx-background-color: #1E293B; -fx-text-fill: #CBD5E1; -fx-font-size: 11px; "
                + "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 4 10;");
        clearBtn.setOnAction(e -> {
            ChatbotLogManager.clear();
            logRows.getChildren().clear();
            updateCount();
        });

        HBox topBar = new HBox(8, title, countLbl, spring, copyBtn, clearBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setStyle("-fx-background-color: #0F172A; -fx-padding: 10 14; -fx-border-color: #1E293B; -fx-border-width: 0 0 1 0;");

        // Log rows area
        logRows.setStyle("-fx-background-color: #070B11; -fx-padding: 10 12;");
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: #070B11; -fx-background: #070B11; -fx-border-color: transparent;");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox root = new VBox(topBar, scroll);
        root.setStyle("-fx-background-color: #070B11;");

        Scene scene = new Scene(root);
        stage.setScene(scene);

        // Load existing logs
        for (ChatbotLogManager.LogEntry entry : ChatbotLogManager.getEntries()) {
            appendEntry(entry);
        }
        updateCount();

        // Wire real-time updates
        ChatbotLogManager.addListener(listener);
        stage.setOnHidden(e -> ChatbotLogManager.removeListener(listener));
    }

    public void show() {
        if (!stage.isShowing()) {
            stage.show();
        } else {
            stage.toFront();
        }
        scrollToBottom();
    }

    private void appendEntry(ChatbotLogManager.LogEntry entry) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> appendEntry(entry));
            return;
        }

        HBox row = new HBox(6);
        row.setAlignment(Pos.TOP_LEFT);

        // Timestamp
        Label timeLbl = new Label(entry.timestamp());
        timeLbl.setStyle("-fx-font-family: " + MONO_FONT + "; -fx-font-size: 11px; -fx-text-fill: #475569;");
        timeLbl.setMinWidth(76);

        // Tag badge
        Label tagLbl = new Label("[" + entry.tag() + "]");
        tagLbl.setStyle("-fx-font-family: " + MONO_FONT + "; -fx-font-size: 11px; -fx-font-weight: bold; "
                + badgeColor(entry.level()) + " -fx-background-radius: 4; -fx-padding: 1 5;");

        // Message
        Label msgLbl = new Label(entry.message());
        msgLbl.setStyle("-fx-font-family: " + MONO_FONT + "; -fx-font-size: 12px; -fx-text-fill: #F1F5F9;");
        msgLbl.setWrapText(true);
        HBox.setHgrow(msgLbl, Priority.ALWAYS);

        row.getChildren().addAll(timeLbl, tagLbl, msgLbl);

        if (entry.details() != null && !entry.details().isBlank()) {
            Label detLbl = new Label(entry.details());
            detLbl.setStyle("-fx-font-family: " + MONO_FONT + "; -fx-font-size: 11px; -fx-text-fill: #94A3B8; -fx-padding: 0 0 2 82;");
            detLbl.setWrapText(true);
            VBox block = new VBox(2, row, detLbl);
            logRows.getChildren().add(block);
        } else {
            logRows.getChildren().add(row);
        }

        updateCount();
        scrollToBottom();
    }

    private void updateCount() {
        countLbl.setText(logRows.getChildren().size() + " events");
    }

    private void scrollToBottom() {
        Platform.runLater(() -> scroll.setVvalue(1.0));
    }

    private static String badgeColor(ChatbotLogManager.LogLevel level) {
        return switch (level) {
            case ROUTER -> "-fx-text-fill: #38BDF8; -fx-background-color: rgba(56, 189, 248, 0.12);";
            case DISPATCH -> "-fx-text-fill: #818CF8; -fx-background-color: rgba(129, 140, 248, 0.12);";
            case TOOL -> "-fx-text-fill: #F59E0B; -fx-background-color: rgba(245, 158, 11, 0.12);";
            case MCP -> "-fx-text-fill: #10B981; -fx-background-color: rgba(16, 185, 129, 0.12);";
            case SUCCESS -> "-fx-text-fill: #22C55E; -fx-background-color: rgba(34, 197, 94, 0.15);";
            case WARN -> "-fx-text-fill: #FB923C; -fx-background-color: rgba(251, 146, 60, 0.12);";
            case ERROR -> "-fx-text-fill: #EF4444; -fx-background-color: rgba(239, 68, 68, 0.15);";
            default -> "-fx-text-fill: #94A3B8; -fx-background-color: rgba(148, 163, 184, 0.10);";
        };
    }
}
