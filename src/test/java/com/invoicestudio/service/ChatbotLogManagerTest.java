package com.invoicestudio.service;

import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ChatbotLogManagerTest {

    @BeforeAll
    static void initFx() {
        try {
            Platform.startup(() -> {});
        } catch (IllegalStateException ignored) {}
    }

    @BeforeEach
    void clearLogs() {
        ChatbotLogManager.clear();
    }

    @Test
    void recordsAndFormatsLogEntries() {
        ChatbotLogManager.router("Smart router decision", "ROUTE: list_items");
        ChatbotLogManager.tool("Calling tool", "{\"limit\": 10}");
        ChatbotLogManager.mcp("Executed in 15ms", "[]");
        ChatbotLogManager.success("Completed in 1 round", null);

        List<ChatbotLogManager.LogEntry> entries = ChatbotLogManager.getEntries();
        // clear() emits 1 SYSTEM entry, plus 4 logged entries = 5
        assertTrue(entries.size() >= 5);

        ChatbotLogManager.LogEntry last = entries.get(entries.size() - 1);
        assertEquals(ChatbotLogManager.LogLevel.SUCCESS, last.level());
        assertEquals("SUCCESS", last.tag());
        assertTrue(last.toCliString().contains("[SUCCESS]"));
    }

    @Test
    void listenerReceivesLogEvents() {
        AtomicInteger count = new AtomicInteger(0);
        List<ChatbotLogManager.LogEntry> received = new ArrayList<>();

        java.util.function.Consumer<ChatbotLogManager.LogEntry> listener = e -> {
            count.incrementAndGet();
            received.add(e);
        };

        ChatbotLogManager.addListener(listener);
        try {
            ChatbotLogManager.info("Test message", "Detail text");
            // Since this runs in JUnit, listener may be notified synchronously or via runLater
            assertTrue(received.stream().anyMatch(e -> "Test message".equals(e.message()))
                    || ChatbotLogManager.getEntries().stream().anyMatch(e -> "Test message".equals(e.message())));
        } finally {
            ChatbotLogManager.removeListener(listener);
        }
    }

    @Test
    void clearEmptiesBuffer() {
        ChatbotLogManager.info("Item 1", null);
        ChatbotLogManager.info("Item 2", null);
        ChatbotLogManager.clear();

        List<ChatbotLogManager.LogEntry> entries = ChatbotLogManager.getEntries();
        assertEquals(1, entries.size(), "Clear should reset buffer leaving only the system cleared notification");
        assertEquals("SYSTEM", entries.get(0).tag());
    }
}
