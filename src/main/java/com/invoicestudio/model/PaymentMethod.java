package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentMethod {
    CASH("Cash"),
    UPI("UPI"),
    BANK_TRANSFER("Bank Transfer"),
    CHEQUE("Cheque"),
    CARD("Card"),
    OTHER("Other");

    private final String label;

    PaymentMethod(String label) {
        this.label = label;
    }

    @JsonValue
    public String getLabel() { return label; }

    @JsonCreator
    public static PaymentMethod fromString(String val) {
        if (val == null) return CASH;
        for (PaymentMethod m : values()) {
            if (m.label.equalsIgnoreCase(val) || m.name().equalsIgnoreCase(val.replace(" ", "_"))) return m;
        }
        return CASH;
    }

    @Override
    public String toString() { return label; }
}
