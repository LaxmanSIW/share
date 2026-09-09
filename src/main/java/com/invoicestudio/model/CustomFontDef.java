package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CustomFontDef {
    private String id;
    private String name;
    private String family;
    private String source = "google"; // google, upload, url
    private String url;
    private String fileUrl;
    private String dataUrl;
    private String format;
    private String category = "sans"; // sans, serif, mono, display, handwriting
    private String createdAt;

    public CustomFontDef() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getFamily() { return family; }
    public void setFamily(String family) { this.family = family; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getFileUrl() { return fileUrl; }
    public void setFileUrl(String fileUrl) { this.fileUrl = fileUrl; }

    public String getDataUrl() { return dataUrl; }
    public void setDataUrl(String dataUrl) { this.dataUrl = dataUrl; }

    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
