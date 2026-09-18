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
 * optional endpoint override and whether the floating chat icon is shown.</p>
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
}
