package com.invoicestudio.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RepeatCadence {
    NONE("none", "None"),
    WEEKLY("weekly", "Weekly"),
    MONTHLY("monthly", "Monthly"),
    YEARLY("yearly", "Yearly");

    private final String code;
    private final String label;

    RepeatCadence(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @JsonValue
    public String getCode() { return code; }

    public String getLabel() { return label; }

    @JsonCreator
    public static RepeatCadence fromString(String val) {
        if (val == null) return NONE;
        for (RepeatCadence r : values()) {
            if (r.code.equalsIgnoreCase(val) || r.name().equalsIgnoreCase(val)) return r;
        }
        return NONE;
    }

    @Override
    public String toString() { return label; }
}
