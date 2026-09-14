package com.invoicestudio.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Append-only audit trail of MCP operations (tool calls, results, confirmations).
 *
 * <p>Written both to memory (for the Settings → MCP Server tab) and to
 * {@code mcp-audit.log} in the app data directory so an owner can review
 * exactly what an AI assistant did to their books.</p>
 */
public final class McpAuditLog {

    private static final int MEMORY_LIMIT = 200;
    private static final Object LOCK = new Object();
    private static final Deque<String> ENTRIES = new ArrayDeque<>(MEMORY_LIMIT);

    private McpAuditLog() {}

    public static void log(String line) {
        String stamped = Instant.now() + "  " + line;
        synchronized (LOCK) {
            ENTRIES.addLast(stamped);
            while (ENTRIES.size() > MEMORY_LIMIT) ENTRIES.removeFirst();
        }
        appendToDisk(stamped);
    }

    /** Recent entries, oldest first. */
    public static List<String> recent() {
        synchronized (LOCK) {
            return new ArrayList<>(ENTRIES);
        }
    }

    private static void appendToDisk(String line) {
        try {
            Path log = AppDirsAccess.path();
            Files.createDirectories(log.getParent());
            Files.write(log, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | SecurityException ignored) {
            // audit-to-disk is best effort; the in-memory copy is authoritative for the UI
        }
    }

    /** Indirection so tests can run without touching the real app-data dir. */
    private static final class AppDirsAccess {
        static Path path() {
            return com.invoicestudio.AppDirs.dataDir().resolve("mcp-audit.log");
        }
    }
}
