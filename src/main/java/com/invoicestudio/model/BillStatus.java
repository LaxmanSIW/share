package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum BillStatus {
    UNPAID("unpaid", "Unpaid", "badge-warning"),
    PAID("paid", "Paid", "badge-success"),
    CANCELLED("cancelled", "Cancelled", "badge-error");

    private final String code;
    private final String label;
    private final String styleClass;

    BillStatus(String code, String label, String styleClass) {
        this.code = code;
        this.label = label;
        this.styleClass = styleClass;
    }

    @JsonValue
    public String getCode() { return code; }

    public String getLabel() { return label; }
    public String getStyleClass() { return styleClass; }

    @JsonCreator
    public static BillStatus fromString(String val) {
        if (val == null) return UNPAID;
        for (BillStatus s : values()) {
            if (s.code.equalsIgnoreCase(val) || s.name().equalsIgnoreCase(val)) return s;
        }
        return UNPAID;
    }

    @Override
    public String toString() { return label; }
}
