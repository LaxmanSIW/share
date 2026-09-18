package com.invoicestudio.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the multi-provider chat client's pure parts: provider defaults,
 * labels and config persistence. (The HTTP paths need live keys; the
 * tool-loop wiring is exercised through the app's MCP tests.)
 */
class ChatbotConfigTest {

    @Test
    void defaultsAreGeminiAndVisible() {
        ChatbotConfig cfg = new ChatbotConfig();
        assertEquals(ChatbotConfig.GEMINI, cfg.getProvider());
        assertTrue(cfg.isShowIcon());
        assertTrue(cfg.getApiKey().isEmpty());
    }

    @Test
    void historyWindowIsClamped() {
        ChatbotConfig cfg = new ChatbotConfig();
        cfg.setHistoryMessages(0);
        assertEquals(4, cfg.getHistoryMessages());
        cfg.setHistoryMessages(500);
        assertEquals(80, cfg.getHistoryMessages());
        cfg.setHistoryMessages(24);
        assertEquals(24, cfg.getHistoryMessages());
    }

    @Test
    void providerFallbackForBlankValues() {
        ChatbotConfig cfg = new ChatbotConfig();
        cfg.setProvider("  ");
        assertEquals(ChatbotConfig.GEMINI, cfg.getProvider());
    }

    @Test
    void jsonRoundTrip() throws Exception {
        ChatbotConfig cfg = new ChatbotConfig();
        cfg.setProvider(ChatbotConfig.OPENAI);
        cfg.setModel("gpt-4o-mini");
        cfg.setApiKey("sk-test");
        cfg.setShowIcon(false);
        cfg.setHistoryMessages(12);

        com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
        ChatbotConfig back = m.readValue(m.writeValueAsString(cfg), ChatbotConfig.class);
        assertEquals(ChatbotConfig.OPENAI, back.getProvider());
        assertEquals("gpt-4o-mini", back.getModel());
        assertEquals("sk-test", back.getApiKey());
        assertFalse(back.isShowIcon());
        assertEquals(12, back.getHistoryMessages());
    }

    @Test
    void providerDefaultsAndLabels() {
        assertEquals("gemini-flash-latest", AiChatClient.defaultModel(ChatbotConfig.GEMINI));
        assertEquals("gpt-4o-mini", AiChatClient.defaultModel(ChatbotConfig.OPENAI));
        assertEquals("claude-sonnet-4-5", AiChatClient.defaultModel(ChatbotConfig.ANTHROPIC));
        assertEquals("Google Gemini", AiChatClient.providerLabel(ChatbotConfig.GEMINI));
        assertEquals("OpenAI (GPT)", AiChatClient.providerLabel(ChatbotConfig.OPENAI));
        assertEquals("Anthropic (Claude)", AiChatClient.providerLabel(ChatbotConfig.ANTHROPIC));
    }

    /** The client must refuse to hit the network without a key — a clear
     *  setup error, never a mystery HTTP failure. */
    @Test
    void sendWithoutKeyFailsFast() {
        ChatbotConfig cfg = new ChatbotConfig();
        cfg.setApiKey("");
        Exception ex = assertThrows(IllegalStateException.class, () ->
                new AiChatClient().send(cfg, List.of(), "hi", null));
        assertTrue(ex.getMessage().contains("API key"));
    }
}
