package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * An individual article in the Knowledge Hub documentation system.
 *
 * @param id        Unique identifier for the article
 * @param path      Slash-separated hierarchy path, e.g. "TSC / TA210 — Printer" or "Invoicing / GST Rules"
 * @param title     Display title of the article
 * @param subtitle  Short description or summary
 * @param markdown  Full Markdown body text
 * @param updatedAt Timestamp of last modification
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KnowledgeArticle(
    String id,
    String path,
    String title,
    String subtitle,
    String markdown,
    long updatedAt
) {
    public KnowledgeArticle {
        if (id == null || id.isBlank()) {
            id = "art_" + Long.toHexString(System.nanoTime());
        }
        if (path == null || path.isBlank()) {
            path = "General";
        }
        if (title == null || title.isBlank()) {
            title = "Untitled Article";
        }
        if (subtitle == null) {
            subtitle = "";
        }
        if (markdown == null) {
            markdown = "";
        }
    }

    public KnowledgeArticle withUpdates(String newPath, String newTitle, String newSubtitle, String newMarkdown) {
        return new KnowledgeArticle(id, newPath, newTitle, newSubtitle, newMarkdown, System.currentTimeMillis());
    }
}
