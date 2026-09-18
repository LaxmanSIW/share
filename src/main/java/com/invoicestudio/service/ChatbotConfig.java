package com.invoicestudio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.invoicestudio.AppDirs;

import java.io.File;

/**
 * Persisted settings for the in-app AI chatbot (Settings → Chatbot).
 *
 * <p>Stored per-user as {@code chatbot.json} in the application data
 * directory (same pattern as {@code mcp-server.json}). Holds the provider
 * choice (Gemini / OpenAI / Anthropic-compatible), model id, API key,
 * optional endpoint override, whether the floating chat icon is shown,
 * the maximum tool-call rounds per send, and smart-routing toggle.</p>
 */
public final class ChatbotConfig {

    private static final String FILE_NAME = "chatbot.json";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** Provider ids — see {@link AiChatClient#defaultModel(String)}. */
    public static final String GEMINI = "gemini";
    public static final String OPENAI = "openai";
    public static final String ANTHROPIC = "anthropic";
    public static final String OPENROUTER = "openrouter";
    public static final String GROQ = "groq";
    public static final String OLLAMA = "ollama";
    public static final String MISTRAL = "mistral";
    public static final String DEEPSEEK = "deepseek";
    /** Z.ai GLM — OpenAI-compatible endpoint, free flash tier by default. */
    public static final String GLM = "glm";
    public static final String CUSTOM = "custom";

    /** Show the floating round chat icon on the main shell. */
    private boolean showIcon = true;
    /** Active provider id. */
    private String provider = GEMINI;
    /** Model id (free text — defaults per provider when blank). */
    private String model = "";
    /** API key for the chosen provider. */
    private String apiKey = "";
    /** Optional endpoint override (blank = provider default). */
    private String endpoint = "";
    /** How many past messages accompany each request (context window control). */
    private int historyMessages = 30;
    /** Smart tool routing: a cheap first pass decides if tools are needed at
     *  all, so chat-only messages never carry the full tool schemas. */
    private boolean smartRouting = true;
    /**
     * Maximum number of model↔tool round-trips allowed per single send.
     * Capped in both directions (1–20) by the getter. Default matches the
     * previous hard-coded {@code MAX_TOOL_ROUNDS = 6}.
     */
    private int maxToolCalls = 6;

    private static final java.util.List<java.util.function.Consumer<ChatbotConfig>> LISTENERS =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void addChangeListener(java.util.function.Consumer<ChatbotConfig> listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    public static void removeChangeListener(java.util.function.Consumer<ChatbotConfig> listener) {
        LISTENERS.remove(listener);
    }

    public void notifyChanged() {
        for (java.util.function.Consumer<ChatbotConfig> l : LISTENERS) {
            try {
                l.accept(this);
            } catch (Exception e) {
                AppLog.debug(e);
            }
        }
    }

    public static ChatbotConfig load() {
        try {
            File f = file();
            if (f.exists()) {
                return MAPPER.readValue(f, ChatbotConfig.class);
            }
        } catch (Exception ignored) {
            AppLog.debug(ignored);
        }
        ChatbotConfig cfg = new ChatbotConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            MAPPER.writeValue(file(), this);
        } catch (Exception ignored) {
            AppLog.debug(ignored);
        }
        notifyChanged();
    }

    private static File file() {
        return AppDirs.dataDir().resolve(FILE_NAME).toFile();
    }

    // --- getters / setters ---

    public boolean isShowIcon() { return showIcon; }
    public void setShowIcon(boolean showIcon) { this.showIcon = showIcon; }

    public String getProvider() { return provider == null || provider.isBlank() ? GEMINI : provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getModel() { return model == null ? "" : model.trim(); }
    public void setModel(String model) { this.model = model; }

    public String getApiKey() { return apiKey == null ? "" : apiKey.trim(); }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getEndpoint() { return endpoint == null ? "" : endpoint.trim(); }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public int getHistoryMessages() { return Math.max(4, Math.min(80, historyMessages)); }
    public void setHistoryMessages(int historyMessages) { this.historyMessages = historyMessages; }

    /** Smart tool routing (cheap router pass, fewer/leaner requests). */
    public boolean isSmartRouting() { return smartRouting; }
    public void setSmartRouting(boolean smartRouting) { this.smartRouting = smartRouting; }

    /** Max model↔tool round-trips per send (1–20). */
    public int getMaxToolCalls() { return Math.max(1, Math.min(20, maxToolCalls)); }
    public void setMaxToolCalls(int maxToolCalls) { this.maxToolCalls = maxToolCalls; }
}
