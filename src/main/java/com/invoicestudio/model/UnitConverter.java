package com.invoicestudio.model;

import java.util.Locale;

public class UnitConverter {

    public static final double DEFAULT_SCREEN_DPI = 96.0;
    public static final double DEFAULT_PRINT_DPI = 300.0;
    public static final double MM_PER_INCH = 25.4;
    public static final double PT_PER_INCH = 72.0;

    public enum Unit {
        MM("mm", "Millimeters"),
        PX("px", "Pixels (Screen 96 DPI)"),
        PT("pt", "Points (1/72 in)"),
        CM("cm", "Centimeters"),
        IN("in", "Inches"),
        INCH("in", "Inches");

        private final String code;
        private final String label;

        Unit(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String getCode() { return code; }
        public String getLabel() { return label; }

        public static Unit fromCode(String code) {
            if (code == null) return MM;
            for (Unit u : values()) {
                if (u.code.equalsIgnoreCase(code.trim()) || u.name().equalsIgnoreCase(code.trim())) {
                    return u;
                }
            }
            return MM;
        }
    }

    public static double mmToPx(double mm) {
        return mmToPx(mm, DEFAULT_SCREEN_DPI);
    }

    public static double mmToPx(double mm, double dpi) {
        return mm * (dpi / MM_PER_INCH);
    }

    public static double pxToMm(double px) {
        return pxToMm(px, DEFAULT_SCREEN_DPI);
    }

    public static double pxToMm(double px, double dpi) {
        if (dpi <= 0) dpi = DEFAULT_SCREEN_DPI;
        return px * (MM_PER_INCH / dpi);
    }

    public static double mmToPt(double mm) {
        return mm * (PT_PER_INCH / MM_PER_INCH);
    }

    public static double ptToMm(double pt) {
        return pt * (MM_PER_INCH / PT_PER_INCH);
    }

    public static double toMm(double value, Unit unit) {
        return toMm(value, unit, DEFAULT_SCREEN_DPI);
    }

    public static double toMm(double value, Unit unit, double dpi) {
        if (unit == null) unit = Unit.MM;
        return switch (unit) {
            case MM -> value;
            case CM -> value * 10.0;
            case IN, INCH -> value * MM_PER_INCH;
            case PT -> ptToMm(value);
            case PX -> pxToMm(value, dpi);
        };
    }

    public static double fromMm(double mm, Unit unit) {
        return fromMm(mm, unit, DEFAULT_SCREEN_DPI);
    }

    public static double fromMm(double mm, Unit unit, double dpi) {
        if (unit == null) unit = Unit.MM;
        return switch (unit) {
            case MM -> mm;
            case CM -> mm / 10.0;
            case IN, INCH -> mm / MM_PER_INCH;
            case PT -> mmToPt(mm);
            case PX -> mmToPx(mm, dpi);
        };
    }

    public static String format(double value, Unit unit) {
        if (unit == null) unit = Unit.MM;
        return switch (unit) {
            case MM, PT -> String.format(Locale.US, "%.1f %s", value, unit.getCode());
            case CM, IN, INCH -> String.format(Locale.US, "%.2f %s", value, unit.getCode());
            case PX -> String.format(Locale.US, "%.0f %s", value, unit.getCode());
        };
    }
}
