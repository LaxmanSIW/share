package com.invoicestudio.mcp;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Loads the embedded docs (APP_GUIDE.md / MCP_SERVER.md) from app resources. */
public final class GuideContent {

    private static final String GUIDE = "/docs/APP_GUIDE.md";
    private static final String MCP_DOCS = "/docs/MCP_SERVER.md";

    private GuideContent() {}

    /** Full application manual (what the app is, every feature, how to use it). */
    public static String appGuide() {
        return read(GUIDE);
    }

    /** MCP server documentation (protocol, tools, safety model). */
    public static String mcpDocs() {
        return read(MCP_DOCS);
    }

    private static String read(String resource) {
        try (InputStream in = GuideContent.class.getResourceAsStream(resource)) {
            if (in == null) return "Documentation resource missing: " + resource;
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "Failed to load " + resource + ": " + e.getMessage();
        }
    }
}
