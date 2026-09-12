package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VariableDef {
    private String key;
    private String label;
    private String type = "text"; // text, number, date
    private boolean builtin;

    /**
     * "fixed"  — one value per bill (shown as an input field in CreateBillView).
     * "table"  — one value per line-item (added as a table column in TemplateDesigner).
     * Defaults to "fixed" for backward-compatibility with existing records.
     */
    private String scope = "fixed";

    /**
     * For fixed-scope variables: optional pre-filled default that appears in the
     * CreateBillView input field when starting a new bill.  Empty = no pre-fill.
     */
    private String defaultValue = "";

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

    public String getScope() { return scope != null ? scope : "fixed"; }
    public void setScope(String scope) { this.scope = scope != null ? scope : "fixed"; }

    public String getDefaultValue() { return defaultValue != null ? defaultValue : ""; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue != null ? defaultValue : ""; }

    @Override
    public String toString() { return label + " (" + key + ")"; }
}
