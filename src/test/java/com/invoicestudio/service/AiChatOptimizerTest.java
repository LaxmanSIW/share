package com.invoicestudio.service;

import com.invoicestudio.mcp.McpToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the token/request optimizer: the router decision parser
 * (CHAT vs ROUTE lines, unknown-tool filtering, fail-open), the smalltalk
 * zero-cost skip, and the configured history window (regression: it was
 * hardcoded and the Settings slider had no effect).
 */
class AiChatOptimizerTest {

    @Test
    void chatDecisionExtractsAnswerAndNeedsNoTools() {
        AiChatClient.ToolRoute r = AiChatClient.parseRouteDecision(
                "CHAT: Hello! I'm your InvoiceStudio assistant.", null);
        assertFalse(r.needTools());
        assertEquals("Hello! I'm your InvoiceStudio assistant.", r.note());
    }

    @Test
    void routeDecisionKeepsOnlyKnownTools() {
        List<String> known = McpToolRegistry.tools().stream()
                .map(t -> t.name).limit(3).toList();
        String line = "ROUTE: " + String.join(", ", known) + ", not_a_real_tool, " + known.get(0);
        AiChatClient.ToolRoute r = AiChatClient.parseRouteDecision(line, null);
        assertTrue(r.needTools());
        assertEquals(known.size(), r.tools().size(), "duplicates and unknown names dropped");
        assertTrue(r.tools().containsAll(known));
    }

    @Test
    void emptyOrGarbageRouterOutputFailsOpenToFullCatalogue() {
        assertTrue(AiChatClient.parseRouteDecision("", null).needTools());
        assertTrue(AiChatClient.parseRouteDecision(null, null).needTools());
        AiChatClient.ToolRoute garbage = AiChatClient.parseRouteDecision(
                "I think maybe list_items but not sure sorry", null);
        assertTrue(garbage.needTools());
        assertTrue(garbage.tools().isEmpty(), "no usable shortlist → full catalogue");
    }

    @Test
    void routeWithNoKnownNamesMeansFullCatalogue() {
        AiChatClient.ToolRoute r = AiChatClient.parseRouteDecision("ROUTE: made_up_tool", null);
        assertTrue(r.needTools());
        assertTrue(r.tools().isEmpty(), "empty shortlist must mean 'all tools', not 'no tools'");
    }

    @Test
    void configuredHistoryWindowIsRespected() {
        ChatbotConfig cfg = new ChatbotConfig();
        cfg.setHistoryMessages(6);
        List<AiChatClient.ChatTurn> turns = new java.util.ArrayList<>();
        for (int i = 0; i < 20; i++) turns.add(AiChatClient.ChatTurn.user("msg" + i));
        List<AiChatClient.ChatTurn> prepared = AiChatClient.prepareTurns(turns, cfg);
        // 6 turns kept from prior history plus the 20th user turn
        assertEquals(7, prepared.size());
        assertEquals("msg13", prepared.get(0).text(), "keeps the tail of prior history");
        assertEquals("msg19", prepared.get(prepared.size() - 1).text(), "retains current turn");
    }

    @Test
    void confirmationContextRecognizesAffirmativeAndAssistantPrompts() {
        // Confirmation words
        assertTrue(AiChatClient.isConfirmationContext(List.of(AiChatClient.ChatTurn.user("yes")), "yes"));
        assertTrue(AiChatClient.isConfirmationContext(List.of(AiChatClient.ChatTurn.user("sure")), "sure"));
        assertTrue(AiChatClient.isConfirmationContext(List.of(AiChatClient.ChatTurn.user("ok")), "ok"));
        assertTrue(AiChatClient.isConfirmationContext(List.of(AiChatClient.ChatTurn.user("approve")), "approve"));
        assertTrue(AiChatClient.isConfirmationContext(List.of(AiChatClient.ChatTurn.user("proceed")), "proceed"));

        // Prior assistant asking for approval
        List<AiChatClient.ChatTurn> historyWithQuestion = List.of(
                AiChatClient.ChatTurn.user("delete bill 10"),
                AiChatClient.ChatTurn.assistant("Queued deletion for invoice 10 (operationId: op_123). Please confirm.")
        );
        assertTrue(AiChatClient.isConfirmationContext(historyWithQuestion, "do it"));
        assertTrue(AiChatClient.isConfirmationContext(historyWithQuestion, "yes please"));

        // Regular new questions without confirmation context
        assertFalse(AiChatClient.isConfirmationContext(List.of(), "what is our profit this month?"));
    }
}
