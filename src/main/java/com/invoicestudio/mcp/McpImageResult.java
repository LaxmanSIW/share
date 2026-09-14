package com.invoicestudio.mcp;

import java.util.Map;

/**
 * A tool result that carries a binary image alongside structured metadata.
 *
 * <p>When a tool returns this type, the MCP server emits a native
 * {@code {type:"image", data, mimeType}} content block (so vision-capable
 * AI clients can see the picture directly) followed by a text block with
 * the metadata map (page size, warnings, element count, ...).</p>
 */
public final class McpImageResult {

    private final String mimeType;
    private final byte[] data;
    private final Map<String, Object> meta;

    public McpImageResult(String mimeType, byte[] data, Map<String, Object> meta) {
        this.mimeType = mimeType != null ? mimeType : "image/png";
        this.data = data != null ? data : new byte[0];
        this.meta = meta;
    }

    public String getMimeType() { return mimeType; }
    public byte[] getData() { return data; }
    public Map<String, Object> getMeta() { return meta; }

    /** Base64 of the raw bytes (the value placed in the image content block). */
    public String base64() {
        return java.util.Base64.getEncoder().encodeToString(data);
    }
}
