package com.invoicestudio.ui;

import com.invoicestudio.service.AiChatClient;
import com.invoicestudio.service.AppExecutors;
import com.invoicestudio.service.ChatbotConfig;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.List;

/**
 * Settings → Chatbot: every chatbot-related control in one tab —
 * provider (Gemini / OpenAI / Claude-compatible), model id, API key,
 * optional endpoint override, history window, and the show/hide switch for
 * the floating chat icon. Saving persists {@link ChatbotConfig} and refreshes
 * the shell icon immediately.
 */
public class ChatbotSettingsPanel extends VBox {

    private static final String CARD =
            "-fx-background-color: #151C29; -fx-background-radius: 8;"
            + "-fx-border-color: #273245; -fx-border-radius: 8; -fx-border-width: 1; -fx-padding: 14;";

    private final StudioApp app;
    private final ChatbotConfig cfg;

    private final ComboBox<String> providerCb = new ComboBox<>(
            javafx.collections.FXCollections.observableArrayList(
                    ChatbotConfig.GEMINI, ChatbotConfig.OPENAI, ChatbotConfig.ANTHROPIC,
                    ChatbotConfig.OPENROUTER, ChatbotConfig.GROQ, ChatbotConfig.OLLAMA,
                    ChatbotConfig.MISTRAL, ChatbotConfig.DEEPSEEK, ChatbotConfig.CUSTOM));
    private final TextField modelField = new TextField();
    private final javafx.scene.control.Button browseBtn = new javafx.scene.control.Button("Browse…");
    private final PasswordField keyField = new PasswordField();
    private final TextField endpointField = new TextField();
    private final Spinner<Integer> historySpin = new Spinner<>(4, 80, 30, 2);
    private final CheckBox showIconCb = new CheckBox("Show the floating chat icon (bottom-right)");
    private final CheckBox smartRouteCb = new CheckBox("Smart tool routing (recommended — fewer tokens & requests)");
    private final Label status = new Label();

    public ChatbotSettingsPanel(StudioApp app, ChatbotConfig cfg) {
        this.app = app;
        this.cfg = cfg;
        setSpacing(12);
        setPadding(new Insets(4, 4, 8, 4));

        getChildren().addAll(
                heroCard(),
                providerCard(),
                behaviourCard(),
                statusCard());
        loadFromConfig();
        ChatbotConfig.addChangeListener(c -> javafx.application.Platform.runLater(this::loadFromConfig));
    }

    // ── Cards ─────────────────────────────────────────────────────────

    private VBox heroCard() {
        Label title = new Label("AI Chatbot");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #F2F4F8;");
        Label desc = new Label("A built-in assistant that chats with your live business data. It uses the same "
                + "tool access as the MCP server (read tools run directly; destructive operations always ask for "
                + "your approval in Settings → MCP Server). Bring your own API key — Gemini, OpenAI or "
                + "Claude-compatible endpoints are supported.");
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 12px; -fx-text-fill: #97A3B6;");
        VBox box = new VBox(6, title, desc);
        box.setStyle(CARD);
        return box;
    }

