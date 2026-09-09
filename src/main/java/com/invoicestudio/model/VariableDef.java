package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VariableDef {
    private String key;
    private String label;
    private String type = "text"; // text, number, date
    private boolean builtin;

    public VariableDef() {}

    public VariableDef(String key, String label, String type, boolean builtin) {
        this.key = key;
        this.label = label;
        this.type = type != null ? type : "text";
        this.builtin = builtin;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isBuiltin() { return builtin; }
    public void setBuiltin(boolean builtin) { this.builtin = builtin; }

    @Override
    public String toString() { return label + " (" + key + ")"; }
}
