package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PageSizeName {
    A4("A4", 210, 297),
    A5("A5", 148, 210),
    LETTER("Letter", 215.9, 279.4),
    LEGAL("Legal", 215.9, 355.6),
    THERMAL_80("Thermal 80", 80, 240),
    THERMAL_58("Thermal 58", 58, 180),
    CUSTOM("Custom", 210, 297);

    private final String label;
    private final double defaultWidth; // mm
    private final double defaultHeight; // mm

    PageSizeName(String label, double defaultWidth, double defaultHeight) {
        this.label = label;
        this.defaultWidth = defaultWidth;
        this.defaultHeight = defaultHeight;
    }

    @JsonValue
    public String getLabel() { return label; }

    public double getDefaultWidth() { return defaultWidth; }
    public double getDefaultHeight() { return defaultHeight; }

    @JsonCreator
    public static PageSizeName fromString(String val) {
        if (val == null) return A4;
        for (PageSizeName s : values()) {
            if (s.label.equalsIgnoreCase(val) || s.name().equalsIgnoreCase(val.replace(" ", "_"))) return s;
        }
        return A4;
    }

    @Override
    public String toString() { return label; }
}
