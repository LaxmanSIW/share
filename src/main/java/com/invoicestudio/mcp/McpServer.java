package com.invoicestudio.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javafx.application.Platform;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The InvoiceStudio MCP server: a localhost-only JSON-RPC 2.0 endpoint that
 * speaks the Model Context Protocol over streamable HTTP.
 *
 * <p>Lifecycle: started/stopped from Settings → MCP Server; auto-stopped on
 * app close via {@link #shutdown()} (called from StudioApp.stop()). Binds to
 * 127.0.0.1 only — the machine's external interfaces are never exposed.</p>
 *
 * <p>Protocol: POST /mcp with {@code initialize}, {@code tools/list},
 * {@code tools/call} (see MCP_SERVER.md). Every request must carry
 * {@code Authorization: Bearer <token>} when a token is configured.</p>
 */
public final class McpServer {

    public static final String SERVER_NAME = "InvoiceStudio MCP";
    public static final String PROTOCOL_VERSION = "2024-11-05";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final List<Consumer<String>> STATUS_LISTENERS = new CopyOnWriteArrayList<>();

    private static volatile HttpServer server;
    private static volatile int boundPort = -1;
    private static volatile String boundToken = "";

    private McpServer() {}

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /** Starts the server on the configured port. Returns an error message on failure, null on success. */
    public static synchronized String start(McpConfig config) {
        if (RUNNING.get()) stop();
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress("127.0.0.1", config.getPort()), 0);
            http.createContext("/mcp", exchange -> handle(exchange, config));
            http.setExecutor(Executors.newFixedThreadPool(4));
            http.start();

            server = http;
            boundPort = config.getPort();
            boundToken = config.getToken();
            RUNNING.set(true);
            McpAuditLog.log("[SERVER] started on http://127.0.0.1:" + boundPort + "/mcp");
            notifyStatus();
            return null;
        } catch (Exception e) {
            McpAuditLog.log("[SERVER] start FAILED on port " + config.getPort() + ": " + e.getMessage());
            return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
    }

    /** Stops the server and discards any un-approved pending operations. */
    public static synchronized void stop() {
        HttpServer http = server;
        server = null;
        RUNNING.set(false);
        boundPort = -1;
        if (http != null) {
            http.stop(0);
        }
        PendingOperations.clearAll();
        McpAuditLog.log("[SERVER] stopped");
        notifyStatus();
    }

    /** Called from StudioApp.stop() — guarantees the server dies with the app. */
    public static void shutdown() {
        if (RUNNING.get()) stop();
    }

    public static boolean isRunning() {
        return RUNNING.get();
    }

    public static int port() {
        return boundPort;
    }

    /** Human-readable endpoint URL while running (for the Settings tab). */
    public static String endpointUrl() {
        return RUNNING.get() ? "http://127.0.0.1:" + boundPort + "/mcp" : "";
    }

    /** Status payload for the server_status tool and the Settings tab. */
    public static Map<String, Object> statusMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("server", SERVER_NAME);
        m.put("running", RUNNING.get());
        m.put("endpoint", endpointUrl());
        m.put("protocolVersion", PROTOCOL_VERSION);
        m.put("toolCount", McpToolRegistry.tools().size());
        m.put("pendingConfirmations", PendingOperations.pending().size());
        return m;
    }

    /** Subscribes to running-state changes (Settings tab status pill). */
    public static void addStatusListener(Consumer<String> l) {
        STATUS_LISTENERS.add(l);
    }

    private static void notifyStatus() {
        String url = endpointUrl();
        for (Consumer<String> l : STATUS_LISTENERS) {
            runOnFxThread(() -> {
                try {
                    l.accept(url);
                } catch (Exception ignored) {}
            });
        }
    }

    /** Runs on the FX thread; silently no-ops when no JavaFX toolkit exists (tests/headless). */
    static void runOnFxThread(Runnable r) {
        try {
            Platform.runLater(r);
        } catch (IllegalStateException noToolkit) {
            // headless JVM (unit tests) — UI notifications are not applicable
        }
    }

    // ------------------------------------------------------------------
    // HTTP handling
    // ------------------------------------------------------------------

    private static void handle(HttpExchange exchange, McpConfig config) throws IOException {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, MAPPER.writeValueAsString(Map.of("error", "POST only")));
                return;
            }

            // ---- Auth ----
            if (config.isRequireToken()) {
                String auth = exchange.getRequestHeaders().getFirst("Authorization");
                String expected = "Bearer " + config.getToken();
                if (auth == null || !constantTimeEquals(auth, expected) || config.getToken().isBlank()) {
                    McpAuditLog.log("[AUTH] rejected request (bad or missing bearer token)");
                    sendJson(exchange, 401, rpcError(null, -32001, "Unauthorized: missing or invalid bearer token"));
                    return;
                }
            }

            // ---- Parse JSON-RPC ----
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            Map<?, ?> req;
            try {
                req = MAPPER.readValue(body, Map.class);
            } catch (Exception e) {
                sendJson(exchange, 400, rpcError(null, -32700, "Parse error"));
                return;
            }

            Object id = req.get("id");
            String method = req.get("method") != null ? String.valueOf(req.get("method")) : "";
            Map<String, Object> params = castMap(req.get("params"));

            Object result;
            switch (method) {
                case "initialize" -> {
                    Map<String, Object> info = new LinkedHashMap<>();
                    info.put("protocolVersion", PROTOCOL_VERSION);
                    info.put("capabilities", Map.of("tools", Map.of("listChanged", false)));
                    info.put("serverInfo", Map.of("name", SERVER_NAME, "version", appVersion()));
                    result = info;
                }
                case "notifications/initialized", "ping" -> result = Map.of();
                case "tools/list" -> {
                    List<Map<String, Object>> tools = new java.util.ArrayList<>();
                    for (McpToolRegistry.ToolDef t : McpToolRegistry.tools()) {
                        Map<String, Object> tm = new LinkedHashMap<>();
                        tm.put("name", t.name);
                        tm.put("description", (t.destructive ? "[CONFIRMATION REQUIRED] " : "")
                                + (t.mutates ? "[WRITES] " : "") + t.description);
                        tm.put("inputSchema", t.inputSchema);
                        tools.add(tm);
                    }
                    result = Map.of("tools", tools);
                }
                case "tools/call" -> {
                    String name = String.valueOf(params.getOrDefault("name", ""));
                    Map<String, Object> args = castMap(params.get("arguments"));
                    McpAuditLog.log("[TOOL] " + name + " " + abbreviate(args));
                    try {
                        Object out = McpToolRegistry.call(name, args);
                        if (out instanceof McpImageResult img) {
                            // Native MCP image content block (vision clients see the PNG) + text meta.
                            Map<String, Object> imageBlock = Map.of(
                                    "type", "image",
                                    "data", img.base64(),
                                    "mimeType", img.getMimeType());
                            String metaJson = img.getMeta() != null
                                    ? MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(img.getMeta())
                                    : "{}";
                            Map<String, Object> textBlock = Map.of("type", "text", "text", metaJson);
                            result = Map.of("content", List.of(imageBlock, textBlock));
                        } else {
                            result = Map.of("content", List.of(Map.of("type", "text", "text",
                                    out instanceof String s ? s : MAPPER.writerWithDefaultPrettyPrinter()
                                            .writeValueAsString(out))));
                        }
                    } catch (IllegalArgumentException e) {
                        McpAuditLog.log("[TOOL-ERROR] " + name + ": " + e.getMessage());
                        sendJson(exchange, 200, rpcError(id, -32002, e.getMessage()));
                        return;
                    } catch (Exception e) {
                        McpAuditLog.log("[TOOL-ERROR] " + name + ": " + e);
                        sendJson(exchange, 200, rpcError(id, -32003, "Tool failed: " + e.getMessage()));
                        return;
                    }
                }
                default -> {
                    sendJson(exchange, 200, rpcError(id, -32601, "Method not found: " + method));
                    return;
                }
            }

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("jsonrpc", "2.0");
            resp.put("id", id);
            resp.put("result", result);
            sendJson(exchange, 200, MAPPER.writeValueAsString(resp));

        } catch (Exception e) {
            try {
                sendJson(exchange, 500, rpcError(null, -32603, "Internal error: " + e.getMessage()));
            } catch (IOException ignored) {}
        } finally {
            exchange.close();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        return new LinkedHashMap<>();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }

    private static String rpcError(Object id, int code, String message) {
        try {
            Map<String, Object> err = Map.of("code", code, "message", message == null ? "" : message);
            return MAPPER.writeValueAsString(Map.of("jsonrpc", "2.0", "id", id == null ? "" : id, "error", err));
        } catch (Exception e) {
            return "{\"error\":{\"code\":" + code + "}}";
        }
    }

    private static String abbreviate(Object o) {
        String s = String.valueOf(o);
        return s.length() > 160 ? s.substring(0, 157) + "..." : s;
    }

    private static String appVersion() {
        try {
            Package p = McpServer.class.getPackage();
            String v = p != null ? p.getImplementationVersion() : null;
            return v != null ? v : "1.0.0";
        } catch (Exception e) {
            return "1.0.0";
        }
    }
}
