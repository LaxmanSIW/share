package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TableColumn {
    private String key;
    private String label;
    private double width; // percentage of table width (0-100)
    private String align = "left"; // left, center, right

    public TableColumn() {}

    public TableColumn(String key, String label, double width, String align) {
        this.key = key;
        this.label = label;
        this.width = width;
        this.align = align;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public double getWidth() { return width; }
    public void setWidth(double width) { this.width = width; }

    public String getAlign() { return align != null ? align : "left"; }
    public void setAlign(String align) { this.align = align; }
}
