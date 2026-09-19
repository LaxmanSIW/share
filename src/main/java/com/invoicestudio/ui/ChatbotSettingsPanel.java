package com.invoicestudio.ui;

import com.invoicestudio.service.AiChatClient;
import com.invoicestudio.service.ApiKeysVault;
import com.invoicestudio.service.AppExecutors;
import com.invoicestudio.service.ChatbotConfig;
import com.invoicestudio.ui.chat.ChatbotModelPickerDialog;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
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
                    ChatbotConfig.GEMINI, ChatbotConfig.GLM, ChatbotConfig.OPENAI, ChatbotConfig.ANTHROPIC,
                    ChatbotConfig.OPENROUTER, ChatbotConfig.GROQ, ChatbotConfig.OLLAMA,
                    ChatbotConfig.MISTRAL, ChatbotConfig.DEEPSEEK, ChatbotConfig.CUSTOM));
    private final TextField modelField = new TextField();
    private final javafx.scene.control.Button browseBtn = new javafx.scene.control.Button("Browse…");
    private final PasswordField keyField = new PasswordField();
    private final TextField endpointField = new TextField();

    // API-key vault: stored keys + assignment dropdown (see vaultCard()).
    private final ComboBox<ApiKeysVault.VaultEntry> vaultCb = new ComboBox<>();
    private final TextField vaultLabelField = new TextField();
    private final javafx.scene.control.Button vaultSaveBtn = new javafx.scene.control.Button("Save current key");
    private final javafx.scene.control.Button vaultDeleteBtn = new javafx.scene.control.Button("Delete");
    private final Label vaultNote = new Label();
    /** Guards the vault combo listener while the list is being rebuilt. */
    private boolean syncingVault;

    private final Spinner<Integer> historySpin = new Spinner<>(4, 80, 12, 2);
    private final CheckBox showIconCb = new CheckBox("Show the floating chat icon (bottom-right)");
    private final CheckBox smartRouteCb = new CheckBox("Smart tool routing (recommended — fewer tokens & requests)");
    private final Spinner<Integer> maxToolSpin = new Spinner<>(1, 20, 6, 1);
    private final Label status = new Label();

    public ChatbotSettingsPanel(StudioApp app, ChatbotConfig cfg) {
        this.app = app;
        this.cfg = cfg;
        setSpacing(12);
        setPadding(new Insets(4, 4, 8, 4));

        getChildren().addAll(
                heroCard(),
                providerCard(),
                vaultCard(),
                behaviourCard(),
                statusCard());
        loadFromConfig();
        ChatbotConfig.addChangeListener(c -> javafx.application.Platform.runLater(this::loadFromConfig));
        ApiKeysVault.addChangeListener(l -> javafx.application.Platform.runLater(this::refreshVault));
    }

    // ── Cards ─────────────────────────────────────────────────────────

    private VBox heroCard() {
        Label title = new Label("AI Chatbot");
        title.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-text-fill: #F2F4F8;");
        Label desc = new Label("A built-in assistant that chats with your live business data. It uses the same "
                + "tool access as the MCP server (read tools run directly; destructive operations always ask for "
                + "your approval in Settings → MCP Server). Bring your own API key — Gemini, Z.ai GLM, OpenAI or "
                + "Claude-compatible endpoints are supported. A light, fast model is used by default so free "+
                "quotas last; pick a heavier model only when you need it.");
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
        providerCb.valueProperty().addListener((o, oldP, newP) -> {
            if (newP != null) {
                modelField.setPromptText(AiChatClient.defaultModel(newP) + "  (default)");
                boolean customEndpoint = ChatbotConfig.CUSTOM.equals(newP) || ChatbotConfig.OLLAMA.equals(newP);
                endpointField.setPromptText(customEndpoint
                        ? (ChatbotConfig.OLLAMA.equals(newP)
                            ? "Defaults to http://localhost:11434/v1 — override if Ollama runs elsewhere"
                            : "Required, e.g. https://your-host/v1/chat/completions")
                        : "Leave blank for the official endpoint (custom/proxied endpoints welcome)");
                // Vault assignment: switching providers fills the key saved for
                // that provider — no manual re-pasting per provider. Only on a
                // genuine switch (initial load must not clobber the saved key).
                if (oldP != null && !oldP.equals(newP)) {
                    autoFillKeyFromVault(newP);
                }
            }
        });

        modelField.setPromptText(AiChatClient.defaultModel(cfg.getProvider()) + "  (default)");
        styleField(modelField);
        keyField.setPromptText("Pick from the vault below or paste a new key — stored locally on this machine only");
        styleField(keyField);
        endpointField.setPromptText("Leave blank for the official endpoint (custom/proxied endpoints welcome)");
        styleField(endpointField);

        Label keyNote = new Label("The key never leaves this computer except to call your chosen provider directly. "
                + "Get a Gemini key free at aistudio.google.com/apikey, or a Z.ai GLM key at z.ai — its flash "
                + "tier is free.");
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

    // ── API Key Vault ──────────────────────────────────────────────

    private static final String KEY_GLYPH =
            "M12.65 10C11.83 7.67 9.61 6 7 6c-3.31 0-6 2.69-6 6s2.69 6 6 6 "
                    + "c2.61 0 4.83-1.67 5.65-4H17v4h4v-4h2v-4H12.65zM7 14c-1.1 0-2-.9-2-2 "
                    + "s.9-2 2-2 2 .9 2 2-.9 2-2 2z";

    /**
     * The vault card: every saved key in one dropdown. Selecting an entry
     * assigns it to the key field; saving/deleting happens immediately (the
     * vault is its own store — independent of the main Save settings). A
     * provider switch auto-fills the key saved for that provider.
     */
    private VBox vaultCard() {
        Label head = sectionHead("API KEY VAULT");
        Label desc = new Label("Save each provider key once, under a name — then assign keys with the dropdown "
                + "instead of re-pasting when you switch providers. Switching the provider above fills the key "
                + "saved for it automatically. Keys stay in api-vault.json on this machine only.");
        desc.setWrapText(true);
        desc.setStyle("-fx-font-size: 11px; -fx-text-fill: #7C8AA0;");

        vaultCb.setMaxWidth(Double.MAX_VALUE);
        vaultCb.setCellFactory(lv -> new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(ApiKeysVault.VaultEntry e, boolean empty) {
                super.updateItem(e, empty);
                setGraphic(empty || e == null ? null : vaultRow(e));
                setText(null);
            }
        });
        vaultCb.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override protected void updateItem(ApiKeysVault.VaultEntry e, boolean empty) {
                super.updateItem(e, empty);
                setGraphic(empty || e == null ? null : vaultRow(e));
                setText(null);
            }
        });
        vaultCb.setPromptText(vaultCb.getItems().isEmpty() ? "No keys in the vault yet — save one below…" : "Assign a key…");
        vaultCb.valueProperty().addListener((o, a, e) -> {
            if (syncingVault || e == null) return;
            // Assignment = fill the key field; applied on Save settings like a hand-typed key.
            keyField.setText(e.key());
            note(vaultNote, "Assigned “" + e.label() + "” — press Save settings to use it.", "#D9A13B");
        });

        vaultLabelField.setPromptText("Name this key — e.g. \"Gemini free tier\"");
        styleField(vaultLabelField);
        HBox.setHgrow(vaultLabelField, Priority.ALWAYS);

        vaultSaveBtn.getStyleClass().addAll("button-sm", "button-secondary");
        vaultSaveBtn.setTooltip(new javafx.scene.control.Tooltip("Store the key currently in the API key field"));
        vaultSaveBtn.setOnAction(e -> saveCurrentKeyToVault());

        vaultDeleteBtn.getStyleClass().add("button-sm");
        vaultDeleteBtn.setStyle("-fx-text-fill: #FCA5A5; -fx-background-color: #2A1720;"
                + "-fx-background-radius: 6; -fx-cursor: hand; -fx-padding: 4 10;");
        vaultDeleteBtn.setTooltip(new javafx.scene.control.Tooltip("Remove the selected key from the vault"));
        vaultDeleteBtn.setOnAction(e -> deleteSelectedVaultKey());

        note(vaultNote, "", "#7C8AA0");

        VBox box = new VBox(8, head, desc, vaultCb,
                new HBox(8, vaultLabelField, vaultSaveBtn, vaultDeleteBtn),
                vaultNote);
        box.setStyle(CARD);
        return box;
    }

    /** One vault row: gold key glyph, name, masked key, provider chip. */
    private HBox vaultRow(ApiKeysVault.VaultEntry e) {
        javafx.scene.shape.SVGPath key = new javafx.scene.shape.SVGPath();
        key.setContent(KEY_GLYPH);
        key.setFill(javafx.scene.paint.Color.web("#D9A13B"));
        key.setStyle("-fx-scale-x: 0.8; -fx-scale-y: 0.8;");

        Label name = new Label(e.label());
        name.setStyle("-fx-font-size: 12px; -fx-font-weight: bold; -fx-text-fill: #E6EAF0;");
        Label masked = new Label(ApiKeysVault.mask(e.key()));
        masked.setStyle("-fx-font-family: 'Consolas','Courier New',monospace; -fx-font-size: 10.5px; -fx-text-fill: #97A3B6;");
        VBox text = new VBox(1, name, masked);

        boolean forCurrentProvider = e.provider() != null
                && e.provider().equalsIgnoreCase(providerCb.getValue());
        Label prov = new Label(AiChatClient.providerLabel(e.provider()).toUpperCase()
                + (forCurrentProvider ? "  ●" : ""));
        prov.setStyle("-fx-font-size: 9.5px; -fx-font-weight: bold; -fx-text-fill: "
                + (forCurrentProvider ? "#D9A13B;" : "#7C8AA0;")
                + "-fx-background-color: rgba(217,161,59,0.10); -fx-background-radius: 999;"
                + "-fx-border-color: rgba(217,161,59,0.35); -fx-border-radius: 999; -fx-border-width: 1;"
                + "-fx-padding: 2 8;");

        Region spring = new Region();
        HBox.setHgrow(spring, Priority.ALWAYS);
        HBox row = new HBox(9, key, text, spring, prov);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setStyle("-fx-background-color: transparent;");
        return row;
    }

    /** Rebuilds the vault dropdown; keys for the current provider first. */
    private void refreshVault() {
        syncingVault = true;
        try {
            ApiKeysVault.VaultEntry keep = vaultCb.getValue();
            String currentKey = keyField.getText() == null ? "" : keyField.getText().trim();
            String provider = providerCb.getValue();

            List<ApiKeysVault.VaultEntry> all = new ArrayList<>(ApiKeysVault.list());
            all.sort((x, y) -> {
                boolean xp = x.provider() != null && x.provider().equalsIgnoreCase(provider);
                boolean yp = y.provider() != null && y.provider().equalsIgnoreCase(provider);
                if (xp != yp) return xp ? -1 : 1;           // current provider first
                return x.label().compareToIgnoreCase(y.label());
            });
            vaultCb.setItems(javafx.collections.FXCollections.observableArrayList(all));
            vaultCb.setPromptText(all.isEmpty()
                    ? "No keys in the vault yet — save one below…"
                    : (provider == null ? "Assign a key…"
                        : "Assign a key…  (" + all.stream().filter(e -> e.provider() != null
                            && e.provider().equalsIgnoreCase(provider)).count() + " for "
                            + AiChatClient.providerLabel(provider) + ")"));

            // Re-select: previously chosen entry, else the key already in use.
            ApiKeysVault.VaultEntry match = null;
            if (keep != null) {
                for (ApiKeysVault.VaultEntry e : all) {
                    if (e.id().equals(keep.id())) { match = e; break; }
                }
            }
            if (match == null && !currentKey.isBlank()) {
                for (ApiKeysVault.VaultEntry e : all) {
                    if (e.key().equals(currentKey)) { match = e; break; }
                }
            }
            vaultCb.setValue(match);
        } finally {
            syncingVault = false;
        }
    }

    /** Provider switched → fill the key saved for it (the no-repaste promise). */
    private void autoFillKeyFromVault(String provider) {
        java.util.Optional<ApiKeysVault.VaultEntry> entry = ApiKeysVault.findForProvider(provider);
        String current = keyField.getText() == null ? "" : keyField.getText().trim();
        if (entry.isPresent()) {
            if (!entry.get().key().equals(current)) {
                keyField.setText(entry.get().key());
                note(vaultNote, "Provider switched — key “" + entry.get().label()
                        + "” filled from the vault. Press Save settings to apply.", "#D9A13B");
            }
        } else {
            note(vaultNote, "No vault key for " + AiChatClient.providerLabel(provider)
                    + " yet — paste one and press \"Save current key\".", "#7C8AA0");
        }
    }

    private void saveCurrentKeyToVault() {
        String key = keyField.getText() == null ? "" : keyField.getText().trim();
        if (key.isBlank()) {
            note(vaultNote, "Type or paste the key in the API key field first — nothing to save.", "#dc2626");
            return;
        }
        String provider = providerCb.getValue() == null ? cfg.getProvider() : providerCb.getValue();
        String label = vaultLabelField.getText();
        ApiKeysVault.VaultEntry entry = ApiKeysVault.add(label, provider, key);
        refreshVault();
        vaultCb.setValue(entry);
        vaultLabelField.clear();
        note(vaultNote, "Saved “" + entry.label() + "” to the vault (" + AiChatClient.providerLabel(provider) + ").", "#16a34a");
    }

    private void deleteSelectedVaultKey() {
        ApiKeysVault.VaultEntry sel = vaultCb.getValue();
        if (sel == null) {
            note(vaultNote, "Pick a key from the dropdown to delete it.", "#dc2626");
            return;
        }
        ApiKeysVault.remove(sel.id());
        refreshVault();
        note(vaultNote, "Removed “" + sel.label() + "” from the vault. The API key field was left untouched.", "#16a34a");
    }

    private static void note(Label l, String text, String colorHex) {
        l.setText(text);
        l.setStyle("-fx-font-size: 11px; -fx-text-fill: " + colorHex + ";");
        l.setWrapText(true);
    }

    private VBox behaviourCard() {
        Label head = sectionHead("BEHAVIOUR");
        styleCheck(showIconCb);
        styleCheck(smartRouteCb);
        historySpin.setPrefWidth(90);
        maxToolSpin.setPrefWidth(90);
        maxToolSpin.setEditable(true);
        Label histNote = new Label("Messages of history sent with each request (higher = better context, more tokens).");
        histNote.setWrapText(true);
        histNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #7C8AA0;");
        Label routeNote = new Label("Before each data question a tiny routing pass picks only the tools needed, "
                + "so big tool catalogues are not sent every time — chat-only messages skip them entirely.");
        routeNote.setWrapText(true);
        routeNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #7C8AA0;");
        Label toolNote = new Label(
                "Maximum model\u2194tool round-trips per single request. Lower = cheaper / faster; raise for complex "
                + "multi-step tasks. Round-trips spent only on switching models after a daily-quota failover or "
                + "re-planning after a router escalation are FREE — they never consume this budget. The assistant "
                + "will tell you if it hits this limit and ask you to continue.");
        toolNote.setWrapText(true);
        toolNote.setStyle("-fx-font-size: 11px; -fx-text-fill: #7C8AA0;");
        VBox box = new VBox(8, head, showIconCb, smartRouteCb, routeNote,
                row("History window", historySpin), histNote,
                row("Max tool rounds", maxToolSpin), toolNote);
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
        maxToolSpin.getValueFactory().setValue(cfg.getMaxToolCalls());
        showIconCb.setSelected(cfg.isShowIcon());
        smartRouteCb.setSelected(cfg.isSmartRouting());
        // Vault dropdown tracks the key actually in use (gold ● marks its provider).
        refreshVault();
    }

    private void save() {
        if (providerCb.getValue() != null) cfg.setProvider(providerCb.getValue());
        cfg.setModel(modelField.getText());
        cfg.setApiKey(keyField.getText());
        cfg.setEndpoint(endpointField.getText());
        cfg.setHistoryMessages(historySpin.getValue());
        cfg.setMaxToolCalls(maxToolSpin.getValue());
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
        AppExecutors.chat().execute(task);
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
        if (!com.invoicestudio.service.ModelCatalog.hasLiveCatalogue(provider)) {
            status.setText("Live catalogue is Gemini- and Z.ai GLM-only right now — type the model id for "
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
                models = com.invoicestudio.service.ModelCatalog.modelsFor(provider, fkey);
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() ->
                        status.setText("Couldn't load catalogue: " + ex.getMessage()));
                return;
            }
            javafx.application.Platform.runLater(() -> {
                status.setText(models.size() + " chat-capable models found.");
                String curModel = modelField.getText() == null ? "" : modelField.getText().trim();
                ChatbotModelPickerDialog picker = new ChatbotModelPickerDialog(
                        app != null ? app.getPrimaryStage() : null,
                        provider,
                        models,
                        curModel,
                        sel -> modelField.setText(sel.id())
                );
                picker.showAndWait();
            });
        });
    }
}
