package com.invoicestudio.service;

import javafx.application.Platform;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe real-time log buffer for AI chatbot background execution steps.
 * Captures smart routing decisions, provider requests, tool dispatches,
 * local MCP tool results, execution durations, and error diagnostics.
 *
 * <p>All listener notifications are guaranteed to be delivered on the
 * JavaFX Application Thread via {@link Platform#runLater}.</p>
 */
public final class ChatbotLogManager {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final int MAX_ENTRIES = 500;

    public enum LogLevel {
        INFO,
        ROUTER,
        DISPATCH,
        HTTP,
        TOOL,
        MCP,
        TOKENS,
        SUCCESS,
        WARN,
        ERROR
    }

    public record LogEntry(
            String timestamp,
            LogLevel level,
            String tag,
            String message,
            String details
    ) {
        public String toCliString() {
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(timestamp).append("] ");
            sb.append("[").append(tag).append("] ");
            sb.append(message);
            if (details != null && !details.isBlank()) {
                sb.append("\n    ").append(details.replace("\n", "\n    "));
            }
            return sb.toString();
        }
    }

    private static final List<LogEntry> ENTRIES = Collections.synchronizedList(new ArrayList<>());
    private static final List<Consumer<LogEntry>> LISTENERS = new CopyOnWriteArrayList<>();

    private ChatbotLogManager() {}

    /** Emits a structured log entry and notifies listeners. Safe to call from any thread. */
    public static void log(LogLevel level, String tag, String message, String details) {
        String time = LocalTime.now().format(TIME_FMT);
        LogEntry entry = new LogEntry(time, level, tag, message, details);

        synchronized (ENTRIES) {
            if (ENTRIES.size() >= MAX_ENTRIES) {
                ENTRIES.remove(0);
            }
            ENTRIES.add(entry);
        }

        for (Consumer<LogEntry> listener : LISTENERS) {
            if (Platform.isFxApplicationThread()) {
                listener.accept(entry);
            } else {
                Platform.runLater(() -> listener.accept(entry));
            }
        }
    }

    // Convenience logging helpers
    public static void router(String message, String details) {
        log(LogLevel.ROUTER, "ROUTER", message, details);
    }

    public static void dispatch(String message, String details) {
        log(LogLevel.DISPATCH, "DISPATCH", message, details);
    }

    /** One line per real provider HTTP request: status, latency, payload size. */
    public static void http(String message, String details) {
        log(LogLevel.HTTP, "HTTP", message, details);
    }

    /** Compact token-usage trace (↑prompt ↓completion), per stage of a send. */
    public static void usage(String message, String details) {
        log(LogLevel.TOKENS, "TOKENS", message, details);
    }

    public static void tool(String message, String details) {
        log(LogLevel.TOOL, "TOOL-CALL", message, details);
    }

    public static void mcp(String message, String details) {
        log(LogLevel.MCP, "MCP-EXEC", message, details);
    }

    public static void success(String message, String details) {
        log(LogLevel.SUCCESS, "SUCCESS", message, details);
    }

    public static void warn(String message, String details) {
        log(LogLevel.WARN, "WARN", message, details);
    }

    public static void error(String message, String details) {
        log(LogLevel.ERROR, "ERROR", message, details);
    }

    public static void info(String message, String details) {
        log(LogLevel.INFO, "INFO", message, details);
    }

    /** Returns an unmodifiable snapshot of current entries. Safe to call from any thread. */
    public static List<LogEntry> getEntries() {
        synchronized (ENTRIES) {
            return new ArrayList<>(ENTRIES);
        }
    }

    /** Registers a listener that receives each newly logged entry on the JavaFX thread. */
    public static void addListener(Consumer<LogEntry> listener) {
        if (listener != null && !LISTENERS.contains(listener)) {
            LISTENERS.add(listener);
        }
    }

    /** Unregisters a log listener. */
    public static void removeListener(Consumer<LogEntry> listener) {
        LISTENERS.remove(listener);
    }

    /** Clears all logged entries (invoked when chatbot is closed or cleared). */
    public static void clear() {
        synchronized (ENTRIES) {
            ENTRIES.clear();
        }
        log(LogLevel.INFO, "SYSTEM", "Session execution logs cleared", null);
    }
}
