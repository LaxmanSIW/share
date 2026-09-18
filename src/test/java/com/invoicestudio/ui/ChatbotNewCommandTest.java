package com.invoicestudio.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the "/new" chat command parser introduced with the
 * context-reset feature (bare "/new" = instant local thread reset,
 * "/new &lt;question&gt;" = answer with EMPTY history, anything else = a
 * normal message). Runs without the JavaFX toolkit: parseNewCommand is
 * static and ChatbotPanel's static surface is string constants only.
 */
class ChatbotNewCommandTest {

    @Test
    void bareNewIsRecognizedAsThreadReset() {
        assertEquals("", ChatbotPanel.parseNewCommand("/new"));
        assertEquals("", ChatbotPanel.parseNewCommand("  /new  "));
        assertEquals("", ChatbotPanel.parseNewCommand("/NEW"));
    }

    @Test
    void newWithQuestionReturnsTheQuestionOnly() {
        assertEquals("what is GST?",
                ChatbotPanel.parseNewCommand("/new what is GST?"));
        assertEquals("explain the profitability report",
                ChatbotPanel.parseNewCommand("  /new   explain the profitability report"));
        assertEquals("hi", ChatbotPanel.parseNewCommand("/NEW\thi"));
    }

    @Test
    void nonCommandsReturnNull() {
        assertNull(ChatbotPanel.parseNewCommand("hello"));
        assertNull(ChatbotPanel.parseNewCommand("/newly created invoice"));
        assertNull(ChatbotPanel.parseNewCommand("please /new this"));
        assertNull(ChatbotPanel.parseNewCommand(""));
        assertNull(ChatbotPanel.parseNewCommand(null));
        assertNull(ChatbotPanel.parseNewCommand("/newx"));
    }
}
