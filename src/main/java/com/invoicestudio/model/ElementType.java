package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ElementType {
    TEXT("text"),
    IMAGE("image"),
    TABLE("table"),
    LINE("line"),
    RECT("rect"),
    PAGENO("pageno"),
    QRCODE("qrcode"),
    BARCODE("barcode");

    private final String code;

    ElementType(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() { return code; }

    @JsonCreator
    public static ElementType fromString(String val) {
        if (val == null) return TEXT;
        for (ElementType t : values()) {
            if (t.code.equalsIgnoreCase(val) || t.name().equalsIgnoreCase(val)) return t;
        }
        return TEXT;
    }
}
