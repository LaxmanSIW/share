package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One Bulk Label Print run, recorded for the "Label Print History" catalog
 * view (when / what / how much — pure info, never used by billing).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LabelPrintHistory {
    private String id;
    private String templateId;
    private String templateName;
    private String printerName;
    private double labelWidth;   // mm
    private double labelHeight;  // mm
    private int columns;
    private int pages;           // strip rows sent to the printer
    private int labels;          // total physical labels printed
    private int totalCopies;     // sum of copies across all print lines (== labels)
    /** Human-readable print queue: "Item A · 19 · S × 20; ..." */
    private String summary;
    /** JSON of the print lines as entered in the Bulk Print popup. */
    private String linesJson;
    private String userId;
    private String createdAt;

    public LabelPrintHistory() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }

    public String getTemplateName() { return templateName != null ? templateName : ""; }
    public void setTemplateName(String templateName) { this.templateName = templateName; }

    public String getPrinterName() { return printerName != null ? printerName : ""; }
    public void setPrinterName(String printerName) { this.printerName = printerName; }

    public double getLabelWidth() { return labelWidth; }
    public void setLabelWidth(double labelWidth) { this.labelWidth = labelWidth; }

    public double getLabelHeight() { return labelHeight; }
    public void setLabelHeight(double labelHeight) { this.labelHeight = labelHeight; }

    public int getColumns() { return columns; }
    public void setColumns(int columns) { this.columns = columns; }

    public int getPages() { return pages; }
    public void setPages(int pages) { this.pages = pages; }

    public int getLabels() { return labels; }
    public void setLabels(int labels) { this.labels = labels; }

    public int getTotalCopies() { return totalCopies; }
    public void setTotalCopies(int totalCopies) { this.totalCopies = totalCopies; }

    public String getSummary() { return summary != null ? summary : ""; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getLinesJson() { return linesJson != null ? linesJson : ""; }
    public void setLinesJson(String linesJson) { this.linesJson = linesJson; }

    public String getUserId() { return userId != null ? userId : ""; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getCreatedAt() { return createdAt != null ? createdAt : ""; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
