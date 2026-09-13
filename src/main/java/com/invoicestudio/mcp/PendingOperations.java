package com.invoicestudio.mcp;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pending destructive operations awaiting user confirmation in the UI.
 *
 * <p>Flow: an AI calls a mutating tool → the call does NOT execute; instead a
 * {@link PendingOp} is stored and a confirmation banner appears in the
 * Settings → MCP Server tab showing exactly what will happen. The user
 * clicks Approve (or the AI calls {@code confirm_operation} with the token)
 * → the operation executes. Reject/Deny/expire → discarded.</p>
 */
public final class PendingOperations {

    /** One queued destructive operation. */
    public static final class PendingOp {
        private final String id;
        private final String tool;
        private final String summary;
        private final String detail;
        private final Map<String, Object> args;
        private final Runnable action;
        private final Instant createdAt = Instant.now();

        PendingOp(String id, String tool, String summary, String detail,
                  Map<String, Object> args, Runnable action) {
            this.id = id;
            this.tool = tool;
            this.summary = summary;
            this.detail = detail;
            this.args = args;
            this.action = action;
        }

        public String getId() { return id; }
        public String getTool() { return tool; }
        public String getSummary() { return summary; }
        public String getDetail() { return detail; }
        public Map<String, Object> getArgs() { return args; }
        public Instant getCreatedAt() { return createdAt; }

        /** Executes the queued action. */
        public void approve() { action.run(); }
    }

    private static final Map<String, PendingOp> PENDING = new ConcurrentHashMap<>();
    private static final Object UI_LOCK = new Object();

    private PendingOperations() {}

    /** Queues an operation and notifies the JavaFX listener. Returns its id. */
    public static String queue(String tool, String summary, String detail,
                               Map<String, Object> args, Runnable action) {
        String id = "op_" + Long.toHexString(System.currentTimeMillis()) + "_" + (SEQ++);
        PendingOp op = new PendingOp(id, tool, summary, detail, args, action);
        PENDING.put(id, op);
        McpAuditLog.log("[CONFIRM-REQUESTED] " + tool + " — " + summary + " (id=" + id + ")");
        McpServer.runOnFxThread(() -> {
            synchronized (UI_LOCK) {
                if (listener != null) listener.onPendingChanged();
            }
        });
        return id;
    }

    /** Approves and executes. Returns false if unknown/expired. */
    public static boolean approve(String id) {
        PendingOp op = PENDING.remove(id);
        if (op == null) return false;
        McpAuditLog.log("[CONFIRMED] " + op.getTool() + " (id=" + id + ") — executing");
        op.approve();
        notifyUi();
        return true;
    }

    /** Discards without executing. Returns false if unknown/expired. */
    public static boolean reject(String id) {
        PendingOp op = PENDING.remove(id);
        if (op == null) return false;
        McpAuditLog.log("[REJECTED] " + op.getTool() + " (id=" + id + ")");
        notifyUi();
        return true;
    }

    /** Snapshot of pending operations (for the Settings tab). */
    public static List<PendingOp> pending() {
        return List.copyOf(PENDING.values());
    }

    /** Drops everything without executing (e.g. server stop). */
    public static void clearAll() {
        PENDING.clear();
        notifyUi();
    }

    // ---- UI listener wiring ----

    /** Implemented by the Settings tab to refresh its confirmation banner. */
    public interface UiListener { void onPendingChanged(); }

    private static UiListener listener;
    private static long SEQ = 0;

    public static void setUiListener(UiListener l) {
        synchronized (UI_LOCK) {
            listener = l;
        }
    }

    private static void notifyUi() {
        McpServer.runOnFxThread(() -> {
            synchronized (UI_LOCK) {
                if (listener != null) listener.onPendingChanged();
            }
        });
    }
}
