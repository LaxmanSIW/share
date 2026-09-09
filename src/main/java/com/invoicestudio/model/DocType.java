package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DocType {
    INVOICE("invoice", "TAX INVOICE", "Invoice", "INV", "badge-accent"),
    PROFORMA("proforma", "PROFORMA INVOICE", "Proforma", "PI", "badge-neutral"),
    QUOTATION("quotation", "QUOTATION", "Quotation", "QT", "badge-neutral"),
    CHALLAN("challan", "DELIVERY CHALLAN", "Challan", "DC", "badge-neutral"),
    CREDITNOTE("creditnote", "CREDIT NOTE", "Credit Note", "CN", "badge-error");

    private final String code;
    private final String title;
    private final String label;
    private final String shortCode;
    private final String styleClass;

    DocType(String code, String title, String label, String shortCode, String styleClass) {
        this.code = code;
        this.title = title;
        this.label = label;
        this.shortCode = shortCode;
        this.styleClass = styleClass;
    }

    @JsonValue
    public String getCode() { return code; }

    public String getTitle() { return title; }
    public String getLabel() { return label; }
    public String getShortCode() { return shortCode; }
    public String getStyleClass() { return styleClass; }

    public boolean isRevenue() {
        return this == INVOICE;
    }

    @JsonCreator
    public static DocType fromString(String val) {
        if (val == null) return INVOICE;
        for (DocType d : values()) {
            if (d.code.equalsIgnoreCase(val) || d.name().equalsIgnoreCase(val)) return d;
        }
        return INVOICE;
    }

    @Override
    public String toString() { return label; }
}
