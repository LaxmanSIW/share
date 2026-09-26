package com.invoicestudio.ui;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents a color theme for InvoiceStudio.
 * Each theme defines a complete {@code .root} CSS block containing
 * design system tokens.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Theme {

    private String id;
    private String name;
    private String description;
    private String cssContent;
    private boolean builtIn;
    private long createdAt;
    private long updatedAt;

    public Theme() {
    }

    public Theme(String id, String name, String description, String cssContent, boolean builtIn) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.cssContent = cssContent;
        this.builtIn = builtIn;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCssContent() {
        return cssContent;
    }

    public void setCssContent(String cssContent) {
        this.cssContent = cssContent;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public void setBuiltIn(boolean builtIn) {
        this.builtIn = builtIn;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * Extracts a CSS variable value from this theme's CSS content.
     */
    public String extractVariable(String variableName, String defaultValue) {
        if (cssContent == null || variableName == null) return defaultValue;
        Pattern pattern = Pattern.compile(Pattern.quote(variableName) + "\\s*:\\s*([^;]+);");
        Matcher matcher = pattern.matcher(cssContent);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return defaultValue;
    }

    public String getBgColor() {
        return extractVariable("-color-bg", "#0B0E13");
    }

    public String getSurfaceColor() {
        return extractVariable("-color-surface", "#151B25");
    }

    public String getAccentColor() {
        return extractVariable("-color-accent", "#D9A13B");
    }

    public String getAccentTextColor() {
        return extractVariable("-color-accent-text", "#F2CA6B");
    }

    public String getSuccessColor() {
        return extractVariable("-color-success", "#10B981");
    }

    public String getBorderColor() {
        return extractVariable("-color-border", "#232B38");
    }

    public String getTextColor() {
        return extractVariable("-color-text", "#F4F4F5");
    }

    @Override
    public String toString() {
        return name != null ? name : (id != null ? id : "Unnamed Theme");
    }
}
