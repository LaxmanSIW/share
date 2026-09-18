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

    @Test
    void prepareTurnsPreservesInFlightToolLoopEvenWithSmallHistoryLimit() {
        ChatbotConfig cfg = new ChatbotConfig();
        cfg.setHistoryMessages(4); // Small limit, matching user setting

        // Prior conversation
        AiChatClient.ChatTurn u1 = AiChatClient.ChatTurn.user("older question");
        AiChatClient.ChatTurn a1 = AiChatClient.ChatTurn.assistant("older answer");
        // Current interaction with 2 tool rounds (5 turns total for this interaction)
        AiChatClient.ChatTurn uCurrent = AiChatClient.ChatTurn.user("update HSN numbers");
        AiChatClient.ChatTurn call1 = new AiChatClient.ChatTurn("call", "[{\"name\":\"list_items\",\"args\":\"{}\",\"id\":\"c1\"}]", null);
        AiChatClient.ChatTurn tool1 = new AiChatClient.ChatTurn("tool", "[{\"name\":\"list_items\",\"id\":\"c1\",\"result\":\"[]\"}]", null);
        AiChatClient.ChatTurn call2 = new AiChatClient.ChatTurn("call", "[{\"name\":\"update_item\",\"args\":\"{\\\"id\\\":\\\"1\\\"}\",\"id\":\"c2\"}]", null);
        AiChatClient.ChatTurn tool2 = new AiChatClient.ChatTurn("tool", "[{\"name\":\"update_item\",\"id\":\"c2\",\"result\":\"{\\\"ok\\\":true}\"}]", null);

        List<AiChatClient.ChatTurn> allTurns = List.of(u1, a1, uCurrent, call1, tool1, call2, tool2);

        List<AiChatClient.ChatTurn> prepared = AiChatClient.prepareTurns(allTurns, cfg);

        // Crucial: uCurrent must NOT be dropped, and all in-flight calls/tools must be preserved
        assertTrue(prepared.contains(uCurrent), "Current user prompt must never be trimmed by history limit");
        assertTrue(prepared.contains(call1));
        assertTrue(prepared.contains(tool1));
        assertTrue(prepared.contains(call2));
        assertTrue(prepared.contains(tool2));
        assertEquals("user", prepared.get(0).role(), "First turn must always be a user turn");
    }

    @Test
    void geminiContentsStartsUserAndMergesConsecutiveRoles() throws Exception {
        ChatbotConfig cfg = new ChatbotConfig();
        cfg.setHistoryMessages(10);

        AiChatClient client = new AiChatClient();
        Method m = AiChatClient.class.getDeclaredMethod("geminiContents", ChatbotConfig.class, List.class);
        m.setAccessible(true);

        AiChatClient.ChatTurn u = AiChatClient.ChatTurn.user("update HSN");
        AiChatClient.ChatTurn call = new AiChatClient.ChatTurn("call", "[{\"name\":\"list_items\",\"args\":\"{}\",\"id\":\"c1\",\"tSig\":\"sig123\"}]", null);
        AiChatClient.ChatTurn tool = new AiChatClient.ChatTurn("tool", "[{\"name\":\"list_items\",\"id\":\"c1\",\"result\":\"[]\"}]", null);

        JsonNode contents = (JsonNode) m.invoke(client, cfg, List.of(u, call, tool));

        assertEquals(3, contents.size());
        assertEquals("user", contents.get(0).path("role").asText());
        assertEquals("model", contents.get(1).path("role").asText());
        assertEquals("user", contents.get(2).path("role").asText());

        // Verify functionCall round-trips id and tSig
        JsonNode fc = contents.get(1).path("parts").get(0).path("functionCall");
        assertEquals("list_items", fc.path("name").asText());
        assertEquals("c1", fc.path("id").asText());
        assertEquals("sig123", contents.get(1).path("parts").get(0).path("thoughtSignature").asText());

        // Verify functionResponse round-trips id
        JsonNode fr = contents.get(2).path("parts").get(0).path("functionResponse");
        assertEquals("list_items", fr.path("name").asText());
        assertEquals("c1", fr.path("id").asText());
    }

    @Test
    void geminiImageAttachmentProvidesFallbackPromptWhenTextEmpty() throws Exception {
        ChatbotConfig cfg = new ChatbotConfig();
        AiChatClient client = new AiChatClient();
        Method m = AiChatClient.class.getDeclaredMethod("geminiContents", ChatbotConfig.class, List.class);
        m.setAccessible(true);

        byte[] dummyImg = new byte[]{1, 2, 3, 4};
        AiChatClient.ImagePart part = new AiChatClient.ImagePart("image/png", dummyImg);
        AiChatClient.ChatTurn u = AiChatClient.ChatTurn.userImage("", part);

        JsonNode contents = (JsonNode) m.invoke(client, cfg, List.of(u));
        assertEquals(1, contents.size());
        JsonNode parts = contents.get(0).path("parts");
        assertEquals(2, parts.size(), "Should contain inline_data and a fallback text prompt");
        assertTrue(parts.get(0).has("inline_data"));
        assertEquals("image/png", parts.get(0).path("inline_data").path("mime_type").asText());
        assertTrue(parts.get(1).has("text"), "Must have text prompt for Gemini multimodal completeness");
        assertFalse(parts.get(1).path("text").asText().isBlank());
    }
}
