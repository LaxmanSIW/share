package com.invoicestudio.mcp;

import com.invoicestudio.ui.Toast;
import com.invoicestudio.ui.UiTheme;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Settings → MCP Server tab: configure the AI-access server (port,
 * auto-start, auth token), start/stop it, approve or reject pending
 * destructive operations, and review the audit trail.
 *
 * <p>Styled exclusively with UiTheme / globalfile.css — no inline colors.</p>
 */
public class McpSettingsPanel extends VBox {

    private final McpConfig config;
    private final TextField portField = new TextField();
    private final CheckBox autoStartBox = new CheckBox("Start server automatically when the app opens");
    private final TextField tokenField = new TextField();
    private final Label statusPill = new Label();
    private final Label endpointLabel = new Label();
    private final Button startBtn;
    private final Button stopBtn;
    private final VBox pendingBox = new VBox(8);
    private final VBox auditBox = new VBox(2);

    public McpSettingsPanel(McpConfig config) {
        this.config = config;
        setSpacing(16);

        startBtn = UiTheme.goldBtn("Start Server");
        stopBtn = UiTheme.secondaryBtn("Stop Server");

        getChildren().addAll(
                buildStatusSection(),
                buildConfigSection(),
                buildPendingSection(),
                buildAuditSection(),
                buildTokenSection());
        refreshStatus();
        refreshPending();
        refreshAudit();
        PendingOperations.setUiListener(this::refreshPending);
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    private VBox buildStatusSection() {
        VBox card = UiTheme.card(16);
        Label title = new Label("AI ACCESS SERVER (MCP)");
        title.getStyleClass().add("card-title");

        Label desc = new Label("Lets AI assistants (Claude, Cursor, any MCP client) use every InvoiceStudio "
                + "feature — billing, purchases, stock, expenses, financials — exactly like a veteran operator. "
                + "Runs on this machine only and stops when the app closes.");
        desc.getStyleClass().add("muted-label");
        desc.setWrapText(true);

        HBox statusRow = new HBox(12);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusPill.getStyleClass().add("status-pill");
        endpointLabel.getStyleClass().add("muted-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        statusRow.getChildren().addAll(statusPill, endpointLabel, spacer, startBtn, stopBtn);

        startBtn.setOnAction(e -> {
            persistConfig();
            String err = McpServer.start(config);
            if (err != null) {
                Toast.show(this, "MCP server failed to start: " + err);
            }
            refreshStatus();
        });
        stopBtn.setOnAction(e -> {
            McpServer.stop();
            refreshStatus();
        });

        card.getChildren().addAll(title, desc, statusRow);
        return card;
    }

    private VBox buildConfigSection() {
        VBox card = UiTheme.card(16);
        Label title = new Label("CONFIGURATION");
        title.getStyleClass().add("card-title");

        HBox portRow = new HBox(10);
        portRow.setAlignment(Pos.CENTER_LEFT);
        portField.setText(String.valueOf(config.getPort()));
        portField.setPrefWidth(110);
        portField.setTooltip(new Tooltip("TCP port on 127.0.0.1 (1024–65535). Default 7800."));
        portRow.getChildren().addAll(new Label("Port:"), portField,
                new Label("  Token required: "), tokenReqBox());

        autoStartBox.setSelected(config.isAutoStart());

        card.getChildren().addAll(title, portRow, autoStartBox);
        return card;
    }

    private CheckBox tokenReqBox() {
        CheckBox box = new CheckBox("Require bearer token");
        box.setSelected(config.isRequireToken());
        box.setTooltip(new Tooltip("Strongly recommended. AI clients must send Authorization: Bearer <token>."));
        box.setOnAction(e -> {
            config.setRequireToken(box.isSelected());
            config.save();
        });
        return box;
    }

    private VBox buildTokenSection() {
        VBox card = UiTheme.card(16);
        Label title = new Label("AUTH TOKEN & AI CLIENT SETUP");
        title.getStyleClass().add("card-title");

        Label explain = new Label("Two separate logins — don't mix them up:\n"
                + "1. APP LOGIN (Firebase): the account signed in inside InvoiceStudio. The MCP server "
                + "always works on THAT account's books. Your Firebase password never leaves the app.\n"
                + "2. MCP TOKEN (below): what your AI client (Cursor, VS Code, Claude) sends to prove it "
                + "may talk to this machine. It grants access only while the app is running, and only "
                + "as the signed-in user.");
        explain.getStyleClass().add("muted-label");
        explain.setWrapText(true);

        Label hint = new Label("Endpoint:  POST " + (McpServer.isRunning() ? McpServer.endpointUrl()
                : "http://127.0.0.1:" + config.getPort() + "/mcp")
                + "   with header   Authorization: Bearer <token>");
        hint.getStyleClass().add("muted-label");
        hint.setWrapText(true);

        tokenField.setText(config.getToken());
        tokenField.setEditable(false);
        HBox.setHgrow(tokenField, Priority.ALWAYS);

        Button regen = UiTheme.secondaryBtn("Regenerate");
        regen.setOnAction(e -> {
            config.setToken(McpConfig.generateToken());
            config.save();
            tokenField.setText(config.getToken());
            Toast.show(this, "New token generated — update your MCP client config");
        });

        Button copy = UiTheme.secondaryBtn("Copy Token");
        copy.setOnAction(e -> {
            javafx.scene.input.Clipboard.getSystemClipboard()
                    .setContent(new javafx.scene.input.ClipboardContent() {{
                        putString(config.getToken());
                    }});
            Toast.show(this, "Token copied to clipboard");
        });

        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getChildren().addAll(tokenField, regen, copy);

        // One-click config for the common IDEs
        Button copyIde = UiTheme.secondaryBtn("Copy Cursor / VS Code Config");
        copyIde.setTooltip(new Tooltip("Copies ready-to-paste JSON for .cursor/mcp.json or VS Code MCP settings"));
        copyIde.setOnAction(e -> {
            String json = clientConfigJson("Cursor / VS Code");
            javafx.scene.input.Clipboard.getSystemClipboard()
                    .setContent(new javafx.scene.input.ClipboardContent() {{
                        putString(json);
                    }});
            Toast.show(this, "Client config copied — paste into .cursor/mcp.json or VS Code MCP settings");
        });

        Button copyClaude = UiTheme.secondaryBtn("Copy Claude Desktop Config");
        copyClaude.setTooltip(new Tooltip("Copies ready-to-paste JSON for claude_desktop_config.json"));
        copyClaude.setOnAction(e -> {
            String json = clientConfigJson("Claude Desktop");
            javafx.scene.input.Clipboard.getSystemClipboard()
                    .setContent(new javafx.scene.input.ClipboardContent() {{
                        putString(json);
                    }});
            Toast.show(this, "Claude Desktop config copied");
        });

        HBox ideRow = new HBox(10, copyIde, copyClaude);

        card.getChildren().addAll(title, explain, hint, row, ideRow);
        return card;
    }

    /** Ready-to-paste MCP client config for the running (or configured) server. */
    private String clientConfigJson(String client) {
        String url = McpServer.isRunning() ? McpServer.endpointUrl()
                : "http://127.0.0.1:" + config.getPort() + "/mcp";
        if (client.startsWith("Claude")) {
            return "// claude_desktop_config.json — note: Claude Desktop speaks stdio;\n"
                    + "// use an HTTP bridge such as `npx mcp-remote" + url + " --header \"Authorization:Bearer "
                    + config.getToken() + "\"`\n" + clientConfigJson("other");
        }
        return "{\n"
                + "  \"mcpServers\": {\n"
                + "    \"invoicestudio\": {\n"
                + "      \"url\": \"" + url + "\",\n"
                + "      \"headers\": {\n"
                + "        \"Authorization\": \"Bearer " + config.getToken() + "\"\n"
                + "      }\n"
                + "    }\n"
                + "  }\n"
                + "}\n";
    }

    private VBox buildPendingSection() {
        VBox card = UiTheme.card(16);
        Label title = new Label("PENDING CONFIRMATIONS");
        title.getStyleClass().add("card-title");

        Label hint = new Label("AI-requested updates & deletes wait here. Nothing changes until you approve.");
        hint.getStyleClass().add("muted-label");
        hint.setWrapText(true);

        card.getChildren().addAll(title, hint, pendingBox);
        return card;
    }

    private VBox buildAuditSection() {
        VBox card = UiTheme.card(16);
        Label title = new Label("ACTIVITY LOG");
        title.getStyleClass().add("card-title");

        ScrollPane sp = new ScrollPane(auditBox);
        sp.setFitToWidth(true);
        sp.setPrefHeight(180);
        sp.getStyleClass().add("settings-scroll-pane");

        card.getChildren().addAll(title, sp);
        return card;
    }

    // ------------------------------------------------------------------
    // Refreshers
    // ------------------------------------------------------------------

    private void refreshStatus() {
        boolean running = McpServer.isRunning();
        statusPill.setText(running ? "● RUNNING" : "○ STOPPED");
        statusPill.getStyleClass().removeAll("success", "danger");
        statusPill.getStyleClass().add(running ? "success" : "danger");
        endpointLabel.setText(running ? McpServer.endpointUrl() : "  (not running)");
        startBtn.setDisable(running);
        stopBtn.setDisable(!running);
    }

    private void refreshPending() {
        pendingBox.getChildren().clear();
        List<PendingOperations.PendingOp> ops = PendingOperations.pending();
        if (ops.isEmpty()) {
            Label none = new Label("No pending operations.");
            none.getStyleClass().add("muted-label");
            pendingBox.getChildren().add(none);
            return;
        }
        for (PendingOperations.PendingOp op : ops) {
            VBox row = new VBox(4);
            row.getStyleClass().add("card-pane-subtle");
            row.setPadding(new Insets(10));

            Label summary = new Label(op.getSummary());
            summary.getStyleClass().add("bold-label");
            summary.setWrapText(true);

            Label detail = new Label(op.getDetail());
            detail.getStyleClass().add("muted-label");
            detail.setWrapText(true);

            Button approve = UiTheme.goldBtn("Approve");
            Button reject = UiTheme.secondaryBtn("Reject");
            approve.setOnAction(e -> {
                PendingOperations.approve(op.getId());
                Toast.show(this, "Approved: " + op.getSummary());
                refreshAudit();
            });
            reject.setOnAction(e -> {
                PendingOperations.reject(op.getId());
                Toast.show(this, "Rejected: " + op.getSummary());
                refreshAudit();
            });

            HBox buttons = new HBox(10, approve, reject);
            row.getChildren().addAll(summary, detail, buttons);
            pendingBox.getChildren().add(row);
        }
    }

    private void refreshAudit() {
        auditBox.getChildren().clear();
        List<String> entries = McpAuditLog.recent();
        if (entries.isEmpty()) {
            Label none = new Label("No MCP activity yet.");
            none.getStyleClass().add("muted-label");
            auditBox.getChildren().add(none);
            return;
        }
        for (int i = entries.size() - 1; i >= 0; i--) { // newest first
            Label line = new Label(entries.get(i));
            line.getStyleClass().add("muted-label");
            line.setWrapText(true);
            auditBox.getChildren().add(line);
        }
    }

    /** Persists port & auto-start before the server (re)starts. */
    private void persistConfig() {
        try {
            config.setPort(Integer.parseInt(portField.getText().trim()));
        } catch (NumberFormatException ignored) {
            portField.setText(String.valueOf(config.getPort()));
        }
        config.setAutoStart(autoStartBox.isSelected());
        config.save();
    }
}
