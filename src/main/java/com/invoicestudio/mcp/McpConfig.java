package com.invoicestudio.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.invoicestudio.AppDirs;

import java.io.File;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Persisted configuration for the InvoiceStudio MCP server.
 *
 * <p>Stored per-user in the application data directory as
 * {@code mcp-server.json}. The auth token is generated once (or on demand
 * via "Regenerate") and is required as a Bearer token on every MCP call.</p>
 */
public final class McpConfig {

    private static final String FILE_NAME = "mcp-server.json";
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private int port = 7800;
    private boolean autoStart = false;
    private boolean requireToken = true;
    private String token = "";

    public static McpConfig load() {
        try {
            File f = file();
            if (f.exists()) {
                return MAPPER.readValue(f, McpConfig.class);
            }
        } catch (Exception ignored) {
            // fall through to defaults
        }
        McpConfig cfg = new McpConfig();
        cfg.save();
        return cfg;
    }

    public void save() {
        try {
            MAPPER.writeValue(file(), this);
        } catch (Exception ignored) {
            // config persistence is best-effort; server still runs with in-memory values
        }
    }

    private static File file() {
        return AppDirs.dataDir().resolve(FILE_NAME).toFile();
    }

    /** Generates a new 32-character URL-safe random token. */
    public static String generateToken() {
        byte[] raw = new byte[24];
        new SecureRandom().nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = Math.max(1024, Math.min(65535, port)); }

    public boolean isAutoStart() { return autoStart; }
    public void setAutoStart(boolean autoStart) { this.autoStart = autoStart; }

    public boolean isRequireToken() { return requireToken; }
    public void setRequireToken(boolean requireToken) { this.requireToken = requireToken; }

    public String getToken() { return token != null ? token : ""; }
    public void setToken(String token) { this.token = token != null ? token : ""; }
}
