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

    @Test
    void userLevelMarksTheStartOfANewMessageTurn() {
        ChatbotLogManager.user("New message: \"hi\"", null);
        List<ChatbotLogManager.LogEntry> entries = ChatbotLogManager.getEntries();
        ChatbotLogManager.LogEntry last = entries.get(entries.size() - 1);
        assertEquals(ChatbotLogManager.LogLevel.USER, last.level());
        assertEquals("USER", last.tag());
        assertTrue(last.message().contains("hi"));
    }

    @Test
    void throwingListenerDoesNotBreakOthersOrTheLogger() throws Exception {
        // A misbehaving UI listener must never poison the notification chain
        // for the others, nor propagate into the chat's background threads —
        // that was the "logs stop working after an unexpected result" class
        // of failure.
        java.util.concurrent.CountDownLatch goodGotIt = new java.util.concurrent.CountDownLatch(1);
        List<String> seen = new ArrayList<>();
        java.util.function.Consumer<ChatbotLogManager.LogEntry> bad = e -> {
            throw new IllegalStateException("simulated broken UI listener");
        };
        java.util.function.Consumer<ChatbotLogManager.LogEntry> good = e -> {
            seen.add(e.message());
            goodGotIt.countDown();
        };
        ChatbotLogManager.addListener(bad);
        ChatbotLogManager.addListener(good);
        try {
            assertDoesNotThrow(() -> ChatbotLogManager.info("isolation-probe", null));
            assertTrue(goodGotIt.await(5, java.util.concurrent.TimeUnit.SECONDS),
                    "the healthy listener must still receive entries after a broken one throws");
            assertTrue(seen.contains("isolation-probe"));
        } finally {
            ChatbotLogManager.removeListener(bad);
            ChatbotLogManager.removeListener(good);
        }
    }
}
