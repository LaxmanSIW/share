package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BuyerFieldDef {
    private String key;
    private String label;
    private String type = "text"; // text, number, date

    public BuyerFieldDef() {}

    public BuyerFieldDef(String key, String label, String type) {
        this.key = key;
        this.label = label;
        this.type = type != null ? type : "text";
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