    private VBox providerCard() {
        Label head = sectionHead("PROVIDER & MODEL");

        providerCb.setMaxWidth(Double.MAX_VALUE);
        providerCb.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(String p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : AiChatClient.providerLabel(p));
            }
        });
        providerCb.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(String p, boolean empty) {
                super.updateItem(p, empty);
                setText(empty || p == null ? null : AiChatClient.providerLabel(p));
            }
        });
        providerCb.valueProperty().addListener((o, a, b) -> {
            if (b != null) {
                modelField.setPromptText(AiChatClient.defaultModel(b) + "  (default)");
                boolean customEndpoint = ChatbotConfig.CUSTOM.equals(b) || ChatbotConfig.OLLAMA.equals(b);
                endpointField.setPromptText(customEndpoint
                        ? (ChatbotConfig.OLLAMA.equals(b)
                            ? "Defaults to http://localhost:11434/v1 — override if Ollama runs elsewhere"
                            : "Required, e.g. https://your-host/v1/chat/completions")
                        : "Leave blank for the official endpoint (custom/proxied endpoints welcome)");
            }
        });

        modelField.setPromptText(AiChatClient.defaultModel(cfg.getProvider()) + "  (default)");
        styleField(modelField);
        keyField.setPromptText("Paste your API key — stored locally on this machine only");
        styleField(keyField);
        endpointField.setPromptText("Leave blank for the official endpoint (custom/proxied endpoints welcome)");
        styleField(endpointField);

        Label keyNote = new Label("The key never leaves this computer except to call your chosen provider directly. "
                + "Get a Gemini key free at aistudio.google.com/apikey.");
        keyNote.setWrapText(true);
        keyNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #7C8AA0;");

        Button testBtn = new Button("Test connection");
        testBtn.getStyleClass().addAll("button-sm", "button-secondary");
        testBtn.setOnAction(e -> runTest(testBtn));

        // "Browse" fetches the LIVE model catalogue from the provider API
        // (verified: GET /v1beta/models) into a pick list.
        browseBtn.getStyleClass().addAll("button-sm", "button-secondary");
        browseBtn.setTooltip(new javafx.scene.control.Tooltip("Fetch the live model list from your provider"));
        browseBtn.setOnAction(e -> showModelPicker());
        HBox modelRow = new HBox(8, modelField, browseBtn);
        HBox.setHgrow(modelField, javafx.scene.layout.Priority.ALWAYS);

        VBox box = new VBox(8, head,
                row("Provider", providerCb),
                row("Model", modelRow),
                row("API key", keyField),
                row("Endpoint", endpointField),
                keyNote, testBtn);
        box.setStyle(CARD);
        return box;
    }

    private VBox behaviourCard() {
        Label head = sectionHead("BEHAVIOUR");
        styleCheck(showIconCb);
        styleCheck(smartRouteCb);
        historySpin.setPrefWidth(90);
        Label histNote = new Label("Messages of history sent with each request (higher = better context, more tokens).");
        histNote.setWrapText(true);
        histNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #7C8AA0;");
        Label routeNote = new Label("Before each data question a tiny routing pass picks only the tools needed, "
                + "so big tool catalogues are not sent every time — chat-only messages skip them entirely.");
        routeNote.setWrapText(true);
        routeNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #7C8AA0;");
        VBox box = new VBox(8, head, showIconCb, smartRouteCb, routeNote,
                row("History window", historySpin), histNote);
        box.setStyle(CARD);
        return box;
    }

    private VBox statusCard() {
        status.setWrapText(true);
        status.setStyle("-fx-font-size: 12px; -fx-text-fill: #97A3B6;");

        Button save = new Button("Save settings");
        save.getStyleClass().addAll("button-sm", "button-primary");
        save.setOnAction(e -> save());

        HBox buttons = new HBox(8, save);
        buttons.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(8, status, buttons);
        box.setStyle(CARD);
        return box;
    }

    // ── Behaviour ─────────────────────────────────────────────────────

    private void loadFromConfig() {
        providerCb.setValue(cfg.getProvider());
        modelField.setText(cfg.getModel());
        keyField.setText(cfg.getApiKey());
        endpointField.setText(cfg.getEndpoint());
        historySpin.getValueFactory().setValue(cfg.getHistoryMessages());
        showIconCb.setSelected(cfg.isShowIcon());
        smartRouteCb.setSelected(cfg.isSmartRouting());
    }

    private void save() {
        if (providerCb.getValue() != null) cfg.setProvider(providerCb.getValue());
        cfg.setModel(modelField.getText());
        cfg.setApiKey(keyField.getText());
        cfg.setEndpoint(endpointField.getText());
        cfg.setHistoryMessages(historySpin.getValue());
        cfg.setShowIcon(showIconCb.isSelected());
        cfg.setSmartRouting(smartRouteCb.isSelected());
        cfg.save();
        if (app != null) {
            // Hand the SAME instance to the shell so the icon/panel follow
            // the saved values immediately (no stale-copy divergence).
            app.setChatbotConfig(cfg);
        }
        status.setStyle("-fx-font-size: 12px; -fx-text-fill: #16a34a;");
        status.setText("Saved. " + (cfg.getApiKey().isBlank()
                ? "Add an API key to start chatting."
                : "Chatbot ready — click the bubble icon (bottom-right) to chat."));
    }

    private void runTest(Button testBtn) {
        String provider = providerCb.getValue();
        String model = modelField.getText();
        String key = keyField.getText();
        String endpoint = endpointField.getText();
        if (key == null || key.isBlank()) {
            status.setStyle("-fx-font-size: 12px; -fx-text-fill: #dc2626;");
            status.setText("Enter an API key first.");
            return;
        }
        testBtn.setDisable(true);
        status.setStyle("-fx-font-size: 12px; -fx-text-fill: #97A3B6;");
        status.setText("Testing " + AiChatClient.providerLabel(provider) + "…");
        ChatbotConfig probe = new ChatbotConfig();
        probe.setProvider(provider);
        probe.setModel(model);
        probe.setApiKey(key);
        probe.setEndpoint(endpoint);
        Task<AiChatClient.ChatResult> task = new Task<>() {
            @Override protected AiChatClient.ChatResult call() throws Exception {
                return new AiChatClient().send(probe,
                        List.of(AiChatClient.ChatTurn.assistant("")), // minimal context
                        "Reply with exactly: OK", null);
            }
        };
        task.setOnSucceeded(e -> AppExecutors.runOnFx(() -> {
            testBtn.setDisable(false);
            status.setStyle("-fx-font-size: 12px; -fx-text-fill: #16a34a;");
            status.setText("Connected — model replied: " + task.getValue().text());
        }));
        task.setOnFailed(e -> AppExecutors.runOnFx(() -> {
            testBtn.setDisable(false);
            Throwable ex = task.getException();
            status.setStyle("-fx-font-size: 12px; -fx-text-fill: #dc2626;");
            status.setText("Failed: " + (ex == null ? "unknown error" : ex.getMessage()));
        }));
        AppExecutors.io().execute(task);
    }

    // ── Small builders ────────────────────────────────────────────────

    private Label sectionHead(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #D9A13B;");
        return l;
    }

    private HBox row(String labelText, javafx.scene.Node control) {
        Label l = new Label(labelText);
        l.setStyle("-fx-font-size: 12px; -fx-text-fill: #B9C4D6;");
        l.setMinWidth(110);
        HBox box = new HBox(10, l, control);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);
        return box;
    }

    private void styleField(javafx.scene.control.Control c) {
        c.setStyle("-fx-background-color: #0F1520; -fx-text-fill: #E6EAF0; -fx-prompt-text-fill: #5b6779;"
                + "-fx-background-radius: 6; -fx-border-color: #273245; -fx-border-radius: 6; -fx-border-width: 1;"
                + "-fx-padding: 6 10 6 10; -fx-font-size: 12px;");
    }

    private void styleCheck(CheckBox c) {
        c.setStyle("-fx-text-fill: #B9C4D6; -fx-font-size: 12px;");
    }

    /** Dialog listing chat-capable models fetched LIVE from the provider. */
    private void showModelPicker() {
        String provider = providerCb.getValue() == null ? cfg.getProvider() : providerCb.getValue();
        if (!ChatbotConfig.GEMINI.equals(provider)) {
            status.setText("Live catalogue is Gemini-only right now — type the model id for "
                    + AiChatClient.providerLabel(provider) + ".");
            return;
        }
        String key = keyField.getText() == null ? "" : keyField.getText().trim();
        if (key.isEmpty()) key = cfg.getApiKey();
        final String fkey = key;
        if (key.isEmpty()) {
            status.setText("Paste your API key first — the model list is fetched with it.");
            return;
        }
        status.setText("Loading model catalogue…");
        com.invoicestudio.service.AppExecutors.io().execute(() -> {
            final List<com.invoicestudio.service.ModelCatalog.ModelInfo> models;
            try {
                models = com.invoicestudio.service.ModelCatalog.chatModels(fkey);
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() ->
                        status.setText("Couldn't load catalogue: " + ex.getMessage()));
                return;
            }
            javafx.application.Platform.runLater(() -> {
                status.setText(models.size() + " chat-capable models found.");
                javafx.scene.control.ListView<com.invoicestudio.service.ModelCatalog.ModelInfo> lv =
                        new javafx.scene.control.ListView<>(javafx.collections.FXCollections
                                .observableArrayList(models));
                lv.setPrefHeight(360);
                lv.setCellFactory(v -> new javafx.scene.control.ListCell<>() {
                    @Override protected void updateItem(com.invoicestudio.service.ModelCatalog.ModelInfo mi,
                                                        boolean empty) {
                        super.updateItem(mi, empty);
                        if (empty || mi == null) { setText(null); setGraphic(null); return; }
                        String current = modelField.getText() == null ? "" : modelField.getText().trim();
                        String defaultM = AiChatClient.defaultModel(provider);
                        boolean active = mi.id().equals(current)
                                || (current.isEmpty() && mi.id().equals(defaultM));
                        setText((active ? "✓  " : "") + mi.displayName()
                                + "  —  " + mi.id()
                                + (mi.inputTokenLimit() > 0 ? "  ·  "
                                    + (mi.inputTokenLimit() / 1000) + "K context" : ""));
                        setStyle("-fx-text-fill: " + (active ? "#D9A13B" : "#C7D0DE") + ";"
                                + "-fx-font-size: 12px;");
                    }
                });
                javafx.scene.control.Button pick = new javafx.scene.control.Button("Use this model");
                pick.getStyleClass().addAll("button-sm", "button-primary");
                pick.setDisable(true);
                lv.getSelectionModel().selectedItemProperty().addListener((o, a, sel) ->
                        pick.setDisable(sel == null));
                pick.setOnAction(ev -> {
                    var sel = lv.getSelectionModel().getSelectedItem();
                    if (sel != null) {
                        modelField.setText(sel.id());
                        dialog.close();
                    }
                });
                javafx.scene.layout.VBox root = new javafx.scene.layout.VBox(10, lv, pick);
                root.setStyle("-fx-background-color: #10161F; -fx-padding: 12;");
                root.setPrefWidth(560);
                dialog.setTitle("Pick a model (live from " + AiChatClient.providerLabel(provider) + ")");
                dialog.getDialogPane().setContent(root);
                dialog.getDialogPane().getButtonTypes().setAll(javafx.scene.control.ButtonType.CLOSE);
                dialog.getDialogPane().setStyle("-fx-background-color: #10161F;");
                dialog.showAndWait();
            });
        });
    }

    private final javafx.scene.control.Dialog<Void> dialog = new javafx.scene.control.Dialog<>();
}
