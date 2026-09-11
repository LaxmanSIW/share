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
    BARCODE("barcode"),
    CIRCLE("circle"),
    ELLIPSE("ellipse"),
    POLYLINE("polyline"),
    POLYGON("polygon"),
    ARC("arc"),
    PATH("path"),
    STAR("star"),
    ARROW("arrow"),
    DIVIDER("divider"),
    FREEHAND("freehand"),
    WATERMARK("watermark"),
    SVG("svg"),
    ICON("icon"),
    GROUP("group"),
    COMPONENT("component");

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
