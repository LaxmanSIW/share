package com.invoicestudio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression for the Gemini HTTP 400 ("Unknown name \"text\" at
 * 'system_instruction'"): the request wrappers must be OUTER objects, never
 * the bare inner text/content node produced by chained putArray().addObject().
 * Also locks the multi-provider routing table (9 providers).
 */
class AiChatClientPayloadTest {

    private static final ObjectMapper M = new ObjectMapper();

    private static ObjectNode geminiBody(AiChatClient c) throws Exception {
        Method contents = AiChatClient.class.getDeclaredMethod("geminiContents",
                ChatbotConfig.class, List.class);
        contents.setAccessible(true);
        Method tools = AiChatClient.class.getDeclaredMethod("geminiTools", java.util.Set.class);
        tools.setAccessible(true);
        Method sys = AiChatClient.class.getDeclaredMethod("sysInstruction");
        sys.setAccessible(true);

        ObjectNode body = M.createObjectNode();
        body.set("system_instruction", (JsonNode) sys.invoke(c));
        body.set("contents", (JsonNode) contents.invoke(c, new ChatbotConfig(),
                List.of(AiChatClient.ChatTurn.user("hi"))));
        body.set("tools", (JsonNode) tools.invoke(c, new Object[]{null}));
        return body;
    }

    @Test
    void geminiSystemInstructionIsWrappedInParts() throws Exception {
        JsonNode si = geminiBody(new AiChatClient()).get("system_instruction");
        // The exact shape Gemini rejected when broken: a bare "text" at top level.
        assertFalse(si.has("text"), "system_instruction must NOT be a bare text node");
        assertTrue(si.has("parts") && si.get("parts").isArray() && si.get("parts").size() == 1,
                "system_instruction must be { parts: [ … ] }");
        assertTrue(si.get("parts").get(0).has("text"));
    }

    @Test
    void geminiUserTurnShape() throws Exception {
        JsonNode contents = geminiBody(new AiChatClient()).get("contents");
        assertEquals(1, contents.size());
        assertEquals("user", contents.get(0).get("role").asText());
        assertTrue(contents.get(0).get("parts").get(0).has("text"));
    }

    @Test
    void geminiToolDeclarationsWrapped() throws Exception {
        JsonNode tools = geminiBody(new AiChatClient()).get("tools");
        assertTrue(tools.isArray() && tools.size() == 1);
        JsonNode decls = tools.get(0).get("functionDeclarations");
        assertTrue(decls.isArray() && decls.size() >= 60, "all MCP tools must be declared");
    }

    @Test
    void nineProvidersRoutedWithDefaults() {
        for (String p : new String[]{
                ChatbotConfig.GEMINI, ChatbotConfig.OPENAI, ChatbotConfig.ANTHROPIC,
                ChatbotConfig.OPENROUTER, ChatbotConfig.GROQ, ChatbotConfig.OLLAMA,
                ChatbotConfig.MISTRAL, ChatbotConfig.DEEPSEEK, ChatbotConfig.CUSTOM}) {
            assertNotNull(AiChatClient.providerLabel(p), p);
            assertNotNull(AiChatClient.defaultModel(p), p);
        }
        assertEquals("Google Gemini", AiChatClient.providerLabel(ChatbotConfig.GEMINI));
        assertEquals("OpenRouter (400+ models)", AiChatClient.providerLabel(ChatbotConfig.OPENROUTER));
        assertEquals("Ollama (local, free)", AiChatClient.providerLabel(ChatbotConfig.OLLAMA));
        assertEquals("llama-3.3-70b-versatile", AiChatClient.defaultModel(ChatbotConfig.GROQ));
        assertEquals("deepseek-chat", AiChatClient.defaultModel(ChatbotConfig.DEEPSEEK));
    }
}
